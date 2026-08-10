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
import jp.main.taikun.insaneae.menu.InsaneInterfaceMenu;
import jp.main.taikun.insaneae.menu.InsanePatternProviderMenu;
import jp.main.taikun.insaneae.menu.QuantumCpuMenu;
import jp.main.taikun.insaneae.registries.ModBlockEntities;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModCells;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
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
        bus.addListener(InsaneAEClient::registerScreens);
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
    }

    /**
     * 画面の登録。1.20.5 で専用イベント {@link RegisterMenuScreensEvent} が新設され、
     * {@code FMLClientSetupEvent} からの登録はできなくなった
     * (AE2 の {@code InitScreens#register} もイベントを第 1 引数に取るようになっている)。
     */
    private static void registerScreens(RegisterMenuScreensEvent event) {
        InitScreens.register(event,
                QuantumCpuMenu.TYPE, QuantumCpuScreen<QuantumCpuMenu>::new,
                "/screens/insaneae/quantum_cpu.json");
        InitScreens.register(event,
                InsaneInterfaceMenu.TYPE, InsaneInterfaceScreen::new,
                "/screens/insaneae/insane_interface.json");
        // 特大パターンプロバイダーは画面クラスごと Quantum CPU と共用 (レイアウトも同じ)。
        InitScreens.register(event,
                InsanePatternProviderMenu.TYPE, QuantumCpuScreen<InsanePatternProviderMenu>::new,
                "/screens/insaneae/insane_pattern_provider.json");
    }

    /** formed 状態のブロックモデルを、階層色の発光レイヤを持つマルチブロック用モデルに差し替える。 */
    private static void addFormedModel(ICraftingUnitType type, ResourceLocation modelId, String lightTexture) {
        BuiltInModelHooks.addBuiltInModel(modelId,
                new CraftingCubeModel(new InsaneCraftingUnitModelProvider(type, lightTexture)));
    }

    /**
     * ストレージセルのモデル 2 レイヤ目 (LED) の色付け。AE2 の
     * {@link BasicStorageCell#getColor} が中身の量に応じた色を返す。
     *
     * <p><b>必ず {@link #opaque} を通して登録すること。</b>理由はそちらの説明を参照。</p>
     */
    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        ItemColor basic = opaque(BasicStorageCell::getColor);
        // ポータブルセルは染色できるので AE2 側の色解決を使う。
        ItemColor portable = opaque(AbstractPortableCell::getColor);
        ModCells.ITEM_CELLS.values().forEach(cell -> event.register(basic, cell.get()));
        ModCells.FLUID_CELLS.values().forEach(cell -> event.register(basic, cell.get()));
        ModCells.PORTABLE_ITEM_CELLS.values().forEach(cell -> event.register(portable, cell.get()));
        ModCells.PORTABLE_FLUID_CELLS.values().forEach(cell -> event.register(portable, cell.get()));
        if (ModList.get().isLoaded(InsaneAE.APPMEK_MODID)) {
            jp.main.taikun.insaneae.integration.appmek.AppMekCells.CHEMICAL_CELLS.values()
                    .forEach(cell -> event.register(basic, cell.get()));
            jp.main.taikun.insaneae.integration.appmek.AppMekCells.PORTABLE_CHEMICAL_CELLS.values()
                    .forEach(cell -> event.register(portable, cell.get()));
        }
    }

    /**
     * 色ハンドラの戻り値を<b>不透明にして</b>から使う。これを通さないとセルが丸ごと消える。
     *
     * <p>AE2 の {@link BasicStorageCell#getColor} / {@link AbstractPortableCell#getColor} は
     * 色を付けないレイヤに {@code 0xFFFFFF} を返す。これは ARGB として見ると
     * <b>アルファが 0</b> であり、NeoForge 1.21.1 の {@code ItemRenderer#renderQuadList} は</p>
     *
     * <pre>float f = FastColor.ARGB32.alpha(色) / 255.0F;   // ← 1.20.1 には無かった
     *buffer.putBulkData(pose, quad, r, g, b, f, ...);</pre>
     *
     * <p>と<b>戻り値のアルファを頂点に掛ける</b>ので、そのまま登録すると全レイヤの
     * アルファが 0 になり、アイテムが完全に透明になって見えなくなる
     * ({@code item/generated} は layerN に tintindex N を振るため、
     * 筐体も階層色も例外なく色ハンドラを通る)。1.20.1 の {@code ItemRenderer} は
     * アルファを 1.0 固定にしていたので、これは 1.21.1 で初めて出る問題。</p>
     *
     * <p>色ハンドラを登録していないアイテム (セルコンポーネントなど) は既定値 {@code -1}
     * = {@code 0xFFFFFFFF} が使われるので影響を受けない。<b>セルだけが消えていたのはこのため。</b></p>
     *
     * <p>AE2 自身も {@code InitItemColors#makeOpaque} で同じ処理をしてから登録しているが、
     * private で借りられないのでこちらで用意する。</p>
     */
    private static ItemColor opaque(ItemColor color) {
        return (stack, tintIndex) -> color.getColor(stack, tintIndex) | 0xFF000000;
    }
}
