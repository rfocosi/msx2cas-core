# MSX2CAS Project

## Objective

MSX2CAS is a Java 17+ application that converts MSX files (`ROM`, `MX1`, `MX2`, `BIN`, `LDR`, `CAS`, `TSX`, `BAS`, ASCII) into cassette audio (`WAV`), `.cas`, or `.tsx` (TZX 1.21 / `#4B`) tape images to be played or saved for loading on actual MSX hardware and emulators. It also supports loading MegaROM images (> 49 KB) into MegaRAM cartridges (port `0x8E`) with automatic or explicit mapper patching (`konami4`, `konami5`, `ascii8`, `ascii16`).

This core library is used in the Android app MSX2Cas available on Google Play, and was developed as a cross-platform solution (Windows/macOS/Linux) using Java. The project was inspired by CasLink2 by Alexey Podrezov.

## Project Structure

The project is organized as a Maven multi-module build:

### 1. `msx2cas-core`

Core library handling file detection, conversion, MegaROM patching, CAS/TSX container reading & writing, unified tape IR, and T-state WAV waveform synthesis:

- `br.com.dod.vcas.VirtualCas`: Main orchestrator (`convertToTape`, `convertToCas`, `convertToTsx`, `convertToWav`, `openPcmStream`)
- `br.com.dod.vcas.tape`: Unified intermediate representation (`TapeBlock`, `TapeImage`, `TapeBuilder`)
- `br.com.dod.vcas.tsx`: TZX 1.21 / MSX `#4B` container reading and writing (`TsxParser`, `TsxWriter`, `TsxOutput`)
- `br.com.dod.vcas.cas`: CAS container reading/building (`CasFile`, `CasUtil`, `CasEncoder`)
- `br.com.dod.vcas.converter`: Converter abstractions (`Converter`, `AbstractFileConverter`, `ConversionOptions`, `FileConverterFactory`, `DefaultFileConverterFactory`)
- `br.com.dod.vcas.converter.impl`: Format-specific converters and loaders:
  - `AsciiConverter`: ASCII text file conversion
  - `BasConverter`: Tokenized BASIC file conversion
  - `BinConverter`: Binary (`BIN` / `LDR`) file conversion
  - `CasConverter`: CAS file conversion
  - `TsxConverter`: TSX file conversion (native T-state preservation or `#4B` re-timing)
  - `RomConverter`: Standard ROM (16K/32K/49K) and MegaRAM conversion
  - `RomLoaders`: Z80 cassette/MegaRAM boot loaders
  - `MegaRomPatcher`: MegaROM mapper detection (`detectMapper`) and MegaRAM write patching
- `br.com.dod.vcas.model`: Domain enums (`FileType`, `MapperType`, `SampleRate`, `TapeSpeed`)
- `br.com.dod.vcas.wav`: Unified 3.5 MHz T-state synthesis (`TapePcmInputStream`), WAV writer (`WavWriter`), and `WavOutput`
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
java -jar msx2cas-cmd/target/msx2cas-cmd-8.0.0-jar-with-dependencies.jar [options] [<1200|2400|3000|turbo>] <input-file> [<input-file>...]
```

*(The JAR manifest includes `Enable-Native-Access: ALL-UNNAMED` so Java 24+ runs without `JNativeHook` native-access warnings.)*

### 2. Using binary releases

Pre-compiled binaries (`msx2cas` for Linux, `msx2cas.exe` for Windows) are built in `msx2cas-cmd/target/` and published on the [releases page](https://github.com/rfocosi/msx2cas-core/releases/).

### Command Line Options

- `<1200|2400|3000|turbo>`: Playback speed in bps (optional with `-c`, `-t`, or `.tsx` inputs; `turbo` uses a 2400 bps MSX BIOS bootstrap + Stage-2 Z80 PWM loader in WAV/TSX and cannot combine with `-c`)
- `-i`: Invert waveform (fix playback on some soundcards)
- `-r`: Reset MSX after loading a ROM
- `-m [<auto|konami4|konami5|ascii8|ascii16>]`: Load MegaROM (> 49 KB) into MegaRAM cartridge (port `0x8E`), defaulting to `auto` or forcing the specified mapper type
- `-s <11025|22050|44100|48000>`: Output WAV/audio PCM sample rate in Hz (default: `44100`)
- `-c [<output-file>]`: Write a CAS file (`<bps>` is optional; fails if `.tsx` contains non-MSX-KCS blocks)
- `-t [<output-file>]`: Write a TSX (TZX 1.21 / `#4B`) file (`<bps>` defaults to `2400`, or preserves native timing on `.tsx` input when omitted)
- `-w [<output-file>]`: Write a WAV file instead of playing audio (uses `<output-file>` if provided for a single input, or the 6-character CAS token name; renders `#20` Stop-the-Tape markers as 4s silence)

### Examples

Convert a BASIC file to audio and play it at 2400 bps:
```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-8.0.0-jar-with-dependencies.jar 2400 GAME.BAS
```

Convert a ROM file to a WAV file with reset enabled in turbo mode:
```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-8.0.0-jar-with-dependencies.jar -r -w turbo GAME.ROM
```

Convert a ROM file to a TSX image in turbo mode:
```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-8.0.0-jar-with-dependencies.jar -r -t turbo GAME.ROM
```

Convert a TSX image to a 48000 Hz WAV file preserving native TSX timings:
```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-8.0.0-jar-with-dependencies.jar -s 48000 -w GAME.TSX
```

Convert a MegaROM to WAV for MegaRAM with auto-detected mapper at 2400 bps:
```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-8.0.0-jar-with-dependencies.jar -m -w aleste.wav 2400 aleste.rom
```

Convert a MegaROM forcing `ascii16` mapper:
```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-8.0.0-jar-with-dependencies.jar -m ascii16 -w 2400 aleste.rom
```

## Playback Controls

When playing audio directly (without `-w`, `-c`, or `-t`), the following global keyboard controls are available (live playback also auto-pauses at TSX `#20` Stop-the-Tape markers until `SPACE` is pressed):

- `SPACE`: Start/Pause/Resume playback
- `ESC`: Stop playback
- `HOME`: Restart playback
