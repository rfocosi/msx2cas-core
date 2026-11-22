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
java -jar msx2cas-cmd/target/msx2cas-cmd-8.0.0-jar-with-dependencies.jar [-i] [-r] [-m [<auto|konami4|konami5|ascii8|ascii16>]] [--turbo] [-s <11025|22050|44100|48000>] [-c [<output-file>] | -t [<output-file>] | -w [<output-file>]] [<1200|2400|3000>] <input-file> [<input-file>...]
```

- `-i`: Invert waveform (fix playback on some soundcards)
- `-r`: Reset MSX after loading a ROM
- `-m`: Load MegaROM (> 49 KB) into a MegaRAM cartridge (port `0x8E`); optionally force mapper type (`auto`, `konami4`, `konami5`, `ascii8`, `ascii16`; default: `auto`)
- `--turbo`: Enable Stage-2 Z80 PWM turbo loader for ROM and BIN files (cannot combine with `-c`)
- `-s`: PCM/WAV sample rate in Hz (`11025`, `22050`, `44100`, `48000`; default: `44100`)
- `-c`: Write a CAS file (`<output-file>` or first 6 characters of `<input-file>`); cannot combine with `--turbo`, `-t`, or `-w`
- `-t`: Write a TSX (TZX 1.21 / `#4B`) file (`<output-file>` or first 6 characters of `<input-file>`); preserves native TSX timing when input is `.tsx` and `<bps>` is omitted; supports `--turbo` Stage-2 `#11` encoding
- `-w`: Write a WAV file (`<output-file>` or first 6 characters of `<input-file>`), instead of playing to the default sound interface (renders `#20` Stop-the-Tape markers as 4s silence)
- `<1200|2400|3000>`: Stage-1 / standard tape speed in bps (default: `2400`; preserves native timing for `.tsx` inputs when omitted)
