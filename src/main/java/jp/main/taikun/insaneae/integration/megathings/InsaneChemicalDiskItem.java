package jp.main.taikun.insaneae.integration.megathings;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** 化学物質DISK。放射性の化学物質を弾くのはMEGA Thingsの化学物質DISKと同じ。 */
final class InsaneChemicalDiskItem extends InsaneMegaDiskItem {

    InsaneChemicalDiskItem(ItemLike component, ItemLike housing, long bytes, double idleDrain,
                           String nameKey, String tierLabel) {
        super(MekanismKeyType.TYPE, component, housing, bytes, idleDrain, nameKey, tierLabel);
    }

    @Override
    public boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) {
        return !(requestedAddition instanceof MekanismKey key)
                || !ChemicalAttributeValidator.DEFAULT.process(key.getStack());
    }
}
