package jp.main.taikun.insaneae;

import appeng.api.storage.StorageCells;
import com.mojang.logging.LogUtils;
import jp.main.taikun.insaneae.cell.InsaneCreativeCellHandler;
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
import jp.main.taikun.insaneae.testplots.InsaneAETestPlots;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mod(InsaneAE.MODID)
public class InsaneAE {

    /** {@code META-INF/mods.toml} の modId と一致させること。 */
    public static final String MODID = "insaneae";
    private static final Logger LOGGER = LogUtils.getLogger();

    public InsaneAE(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.COMMON, InsaneAEConfig.SPEC);
        ModBlocks.register(bus);
        ModItems.register(bus);
        // ケーブルに貼る版。部品のモデル申告が凍結前に済む必要があるのでここで。
        ModParts.register(bus);
        // 他の AE2 アドオンとの連携 (化学物質セル・FE セル・他 Mod の機械への加速カードなど)。
        // 入っているものだけ有効にする。セルは ModCells の登録に相乗りするので、その前に。
        AddonIntegrations.init();
        ModCells.register(bus);
        ModUpgrades.register(bus);
        ModBlockEntities.register(bus);
        ModMenus.register(bus);
        ModCreativeTabs.register(bus);

        bus.addListener(this::commonSetup);
        bus.addListener(this::gatherData);

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

        // タグはレシピより先に (レシピがタグを材料に使うため、生成の順を揃えておく)。
        generator.addProvider(event.includeServer(), new ModItemTagProvider(output,
                event.getLookupProvider(),
                CompletableFuture.completedFuture(
                        TagsProvider.TagLookup.empty()),
                existingFiles));
        generator.addProvider(event.includeServer(), new ModRecipeProvider(output));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(
                        ModBlockLootProvider::new, LootContextParamSets.BLOCK))));
        // ブロックモデルを先に生成しておく (アイテムモデルが親として参照するため)。
        generator.addProvider(event.includeClient(), new ModBlockStateProvider(output, existingFiles));
        generator.addProvider(event.includeClient(), new ModItemModelProvider(output, existingFiles));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // ブロックと BlockEntityType が揃った後で結びつける。
        event.enqueueWork(ModBlockEntities::bindBlockEntities);
        // 強化クリエイティブセルを ME ドライブ等に認識させる。
        // 判定は自前のアイテムだけなので AE2 側のハンドラとの登録順は問わない。
        event.enqueueWork(() -> StorageCells.addCellHandler(
                InsaneCreativeCellHandler.INSTANCE));
        // 加速カードを AE2 の対応機械に登録する。
        event.enqueueWork(ModUpgrades::registerUpgrades);
        // ACO へ「BigInteger 計画を受け取る外部 CPU アドオン」として名乗る。
        event.enqueueWork(
                OptionalAcoBigIntegerIntegration
                        ::registerBigIntegerPlanConsumer);
        // 検証用のテストプロット。AE2 のテスト基盤が有効なときだけ載せる
        // (`./gradlew runGameTestServer`)。通常のプレイでは何も登録されない。
        if (Boolean.getBoolean("appeng.tests")) {
            event.enqueueWork(InsaneAETestPlots::register);
        }
        LOGGER.info("InsaneAE: {} crafting storage tiers registered.", ModBlocks.CRAFTING_STORAGE.size());
    }
}
