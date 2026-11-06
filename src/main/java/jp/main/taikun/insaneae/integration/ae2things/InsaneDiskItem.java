package jp.main.taikun.insaneae.integration.ae2things;

import io.github.projectet.ae2things.item.DISKDrive;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * InsaneAE 階層の DISK。AE2 Things の {@link DISKDrive} をそのまま継承しているので、
 * 中身の保存 (AE2 Things の {@code disk_manager})・{@code /ae2things recover}・分解
 * (コンポーネント + DISK ハウジングに戻る)・カードは AE2 Things の実装が働く。
 *
 * <p>容量だけは AE2 Things が int で持つので、{@code DiskCellInventoryMixin} が
 * {@link #capacity()} を返すようにしている。</p>
 */
public final class InsaneDiskItem extends DISKDrive {

    private final long capacity;
    private final String tierLabel;

    /** @param capacity 入る個数。DISK はアイテム 1 個 = 1 バイト。 */
    public InsaneDiskItem(ItemLike component, long capacity, double idleDrain, String tierLabel) {
        // 第 2 引数 (KiB) は int の容量にしか使われない。実容量は capacity の方。
        super(component, 0, idleDrain);
        this.capacity = capacity;
        this.tierLabel = tierLabel;
    }

    public long capacity() {
        return capacity;
    }

    /** Mixin が外れたときの保険。int で頭打ちにした容量で動く。 */
    @Override
    public int getBytes(ItemStack cellItem) {
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    @Override
    public Component getName(ItemStack stack) {
        return TieredNames.of(TieredNames.DISK_DRIVE, tierLabel);
    }
}
