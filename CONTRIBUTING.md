# Contributing to HowLongToCook

Thanks for your interest in improving HowLongToCook! This guide covers how to report
problems, propose changes and get a pull request merged.

By participating you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to contribute

- **Report a bug.** Open a [bug report](../../issues/new/choose) with steps to reproduce,
  your Android version and device, and which storage backend you use.
- **Suggest a feature.** Open a [feature request](../../issues/new/choose) and describe
  the problem before the solution.
- **Improve the docs.** Typos, clarifications and examples are always welcome.
- **Write code.** Look for issues labelled `good first issue` or `help wanted`, or
  comment on an issue before starting larger work so we can agree on the approach.

Security problems should **not** be reported in public issues. See [SECURITY.md](SECURITY.md).

## Development setup

| Tool | Version |
|---|---|
| JDK | 17+ |
| Android Studio | latest stable (installs SDK platform 37) |
| Rust | stable 1.85+ (`rustup`) |
| Docker | optional, for `just dev-up` |
| [`just`](https://just.systems) | optional task runner |

```sh
git clone https://github.com/StevenPG/HowLongToCook.git
cd HowLongToCook
just test      # server tests + Android JVM tests
just check     # rustfmt, clippy, Android Lint
just dev-up    # local server + S3-compatible store for manual testing
```

Open the `android/` directory (not the repo root) in Android Studio.

## Project structure

- `android/`: the app. Layers: `data/` (model, Room, settings, remote stores, sync),
  `ui/` (one package per screen, each with a ViewModel), `di/`, `util/`.
- `server/`: the Rust server: `api.rs` (JSON API), `admin.rs` + `templates/` (admin UI),
  `db.rs` (SQLite + sync rules), `model.rs` (validation).
- `api/`: `openapi.yaml` and `examples/`. **The JSON format is a contract.** If you
  change the product model, update the OpenAPI spec, `api/examples/product.json`,
  and both the Kotlin and Rust models and validation, together.
- `docs/DESIGN.md`: update it when behavior or architecture changes.

## Coding standards

### Kotlin / Android
- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
  (`kotlin.code.style=official`); `.editorconfig` sets the basics.
- Compose: stateless, previewable composables; state lives in a `@HiltViewModel` and is
  exposed as `StateFlow`.
- Use Material 3 theme tokens (`MaterialTheme.colorScheme`, `typography`, `shapes`),
  never hard-coded colors, so light, dark and dynamic color keep working.
- New user-facing text should go in `res/values/strings.xml` (existing text is being migrated).
- Keep `./gradlew lintDebug` free of errors.

### Rust
- `cargo fmt` and `cargo clippy --all-targets -- -D warnings` must pass.
- Return typed errors (`ApiError`); don't `unwrap()` on request paths.
- Admin UI: server-rendered and dependency-light. Avoid adding a JavaScript build step.

### Tests
- Bug fixes come with a test that fails without the fix.
- Sync and merge changes need tests in `SyncEngineTest` / `S3RemoteStoreTest` and the
  server's `db.rs` tests.
- UI changes: update or add a screenshot in `ScreenshotTest` and check the output
  (`./gradlew testDebugUnitTest -Pscreenshots`).

## Commits and pull requests

1. Fork the repo and create a branch from `main` (`feature/…`, `fix/…`).
2. Keep pull requests focused; one logical change per PR.
3. Write clear commit messages in the imperative mood ("Add stovetop heat presets").
   [Conventional Commits](https://www.conventionalcommits.org) prefixes are welcome but not required.
4. Update `CHANGELOG.md` under **Unreleased** for user-visible changes.
5. Fill in the pull request template, including the AI-assistance disclosure.
6. Make sure `just test` and `just check` pass.

A maintainer will review your PR. Please be patient: this is a personal project
maintained in spare time.

## AI-assisted contributions

AI tools are allowed with disclosure and human review. See the policy in
[AI_DISCLOSURE.md](AI_DISCLOSURE.md#ai-assisted-contributions).

## License

By contributing, you agree that your contributions are licensed under the
[GNU General Public License v3.0 or later](LICENSE), the same license as the project.
