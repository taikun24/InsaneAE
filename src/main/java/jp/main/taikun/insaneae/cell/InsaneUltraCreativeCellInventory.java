package jp.main.taikun.insaneae.cell;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
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
 */
public class InsaneUltraCreativeCellInventory extends InsaneCreativeCellInventory {

    /** 直前に使ったビット数と、その値。設定は実行中に変えられるので対で持つ。 */
    private static volatile int cachedBits = -1;
    private static volatile BigInteger cachedAmount = BigInteger.ZERO;

    public InsaneUltraCreativeCellInventory(ItemStack stack) {
        super(stack);
    }

    /**
     * 1 種あたりの在庫量。ACO が扱える桁の<b>半分</b>を使う。
     *
     * <p>既定 (54,427 bit) なら 2^27213 — 10 進で 8,192 桁ほど。
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

    /** ACO 連携の Mixin から呼ぶ。設定済みの種類ぶんの BigInteger 在庫。 */
    public Map<AEKey, BigInteger> insaneae$exactAmounts() {
        BigInteger amount = exactAmount();
        Map<AEKey, BigInteger> amounts = new LinkedHashMap<>();
        for (AEKey key : configured) {
            amounts.put(key, amount);
        }
        return amounts;
    }

    /** 設定済みの種類数。 */
    public int insaneae$exactTypeCount() {
        return configured.size();
    }

    /** 全種類の合計。 */
    public BigInteger insaneae$exactTotal() {
        return exactAmount().multiply(BigInteger.valueOf(configured.size()));
    }
}
