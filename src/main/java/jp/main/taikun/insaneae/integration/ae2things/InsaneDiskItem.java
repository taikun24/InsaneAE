package jp.main.taikun.insaneae.integration.ae2things;

import io.github.projectet.ae2things.item.DISKDrive;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * InsaneAE階層のDISK。AE2 Thingsの {@link DISKDrive} をそのまま継承しているので、
 * 中身の保存、AE2 Thingsの{@code disk_manager}、{@code /ae2things recover}、分解
 * は、コンポーネントとDISKハウジングに戻る。カードはAE2 Thingsの実装が働く。
 *
 * <p>容量だけはAE2 Thingsがintで持つので、{@code DiskCellInventoryMixin} が
 * {@link #capacity()} を返すようにしている。</p>
 */
public final class InsaneDiskItem extends DISKDrive implements InsaneDisk {

    private final long capacity;
    private final String tierLabel;

    /** @param capacity入る個数。DISKはアイテム1個 = 1バイト。 */
    public InsaneDiskItem(ItemLike component, long capacity, double idleDrain, String tierLabel) {
        // 第2引数 (KiB) はintの容量にしか使われない。実容量はcapacityの方
        super(component, 0, idleDrain);
        this.capacity = capacity;
        this.tierLabel = tierLabel;
    }

    @Override
    public long capacity() {
        return capacity;
    }

    /** Mixinが外れたときの保険。intで頭打ちにした容量で動く。 */
    @Override
    public int getBytes(ItemStack cellItem) {
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    @Override
    public Component getName(ItemStack stack) {
        return TieredNames.of(TieredNames.DISK_DRIVE, tierLabel);
    }
}
