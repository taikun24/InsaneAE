# ACO への PR — 別セッション用の引き継ぎ

> **状況 (2026-08-17): PR は出して、マージされた。**
> [PR #106](https://github.com/syarukasu/ae2-crafting-optimizer/pull/106)
> "fix: separate wide-plan and snapshot declines from capacity and ambiguity" が
> ACO の `main` に入った。ただし **まだリリースされていない** (CHANGELOG の
> `[Unreleased]`、直前のリリースは 1.6.2)。issue #103 も open のまま。
>
> 入ったもの:
> - `CPU_TOO_SMALL` を騙るのをやめ、「除外」として数えて必要 bytes / 空き bytes を
>   WARN する (`logWidePlanSubmissionDeclines`、既定 on) — **問題 1 の修正**
> - `NO_COMPILED_PROGRAM` を分割し、スナップショット起因に
>   `INCOMPLETE_GRAPH_SNAPSHOT` を新設。新設定
>   `retryIncompleteCraftingGraphSnapshot` (既定 on) が 1 世代 1 回だけ組み直す
>   — **問題 2 の修正**
>
> **残っていること:** これは「診断が正しくなった」であって「投入が通るようになった」
> とは限らない。下の再現テスト 2 本が緑になるかは未確認。`gradle.properties` は
> ACO を CurseForge のファイル ID で固定しているので、**リリースまで CI では確かめられない**。
> 手元で確かめるなら ACO の `main` をビルドしてローカル jar を差し込むこと。
>
> 以下は PR を書いたときの調査メモ。経緯として残してある。

AE2 Crafting Optimizer (ACO) 本体に PR を出したい。作者には Discord で連絡済み。

## リポジトリ

- `syarukasu/ae2-crafting-optimizer` — LGPL-3.0、fork 許可、ソース全公開 (Java)
- ビルドは **Java 17** で `gradlew clean build` (fresh clone から通ること、と CONTRIBUTING にある)
- `CONTRIBUTING.md` の PR 方針 (守ること):
  - レシピ・クラフト可否・ジョブ投入・ストレージ変更の正本は **AE2 のまま**にする
  - タイミング / 順序 / バッチングを変える挙動には **config スイッチを付ける**
  - 速い経路が正しさを証明できないときは **無改変の AE2 fallback を残す**
  - キャッシュは必ず上限を持たせ、無効化条件を書く
  - 任意 Mod への **hard class reference を共通初期化パスから出さない**
  - `docs/TESTING.md` に手動確認を足す
  - 性能変更には before/after の数字を付ける

## 最初にやること (これ次第で PR 不要になる)

**`main` を clone して、1.6 系でもう直っていないか確認する。**
リリース済みは v1.5.19 (2026-08-14) までだが、リポジトリのルートに
`RELEASE_NOTES_1.6.0.md` / `RELEASE_NOTES_1.6.1.md` がある = **1.6 系が未リリースで進行中**。
周辺が大きく変わっているかもしれない。

## 直したい不具合 (issue #103 に報告済み)

https://github.com/syarukasu/ae2-crafting-optimizer/issues/103

**long を超える要求 (wide plan) が投入できない。** 別々の 2 つが重なっている。

### 問題 1: `CPU_TOO_SMALL` が容量と無関係に返る

`CraftingCpuClusterBigCapacityGuardMixin` が

```
wide plan かつ (BigInteger サイドカーが無い or 外部コンシューマ未登録)
  → CraftingSubmitResult.CPU_TOO_SMALL
```

を返す。**蹴ること自体は fail-closed として妥当**だが、`CPU_TOO_SMALL` は
AE2 の「容量不足」コードなので、使う側はストレージを増やしに行ってしまう
(実際そうして何時間も溶かした)。しかも**完全に無言**でログにも出ない。

実測: 必要 bytes も CPU の空きも同じ `9223372036854775807` で、
AE2 自身の判定 (`CraftingCpuLogic`: `available < bytes` なら CPU_TOO_SMALL) は通っている。

**修正案**: 専用の理由コードか文言分け、decline reason を添える、WARN を 1 回出す。
→ **小さくて低リスク。まずこれから出すのがよい。**

### 問題 2: graph は `rootProgram` を返すのに planner は `NO_COMPILED_PROGRAM`

`BigIntegerPlanDiagnostics.summaryLines()` が `[BigInteger plan NO_COMPILED_PROGRAM: 4]`。
`Ae2AuthoritativeCraftingPlanner#tryPlanAttempt` を読む限りこの理由は

```java
Ae2CompiledCraftingGraphCache.getOrCompile(capture.grid(), capture.level())
    .rootProgram(what).isEmpty()
```

が真だった、という意味。**同じ呼び出しをこちらでやると present が返る**:

```
craftables=2
oak_button: patterns=1 oneFullyCompiled=true incomplete=false cyclic=false rootProgram=true
oak_planks: patterns=1 oneFullyCompiled=true incomplete=false cyclic=false rootProgram=true
```

注意: この覗き見は**サーバースレッドで、投入が断られた後**にやっている。
計算は別スレッドなので、そこの差かもしれない (60 tick 待っても結果は同じだった)。

**修正案**:
1. `NO_COMPILED_PROGRAM` を「構造的に組めない (曖昧・循環・不完全)」と
   「スナップショットが取れなかった / 世代が変わった」に**割る**。
   前者はプレイヤーが直せるが後者は直せない。同じ枝で
   `FallbackReasonCode.AMBIGUOUS_PRODUCER` も記録されるので、曖昧でなくても曖昧に読める。
2. 診断に**対象キーと `patternGeneration` / `recipeGeneration`** を入れる。
3. 空だったとき**もう一度だけ取り直してから諦める** (世代の揺れが原因ならこれで消える)。

**本丸なので、原因次第では設計判断が要る。作者の意向を聞いてから手を入れること。**

## 切り分け済み (同じ道を辿らないこと)

- **パターンの種類は無関係。** 同じ木を加工パターンで組んだ対照実験でも同じ症状。
  (`Ae2CompiledPatternFactory` が完全さを
  `IPatternDetails.supportsPushInputsToExternalInventory()` で決めているので
  「クラフトパターンだから不完全扱いでは」と疑ったが**外れ**。)
- **graph は壊れていない** (上の probe のとおり)。
- **容量不足ではない** (必要 bytes = CPU の空き)。
- **外部コンシューマ未登録でもない。** InsaneAE は
  `BigCraftingEngineApi.registerExternalBigIntegerPlanConsumer()` を呼んでおり、
  ログに `registered as an ACO external BigInteger plan consumer` が出る。
- long に収まる規模なら**同じ並びで普通に完走する**。
- 世代の揺れ (warm-up) 説も、60 tick 待って否定済み。

## 再現 (InsaneAE 側にゲームテストがある)

このリポジトリの `1.21.1` ブランチ (1.20.1 は `main` ブランチ)。

```
./gradlew runGameTestServer -PwithAco=true
```

- `insaneae_craft_past_long` — クラフトテーブル用パターン
- `insaneae_craft_past_long_processing` — 同じ木を加工パターンで組んだ対照実験

**この 2 本は意図的に赤のまま置いてある** (再現用)。ACO 側が直れば緑になるはず。
失敗メッセージに 必要 bytes / CPU の空き / `BigIntegerPlanDiagnostics.summaryLines()` /
compiled graph の中身 を全部出す。

構成は最小: クリエイティブセル (原木無限) + 容量 `Long.MAX_VALUE` のクラフトストレージ +
2 段のパターン (`1 原木 → 4 板材`、`1 板材 → 1 ボタン`) + ボタン `Long.MAX_VALUE` 個の要求。
2 段なので実行回数の合計が要求量の約 1.25 倍になり **long に収まらない** (= wide plan)。

**罠: `-PwithAco` (値なし) では ACO が載らない。** `build.gradle` の条件が `== 'true'` なので
空文字だと偽になり、**黙って ACO 無しで走って ACO 条件付きのテストが全部素通りする**。
必ず `-PwithAco=true`。`-PwithAdvancedAe=true` も同様。

## 関連 issue

| | 内容 | 状態 |
|---|---|---|
| #79 | 2^63 の窓の刻み | 1.5.18 で修正済み |
| #101 | BigInteger 在庫を名乗る公開フック (suggestion) | 未対応 |
| #102 | 1.5.19 の `round_robin` 退行 + 1.20.1 に `api.contract` が無い | 未対応 |
| #103 | **今回の本件** | 未対応 |

## 背景メモ

Claude のプロジェクトメモリ `aco-addon-integration-boundary.md` に、
ACO の連携境界・桁の上限が 2 つある件・「即時」経路が Advanced AE に縛られている件などがまとまっている
(場所はセッション開始時に読み込まれる `MEMORY.md` の索引を参照)。
