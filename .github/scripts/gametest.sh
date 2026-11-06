#!/usr/bin/env bash
# 1 つのバージョンの GameTest を走らせ、落ちたテストの顔ぶれを既知の一覧と突き合わせる。
#
#   .github/scripts/gametest.sh <versions/ の下の名前 (例: 1.21.1-neoforge)>
#
# AE2 本体に元から落ちるテストがあるので、終了コードではなく
# 「落ちたテストが .github/known-test-failures/<名前>.txt に載っているか」で判定する。
set -uo pipefail

node="$1"
known=".github/known-test-failures/${node}.txt"
log="gametest-${node}.log"

# GameTestServer はワールドを作るので EULA への同意が要る (run/ は .gitignore)。
mkdir -p "versions/${node}/run"
echo "eula=true" > "versions/${node}/run/eula.txt"

./gradlew ":${node}:runGameTestServer" 2>&1 | tee "$log" || true

# サーバーがテストに到達せず落ちた場合を先に弾く (下の比較だと素通りしてしまう)。
if ! grep -qE 'required tests (passed|failed)' "$log"; then
  echo "::error::${node}: テストが実行されていません (サーバーが起動途中で落ちた可能性)"
  exit 1
fi

grep -oE 'LogTestReporter\]: [a-z0-9_.]+ failed!' "$log" \
  | sed -E 's/.*\]: //; s/ failed!$//' | sort -u > actual-failures.txt
# 行頭コメント・行末コメント・空行を落としてテスト名だけにする。
sed -E 's/#.*//; s/[[:space:]]+//g' "$known" | grep -vE '^$' | sort -u > expected-failures.txt

echo "--- ${node}: 落ちたテスト ---"
cat actual-failures.txt

# <b>完全一致では判定しない。</b>AE2 本体のテストにはタイミング依存で結果が変わるものがあり、
# 完全一致にすると「想定より良い結果」でビルドが落ちる。落とすのは新しく落ちたテストがあるときだけ。
comm -13 expected-failures.txt actual-failures.txt > new-failures.txt
comm -23 expected-failures.txt actual-failures.txt > now-passing.txt

if [ -s now-passing.txt ]; then
  echo "::warning::${node}: 許可リストにあるが今回は通ったテスト (環境差か、直ったならリストから消すこと):"
  cat now-passing.txt
fi
if [ -s new-failures.txt ]; then
  echo "::error::${node}: 新たに落ちたテストがあります:"
  cat new-failures.txt
  exit 1
fi
echo "${node}: 新規の失敗はありません。"
