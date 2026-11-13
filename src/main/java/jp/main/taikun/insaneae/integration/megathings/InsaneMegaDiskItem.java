package jp.main.taikun.insaneae.integration.megathings;

import appeng.api.stacks.AEKeyType;
import io.github.lapis256.ae2_mega_things.item.AbstractDISKDrive;
import jp.main.taikun.insaneae.integration.ae2things.InsaneDisk;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * InsaneAE階層の液体・化学物質DISK。AE2 MEGA Thingsの {@link AbstractDISKDrive} を継承するので、
 * MEGA Thingsのセルハンドラが拾い、中身はAE2 Thingsの保存データに入る。分解でコンポーネントと
 * ハウジングに戻るのもMEGA Thingsの実装。
 *
 * <p>MEGA Thingsは1000 mBを1バイトと数えるが、中身の合計はmBのlongで持つ。そのため容量は
 * {@code Long.MAX_VALUE / 1000} バイトで頭打ちにする (16Pだけが該当)。</p>
 */
public class InsaneMegaDiskItem extends AbstractDISKDrive implements InsaneDisk {

    private final long capacity;
    private final String nameKey;
    private final String tierLabel;

    public InsaneMegaDiskItem(AEKeyType keyType, ItemLike component, ItemLike housing, long bytes,
                              double idleDrain, String nameKey, String tierLabel) {
        // 第4引数 (KB) はintの容量にしか使われない。実容量はcapacityの方
        super(keyType, component, housing, 0, idleDrain);
        this.capacity = capacityFor(bytes, keyType.getAmountPerUnit());
        this.nameKey = nameKey;
        this.tierLabel = tierLabel;
    }

    /** 満杯でも中身の合計 (mB) がlongに収まる上限。端数の入れ込みぶん (1単位未満) も残す。 */
    static long capacityFor(long bytes, int amountPerUnit) {
        return Math.min(bytes, (Long.MAX_VALUE - (amountPerUnit - 1)) / amountPerUnit);
    }

    @Override
    public long capacity() {
        return capacity;
    }

    /** Mixinが外れたときの保険。intで頭打ちにした容量で動く。 */
    @Override
    public int getBytes(ItemStack stack) {
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    @Override
    public Component getName(ItemStack stack) {
        return TieredNames.of(nameKey, tierLabel);
    }
}
