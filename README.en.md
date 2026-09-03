[日本語](README.md) | **English**

# InsaneAE

> More Storage, Better Gameplay...?

A Minecraft add-on that picks up where Applied Energistics 2 / MEGA Cells leave off.
MEGA Cells stops at 256M; InsaneAE adds storage, crafting power and energy from 1G upwards.

## Supported versions

Each Minecraft version lives on its own branch. **When reporting a bug, please say which one you are using.**

| Branch | Minecraft | Loader |
|---|---|---|
| [`main`](https://github.com/taikun24/InsaneAE/tree/main) | 1.20.1 | Forge |
| [`1.21.1`](https://github.com/taikun24/InsaneAE/tree/1.21.1) | 1.21.1 | NeoForge |

This branch is **1.21.1 (NeoForge)**.

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.0 or later |
| [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) | 19.2.17 or later (required) |
| [MEGA Cells](https://github.com/62832/MEGACells) | 4.11 or later (required) |
| [Applied Mekanistics](https://github.com/ramidzkh/AppliedMekanistics) | 1.6 or later (optional, for chemical cells) |
| [AE2 Crafting Optimizer](https://github.com/syarukasu/ae2-crafting-optimizer) | 1.5.12 or later, **1.5.18+ recommended** (optional, for BigInteger accounting and exact planning) |

AE2 requires [GuideME](https://github.com/AppliedEnergistics/GuideME) as a hard dependency (on 1.20.1 it was bundled with AE2).

AE2 Crafting Optimizer is not required. When it is installed and its BigInteger backend is
enabled, the Quantum CPU's pending-output ledger is wired to ACO's public API. Without it — or
when it is disabled — InsaneAE falls back to its own equivalent BigInteger ledger. The on-disk
format is the same either way.

Because InsaneAE reaches into AE2 internals with Mixins (`BasicCellInventory`,
`CraftingCPUCluster`, tooltip rendering and more), the AE2 version range is pinned to
`[19.2.17,20)` — the range that has actually been tested.

### ACO integration (optional)

With the 1.21.1 build of [AE2 Crafting Optimizer](https://github.com/syarukasu/ae2-crafting-optimizer)
installed, the Quantum CPU accepts ACO's BigInteger plans and executes them by splitting the work
into long-range windows. Without ACO, or with the integration profile disabled, everything falls
back to AE2's normal calculation and execution path.

The integration never makes ACO a hard dependency. Do make sure both mods are the builds for your
NeoForge version.

## What it adds

- **Storage cells** — 1G to 8E cells for items, fluids and chemicals (chemicals need Applied
  Mekanistics). Portable and creative cells exist at the same tiers.
- **Crafting storage** — 1G to 8E. A Mixin works around AE2 displaying byte counts as 32-bit.
  When several 4E-or-larger units share a CPU, the total is recomputed in BigInteger before being
  rounded to a long. The AE2-compatible long getter saturates at `Long.MAX_VALUE`, but other mods
  can read the exact figure from `IBigCraftingCapacity#insaneae$exactStorageCapacity()`.
- **Co-processing units** — 16x to 2G. Lifts AE2's 16-thread ceiling, so a single block can drive
  a large number of parallel crafts.
- **Quantum CPU** — a dedicated CPU for processing bulk crafts in one go, with its own GUI.
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

The jar lands in `build/libs/` as `insaneae-1.21.1-<version>.jar`.

| Command | What it does |
|---|---|
| `./gradlew runClient` / `runServer` | Launch a development environment |
| `./gradlew runData` | Regenerate `src/generated/resources` |
| `./gradlew runGameTestServer` | Run the checks that ride along on AE2's test plots |

## Development notes

- Fixes normally land on `main` (1.20.1) and are `git cherry-pick`ed onto `1.21.1`. The Mixin and
  calculation classes are identical on both branches, so they usually apply cleanly.
- NeoForge uses official Mojang mappings at runtime, so no Mixin refmap is needed. (The 1.20.1
  branch does have an `insaneae.refmap.json`.)

## License

[LGPL-3.0](LICENSE). The full text of GPL-3.0, which LGPL-3.0 refers to, is in [LICENSE.GPL](LICENSE.GPL).
