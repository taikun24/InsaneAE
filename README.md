# InsaneAE

> More Storage, Better Gameplay...?

Applied Energistics 2 / MEGA Cells の「その先」を足す Minecraft 1.20.1 (Forge) 用アドオンです。
MEGA Cells が 256M で止めているところから、1G 以上の容量・クラフト能力・電力を追加します。

## 必要環境

| | バージョン |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.20 以上 |
| [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) | 15.2.16 以上 (必須) |
| [MEGA Cells](https://github.com/62832/MEGACells) | 2.4.6 以上 (必須) |
| [Applied Mekanistics](https://github.com/ramidzkh/AppliedMekanistics) | 1.4 以上 (任意 / 化学物質セル用) |

AE2 の内部 (`BasicCellInventory`、`CraftingCPUCluster`、ツールチップ描画など) に Mixin で踏み込んでいるため、
AE2 のバージョン範囲は実際に検証した `[15.2.16,16)` に固定しています。

## 追加されるもの

- **ストレージセル** — アイテム / 液体 / 化学物質 (Applied Mekanistics 導入時) の 1G 〜 8E セル。
  同階層のポータブルセル、クリエイティブセルもあります。
- **クラフトストレージ** — 1G 〜 8E。AE2 のバイト表示が 32bit で溢れる問題を Mixin で回避しています。
- **クラフト協調処理ユニット** — 16x 〜 2G。AE2 の 16 スレッド上限を外し、1 ブロックで多数の並列クラフトを担当します。
- **Quantum CPU** — 大量クラフトを一括処理するための専用 CPU。専用の GUI 付き。
- **エネルギーセル** — Superdense の上に 13 階層 (Hyperdense 〜 Cosmic、最上段 約 703 京 AE)。
- **ソーラーパネル** — 4 階層。
- **改良型チャージャー** — 上位エネルギーセル / ポータブルセルを現実的な時間で充電できます。
- **加速カード** — Turbo (×8) / Overclock (×64) / Hypersonic (×512) / Warp (×4096)。
  カードの枚数ではなく機械側の速度値に倍率を掛けます。

## ビルド

```sh
./gradlew build
```

`build/libs/` に jar が生成されます。開発環境での起動は `./gradlew runClient` / `./gradlew runServer`。

## ライセンス

[LGPL-3.0](LICENSE) (LGPL-3.0 が参照する GPL-3.0 の全文は [LICENSE.GPL](LICENSE.GPL))
