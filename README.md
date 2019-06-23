# msx2cas-core
Converts MSX files to cassette audio to play on MSX hardware.

It was developed in Java to make possible to run it on Windows/MacOS/Linux.

This core is used on Android app MSX2Cas:
[MSX2Cas on Google Play](https://play.google.com/store/apps/details?id=br.com.dod.msx2cas)

This project has as reference CasLink2 (Thanks Alexey Podrezov):

http://www.finnov.net/~wierzbowsky/caslink2.htm

## Binary files

You can just download the compiled files for use (to run its required Java 8):

[Binary Releases](https://github.com/rfocosi/msx2cas-core/releases/)

## Source requirements

- [dotnet-types](https://github.com/rfocosi/dotnet-types)

## Before run

Execute `FileEncodingTest.java` a first time, before editing source, to generate comparable WAV's

Compile with `mvn clean initialize && mvn package` at sources root
