package jp.main.taikun.insaneae.crafting;

import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerLimitBridge;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * ACOの理論上限を容量として使う、通常AE2クラフトストレージのユニット型。
 *
 * <p>これはQuantum CPUではない。標準{@code CraftingUnitBlock}と
 * {@code CraftingBlockEntity}を使い、通常のAE2クラフトCPUマルチブロックへ組み込まれる。</p>
 */
public final class BigIntegerCraftingUnitType implements ExactCraftingUnitType {

    /** ACOが無い場合でも、AE2のlong範囲で通常ストレージとして成立させる容量。 */
    private static final BigInteger LONG_FACADE_CAPACITY = BigInteger.valueOf(Long.MAX_VALUE);

    public static final BigIntegerCraftingUnitType INSTANCE =
            new BigIntegerCraftingUnitType(AcoBigIntegerLimitBridge::maximumSupportedAmount);

    private final Supplier<Optional<BigInteger>> exactCapacity;
    private Supplier<Item> item = () -> Items.AIR;

    BigIntegerCraftingUnitType(Supplier<Optional<BigInteger>> exactCapacity) {
        this.exactCapacity = exactCapacity;
    }

    /** BlockItem登録後に、AE2が型からアイテムを引けるよう結び付ける。 */
    public void setItem(Supplier<Item> item) {
        this.item = item;
    }

    @Override
    public long getStorageBytes() {
        // AE2の既存APIはlong固定なので、正確値に関係なく正の最大互換値だけを返す。
        return Long.MAX_VALUE;
    }

    @Override
    public BigInteger exactStorageBytes() {
        // ACOが使える場合だけ公開上限を採用し、未導入時に推測した巨大容量を作らない。
        return exactCapacity.get().orElse(LONG_FACADE_CAPACITY);
    }

    @Override
    public int getAcceleratorThreads() {
        // 通常クラフトストレージなので、並列処理能力は提供しない。
        return 0;
    }

    @Override
    public Item getItemFromType() {
        return item.get();
    }
}
