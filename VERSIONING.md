# Versioning Policy

Garfield follows [Semantic Versioning 2.0.0](https://semver.org/).

A Garfield version number is `MAJOR.MINOR.PATCH`:

- `MAJOR` — incremented for **incompatible API changes**.
- `MINOR` — incremented for **backwards-compatible feature additions**.
- `PATCH` — incremented for **backwards-compatible bug fixes**.

## Pre-1.0 Stability

Garfield is currently pre-1.0. Until `1.0.0` GA:

- **MINOR versions may contain breaking changes.** We will document them in
  the release notes, but downstream callers should not assume strict SemVer
  guarantees during this phase.
- **PATCH versions remain backwards-compatible** even pre-1.0.
- We aim to ship `1.0.0` once the SPI surface (engine, lock, circuit breaker,
  metrics, compensation channel, back-off) is stable based on real-world usage.

After `1.0.0`, full SemVer guarantees apply.

## Public API

The following are part of Garfield's **public API** and follow the versioning
policy above:

- All non-`internal` packages in:
  - `garfield-common`
  - `garfield-engine`
  - `garfield-transfer`
  - `garfield-process`
  - `garfield-spring-boot-starter`
- All Spring Boot autoconfiguration property keys (under `garfield.*`)
- All SPI interfaces and their default implementations

The following are **NOT** part of the public API and may change in any release:

- Any package containing `.internal.` in its path
- Any class or member annotated with `@Internal`
- Any class or member annotated with `@Experimental` (see below)
- The `garfield-example` module (it is illustrative, not consumable)

## API Stability Annotations

Garfield uses two annotations to mark APIs that fall outside normal SemVer
guarantees:

- **`@Experimental`** — The API is being trialed and may change or be removed
  in a future MINOR release. Do not use it in production code unless you can
  tolerate breakage.
- **`@Internal`** — The API exists only for use by other Garfield modules.
  External callers should not depend on it; it can change in any release.

If neither annotation is present and the API lives in a non-`internal` package,
it is considered stable (subject to the pre-1.0 caveat above).

## Deprecation Policy

When we plan to remove a public API:

1. The API is marked `@Deprecated` with a Javadoc note pointing at the
   replacement.
2. The API remains functional for **at least one MINOR release** before
   removal.
3. Removal happens only in a MAJOR release. Migration steps are documented
   in `MIGRATION-<version>.md`.

## Supported Versions

Pre-1.0: only the latest released MINOR version receives bug fixes and
security updates.

Post-1.0: the latest MINOR and the previous MINOR receive patch updates.
Older MINORs are end-of-life.

This policy is referenced by [SECURITY.md](SECURITY.md).

## Release Cadence

We do not commit to a fixed cadence. Releases happen when there is meaningful
work to ship. Security fixes are released as soon as a fix is validated.
