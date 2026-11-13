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

パターンプロバイダーと分子組立装置の機能を 1 ブロックに統合したデバイスです。クラフトパターンを 1620 個（54 個 × 30 ページ）保持し、
組立装置へ渡すことなく自身でクラフトを組み立てて完成品をネットワークへ返還します。素の処理速度は 1 tick あたり 256 クラフトです。

加工パターンは格納できないため、<ItemLink id="insane_pattern_provider" /> を使用してください。

<RecipeFor id="quantum_cpu" />

## カード

<ItemImage id="quantum_acceleration_card" scale="2" />

<ItemLink id="quantum_acceleration_card" /> は、1 枚につきクラフト速度を 256 倍にします。7 枚装着することで long 型の上限（1 tick あたり約 9.2E クラフト）に到達します。

<ItemImage id="task_fusion_card" scale="2" />

<ItemLink id="task_fusion_card" /> を挿入すると、一括クラフト（1 つのパターンで大量生産する処理）において、クラフトCPU の 1 tick 分の処理コストが 1 操作分のみに軽減されます。
これにより、並列クラフトユニットの数量による速度制限を受けなくなります。