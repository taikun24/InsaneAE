[日本語](README.md) | **English** | [中文](README.zh-CN.md)

# InsaneAE

> More Storage, Better Gameplay...?

A Minecraft add-on that picks up where Applied Energistics 2 / MEGA Cells leave off.
MEGA Cells stops at 256M; InsaneAE adds storage, crafting power and energy from 1G upwards.

## Supported versions

Both builds are made from a single source tree with [Stonecutter](https://stonecutter.kikugie.dev/).
**When reporting a bug, please say which one you are using.**

| Minecraft | Loader | Jar |
|---|---|---|
| 1.20.1 | Forge | `insaneae-1.20.1-<version>.jar` |
| 1.21.1 | NeoForge | `insaneae-1.21.1-<version>.jar` |

## Requirements

| | 1.20.1 (Forge) | 1.21.1 (NeoForge) |
|---|---|---|
| Loader | Forge 47.4.20 or later | NeoForge 21.1.0 or later |
| [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) (required) | 15.2.15 or later | 19.2.15 or later |
| [MEGA Cells](https://github.com/62832/MEGACells) (required) | 2.4.6 or later | 4.11 or later |
| [Applied Mekanistics](https://github.com/ramidzkh/AppliedMekanistics) (optional, chemical cells) | 1.4 or later | 1.6 or later |
| [AppliedFlux](https://www.curseforge.com/minecraft/mc-mods/applied-flux) (optional, FE cells) | 1.20-1.3.7 or later | 1.21-2.1.6 or later |
| [AE2 Things](https://www.curseforge.com/minecraft/mc-mods/ae2-things-forge) (optional, DISK drives) | 1.2.1 or later | 1.4.2-beta or later |
| [AE2 Crafting Optimizer](https://github.com/syarukasu/ae2-crafting-optimizer) (optional, BigInteger accounting and exact planning) | 1.5.12 or later | 1.5.12 or later (**1.5.18+ recommended**) |
| [Astral Mekanism & Energistics](https://www.curseforge.com/minecraft/mc-mods/astral-mekanism) (optional, batches auto-ejection into ME interfaces) | 1.8 or later | — |

On 1.21.1, AE2 requires [GuideME](https://github.com/AppliedEnergistics/GuideME) as a hard dependency (on 1.20.1 it was bundled with AE2).

AE2 Crafting Optimizer is not required. When it is installed and its BigInteger backend is
enabled, the Quantum CPU's pending-output ledger is wired to ACO's public API. Without it — or
when it is disabled — InsaneAE falls back to its own equivalent BigInteger ledger. The on-disk
format is the same either way.

Because InsaneAE reaches into AE2 internals with Mixins (`BasicCellInventory`,
`CraftingCPUCluster`, tooltip rendering and more), the AE2 version range is pinned to
the range that has actually been tested (`[15.2.15,16)` on 1.20.1, `[19.2.15,20)` on 1.21.1).

### ACO integration (optional)

With the 1.21.1 build of [AE2 Crafting Optimizer](https://github.com/syarukasu/ae2-crafting-optimizer)
installed, the Quantum CPU accepts ACO's BigInteger plans and executes them by splitting the work
into long-range windows. Without ACO, or with the integration profile disabled, everything falls
back to AE2's normal calculation and execution path.

The integration never makes ACO a hard dependency. Do make sure both mods are the builds for your
NeoForge version.

## What it adds

- **Storage cells** — 1G to 8E cells for items, fluids, chemicals (needs Applied Mekanistics) and
  FE (needs AppliedFlux). Portable and creative cells exist at the same tiers. FE cells stop at
  16T, and the 16T one holds 8T: AppliedFlux computes capacity as bytes × "FE per byte" (default
  1,048,576) in a long, which 8T just fits (about 9.2 quintillion FE). Like AppliedFlux, they are
  built from dedicated Energy Storage Components (1G–16T, continuing from AppliedFlux's 256M).
  Except for FE cells, the contents live in the world (`data/insaneae_cells.dat`) keyed by UUID,
  and the item only keeps the UUID and a summary. A lost cell can be rebuilt with
  `/insaneae cells list` and `/insaneae cells recover <uuid>`.
- **ME DISK drives** (needs AE2 Things) — 1G to 8E, for items. No type limit, but one item takes
  one byte. The contents are stored in AE2 Things' own saved data.
- **Crafting storage** — 1G to 8E. A Mixin works around AE2 displaying byte counts as 32-bit.
  When several 4E-or-larger units share a CPU, the total is recomputed in BigInteger before being
  rounded to a long. The AE2-compatible long getter saturates at `Long.MAX_VALUE`, but other mods
  can read the exact figure from `IBigCraftingCapacity#insaneae$exactStorageCapacity()`.
- **Co-processing units** — 16x to 2G. Lifts AE2's 16-thread ceiling, so a single block can drive
  a large number of parallel crafts.
- **Quantum CPU** — a dedicated CPU for processing bulk crafts in one go, with its own GUI.
  It needs **Insane Processors**, made in the inscriber (press: accumulation press + quantum
  entangled singularity + logic press; circuit: a singularity; processor: an accumulation processor).
  AE2 19.x has no JEI support, so InsaneAE shows the inscriber and charger recipes in JEI itself
  (skipped when EMI is installed, which already shows them).
- **BigInteger crafting CPU** — crafting storage with a theoretical-maximum capacity that slots
  into a standard AE2 crafting CPU structure. It is not a Quantum CPU variant: no dedicated GUI,
  no ticker, no parallelism. With ACO installed it uses the public API's ceiling as its exact
  capacity; without ACO it falls back to the long ceiling. It has no dedicated texture and no
  survival recipe.
- **Energy cells** — 13 tiers above Superdense (Hyperdense through Cosmic; the top tier holds
  roughly 7.03 × 10^18 AE).
- **Solar panels** — 4 tiers.
- **Improved charger** — charges high-tier energy cells and portable cells in a realistic amount
  of time.
- **Acceleration cards** — Turbo (×8), Overclock (×64), Hypersonic (×512) and Warp (×4096).
  They multiply the machine's own speed value rather than counting card copies.

## Configuration

`config/insaneae-common.toml` lets you toggle the performance features individually (all enabled
by default). Use it to narrow things down if something looks wrong.

| Option | Meaning |
|---|---|
| `batchCraftingCalculation` | Batch crafting calculations |
| `craftingBatchThreshold` | Minimum craft count before batching kicks in |
| `serverSidePatternPaging` | Split the Quantum CPU's pattern slots into pages server-side |

### Special thanks
- kaitsu — for a great many ideas, test runs and tuning suggestions.
- [syarukasu](https://github.com/syarukasu) — author of AE2 Crafting Optimizer, who built and
  verified the APIs this integration relies on.

## Building

```sh
./gradlew build
```

The jars for both versions land in `versions/<version>/build/libs/`
(`versions/1.20.1-forge/` and `versions/1.21.1-neoforge/`).
To build just one, prefix the task: `./gradlew :1.21.1-neoforge:build`.
Gradle runs on Java 21; the Java 17 toolchain used for 1.20.1 is provisioned automatically.

| Command | What it does |
|---|---|
| `./gradlew :<version>:runClient` / `runServer` | Launch a development environment |
| `./gradlew :<version>:runData` | Regenerate `versions/<version>/src/generated/resources` |
| `./gradlew :<version>:runGameTestServer` | Run the checks that ride along on AE2's test plots |

## Development notes

- There is one source tree in `src/`, kept in its 1.21.1 state. Differences from 1.20.1 are written
  as conditional comments: `//? if >=1.21 { … //?} else { /* … *///?}` (`//? if neoforge {` works
  for loader-only differences). Packages that differ only in name between Forge and NeoForge are
  handled by the replacements in `stonecutter.gradle.kts`.
- Files that exist for only one version live under `versions/<version>/src/`, as do resources that
  differ per version (textures and so on).
- To edit the 1.20.1 side in the IDE, switch the active version with the Stonecutter Gradle tasks
  (the conditional comments in `src/` are rewritten for that version). Switch back to 1.21.1 before committing.
- 1.20.1 ships with Forge's obfuscated names, so the jar carries a Mixin refmap (`insaneae.refmap.json`).
  1.21.1 uses official Mojang mappings at runtime and does not need one.

## License

[LGPL-3.0](LICENSE). The full text of GPL-3.0, which LGPL-3.0 refers to, is in [LICENSE.GPL](LICENSE.GPL).
