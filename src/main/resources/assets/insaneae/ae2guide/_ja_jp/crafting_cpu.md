---
navigation:
  title: "クラフトCPUの部品"
  icon: 8e_crafting_storage
  parent: index.md
  position: 20
item_ids:
  - 1g_crafting_storage
  - 4g_crafting_storage
  - 16g_crafting_storage
  - 64g_crafting_storage
  - 256g_crafting_storage
  - 1t_crafting_storage
  - 4t_crafting_storage
  - 16t_crafting_storage
  - 64t_crafting_storage
  - 256t_crafting_storage
  - 1p_crafting_storage
  - 4p_crafting_storage
  - 16p_crafting_storage
  - 64p_crafting_storage
  - 256p_crafting_storage
  - 1e_crafting_storage
  - 4e_crafting_storage
  - 8e_crafting_storage
  - 16x_crafting_accelerator
  - 64x_crafting_accelerator
  - 256x_crafting_accelerator
  - 1k_crafting_accelerator
  - 4k_crafting_accelerator
  - 16k_crafting_accelerator
  - 64k_crafting_accelerator
  - 256k_crafting_accelerator
  - 1m_crafting_accelerator
  - 4m_crafting_accelerator
  - 16m_crafting_accelerator
  - 64m_crafting_accelerator
  - 256m_crafting_accelerator
  - 1g_crafting_accelerator
  - 2g_crafting_accelerator
  - big_integer_cpu
---

# クラフトCPUの部品

## クラフトストレージ

<Row>
  <BlockImage id="1g_crafting_storage" scale="3" />
  <BlockImage id="1e_crafting_storage" scale="3" />
  <BlockImage id="8e_crafting_storage" scale="3" />
</Row>

MEGA Cells の 256M に続く、1G から 8E までのクラフトストレージです。1 段階ごとに容量が 4 倍になります。
8E は 1 個でクラフトCPU の容量の上限に達するため、1 つの CPU に組み込めるのは 8E なら 1 個、4E なら 2 個までとなります。

<RecipeFor id="1g_crafting_storage" />

## 並列クラフトユニット

<Row>
  <BlockImage id="16x_crafting_accelerator" scale="3" />
  <BlockImage id="2g_crafting_accelerator" scale="3" />
</Row>

16 倍から 2G 倍までの並列クラフトユニットです。AE2 の 16 スレッドの上限が撤廃されているため、1 個でその倍率に応じた並列クラフトを実行できます。
2G 倍が最上位です。

## BigInteger クラフトCPU

<ItemLink id="big_integer_cpu" /> は理論上の最大容量を持つクラフトストレージで、通常の AE2 クラフトCPU に組み込むことができます。
AE2 Crafting Optimizer (ACO) が導入されている場合は ACO が扱える上限、導入されていない場合は 8E 1 個分が容量になります。
サバイバルモードでのレシピはありません。