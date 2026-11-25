package jp.main.taikun.insaneae.cell;

import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
//? if >=1.21 {
import jp.main.taikun.insaneae.registries.ModDataComponents;
//?}
//? if <1.21 {
/*import jp.main.taikun.insaneae.cell.storage.CellNbt;
*///?}

/**
 * 超次元MEセルのアイテムに残す分。中身は {@code CellContentStore} にあり、アイテムはUUIDだけを持つ。
 *
 * <p>ほかのUUIDセルと同じ鍵を使うので、F3+Hのツールチップと {@code /insaneae cells recover} がそのまま効く。
 * 空のセルにはUUIDを付けない。付いたままだと新品と重ならない。</p>
 */
public final class InsaneMeCellData {

    private InsaneMeCellData() {
    }

    @Nullable
    public static UUID id(ItemStack stack) {
        //? if >=1.21 {
        return stack.get(ModDataComponents.CELL_ID.get());
        //?} else {
        /*return CellNbt.id(stack);
        *///?}
    }

    public static void setId(ItemStack stack, UUID id) {
        //? if >=1.21 {
        stack.set(ModDataComponents.CELL_ID.get(), id);
        //?} else {
        /*stack.getOrCreateTag().putUUID(CellNbt.ID, id);
        *///?}
    }

    public static void clearId(ItemStack stack) {
        //? if >=1.21 {
        stack.remove(ModDataComponents.CELL_ID.get());
        //?} else {
        /*var tag = stack.getTag();
        if (tag != null) {
            tag.remove(CellNbt.ID);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
        *///?}
    }
}
