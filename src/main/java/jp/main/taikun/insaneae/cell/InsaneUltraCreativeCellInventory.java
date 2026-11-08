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
 * <p>AE2に見せる在庫は親と同じ{@code Long.MAX_VALUE}で、{@code KeyCounter}がlongなので
 * これ以上は表現できない)。BigIntegerの在庫を名乗る窓口はACOが居るときだけ
 * Mixinで生える ({@code mixin/aco/UltraCreativeCellExactStorageMixin})。</p>
 *
 * <p>量は {@link AcoExactLimits#advertisableBits()} から決める。
 * 固定値にしてはいけない — ACOの計画エンジンが扱える桁は
 * {@code ACOConfig.bigIntegerMaximumBits}が上限の54,427 bitで、
 * {@code api.contract.ExactCountLimits} の1,048,576 bitとは別物。
 * 大きすぎる在庫を名乗ると計算が例外で終わり、このセルを入れただけで
 * あらゆるクラフトが {@code WidePlanUnavailableException} になる。</p>
 *
 * <h2>在庫のマップは「生きた1つ」でなければならない</h2>
 * <p>ACOの {@code ExactNetworkStorageBridge} は、シミュレーション時に受け取ったマップと
 * コミット時に受け取ったマップを参照の同一性 ({@code ==}) で比べる。そして
 * そのマップを直接書き換えて在庫を減らす。相手はExtendedAE Plusの実セルなので、
 * マップがセルの中身そのものになっている)。
 *
 * <p>だから毎回コピーを返してはいけない。返すと必ず
 * {@code exact cell storage map changed between simulation and commit} で
 * 取引ごと巻き戻される — クラフトは進まず、警告だけが延々と出る。
 * 合計と種類数も、向こうのsetterで書かれた値をそのまま覚えておく必要がある
 * こちらも{@code beforeTotal}と{@code beforeTypes}と突き合わされる。</p>
 *
 * <p>クリエイティブなので減っても構わない。初期値が2^27212なので、
 * 減らし切ることは現実的に起こらないし、セルを入れ直せば元に戻る。</p>
 */
public class InsaneUltraCreativeCellInventory extends InsaneCreativeCellInventory {

    /** 直前に使ったビット数と、その値。設定は実行中に変えられるので対で持つ。 */
    private static volatile int cachedBits = -1;
    private static volatile BigInteger cachedAmount = BigInteger.ZERO;

    /** ACOへ渡す生きたマップ。同じインスタンスを返し続けること。 */
    private Object2ObjectMap<AEKey, BigInteger> exactAmounts;
    private BigInteger exactTotal;
    private int exactTypeCount;

    public InsaneUltraCreativeCellInventory(ItemStack stack) {
        super(stack);
    }

    /**
     * 1種あたりの在庫量。ACOが扱える桁の半分を使う。
     *
     * <p>既定の54,427 bitなら2^27212で、10進で8,192桁ほど。
     * 922京、19桁とは比べものにならない大きさで、なお上限まで同じだけ余地がある。</p>
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
     * ACO連携のMixinから呼ぶ。毎回まったく同じインスタンスを返す。
     *
     * <p>ここでコピーを返すとACOの同一性検査に落ちる。クラスのJavadocを参照。</p>
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

    /** 種類数。ACOが書いた値をそのまま覚えておく。 */
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
