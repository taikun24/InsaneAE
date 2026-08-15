package jp.main.taikun.insaneae.cell;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.items.contents.CellConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * 強化クリエイティブセルの中身。AE2 の {@code CreativeCellInventory} と同じ作りで、
 * <b>報告する在庫量だけ</b> {@code Integer.MAX_VALUE} (約 21 億) から
 * {@code Long.MAX_VALUE} (約 922 京) に上げてある。
 *
 * <p>出し入れは元から無制限 (設定済みの種類なら要求量をそのまま返す) なので、
 * 変わるのは端末の表示とクラフト計算が見る「在庫量」。
 * 21 億を超える数量を 1 回のクラフトで要求する場合はここが効いてくる。</p>
 */
public class InsaneCreativeCellInventory implements StorageCell {

    /** セルワークベンチで設定された中身。 */
    protected final Set<AEKey> configured;
    private final ItemStack stack;

    public InsaneCreativeCellInventory(ItemStack stack) {
        this.stack = stack;
        // AE2 と同じく、設定の内容はここでコピーしておく (設定インベントリは作り直されるため)。
        this.configured = new HashSet<>(CellConfig.create(stack).keySet());
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        // 設定済みの種類は無限に飲み込む (= 実質ボイド)。それ以外は受け付けない。
        return configured.contains(what) ? amount : 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return configured.contains(what) ? amount : 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (AEKey key : configured) {
            // ここが AE2 との唯一の違い (向こうは Integer.MAX_VALUE)。
            out.add(key, Long.MAX_VALUE);
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return configured.contains(what);
    }

    @Override
    public CellState getStatus() {
        return CellState.TYPES_FULL;
    }

    @Override
    public double getIdleDrain() {
        return 0;
    }

    @Override
    public boolean canFitInsideCell() {
        // 未設定なら他のセルに収納できる (AE2 のクリエイティブセルと同じ扱い)。
        return configured.isEmpty();
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }

    @Override
    public void persist() {
        // 保存するものは無い (中身は設定そのもの)。
    }
}
