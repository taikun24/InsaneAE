package jp.main.taikun.insaneae;

import com.mojang.logging.LogUtils;
import jp.main.taikun.insaneae.client.InsaneAEClient;
import jp.main.taikun.insaneae.config.InsaneAEConfig;
import jp.main.taikun.insaneae.datagen.ModBlockLootProvider;
import jp.main.taikun.insaneae.datagen.ModBlockStateProvider;
import jp.main.taikun.insaneae.datagen.ModItemModelProvider;
import jp.main.taikun.insaneae.datagen.ModRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import jp.main.taikun.insaneae.registries.ModBlockEntities;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModCapabilities;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModCreativeTabs;
import jp.main.taikun.insaneae.registries.ModItems;
import jp.main.taikun.insaneae.registries.ModMenus;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(InsaneAE.MODID)
public class InsaneAE {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "insaneae";
    /** 任意依存: Applied Mekanistics。導入時のみ化学物質セルを追加する。 */
    public static final String APPMEK_MODID = "appmek";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    // NeoForge の Mod コンストラクタは (IEventBus, ModContainer) を受け取る。
    // 1.20.1 の Forge では FMLJavaModLoadingContext 経由だった。
    public InsaneAE(IEventBus bus, ModContainer container) {
        // 設定の登録先も ModLoadingContext から ModContainer に移った。
        container.registerConfig(ModConfig.Type.COMMON, InsaneAEConfig.SPEC);
        ModBlocks.register(bus);
        ModItems.register(bus);
        // appmek 未導入の環境では AppMekCells をロードしてはいけないので、
        // クラス参照ごと分岐の内側に閉じ込める (別クラスなので条件が false ならロードされない)。
        if (ModList.get().isLoaded(APPMEK_MODID)) {
            LOGGER.info("InsaneAE: Applied Mekanistics detected, adding chemical cells.");
            jp.main.taikun.insaneae.integration.appmek.AppMekCells.register();
        }
        ModCells.register(bus);
        ModUpgrades.register(bus);
        ModBlockEntities.register(bus);
        ModMenus.register(bus);
        ModCreativeTabs.register(bus);

        bus.addListener(this::commonSetup);
        bus.addListener(this::gatherData);
        // NeoForge では capability は BlockEntityType / Item ごとに専用イベントで登録する
        // (1.20.1 のように BlockEntity#getCapability を override して継承させる方式は廃止された)。
        // AE2 の一括登録は AE2 自身の型しか見ないので、自前の型はここで全部登録し直す。
        bus.addListener(ModCapabilities::register);

        // formed モデルの組み込み登録は ModelBakery より前に済ませる必要があるため、
        // client でのみ Mod 構築時に行う (AE2 の AppEngClient コンストラクタと同タイミング)。
        // 分離クラス経由なので dedicated server ではロードされない。
        if (FMLEnvironment.dist == Dist.CLIENT) {
            InsaneAEClient.init(bus);
        }
    }

    /**
     * レシピ・ドロップ・モデル類は手書き JSON ではなく datagen で生成する
     * ({@code ./gradlew runData} → {@code src/generated/resources})。
     */
    private void gatherData(final GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFiles = event.getExistingFileHelper();
        // 1.21 でレシピ・ドロップ系のプロバイダは HolderLookup.Provider を要求するようになった
        // (タグやデータコンポーネントをレジストリ越しに引くため)。
        CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();

        generator.addProvider(event.includeServer(), new ModRecipeProvider(output, registries));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(
                        ModBlockLootProvider::new, LootContextParamSets.BLOCK)),
                registries));
        // ブロックモデルを先に生成しておく (アイテムモデルが親として参照するため)。
        generator.addProvider(event.includeClient(), new ModBlockStateProvider(output, existingFiles));
        generator.addProvider(event.includeClient(), new ModItemModelProvider(output, existingFiles));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // ブロックと BlockEntityType が揃った後で結びつける。
        event.enqueueWork(ModBlockEntities::bindBlockEntities);
        // 強化クリエイティブセルを ME ドライブ等に認識させる。
        // 判定は自前のアイテムだけなので AE2 側のハンドラとの登録順は問わない。
        event.enqueueWork(() -> appeng.api.storage.StorageCells.addCellHandler(
                jp.main.taikun.insaneae.cell.InsaneCreativeCellHandler.INSTANCE));
        // 加速カードを AE2 の対応機械に登録する。
        event.enqueueWork(ModUpgrades::registerUpgrades);
        // ACO へ「BigInteger 計画を受け取る外部 CPU アドオン」として名乗る。
        event.enqueueWork(
                jp.main.taikun.insaneae.integration.aco.OptionalAcoBigIntegerIntegration
                        ::registerBigIntegerPlanConsumer);
        // 検証用のテストプロット (`./gradlew runGameTestServer`) は
        // @TestPlotClass 経由で AE2 が自動的に拾うので、ここでの登録は不要になった。
        LOGGER.info("InsaneAE: {} crafting storage tiers registered.", ModBlocks.CRAFTING_STORAGE.size());
    }
}
