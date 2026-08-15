package jp.main.taikun.insaneae.cell;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.math.BigInteger;
import jp.main.taikun.insaneae.integration.aco.AcoExactLimits;
import net.minecraft.world.item.ItemStack;

/**
 * 超強化クリエイティブセルの中身。
 *
 * <p>AE2 に見せる在庫は親と同じ {@code Long.MAX_VALUE} ({@code KeyCounter} が long なので
 * これ以上は表現できない)。<b>BigInteger の在庫を名乗る窓口</b>は ACO が居るときだけ
 * Mixin で生える ({@code mixin/aco/UltraCreativeCellExactStorageMixin})。</p>
 *
 * <p>量は {@link AcoExactLimits#advertisableBits()} から決める。
 * <b>固定値にしてはいけない</b> — ACO の計画エンジンが扱える桁は
 * {@code ACOConfig.bigIntegerMaximumBits} (上限 54,427 bit) で、
 * {@code api.contract.ExactCountLimits} の 1,048,576 bit とは<b>別物</b>。
 * 大きすぎる在庫を名乗ると計算が例外で終わり、このセルを入れただけで
 * あらゆるクラフトが {@code WidePlanUnavailableException} になる。</p>
 *
 * <h2>在庫のマップは「生きた 1 つ」でなければならない</h2>
 * <p>ACO の {@code ExactNetworkStorageBridge} は、シミュレーション時に受け取ったマップと
 * コミット時に受け取ったマップを<b>参照の同一性 ({@code ==}) で比べる</b>。そして
 * <b>そのマップを直接書き換えて</b>在庫を減らす (相手は ExtendedAE Plus の実セルなので、
 * マップがセルの中身そのものになっている)。
 *
 * <p>だから毎回コピーを返してはいけない。返すと必ず
 * {@code exact cell storage map changed between simulation and commit} で
 * <b>取引ごと巻き戻される</b> — クラフトは進まず、警告だけが延々と出る。
 * 合計と種類数も、向こうの setter で書かれた値をそのまま覚えておく必要がある
 * (こちらも {@code beforeTotal} / {@code beforeTypes} と突き合わされる)。</p>
 *
 * <p>クリエイティブなので減っても構わない。初期値が 2^27212 なので、
 * 減らし切ることは現実的に起こらないし、セルを入れ直せば元に戻る。</p>
 */
public class InsaneUltraCreativeCellInventory extends InsaneCreativeCellInventory {

    /** 直前に使ったビット数と、その値。設定は実行中に変えられるので対で持つ。 */
    private static volatile int cachedBits = -1;
    private static volatile BigInteger cachedAmount = BigInteger.ZERO;

    /** ACO へ渡す<b>生きたマップ</b>。同じインスタンスを返し続けること。 */
    private Object2ObjectMap<AEKey, BigInteger> exactAmounts;
    private BigInteger exactTotal;
    private int exactTypeCount;

    public InsaneUltraCreativeCellInventory(ItemStack stack) {
        super(stack);
    }

    /**
     * 1 種あたりの在庫量。ACO が扱える桁の<b>半分</b>を使う。
     *
     * <p>既定 (54,427 bit) なら 2^27212 — 10 進で 8,192 桁ほど。
     * 922 京 (19 桁) とは比べものにならない大きさで、なお上限まで同じだけ余地がある。</p>
     */
    public static BigInteger exactAmount() {
        int bits = AcoExactLimits.advertisableBits();
        if (bits != cachedBits) {
            cachedAmount = BigInteger.ONE.shiftLeft(bits);
            cachedBits = bits;
        }
        return cachedAmount;
    }

    /**
     * ACO 連携の Mixin から呼ぶ。<b>毎回まったく同じインスタンス</b>を返す。
     *
     * <p>ここでコピーを返すと ACO の同一性検査に落ちる (クラス javadoc 参照)。</p>
     */
    public synchronized Object2ObjectMap<AEKey, BigInteger> insaneae$exactAmounts() {
        if (exactAmounts == null) {
            BigInteger amount = exactAmount();
            Object2ObjectMap<AEKey, BigInteger> amounts = new Object2ObjectLinkedOpenHashMap<>();
            for (AEKey key : configured) {
                amounts.put(key, amount);
            }
            exactAmounts = amounts;
            exactTypeCount = amounts.size();
            exactTotal = amount.multiply(BigInteger.valueOf(amounts.size()));
        }
        return exactAmounts;
    }

    /** 種類数。ACO が書いた値をそのまま覚えておく。 */
    public synchronized int insaneae$exactTypeCount() {
        insaneae$exactAmounts();
        return exactTypeCount;
    }

    public synchronized void insaneae$setExactTypeCount(int typeCount) {
        insaneae$exactAmounts();
        exactTypeCount = typeCount;
    }

    /** 全種類の合計。同上。 */
    public synchronized BigInteger insaneae$exactTotal() {
        insaneae$exactAmounts();
        return exactTotal;
    }

    public synchronized void insaneae$setExactTotal(BigInteger total) {
        insaneae$exactAmounts();
        exactTotal = total == null ? BigInteger.ZERO : total;
    }
}
