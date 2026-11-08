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
 * 強化クリエイティブセルの中身。AE2の {@code CreativeCellInventory} と同じ作りで、
 * 報告する在庫量だけ、{@code Integer.MAX_VALUE}の約21億から
 * {@code Long.MAX_VALUE}の約922京に上げてある。
 *
 * <p>出し入れは元から無制限。設定済みの種類なら要求量をそのまま返すので、
 * 変わるのは端末の表示とクラフト計算が見る「在庫量」。
 * 21億を超える数量を1回のクラフトで要求する場合に、この差が出る。</p>
 */
public class InsaneCreativeCellInventory implements StorageCell {

    /** セルワークベンチで設定された中身。 */
    protected final Set<AEKey> configured;
    private final ItemStack stack;

    public InsaneCreativeCellInventory(ItemStack stack) {
        this.stack = stack;
        // AE2と同じく、設定の内容はここでコピーしておく。設定インベントリは作り直されるため
        this.configured = new HashSet<>(CellConfig.create(stack).keySet());
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        // 設定済みの種類は無限に飲み込む。実質ボイドになる。それ以外は受け付けない
        return configured.contains(what) ? amount : 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return configured.contains(what) ? amount : 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (AEKey key : configured) {
            // ここがAE2との唯一の違い。AE2のほうはInteger.MAX_VALUEを返す
            //
            // ただし天井を越えて足さない。KeyCounterはキーごとにlongなので、
            // 同じアイテムを大量に申告する別のセル、ExtendedAE PlusのInfinityセルなどと
            // 他のクリエイティブセルと同居すると、素朴な加算が負数へ折り返す
            // 負の在庫はACOが「正確値を復元できない」として計画ごと降りるため
            // (WidePlanUnavailableException: BigInteger inventory sidecar is incomplete)、
            // セルを入れただけであらゆるクラフトが失敗する
            // 自分の申告で天井を越えないぶんなら、単独ではLong.MAX_VALUEになる
            long headroom = Long.MAX_VALUE - Math.max(out.get(key), 0L);
            if (headroom > 0L) {
                out.add(key, headroom);
            }
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
        // 未設定なら他のセルに収納できる。AE2のクリエイティブセルと同じ扱い
        return configured.isEmpty();
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }

    @Override
    public void persist() {
        // 保存するものは無い。中身は設定そのもの
    }
}
