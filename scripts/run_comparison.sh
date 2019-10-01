#!/usr/bin/env bash

set -o nounset

mkdir -p output
mkdir -p tmp

if ! [[ -f output/"$1".log ]]
then
  python compare_uat_and_prod.py "$1" > tmp/"$1".log 2>&1
  mv tmp/"$1".log output/"$1".log
fi
