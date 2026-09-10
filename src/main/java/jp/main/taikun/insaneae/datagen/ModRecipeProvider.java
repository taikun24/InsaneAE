package jp.main.taikun.insaneae.datagen;

import java.util.List;
import java.util.Arrays;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.api.util.AEColor;
import gripe._90.megacells.definition.MEGABlocks;
import gripe._90.megacells.definition.MEGAItems;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.crafting.InsaneAcceleratorType;
import jp.main.taikun.insaneae.energy.InsaneEnergyCellTier;
import jp.main.taikun.insaneae.energy.SolarPanelTier;
import jp.main.taikun.insaneae.upgrade.InsaneSpeedCardType;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModItems;
import jp.main.taikun.insaneae.registries.ModParts;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import java.util.concurrent.CompletableFuture;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 全レシピの生成元。手書き JSON は置かず、ここから {@code src/generated/resources} に出力する。
 *
 * <p>生成: {@code ./gradlew runData}</p>
 *
 * <p>階層ごとに次の 8 種類 (化学物質系 2 種は Applied Mekanistics 導入時のみ有効な
 * 条件付きレシピ) を出す。レシピ ID は結果アイテムの登録名と同じになるため、
 * ポータブルセルの分解処理 ({@code getRecipeId()}) ともそのまま噛み合う。</p>
 */
public class ModRecipeProvider extends RecipeProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Item QUARTZ_VIBRANT_GLASS = AEBlocks.QUARTZ_VIBRANT_GLASS.asItem();
    private static final Item MATTER_BALL = AEItems.MATTER_BALL.asItem();
    private static final Item SINGULARITY = AEItems.SINGULARITY.asItem();
    private static final Item ACCUMULATION_PROCESSOR = MEGAItems.ACCUMULATION_PROCESSOR.asItem();

    /**
     * AE2 の「色を落とせるもの」タグ (水入りバケツ・雪玉など)。
     * AE2 自身がケーブルの色落としに使っているものをそのまま借りる。
     */
    private static final TagKey<Item> CAN_REMOVE_COLOR = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("ae2", "can_remove_color"));

    /**
     * その色の染料タグ。1.21 で共通タグの名前空間が {@code forge} から {@code c} に変わっているので、
     * <b>1.20.1 側へ移植するときはここを {@code forge} に直すこと</b>。
     */
    private static TagKey<Item> dyeTag(AEColor color) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "dyes/" + color.dye.getName()));
    }

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput consumer) {
        boolean appMek = ModList.get().isLoaded(InsaneAE.APPMEK_MODID);
        if (!appMek) {
            LOGGER.warn("Applied Mekanistics not present: 化学物質セルのレシピは生成されません。"
                    + " 完全なデータを作るには appmek を入れた状態で runData すること。");
        }

        InsaneCraftingUnitType[] tiers = InsaneCraftingUnitType.values();
        for (int index = 0; index < tiers.length; index++) {
            InsaneCraftingUnitType tier = tiers[index];
            ItemLike component = ModItems.CELL_COMPONENTS.get(tier).get();
            // 最下段のコンポーネントだけ MEGA Cells の 256M コンポーネントから作る。
            ItemLike lower = index == 0
                    ? MEGAItems.CELL_COMPONENT_256M
                    : ModItems.CELL_COMPONENTS.get(tiers[index - 1]).get();

            // セルコンポーネント: 下位 ×4
            // shapeless(consumer, component, lower, b -> b.requires(lower, 4));

            ItemLike baseMaterial = MATTER_BALL;
            if (tier.getStorageBytes() > InsaneCraftingUnitType.STORAGE_256G.getStorageBytes()){
                baseMaterial = SINGULARITY;
            }

            shaped(consumer, component, lower, new String[]{"ABA", "CDC", "ACA"},
                    Map.of(
                            'A', baseMaterial,
                            'B', ACCUMULATION_PROCESSOR,
                            'C', lower,
                            'D', QUARTZ_VIBRANT_GLASS
                    ));


            // クラフトストレージ: MEGA のクラフトユニット + コンポーネント
            shapeless(consumer, ModBlocks.CRAFTING_STORAGE.get(tier).get(), component,
                    b -> b.requires(MEGABlocks.MEGA_CRAFTING_UNIT).requires(component));

            // 通常セル: ハウジング + コンポーネント
            shapeless(consumer, ModCells.ITEM_CELLS.get(tier).get(), component,
                    b -> b.requires(MEGAItems.MEGA_ITEM_CELL_HOUSING).requires(component));
            shapeless(consumer, ModCells.FLUID_CELLS.get(tier).get(), component,
                    b -> b.requires(MEGAItems.MEGA_FLUID_CELL_HOUSING).requires(component));

            // 分解 (空のセルを右クリックでコンポーネント + ハウジングに戻す)。
            cellDisassembly(consumer, ModCells.ITEM_CELLS.get(tier).get(),
                    component, MEGAItems.MEGA_ITEM_CELL_HOUSING);
            cellDisassembly(consumer, ModCells.FLUID_CELLS.get(tier).get(),
                    component, MEGAItems.MEGA_FLUID_CELL_HOUSING);

            // ポータブルセル: ME チェスト + コンポーネント + 高密度エネルギーセル + ハウジング
            shapeless(consumer, ModCells.PORTABLE_ITEM_CELLS.get(tier).get(), component,
                    b -> portable(b, component, MEGAItems.MEGA_ITEM_CELL_HOUSING));
            shapeless(consumer, ModCells.PORTABLE_FLUID_CELLS.get(tier).get(), component,
                    b -> portable(b, component, MEGAItems.MEGA_FLUID_CELL_HOUSING));

            if (appMek) {
                AppMekRecipes.build(consumer, tier, component);
            }
        }

        // クラフト協調処理ユニット: 下位 ×4 (最下段は MEGA Cells の 4 スレッド版 ×4 = 16 スレッド)。
        // 最上段 2G だけはスレッド数が 2 倍しか増えないので下位 ×2。
        InsaneAcceleratorType[] accelerators = InsaneAcceleratorType.values();
        for (int index = 0; index < accelerators.length; index++) {
            InsaneAcceleratorType tier = accelerators[index];
            ItemLike lower = index == 0
                    ? MEGABlocks.CRAFTING_ACCELERATOR
                    : ModBlocks.CRAFTING_ACCELERATOR.get(accelerators[index - 1]).get();
            shapeless(consumer, ModBlocks.CRAFTING_ACCELERATOR.get(tier).get(), lower,
                    b -> b.requires(lower, tier.lowerCount()));
        }

        // 加速カード: 下位カード ×4 + 集積プロセッサ (最下段は AE2 の加速カード ×4)。
        InsaneSpeedCardType[] cards = InsaneSpeedCardType.values();
        for (int index = 0; index < cards.length; index++) {
            InsaneSpeedCardType card = cards[index];
            ItemLike lower = index == 0 ? AEItems.SPEED_CARD : cards[index - 1].item();
            shapeless(consumer, card.item(), lower,
                    b -> b.requires(lower, 4).requires(ACCUMULATION_PROCESSOR));
        }

        // Quantum CPU: パターンプロバイダ 1 + 分子組立装置 4 + 集積プロセッサ 4。
        shaped(consumer, ModBlocks.QUANTUM_CPU.get(), ACCUMULATION_PROCESSOR,
                new String[]{"ABA", "BCB", "ABA"},
                Map.of(
                        'A', ACCUMULATION_PROCESSOR,
                        'B', AEBlocks.MOLECULAR_ASSEMBLER,
                        'C', AEBlocks.PATTERN_PROVIDER
                ));

        // 超特大インターフェイス: ME インターフェイス + 集積プロセッサ 4 + 特異点 4。
        // 1 枠 21 億 × 81 枠なので、他の限界突破ブロックと同じく特異点を要求する。
        shaped(consumer, ModBlocks.INSANE_INTERFACE.get(), ACCUMULATION_PROCESSOR,
                new String[]{"ABA", "BCB", "ABA"},
                Map.of(
                        'A', ACCUMULATION_PROCESSOR,
                        'B', SINGULARITY,
                        'C', AEBlocks.INTERFACE
                ));

        // 特大パターンプロバイダー: パターンプロバイダ + 集積プロセッサ 4 + 特異点 4。
        // 枠数が 45 倍 (36 → 1620) なので、他の限界突破ブロックと同じく特異点を要求する。
        shaped(consumer, ModBlocks.INSANE_PATTERN_PROVIDER.get(), ACCUMULATION_PROCESSOR,
                new String[]{"ABA", "BCB", "ABA"},
                Map.of(
                        'A', ACCUMULATION_PROCESSOR,
                        'B', SINGULARITY,
                        'C', AEBlocks.PATTERN_PROVIDER
                ));

        // 超次元 ME ケーブル (fluix) ×4: 高密度スマートケーブル (fluix) 8 + 集積プロセッサ。
        // 高密度と同じ本数のまま「細くなって部品が貼れる」段と、そこから
        // 超次元 ME コントローラで 32 本を超える段の 2 段構えなので、素材は控えめにしてある。
        // 色付きは fluix から染めて作る (下の hyperCableColoring)。
        shapedCount(consumer, ModParts.hyperCable(AEColor.TRANSPARENT), 4, ACCUMULATION_PROCESSOR,
                new String[]{"AAA", "ABA", "AAA"},
                Map.of(
                        'A', AEParts.SMART_DENSE_CABLE.item(AEColor.TRANSPARENT),
                        'B', ACCUMULATION_PROCESSOR
                ));

        hyperCableColoring(consumer);

        // 超次元 ME コントローラ: AE2 のコントローラ + 集積プロセッサ 4 + 特異点 4。
        // 他の限界突破ブロックと同じ形。
        shaped(consumer, ModBlocks.HYPER_CONTROLLER.get(), ACCUMULATION_PROCESSOR,
                new String[]{"ABA", "BCB", "ABA"},
                Map.of(
                        'A', ACCUMULATION_PROCESSOR,
                        'B', SINGULARITY,
                        'C', AEBlocks.CONTROLLER
                ));

        // ケーブル版 (プレート) ⇔ ブロック版。AE2 の ME インターフェイス / パターンプロバイダと同じく
        // 1:1 で行き来できる。中身 (パターン・設定) は移らないので、空の状態で持ち替えること。
        convert(consumer, ModBlocks.INSANE_INTERFACE.get(), ModParts.INSANE_INTERFACE.get());
        convert(consumer, ModBlocks.INSANE_PATTERN_PROVIDER.get(),
                ModParts.INSANE_PATTERN_PROVIDER.get());

        // エネルギーセル: 下位セル ×8 + 集積プロセッサ (AE2/MEGA の Dense / Superdense と同じ形)。
        // 容量が 1 段 8 倍なので、材料 8 個ぶんの容量がそのまま 1 個に収まる。
        InsaneEnergyCellTier[] energyCells = InsaneEnergyCellTier.values();
        for (int index = 0; index < energyCells.length; index++) {
            InsaneEnergyCellTier tier = energyCells[index];
            ItemLike lower = index == 0
                    ? MEGABlocks.MEGA_ENERGY_CELL
                    : ModBlocks.ENERGY_CELLS.get(energyCells[index - 1]).get();
            shaped(consumer, ModBlocks.ENERGY_CELLS.get(tier).get(), lower,
                    new String[]{"AAA", "ABA", "AAA"},
                    Map.of('A', lower, 'B', ACCUMULATION_PROCESSOR));
        }

        // Improved Crystal Charger: AE2 のチャージャー + 集積プロセッサ + 帯電水晶。
        shaped(consumer, ModBlocks.IMPROVED_CHARGER.get(), ACCUMULATION_PROCESSOR,
                new String[]{"ABA", "BCB", "ABA"},
                Map.of(
                        'A', ACCUMULATION_PROCESSOR,
                        'B', AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED,
                        'C', AEBlocks.CHARGER
                ));

        // ソーラーパネル: 最下段だけ素材から、以降は下位 ×8 + 中央の「核」。
        // 発電量が 1 段で 256 倍 (2^8-1 → 2^32-1) 伸びるので、核も段ごとに重くしてある。
        SolarPanelTier[] panels = SolarPanelTier.values();
        ItemLike[] cores = {ModBlocks.ENERGY_CELLS.get(InsaneEnergyCellTier.DEGENERATE).get(), ModBlocks.ENERGY_CELLS.get(InsaneEnergyCellTier.QUASAR).get(), ModBlocks.ENERGY_CELLS.get(InsaneEnergyCellTier.GALACTIC).get()};
        for (int index = 0; index < panels.length; index++) {
            ItemLike panel = ModBlocks.SOLAR_PANELS.get(panels[index]).get();
            if (index == 0) {
                shaped(consumer, panel, AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED,
                        new String[]{"GGG", "PCP", "SSS"},
                        Map.of(
                                'G', AEBlocks.QUARTZ_GLASS,
                                'C', AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED,
                                'P', AEItems.ENGINEERING_PROCESSOR,
                                'S', AEBlocks.SMOOTH_SKY_STONE_BLOCK
                        ));
            } else {
                ItemLike lower = ModBlocks.SOLAR_PANELS.get(panels[index - 1]).get();
                shaped(consumer, panel, lower, new String[]{"AAA", "ABA", "AAA"},
                        Map.of('A', lower, 'B', cores[index - 1]));
            }
        }

        // Quantum CPU 用加速カード: 最上位の加速カード ×4 + 特異点。
        ItemLike topSpeedCard = cards[cards.length - 1].item();
        shapeless(consumer, ModUpgrades.QUANTUM_ACCELERATION_CARD.get(), topSpeedCard,
                b -> b.requires(topSpeedCard, 4).requires(SINGULARITY));

        // タスク統合カード: 加速カード ×2 + 特異点 ×2 (タスクを 1 つに融合するイメージ)。
        shapeless(consumer, ModUpgrades.TASK_FUSION_CARD.get(),
                ModUpgrades.QUANTUM_ACCELERATION_CARD.get(),
                b -> b.requires(ModUpgrades.QUANTUM_ACCELERATION_CARD.get(), 2)
                        .requires(SINGULARITY, 2));
    }

    private static void portable(ShapelessRecipeBuilder builder, ItemLike component, ItemLike housing) {
        builder.requires(AEBlocks.ME_CHEST)
                .requires(component)
                .requires(AEBlocks.DENSE_ENERGY_CELL)
                .requires(housing);
    }

    /** 素材を組み立てて shapeless レシピを 1 件出す (解禁条件は素材のコンポーネント)。 */
    static void shapeless(RecipeOutput consumer, ItemLike result, ItemLike unlockedBy,
            Consumer<ShapelessRecipeBuilder> ingredients) {
        ShapelessRecipeBuilder builder = ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, result);
        ingredients.accept(builder);
        builder.unlockedBy("has_component", has(unlockedBy)).save(consumer);
    }
    /**
     * ブロック版とケーブル版を 1:1 で行き来する shapeless レシピを<b>両方向</b>出す。
     *
     * <p>レシピ ID は結果アイテム名そのままにすると<b>本来の作成レシピと衝突する</b>
     * (ブロック版はここより上で 3x3 から作っている) ので、{@code convert/} を頭に付けて分ける。</p>
     */
    static void convert(RecipeOutput consumer, ItemLike block, ItemLike part) {
        convertOneWay(consumer, block, part);
        convertOneWay(consumer, part, block);
    }

    private static void convertOneWay(RecipeOutput consumer, ItemLike from, ItemLike to) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(to.asItem()).withPrefix("convert/");
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, to)
                .requires(from)
                .unlockedBy("has_component", has(from))
                .save(consumer, id);
    }

    /**
     * 超次元 ME ケーブルの<b>染色と色落とし</b>。AE2 のケーブルとまったく同じ形にしてある。
     *
     * <ul>
     *   <li>染色: fluix 8 本で染料 1 個を囲んで<b>その色 8 本</b>
     *       ({@code ae2:network/cables/smart_<色>} と同じ)。</li>
     *   <li>色落とし: 色付き 1 本 + {@code ae2:can_remove_color} (水入りバケツなど) で fluix 1 本。
     *       AE2 は「タグ - fluix」の差分材料 1 レシピで済ませているが、
     *       こちらは<b>色ごとに 1 レシピ</b>に分けてある。差分材料を使うには自前の
     *       アイテムタグが要り、そのためだけにタグプロバイダを足すほどではないため。
     *       レシピ ID は結果 (常に fluix) が同じでぶつかるので、色名で分けている。</li>
     * </ul>
     *
     * <p>色を変える経路はもう 1 つ、色塗り器 / ペイントボールがある
     * ({@code HyperCablePart#changeColor})。そちらはクラフトを通らない。</p>
     */
    private static void hyperCableColoring(RecipeOutput consumer) {
        ItemLike fluix = ModParts.hyperCable(AEColor.TRANSPARENT);
        for (AEColor color : AEColor.values()) {
            if (color == AEColor.TRANSPARENT) {
                continue;
            }
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModParts.hyperCable(color), 8)
                    .pattern("aaa")
                    .pattern("aba")
                    .pattern("aaa")
                    .define('a', fluix)
                    .define('b', dyeTag(color))
                    .unlockedBy("has_component", has(fluix))
                    .save(consumer);

            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, fluix)
                    .requires(ModParts.hyperCable(color))
                    .requires(CAN_REMOVE_COLOR)
                    .unlockedBy("has_component", has(fluix))
                    .save(consumer, ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID,
                            "hyper_cable_clean/" + color.registryPrefix));
        }
    }

    /** {@link #shaped} の、結果を複数個出す版。 */
    static void shapedCount(RecipeOutput consumer, ItemLike result, int count, ItemLike unlockedBy,
            String[] pattern, Map<Character, ItemLike> ingredients) {
        ShapedRecipeBuilder builder = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result, count);
        for (String s : pattern) {
            builder.pattern(s);
        }
        for (Map.Entry<Character, ItemLike> entry : ingredients.entrySet()) {
            builder.define(entry.getKey(), entry.getValue());
        }
        builder.unlockedBy("has_component", has(unlockedBy)).save(consumer);
    }

    static void shaped(RecipeOutput consumer, ItemLike result, ItemLike unlockedBy, String[] pattern, Map<Character, ItemLike> ingredients) {
        ShapedRecipeBuilder builder = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result);
        for (String s : pattern) {
            builder.pattern(s);
        }
        for (Map.Entry<Character, ItemLike> entry : ingredients.entrySet()) {
            builder.define(entry.getKey(), entry.getValue());
        }
        builder.unlockedBy("has_component", has(unlockedBy)).save(consumer);
    }

    /**
     * セルの分解レシピ。空のセルを右クリックすると、ここで指定したアイテムに戻る。
     *
     * <p>1.20.1 (AE2 15.2.16) では戻すアイテムを {@code BasicStorageCell} のコンストラクタに
     * 渡していたが、AE2 19.2 で {@code StorageCellDisassemblyRecipe} というデータ駆動レシピに
     * 変わったため、datagen 側で出す必要がある。これを出さないとセルを分解できなくなる。</p>
     */
    static void cellDisassembly(RecipeOutput consumer, ItemLike cell, ItemLike... results) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(cell.asItem()).withPrefix("cell_disassembly/");
        List<ItemStack> stacks = Arrays.stream(results).map(ItemStack::new).toList();
        // 分解レシピはクラフト台に出ないので進捗 (advancement) は付けない。
        consumer.accept(id, new StorageCellDisassemblyRecipe(cell.asItem(), stacks), null);
    }

    /** appmek 導入時のみ有効な条件付きレシピを出す。 */
    static void conditionalShapeless(RecipeOutput consumer, ItemLike result, ItemLike unlockedBy,
            Consumer<ShapelessRecipeBuilder> ingredients) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.asItem());
        // NeoForge では ConditionalRecipe のビルダーが廃止され、
        // RecipeOutput#withConditions で「条件つきの出力先」を作って普通に save する方式になった。
        // 進捗 (advancement) も条件つきで一緒に出るので generateAdvancement() 相当は不要。
        RecipeOutput conditional = consumer.withConditions(new ModLoadedCondition(InsaneAE.APPMEK_MODID));
        ShapelessRecipeBuilder builder = ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, result);
        ingredients.accept(builder);
        builder.unlockedBy("has_component", has(unlockedBy)).save(conditional, id);
    }
}
