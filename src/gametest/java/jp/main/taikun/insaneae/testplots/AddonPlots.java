package jp.main.taikun.insaneae.testplots;

import appeng.core.definitions.ItemDefinition;
import appeng.server.testplots.TestPlot;
import appeng.server.testworld.PlotBuilder;
import jp.main.taikun.insaneae.integration.appflux.AppFluxIntegration;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModParts;
import net.minecraft.core.Direction;
import net.neoforged.fml.ModList;
//? if >=1.21 {
import appeng.server.testplots.TestPlotClass;
//?}

/**
 * 他アドオン連携 ({@code integration/}) の検証。
 *
 * <p>相手Modのクラスに触る検査は、別クラスの{@link AppFluxChecks}などに分けてあり、
 * 相手が入っていないときはそのクラスをロードせずに素通りで成功させる
 * dev実行で{@code -PwithAppFlux=false}を付けて外しても全体が落ちないように。</p>
 */
//? if >=1.21 {
@TestPlotClass
//?}
public final class AddonPlots {

    private AddonPlots() {
    }

    /** FEセル: 中身の出し入れ・longを溢れない容量・カード・FEのcapability。 */
    @TestPlot("insaneae_appflux_fe_cells")
    public static void appFluxFeCells(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.test(helper -> helper.succeedIf(() -> {
            if (ModList.get().isLoaded(AppFluxIntegration.MODID)) {
                AppFluxChecks.run(helper);
            }
        }));
    }

    /**
     * AppliedFluxの誘導カードを、超次元版のインターフェイス / パターンプロバイダーのブロックと部品の両方に挿すと、
     * AE2の機械と同じくFEの口が開くこと。
     *
     * <p>並び: ケーブル (0..2, 0, 0) の上に超特大インターフェイス (0,1,0) と特大パターンプロバイダー (1,1,0)。
     * (2,0,0) のケーブルには上面にインターフェイス、北面にパターンプロバイダーの部品を貼る。</p>
     */
    @TestPlot("insaneae_appflux_induction_card")
    public static void appFluxInductionCard(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockState("0 1 0", ModBlocks.INSANE_INTERFACE.get().defaultBlockState());
        plot.blockState("1 1 0", ModBlocks.INSANE_PATTERN_PROVIDER.get().defaultBlockState());
        //? if >=1.21 {
        plot.part("2 0 0", Direction.UP, new ItemDefinition<>("Insane ME Interface", ModParts.INSANE_INTERFACE));
        plot.part("2 0 0", Direction.NORTH,
                new ItemDefinition<>("Insane Pattern Provider", ModParts.INSANE_PATTERN_PROVIDER));
        //?} else {
        /*plot.part("2 0 0", Direction.UP, new ItemDefinition<>("Insane ME Interface",
                ModParts.INSANE_INTERFACE.getId(), ModParts.INSANE_INTERFACE.get()));
        plot.part("2 0 0", Direction.NORTH, new ItemDefinition<>("Insane Pattern Provider",
                ModParts.INSANE_PATTERN_PROVIDER.getId(), ModParts.INSANE_PATTERN_PROVIDER.get()));
        *///?}
        plot.test(helper -> {
            var sequence = helper.startSequence();
            sequence.thenIdle(10);
            sequence.thenExecute(() -> {
                if (ModList.get().isLoaded(AppFluxIntegration.MODID)) {
                    AppFluxChecks.inductionCard(helper);
                }
            });
            //? if <1.21 {
            /*sequence.thenIdle(5);
            sequence.thenExecute(() -> {
                if (ModList.get().isLoaded(AppFluxIntegration.MODID)) {
                    AppFluxChecks.neighborRelay(helper);
                }
            });
            *///?}
            sequence.thenSucceed();
        });
    }
}
