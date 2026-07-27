package jp.main.taikun.insaneae.upgrade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * AE2 の加速カードより速い「限界突破」加速カードの階層。
 *
 * <p>倍率は<b>機械の処理速度そのものに掛かる</b>。AE2 の加速カードは機械側が
 * {@code switch (枚数)} で速度を決めており、枚数を増やしても表の範囲外は最低速に落ちるだけなので、
 * このカードは枚数ではなく機械の速度値に倍率を掛ける方式で実装している
 * ({@code IOBusPartMixin} ほか)。よって AE2 の加速カードと併用でき、効果は掛け算になる。</p>
 */
public enum InsaneSpeedCardType {
    TURBO("turbo_card", 8),
    OVERCLOCK("overclock_card", 64),
    HYPERSONIC("hypersonic_card", 512),
    WARP("warp_card", 4096);

    private final String id;
    private final int multiplier;

    private Supplier<Item> item = () -> Items.AIR;

    InsaneSpeedCardType(String id, int multiplier) {
        this.id = id;
        this.multiplier = multiplier;
    }

    /** 登録名。例: "turbo_card"。 */
    public String id() {
        return id;
    }

    /** 何倍速か。 */
    public int multiplier() {
        return multiplier;
    }

    public void setItem(Supplier<Item> item) {
        this.item = item;
    }

    public Item item() {
        return item.get();
    }
}
