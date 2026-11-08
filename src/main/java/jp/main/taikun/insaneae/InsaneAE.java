package jp.main.taikun.insaneae;

import appeng.api.storage.StorageCells;
import com.mojang.logging.LogUtils;
import jp.main.taikun.insaneae.cell.InsaneCreativeCellHandler;
import jp.main.taikun.insaneae.cell.storage.CellCommands;
import jp.main.taikun.insaneae.cell.storage.CellContentStore;
import jp.main.taikun.insaneae.client.InsaneAEClient;
import jp.main.taikun.insaneae.config.InsaneAEConfig;
import jp.main.taikun.insaneae.datagen.ModBlockLootProvider;
import jp.main.taikun.insaneae.datagen.ModBlockStateProvider;
import jp.main.taikun.insaneae.datagen.ModItemModelProvider;
import jp.main.taikun.insaneae.datagen.ModItemTagProvider;
import jp.main.taikun.insaneae.datagen.ModRecipeProvider;
import jp.main.taikun.insaneae.integration.aco.OptionalAcoBigIntegerIntegration;
import jp.main.taikun.insaneae.integration.AddonIntegrations;
import jp.main.taikun.insaneae.registries.ModBlockEntities;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModCells;
import jp.main.taikun.insaneae.registries.ModCreativeTabs;
import jp.main.taikun.insaneae.registries.ModItems;
import jp.main.taikun.insaneae.registries.ModMenus;
import jp.main.taikun.insaneae.registries.ModParts;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import net.minecraft.data.DataGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
//? if >=1.21 {
import jp.main.taikun.insaneae.registries.ModCapabilities;
import jp.main.taikun.insaneae.registries.ModDataComponents;
import net.minecraft.core.HolderLookup;
import net.neoforged.fml.ModContainer;
//?}
//? if <1.21 {
/*import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
*///?}

@Mod(InsaneAE.MODID)
public class InsaneAE {

    /** {@code META-INF/neoforge.mods.toml} のmodIdと一致させること。 */
    public static final String MODID = "insaneae";
    private static final Logger LOGGER = LogUtils.getLogger();

    //? if >=1.21 {
    public InsaneAE(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, InsaneAEConfig.SPEC);
    //?} else {
    /*public InsaneAE(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.COMMON, InsaneAEConfig.SPEC);
    *///?}
        ModBlocks.register(bus);
        ModItems.register(bus);
        //? if >=1.21 {
        ModDataComponents.register(bus);
        //?}
        // ケーブルに貼る版。部品のモデル申告が凍結前に済む必要があるのでここで
        ModParts.register(bus);
        // 他のAE2アドオンとの連携。化学物質セル、FEセル、他Modの機械への加速カードなど
        // 入っているものだけ有効にする。セルはModCellsの登録に相乗りするので、その前に
        AddonIntegrations.init();
        ModCells.register(bus);
        ModUpgrades.register(bus);
        ModBlockEntities.register(bus);
        ModMenus.register(bus);
        ModCreativeTabs.register(bus);

        bus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                CellCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((LevelEvent.Save event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                CellContentStore.onLevelSaved(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> CellContentStore.forget());
        bus.addListener(this::gatherData);
        // NeoForgeではcapabilityをBlockEntityType / Itemごとに専用イベントで登録する
        // AE2の一括登録はAE2自身の型しか見ないので、自前の型はここで全部登録する
        //? if >=1.21 {
        bus.addListener(ModCapabilities::register);
        // ケーブルに貼る版はAE2の部品用イベントで登録する。AE2の登録はクラスの完全一致でしか効かないため
        bus.addListener(ModCapabilities::registerParts);
        //?}

        // formedモデルの組み込み登録はModelBakeryより前に済ませる必要があるため、
        // clientでのみMod構築時に行う。AE2のAppEngClientコンストラクタと同じタイミングにするため
        // 分離クラス経由なのでdedicated serverではロードされない
        if (FMLEnvironment.dist == Dist.CLIENT) {
            InsaneAEClient.init(bus);
        }
    }

    /**
     * レシピ・ドロップ・モデル類は手書きJSONではなくdatagenで生成する
     * 生成は{@code ./gradlew runData}で行い、出力先は{@code src/generated/resources}。
     */
    private void gatherData(final GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFiles = event.getExistingFileHelper();
        // レシピ・ドロップ系のプロバイダはHolderLookup.Providerを要求する
        // タグやデータコンポーネントをレジストリ越しに引くため
        //? if >=1.21 {
        CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();

        // タグはレシピより先に生成する。レシピがタグを材料に使うため、生成の順を揃えておく
        generator.addProvider(event.includeServer(), new ModItemTagProvider(output, registries,
        //?} else {
        /*generator.addProvider(event.includeServer(), new ModItemTagProvider(output,
                event.getLookupProvider(),
        *///?}
                CompletableFuture.completedFuture(
                        TagsProvider.TagLookup.empty()),
                existingFiles));
        //? if >=1.21 {
        generator.addProvider(event.includeServer(), new ModRecipeProvider(output, registries));
        //?} else {
        /*generator.addProvider(event.includeServer(), new ModRecipeProvider(output));
        *///?}
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(
                //? if >=1.21 {
                        ModBlockLootProvider::new, LootContextParamSets.BLOCK)),
                registries));
                //?} else {
                        /*ModBlockLootProvider::new, LootContextParamSets.BLOCK))));
                *///?}
        // ブロックモデルを先に生成しておく。アイテムモデルが親として参照するため
        generator.addProvider(event.includeClient(), new ModBlockStateProvider(output, existingFiles));
        generator.addProvider(event.includeClient(), new ModItemModelProvider(output, existingFiles));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // ブロックとBlockEntityTypeが揃った後で結びつける
        event.enqueueWork(ModBlockEntities::bindBlockEntities);
        // 強化クリエイティブセルをMEドライブ等に認識させる
        // 判定は自前のアイテムだけなのでAE2側のハンドラとの登録順は問わない
        event.enqueueWork(() -> StorageCells.addCellHandler(
                InsaneCreativeCellHandler.INSTANCE));
        event.enqueueWork(ModUpgrades::registerUpgrades);
        // ACOへ「BigInteger計画を受け取る外部CPUアドオン」として名乗る
        event.enqueueWork(
                OptionalAcoBigIntegerIntegration
                        ::registerBigIntegerPlanConsumer);
        //? if <1.21 {
        /*if (Boolean.getBoolean("appeng.tests")) {
            // プロットは配布jarに入らない別のソースセットにあるので、クラス名で呼ぶ
            event.enqueueWork(InsaneAE::registerTestPlots);
        }
        *///?}
        // 検証用のテストプロット (`./gradlew runGameTestServer`) は
        // @TestPlotClass経由でAE2が自動的に拾うので、ここで登録する必要はない
        LOGGER.info("InsaneAE: {} crafting storage tiers registered.", ModBlocks.CRAFTING_STORAGE.size());
    }
    //? if <1.21 {
    /*private static void registerTestPlots() {
        try {
            Class.forName("jp.main.taikun.insaneae.testplots.InsaneAETestPlots")
                    .getMethod("register").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("InsaneAE: test plots are missing from the classpath", e);
        }
    }
    *///?}
}
