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

このブランチは **1.20.1 (Forge)** です。

## 必要環境

| | バージョン |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.20 以上 |
| [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) | 15.2.16 以上 (必須) |
| [MEGA Cells](https://github.com/62832/MEGACells) | 2.4.6 以上 (必須) |
| [Applied Mekanistics](https://github.com/ramidzkh/AppliedMekanistics) | 1.4 以上 (任意 / 化学物質セル用) |
| [AE2 Crafting Optimizer](https://github.com/syarukasu/ae2-crafting-optimizer) | 1.5.11 以上 (任意 / BigInteger量会計・厳密計算連携用) |

AE2 の内部 (`BasicCellInventory`、`CraftingCPUCluster`、ツールチップ描画など) に Mixin で踏み込んでいるため、
AE2 のバージョン範囲は実際に検証した `[15.2.16,16)` に固定しています。

AE2 Crafting Optimizer は必須ではありません。導入されていてBigIntegerバックエンドが有効な場合、
Quantum CPUの完成品待ち台帳と厳密な計算計画をACO公開APIへ接続します。ACOが無い場合や設定で
無効な場合は、InsaneAE内蔵の同じBigInteger台帳へ戻ります。AE2へ搬入する一回分だけを安全な
long窓へ変換し、計算中の `times * outputCount` をlongへクランプしません。

ACOの計算プロファイルAPIが利用可能で、`enableInsaneAeBigCraftingProfile` が有効なとき、
AE2の厳密なBigInteger計算境界をACOへ委譲します。InsaneAEの計算用バッチは同じ計算へ
重ねて介入せず、通常のQuantum CPU実行バッチはそのまま使用します。ACOのAPIは任意依存
なので、未導入・旧版・設定無効時は従来どおりInsaneAE内蔵経路へ戻ります。

## 追加されるもの

- **ストレージセル** — アイテム / 液体 / 化学物質 (Applied Mekanistics 導入時) の 1G 〜 8E セル。
  同階層のポータブルセル、クリエイティブセルもあります。
- **クラフトストレージ** — 1G 〜 8E。AE2 のバイト表示が 32bit で溢れる問題を Mixin で回避しています。
- **クラフト協調処理ユニット** — 16x 〜 2G。AE2 の 16 スレッド上限を外し、1 ブロックで多数の並列クラフトを担当します。
- **Quantum CPU** — 大量クラフトを一括処理するための専用 CPU。専用の GUI 付き。
- **BigInteger クラフト CPU** — ACO の BigInteger 計画を試す実験用 CPU。既存 Quantum CPU と同じ
  InsaneAE 実行経路を使い、専用テクスチャとサバイバルレシピは持ちません。
- **エネルギーセル** — Superdense の上に 13 階層 (Hyperdense 〜 Cosmic、最上段 約 703 京 AE)。
- **ソーラーパネル** — 4 階層。
- **改良型チャージャー** — 上位エネルギーセル / ポータブルセルを現実的な時間で充電できます。
- **加速カード** — Turbo (×8) / Overclock (×64) / Hypersonic (×512) / Warp (×4096)。
  カードの枚数ではなく機械側の速度値に倍率を掛けます。

## ビルド

```sh
./gradlew build
```

`build/libs/` に jar が生成されます (`insaneae-1.20.1-<version>.jar`)。

| コマンド | 内容 |
|---|---|
| `./gradlew runClient` / `runServer` | 開発環境で起動 |
| `./gradlew runData` | `src/generated/resources` の再生成 |
| `./gradlew runGameTestServer` | AE2 のテストプロットに相乗りした検証を実行 |

## 開発メモ

- 修正は原則このブランチ (`main`) で入れて `1.21.1` に `git cherry-pick` します。
  Mixin と計算まわりのクラスは両ブランチで内容が一致しているので、たいていそのまま通ります。
- Forge は実行時に難読化されたままなので Mixin の refmap (`insaneae.refmap.json`) が要ります。
  1.21.1 ブランチは NeoForge で実行時も Mojang 公式マッピングなので不要です。

## ライセンス

[LGPL-3.0](LICENSE) (LGPL-3.0 が参照する GPL-3.0 の全文は [LICENSE.GPL](LICENSE.GPL))
