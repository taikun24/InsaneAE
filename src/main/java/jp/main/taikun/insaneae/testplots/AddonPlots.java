package jp.main.taikun.insaneae.testplots;

import appeng.server.testplots.TestPlot;
import appeng.server.testworld.PlotBuilder;
import jp.main.taikun.insaneae.integration.appflux.AppFluxIntegration;
import net.minecraftforge.fml.ModList;

/**
 * 他アドオン連携 ({@code integration/}) の検証。
 *
 * <p>相手 Mod のクラスに触る検査は別クラス ({@link AppFluxChecks} など) に分けてあり、
 * 相手が入っていないときはそのクラスをロードせずに素通りで成功させる
 * (dev 実行から {@code -PwithAppFlux=false} で外しても全体が落ちないように)。
 * 登録は {@link InsaneAETestPlots}。</p>
 */
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
}
