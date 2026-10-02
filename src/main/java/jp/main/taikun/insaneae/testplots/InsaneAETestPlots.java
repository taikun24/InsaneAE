package jp.main.taikun.insaneae.testplots;

import appeng.server.testplots.TestPlots;

/**
 * InsaneAE のテストプロットを AE2 に登録する。中身はグループごとのクラスにある。
 *
 * <p>AE2 15.x はプロットを自動では拾わないので、プロットクラスを足したらここにも足すこと。</p>
 */
public final class InsaneAETestPlots {

    private InsaneAETestPlots() {
    }

    /** AE2 のプロット一覧に登録する。{@code appeng.tests} が有効なときだけ呼ぶこと。 */
    public static void register() {
        TestPlots.addPlotClass(CraftingCalculationPlots.class);
        TestPlots.addPlotClass(NetworkPlots.class);
        TestPlots.addPlotClass(SpeedCardPlots.class);
        TestPlots.addPlotClass(QuantumCpuPlots.class);
        TestPlots.addPlotClass(AcoPlots.class);
        TestPlots.addPlotClass(AdvancedAePlots.class);
        TestPlots.addPlotClass(LongOverflowCraftPlots.class);
        TestPlots.addPlotClass(AddonPlots.class);
    }
}
