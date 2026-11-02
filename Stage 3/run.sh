#!/usr/bin/env bash
# 編譯並執行 Stage 3。
#   ./run.sh 狗                 搜尋一次
#   ./run.sh --tree 有狗的動畫電影   顯示每個網站的子網頁樹
#   ./run.sh                    互動模式
#   ./run.sh test               執行離線測試
set -e
cd "$(dirname "$0")"
rm -rf out
javac -encoding UTF-8 -d out src/*.java
if [ "$1" = "test" ]; then
  javac -encoding UTF-8 -cp out -d out test/*.java
  java -cp out Stage3Test
else
  java -cp out Main "$@"
fi
