package jp.main.taikun.insaneae.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * InsaneAE の設定 ({@code config/insaneae-common.toml})。
 *
 * <p>ここに並ぶのは<b>軽量化まわりの切り替え</b>だけ。どれも既定は有効で、
 * 挙動が変わったり他 Mod と噛み合わなかったりしたときに個別で切れるようにしてある。</p>
 *
 * <p>設定の読み出しは<b>実行時</b>に行うこと (Mixin の適用可否には使わない)。
 * 設定ファイルの読み込みは Mod 構築より後なので、
 * クラスロード時に値を確定させると既定値で固まってしまう。</p>
 */
public final class InsaneAEConfig {

    private static final Common COMMON;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        SPEC = pair.getRight();
    }

    private InsaneAEConfig() {
    }

    /** クラフト計算のまとめ処理を使うか。 */
    public static boolean batchCraftingCalculation() {
        return get(COMMON.batchCraftingCalculation, true);
    }

    /**
     * まとめ処理の有効・無効を実行中に切り替える。
     * <b>ゲームテスト用</b> (同じ発注を「まとめ有り／無し」で計算して結果を突き合わせるため)。
     */
    public static void setBatchCraftingCalculation(boolean value) {
        COMMON.batchCraftingCalculation.set(value);
    }

    /** 何クラフト以上をまとめて処理するか。これ未満は AE2 本来の 1 回ずつに任せる。 */
    public static int craftingBatchThreshold() {
        return get(COMMON.craftingBatchThreshold, 16);
    }

    /** Quantum CPU のパターン枠をサーバ側でページ分割するか。 */
    public static boolean serverSidePatternPaging() {
        return get(COMMON.serverSidePatternPaging, true);
    }

    /** Astral Mekanism の自動搬出を、ME インターフェイス相手のときネットワークへまとめて流すか。 */
    public static boolean astralNetworkEject() {
        return get(COMMON.astralNetworkEject, true);
    }

    /**
     * 設定ファイル読み込み前でも安全に読む。
     *
     * <p>{@code ConfigValue#get()} は読み込み前に呼ぶと例外になるので、
     * その場合はコード側の既定値を返す。</p>
     */
    private static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        if (!SPEC.isLoaded()) {
            return fallback;
        }
        return value.get();
    }

    private static final class Common {

        private final ForgeConfigSpec.BooleanValue batchCraftingCalculation;
        private final ForgeConfigSpec.IntValue craftingBatchThreshold;
        private final ForgeConfigSpec.BooleanValue serverSidePatternPaging;
        private final ForgeConfigSpec.BooleanValue astralNetworkEject;

        private Common(ForgeConfigSpec.Builder builder) {
            builder.comment("クラフト計算 (Calculating... の部分) の軽量化").push("crafting_calculation");

            batchCraftingCalculation = builder
                    .comment("同じパターンを何度も繰り返す部分をまとめて計算する。",
                            "AE2 は (1) 出力が自分の入力にもあるパターン (2) バケツ等のコンテナアイテムを使うパターン",
                            "(3) 同じアイテムを作れるパターンが複数ある場合 を「1 クラフトずつ」シミュレートするため、",
                            "要求数に比例して計算時間が伸びる。ここを 1 回ぶんの差分の掛け算に置き換える。",
                            "計算結果 (必要な材料・バイト数) は同じになるはずだが、",
                            "クラフト計画がおかしいと思ったらここを false にして比べること。")
                    .define("batchCraftingCalculation", true);

            craftingBatchThreshold = builder
                    .comment("この回数以上まとめられるときだけ上の処理を使う。",
                            "小さいクラフトでは差が出ないので、AE2 本来の経路に任せる。")
                    .defineInRange("craftingBatchThreshold", 16, 2, Integer.MAX_VALUE);

            builder.pop();
            builder.comment("Quantum CPU").push("quantum_cpu");

            serverSidePatternPaging = builder
                    .comment("パターン枠をサーバ側で 1 ページ (54 枠) ずつ扱う。",
                            "全 1620 枠をメニューに並べると、GUI を開いている間",
                            "毎 tick 1620 枠ぶんの中身比較がサーバで走るため。",
                            "false にすると全枠を並べる代わりに、ページ送りがクライアント内で完結して速くなる。")
                    .define("serverSidePatternPaging", true);

            builder.pop();
            builder.comment("他 Mod との連携").push("compat");

            astralNetworkEject = builder
                    .comment("Astral Mekanism & Energistics の機械が ME インターフェイスへ自動搬出するとき、",
                            "スタック数や搬出レート (fluidAutoEjectRate / chemicalAutoEjectRate) で刻まずに",
                            "中身を全部ネットワークへ渡す。Astral の機械は 1 回の処理で long 級を作るので、",
                            "Mekanism 本来の刻みでは搬出が追いつかないため。",
                            "設定枠を入れてあるインターフェイス (在庫確保用) には効かない。",
                            "false にすると Mekanism 本来の搬出だけになる。")
                    .define("astralNetworkEject", true);

            builder.pop();
        }
    }
}
