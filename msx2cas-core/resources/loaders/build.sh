#!/bin/bash

[ -z "$1" ] && echo "Usage: `basename $0` <filename>" && exit 1

FILE_SRC=$1
FILE=`echo $FILE_SRC | sed 's/\.\w\+$//g'`

TARGET_PATH=`dirname $( realpath $0 )`/target

docker-compose run --rm sdcc sdasm $FILE_SRC

if [ -f "$TARGET_PATH/$FILE.bin" ]; then
  echo
  cat $TARGET_PATH/$FILE.bin | hexdump -ve '/1 "0x%02X, "' | fold -s -w80
  echo
fi
