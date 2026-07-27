package jp.main.taikun.insaneae.client;

import appeng.api.inventories.InternalInventory;
import appeng.block.crafting.ICraftingUnitType;
import appeng.client.render.crafting.CraftingCubeModel;
import appeng.client.render.renderable.ItemRenderable;
import appeng.client.render.tesr.ModularTESR;
import appeng.hooks.BuiltInModelHooks;
import appeng.init.client.InitScreens;
import appeng.items.storage.BasicStorageCell;
import appeng.items.tools.powered.AbstractPortableCell;
import com.mojang.math.Transformation;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.charger.ImprovedChargerBlockEntity;
import jp.main.taikun.insaneae.crafting.InsaneAcceleratorType;
import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import jp.main.taikun.insaneae.menu.QuantumCpuMenu;
import jp.main.taikun.insaneae.registries.ModBlockEntities;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModCells;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.joml.Vector3f;

/**
 * クライアント専用のセットアップ。
 *
 * formed 状態のブロックモデルは、AE2 と同じく {@link BuiltInModelHooks#addBuiltInModel} で
 * 組み込みモデルとして登録する。{@code formed=true} のブロックステートが参照する
 * formed モデル ID を、階層色の発光レイヤを持つ {@link CraftingCubeModel} に差し替える。
 * <b>モデル ID は ae2 名前空間でなければならない</b> ({@code BuiltInModelHooks} が他名前空間を
 * 無条件で弾くため) → {@link jp.main.taikun.insaneae.crafting.FormedModels}。
 *
 * <p><b>重要:</b> 登録は必ず Mod 構築時 (メインクラスのコンストラクタ) から呼ぶこと。
 * AE2 も {@code AppEngClient} のコンストラクタで {@code InitBuiltInModels.init()} を呼んでおり、
 * これは {@code ModelBakery} (組み込みモデルを読む {@code ModelBakeryMixin}) より前に実行される。
 * {@code FMLClientSetupEvent}+{@code enqueueWork} だと ModelBakery より後になり得るため、
 * formed モデルが差し替わらず空モデル {@code {}} が見えてブロックが透明になる。</p>
 */
public final class InsaneAEClient {

    private InsaneAEClient() {
    }

    /** Mod 構築時にクライアントでのみ呼ぶ。 */
    public static void init(IEventBus bus) {
        for (InsaneCraftingUnitType type : InsaneCraftingUnitType.values()) {
            addFormedModel(type, type.formedModel(),
                    "block/crafting/" + type.id() + "_storage_light");
        }
        for (InsaneAcceleratorType type : InsaneAcceleratorType.values()) {
            addFormedModel(type, type.formedModel(),
                    "block/crafting/" + type.id() + "_accelerator_light");
        }
        InsaneTooltips.register();
        bus.addListener(InsaneAEClient::registerItemColors);
        bus.addListener(InsaneAEClient::clientSetup);
        bus.addListener(InsaneAEClient::registerRenderers);
    }

    /**
     * Improved Crystal Charger の中身を宙に浮かせて描く (AE2 のチャージャーと同じ見せ方)。
     * 入力が空なら出力側を見せる。
     */
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.IMPROVED_CHARGER.get(),
                context -> new ModularTESR<>(new ItemRenderable<ImprovedChargerBlockEntity>(be -> {
                    InternalInventory inv = be.getInternalInventory();
                    ItemStack shown = inv.getStackInSlot(ImprovedChargerBlockEntity.INPUT_SLOT);
                    if (shown.isEmpty()) {
                        shown = inv.getStackInSlot(ImprovedChargerBlockEntity.OUTPUT_SLOT);
                    }
                    return ImmutablePair.of(shown,
                            new Transformation(new Vector3f(0.5F, 0.375F, 0.5F), null, null, null));
                })));
    }

    /**
     * Quantum CPU の画面を登録する。レイアウト JSON は {@code StyleManager} が
     * {@code ae2} 名前空間固定で読むため、こちらの分も {@code assets/ae2/screens/insaneae/} に置いている。
     */
    private static void clientSetup(FMLClientSetupEvent event) {
        // AE2 は自分のクラフトユニットを cutout で描いている (InitRenderTypes)。
        // 登録しないと solid になり、formed の枠テクスチャの透明部分が正しく抜けない。
        event.enqueueWork(() -> ModBlocks.allCraftingBlocks()
                .forEach(block -> ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutout())));
        event.enqueueWork(() -> InitScreens.register(
                QuantumCpuMenu.TYPE, QuantumCpuScreen::new, "/screens/insaneae/quantum_cpu.json"));
    }

    /** formed 状態のブロックモデルを、階層色の発光レイヤを持つマルチブロック用モデルに差し替える。 */
    private static void addFormedModel(ICraftingUnitType type, ResourceLocation modelId, String lightTexture) {
        BuiltInModelHooks.addBuiltInModel(modelId,
                new CraftingCubeModel(new InsaneCraftingUnitModelProvider(type, lightTexture)));
    }

    /**
     * ストレージセルのモデル 2 レイヤ目 (LED) の色付け。AE2 の
     * {@link BasicStorageCell#getColor} が中身の量に応じた色を返す。
     */
    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        ModCells.ITEM_CELLS.values().forEach(cell -> event.register(BasicStorageCell::getColor, cell.get()));
        ModCells.FLUID_CELLS.values().forEach(cell -> event.register(BasicStorageCell::getColor, cell.get()));
        // ポータブルセルは染色できるので AE2 側の色解決を使う。
        ModCells.PORTABLE_ITEM_CELLS.values()
                .forEach(cell -> event.register(AbstractPortableCell::getColor, cell.get()));
        ModCells.PORTABLE_FLUID_CELLS.values()
                .forEach(cell -> event.register(AbstractPortableCell::getColor, cell.get()));
        if (ModList.get().isLoaded(InsaneAE.APPMEK_MODID)) {
            jp.main.taikun.insaneae.integration.appmek.AppMekCells.CHEMICAL_CELLS.values()
                    .forEach(cell -> event.register(BasicStorageCell::getColor, cell.get()));
            jp.main.taikun.insaneae.integration.appmek.AppMekCells.PORTABLE_CHEMICAL_CELLS.values()
                    .forEach(cell -> event.register(AbstractPortableCell::getColor, cell.get()));
        }
    }
}
