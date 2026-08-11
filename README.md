# InsaneAE

> More Storage, Better Gameplay...?

Applied Energistics 2 / MEGA Cells の「その先」を足す Minecraft アドオンです。
MEGA Cells が 256M で止めているところから、1G 以上の容量・クラフト能力・電力を追加します。

## 対応バージョン

Minecraft のバージョンごとにブランチを分けています。**不具合を報告するときは、どちらを使っているか書いてください。**

| ブランチ | Minecraft | ローダー |
|---|---|---|
| [`main`](https://github.com/taikun24/InsaneAE/tree/main) | 1.20.1 | Forge |
| [`1.21.1`](https://github.com/taikun24/InsaneAE/tree/1.21.1) | 1.21.1 | NeoForge |

このブランチは **1.21.1 (NeoForge)** です。

## 必要環境

| | バージョン |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.0 以上 |
| [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) | 19.2.17 以上 (必須) |
| [MEGA Cells](https://github.com/62832/MEGACells) | 4.11 以上 (必須) |
| [Applied Mekanistics](https://github.com/ramidzkh/AppliedMekanistics) | 1.6 以上 (任意 / 化学物質セル用) |
| [AE2 Crafting Optimizer](https://github.com/syarukasu/ae2-crafting-optimizer) | 1.5.11 以上 (任意 / BigInteger量会計用) |

AE2 が [GuideME](https://github.com/AppliedEnergistics/GuideME) を必須依存として要求します (1.20.1 では AE2 に同梱されていました)。

AE2 Crafting Optimizer は必須ではありません。導入されていて BigInteger バックエンドが有効な場合、
Quantum CPU の完成品待ち台帳を ACO の公開 API へ接続します。無い場合や無効な場合は
InsaneAE 内蔵の同じ BigInteger 台帳を使います (保存形式はどちらでも共通)。

### ACO連携 (任意)

[AE2 Crafting Optimizer](https://github.com/syarukasu/ae2-crafting-optimizer) 1.21.1版が導入されている場合、
Quantum CPUはACOのBigInteger計画を受け取り、long範囲の実行ウィンドウへ分割して処理できます。
ACOが無い場合、または連携プロファイルが無効な場合は、通常のAE2計算・実行経路へ戻ります。

この連携はACOを必須依存にしません。両Modのバージョンと対応するNeoForge版を揃えてください。

AE2 の内部 (`BasicCellInventory`、`CraftingCPUCluster`、ツールチップ描画など) に Mixin で踏み込んでいるため、
AE2 のバージョン範囲は実際に検証した `[19.2.17,20)` に固定しています。

## 追加されるもの

- **ストレージセル** — アイテム / 液体 / 化学物質 (Applied Mekanistics 導入時) の 1G 〜 8E セル。
  同階層のポータブルセル、クリエイティブセルもあります。
- **クラフトストレージ** — 1G 〜 8E。AE2 のバイト表示が溢れる問題を Mixin で回避しています。
- **クラフト協調処理ユニット** — 16x 〜 2G。AE2 の 16 スレッド上限を外し、1 ブロックで多数の並列クラフトを担当します。
- **Quantum CPU** — 大量クラフトを一括処理するための専用 CPU。専用の GUI 付き。
- **エネルギーセル** — Superdense の上に 13 階層 (Hyperdense 〜 Cosmic、最上段 約 703 京 AE)。
- **ソーラーパネル** — 4 階層。
- **改良型チャージャー** — 上位エネルギーセル / ポータブルセルを現実的な時間で充電できます。
- **加速カード** — Turbo (×8) / Overclock (×64) / Hypersonic (×512) / Warp (×4096)。
  カードの枚数ではなく機械側の速度値に倍率を掛けます。

## 設定

`config/insaneae-common.toml` で軽量化まわりを個別に切り替えられます (既定はすべて有効)。
挙動がおかしいと感じたときの切り分けに使ってください。

| 項目 | 内容 |
|---|---|
| `batchCraftingCalculation` | クラフト計算のまとめ処理 |
| `craftingBatchThreshold` | まとめ処理を使う最小クラフト回数 |
| `serverSidePatternPaging` | Quantum CPU のパターン枠をサーバ側でページ分割する |

## ビルド

```sh
./gradlew build
```

`build/libs/` に jar が生成されます (`insaneae-1.21.1-<version>.jar`)。

| コマンド | 内容 |
|---|---|
| `./gradlew runClient` / `runServer` | 開発環境で起動 |
| `./gradlew runData` | `src/generated/resources` の再生成 |
| `./gradlew runGameTestServer` | AE2 のテストプロットに相乗りした検証を実行 |

## 開発メモ

- 修正は原則 `main` (1.20.1) で入れて `1.21.1` に `git cherry-pick` します。
  Mixin と計算まわりのクラスは両ブランチで内容が一致しているので、たいていそのまま通ります。
- NeoForge は実行時も Mojang 公式マッピングなので、Mixin の refmap は不要です
  (1.20.1 ブランチには `insaneae.refmap.json` があります)。

## ライセンス

[LGPL-3.0](LICENSE) (LGPL-3.0 が参照する GPL-3.0 の全文は [LICENSE.GPL](LICENSE.GPL))
