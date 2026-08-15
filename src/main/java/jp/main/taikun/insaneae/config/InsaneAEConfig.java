package jp.main.taikun.insaneae.config;

import net.neoforged.neoforge.common.ModConfigSpec;
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
    public static final ModConfigSpec SPEC;

    static {
        Pair<Common, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Common::new);
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
    public static int maxCraftingWindowsPerTick() {
        return get(COMMON.maxCraftingWindowsPerTick, 1024);
    }

    public static int craftingBatchThreshold() {
        return get(COMMON.craftingBatchThreshold, 16);
    }

    /** Quantum CPU のパターン枠をサーバ側でページ分割するか。 */
    public static boolean serverSidePatternPaging() {
        return get(COMMON.serverSidePatternPaging, true);
    }

    /**
     * 設定ファイル読み込み前でも安全に読む。
     *
     * <p>{@code ConfigValue#get()} は読み込み前に呼ぶと例外になるので、
     * その場合はコード側の既定値を返す。</p>
     */
    private static <T> T get(ModConfigSpec.ConfigValue<T> value, T fallback) {
        if (!SPEC.isLoaded()) {
            return fallback;
        }
        return value.get();
    }

    private static final class Common {

        private final ModConfigSpec.BooleanValue batchCraftingCalculation;
        private final ModConfigSpec.IntValue craftingBatchThreshold;
        private final ModConfigSpec.BooleanValue serverSidePatternPaging;
        private final ModConfigSpec.IntValue maxCraftingWindowsPerTick;

        private Common(ModConfigSpec.Builder builder) {
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

            maxCraftingWindowsPerTick = builder
                    .comment("加速カードを満載 (7 枚) + タスク統合カードにしたとき、",
                            "1 tick のうちに同じパターンを何窓まで回すか。",
                            "1 窓に組める回数は long の会計 (完成品 Long.MAX / 出力数) で頭打ちになるため、",
                            "BigInteger 級の注文は窓を重ねないと終わらない。窓の間で完成品を",
                            "ネットワークへ流して完成待ちを清算するので、1 tick の合計は long を超えられる。",
                            "サーバが 1 tick に使う時間はこの値に比例するので、上げすぎると重くなる。")
                    .defineInRange("maxCraftingWindowsPerTick", 1024, 1, Integer.MAX_VALUE);

            builder.pop();
        }
    }
}
