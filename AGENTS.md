# AGENTS.md

## Project

`libeetlite` is a Groovy library for creating and parsing XML/SOAP EET messages.

## Build and test

- Use Gradle 9.1+ when running on Java 25; JDK 21 is suitable for running the build.
- Production and test code target Java 11 by default. When Gradle runs on JDK 8, the build automatically uses Java 8 compatibility settings, Groovy 4, and Java 8-compatible dependency versions. Gradle 8.14.5 is used with JDK 8; Gradle 9.6.1 is used with newer JDKs.
- When using the repository environment setup, run `source ../.env` before Gradle commands.
- Run the default checks with `gradle clean test`; this executes the `unit` and `local` groups.
- Run internet integration tests separately with `gradle internetTest`; they contact the EET Playground and require the certificates under `src/test/testData/cert`.
- Run one internet test with `gradle internetTest --tests "com.github.novakmi.libeetlite.test.EetliteXmlTest.getPokTest"`.
- `--no-daemon` is optional; use it for isolated one-off or CI runs.
- GitLab CI runs build/unit/local and internet integration checks on every push to every branch. Internet tests also run on scheduled pipelines. Configure the weekly schedule in GitLab under Build > Pipeline schedules with cron `0 3 * * 0`.
- GitHub Actions `.github/workflows/ci.yml` runs the main Java 21/Groovy 5 build and internet tests on every push. `.github/workflows/ci-extensive.yml` runs the Java 8/Groovy 4 and Java 21/Groovy 5 matrices on Ubuntu, Windows, and macOS weekly (`0 3 * * 0`) or manually.
- The Java 8 macOS matrix entry uses the Intel `macos-15-intel` runner because the ARM64 `macos-latest` runner does not provide a matching Temurin JDK 8 distribution.
- Build artifacts are produced under `build/`; local install artifacts use `install/`.

## Conventions

- Keep production code under `src/main/groovy` and tests under `src/test/groovy`.
- Preserve the Java 11 source/target compatibility unless changing the project baseline deliberately.
- Do not commit generated output, local `install/` contents, or environment-specific configuration.
- Avoid logging certificate passwords or private certificate material.
