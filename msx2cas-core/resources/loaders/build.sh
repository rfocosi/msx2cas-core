#!/bin/bash

FILE=$1
BUILD_PATH=./build
TARGET_PATH=./target

[ ! -d $BUILD_PATH ] && mkdir -p $BUILD_PATH
[ ! -d $TARGET_PATH ] && mkdir -p $TARGET_PATH

rm -f $BUILD_PATH/*
rm -f $TARGET_PATH/*

echo "Compiling $BUILD_PATH/$FILE.rel ..."
[ -f "$FILE.mac" ] && sdasz80 -lso $BUILD_PATH/$FILE.rel $FILE.mac

echo "Linking file $BUILD_PATH/$FILE.rel"
[ -f "$BUILD_PATH/$FILE.rel" ] && sdcc --code-loc 0x9000 --data-loc 0 -mz80 --disable-warning 196 --no-std-crt0 $BUILD_PATH/$FILE.rel -o $BUILD_PATH/

echo "Copying file $BUILD_PATH/$FILE.ihx"
[ -f "$BUILD_PATH/$FILE.ihx" ] && objcopy -I ihex -O binary $BUILD_PATH/$FILE.ihx $TARGET_PATH/$FILE.bin

[ ! -f "$TARGET_PATH/$FILE.bin" ] && echo "Error building file: $FILE.mac"

if [ -f "$TARGET_PATH/$FILE.bin" ]; then
  echo "Done: $TARGET_PATH/$FILE.bin"
  echo
  cat $TARGET_PATH/$FILE.bin | hexdump -ve '/1 "0x%02X, "' | fold -s -w80
  echo
fi

