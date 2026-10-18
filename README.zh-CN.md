[日本語](README.md) | [English](README.en.md) | **中文**

# InsaneAE

> 更多存储，更好的游戏体验……？

这是一个 Minecraft 附属模组，旨在突破 Applied Energistics 2（AE2）和 MEGA Cells 的功能上限。

MEGA Cells 的存储容量上限为 256M，而 InsaneAE 在此基础上进一步扩展，提供从 1G 起步的存储容量、合成能力和能源容量。

## 支持的版本

不同 Minecraft 版本的代码分别维护在不同的分支中。**报告 Bug 时，请注明你正在使用的分支及对应版本。**

| 分支                                                           | Minecraft 版本 | 模组加载器    |
| ------------------------------------------------------------ | ------------ | -------- |
| [`main`](https://github.com/taikun24/InsaneAE/tree/main)     | 1.20.1       | Forge    |
| [`1.21.1`](https://github.com/taikun24/InsaneAE/tree/1.21.1) | 1.21.1       | NeoForge |

当前分支对应 **Minecraft 1.20.1（Forge）**。

## 环境要求

| 组件                                                                                            | 版本要求                                                             |
| --------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| Minecraft                                                                                     | 1.20.1                                                           |
| Forge                                                                                         | 47.4.20 或更高版本                                                    |
| [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)          | 15.4.10 或更高版本（必需）                                                |
| [MEGA Cells](https://github.com/62832/MEGACells)                                              | 2.4.6 或更高版本（必需）                                                  |
| [Applied Mekanistics](https://github.com/ramidzkh/AppliedMekanistics)                         | 1.4 或更高版本（可选，用于化学物质存储元件）                                         |
| [AE2 Crafting Optimizer](https://github.com/syarukasu/ae2-crafting-optimizer)                 | 1.5.12 或更高版本，**推荐使用 1.5.18 或更高版本**（可选，用于 BigInteger 账目管理和精确计算规划） |
| [Astral Mekanism & Energistics](https://www.curseforge.com/minecraft/mc-mods/astral-mekanism) | 1.8 或更高版本（可选，用于整合并批量自动输出至 ME 接口的功能）                              |

由于 InsaneAE 通过 Mixin 深入修改 AE2 的内部实现，包括 `BasicCellInventory`、`CraftingCPUCluster` 和工具提示渲染等，因此 AE2 的兼容版本范围被限定为经过实际测试的 `[15.4.10,16)`。

### AE2 Crafting Optimizer 联动说明

AE2 Crafting Optimizer（以下简称 ACO）并非必需依赖。

如果安装了 ACO，并启用了它的 BigInteger 后端，InsaneAE 会将 Quantum CPU 的待完成品账本和精确计算计划接入 ACO 的公开 API。

如果没有安装 ACO，或者在配置中禁用了相关功能，InsaneAE 会自动回退到内置的、功能等效的 BigInteger 账本。

只有在实际向 AE2 中插入某一批物品时，才会将该批次转换到安全的 `long` 数值范围内。在计算过程中，`times * outputCount` 不会被提前限制或截断到 `long` 的范围。

当 ACO 的计算配置文件 API 可用，且启用了 `enableInsaneAeBigCraftingProfile` 时，InsaneAE 会将 AE2 中需要精确 BigInteger 计算的部分交由 ACO 处理。

在这种情况下，InsaneAE 自身的计算分批机制不会重复介入同一计算过程，但 Quantum CPU 正常的执行分批机制仍然保留。

由于 ACO API 属于可选依赖，如果未安装 ACO、安装的是旧版本，或者相关配置未启用，InsaneAE 都会像以往一样回退到内置实现。

## 新增内容

### 1. 存储元件

* **存储元件（Storage Cells）**：提供容量从 1G 到 8E 的物品、流体和化学物质存储元件。
* 化学物质存储元件需要安装 Applied Mekanistics。
* 各容量等级均提供对应的便携式存储元件和创造模式存储元件。

### 2. 合成存储

* **合成存储（Crafting Storage）**：容量从 1G 到 8E。
* 通过 Mixin 修复 AE2 使用 32 位整数显示字节容量时可能出现的溢出问题。
* 当同一个合成 CPU 连接多个容量达到或超过 4E 的合成存储单元时，会先使用 BigInteger 重新计算总容量，再将结果转换为 `long`。
* 与 AE2 兼容的 `long` 类型容量接口会将超出范围的结果限制为 `Long.MAX_VALUE`。
* 其他模组可以通过 `IBigCraftingCapacity#insaneae$exactStorageCapacity()` 接口获取准确容量。

### 3. 合成协处理单元

* **合成协处理单元（Co-processing Units）**：倍率从 16x 到 2G。
* 解除 AE2 原本的 16 线程上限，使单个方块能够负责大量并行合成任务。

### 4. Quantum CPU（量子合成 CPU）

* 专门用于批量处理大规模合成任务的 CPU。
* 具有独立的图形用户界面（GUI）。

### 5. BigInteger 合成 CPU

* 一种能够融入 AE2 标准合成 CPU 结构、具有理论最大容量的合成存储组件。
* 它不是 Quantum CPU 的衍生型号，不具备独立 GUI、专用 ticker 或并行处理性能。
* 安装 ACO 后，会将 ACO 公开 API 所支持的容量上限作为精确容量使用；未安装 ACO 时，则回退到 `long` 类型的容量上限。
* 不提供专属纹理，也没有生存模式合成配方。

### 6. 能量元件

* **能量元件（Energy Cells）**：在 Superdense 等级之上新增 13 个等级，从 Hyperdense 一直到 Cosmic。
* 最高等级的能量元件可储存约 \(7.03 \times 10^{18}\) AE 的能量。

### 7. 太阳能板

* 新增 4 个等级的太阳能板。

| 类型         |  最大产能 |
| ---------- | ----: |
| AE太阳能板   |    2,047 AE/t |
| 高级AE太阳能板  |  8,388,607 AE/t |
| 精英AE太阳能板 |  34,359,738,367 AE/t |
| 终极AE太阳能板       | 140,737,488,355,327 AE/t |

### 8. 改良型晶体充能器

* **改良型晶体充能器（Improved Charger）**：能够在合理的时间内为高等级能量元件和便携式存储元件充能。
* 在1tick内进行1次充能并转化64个物品

### 9. 加速卡

提供以下四种加速卡：

| 类型         |  加速倍率 |
| ---------- | ----: |
| 涡轮加速卡      |    ×8 |
| 超频加速卡  |   ×64 |
| 高超音速加速卡 |  ×512 |
| 曲速加速卡       | ×4096 |

加速卡的倍率直接作用于机器自身的速度数值，而不是简单地根据插入的加速卡数量进行计算。

## 特别鸣谢

* **kaitsu**：提供了大量创意、测试验证和数值调整方面的建议。
* [**syarukasu**](https://github.com/syarukasu)：AE2 Crafting Optimizer 的开发者，为本模组的联动功能完善和验证了相关 API。

## 构建项目

执行以下命令：

```sh
./gradlew build
```

构建完成后，生成的 JAR 文件将位于 `build/libs/` 目录下，文件名格式为：

```text
insaneae-1.20.1-<version>.jar
```

其中 `<version>` 表示模组的版本号。

### 常用开发命令

| 命令                            | 功能                                    |
| ----------------------------- | ------------------------------------- |
| `./gradlew runClient`         | 启动开发环境中的 Minecraft 客户端                |
| `./gradlew runServer`         | 启动开发环境中的 Minecraft 服务端                |
| `./gradlew runData`           | 重新生成 `src/generated/resources` 中的数据资源 |
| `./gradlew runGameTestServer` | 在 AE2 的测试结构上运行相关验证                    |

## 开发说明

* 通常先在当前分支（`main`）上进行修复，然后使用 `git cherry-pick` 将修改移植到 `1.21.1` 分支。
* 两个分支中的 Mixin 和计算相关类保持一致，因此大多数情况下可以直接移植。
* Forge 在运行时仍然使用混淆后的代码，因此 Mixin 需要引用映射文件 `insaneae.refmap.json`。
* `1.21.1` 分支使用 NeoForge，并在运行时采用 Mojang 官方映射，因此不需要该引用映射文件。

## 许可证

本项目采用 [LGPL-3.0 许可证](LICENSE)。

LGPL-3.0 所引用的 GPL-3.0 完整许可证文本见 [LICENSE.GPL](LICENSE.GPL)。

使用、修改或分发本项目的代码时，请遵守相应的开源许可证条款。

