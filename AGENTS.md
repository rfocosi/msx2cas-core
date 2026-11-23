# Repository Guidelines

## Project Structure & Module Organization
The root Maven pom aggregates the `msx2cas-core` library and the `msx2cas-cmd` CLI. Core sources live in `msx2cas-core/src/main/java/br/com/dod/vcas/**`, grouped into `converter`, `wav`, `model`, `util`, and `exception` packages. Tests sit in `msx2cas-core/src/test/java`, with `VirtualCasRegressionTest` and `TapePcmInputStreamTest` guarding conversion and waveform parity. CLI entry points and option parsing stay inside `msx2cas-cmd/src/main/java`, while release descriptors reside in `src/assembly`.

## Build, Test, and Development Commands
- `mvn clean package`: compiles both modules with Java 17, runs resource processing, and produces the library JARs plus the CLI assembly.  
- `mvn -pl msx2cas-core test`: execute the core unit test suite.  
- `java -jar msx2cas-cmd/target/msx2cas-cmd-<version>-jar-with-dependencies.jar -w -b 2400 GAME.BAS`: converts a BASIC file to WAV at 2400 bps; drop `-w` to stream audio to the sound card.  
Keep `JAVA_HOME` pointed at a JDK 17+ runtime before running these commands.

## Coding Style & Naming Conventions
Follow idiomatic Java 17: 4-space indentation, braces on the declaration line, and UTF-8 encoding (as enforced by the compiler plugin). Classes use PascalCase, methods and fields use camelCase, and constants remain UPPER_SNAKE_CASE. Keep packages rooted at `br.com.dod.vcas` and mirror that structure on disk. Store format-specific conversion logic inside `converter/impl/*Converter.java`, shared waveform encoding in `wav/WavEncoder.java`, and limit helpers in other packages to reusable utilities to preserve the current SOLID layout.

## Testing Guidelines
Unit tests reside under `msx2cas-core/src/test/java`; new cases should use the `*Test` suffix and deterministic fixtures so WAV bytes compare cleanly. Regressions should use `mvn -pl msx2cas-core test`, and CLI-affecting work should include documented manual command examples in the PR for verification.

## Commit & Pull Request Guidelines
Commits are short, imperative, and scoped (e.g., `Fix Linux executable`, `Upgrade to JDK 17 (#13)`), so follow that tone and reference related issues with `(#id)`. Avoid bundling unrelated changes and keep the diff focused on one converter or subsystem. Pull requests need a concise summary, confirmation that `mvn package` and the relevant tests passed, and evidence for user-visible changes (CLI output, WAV samples, or screenshots).
