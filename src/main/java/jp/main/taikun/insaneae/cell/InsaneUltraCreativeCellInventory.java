package jp.main.taikun.insaneae.cell;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/**
 * 超強化クリエイティブセルの中身。
 *
 * <p>AE2 に見せる在庫は親と同じ {@code Long.MAX_VALUE} ({@code KeyCounter} が long なので
 * これ以上は表現できない)。<b>BigInteger の在庫を名乗る窓口</b>は ACO が居るときだけ
 * Mixin で生える ({@code mixin/aco/UltraCreativeCellExactStorageMixin})。</p>
 *
 * <p>量を上限そのものではなく<b>半分</b>にしてあるのは、ACO の
 * {@code ExactCountLimits} が「受け付ける最大値」と「保存できる正準バイト数」を
 * <b>ちょうど揃えて</b>あるため。上限いっぱいを名乗ると、計算の途中で少しでも足された
 * 瞬間に境界を越えて弾かれる。半分なら足し算の余地が残る。</p>
 */
public class InsaneUltraCreativeCellInventory extends InsaneCreativeCellInventory {

    /**
     * 1 種あたりの在庫量。ACO の上限 (2^1048576 - 1) の半分 = 2^1048575。
     *
     * <p>10 進で 31 万桁ほどある。数としては巨大だが、正準形は 131,072 バイトで
     * ACO の {@code maximumCanonicalCountBytes} にちょうど収まる。</p>
     */
    public static final BigInteger EXACT_AMOUNT = BigInteger.ONE.shiftLeft(1_048_575);

    public InsaneUltraCreativeCellInventory(ItemStack stack) {
        super(stack);
    }

    /** ACO 連携の Mixin から呼ぶ。設定済みの種類ぶんの BigInteger 在庫。 */
    public Map<AEKey, BigInteger> insaneae$exactAmounts() {
        Map<AEKey, BigInteger> amounts = new LinkedHashMap<>();
        for (AEKey key : configured) {
            amounts.put(key, EXACT_AMOUNT);
        }
        return amounts;
    }

    /** 設定済みの種類数。 */
    public int insaneae$exactTypeCount() {
        return configured.size();
    }

    /** 全種類の合計。 */
    public BigInteger insaneae$exactTotal() {
        return EXACT_AMOUNT.multiply(BigInteger.valueOf(configured.size()));
    }
}
