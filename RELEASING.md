# Releasing

Releases are made **by hand**: nothing is published until a maintainer pushes a version
tag or creates a release in the GitHub UI. GitHub Actions
([`.github/workflows/release.yml`](.github/workflows/release.yml)) then builds and
publishes everything for that version:

| Artifact | Where |
|---|---|
| Server image | `ghcr.io/stevenpg/howlongtocook-server:<version>`, `:<major>.<minor>` and `:latest` |
| Android APK | `HowLongToCook-<version>.apk` and its `.sha256`, attached to the GitHub Release |
| Release notes | Install instructions, the version's `CHANGELOG.md` section if there is one, and GitHub's generated notes |
| Provenance | An SBOM for the image; build attestations for the image and the APK once the repository is public (GitHub stores them only for public repos on personal accounts) |

The app and the server share one version number. The signing key lives only in
repository secrets and on your machine; it is never needed anywhere else.

## One-time setup

### 1. Create the signing key and secrets

Android only installs an update if it is signed with the **same key** as the installed
app. Create the key once, on your own machine, with:

```sh
tools/setup_release_signing.sh
```

It needs `keytool` (from any JDK), `openssl`, and the GitHub CLI logged in to this
repository. The script:

- generates a 4096-bit key with a random password
- backs up the key and password in `~/.android/howlongtocook-release/` (readable only by you)
- stores them as the `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
  `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` repository secrets

The password goes straight from the script into GitHub. It is never printed, typed or
passed on a command line. The script refuses to overwrite an existing key.

**Copy the backup folder somewhere safe**, such as a password manager or an encrypted drive.
If the key is lost, existing users must uninstall and reinstall (losing anything not
synced or exported) to get later versions.

The workflow refuses to build an APK if these secrets are missing, and refuses to
publish one signed with a debug key.

### 2. Check Actions is allowed to run

**Settings → Actions → General**: Actions must be enabled. Each job declares the
permissions it needs, so the default read-only workflow permissions are fine.

### 3. Decide who can download

While the repository is **private**, the release, its APK and the GHCR image are only
visible to people with access to the repository. To share them:

- **Image only:** after the first release, open the package (**Your profile → Packages →
  howlongtocook-server → Package settings**) and change its visibility to **Public**.
- **Everything:** make the repository public. Release assets and the package then become
  downloadable by anyone.

## Making a release

1. Make sure `main` has everything to ship, and that `just test` and `just check` pass.
2. Pick the version, following [Semantic Versioning](https://semver.org). It is limited
   to `MAJOR.MINOR.PATCH` with minor and patch ≤ 99, because the Android `versionCode`
   is derived from it (`1.2.3` → `10203`).
3. On a branch, update:
   - `server/Cargo.toml` `version` (and run `cargo check` so `Cargo.lock` follows)
   - `CHANGELOG.md` (recommended): rename **Unreleased** to `## [X.Y.Z] - YYYY-MM-DD`, add a
     fresh empty **Unreleased** section, and update the links at the bottom. The section
     is copied into the release notes.
   - the image tag in `deploy/kubernetes/kustomization.yaml` and `deploy/docker/compose.yaml`
4. Merge the pull request, then publish in **one** of these ways:

   - **Push a tag** from `main` (or ask Claude Code to push it for you):

     ```sh
     git checkout main && git pull
     git tag -a vX.Y.Z -m "HowLongToCook X.Y.Z"
     git push origin vX.Y.Z
     ```

   - **Create the release in the GitHub UI:** **Releases → Draft a new release**, type the
     new tag `vX.Y.Z` targeting `main`, write notes if you like, and publish. The workflow
     adds the APK and checksum to that release and keeps your notes.

5. Watch **Actions → Release**. The run tests both projects, publishes the image and the
   APK, and creates or completes the GitHub Release.

A tag with a suffix, such as `v0.2.0-rc.1`, is published as a **pre-release** and doesn't
move the `latest` image tag.

### If a release fails

- **The version check failed:** the tag doesn't match `server/Cargo.toml`. Delete the tag (`git push --delete origin vX.Y.Z && git tag -d vX.Y.Z`),
  fix it on `main`, and tag again.
- **A later job failed:** fix the cause and use **Re-run failed jobs**. Pushing the image
  again with the same tags is harmless.
- **Moving a tag that never produced a release** (for example after fixing the cause):

  ```sh
  git push --delete origin vX.Y.Z && git tag -d vX.Y.Z
  git checkout main && git pull
  git tag -a vX.Y.Z -m "HowLongToCook X.Y.Z" && git push origin vX.Y.Z
  ```

  If an image was already pushed for that version, the new run overwrites its tags.
- **Never re-tag a version that was published** (a GitHub Release exists for it, or people
  installed it). Release a new patch version instead.

## Verifying a release

```sh
sha256sum -c HowLongToCook-X.Y.Z.apk.sha256
gh attestation verify HowLongToCook-X.Y.Z.apk --repo StevenPG/HowLongToCook
gh attestation verify oci://ghcr.io/stevenpg/howlongtocook-server:X.Y.Z --repo StevenPG/HowLongToCook
apksigner verify --print-certs HowLongToCook-X.Y.Z.apk   # the signer must be the same for every release
```
