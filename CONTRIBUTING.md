# Contributing to Garfield

Thank you for your interest in contributing to Garfield — a multi-storage
orchestration framework for Spring Boot. This document outlines how to
contribute effectively.

## Prerequisites

The following are required to work on the codebase:

- **Java 21+** — Garfield targets JDK 21 (see `<java.version>` in `pom.xml`)
- **Git**
- A working **Redis** and **Kafka** instance only if you want to run the
  `garfield-example` module end-to-end (the unit tests do not require them)

You do **not** need to install Maven — the repository ships a Maven Wrapper
(`./mvnw`) that downloads the pinned version (3.9.9) on first use.

## Getting Started

1. Fork the repository on GitHub.
2. Clone your fork:

```bash
git clone https://github.com/YOUR-USERNAME/Garfield.git
cd Garfield
```

3. Build and run the test suite:

```bash
./mvnw clean install -DskipTests   # build only
./mvnw test                         # run unit tests across all modules
./mvnw verify                       # full build incl. integration-test phase
```

> **Tip**: If you use [`jenv`](https://github.com/jenv/jenv), the project's
> `.java-version` is git-ignored — set your local pin once with
> `jenv local 21.0.5`.

## Reporting Issues

Please open an issue if you discover a bug or wish to propose an enhancement.
Bug reports should include:

- A minimal reproducer (code snippet or repository link)
- The Garfield version and the affected module(s)
- Expected vs. actual behavior
- Stack trace, if applicable

For security vulnerabilities, **do not** open a public issue — see
[SECURITY.md](SECURITY.md).

## Making Changes

1. Create a feature branch:

```bash
git checkout -b feature/short-description
```

2. Make your changes, keeping commits focused and well-described.
3. Validate locally before pushing:

```bash
./mvnw clean verify
```

### Design Principles

When proposing changes, keep these principles in mind — they shape what gets
accepted:

1. **Simple and minimal**. Garfield is a framework that downstream services
   depend on. New concepts and primitives raise the long-term maintenance and
   compatibility burden, so the bar for adding them is high.
2. **SPI-first extensibility**. Prefer adding a new SPI interface (with a
   sensible default implementation) over hard-coding new behavior into core
   modules. Circuit breakers, distributed locks, metrics, compensation
   channels, and back-off strategies are all SPIs today; new pluggable
   behavior should follow the same pattern.
3. **Configuration-driven, not code-driven**. Routing decisions
   (`reqClassName → leader/follower`) live in configuration files and reload
   hot. New runtime behavior should ideally be expressible as configuration,
   not hard-wired into Java code paths.

### Code Style

- Use the existing formatting in the module you are touching.
- Lombok annotations (`@Slf4j`, `@RequiredArgsConstructor`, `@Data`, etc.) are
  acceptable; do not introduce a different code-generation toolchain.
- Public APIs across module boundaries (e.g., anything in `garfield-common`
  consumed by `garfield-engine`) require Javadoc.

### Tests

- New code should come with unit tests. Use **JUnit 5** + **Mockito** —
  these are the only frameworks already on the test classpath.
- Tests must be hermetic: do not require a live Redis, Kafka, or network
  service. The `garfield-example` module is the only place where end-to-end
  scenarios touching real infrastructure are acceptable, and even there they
  should be runnable on a developer laptop with default configuration.
- Run `./mvnw verify` and confirm a green build before opening a PR.

## Submitting Changes

1. For non-trivial changes, please open an issue first to align on scope and
   approach with the maintainers.
2. For trivial changes (typos, small documentation fixes, obviously correct
   one-line bug fixes), feel free to open a PR directly.
3. Push your changes to your fork.
4. Open a pull request against `main` in the upstream repository.
5. Ensure CI passes — see the `CI / Build and Test` check on your PR.
6. Wait for review.
7. For follow-up work, please **add new commits instead of force-pushing** so
   reviewers can see incremental changes.

## Code of Conduct

This project follows a [Code of Conduct](CODE_OF_CONDUCT.md). By participating,
you agree to uphold it.

## License

By contributing, you agree that your contributions will be licensed under the
project's license (to be added).

## Questions

If you have questions, please open an issue in the repository and search
existing issues first.
