# MSX2CAS Project

## Objective

MSX2CAS is a Java application that converts MSX files to cassette audio format that can be played on MSX hardware. The application works by converting various MSX file formats (BAS, BIN, CAS, ROM, ASCII) into WAV audio files that emulate the cassette tapes used by MSX computers for loading programs. This enables users to load vintage MSX software onto actual MSX hardware using modern audio devices.

This core library is used in the Android app MSX2Cas available on Google Play, and was developed as a cross-platform solution (Windows/MacOS/Linux) using Java. The project was inspired by CasLink2 by Alexey Podrezov.

## Project Structure

The project is organized into two main modules:

### 1. msx2cas-core

This is the core library that handles file conversion functionality:

- `br.com.dod.vcas.VirtualCas`: The main class that orchestrates the conversion process
- `br.com.dod.vcas.model`: Contains enums for file types and sample rates
- `br.com.dod.vcas.wav`: Contains classes for different file format conversions:
  - `Ascii.java`: ASCII text file conversion
  - `Bas.java`: BASIC file conversion
  - `Bin.java`: Binary file conversion
  - `Cas.java`: CAS file conversion
  - `Rom.java`: ROM file conversion
  - `Wav.java`: WAV file output handling
- `br.com.dod.vcas.util`: Utility classes for file operations and WAV header creation
- `br.com.dod.vcas.exception`: Custom exceptions for error handling

### 2. msx2cas-cmd

This module provides the command-line interface for the core library:

- `br.com.dod.vcas.Main`: Entry point for the command-line application
- `br.com.dod.vcas.Params`: Command-line parameter handling
- `br.com.dod.vcas.ConvertFile`: File conversion orchestration

## Dependencies

- Java 17 or higher
- [dotnet-types](https://github.com/rfocosi/dotnet-types) - A custom library
- Maven for building (3.0 or higher recommended)

## How to Build

1. Ensure you have Java 17 JDK installed
2. Ensure you have Maven installed
3. Clone the required dependency repository:
   ```bash
   git clone https://github.com/rfocosi/dotnet-types
   ```
4. Build and install the dependency:
   ```bash
   cd dotnet-types
   mvn install
   ```
5. Return to the msx2cas-core directory and run the following command:
   ```bash
   mvn clean initialize && mvn package
   ```
6. Before running the application for the first time, execute the `FileEncodingTest.java` to generate comparable WAV files:
   ```bash
   # Navigate to the test directory
   cd msx2cas-core/src/test/java/br/com/dod/vcas
   # Run the test
   java FileEncodingTest
   ```

## How to Run

You can run the application in two ways:

### 1. Using JAR files

After building, you'll find the JAR files in the `target` directories. You can run the command-line application with:

```bash
java -jar msx2cas-cmd/target/msx2cas-cmd-*.jar [options] <input-file>
```

### 2. Using binary releases

Pre-compiled binary files are available from the [releases page](https://github.com/rfocosi/msx2cas-core/releases/).

### Command Line Options

The application supports several options:

- `-w`: Write to WAV file instead of playing audio
- `-r`: Reset ROM (applicable only for ROM files)
- Sample rates (default is 44100Hz):
  - `-11`: 11025Hz
  - `-22`: 22050Hz
  - `-44`: 44100Hz
  - `-48`: 48000Hz
  - `-96`: 96000Hz
- Waveform (default is Normal):
  - `-i`: Use inverted waveform

### Examples

Convert a BASIC file to audio and play it:
```bash
java -jar msx2cas-cmd.jar GAME.BAS
```

Convert a ROM file to a WAV file with reset enabled:
```bash
java -jar msx2cas-cmd.jar -w -r GAME.ROM
```

Convert a file using a different sample rate:
```bash
java -jar msx2cas-cmd.jar -44 -w GAME.BIN
```

## Playback Controls

When playing audio directly (without the `-w` option), the following keyboard controls are available:

- `SPACE`: Start/Pause playback
- `ESC`: Stop playback
- `HOME`: Restart playback
