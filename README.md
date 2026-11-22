# msx2cas-core
Converts MSX files to cassette audio to play on MSX hardware.

It was developed in Java (now requires Java 17+) to make possible to run it on Windows/MacOS/Linux. For contributor workflow, see [Repository Guidelines](AGENTS.md).

This core is used on Android app MSX2Cas:
[MSX2Cas on Google Play](https://play.google.com/store/apps/details?id=br.com.dod.msx2cas)

This project has as reference CasLink2 (Thanks Alexey Podrezov):

http://www.finnov.net/~wierzbowsky/caslink2.htm

## Binary files

You can just download the compiled files for use (to run it requires Java 17):

[Binary Releases](https://github.com/rfocosi/msx2cas-core/releases/)

## Source requirements

- Java 17 or higher (tested up to JDK 25)
- Maven 3.x

## Building and testing

Compile and package all artifacts (`jar-with-dependencies`, Linux `msx2cas`, Windows `msx2cas.exe`, and release archives) from the repository root:

```bash
mvn clean package
```

To generate or validate waveform regression fixtures before/after editing audio code:

```bash
mvn -pl msx2cas-core -Dtest=FileEncodingTest test
```

## CLI usage

```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-8.0.0-jar-with-dependencies.jar [options] <input-file> [<input-file>...]
```

- `-i`, `--invert-waveform`: Invert waveform (fix playback on some soundcards)
- `-r`, `--reset`: Reset MSX after loading a ROM
- `-m`, `--megarom [<auto|konami4|konami5|ascii8|ascii16>]`: Load MegaROM (> 49 KB) into a MegaRAM cartridge (port `0x8E`); optionally force mapper type (default: `auto`)
- `-f`, `--turbo`, `--fast-load`: Enable Stage-2 Z80 PWM turbo loader for ROM and BIN files (cannot combine with `-c`/`--cas`)
- `-b`, `--bps <1200|2400|3000>`: Stage-1 / standard tape speed in bps (default: `2400`; preserves native timing for `.tsx` inputs when omitted)
- `-s`, `--sr <11025|22050|33075|44100|48000>`: PCM/WAV sample rate in Hz (default: `44100`; use `-s 33075` for fastest Stage-2 `-f`/`--turbo` WAV/playback speed)
- `-c`, `--cas [<output-file>]`: Write a CAS file (`<output-file>` or first 6 characters of `<input-file>`); cannot combine with `-f`/`--turbo`, `-t`/`--tsx`, or `-w`/`--wav`
- `-t`, `--tsx [<output-file>]`: Write a TSX (TZX 1.21 / `#4B`) file (`<output-file>` or first 6 characters of `<input-file>`); preserves native TSX timing when input is `.tsx` and `-b`/`--bps` is omitted; supports `-f`/`--turbo` Stage-2 `#11` encoding
- `-w`, `--wav [<output-file>]`: Write a WAV file (`<output-file>` or first 6 characters of `<input-file>`), instead of playing to the default sound interface (renders `#20` Stop-the-Tape markers as 4s silence)
