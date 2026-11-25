---
navigation:
  title: "Storage Cells"
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

# Storage Cells

<Row>
  <ItemImage id="item_storage_cell_1g" scale="2" />
  <ItemImage id="fluid_storage_cell_1g" scale="2" />
  <ItemImage id="portable_item_cell_1g" scale="2" />
  <ItemImage id="item_storage_cell_8e" scale="2" />
</Row>

Item and fluid cells from 1G to 8E, plus portable cells at the same tiers. With Applied Mekanistics
installed there are chemical cells, and with AppliedFlux FE cells (1G to 16T). Components follow the
MEGA Cells pattern: four of the tier below.

The contents of a cell are saved with the world, not in the item. A lost cell can be
rebuilt with `/insaneae cells list` and `/insaneae cells recover <uuid>`
(the cell's UUID shows in the advanced tooltip, F3+H).

<RecipeFor id="cell_component_1g" />
<RecipeFor id="item_storage_cell_1g" />

## DISK Drives

With AE2 Things installed there are ME DISK drives from 1G to 8E, for items. They have no type limit,
but each item takes one byte.

With AE2 MEGA Things installed there are also fluid DISK drives from 1G to 16P, and chemical DISK drives
when Applied Mekanistics is installed too. 1,000 mB takes one byte, as in AE2 MEGA Things. The 16P drive
holds about 8.19P bytes.

## Creative Cells

<ItemLink id="creative_cell" /> supplies the configured items endlessly, 9,223,372,036,854,775,807 of each.
<ItemLink id="ultra_creative_cell" /> works the same, and with AE2 Crafting Optimizer installed it can also supply
crafts that need more than that.
