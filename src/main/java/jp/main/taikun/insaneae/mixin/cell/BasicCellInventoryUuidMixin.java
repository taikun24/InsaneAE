package jp.main.taikun.insaneae.mixin.cell;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.me.cells.BasicCellInventory;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import jp.main.taikun.insaneae.cell.IHugeCellItem;
import jp.main.taikun.insaneae.cell.storage.CellContentStore;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
//? if >=1.21 {
import appeng.api.ids.AEComponents;
import jp.main.taikun.insaneae.cell.storage.CellSummary;
import jp.main.taikun.insaneae.registries.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
//?}
//? if <1.21 {
/*import it.unimi.dsi.fastutil.longs.LongArrayList;
import jp.main.taikun.insaneae.cell.storage.CellNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
*///?}

/**
 * {@link IHugeCellItem} なセルの中身を、アイテムではなく {@link CellContentStore} に置く。
 *
 * <p>AE2 が中身を読み書きするのは {@code getStoredStacks()} と {@code persist()} の 2 か所だけなので、
 * そこを差し替えれば入出庫・パーティション・カード・容量計算は AE2 の実装のまま使える。</p>
 *
 * <p>UUID の無いセルは AE2 の形 ({@code ae2:storage_cell_inv}) のまま読む。以前の版で中身を入れた
 * セルはこれに当たり、次に中身が変わって {@code persist()} が走ったときに保存データへ移る。</p>
 */
@Mixin(value = BasicCellInventory.class, remap = false)
public abstract class BasicCellInventoryUuidMixin {

    @Shadow
    @Final
    private IBasicCellItem cellType;

    @Shadow
    @Final
    private ItemStack i;

    @Shadow
    private Object2LongMap<AEKey> storedAmounts;

    @Shadow
    private boolean isPersisted;

    @Shadow
    //? if >=1.21 {
    private int storedItems;
    //?} else {
    /*private short storedItems;
    *///?}

    @Shadow
    private long storedItemCount;

    /**
     * 保存データに届かない場所 (クライアント) で開いた UUID 付きのセル。アイテム側の要約しか
     * 見えないので、書き戻すと要約の先頭数件だけが残って中身が消える。読むだけにする。
     */
    @Unique
    private boolean insaneae$readOnly;

    //? if >=1.21 {
    @Inject(method = "getStoredStacks", at = @At("HEAD"), cancellable = true)
    private void insaneae$storedStacksFromStore(CallbackInfoReturnable<List<GenericStack>> cir) {
    //?} else {
    /*@Inject(method = "<init>", at = @At("RETURN"))
    private void insaneae$detectReadOnly(IBasicCellItem cellType, ItemStack o, ISaveProvider container,
                                         CallbackInfo ci) {
        if (cellType instanceof IHugeCellItem && CellNbt.id(o) != null && CellContentStore.get() == null) {
            insaneae$readOnly = true;
        }
    }

    @Inject(method = "loadCellItems", at = @At("HEAD"), cancellable = true)
    private void insaneae$loadFromStore(CallbackInfo ci) {
    *///?}
        if (!(cellType instanceof IHugeCellItem)) {
            return;
        }
        //? if >=1.21 {
        UUID id = i.get(ModDataComponents.CELL_ID.get());
        //?} else {
        /*UUID id = CellNbt.id(i);
        *///?}
        if (id == null) {
            return;
        }
        //? if >=1.21 {
        CellContentStore store = CellContentStore.get();
        if (store == null) {
            insaneae$readOnly = true;
            CellSummary summary = i.get(ModDataComponents.CELL_SUMMARY.get());
            cir.setReturnValue(summary != null ? summary.preview() : List.of());
            return;
        //?} else {
        /*if (insaneae$readOnly) {
            for (GenericStack stack : CellNbt.preview(i)) {
                storedAmounts.put(stack.what(), stack.amount());
            }
        } else {
            CellContentStore store = CellContentStore.get();
            CellContentStore.Entry entry = store != null ? store.get(id) : null;
            if (entry != null) {
                entry.decode(storedAmounts);
            }
        *///?}
        }
    //? if >=1.21 {
        CellContentStore.Entry entry = store.get(id);
        cir.setReturnValue(entry != null ? entry.stacks() : List.of());
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void insaneae$countsFromSummary(IBasicCellItem cellType, ItemStack o, ISaveProvider container,
                                            CallbackInfo ci) {
        // コンストラクタは種類数と総数を getStoredStacks() から数えるが、読み取り専用のときは
        // それが要約の先頭数件でしかない。ツールチップの使用バイトや LED の色が合うよう、要約の値で上書きする。
        if (!insaneae$readOnly) {
            return;
        }
        CellSummary summary = o.get(ModDataComponents.CELL_SUMMARY.get());
        if (summary != null) {
            storedItems = summary.types();
            storedItemCount = summary.count();
        }
    //?} else {
        /*ci.cancel();
    *///?}
    }

    @Inject(method = "persist", at = @At("HEAD"), cancellable = true)
    private void insaneae$persistToStore(CallbackInfo ci) {
        if (!(cellType instanceof IHugeCellItem)) {
            return;
        }
        if (insaneae$readOnly) {
            isPersisted = true;
            ci.cancel();
            return;
        }
        if (isPersisted) {
            return;
        }
        //? if >=1.21 {
        UUID id = i.get(ModDataComponents.CELL_ID.get());
        //?} else {
        /*CompoundTag tag = i.getOrCreateTag();
        UUID id = CellNbt.id(i);
        *///?}
        CellContentStore store = CellContentStore.get();
        if (store == null) {
            // 開いた後にサーバーが止まった。AE2 の形でアイテムへ書き戻させ、UUID は外して
            // 次に読むときに古い保存データの方を拾わないようにする。
            //? if >=1.21 {
            i.remove(ModDataComponents.CELL_ID.get());
            i.remove(ModDataComponents.CELL_SUMMARY.get());
            //?} else {
            /*tag.remove(CellNbt.ID);
            tag.remove(CellNbt.SUMMARY);
            *///?}
            return;
        }

        long itemCount = 0;
        //? if >=1.21 {
        List<GenericStack> stacks = new ArrayList<>(storedAmounts.size());
        //?} else {
        /*LongArrayList amounts = new LongArrayList(storedAmounts.size());
        ListTag keys = new ListTag();
        *///?}
        for (var entry : storedAmounts.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            //? if >=1.21 {
            itemCount += amount;
            //?}
            if (amount > 0) {
                //? if >=1.21 {
                stacks.add(new GenericStack(entry.getKey(), amount));
                //?} else {
                /*itemCount += amount;
                keys.add(entry.getKey().toTagGeneric());
                amounts.add(amount);
                *///?}
            }
        }

        //? if >=1.21 {
        i.remove(AEComponents.STORAGE_CELL_INV);
        if (stacks.isEmpty()) {
        //?} else {
        /*tag.remove(CellNbt.KEYS);
        tag.remove(CellNbt.AMOUNTS);
        if (keys.isEmpty()) {
        *///?}
            // 空のセルには何も残さない。UUID が残ると、新品のセルと重ならず分解もできなくなる。
            if (id != null) {
                store.remove(id);
            }
            //? if >=1.21 {
            i.remove(ModDataComponents.CELL_ID.get());
            i.remove(ModDataComponents.CELL_SUMMARY.get());
            //?} else {
            /*tag.remove(CellNbt.ID);
            tag.remove(CellNbt.SUMMARY);
            tag.remove(CellNbt.ITEM_COUNT);
            if (tag.isEmpty()) {
                i.setTag(null);
            }
            *///?}
        } else {
            if (id == null) {
                id = UUID.randomUUID();
                //? if >=1.21 {
                i.set(ModDataComponents.CELL_ID.get(), id);
                //?} else {
                /*tag.putUUID(CellNbt.ID, id);
                *///?}
            }
            //? if >=1.21 {
            store.put(id, i.getItem(), stacks);
            i.set(ModDataComponents.CELL_SUMMARY.get(), CellSummary.of(stacks));
            //?} else {
            /*store.put(id, i.getItem(), keys, amounts.toLongArray());
            tag.put(CellNbt.SUMMARY, CellNbt.summary(storedAmounts));
            tag.putLong(CellNbt.ITEM_COUNT, itemCount);
            *///?}
        }

        //? if >=1.21 {
        storedItems = storedAmounts.size();
        //?} else {
        /*storedItems = (short) storedAmounts.size();
        *///?}
        storedItemCount = itemCount;
        isPersisted = true;
        ci.cancel();
    }

    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private void insaneae$readOnlyInsert(AEKey what, long amount, Actionable mode, IActionSource source,
                                         CallbackInfoReturnable<Long> cir) {
        if (insaneae$readOnly) {
            cir.setReturnValue(0L);
        }
    }

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void insaneae$readOnlyExtract(AEKey what, long amount, Actionable mode, IActionSource source,
                                          CallbackInfoReturnable<Long> cir) {
        if (insaneae$readOnly) {
            cir.setReturnValue(0L);
        }
    }
}
