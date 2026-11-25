---
navigation:
  title: "ストレージセル"
  icon: item_storage_cell_8e
  parent: index.md
  position: 40
item_ids:
  - cell_component_1g
  - cell_component_4g
  - cell_component_16g
  - cell_component_64g
  - cell_component_256g
  - cell_component_1t
  - cell_component_4t
  - cell_component_16t
  - cell_component_64t
  - cell_component_256t
  - cell_component_1p
  - cell_component_4p
  - cell_component_16p
  - cell_component_64p
  - cell_component_256p
  - cell_component_1e
  - cell_component_4e
  - cell_component_8e
  - item_storage_cell_1g
  - item_storage_cell_4g
  - item_storage_cell_16g
  - item_storage_cell_64g
  - item_storage_cell_256g
  - item_storage_cell_1t
  - item_storage_cell_4t
  - item_storage_cell_16t
  - item_storage_cell_64t
  - item_storage_cell_256t
  - item_storage_cell_1p
  - item_storage_cell_4p
  - item_storage_cell_16p
  - item_storage_cell_64p
  - item_storage_cell_256p
  - item_storage_cell_1e
  - item_storage_cell_4e
  - item_storage_cell_8e
  - fluid_storage_cell_1g
  - fluid_storage_cell_4g
  - fluid_storage_cell_16g
  - fluid_storage_cell_64g
  - fluid_storage_cell_256g
  - fluid_storage_cell_1t
  - fluid_storage_cell_4t
  - fluid_storage_cell_16t
  - fluid_storage_cell_64t
  - fluid_storage_cell_256t
  - fluid_storage_cell_1p
  - fluid_storage_cell_4p
  - fluid_storage_cell_16p
  - fluid_storage_cell_64p
  - fluid_storage_cell_256p
  - fluid_storage_cell_1e
  - fluid_storage_cell_4e
  - fluid_storage_cell_8e
  - portable_item_cell_1g
  - portable_item_cell_4g
  - portable_item_cell_16g
  - portable_item_cell_64g
  - portable_item_cell_256g
  - portable_item_cell_1t
  - portable_item_cell_4t
  - portable_item_cell_16t
  - portable_item_cell_64t
  - portable_item_cell_256t
  - portable_item_cell_1p
  - portable_item_cell_4p
  - portable_item_cell_16p
  - portable_item_cell_64p
  - portable_item_cell_256p
  - portable_item_cell_1e
  - portable_item_cell_4e
  - portable_item_cell_8e
  - portable_fluid_cell_1g
  - portable_fluid_cell_4g
  - portable_fluid_cell_16g
  - portable_fluid_cell_64g
  - portable_fluid_cell_256g
  - portable_fluid_cell_1t
  - portable_fluid_cell_4t
  - portable_fluid_cell_16t
  - portable_fluid_cell_64t
  - portable_fluid_cell_256t
  - portable_fluid_cell_1p
  - portable_fluid_cell_4p
  - portable_fluid_cell_16p
  - portable_fluid_cell_64p
  - portable_fluid_cell_256p
  - portable_fluid_cell_1e
  - portable_fluid_cell_4e
  - portable_fluid_cell_8e
  - creative_cell
  - ultra_creative_cell
---

# ストレージセル

<Row>
  <ItemImage id="item_storage_cell_1g" scale="2" />
  <ItemImage id="fluid_storage_cell_1g" scale="2" />
  <ItemImage id="portable_item_cell_1g" scale="2" />
  <ItemImage id="item_storage_cell_8e" scale="2" />
</Row>

1G から 8E までのアイテム・液体セルおよび同等のポータブルセルが用意されています。Applied Mekanistics が導入されている場合は化学物質セル、
AppliedFlux が導入されている場合は FE セル（1G〜16T）も追加されます。コンポーネントは MEGA Cells と同様に、1 つ下の段階のパーツ 4 個で作成します。

セルの中身はアイテムではなくワールドに保存されます。セルを紛失した場合は、
`/insaneae cells list` で UUID を検索し、`/insaneae cells recover <uuid>` で復元することが可能です（UUID は F3 + H の詳細表示で確認できます）。

<RecipeFor id="cell_component_1g" />
<RecipeFor id="item_storage_cell_1g" />

## DISK

AE2 Things が導入されている場合は、アイテム用の ME DISK（1G〜8E）が追加されます。格納できる種類数の制限はありませんが、アイテム 1 個につき 1 バイトを消費します。

AE2 MEGA Things も導入されている場合は、液体用の ME DISK（1G〜16P）が追加されます。Applied Mekanistics も入っていれば化学物質用も追加されます。AE2 MEGA Things と同じく 1000 mB が 1 バイトです。16P だけは容量が約 8.19P バイトになります。

## クリエイティブセル

<ItemLink id="creative_cell" /> は、設定した中身を 1 種類につき 922 京個という無限の数量で供給します。
<ItemLink id="ultra_creative_cell" /> も同じですが、AE2 Crafting Optimizer が導入されていれば、これを超える量を使うクラフトにも材料を出せます。