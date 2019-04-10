# msx2cas-core
Converts MSX files to cassette audio to play on MSX hardware.

It was developed in Java to make possible to run it on Windows/MacOS/Linux.

This core is used on Android app MSX2Cas:
[MSX2Cas on Google Play](https://play.google.com/store/apps/details?id=br.com.dod.msx2cas)

This project has as reference CasLink2 (Thanks Alexey Podrezov):

http://www.finnov.net/~wierzbowsky/caslink2.htm

## Binary files

You can just download the compiled files for use (to run its required Java 7):

[msx2cas.zip](https://github.com/rfocosi/msx2cas-core/releases/download/v1.0.0/msx2cas.zip)

[msx2cas.tar.gz](https://github.com/rfocosi/msx2cas-core/releases/download/v1.0.0/msx2cas.tar.gz)

## Source requirements

- [dotnet-types](https://github.com/rfocosi/dotnet-types)

## Before run

Execute `FileEncodingTest.java` a first time, before editing source, to generate comparable WAV's

Compile with `mvn clean package` at sources root

## Contributing

1. Fork it ( https://github.com/rfocosi/msx2cas-core/fork )
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create a new Pull Request
