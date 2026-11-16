FROM ubuntu:24.04

ARG USER_UID=1000
ARG USER_GID=1000

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    HOME=/home/ubuntu \
    SDL_VIDEODRIVER=dummy \
    SDL_AUDIODRIVER=dummy \
    OPENMSX_SYSTEM_ROMS=/openmsx-roms

RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless \
        maven \
        openmsx \
    && rm -rf /var/lib/apt/lists/* \
    && if [ "$USER_GID" != "1000" ]; then groupmod -g "$USER_GID" ubuntu; fi \
    && if [ "$USER_UID" != "1000" ]; then usermod -u "$USER_UID" -g "$USER_GID" ubuntu; fi \
    && mkdir -p /home/ubuntu/.m2 /workspace \
    && chown -R ubuntu:ubuntu /home/ubuntu /workspace

USER ubuntu

WORKDIR /workspace

CMD ["mvn", "-pl", "msx2cas-core", "initialize", "test-compile", "failsafe:integration-test", "failsafe:verify"]
