package jp.main.taikun.insaneae.datagen;

import appeng.api.orientation.BlockOrientation;
import appeng.block.crafting.AbstractCraftingUnitBlock;
import appeng.block.networking.EnergyCellBlock;
import com.google.gson.JsonObject;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.crafting.InsaneAcceleratorType;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.energy.InsaneEnergyCellTier;
import jp.main.taikun.insaneae.energy.SolarPanelTier;
import jp.main.taikun.insaneae.registries.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * ブロックステートとブロックモデル。
 *
 * <p>{@code formed=false} は普通のキューブ、{@code formed=true} は
 * {@code BuiltInModelHooks} でマルチブロック用モデルに差し替えられるので
 * 中身が空のモデルを置くだけでよい。実ファイルが無いとモデル解決に失敗するため。</p>
 *
 * <p>どこまでdatagenが持つか:</p>
 * <ul>
 *   <li>ブロックステートは全ブロックぶんここで生成する。手書きしないこと。
 *       {@code src/generated/resources} に出たものが正)。</li>
 *   <li>ブロックモデルも基本はここで生成するが、ソーラーパネル4種 /
 *       Quantum CPU / Improved Crystal Chargerだけは手書き
 *       ({@code src/main/resources/assets/insaneae/models/block/<id>.json}) を参照する
 *       → {@link #handwritten}。凝った形にしたいのでJSONを直接触れるようにしてある。</li>
 *   <li>クラフトストレージ／協調処理ユニット／エネルギーセルのテクスチャは
 *       {@code tools/gen_*_textures.py} が階層の色から生成している。</li>
 * </ul>
 */
public class ModBlockStateProvider extends BlockStateProvider {

    /**
     * エネルギーセルの筐体テクスチャ。ベースと階層色を使い、残量に依らず常に出る。
     * {@code tools/gen_energy_textures.py} が階層の色から生成している。
     */
    public static ResourceLocation energyCellTexture(InsaneEnergyCellTier tier) {
        return ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/energy/" + tier.id());
    }

    /** 同上、残量ぶんのセグメントだけを描いた発光レイヤ。透過あり。 */
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
        // テクスチャはtools/gen_crafting_textures.pyが階層の色から生成している
        // モデル名とテクスチャ名が同じなので、生成先を変えたらここも合わせること
        for (InsaneCraftingUnitType tier : InsaneCraftingUnitType.values()) {
            // ModelProvider#getBuilderはパスに "/" を含むとフォルダを補完しないので、
            // "block/" から明示的に書く
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

        // ここから下の3種はブロックモデルを手書きしている ({@link #handwritten})
        // datagenはブロックステートを出して手書きモデルを指すだけで、モデル本体は触らない
        for (SolarPanelTier tier : SolarPanelTier.values()) {
            simpleBlock(ModBlocks.SOLAR_PANELS.get(tier).get(), handwritten(tier.id()));
        }
        simpleBlock(ModBlocks.QUANTUM_CPU.get(), handwritten("quantum_cpu"));
        // 通常クラフトストレージとしてformed状態を持つが、両状態とも意図的にmissing-textureを使う
        craftingUnitWithSingleModel(ModBlocks.BIG_INTEGER_CPU.get(), handwritten("big_integer_cpu"));
        // チャージャーは向きを持つ。facing × 6、spin × 4
        orientedBlock(ModBlocks.IMPROVED_CHARGER.get(), handwritten("improved_charger"));
        // 人形は水平4方向。手書きモデルは正面が北
        horizontalBlock(ModBlocks.GAMERTAIKUN_DOLL.get(), handwritten("gamertaikun_doll"));

        // 超特大インターフェイスはただのキューブ
        simpleBlock(ModBlocks.INSANE_INTERFACE.get(), models().cubeAll("insane_interface",
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/insane_interface")));

        // 特大パターンプロバイダーも同様に、AE2のパターンプロバイダの色相を回したキューブ
        // tools/gen_pattern_provider_texture.pyが生成している
        simpleBlock(ModBlocks.INSANE_PATTERN_PROVIDER.get(), models().cubeAll("insane_pattern_provider",
                ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/insane_pattern_provider")));
    }

    /**
     * 向き付きブロック1個ぶんのブロックステートを出す
     * {@code facing}が6方向、{@code spin}が4回転で、24通り。
     *
     * <p>AE2の {@code OrientationStrategies.full()} を使うブロック用。
     * 角度は {@link BlockOrientation} から引くので、AE2のチャージャーなどと
     * 同じ向き付けになることが保証される。</p>
     *
     * <p>{@code getVariantBuilder} が使えない理由: 24通りのうち半分は
     * Z軸回転を要求するが、バニラのブロックステートはx / yしか持たない。
     * AE2は {@code ae2:z} という独自キーを足し、その解釈をMixin
     * ({@code VariantDeserializerMixin}) でバニラのパーサに入れている。
     * これは名前空間に関係なく効くのでこちらのJSONでも使えるが、
     * NeoForgeの {@code ConfiguredModel} にはzを書く口が無いので、
     * ここだけJSONを直接組んで {@code registeredBlocks} に入れている。</p>
     */
    private void orientedBlock(Block block, ModelFile model) {
        JsonObject variants = new JsonObject();
        for (Direction facing : Direction.values()) {
            for (int spin = 0; spin < 4; spin++) {
                BlockOrientation orientation = BlockOrientation.get(facing, spin);
                JsonObject variant = new JsonObject();
                variant.addProperty("model", model.getLocation().toString());
                putAngle(variant, "x", orientation.getAngleX());
                putAngle(variant, "y", orientation.getAngleY());
                putAngle(variant, "ae2:z", orientation.getAngleZ());
                variants.add("facing=" + facing.getSerializedName() + ",spin=" + spin, variant);
            }
        }

        JsonObject blockState = new JsonObject();
        blockState.add("variants", variants);
        registeredBlocks.put(block, () -> blockState);
    }

    /** 0度は書かない。AE2が出すJSONと同じ形にするため。 */
    private static void putAngle(JsonObject variant, String key, int angle) {
        int normalized = Math.floorMod(angle, 360);
        if (normalized != 0) {
            variant.addProperty(key, normalized);
        }
    }

    /**
     * 手書きのブロックモデルを参照する。
     *
     * <p>実体は {@code src/main/resources/assets/insaneae/models/block/<id>.json}。
     * {@code runData} の引数に {@code --existing src/main/resources} があるので
     * ExistingFileHelperから見えており、ファイルが無ければdatagenが落ちて気付ける
     * typoで真っ黒なモデルになるのを防ぐため、あえて存在チェック付きで参照している。</p>
     *
     * <p>アイテムモデルは {@code ModItemModelProvider} が
     * 「このブロックモデルを親にするだけ」のJSONを出しているので、
     * ここを差し替えれば持ち手の見た目も一緒に変わる。</p>
     */
    private ModelFile handwritten(String id) {
        return models().getExistingFile(ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "block/" + id));
    }

    /**
     * formed / 未formedの2状態を持つクラフトユニット1種ぶんを出す。
     *
     * <p>formed側は実ファイルを出さない。{@link jp.main.taikun.insaneae.crafting.FormedModels}
     * のとおりae2名前空間の組み込みモデルに差し替わるので、JSONは読まれない。</p>
     */
    private void craftingUnit(Block block, String base,
            ResourceLocation texture, ResourceLocation formedModel) {
        ModelFile unformed = models().cubeAll(base, texture);

        getVariantBuilder(block)
                .partialState().with(AbstractCraftingUnitBlock.FORMED, false)
                .modelForState().modelFile(unformed).addModel()
                .partialState().with(AbstractCraftingUnitBlock.FORMED, true)
                .modelForState().modelFile(new ModelFile.UncheckedModelFile(formedModel)).addModel();
    }

    /** formedの前後で同じモデルを使う、通常クラフトユニット用のblockstateを生成する。 */
    private void craftingUnitWithSingleModel(Block block, ModelFile model) {
        getVariantBuilder(block)
                .partialState().with(AbstractCraftingUnitBlock.FORMED, false)
                .modelForState().modelFile(model).addModel()
                .partialState().with(AbstractCraftingUnitBlock.FORMED, true)
                .modelForState().modelFile(model).addModel();
    }

    /** 残量0〜4の5状態を持つエネルギーセル1種ぶんを出す。 */
    private void energyCell(InsaneEnergyCellTier tier) {
        var builder = getVariantBuilder(ModBlocks.ENERGY_CELLS.get(tier).get());
        for (int fullness = 0; fullness <= EnergyCellBlock.MAX_FULLNESS; fullness++) {
            builder.partialState().with(EnergyCellBlock.ENERGY_STORAGE, fullness)
                    .modelForState().modelFile(energyCellModel(tier, fullness)).addModel();
        }
    }

    /**
     * エネルギーセルのブロックモデル。筐体キューブ + ひとまわり大きい発光キューブの
     * 2要素で組む。
     *
     * <p>筐体側は「ベース + 階層色」を焼いた残量非依存のテクスチャで、発光側が残量ぶんの
     * セグメントだけを描いた透過テクスチャ。</p>
     *
     * <p>キューブを0.01だけ大きくしているのはZファイティング避け。透過を含むので
     * モデル全体の描画層は{@code cutout}にする必要がある。発光テクスチャは
     * 完全不透明か完全透明かの2値なのでalpha testで足りる)。</p>
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
                //? if >=1.21 {
                .allFaces((direction, face) -> face.texture("#light")).end();
                //?} else {
                /*.allFaces((direction, face) -> face.texture("#light").emissivity(15, 15)).end();
                *///?}
        return model;
    }

}
