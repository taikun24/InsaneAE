package jp.main.taikun.insaneae.datagen;

import appeng.block.crafting.AbstractCraftingUnitBlock;
import appeng.block.networking.EnergyCellBlock;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.crafting.InsaneAcceleratorType;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.energy.InsaneEnergyCellTier;
import jp.main.taikun.insaneae.energy.SolarPanelTier;
import jp.main.taikun.insaneae.registries.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * ブロックステートとブロックモデル。
 *
 * <p>{@code formed=false} は普通のキューブ、{@code formed=true} は
 * {@code BuiltInModelHooks} でマルチブロック用モデルに差し替えられるので
 * <b>中身が空のモデル ({@code {}}) を置くだけ</b>でよい (実ファイルが無いとモデル解決に失敗する)。</p>
 *
 * <p><b>どこまで datagen が持つか</b>:</p>
 * <ul>
 *   <li>ブロックステートは<b>全ブロックぶんここで生成する</b> (手書きしないこと。
 *       {@code src/generated/resources} に出たものが正)。</li>
 *   <li>ブロックモデルも基本はここで生成するが、<b>ソーラーパネル 4 種 /
 *       Quantum CPU / Improved Crystal Charger だけは手書き</b>
 *       ({@code src/main/resources/assets/insaneae/models/block/<id>.json}) を参照する
 *       → {@link #handwritten}。凝った形にしたいので JSON を直接触れるようにしてある。</li>
 *   <li>クラフトストレージ／協調処理ユニット／エネルギーセルのテクスチャは
 *       {@code tools/gen_*_textures.py} が階層の色から生成している。</li>
 * </ul>
 */
public class ModBlockStateProvider extends BlockStateProvider {

    /**
     * エネルギーセルの筐体テクスチャ (ベース + 階層色)。残量に依らず常に出る。
     * {@code tools/gen_energy_textures.py} が階層の色から生成している。
     */
    public static ResourceLocation energyCellTexture(InsaneEnergyCellTier tier) {
        return ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/energy/" + tier.id());
    }

    /** 同上、残量ぶんのセグメントだけを描いた発光レイヤ (透過あり)。 */
    public static ResourceLocation energyCellLightTexture(InsaneEnergyCellTier tier, int fullness) {
        return ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID,
                "block/energy/" + tier.id() + "_" + fullness + "_light");
    }

    /** {@code block/<id>_<残量>} というブロックモデル名。アイテムモデル側からも参照する。 */
    public static String energyCellModelPath(InsaneEnergyCellTier tier, int fullness) {
        return "block/" + tier.id() + "_" + fullness;
    }

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, InsaneAE.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // テクスチャは tools/gen_crafting_textures.py が階層の色から生成している
        // (モデル名とテクスチャ名が同じなので、生成先を変えたらここも合わせること)。
        for (InsaneCraftingUnitType tier : InsaneCraftingUnitType.values()) {
            // ModelProvider#getBuilder はパスに "/" を含むとフォルダを補完しないので、
            // "block/" から明示的に書く。
            String base = "block/crafting/" + tier.id() + "_storage";
            craftingUnit(ModBlocks.CRAFTING_STORAGE.get(tier).get(), base,
                    ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, base), tier.formedModel());
        }
        for (InsaneAcceleratorType tier : InsaneAcceleratorType.values()) {
            String base = "block/crafting/" + tier.id() + "_accelerator";
            craftingUnit(ModBlocks.CRAFTING_ACCELERATOR.get(tier).get(), base,
                    ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, base), tier.formedModel());
        }

        for (InsaneEnergyCellTier tier : InsaneEnergyCellTier.values()) {
            energyCell(tier);
        }

        // ここから下の 3 種はブロックモデルを<b>手書き</b>している ({@link #handwritten})。
        // datagen はブロックステートを出して手書きモデルを指すだけで、モデル本体は触らない。
        for (SolarPanelTier tier : SolarPanelTier.values()) {
            simpleBlock(ModBlocks.SOLAR_PANELS.get(tier).get(), handwritten(tier.id()));
        }
        simpleBlock(ModBlocks.QUANTUM_CPU.get(), handwritten("quantum_cpu"));
        simpleBlock(ModBlocks.IMPROVED_CHARGER.get(), handwritten("improved_charger"));

        // 超特大インターフェイスはただのキューブ。テクスチャは AE2 の ME インターフェイスの
        // 色相を回したもので、1.21.1 ブランチの tools/gen_interface_texture.py が生成している
        // (画像は版に依存しないので、生成済みの PNG をそのまま持ってきている)。
        simpleBlock(ModBlocks.INSANE_INTERFACE.get(), models().cubeAll("insane_interface",
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/insane_interface")));

        // 特大パターンプロバイダーも同様に、AE2 のパターンプロバイダの色相を回したキューブ。
        // tools/gen_pattern_provider_texture.py が生成している。
        simpleBlock(ModBlocks.INSANE_PATTERN_PROVIDER.get(), models().cubeAll("insane_pattern_provider",
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/insane_pattern_provider")));
    }

    /**
     * 手書きのブロックモデルを参照する。
     *
     * <p>実体は {@code src/main/resources/assets/insaneae/models/block/<id>.json}。
     * {@code runData} の引数に {@code --existing src/main/resources} があるので
     * ExistingFileHelper から見えており、<b>ファイルが無ければ datagen が落ちて気付ける</b>
     * (typo で真っ黒なモデルになるのを防ぐため、あえて存在チェック付きで参照している)。</p>
     *
     * <p>アイテムモデルは {@code ModItemModelProvider} が
     * 「このブロックモデルを親にするだけ」の JSON を出しているので、
     * ここを差し替えれば持ち手の見た目も一緒に変わる。</p>
     */
    private ModelFile handwritten(String id) {
        return models().getExistingFile(ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/" + id));
    }

    /**
     * formed / 未 formed の 2 状態を持つクラフトユニット 1 種ぶんを出す。
     *
     * <p>formed 側は実ファイルを出さない。{@link jp.main.taikun.insaneae.crafting.FormedModels}
     * のとおり ae2 名前空間の組み込みモデルに差し替わるので、JSON は読まれない。</p>
     */
    private void craftingUnit(net.minecraft.world.level.block.Block block, String base,
            ResourceLocation texture, ResourceLocation formedModel) {
        ModelFile unformed = models().cubeAll(base, texture);

        getVariantBuilder(block)
                .partialState().with(AbstractCraftingUnitBlock.FORMED, false)
                .modelForState().modelFile(unformed).addModel()
                .partialState().with(AbstractCraftingUnitBlock.FORMED, true)
                .modelForState().modelFile(new ModelFile.UncheckedModelFile(formedModel)).addModel();
    }

    /** 残量 0〜4 の 5 状態を持つエネルギーセル 1 種ぶんを出す。 */
    private void energyCell(InsaneEnergyCellTier tier) {
        var builder = getVariantBuilder(ModBlocks.ENERGY_CELLS.get(tier).get());
        for (int fullness = 0; fullness <= EnergyCellBlock.MAX_FULLNESS; fullness++) {
            builder.partialState().with(EnergyCellBlock.ENERGY_STORAGE, fullness)
                    .modelForState().modelFile(energyCellModel(tier, fullness)).addModel();
        }
    }

    /**
     * エネルギーセルのブロックモデル。<b>筐体キューブ + ひとまわり大きい発光キューブ</b>の
     * 2 要素で組む。
     *
     * <p>筐体側は「ベース + 階層色」を焼いた残量非依存のテクスチャで、発光側が残量ぶんの
     * セグメントだけを描いた透過テクスチャ。発光キューブの面には Forge の
     * {@code forge_data.block_light/sky_light} (= {@code emissivity}) を 15 で入れてあるので、
     * <b>暗所でも充電されている段が光る</b>。</p>
     *
     * <p>キューブを 0.01 だけ大きくしているのは Z ファイティング避け。透過を含むので
     * モデル全体の描画層は {@code cutout} にする必要がある (発光テクスチャは
     * 完全不透明か完全透明かの 2 値なので alpha test で足りる)。</p>
     */
    private ModelFile energyCellModel(InsaneEnergyCellTier tier, int fullness) {
        ResourceLocation base = energyCellTexture(tier);
        BlockModelBuilder model = models()
                .withExistingParent(energyCellModelPath(tier, fullness), "block/block")
                .renderType("minecraft:cutout")
                .texture("particle", base)
                .texture("all", base)
                .texture("light", energyCellLightTexture(tier, fullness));

        model.element().from(0, 0, 0).to(16, 16, 16)
                .allFaces((direction, face) -> face.texture("#all").cullface(direction)).end();
        model.element().from(-0.01F, -0.01F, -0.01F).to(16.01F, 16.01F, 16.01F)
                .shade(false)
                .allFaces((direction, face) -> face.texture("#light").emissivity(15, 15)).end();
        return model;
    }

}
