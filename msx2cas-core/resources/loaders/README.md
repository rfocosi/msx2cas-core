# MSX2Cas Core loaders

This folder contains MSX2Cas's binary loaders.

## Requirements

- [Docker](https://docs.docker.com/install/)
- [docker-compose](https://docs.docker.com/compose/install/)

## Description

This build uses docker image msx-sdcc-toolchain:

[https://hub.docker.com/r/rfocosi/msx-sdcc-toolchain](https://hub.docker.com/r/rfocosi/msx-sdcc-toolchain)

## How to use:

- Building:

```
./build.sh bin.s
```

- Clean:

```
docker-compose run --rm sdcc clean
```

## How to compile

```docker-compose run sdcc ./build.sh "rom16kR.s"```

The result will be printed on screen and could be used on the correspondent Java file.

Also, the binary will be generated on ./target folder.

