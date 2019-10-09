# MSX2Cas Core loaders

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
