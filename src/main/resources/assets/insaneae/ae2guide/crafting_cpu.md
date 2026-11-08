---
navigation:
  title: "Crafting CPU Parts"
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

# Crafting CPU Parts

## Crafting Storage

<Row>
  <BlockImage id="1g_crafting_storage" scale="3" />
  <BlockImage id="1e_crafting_storage" scale="3" />
  <BlockImage id="8e_crafting_storage" scale="3" />
</Row>

Crafting storage from 1G to 8E, continuing MEGA Cells' 256M. Each tier holds 4 times the one below.
A single 8E block reaches the long limit, so a CPU can hold one 8E or two 4E blocks at most.

<RecipeFor id="1g_crafting_storage" />

## Co-Processing Units

<Row>
  <BlockImage id="16x_crafting_accelerator" scale="3" />
  <BlockImage id="2g_crafting_accelerator" scale="3" />
</Row>

Co-processing units from 16× to 2G×. AE2's 16-thread ceiling is removed, so one block can run that
many crafts in parallel. 2G× is the int limit and the last tier.

## BigInteger Crafting CPU

<ItemLink id="big_integer_cpu" /> is crafting storage with a theoretical-maximum capacity. It joins a normal AE2
crafting CPU. With AE2 Crafting Optimizer installed, its capacity is the exact ceiling ACO can handle;
without it, the long limit. It has no survival recipe.
