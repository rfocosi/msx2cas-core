# Repository Guidelines

## Project Structure & Module Organization
The root Maven pom aggregates the `msx2cas-core` library and the `msx2cas-cmd` CLI. Core sources live in `msx2cas-core/src/main/java/br/com/dod/vcas/**`, grouped into `converter`, `wav`, `model`, `util`, and `exception` packages. Tests sit in `msx2cas-core/src/test/java`, with `FileEncodingTest` guarding WAV parity. CLI entry points and option parsing stay inside `msx2cas-cmd/src/main/java`, while release descriptors reside in `src/assembly`.

## Build, Test, and Development Commands
- `mvn clean initialize && mvn package`: compiles both modules with Java 17, runs resource processing, and produces the library JARs plus the CLI assembly.  
- `mvn -pl msx2cas-core test` or `mvn -pl msx2cas-core -Dtest=FileEncodingTest test`: execute the test suite or just the waveform regression when validating audio changes.  
- `java -jar msx2cas-cmd/target/msx2cas-cmd-<version>.jar -w GAME.BAS`: converts a BASIC file to WAV; drop `-w` to stream audio to the sound card.  
Install the `dotnet-types` dependency via `mvn install` and keep `JAVA_HOME` pointed at a JDK 17+ runtime before running these commands.

## Coding Style & Naming Conventions
Follow idiomatic Java 17: 4-space indentation, braces on the declaration line, and UTF-8 encoding (as enforced by the compiler plugin). Classes use PascalCase, methods and fields use camelCase, and constants remain UPPER_SNAKE_CASE. Keep packages rooted at `br.com.dod.vcas` and mirror that structure on disk. Store conversion logic inside the corresponding `wav/*.java` class and limit helpers in other packages to reusable utilities to preserve the current SOLID layout.

## Testing Guidelines
Unit tests reside under `msx2cas-core/src/test/java`; new cases should use the `*Test` suffix and deterministic fixtures so WAV bytes compare cleanly. Run `mvn -pl msx2cas-core -Dtest=FileEncodingTest test` before editing waveform code to refresh reference artifacts, then rerun after modifications to catch accidental audio drift. Broader regressions should use `mvn -pl msx2cas-core test`, and CLI-affecting work should include documented manual command examples in the PR for verification.

## Commit & Pull Request Guidelines
Commits are short, imperative, and scoped (e.g., `Fix Linux executable`, `Upgrade to JDK 17 (#13)`), so follow that tone and reference related issues with `(#id)`. Avoid bundling unrelated changes and keep the diff focused on one converter or subsystem. Pull requests need a concise summary, confirmation that `mvn package` and the relevant tests passed, and evidence for user-visible changes (CLI output, WAV samples, or screenshots). Link dependent updates such as `dotnet-types` revisions so reviewers can rebuild the toolchain end-to-end.
