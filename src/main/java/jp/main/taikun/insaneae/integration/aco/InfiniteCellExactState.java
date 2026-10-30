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
 * 他 Mod の「無限に出せるセル」(ME Infinity Cell) が ACO に名乗る BigInteger の在庫。
 *
 * <p>そのセルは AE2 には {@code Integer.MAX_VALUE} 個 (約 21 億) しか申告しないので、
 * ACO の BigInteger 計画ではそれ以上の材料が無いことになり、大きな注文が材料不足で止まる。
 * 中身は取り出し放題なので、超強化クリエイティブセルと同じ量を ACO にだけ名乗らせる。</p>
 *
 * <p>守るべき約束は {@link InsaneUltraCreativeCellInventory} と同じ。ACO はシミュレーションと
 * コミットで<b>同じ Map インスタンス</b>かを {@code ==} で比べ、その Map を直接書き換え、
 * 合計と種類数も setter で書いた値と突き合わせる。だからセル 1 つにつき 1 つ作って持ち続ける。</p>
 *
 * <p>ACO の型には触らない (Mixin から呼ぶだけ) ので、ACO が無くてもロードできる。</p>
 */
public final class InfiniteCellExactState {

    /** Mixin でセルに生やす、状態の置き場。 */
    public interface Holder {
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

    /** セルが AE2 に申告している種類をそのまま使う (量だけ差し替える)。 */
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

    /** <b>毎回同じインスタンスを返す。</b> */
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
