# Releasing

Releases are built and published by GitHub Actions
([`.github/workflows/release.yml`](.github/workflows/release.yml)) when a version tag
is pushed. One tag publishes everything:

| Artifact | Where |
|---|---|
| Server image | `ghcr.io/stevenpg/howlongtocook-server:<version>`, `:<major>.<minor>` and `:latest` |
| Android APK | `HowLongToCook-<version>.apk` and its `.sha256`, attached to the GitHub Release |
| Release notes | Generated from the version's section in `CHANGELOG.md` |
| Provenance | Build attestations for the image and the APK, plus an SBOM for the image |

The app and the server share one version number.

## One-time setup

### 1. Create the Android signing key

Android only installs an update if it is signed with the **same key** as the installed
app. Create the key once and keep it safe. **If it is lost, existing users must
uninstall and reinstall** (losing local data that hasn't been synced or exported).

```sh
keytool -genkeypair -v \
  -keystore howlongtocook-release.jks \
  -alias howlongtocook \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=HowLongToCook"
```

- Use the **same password** for the keystore and the key: the default PKCS12 format
  doesn't support separate ones.
- Never commit the `.jks` file (`.gitignore` already excludes `*.jks`).
- Back it up and the password with it, for example in a password manager.

### 2. Add the repository secrets

In **Settings → Secrets and variables → Actions → New repository secret**, or with the GitHub CLI:

```sh
base64 -w0 howlongtocook-release.jks | gh secret set ANDROID_KEYSTORE_BASE64   # macOS: base64 -i howlongtocook-release.jks
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS --body howlongtocook
gh secret set ANDROID_KEY_PASSWORD        # same value as the keystore password
```

The workflow fails before building the APK if these are missing, and it refuses to
publish an APK signed with a debug key.

### 3. Check Actions is allowed to run

**Settings → Actions → General**: Actions must be enabled. Each job declares the
permissions it needs, so the default read-only workflow permissions are fine.

### 4. Decide who can download

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
   - `CHANGELOG.md`: rename **Unreleased** to `## [X.Y.Z] - YYYY-MM-DD`, add a fresh empty
     **Unreleased** section, and update the links at the bottom
   - the image tag in `deploy/kubernetes/kustomization.yaml` and `deploy/docker/compose.yaml`
4. Merge the pull request, then tag the merge commit on `main`:

   ```sh
   git checkout main && git pull
   git tag -a vX.Y.Z -m "HowLongToCook X.Y.Z"
   git push origin vX.Y.Z
   ```

5. Watch **Actions → Release**. The run tests both projects, then builds and publishes the
   image and the APK, and finally creates the GitHub Release.

A tag with a suffix, such as `v0.2.0-rc.1`, is published as a **pre-release** and doesn't
move the `latest` image tag.

### If a release fails

- **The version check failed:** the tag doesn't match `server/Cargo.toml`, or the CHANGELOG
  section is missing. Delete the tag (`git push --delete origin vX.Y.Z && git tag -d vX.Y.Z`),
  fix it on `main`, and tag again.
- **A later job failed:** fix the cause and use **Re-run failed jobs**. Pushing the image
  again with the same tags is harmless.
- **Never re-tag a version that was published.** Release a new patch version instead.

## Verifying a release

```sh
sha256sum -c HowLongToCook-X.Y.Z.apk.sha256
gh attestation verify HowLongToCook-X.Y.Z.apk --repo StevenPG/HowLongToCook
gh attestation verify oci://ghcr.io/stevenpg/howlongtocook-server:X.Y.Z --repo StevenPG/HowLongToCook
apksigner verify --print-certs HowLongToCook-X.Y.Z.apk   # the signer must be the same for every release
```
