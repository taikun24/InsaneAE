package jp.main.taikun.insaneae.cell.storage;

import jp.main.taikun.insaneae.InsaneAE;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
//? if >=1.21 {
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import org.slf4j.Logger;
import java.util.List;
//?}
//? if <1.21 {
/*import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import jp.main.taikun.insaneae.util.SaturatingMath;
*///?}

/**
 * UUID で管理しているストレージセルの中身。オーバーワールドの {@code data/insaneae_cells.dat} に入る。
 *
 * <p>中身をアイテムに持たせると、型数の多いセルではアイテム 1 個のデータが巨大になり、
 * ドライブのあるチャンクや持ち物の同期で通信量が膨れる (上位階層は 21 億種まで入る)。
 * ExtendedAE Plus の Infinity セルや AE2 Things の DISK と同じく、アイテムには UUID と
 * 要約だけを残し、本体はワールド側に置く。</p>
 *
 * <p>セルの出し入れは AE2 のワーカースレッドからも来るので、表は並行アクセスに耐える形にしている。</p>
 */
public final class CellContentStore extends SavedData {

    //? if >=1.21 {
    private static final Logger LOGGER = LogUtils.getLogger();
    //?}
    private static final String FILE_NAME = InsaneAE.MODID + "_cells";
    private static final String CELLS = "cells";
    private static final String ID = "id";
    private static final String ITEM = "item";
    //? if >=1.21 {
    private static final String STACKS = "stacks";

    private static final Factory<CellContentStore> FACTORY =
            new Factory<>(CellContentStore::new, CellContentStore::load, null);
    //?}

    @Nullable
    private static volatile Cached cached;

    private record Cached(MinecraftServer server, CellContentStore store) {
    }

    /** {@code item} は {@code /insaneae cells recover} でセルを作り直すときに使う。 */
    //? if >=1.21 {
    public record Entry(ResourceLocation item, List<GenericStack> stacks) {
        public Entry {
            stacks = List.copyOf(stacks);
    //?} else {
    /*public record Entry(ResourceLocation item, ListTag keys, long[] amounts) {
        public int types() {
            return amounts.length;
        }

        public long count() {
            long count = 0;
            for (long amount : amounts) {
                count = SaturatingMath.add(count, amount);
            }
            return count;
        }

        /^* AE2 の {@code loadCellItems} と同じ読み方。読めないキーは飛ばす。 ^/
        public void decode(Object2LongMap<AEKey> out) {
            int size = Math.min(amounts.length, keys.size());
            for (int index = 0; index < size; index++) {
                AEKey key = AEKey.fromTagGeneric(keys.getCompound(index));
                if (key != null && amounts[index] > 0) {
                    out.put(key, amounts[index]);
                }
            }
    *///?}
        }
    }

    private final Map<UUID, Entry> cells = new ConcurrentHashMap<>();

    private CellContentStore() {
    }

    /**
     * 今のスレッドから使える保存データ。クライアントの描画スレッドと、サーバーが動いていないときは null。
     *
     * <p>シングルプレイではクライアントからも内蔵サーバーが見えるが、描画スレッドから
     * サーバーの保存データを書き換えさせないために除外している。null のときセルは
     * アイテム側の要約だけを見る読み取り専用になる。</p>
     */
    @Nullable
    public static CellContentStore get() {
        if (FMLEnvironment.dist.isClient() && ClientThread.isRenderThread()) {
            return null;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        Cached current = cached;
        if (current != null && current.server == server) {
            return current.store;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return null;
        }
        synchronized (CellContentStore.class) {
            current = cached;
            if (current != null && current.server == server) {
                return current.store;
            }
            //? if >=1.21 {
            CellContentStore store = overworld.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
            //?} else {
            /*CellContentStore store = overworld.getDataStorage()
                    .computeIfAbsent(CellContentStore::load, CellContentStore::new, FILE_NAME);
            *///?}
            cached = new Cached(server, store);
            return store;
        }
    }

    /**
     * 各ディメンションのチャンク保存の後に呼ぶ ({@code LevelEvent.Save})。
     *
     * <p>ME ドライブはセルの中身をチャンク保存の途中 (ブロックエンティティの書き出し時) に
     * {@code persist()} する。一方バニラはディメンションごとに<b>保存データを先、チャンクを後</b>に
     * 書くので、そのままではその回の変更が保存データに入らず、停止時には失われる。
     * 全ディメンションのチャンク保存が終わった後にもう一度書けば取りこぼさない。</p>
     */
    public static void onLevelSaved(ServerLevel level) {
        Cached current = cached;
        if (current != null && current.server == level.getServer() && current.store.isDirty()) {
            ServerLevel overworld = level.getServer().overworld();
            if (overworld != null) {
                overworld.getDataStorage().save();
            }
        }
    }

    /** サーバー停止時に呼ぶ。次のワールドで前のワールドの表を掴まないように。 */
    public static void forget() {
        cached = null;
    }

    @Nullable
    public Entry get(UUID id) {
        return cells.get(id);
    }

    public Map<UUID, Entry> entries() {
        return Collections.unmodifiableMap(cells);
    }

    //? if >=1.21 {
    public void put(UUID id, Item item, List<GenericStack> stacks) {
        cells.put(id, new Entry(BuiltInRegistries.ITEM.getKey(item), stacks));
    //?} else {
    /*public void put(UUID id, Item item, ListTag keys, long[] amounts) {
        cells.put(id, new Entry(BuiltInRegistries.ITEM.getKey(item), keys, amounts));
    *///?}
        setDirty();
    }

    public void remove(UUID id) {
        if (cells.remove(id) != null) {
            setDirty();
        }
    }

    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        ListTag list = new ListTag();
        cells.forEach((id, entry) -> {
            CompoundTag cell = new CompoundTag();
            cell.putUUID(ID, id);
            cell.putString(ITEM, entry.item().toString());
            //? if >=1.21 {
            GenericStack.FAULT_TOLERANT_LIST_CODEC.encodeStart(ops, entry.stacks())
                    .resultOrPartial(error -> LOGGER.error("InsaneAE: failed to save cell {}: {}", id, error))
                    .ifPresent(stacks -> cell.put(STACKS, stacks));
            //?} else {
            /*cell.put(CellNbt.KEYS, entry.keys());
            cell.putLongArray(CellNbt.AMOUNTS, entry.amounts());
            *///?}
            list.add(cell);
        });
        tag.put(CELLS, list);
        return tag;
    }

    //? if >=1.21 {
    private static CellContentStore load(CompoundTag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
    //?} else {
    /*private static CellContentStore load(CompoundTag tag) {
    *///?}
        CellContentStore store = new CellContentStore();
        ListTag list = tag.getList(CELLS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag cell = list.getCompound(index);
            if (!cell.hasUUID(ID)) {
                continue;
            }
            //? if >=1.21 {
            UUID id = cell.getUUID(ID);
            //?}
            ResourceLocation item = ResourceLocation.tryParse(cell.getString(ITEM));
            // 消えた Mod のアイテムが混じっていても、AE2 のセルと同じく「欠けた中身」として残す。
            //? if >=1.21 {
            List<GenericStack> stacks = !cell.contains(STACKS) ? List.of()
                    : GenericStack.FAULT_TOLERANT_LIST_CODEC.parse(ops, cell.get(STACKS))
                            .resultOrPartial(error -> LOGGER.error("InsaneAE: failed to load cell {}: {}", id, error))
                            .orElse(List.of());
            store.cells.put(id, new Entry(item != null ? item : BuiltInRegistries.ITEM.getDefaultKey(), stacks));
            //?} else {
            /*store.cells.put(cell.getUUID(ID), new Entry(
                    item != null ? item : BuiltInRegistries.ITEM.getDefaultKey(),
                    cell.getList(CellNbt.KEYS, Tag.TAG_COMPOUND),
                    cell.getLongArray(CellNbt.AMOUNTS)));
            *///?}
        }
        return store;
    }

    /** 描画スレッドの判定。クライアント専用クラスに触るので、dist の確認の後でだけ呼ぶ。 */
    private static final class ClientThread {
        static boolean isRenderThread() {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            return minecraft != null && minecraft.isSameThread();
        }
    }
}
