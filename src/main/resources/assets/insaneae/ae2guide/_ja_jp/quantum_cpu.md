---
navigation:
  title: "クァンタムCPU"
  icon: quantum_cpu
  parent: index.md
  position: 30
item_ids:
  - quantum_cpu
  - quantum_acceleration_card
  - task_fusion_card
---

# クァンタムCPU

<BlockImage id="quantum_cpu" scale="4" />

パターンプロバイダーと分子組立装置を 1 ブロックにまとめたものです。クラフトパターンを 1620 個 (54 個 × 30 ページ) 持ち、
組立装置に渡す代わりに自分で組み立てて、完成品をネットワークに戻します。素の速度は 1 tick に 256 クラフトです。

加工パターンは入れられません。<ItemLink id="insane_pattern_provider" /> に入れてください。

<RecipeFor id="quantum_cpu" />

## カード

<ItemImage id="quantum_acceleration_card" scale="2" />

<ItemLink id="quantum_acceleration_card" /> は 1 枚ごとにクラフト速度を 256 倍にします。7 枚で long の上限 (1 tick に約 9.2E クラフト) に届きます。

<ItemImage id="task_fusion_card" scale="2" />

<ItemLink id="task_fusion_card" /> を挿すと、まとめクラフト 1 回 (1 パターン × 多数) がクラフトCPU の 1 tick の予算を 1 操作しか使わなくなります。
並列クラフトユニットの数で速度が抑えられなくなります。
