# Security Policy

Thank you for helping keep Garfield and its users secure.

## Reporting Security Issues

If you discover a security vulnerability in Garfield, please report it privately
by opening a [GitHub Security Advisory](../../security/advisories/new) in this
repository. For background on the process, see GitHub's
[guidance on privately reporting a vulnerability](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability).

Please **do not** report security vulnerabilities through public GitHub issues,
discussions, or pull requests, as this may expose users to risk before a fix is available.

## What to Include

To help us triage and respond quickly, please include:

- A description of the vulnerability
- The affected module(s) (e.g., `garfield-process`, `garfield-engine`)
- The Garfield version(s) impacted
- Steps to reproduce the issue
- The potential impact (data loss, denial of service, information disclosure, etc.)
- Any suggested fixes (optional)

## Supported Versions

Until Garfield reaches `1.0.0` GA, only the latest released version receives
security updates. Once stable, the supported version policy will be documented
in [VERSIONING.md](VERSIONING.md).

## Response Process

- **Acknowledgement**: We will acknowledge reports as soon as possible.
- **Investigation**: A maintainer will assess severity and reproducibility.
- **Fix and disclosure**: Confirmed vulnerabilities will be fixed in a private
  fork, then released alongside a public security advisory.
- **Credit**: Reporters will be credited in the advisory unless they prefer to
  remain anonymous.
