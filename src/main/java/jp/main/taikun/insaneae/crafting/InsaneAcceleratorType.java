package jp.main.taikun.insaneae.crafting;

import appeng.block.crafting.CraftingUnitType;
import appeng.block.crafting.ICraftingUnitType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * 「限界突破」クラフト協調処理ユニット (アクセラレータ) の階層。16× 〜 2G×。
 *
 * <p>AE2 本体が 1 スレッド、MEGA Cells が 4 スレッドなので、その上の 16 スレッドから
 * 1 段 4 倍で伸ばす。1G は 2^30 スレッド。</p>
 *
 * <p>最上段の {@link #ACCELERATOR_2G} だけは 1 段 2 倍。
 * {@link ICraftingUnitType#getAcceleratorThreads()} が {@code int} なので 2^31 は表現できず、
 * {@code Integer.MAX_VALUE} (= 2G - 1) で頭打ちになる。この 1 個で
 * <b>1 CPU が持てるスレッド数の上限に単体で到達する</b>ため、これ以上の階層は作れない。</p>
 *
 * <p><b>AE2 側の制限を 2 か所外している</b> ({@code CraftingCPUClusterMixin}):</p>
 * <ul>
 *   <li>AE2 は 1 ブロックあたり 16 スレッドを超えると
 *       {@code IllegalArgumentException("Co-processor threads may not exceed 16 per single unit block.")}
 *       を投げるので、その上限を撤廃している。</li>
 *   <li>{@code CraftingCPUCluster.accelerator} は {@code int} の単純加算で、消費側の
 *       {@code CraftingCpuLogic#tickCraftingLogic} も
 *       {@code getCoProcessors() + 1 - (usedOps[0]+usedOps[1]+usedOps[2])} を int で計算するため、
 *       {@code Integer.MAX_VALUE} 付近で二重に桁あふれして<b>クラフトが 1 回も進まなくなる</b>。
 *       {@code CraftingCPUClusterMixin} が合計を long でも数え、
 *       {@code CraftingCpuBudgetMixin} が 1 tick の予算計算を long でやり直している。</li>
 * </ul>
 *
 * <p>最終的な天井は {@code CraftingCpuLogic#executeCrafting} の引数・戻り値が {@code int} である点で、
 * <b>1 tick あたり {@code Integer.MAX_VALUE} (約 21 億) 回のクラフト操作</b>。
 * つまりこの階層 1 個ぶんで丁度打ち止めになる。</p>
 *
 * <p>スレッド数は「1 tick に処理できるクラフト操作数」の上限であり、実際の処理量は
 * 保留中の作業量で頭打ちになる。つまり巨大な値は「実質無制限」を意味し、
 * 大きなジョブを 1 tick で走り切ろうとするため重くなり得る。</p>
 */
public enum InsaneAcceleratorType implements ICraftingUnitType {
    ACCELERATOR_16X("16x", 16),
    ACCELERATOR_64X("64x", 64),
    ACCELERATOR_256X("256x", 256),
    ACCELERATOR_1K("1k", 1 << 10),
    ACCELERATOR_4K("4k", 1 << 12),
    ACCELERATOR_16K("16k", 1 << 14),
    ACCELERATOR_64K("64k", 1 << 16),
    ACCELERATOR_256K("256k", 1 << 18),
    ACCELERATOR_1M("1m", 1 << 20),
    ACCELERATOR_4M("4m", 1 << 22),
    ACCELERATOR_16M("16m", 1 << 24),
    ACCELERATOR_64M("64m", 1 << 26),
    ACCELERATOR_256M("256m", 1 << 28),
    /** 2^30 スレッド。 */
    ACCELERATOR_1G("1g", 1 << 30),
    /**
     * 名目 2^31 スレッドだが、{@link ICraftingUnitType#getAcceleratorThreads()} が {@code int} なので
     * {@code Integer.MAX_VALUE} (2G - 1)。1 個だけで 1 tick 予算の上限に達するため、
     * 2 個目以降を足しても速くはならない。
     */
    ACCELERATOR_2G("2g", Integer.MAX_VALUE, 2);

    /** 全階層で流用している AE2 の見た目 (専用アート未用意)。 */
    public static final CraftingUnitType PLACEHOLDER_LOOK = CraftingUnitType.ACCELERATOR;

    private final String id;
    private final int threads;
    private final int lowerCount;

    private Supplier<Item> item = () -> Items.AIR;

    InsaneAcceleratorType(String id, int threads) {
        this(id, threads, 4);
    }

    InsaneAcceleratorType(String id, int threads, int lowerCount) {
        this.id = id;
        this.threads = threads;
        this.lowerCount = lowerCount;
    }

    /** 階層 ID。例: "16x"。 */
    public String id() {
        return id;
    }

    /**
     * 表示名に差し込む階層ラベル。例: "16x" → "16"、"1m" → "1M"、"1k" → "1k"。
     *
     * <p>lang 側は「%s× クラフト協調処理ユニット」の書式キー 1 つだけ持たせている。</p>
     */
    public String label() {
        char last = id.charAt(id.length() - 1);
        String head = id.substring(0, id.length() - 1);
        if (last == 'x') {
            return head;                    // 16x / 64x / 256x は倍率そのもの
        }
        return last == 'k' ? id : head + Character.toUpperCase(last);
    }

    /** クラフトに必要な下位ユニットの個数。スレッド数の倍率と同じ (最上段だけ 2)。 */
    public int lowerCount() {
        return lowerCount;
    }

    /** ブロック／アイテムの登録名。例: "16x_crafting_accelerator"。 */
    public String blockId() {
        return id + "_crafting_accelerator";
    }

    /** formed モデルの ID。ae2 名前空間に置く必要がある → {@link FormedModels}。 */
    public net.minecraft.resources.ResourceLocation formedModel() {
        return FormedModels.of(id + "_accelerator");
    }

    public CraftingUnitType ae2FormedType() {
        return PLACEHOLDER_LOOK;
    }

    public void setItem(Supplier<Item> item) {
        this.item = item;
    }

    @Override
    public long getStorageBytes() {
        // 加速ユニットはストレージを提供しない。
        return 0;
    }

    @Override
    public int getAcceleratorThreads() {
        return threads;
    }

    @Override
    public Item getItemFromType() {
        return item.get();
    }
}
