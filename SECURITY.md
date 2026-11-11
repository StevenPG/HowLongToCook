# Security Policy

## Supported versions

HowLongToCook is pre-1.0. Security fixes are made on `main` and included in the next
release; older releases are not patched.

| Version | Supported |
|---|---|
| `main` / latest release | ✅ |
| older releases | ❌ |

## Reporting a vulnerability

**Please do not report security vulnerabilities in public issues, discussions or pull requests.**

Report them privately through GitHub's
[private vulnerability reporting](../../security/advisories/new)
(**Security** tab → **Report a vulnerability**).

Please include:

- the affected component (Android app, server, admin UI, deployment examples)
- version or commit
- steps to reproduce, or a proof of concept
- the impact you expect, such as credential exposure, data loss, or unauthorized access

What to expect:

- acknowledgement within **7 days**
- an assessment and a fix plan, or a reasoned "not a vulnerability", within **30 days**
- credit in the release notes if you'd like it

## Scope and security model

These help you judge whether something is a vulnerability:

- **Server authentication** is a single shared bearer token. Anyone holding the token
  has full access to the API and admin UI; that is by design.
- **The server expects TLS from a reverse proxy.** It serves plain HTTP. Set
  `HLTC_SECURE_COOKIES=true` when it is served over HTTPS.
- **The Android app allows cleartext HTTP** so you can use LAN servers. It warns when an
  `http://` URL is entered.
- **App credentials** (S3 secret key, server token) are encrypted with an Android
  Keystore key and excluded from backups.
- **S3/R2 credentials** should be scoped to a single bucket. Over-privileged keys are a
  configuration issue rather than an app vulnerability, but hardening suggestions are welcome.

## Known limitations

- Two devices writing at the same instant are protected by ETag-conditional writes on
  AWS S3 and Cloudflare R2. Some S3-compatible stores ignore `If-Match`; on those the
  later write wins.
