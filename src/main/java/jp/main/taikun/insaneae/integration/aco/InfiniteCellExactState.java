package jp.main.taikun.insaneae.integration.aco;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.StorageCell;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellInventory;

import java.math.BigInteger;
import java.util.UUID;

/**
 * 他Modの「無限に出せるセル」(ME Infinity Cell) がACOに名乗るBigIntegerの在庫。
 *
 * <p>そのセルはAE2には{@code Integer.MAX_VALUE}個、約21億しか申告しないので、
 * ACOのBigInteger計画ではそれ以上の材料が無いことになり、大きな注文が材料不足で止まる。
 * 中身は取り出し放題なので、超強化クリエイティブセルと同じ量をACOにだけ名乗らせる。</p>
 *
 * <p>守るべき約束は {@link InsaneUltraCreativeCellInventory} と同じ。ACOはシミュレーションと
 * コミットで同じMapインスタンスかを {@code ==} で比べ、そのMapを直接書き換え、
 * 合計と種類数もsetterで書いた値と突き合わせる。だからセル1つにつき1つ作って持ち続ける。</p>
 *
 * <p>ACOの型には触らない。Mixinから呼ぶだけなので、ACOが無くてもロードできる。</p>
 */
public final class InfiniteCellExactState {

    /** Mixinでセルに生やす、状態の置き場。 */
    public interface Holder {
        /** 状態を作っている最中、つまりセルの在庫を読んでいる間に呼ばれたときだけnull。 */
        @org.jetbrains.annotations.Nullable
        InfiniteCellExactState insaneae$infiniteExactState();
    }

    private final Object2ObjectMap<AEKey, BigInteger> amounts;
    private BigInteger total;
    private int typeCount;
    private UUID uuid;

    private InfiniteCellExactState(Object2ObjectMap<AEKey, BigInteger> amounts) {
        this.amounts = amounts;
        this.typeCount = amounts.size();
        this.total = InsaneUltraCreativeCellInventory.exactAmount().multiply(BigInteger.valueOf(amounts.size()));
    }

    /** セルがAE2に申告している種類をそのまま使う。量だけ差し替える。 */
    public static InfiniteCellExactState of(StorageCell cell) {
        KeyCounter stacks = new KeyCounter();
        cell.getAvailableStacks(stacks);
        BigInteger amount = InsaneUltraCreativeCellInventory.exactAmount();
        Object2ObjectMap<AEKey, BigInteger> amounts = new Object2ObjectLinkedOpenHashMap<>();
        for (var entry : stacks) {
            amounts.put(entry.getKey(), amount);
        }
        return new InfiniteCellExactState(amounts);
    }

    /** 毎回同じインスタンスを返す。 */
    public synchronized Object2ObjectMap<AEKey, BigInteger> amounts() {
        return amounts;
    }

    public synchronized BigInteger total() {
        return total;
    }

    public synchronized void setTotal(BigInteger total) {
        this.total = total == null ? BigInteger.ZERO : total;
    }

    public synchronized int typeCount() {
        return typeCount;
    }

    public synchronized void setTypeCount(int typeCount) {
        this.typeCount = typeCount;
    }

    public synchronized UUID uuid() {
        return uuid;
    }

    public synchronized UUID assignUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        return uuid;
    }
}
