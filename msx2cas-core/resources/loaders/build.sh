#!/bin/bash

[ -z "$1" ] && echo "Usage: `basename $0` <filename>" && exit 1

FILE=`echo $1 | sed 's/\.\w\+$//g'`
BUILD_PATH=./build
TARGET_PATH=./target

[ ! -d $BUILD_PATH ] && mkdir -p $BUILD_PATH
[ ! -d $TARGET_PATH ] && mkdir -p $TARGET_PATH

rm -f $BUILD_PATH/*
rm -f $TARGET_PATH/*

echo "Compiling $BUILD_PATH/$FILE.rel ..." && [ -f "$FILE.s" ] && sdasz80 -lso $BUILD_PATH/$FILE.rel $FILE.s

[ $? -eq 0 ] && [ -f "$BUILD_PATH/$FILE.rel" ] && echo "Linking file $BUILD_PATH/$FILE.rel" && sdcc --code-loc 0x9000 --data-loc 0 -mz80 --disable-warning 196 --no-std-crt0 $BUILD_PATH/$FILE.rel -o $BUILD_PATH/

[ -f "$BUILD_PATH/$FILE.ihx" ] && echo "Copying file $BUILD_PATH/$FILE.ihx" && objcopy -I ihex -O binary $BUILD_PATH/$FILE.ihx $TARGET_PATH/$FILE.bin

[ ! -f "$TARGET_PATH/$FILE.bin" ] && echo "Error building file: $FILE.s"

if [ -f "$TARGET_PATH/$FILE.bin" ]; then
  echo "Done: $TARGET_PATH/$FILE.bin"
  echo
  cat $TARGET_PATH/$FILE.bin | hexdump -ve '/1 "0x%02X, "' | fold -s -w80
  echo
fi
