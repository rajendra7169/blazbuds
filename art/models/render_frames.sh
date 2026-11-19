#!/bin/bash
# Renders the frames the app flips through: the lid opening, the earbuds rising, and a turn all the way round.
B=~/.local/share/blender/blender-4.2.23-linux-x64/blender
OUT=$1; SIZE=${2:-512}; mkdir -p "$OUT"
n=0
for lid in 0 15 30 45 60 75 88 100; do
  $B -b -P liberty4nc.py -- "$OUT" $SIZE $lid 0 "open_$(printf %02d $n)" 0 64 >/dev/null 2>&1; n=$((n+1))
done
for rise in 0.15 0.3 0.45 0.6; do
  $B -b -P liberty4nc.py -- "$OUT" $SIZE 100 $rise "open_$(printf %02d $n)" 0 64 >/dev/null 2>&1; n=$((n+1))
done
for i in $(seq 0 11); do
  $B -b -P liberty4nc.py -- "$OUT" $SIZE 100 0.6 "turn_$(printf %02d $i)" $((i * 30)) 64 >/dev/null 2>&1
done
echo done
