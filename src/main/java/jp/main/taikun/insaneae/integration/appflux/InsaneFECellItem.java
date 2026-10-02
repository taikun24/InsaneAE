package jp.main.taikun.insaneae.integration.appflux;

import com.glodblock.github.appflux.common.items.ItemMEGAFECell;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * 1G 以上の FE ストレージセル。
 *
 * <p>AppliedFlux の {@link ItemMEGAFECell} (MEGA の FE セル筐体を使う版) をそのまま継承しているので、
 * 中身の出し入れ・ツールチップ・分解 (筐体 + コンポーネントに戻る)・FE の capability は
 * AppliedFlux の実装 ({@code FECellHandler} / {@code FluxCellInventory}) が働く。こちらで変えているのは容量と表示名だけ。容量は {@code int} (KiB) では足りないので
 * コンストラクタには 0 を渡し、{@link #getBytes} を上書きして long で返す。</p>
 */
public class InsaneFECellItem extends ItemMEGAFECell {

    private final long totalBytes;
    private final String nameKey;
    private final String tierLabel;

    public InsaneFECellItem(ItemLike component, long totalBytes, double idleDrain,
            String nameKey, String tierLabel) {
        super(component, 0, idleDrain);
        this.totalBytes = totalBytes;
        this.nameKey = nameKey;
        this.tierLabel = tierLabel;
    }

    @Override
    public long getBytes(ItemStack stack) {
        return FluxCapacity.clampBytes(totalBytes);
    }

    /** 表示名は階層ごとの lang キーではなく「書式キー + 階層ラベル」で作る。 */
    @Override
    public Component getName(ItemStack stack) {
        return TieredNames.of(nameKey, tierLabel);
    }
}
