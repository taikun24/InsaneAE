package jp.main.taikun.insaneae.iface;

import appeng.items.parts.PartItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 超特大インターフェイスのケーブル版のアイテム。
 *
 * <p>{@link PartItem} そのままで、名前だけブロック版と同じお遊びに乗せる
 * → {@link InsaneInterfaceNames}。</p>
 */
public class InsaneInterfacePartItem extends PartItem<InsaneInterfacePart> {

    public InsaneInterfacePartItem(Properties properties) {
        super(properties, InsaneInterfacePart.class, InsaneInterfacePart::new);
    }

    @Override
    public Component getName(ItemStack stack) {
        return InsaneInterfaceNames.of(InsaneInterfaceNames.PART_KEY);
    }
}
