package jp.main.taikun.insaneae.cell.storage;

import appeng.api.stacks.AEKey;
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

import java.math.BigInteger;
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
/*import it.unimi.dsi.fastutil.objects.Object2LongMap;
import jp.main.taikun.insaneae.util.SaturatingMath;
*///?}

/**
 * UUIDで管理しているストレージセルの中身。オーバーワールドの {@code data/insaneae_cells.dat} に入る。
 *
 * <p>中身をアイテムに持たせると、型数の多いセルではアイテム1個のデータが巨大になり、
 * ドライブのあるチャンクや持ち物の同期で通信量が膨れる。上位階層は21億種まで入るため。
 * ExtendedAE PlusのInfinityセルやAE2 ThingsのDISKと同じく、アイテムにはUUIDと
 * 要約だけを残し、本体はワールド側に置く。</p>
 *
 * <p>セルの出し入れはAE2のワーカースレッドからも来るので、表は並行アクセスに耐える形にしている。</p>
 */
public final class CellContentStore extends SavedData {

    //? if >=1.21 {
    private static final Logger LOGGER = LogUtils.getLogger();
    //?}
    private static final String FILE_NAME = InsaneAE.MODID + "_cells";
    private static final String CELLS = "cells";
    private static final String ID = "id";
    private static final String ITEM = "item";
    private static final String BIG_CELLS = "big_cells";
    private static final String CONTENTS = "contents";
    private static final String KEY = "key";
    private static final String AMOUNT = "amount";
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

    /**
     * 超次元MEセルの中身。量がlongに収まらないので {@link #cells} とは別に持つ。
     *
     * <p>表はセルの在庫が直接書き換える。保存のたびに写しを作り直さずに済むよう、
     * 書き出しはワールドの保存時だけにしている。</p>
     */
    public record BigEntry(ResourceLocation item, Map<AEKey, BigInteger> amounts) {
    }

    private final Map<UUID, Entry> cells = new ConcurrentHashMap<>();
    private final Map<UUID, BigEntry> bigCells = new ConcurrentHashMap<>();

    private CellContentStore() {
    }

    /**
     * 今のスレッドから使える保存データ。クライアントの描画スレッドと、サーバーが動いていないときはnull。
     *
     * <p>シングルプレイではクライアントからも内蔵サーバーが見えるが、描画スレッドから
     * サーバーの保存データを書き換えさせないために除外している。nullのときセルは
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
     * <p>MEドライブはセルの中身を、チャンク保存の途中のブロックエンティティの書き出し時に
     * {@code persist()} する。一方バニラはディメンションごとに保存データを先、チャンクを後に
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

    @Nullable
    public BigEntry getBig(UUID id) {
        return bigCells.get(id);
    }

    public Map<UUID, BigEntry> bigEntries() {
        return Collections.unmodifiableMap(bigCells);
    }

    /** 空の中身を作る。同じUUIDが既にあればそれを返す。 */
    public BigEntry createBig(UUID id, Item item) {
        BigEntry entry = bigCells.computeIfAbsent(id,
                key -> new BigEntry(BuiltInRegistries.ITEM.getKey(item), new ConcurrentHashMap<>()));
        setDirty();
        return entry;
    }

    public void removeBig(UUID id) {
        if (bigCells.remove(id) != null) {
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

        ListTag bigList = new ListTag();
        bigCells.forEach((id, entry) -> {
            CompoundTag cell = new CompoundTag();
            cell.putUUID(ID, id);
            cell.putString(ITEM, entry.item().toString());
            ListTag contents = new ListTag();
            entry.amounts().forEach((key, amount) -> {
                CompoundTag content = new CompoundTag();
                //? if >=1.21 {
                var encoded = AEKey.CODEC.encodeStart(ops, key)
                        .resultOrPartial(error -> LOGGER.error("InsaneAE: failed to save {} in cell {}: {}",
                                key, id, error));
                if (encoded.isEmpty()) {
                    return;
                }
                content.put(KEY, encoded.get());
                //?} else {
                /*content.put(KEY, key.toTagGeneric());
                *///?}
                content.putString(AMOUNT, amount.toString());
                contents.add(content);
            });
            cell.put(CONTENTS, contents);
            bigList.add(cell);
        });
        tag.put(BIG_CELLS, bigList);
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
            // 消えたModのアイテムが混じっていても、AE2のセルと同じく「欠けた中身」として残す
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

        ListTag bigList = tag.getList(BIG_CELLS, Tag.TAG_COMPOUND);
        for (int index = 0; index < bigList.size(); index++) {
            CompoundTag cell = bigList.getCompound(index);
            if (!cell.hasUUID(ID)) {
                continue;
            }
            UUID id = cell.getUUID(ID);
            ResourceLocation item = ResourceLocation.tryParse(cell.getString(ITEM));
            Map<AEKey, BigInteger> amounts = new ConcurrentHashMap<>();
            ListTag contents = cell.getList(CONTENTS, Tag.TAG_COMPOUND);
            // 読めない項目、たとえば外したModのキーは、その項目だけを捨てる。1つのためにセル全体を失わないように
            for (int entryIndex = 0; entryIndex < contents.size(); entryIndex++) {
                CompoundTag content = contents.getCompound(entryIndex);
                if (!content.contains(KEY)) {
                    continue;
                }
                //? if >=1.21 {
                AEKey key = AEKey.CODEC.parse(ops, content.get(KEY))
                        .resultOrPartial(error -> LOGGER.warn("InsaneAE: dropped an unreadable key in cell {}: {}",
                                id, error))
                        .orElse(null);
                //?} else {
                /*AEKey key = AEKey.fromTagGeneric(content.getCompound(KEY));
                *///?}
                BigInteger amount = parseAmount(content.getString(AMOUNT));
                if (key != null && amount != null) {
                    amounts.put(key, amount);
                }
            }
            store.bigCells.put(id, new BigEntry(item != null ? item : BuiltInRegistries.ITEM.getDefaultKey(), amounts));
        }
        return store;
    }

    @Nullable
    private static BigInteger parseAmount(String text) {
        try {
            BigInteger amount = new BigInteger(text);
            return amount.signum() > 0 ? amount : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 描画スレッドの判定。クライアント専用クラスに触るので、distの確認の後でだけ呼ぶ。 */
    private static final class ClientThread {
        static boolean isRenderThread() {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            return minecraft != null && minecraft.isSameThread();
        }
    }
}
