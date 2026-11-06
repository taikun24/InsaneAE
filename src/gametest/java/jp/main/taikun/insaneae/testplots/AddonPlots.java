package jp.main.taikun.insaneae.testplots;

import appeng.core.definitions.ItemDefinition;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import jp.main.taikun.insaneae.integration.appflux.AppFluxIntegration;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModParts;
import net.minecraft.core.Direction;
import net.neoforged.fml.ModList;

/**
 * 他アドオン連携 ({@code integration/}) の検証。
 *
 * <p>相手 Mod のクラスに触る検査は別クラス ({@link AppFluxChecks} など) に分けてあり、
 * 相手が入っていないときはそのクラスをロードせずに素通りで成功させる
 * (dev 実行から {@code -PwithAppFlux=false} で外しても全体が落ちないように)。</p>
 */
@TestPlotClass
public final class AddonPlots {

    private AddonPlots() {
    }

    /** FE セル: 中身の出し入れ・long を溢れない容量・カード・FE の capability。 */
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
     * AppliedFlux の誘導カードを超次元版のインターフェイス / パターンプロバイダー (ブロックと部品) に挿すと、
     * <b>AE2 の機械と同じく FE の口が開く</b>こと。
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
        plot.part("2 0 0", Direction.UP, new ItemDefinition<>("Insane ME Interface", ModParts.INSANE_INTERFACE));
        plot.part("2 0 0", Direction.NORTH,
                new ItemDefinition<>("Insane Pattern Provider", ModParts.INSANE_PATTERN_PROVIDER));
        plot.test(helper -> {
            var sequence = helper.startSequence();
            sequence.thenIdle(10);
            sequence.thenExecute(() -> {
                if (ModList.get().isLoaded(AppFluxIntegration.MODID)) {
                    AppFluxChecks.inductionCard(helper);
                }
            });
            sequence.thenSucceed();
        });
    }
}
