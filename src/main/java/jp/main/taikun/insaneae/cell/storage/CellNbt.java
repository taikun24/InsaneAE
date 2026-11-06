package jp.main.taikun.insaneae.cell.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * UUID で管理しているセルの、アイテムの NBT に残す分。
 *
 * <p>総数は AE2 と同じ {@code ic} に書く (AE2 のコンストラクタがそこから読むので、
 * クライアントでも使用バイトがそのまま合う)。種類数と中身の先頭 {@link #PREVIEW_SIZE} 件は
 * {@code insaneae_cell_summary} に持たせる。AE2 のツールチップは中身を
 * {@code tooltipMaxCellContentShown} (既定 5) 件まで描き、それより多いと「…」を付けるので、
 * その判定が付くよう既定値より少し多めにしている。</p>
 */
public final class CellNbt {

    public static final String ID = "insaneae_cell_id";
    public static final String SUMMARY = "insaneae_cell_summary";
    /** AE2 の {@code BasicCellInventory} と同じタグ名。 */
    public static final String KEYS = "keys";
    public static final String AMOUNTS = "amts";
    public static final String ITEM_COUNT = "ic";

    public static final int PREVIEW_SIZE = 16;
    private static final String TYPES = "types";
    private static final String PREVIEW = "preview";

    private CellNbt() {
    }

    @Nullable
    public static UUID id(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(ID) ? tag.getUUID(ID) : null;
    }

    /** 保存されている種類数。UUID の無いセルは AE2 と同じく {@code amts} の長さ。 */
    public static long storedTypes(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return 0;
        }
        if (tag.hasUUID(ID)) {
            return tag.getCompound(SUMMARY).getInt(TYPES);
        }
        return tag.getLongArray(AMOUNTS).length;
    }

    /** 要約の中身 (量の多い順の先頭だけ)。読み取り専用のときの在庫として使う。 */
    public static List<GenericStack> preview(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return List.of();
        }
        ListTag list = tag.getCompound(SUMMARY).getList(PREVIEW, Tag.TAG_COMPOUND);
        List<GenericStack> preview = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            GenericStack entry = GenericStack.readTag(list.getCompound(index));
            if (entry != null) {
                preview.add(entry);
            }
        }
        return preview;
    }

    public static CompoundTag summary(Object2LongMap<AEKey> amounts) {
        // 先頭 PREVIEW_SIZE 件だけを残す。全体を並べ替えると数億種のセルで重い。
        PriorityQueue<GenericStack> top = new PriorityQueue<>(PREVIEW_SIZE + 1,
                Comparator.comparingLong(GenericStack::amount));
        int types = 0;
        for (var entry : amounts.object2LongEntrySet()) {
            if (entry.getLongValue() <= 0) {
                continue;
            }
            types++;
            top.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            if (top.size() > PREVIEW_SIZE) {
                top.poll();
            }
        }
        List<GenericStack> sorted = new ArrayList<>(top);
        sorted.sort(Comparator.comparingLong(GenericStack::amount).reversed());
        ListTag preview = new ListTag();
        sorted.forEach(stack -> preview.add(GenericStack.writeTag(stack)));
        CompoundTag summary = new CompoundTag();
        summary.putInt(TYPES, types);
        summary.put(PREVIEW, preview);
        return summary;
    }

    /** {@code /insaneae cells recover} 用。保存データの中身から要約を作り直す。 */
    public static CompoundTag summary(CellContentStore.Entry entry) {
        Object2LongMap<AEKey> amounts = new Object2LongOpenHashMap<>();
        entry.decode(amounts);
        return summary(amounts);
    }
}
