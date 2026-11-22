# GEMINI.md - msx2cas-core

## Project Overview
**msx2cas-core** is a Java 17+ application that converts MSX files (BAS, BIN, CAS, TSX, ROM, ASCII) into cassette audio format (WAV), CAS, or TSX (TZX 1.21 / `#4B`) tape images to be played on actual MSX hardware and emulators. It is a cross-platform solution (Windows, macOS, Linux) inspired by CasLink2.

The project is structured as a Maven multi-module project:
- `msx2cas-core`: The core library handling the conversion logic and waveform generation.
- `msx2cas-cmd`: The command-line interface (CLI) for user interaction, playback, and file output.

### Key Technologies
- **Java 17+**: Core language and runtime.
- **Maven**: Build and dependency management.
- **JNativeHook**: Used in the CLI for global keyboard shortcuts during playback.
- **Launch4j**: Used to generate Windows executables.
- **Apache Ant**: Used via Maven plugin to create self-executing Linux binaries.

## Building and Running

### Prerequisites
- JDK 17 or higher.
- Maven 3.x.

### Build Commands
To build the entire project and generate executables:
```bash
mvn clean package
```

### Running the CLI
After building, you can find the artifacts in `msx2cas-cmd/target/`:
- **JAR with dependencies**: `java -jar msx2cas-cmd/target/msx2cas-cmd-8.0.0-jar-with-dependencies.jar [options] <input-file>`
- **Linux Binary**: `./msx2cas-cmd/target/msx2cas`
- **Windows EXE**: `msx2cas-cmd/target/msx2cas.exe`

### CLI Options
- `-w`, `--wav [<output-file>]`: Write to WAV file instead of playing audio (renders `#20` Stop-the-Tape markers as 4s silence).
- `-t`, `--tsx [<output-file>]`: Write to TSX file (TZX 1.21 / `#4B`; preserves native timing on `.tsx` input when `-b`/`--bps` is omitted; cannot combine with `-w`/`--wav` or `-c`/`--cas`).
- `-c`, `--cas [<output-file>]`: Write to CAS file (cannot combine with `-f`/`--turbo`, `-t`/`--tsx`, or `-w`/`--wav`).
- `-f`, `--turbo`, `--fast-load`: Enable Stage-2 Z80 PWM turbo loader for ROM and BIN files (cannot combine with `-c`/`--cas`).
- `-b`, `--bps <1200|2400|3000>`: Stage-1 / standard tape speed in bps (default: `2400`; preserves native timing for `.tsx` inputs when omitted).
- `-s`, `--sr <11025|22050|44100|48000>`: Output WAV/audio PCM sample rate in Hz (default: `44100`).
- `-r`, `--reset`: Reset MSX after loading a ROM.
- `-m`, `--megarom [<auto|konami4|konami5|ascii8|ascii16>]`: Load MegaROM (> 49 KB) into MegaRAM cartridge (port `0x8E`), defaulting to `auto` or forcing the specified mapper type.
- `-i`, `--invert-waveform`: Inverted waveform (default is normal).

### Playback Controls (when streaming)
- `SPACE`: Start/Pause/Resume playback (also resumes after auto-pausing on a TSX `#20` Stop-the-Tape marker).
- `ESC`: Stop playback.
- `HOME`: Restart playback.

## Development Conventions

### Coding Style
- **Indentation**: 4 spaces.
- **Encoding**: UTF-8.
- **Naming**: `PascalCase` for classes, `camelCase` for methods/fields, `UPPER_SNAKE_CASE` for constants.
- **Architecture**: Format-specific conversion logic lives in `br.com.dod.vcas.converter.impl` (created via `FileConverterFactory`), unified tape IR lives in `br.com.dod.vcas.tape` (`TapeBlock`, `TapeImage`, `TapeBuilder`), TZX 1.21 / `#4B` serialization lives in `br.com.dod.vcas.tsx` (`TsxParser`, `TsxWriter`), MegaROM mapper detection and MegaRAM patching live in `MegaRomPatcher` (with `MapperType` in `br.com.dod.vcas.model`), 3.5 MHz T-state audio synthesis and WAV writing live in `br.com.dod.vcas.wav` (`TapePcmInputStream`, `WavWriter`), and `VirtualCas` acts as the main orchestrator.

### Testing Practices
- **Standard Tests**: Run `mvn test` (or `mvn -pl msx2cas-core test` for the core module).
- **Integration Tests**: Run `mvn -pl msx2cas-core verify` to execute `*IT` openMSX integration tests (requires `openmsx` and system ROMs).
- **Regression Testing**: `FileEncodingTest.java` is used to guard against waveform regressions. 
- **Workflow**: Run `mvn -pl msx2cas-core -Dtest=FileEncodingTest test` before editing waveform code to generate reference artifacts, and again after changes to verify parity.

### Guidelines
- Follow the [AGENTS.md](AGENTS.md) for detailed repository guidelines.
- Ensure `mvn package` passes before submitting any changes.
- Keep commits short, imperative, and scoped (e.g., `Fix Linux executable`, `Add ROM reset flag`).
