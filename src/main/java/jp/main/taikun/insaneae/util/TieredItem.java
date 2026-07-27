package jp.main.taikun.insaneae.util;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 表示名を {@link TieredNames} の書式から作るだけのアイテム (セルコンポーネントなど)。 */
public class TieredItem extends Item {

    private final String nameKey;
    private final String tierLabel;

    public TieredItem(Properties props, String nameKey, String tierLabel) {
        super(props);
        this.nameKey = nameKey;
        this.tierLabel = tierLabel;
    }

    @Override
    public Component getName(ItemStack stack) {
        return TieredNames.of(nameKey, tierLabel);
    }
}
