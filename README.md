**日本語** | [English](README.en.md) | [中文](README.zh-CN.md)

# InsaneAE

> More Storage, Better Gameplay...?

Applied Energistics 2 / MEGA Cells の「その先」を足す Minecraft アドオンです。
MEGA Cells が 256M で止めているところから、1G 以上の容量・クラフト能力・電力を追加します。

## 対応バージョン

1 つのソースから、次の 2 つを作っています ([Stonecutter](https://stonecutter.kikugie.dev/) を使用)。
**不具合を報告するときは、どちらを使っているか書いてください。**

| Minecraft | ローダー | jar |
|---|---|---|
| 1.20.1 | Forge | `insaneae-1.20.1-<version>.jar` |
| 1.21.1 | NeoForge | `insaneae-1.21.1-<version>.jar` |

## 必要環境

| | 1.20.1 (Forge) | 1.21.1 (NeoForge) |
|---|---|---|
| ローダー | Forge 47.4.20 以上 | NeoForge 21.1.0 以上 |
| [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) (必須) | 15.2.15 以上 | 19.2.15 以上 |
| [MEGA Cells](https://github.com/62832/MEGACells) (必須) | 2.4.6 以上 | 4.11 以上 |
| [Applied Mekanistics](https://github.com/ramidzkh/AppliedMekanistics) (任意 / 化学物質セル用) | 1.4 以上 | 1.6 以上 |
| [AppliedFlux](https://www.curseforge.com/minecraft/mc-mods/applied-flux) (任意 / FE セル用) | 1.20-1.3.7 以上 | 1.21-2.1.6 以上 |
| [AE2 Things](https://www.curseforge.com/minecraft/mc-mods/ae2-things-forge) (任意 / DISK 用) | 1.2.1 以上 | 1.4.2-beta 以上 |
| [AE2 Crafting Optimizer](https://github.com/syarukasu/ae2-crafting-optimizer) (任意 / BigInteger量会計・厳密計算連携用) | 1.5.12 以上 | 1.5.12 以上 (**1.5.18 以上を推奨**) |
| [Astral Mekanism & Energistics](https://www.curseforge.com/minecraft/mc-mods/astral-mekanism) (任意 / ME インターフェイスへの自動搬出をまとめる連携用) | 1.8 以上 | — |

1.21.1 では、AE2 が [GuideME](https://github.com/AppliedEnergistics/GuideME) を必須依存として要求します (1.20.1 では AE2 に同梱されていました)。

AE2 Crafting Optimizer は必須ではありません。導入されていて BigInteger バックエンドが有効な場合、
Quantum CPU の完成品待ち台帳を ACO の公開 API へ接続します。無い場合や無効な場合は
InsaneAE 内蔵の同じ BigInteger 台帳を使います (保存形式はどちらでも共通)。

### ACO連携 (任意)

[AE2 Crafting Optimizer](https://github.com/syarukasu/ae2-crafting-optimizer) 1.21.1版が導入されている場合、
Quantum CPUはACOのBigInteger計画を受け取り、long範囲の実行ウィンドウへ分割して処理できます。
ACOが無い場合、または連携プロファイルが無効な場合は、通常のAE2計算・実行経路へ戻ります。

この連携はACOを必須依存にしません。両Modのバージョンと対応するNeoForge版を揃えてください。

AE2 の内部 (`BasicCellInventory`、`CraftingCPUCluster`、ツールチップ描画など) に Mixin で踏み込んでいるため、
AE2 のバージョン範囲は実際に検証した範囲 (1.20.1 は `[15.2.15,16)`、1.21.1 は `[19.2.15,20)`) に固定しています。

## 追加されるもの

- **ストレージセル** — アイテム / 液体 / 化学物質 (Applied Mekanistics 導入時) /
  FE (AppliedFlux 導入時) の 1G 〜 8E セル。同階層のポータブルセル、クリエイティブセルもあります。
  FE セルは AppliedFlux の容量計算 (バイト × 1 バイトあたりの FE、既定 1,048,576) が long なので
  16T までで、16T だけ容量は 8T です (既定設定でちょうど約 922 京 FE)。芯は AppliedFlux と同じく
  専用のエネルギーストレージコンポーネント (1G〜16T、AppliedFlux の 256M の続き) です。
  FE 以外のセルの中身はワールド (`data/insaneae_cells.dat`) に UUID ごとに保存し、アイテムには
  UUID と要約だけを持たせます。失ったセルは `/insaneae cells list` と
  `/insaneae cells recover <uuid>` で作り直せます。
- **ME DISK ドライブ** (AE2 Things 導入時) — アイテム用の 1G 〜 8E。型数の上限が無い代わりに
  アイテム 1 個 = 1 バイトです。中身は AE2 Things の保存データに入ります。
- **クラフトストレージ** — 1G 〜 8E。AE2 のバイト表示が 32bit で溢れる問題を Mixin で回避しています。
  同じCPUに複数の4E以上を接続した合計は、longへ丸める前にBigIntegerで再計算します。
  AE2互換のlong getterはLong.MAX_VALUEへ飽和しますが、連携Modは
  `IBigCraftingCapacity#insaneae$exactStorageCapacity()`から正確な容量を取得できます。
- **クラフト協調処理ユニット** — 16x 〜 2G。AE2 の 16 スレッド上限を外し、1 ブロックで多数の並列クラフトを担当します。
- **Quantum CPU** — 大量クラフトを一括処理するための専用 CPU。専用の GUI 付き。
  材料の **超次元プロセッサ** は刻印機で作ります (金型は集積回路の金型 + 量子もつれ特異点 +
  論理回路の金型、回路は特異点、プロセッサは集積プロセッサが芯)。AE2 19.x は JEI に対応して
  いないので、刻印機とチャージャーのレシピは InsaneAE が JEI に出します (EMI 導入時は EMI 側の表示に任せます)。
- **BigInteger クラフト CPU** — AE2標準クラフトCPU構造へ組み込む、理論上限容量の
  クラフトストレージです。Quantum CPU派生ではなく、専用GUI・ticker・並列性能を持ちません。
  ACO導入時は公開APIの上限を正確な容量として使い、未導入時はlong上限へ戻ります。
  専用テクスチャとサバイバルレシピは持ちません。
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

### スペシャルサンクス
- kaitsu さん — 様々なアイデアや検証、調整など多数の助言をいただきました。
- [syarukasu](https://github.com/syarukasu) さん — AE2 Crafting Optimizer の開発者。連携のためのAPIの整備や検証をしていただきました。

## ビルド

```sh
./gradlew build
```

両バージョンの jar が `versions/<バージョン>/build/libs/` に生成されます
(`versions/1.20.1-forge/` と `versions/1.21.1-neoforge/`)。
片方だけなら `./gradlew :1.21.1-neoforge:build` のようにバージョンを付けます。
Gradle は Java 21 で動きます (1.20.1 のコンパイルに使う Java 17 は自動で用意されます)。

| コマンド | 内容 |
|---|---|
| `./gradlew :<バージョン>:runClient` / `runServer` | 開発環境で起動 |
| `./gradlew :<バージョン>:runData` | `versions/<バージョン>/src/generated/resources` の再生成 |
| `./gradlew :<バージョン>:runGameTestServer` | AE2 のテストプロットに相乗りした検証を実行 |

## 開発メモ

- ソースは `src/` に 1 つだけあり、1.21.1 の状態で置いてあります。1.20.1 との違いは
  `//? if >=1.21 { … //?} else { /* … *///?}` の条件コメントで書きます
  (Forge / NeoForge の違いだけなら `//? if neoforge {` も使えます)。
  Forge と NeoForge でパッケージ名だけが違うものは `stonecutter.gradle.kts` の置き換えで吸収しています。
- 片方のバージョンにしか無いファイルは `versions/<バージョン>/src/` に置きます。
  テクスチャなどバージョンごとに違うリソースもこちらです。
- IDE で 1.20.1 側を編集したいときは、Gradle の Stonecutter のタスクで作業バージョンを切り替えます
  (`src/` の条件コメントがそのバージョンの状態に書き換わります)。コミットする前に 1.21.1 に戻してください。
- 1.20.1 は Forge の難読化名で配布されるので、Mixin の refmap (`insaneae.refmap.json`) が jar に入ります。
  1.21.1 は実行時も Mojang 公式マッピングなので不要です。

## ライセンス

[LGPL-3.0](LICENSE) (LGPL-3.0 が参照する GPL-3.0 の全文は [LICENSE.GPL](LICENSE.GPL))
