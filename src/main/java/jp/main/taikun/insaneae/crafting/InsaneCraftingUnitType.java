package jp.main.taikun.insaneae.crafting;

import appeng.block.crafting.CraftingUnitType;
import appeng.block.crafting.ICraftingUnitType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * CrazyAE 相当の「限界突破」クラフトストレージ階層 (1G 〜 8E)。
 *
 * <p>1M〜256M は MEGA Cells が同じバイト数 ({@code 1024*1024*N}) で提供しており、
 * 同じく {@code CraftingUnitBlock} + {@link ICraftingUnitType} で実装されているため
 * InsaneAE の階層と同一の CPU クラスタに合流する。よって重複を避け、InsaneAE は
 * その上 (1G〜) だけを担当する。</p>
 *
 * <p>容量は 1 段ごとに 4 倍 (2 ビットシフト)。最上段の {@link #STORAGE_8E} だけは
 * {@code long} の上限 ({@code 2^63-1} = 8 EiB より 1 バイト少ない) で頭打ちになる。</p>
 *
 * <p><b>オーバーフロー注意:</b> {@code CraftingCPUCluster.storage} は {@code long} で、
 * 各ブロックの storageBytes を単純加算 (乗算なし) するだけ。つまり 1 CPU の合計が
 * {@code Long.MAX_VALUE} を超えると負値になり CPU が壊れる。
 * {@link #STORAGE_8E} は 1 CPU に 1 個、{@link #STORAGE_4E} は 1 CPU に 2 個までが上限。</p>
 *
 * <p>見た目は AE2 の 256k テクスチャ／モデルを全階層で流用中 ({@link #PLACEHOLDER_LOOK})。
 * 専用アートを用意するまでの暫定だが、機能面は完全に動作する。</p>
 */
public enum InsaneCraftingUnitType implements ICraftingUnitType {
    STORAGE_1G("1g", 1L << 30),
    STORAGE_4G("4g", 1L << 32),
    STORAGE_16G("16g", 1L << 34),
    STORAGE_64G("64g", 1L << 36),
    STORAGE_256G("256g", 1L << 38),
    STORAGE_1T("1t", 1L << 40),
    STORAGE_4T("4t", 1L << 42),
    STORAGE_16T("16t", 1L << 44),
    STORAGE_64T("64t", 1L << 46),
    STORAGE_256T("256t", 1L << 48),
    STORAGE_1P("1p", 1L << 50),
    STORAGE_4P("4p", 1L << 52),
    STORAGE_16P("16p", 1L << 54),
    STORAGE_64P("64p", 1L << 56),
    STORAGE_256P("256p", 1L << 58),
    STORAGE_1E("1e", 1L << 60),
    STORAGE_4E("4e", 1L << 62),
    /** long の上限 (2^63-1 バイト ≒ 8 EiB)。1 CPU に 1 個のみ運用可 (複数だと加算オーバーフロー)。 */
    STORAGE_8E("8e", Long.MAX_VALUE);

    /** 全階層で流用している AE2 の見た目 (専用アート未用意のため)。 */
    public static final CraftingUnitType PLACEHOLDER_LOOK = CraftingUnitType.STORAGE_256K;

    private final String id;
    private final long storageBytes;

    /** 登録後に {@code ModBlocks} が設定する、この階層に対応する BlockItem のサプライヤ。 */
    private Supplier<Item> item = () -> Items.AIR;

    InsaneCraftingUnitType(String id, long storageBytes) {
        this.id = id;
        this.storageBytes = storageBytes;
    }

    /** 階層 ID。例: "1g"。 */
    public String id() {
        return id;
    }

    /** 表示名に差し込む階層ラベル。例: "1G"。lang は書式キー 1 つで済ませている。 */
    public String label() {
        return id.toUpperCase(java.util.Locale.ROOT);
    }

    /** ブロック／アイテムの登録名。例: "1g_crafting_storage"。 */
    public String blockId() {
        return id + "_crafting_storage";
    }

    /** この階層のセルコンポーネントの登録名。例: "cell_component_1g"。 */
    public String cellComponentId() {
        return "cell_component_" + id;
    }

    /** formed モデルの ID。ae2 名前空間に置く必要がある → {@link FormedModels}。 */
    public net.minecraft.resources.ResourceLocation formedModel() {
        return FormedModels.of(id + "_storage");
    }

    public CraftingUnitType ae2FormedType() {
        return PLACEHOLDER_LOOK;
    }

    public void setItem(Supplier<Item> item) {
        this.item = item;
    }

    @Override
    public long getStorageBytes() {
        return storageBytes;
    }

    @Override
    public int getAcceleratorThreads() {
        // ストレージユニットは加速スレッドを提供しない。
        return 0;
    }

    @Override
    public Item getItemFromType() {
        return item.get();
    }
}
