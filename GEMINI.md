# GEMINI.md - msx2cas-core

## Project Overview
**msx2cas-core** is a Java 17+ application that converts MSX files (BAS, BIN, CAS, ROM, ASCII) into cassette audio format (WAV) to be played on actual MSX hardware. It is a cross-platform solution (Windows, macOS, Linux) inspired by CasLink2.

The project is structured as a Maven multi-module project:
- `msx2cas-core`: The core library handling the conversion logic and waveform generation.
- `msx2cas-cmd`: The command-line interface (CLI) for user interaction, playback, and file output.

### Key Technologies
- **Java 17+**: Core language and runtime.
- **Maven**: Build and dependency management.
- **dotnet-types**: A custom dependency (provided as a JAR in `msx2cas-core/resources/lib/`).
- **JNativeHook**: Used in the CLI for global keyboard shortcuts during playback.
- **Launch4j**: Used to generate Windows executables.
- **Apache Ant**: Used via Maven plugin to create self-executing Linux binaries.

## Building and Running

### Prerequisites
- JDK 17 or higher.
- Maven 3.x.

### Build Commands
To build the entire project, including installing the local `dotnet-types` dependency and generating executables:
```bash
mvn clean initialize && mvn package
```
*Note: The `initialize` phase is critical as it installs the local `dotnet-types.jar` into your local Maven repository.*

### Running the CLI
After building, you can find the artifacts in `msx2cas-cmd/target/`:
- **JAR with dependencies**: `java -jar msx2cas-cmd/target/msx2cas-cmd-4.0.0-jar-with-dependencies.jar [options] <input-file>`
- **Linux Binary**: `./msx2cas-cmd/target/msx2cas`
- **Windows EXE**: `msx2cas-cmd/target/msx2cas.exe`

### CLI Options
- `-w`: Write to WAV file instead of playing audio.
- `-r`: Reset ROM (applicable for ROM files).
- `-i`: Inverted waveform (default is normal).
- Sample Rate options: `-11` (11025Hz), `-22` (22050Hz), `-44` (44100Hz, default), `-48` (48000Hz), `-96` (96000Hz).

### Playback Controls (when streaming)
- `SPACE`: Start/Pause playback.
- `ESC`: Stop playback.
- `HOME`: Restart playback.

## Development Conventions

### Coding Style
- **Indentation**: 4 spaces.
- **Encoding**: UTF-8.
- **Naming**: `PascalCase` for classes, `camelCase` for methods/fields, `UPPER_SNAKE_CASE` for constants.
- **Architecture**: Logic is encapsulated in the `br.com.dod.vcas.wav` package for format-specific conversion. The `VirtualCas` class acts as the main orchestrator.

### Testing Practices
- **Standard Tests**: Run `mvn test` (or `mvn -pl msx2cas-core test` for the core module).
- **Regression Testing**: `FileEncodingTest.java` is used to guard against waveform regressions. 
- **Workflow**: Run `mvn -pl msx2cas-core -Dtest=FileEncodingTest test` before editing waveform code to generate reference artifacts, and again after changes to verify parity.

### Guidelines
- Follow the [AGENTS.md](AGENTS.md) for detailed repository guidelines.
- Ensure `mvn package` passes before submitting any changes.
- Keep commits short, imperative, and scoped (e.g., `Fix Linux executable`, `Add ROM reset flag`).
