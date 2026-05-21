---
name: Bug report
about: Report a bug to help us improve Garfield
title: '[Bug] '
labels: bug
assignees: ''

---

Please search [existing issues](../../issues?q=is%3Aissue) first — there may already be a duplicate.

If the bug is trivial, go ahead and file. Otherwise, please fill in the sections below.

## Bug Description

A clear and concise description of the bug.

## Affected Module(s)

Which module(s) exhibit the bug?

- [ ] `garfield-common`
- [ ] `garfield-engine`
- [ ] `garfield-transfer`
- [ ] `garfield-process`
- [ ] `garfield-spring-boot-starter`
- [ ] `garfield-example`

## Environment

- Garfield version:
- Spring Boot version:
- Java version:
- OS:
- Storage backend(s) (Redis / Kafka / other):

## Configuration Snippet

If the bug is routing-related, paste the relevant `application.yml` and JSON routing config (redact secrets):

```yaml
# application.yml
```

```json
// routing config
```

## Steps to Reproduce

1. ...
2. ...
3. ...

## Expected Behavior

What you expected to happen.

## Minimal Reproducible Example

A failing test or a minimal Spring Boot project that reproduces the issue. Reproducible reports are prioritized.

## Stack Trace

```
paste here
```
