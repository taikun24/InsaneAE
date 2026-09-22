package jp.main.taikun.insaneae.datagen;

import appeng.core.definitions.AEBlocks;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.TagKey;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.ConditionalRecipe;
import net.minecraftforge.common.crafting.DifferenceIngredient;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
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
            new ResourceLocation("ae2", "can_remove_color"));

    /**
     * その色の染料タグ。共通タグの名前空間は 1.20.1 では {@code forge}
     * (1.21 で {@code c} に変わっているので、あちらへ移植するときは直すこと)。
     */
    private static TagKey<Item> dyeTag(AEColor color) {
        return TagKey.create(Registries.ITEM,
                new ResourceLocation("forge", "dyes/" + color.dye.getName()));
    }

    public ModRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> consumer) {
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

        // 特大パターンプロバイダー: パターンプロバイダ + 集積プロセッサ 4 + 特異点 4。
        // 枠数が 45 倍 (36 → 1620) なので、他の限界突破ブロックと同じく特異点を要求する。
        shaped(consumer, ModBlocks.INSANE_PATTERN_PROVIDER.get(), ACCUMULATION_PROCESSOR,
                new String[]{"ABA", "BCB", "ABA"},
                Map.of(
                        'A', ACCUMULATION_PROCESSOR,
                        'B', SINGULARITY,
                        'C', AEBlocks.PATTERN_PROVIDER
                ));

        // 圧縮 ME 高密度スマートケーブル (fluix) ×4: 高密度スマートケーブル (fluix) 8 + 工学プロセッサ。
        // 本数は高密度のままで、細くなって部品が貼れるようになるだけなので、
        // 素材は AE2 の範囲 (工学プロセッサ) で収めてある。
        // 色付きは fluix から染めて作る (下の cableColoring)。
        shapedCount(consumer, ModParts.compressedCable(AEColor.TRANSPARENT), 4,
                AEItems.ENGINEERING_PROCESSOR,
                new String[]{"AAA", "ABA", "AAA"},
                Map.of(
                        'A', AEParts.SMART_DENSE_CABLE.item(AEColor.TRANSPARENT),
                        'B', AEItems.ENGINEERING_PROCESSOR
                ));

        // 超次元 ME ケーブル (fluix) ×4: 圧縮 ME 高密度スマートケーブル (fluix) 8 + 集積プロセッサ。
        // 「細くする」段 (上) と「32 本を超える」段 (ここ) の 2 段構え。
        shapedCount(consumer, ModParts.hyperCable(AEColor.TRANSPARENT), 4, ACCUMULATION_PROCESSOR,
                new String[]{"AAA", "ABA", "AAA"},
                Map.of(
                        'A', ModParts.compressedCable(AEColor.TRANSPARENT),
                        'B', ACCUMULATION_PROCESSOR
                ));

        cableColoring(consumer, ModParts::compressedCable,
                ModItemTagProvider.COMPRESSED_DENSE_CABLES, "compressed_dense_cable_clean");
        cableColoring(consumer, ModParts::hyperCable,
                ModItemTagProvider.HYPER_CABLES, "hyper_cable_clean");

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

        // 超特大インターフェイス: ME インターフェイス + 集積プロセッサ 4 + 特異点 4。
        // 1 枠 21 億 × 81 枠なので、他の限界突破ブロックと同じく特異点を要求する。
        shaped(consumer, ModBlocks.INSANE_INTERFACE.get(), ACCUMULATION_PROCESSOR,
                new String[]{"ABA", "BCB", "ABA"},
                Map.of(
                        'A', ACCUMULATION_PROCESSOR,
                        'B', SINGULARITY,
                        'C', AEBlocks.INTERFACE
                ));

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
        builder.requires(AEBlocks.CHEST)
                .requires(component)
                .requires(AEBlocks.DENSE_ENERGY_CELL)
                .requires(housing);
    }

    /** 素材を組み立てて shapeless レシピを 1 件出す (解禁条件は素材のコンポーネント)。 */
    static void shapeless(Consumer<FinishedRecipe> consumer, ItemLike result, ItemLike unlockedBy,
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
    static void convert(Consumer<FinishedRecipe> consumer, ItemLike block, ItemLike part) {
        convertOneWay(consumer, block, part);
        convertOneWay(consumer, part, block);
    }

    private static void convertOneWay(Consumer<FinishedRecipe> consumer, ItemLike from, ItemLike to) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(to.asItem()).withPrefix("convert/");
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, to)
                .requires(from)
                .unlockedBy("has_component", has(from))
                .save(consumer, id);
    }

    /**
     * ケーブルの<b>染色と色落とし</b>。AE2 のケーブルとまったく同じ形にしてある。
     *
     * <ul>
     *   <li>染色: <b>同じ種類のケーブル</b> 8 本で染料 1 個を囲んで、その色 8 本。
     *       材料をタグにしてあるので<b>どの色からでも直接塗り替えられる</b>
     *       (fluix に戻してから塗り直す必要がない)。結果の色ごとに 1 本ずつ要るので、
     *       ここだけは 16 本になる。</li>
     *   <li>色落とし: 色付き 1 本 + {@code ae2:can_remove_color} (水入りバケツなど) で fluix 1 本。
     *       材料は<b>「タグ - fluix」の差分</b> ({@link DifferenceIngredient}) なので、
     *       AE2 と同じく<b>全色ぶんで 1 本</b>で済む
     *       (fluix を除かないと「fluix → fluix」が無限に作れてしまう)。</li>
     * </ul>
     *
     * <p>色を変える経路はもう 1 つ、色塗り器 / ペイントボールがある
     * ({@code ThinDenseCablePart#changeColor})。そちらはクラフトを通らない。</p>
     *
     * @param cables   色 → そのケーブルのアイテム
     * @param cableTag そのケーブル 17 色をまとめたタグ ({@link ModItemTagProvider})
     * @param cleanId  色落としレシピの ID (ケーブルごとに分ける)
     */
    private static void cableColoring(Consumer<FinishedRecipe> consumer,
            java.util.function.Function<AEColor, ? extends ItemLike> cables,
            TagKey<Item> cableTag, String cleanId) {
        ItemLike fluix = cables.apply(AEColor.TRANSPARENT);
        for (AEColor color : AEColor.values()) {
            if (color == AEColor.TRANSPARENT) {
                continue;
            }
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, cables.apply(color), 8)
                    .pattern("aaa")
                    .pattern("aba")
                    .pattern("aaa")
                    .define('a', cableTag)
                    .define('b', dyeTag(color))
                    .unlockedBy("has_component", has(fluix))
                    .save(consumer);
        }

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, fluix)
                .requires(DifferenceIngredient.of(Ingredient.of(cableTag), Ingredient.of(fluix)))
                .requires(CAN_REMOVE_COLOR)
                .unlockedBy("has_component", has(fluix))
                .save(consumer, new ResourceLocation(InsaneAE.MODID, cleanId));
    }

    /** {@link #shaped} の、結果を複数個出す版。 */
    static void shapedCount(Consumer<FinishedRecipe> consumer, ItemLike result, int count,
            ItemLike unlockedBy, String[] pattern, Map<Character, ItemLike> ingredients) {
        ShapedRecipeBuilder builder = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result, count);
        for (String s : pattern) {
            builder.pattern(s);
        }
        for (Map.Entry<Character, ItemLike> entry : ingredients.entrySet()) {
            builder.define(entry.getKey(), entry.getValue());
        }
        builder.unlockedBy("has_component", has(unlockedBy)).save(consumer);
    }

    static void shaped(Consumer<FinishedRecipe> consumer, ItemLike result, ItemLike unlockedBy, String[] pattern, Map<Character, ItemLike> ingredients) {
        ShapedRecipeBuilder builder = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result);
        for (String s : pattern) {
            builder.pattern(s);
        }
        for (Map.Entry<Character, ItemLike> entry : ingredients.entrySet()) {
            builder.define(entry.getKey(), entry.getValue());
        }
        builder.unlockedBy("has_component", has(unlockedBy)).save(consumer);
    }

    /** appmek 導入時のみ有効な条件付きレシピを出す。 */
    static void conditionalShapeless(Consumer<FinishedRecipe> consumer, ItemLike result, ItemLike unlockedBy,
            Consumer<ShapelessRecipeBuilder> ingredients) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(result.asItem());
        ConditionalRecipe.builder()
                .addCondition(new ModLoadedCondition(InsaneAE.APPMEK_MODID))
                .addRecipe(wrapped -> {
                    ShapelessRecipeBuilder builder = ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, result);
                    ingredients.accept(builder);
                    builder.unlockedBy("has_component", has(unlockedBy)).save(wrapped, id);
                })
                .generateAdvancement()
                .build(consumer, id);
    }
}
