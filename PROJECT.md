# MSX2CAS Project

## Objective

MSX2CAS is a Java 17+ application that converts MSX files (`ROM`, `MX1`, `MX2`, `BIN`, `LDR`, `CAS`, `BAS`, ASCII) into cassette audio (`WAV`) that can be played or saved for loading on actual MSX hardware. It also supports loading MegaROM images (> 49 KB) into MegaRAM cartridges (port `0x8E`) with automatic or explicit mapper patching (`konami4`, `konami5`, `ascii8`, `ascii16`).

This core library is used in the Android app MSX2Cas available on Google Play, and was developed as a cross-platform solution (Windows/macOS/Linux) using Java. The project was inspired by CasLink2 by Alexey Podrezov.

## Project Structure

The project is organized as a Maven multi-module build:

### 1. `msx2cas-core`

Core library handling file detection, conversion, MegaROM patching, CAS container encoding, and WAV waveform generation:

- `br.com.dod.vcas.VirtualCas`: Main orchestrator for converting files to WAV/audio
- `br.com.dod.vcas.cas`: CAS container reading/building (`CasFile`, `CasUtil`)
- `br.com.dod.vcas.converter`: Converter abstractions (`Converter`, `AbstractFileConverter`, `ConversionOptions`, `FileConverterFactory`, `DefaultFileConverterFactory`)
- `br.com.dod.vcas.converter.impl`: Format-specific converters and loaders:
  - `AsciiConverter`: ASCII text file conversion
  - `BasConverter`: Tokenized BASIC file conversion
  - `BinConverter`: Binary (`BIN` / `LDR`) file conversion
  - `CasConverter`: CAS file conversion
  - `RomConverter`: Standard ROM (16K/32K/49K) and MegaRAM conversion
  - `RomLoaders`: Z80 cassette/MegaRAM boot loaders
  - `MegaRomPatcher`: MegaROM mapper detection (`detectMapper`) and MegaRAM write patching
- `br.com.dod.vcas.model`: Domain enums (`FileType`, `MapperType`, `SampleRate`)
- `br.com.dod.vcas.wav`: Waveform encoding and output (`WavEncoder`, `WavOutput`)
- `br.com.dod.vcas.util`: Utilities (`FileCommons`, `WavHeader`)
- `br.com.dod.vcas.exception`: Custom exceptions (`FlowException`)

### 2. `msx2cas-cmd`

Command-line interface for the core library:

- `br.com.dod.vcas.Main`: Entry point, audio playback loop, and global keyboard hook (`JNativeHook`)
- `br.com.dod.vcas.Params`: Command-line argument parsing and usage output
- `br.com.dod.vcas.ConvertFile`: Per-file conversion parameters

## Dependencies

- Java 17 or higher (tested up to JDK 25)
- Maven 3.x
- `JNativeHook` (`com.1stleg:jnativehook`) for global playback hotkeys

## How to Build & Test

1. Ensure Java 17+ JDK and Maven 3.x are installed.
2. Build both modules and package the JAR, Linux `msx2cas` binary, Windows `msx2cas.exe`, and release archives:
   ```bash
   mvn clean package
   ```
3. Run unit tests:
   ```bash
   mvn test
   ```
4. Refresh or verify waveform regression fixtures:
   ```bash
   mvn -pl msx2cas-core -Dtest=FileEncodingTest test
   ```
5. Run openMSX integration tests (`*IT`, requires `openmsx` and system ROMs installed locally):
   ```bash
   mvn -pl msx2cas-core verify
   ```

## How to Run

### 1. Using JAR files

After building, run the shaded CLI JAR from `msx2cas-cmd/target/`:

```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-7.0.0-jar-with-dependencies.jar [options] <1200|2400|3000|3600> <input-file> [<input-file>...]
```

*(The JAR manifest includes `Enable-Native-Access: ALL-UNNAMED` so Java 24+ runs without `JNativeHook` native-access warnings.)*

### 2. Using binary releases

Pre-compiled binaries (`msx2cas` for Linux, `msx2cas.exe` for Windows) are built in `msx2cas-cmd/target/` and published on the [releases page](https://github.com/rfocosi/msx2cas-core/releases/).

### Command Line Options

- `<1200|2400|3000|3600>`: Playback speed in bps (**required**)
- `-i`: Invert waveform (fix playback on some soundcards)
- `-r`: Reset MSX after loading a ROM
- `-m`: Load MegaROM (> 49 KB) into MegaRAM cartridge (port `0x8E`)
- `-t <auto|konami4|konami5|ascii8|ascii16>`: Force MegaROM mapper type (implies `-m`)
- `-w [<output-file>]`: Write a WAV file instead of playing audio (uses `<output-file>` if provided for a single input, or the 6-character CAS token name)

### Examples

Convert a BASIC file to audio and play it at 2400 bps:
```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-7.0.0-jar-with-dependencies.jar 2400 GAME.BAS
```

Convert a ROM file to a WAV file with reset enabled at 3600 bps:
```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-7.0.0-jar-with-dependencies.jar -r -w 3600 GAME.ROM
```

Convert a MegaROM to WAV for MegaRAM with auto-detected mapper at 2400 bps:
```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-7.0.0-jar-with-dependencies.jar -m -w aleste.wav 2400 aleste.rom
```

Convert a MegaROM forcing `ascii16` mapper:
```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-7.0.0-jar-with-dependencies.jar -t ascii16 -w 2400 aleste.rom
```

## Playback Controls

When playing audio directly (without the `-w` option), the following global keyboard controls are available:

- `SPACE`: Start/Pause playback
- `ESC`: Stop playback
- `HOME`: Restart playback
