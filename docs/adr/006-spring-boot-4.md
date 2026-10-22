# 006 · Spring Boot 4 instead of 3.x

**Status:** accepted · 2026-10

## Context

The initial brief asked for Spring Boot 3.x. When the project started, 4.1 was the current stable line and open-source
support for 3.5, the last 3.x line, had ended in June 2026.

## Decision

Use Spring Boot 4.1 with Java 21, and the libraries of its generation: Jackson 3, Testcontainers 2, springdoc 3,
Spring Security 7, Hibernate 7, Flyway 12.

## Consequences

- The project starts on a supported baseline instead of beginning with a migration.
- Some ecosystem examples online still target Boot 3 (package names of test slices, Jackson 2 imports); the code
  follows the Boot 4 modules (`spring-boot-starter-webmvc`, `-flyway`, `-restclient`, `-security`).
