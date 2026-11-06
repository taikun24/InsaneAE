package jp.main.taikun.insaneae.config;

import org.apache.commons.lang3.tuple.Pair;
//? if >=1.21 {
import net.neoforged.neoforge.common.ModConfigSpec;
//?}
//? if <1.21 {
/*import net.minecraftforge.common.ForgeConfigSpec;
*///?}

/**
 * InsaneAE の設定 ({@code config/insaneae-common.toml})。
 *
 * <p>ここに並ぶのは<b>軽量化の切り替え</b>だけ。どれも既定は有効で、
 * 動作が変わったり他 Mod と噛み合わなかったりしたときに個別で切れるようにしてある。</p>
 *
 * <p>設定の読み出しは<b>実行時</b>に行うこと (Mixin の適用可否には使わない)。
 * 設定ファイルの読み込みは Mod 構築より後なので、
 * クラスロード時に値を確定させると既定値で固まってしまう。</p>
 */
public final class InsaneAEConfig {

    private static final Common COMMON;
    //? if >=1.21 {
    public static final ModConfigSpec SPEC;
    //?} else {
    /*public static final ForgeConfigSpec SPEC;
    *///?}

    static {
        //? if >=1.21 {
        Pair<Common, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Common::new);
        //?} else {
        /*Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        *///?}
        COMMON = pair.getLeft();
        SPEC = pair.getRight();
    }

    private InsaneAEConfig() {
    }

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

    public static int maxCraftingWindowsPerTick() {
        return get(COMMON.maxCraftingWindowsPerTick, 1024);
    }

    /** この回数以上まとめられるクラフトだけまとめて処理する。これ未満は AE2 本来の 1 回ずつに任せる。 */
    public static int craftingBatchThreshold() {
        return get(COMMON.craftingBatchThreshold, 16);
    }

    public static boolean serverSidePatternPaging() {
        return get(COMMON.serverSidePatternPaging, true);
    }
    //? if <1.21 {
    /*public static boolean astralNetworkEject() {
        return get(COMMON.astralNetworkEject, true);
    }
    *///?}

    /**
     * Quantum CPU が同時に受けられるクラフトの本数。
     *
     * <p>内蔵 CPU は容量を切り分けて何本も同時に走らせる。1 にすると
     * AE2 のクラフト CPU と同じ「1 台 1 ジョブ」に戻る。</p>
     */
    public static int quantumCpuMaxJobs() {
        return get(COMMON.quantumCpuMaxJobs, 16);
    }

    /**
     * 超次元ケーブル 1 本が運べるチャンネル数 (ChannelMode の倍率を掛ける前)。
     *
     * <p>置いた時点で常にこの本数。条件は無い。</p>
     */
    public static int hyperChannels() {
        return get(COMMON.hyperChannels, 128);
    }

    /**
     * 待ち時間で頭打ちになる機械 (刻印機など) を、1 tick のうちに何回まで回すか。
     *
     * <p>加速カードの倍率とこの値の小さい方が実際の回数になる。1 にすると追い tick を止めて
     * AE2 本来の「1 tick に 1 回」に戻る。</p>
     */
    public static int machineTickBurst() {
        return get(COMMON.machineTickBurst, 256);
    }

    /** 超次元パターンプロバイダーが、加工パターンを 1 tick にまとめて押し出すか。 */
    public static boolean processingBulkPush() {
        return get(COMMON.processingBulkPush, true);
    }

    /**
     * 設定ファイル読み込み前でも安全に読む。
     *
     * <p>{@code ConfigValue#get()} は読み込み前に呼ぶと例外になるので、
     * その場合はコード側の既定値を返す。</p>
     */
    //? if >=1.21 {
    private static <T> T get(ModConfigSpec.ConfigValue<T> value, T fallback) {
    //?} else {
    /*private static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
    *///?}
        if (!SPEC.isLoaded()) {
            return fallback;
        }
        return value.get();
    }

    private static final class Common {

        //? if >=1.21 {
        private final ModConfigSpec.BooleanValue batchCraftingCalculation;
        private final ModConfigSpec.IntValue craftingBatchThreshold;
        private final ModConfigSpec.BooleanValue serverSidePatternPaging;
        private final ModConfigSpec.IntValue maxCraftingWindowsPerTick;
        private final ModConfigSpec.IntValue quantumCpuMaxJobs;
        private final ModConfigSpec.IntValue hyperChannels;
        private final ModConfigSpec.IntValue machineTickBurst;
        private final ModConfigSpec.BooleanValue processingBulkPush;

        private Common(ModConfigSpec.Builder builder) {
        //?} else {
        /*private final ForgeConfigSpec.BooleanValue batchCraftingCalculation;
        private final ForgeConfigSpec.IntValue craftingBatchThreshold;
        private final ForgeConfigSpec.BooleanValue serverSidePatternPaging;
        private final ForgeConfigSpec.IntValue maxCraftingWindowsPerTick;
        private final ForgeConfigSpec.IntValue quantumCpuMaxJobs;
        private final ForgeConfigSpec.BooleanValue astralNetworkEject;
        private final ForgeConfigSpec.IntValue hyperChannels;
        private final ForgeConfigSpec.IntValue machineTickBurst;
        private final ForgeConfigSpec.BooleanValue processingBulkPush;

        private Common(ForgeConfigSpec.Builder builder) {
        *///?}
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
            //? if <1.21 {
            /*builder.pop();
            builder.comment("他 Mod との連携").push("compat");

            astralNetworkEject = builder
                    .comment("Astral Mekanism & Energistics の機械が ME インターフェイスへ自動搬出するとき、",
                            "スタック数や搬出レート (fluidAutoEjectRate / chemicalAutoEjectRate) で刻まずに",
                            "中身を全部ネットワークへ渡す。Astral の機械は 1 回の処理で long 級を作るので、",
                            "Mekanism 本来の刻みでは搬出が追いつかないため。",
                            "設定枠を入れてあるインターフェイス (在庫確保用) には効かない。",
                            "false にすると Mekanism 本来の搬出だけになる。")
                    .define("astralNetworkEject", true);
            *///?}

            quantumCpuMaxJobs = builder
                    .comment("内蔵クラフト CPU が同時に受けられるクラフトの本数。",
                            "挿してあるクラフトストレージの容量を発注のたびに切り分けるので、",
                            "大きなクラフトを 1 本始めても残量ぶんは発注できる。",
                            "同時に走っているぶんだけクラフト端末の CPU 一覧に行が増える。",
                            "協調処理スレッドは走っているジョブで均等に分けるので、",
                            "本数を増やしても合計の処理速度は変わらない。",
                            "1 にすると AE2 と同じ「1 台 1 ジョブ」に戻る。")
                    .defineInRange("quantumCpuMaxJobs", 16, 1, 256);

            builder.pop();
            builder.comment("ネットワーク (チャンネル)").push("network");

            hyperChannels = builder
                    .comment("超次元 ME ケーブル 1 本が運べるチャンネル数。",
                            "AE2 の上限は高密度ケーブルの 32 本で、そこを上書きする値。",
                            "ChannelMode (AE2 の設定) が x2 などなら、その倍率がさらに掛かる。",
                            "使用チャンネル数はネットワーク全体で int に収まる必要があるので、",
                            "際限なく上げないこと。")
                    .defineInRange("hyperChannels", 128, 32, 65536);

            builder.pop();
            builder.comment("機械 (加速カード)").push("machines");

            machineTickBurst = builder
                    .comment("加速カードを挿した機械を、1 tick のうちに何回まで回すか。",
                            "刻印機のように「加工が終わったあと決まった tick 数だけ待つ」機械は、",
                            "速度値をいくら上げても待ち時間 (刻印機なら 16 tick) で頭打ちになる。",
                            "そこを抜けるために、待ち時間ぶんの tick を同じ tick の中でまとめて回す。",
                            "実際の回数は加速カードの倍率とこの値の小さい方。材料切れ・出力満杯なら",
                            "そこで打ち切るので、動いていない機械の負荷は増えない。",
                            "1 にすると追い tick を止めて AE2 本来の挙動に戻る。",
                            "輸出入バスや IO ポートのように 1 tick の処理量そのものが速度で決まる機械は",
                            "対象外 (二重に掛かるため)。")
                    .defineInRange("machineTickBurst", 256, 1, 65536);

            builder.pop();
            builder.comment("超次元パターンプロバイダー").push("pattern_provider");

            processingBulkPush = builder
                    .comment("加工パターンの材料を、搬入先が受け取れる回数ぶんまとめて 1 回で押し出す。",
                            "AE2 は 1 クラフトごとに材料の取り出しと搬入をやり直すので、",
                            "1 tick に数千回を超えると重くなる。ここをまとめて 1 回にする。",
                            "隣にクラフト機械 (分子組立装置など) があるとき、ブロッキングモードのとき、",
                            "「結果が戻るまでロック」などのクラフトロックを使っているときは AE2 本来の処理のまま。",
                            "特定の機械で材料の入り方がおかしいと思ったら false にして比べること。")
                    .define("processingBulkPush", true);

            builder.pop();
        }
    }
}
