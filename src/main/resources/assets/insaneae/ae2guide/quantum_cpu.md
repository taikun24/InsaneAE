---
navigation:
  title: "Quantum CPU"
  icon: quantum_cpu
  parent: index.md
  position: 30
item_ids:
  - quantum_cpu
  - quantum_acceleration_card
  - task_fusion_card
---

# Quantum CPU

<BlockImage id="quantum_cpu" scale="4" />

A pattern provider and a molecular assembler in one block. It holds 1620 crafting patterns (30 pages of
54) and assembles them itself, returning the results to the network, instead of pushing them to an
assembler. It crafts 256 times per tick on its own.

Processing patterns are not accepted; put them in the <ItemLink id="insane_pattern_provider" />.

<RecipeFor id="quantum_cpu" />

## Cards

<ItemImage id="quantum_acceleration_card" scale="2" />

<ItemLink id="quantum_acceleration_card" /> multiplies the crafting speed by 256 per card. With 7 cards it reaches
the long limit (about 9.2E crafts per tick).

<ItemImage id="task_fusion_card" scale="2" />

With <ItemLink id="task_fusion_card" />, one bulk craft (one pattern × many crafts) uses a single operation of the
crafting CPU's per-tick budget, so the number of co-processing units no longer limits it.
