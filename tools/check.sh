#!/usr/bin/env bash
# 両バージョンのビルドとテストを回し、結果の要約だけを表示する
# Gradleの全出力はbuild/check-*.logに残す。失敗時はそこから該当箇所だけ抜き出す
#
#   tools/check.sh                 compile + 単体テスト、両版
#   tools/check.sh compile         compileJava + compileTestJavaだけ
#   tools/check.sh gametest        GameTest。既知の失敗と突き合わせ
#   tools/check.sh test 1.20.1-forge版を絞る。複数可
#
# 既定は --offline。依存を取り直すときはONLINE=1を付ける
set -uo pipefail
cd "$(dirname "$0")/.."

mode="${1:-test}"; shift || true
nodes=("$@")
[ ${#nodes[@]} -eq 0 ] && nodes=(1.20.1-forge 1.21.1-neoforge)
offline=(--offline); [ "${ONLINE:-}" = 1 ] && offline=()

# javacとログの文字化け (Shift-JIS) を避けるため英語・UTF-8で動かす
export JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

mkdir -p build
start=$SECONDS

summarize_failure() {
  local log="$1"
  echo "--- 失敗 (全文: $log) ---"
  grep -nE 'error:|\berror\b.*\.java|FAILED|What went wrong|Caused by|Exception:|expected:|> Task .* FAILED|BUILD FAILED' "$log" \
    | grep -v 'Picked up JAVA_TOOL_OPTIONS' \
    | sed -E 's#[A-Za-z]:[\\/].*[\\/]versions[\\/][^\\/]+[\\/]build[\\/]generated[\\/]stonecutter[\\/]([a-z]+)[\\/]#src/\1/#; s#\\#/#g' \
    | head -60
  # 版ごとのクラスはversions/<版>/srcにあるので、src/ に無ければそちらを探す
  echo "(src/ に無いファイルは versions/<版>/src/ 側)"
}

test_counts() {
  local dir="versions/$1/build/test-results/test"
  [ -d "$dir" ] || return
  cat "$dir"/*.xml | grep -oE '<testsuite [^>]*' | awk -v n="$1" '
    { for (i = 1; i <= NF; i++) if (split($i, kv, "=") == 2) { gsub(/"/, "", kv[2]); c[kv[1]] += kv[2] } }
    END { printf "  %s: tests=%d failures=%d errors=%d skipped=%d\n", n, c["tests"], c["failures"], c["errors"], c["skipped"] }'
}

case "$mode" in
  compile|test)
    tasks=()
    for n in "${nodes[@]}"; do
      tasks+=(":$n:compileJava" ":$n:compileTestJava")
      [ "$mode" = test ] && tasks+=(":$n:test")
    done
    log="build/check-$mode.log"
    rc=0
    if ./gradlew "${tasks[@]}" "${offline[@]}" --console=plain >"$log" 2>&1; then
      echo "OK: $mode (${nodes[*]}) $((SECONDS - start))s"
      warn=$(grep -cE 'warning:' "$log")
      [ "$warn" -gt 0 ] && echo "  javac 警告 ${warn} 件 (詳細は $log)"
    else
      rc=1
      echo "NG: $mode (${nodes[*]}) $((SECONDS - start))s"
      summarize_failure "$log"
    fi
    if [ "$mode" = test ]; then for n in "${nodes[@]}"; do test_counts "$n"; done; fi
    exit $rc
    ;;
  gametest)
    rc=0
    for n in "${nodes[@]}"; do
      log="build/check-gametest-$n.log"
      if .github/scripts/gametest.sh "$n" >"$log" 2>&1; then r=OK; else r=NG; rc=1; fi
      echo "$r: gametest $n"
      # gametest.shの要約部分、落ちたテスト以降だけ出す。Gradle本体の出力は除く
      sed -n '/^--- .*落ちたテスト ---/,$p' "$log" | grep -v '^\[' | head -40
      [ $r = NG ] && ! grep -q '落ちたテスト' "$log" && summarize_failure "$log"
    done
    rm -f actual-failures.txt expected-failures.txt new-failures.txt now-passing.txt gametest-*.log
    echo "($((SECONDS - start))s)"
    exit $rc
    ;;
  *)
    echo "使い方: tools/check.sh [compile|test|gametest] [版...]" >&2; exit 2 ;;
esac
