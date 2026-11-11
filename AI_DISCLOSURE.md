# AI Disclosure

HowLongToCook is built with substantial help from generative AI. This document says
what that means, so users and contributors can judge the project with full information.

## How AI was used

The initial version of this repository was produced with **Claude Code**, Anthropic's
AI coding assistant, working under the direction of the maintainer. The maintainer
decided the requirements, architecture and trade-offs (storage options, data model,
technology choices, scope). The assistant then:

- wrote the design document (`docs/DESIGN.md`) from those decisions
- wrote most of the source code: the Android app, the Rust server and admin UI,
  deployment examples and build scripts
- wrote the automated tests and the documentation
- produced the logo and the brand color palette

Commits made with AI assistance carry a `Co-Authored-By:` trailer naming the assistant,
so the history shows which changes were AI-assisted.

## How the code is checked

Generated code was not accepted on trust. Before it was proposed, it was:

- **compiled and linted:** Android Lint, `cargo fmt` and `cargo clippy -D warnings`
- **tested automatically:**
  - JVM and Robolectric tests for the app, including two simulated devices syncing to convergence
  - unit and HTTP integration tests for the server
  - the S3 request signer checked against Amazon's published test vectors
- **run against real backends:** the app's sync code was exercised against a real
  S3-compatible store and the real server
- **checked visually:** screens rendered to images and inspected

Every change is reviewed by the maintainer before it is merged.

Automated checks don't prove the code is correct. Some behavior has so far only been
verified with automated tests, not by hand on a physical device: camera scanning,
the permission flow, background sync timing and Keystore storage. Please report
anything that looks wrong; see [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## What AI is not used for

- The app has **no AI features**. It sends nothing to AI services and has no telemetry.
- No user data was used to build or test it.

## AI-assisted contributions

You may use AI tools to help with contributions, under these conditions:

1. **You are responsible for what you submit.** Understand every line and be able to
   explain and maintain it. Don't submit code you haven't read.
2. **Disclose it.** Say in the pull request description whether, and roughly how, AI
   tools were used (for example "tests generated with an assistant, reviewed by me").
   A `Co-Authored-By:` trailer is welcome.
3. **Verify it.** Run the full test and lint suite (`just test`, `just check`) and test
   UI changes on a device or emulator.
4. **Respect licensing.** Contributions are licensed GPL-3.0-or-later. Don't submit
   output that reproduces third-party code under an incompatible license.
5. **No low-effort bulk submissions.** Unreviewed, machine-generated pull requests or
   issues may be closed without review.

## Questions

If you have concerns about AI use in this project, open a
[discussion or issue](../../issues).
