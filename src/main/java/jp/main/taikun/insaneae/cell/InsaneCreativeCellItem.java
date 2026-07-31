package jp.main.taikun.insaneae.cell;

import appeng.api.config.FuzzyMode;
import appeng.api.ids.AEComponents;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 強化クリエイティブセル。セルワークベンチで設定した中身を<b>無限に</b>供給する。
 *
 * <p>AE2 のクリエイティブセルとの違いは<b>報告する在庫量だけ</b>で、
 * AE2 が 1 種あたり {@code Integer.MAX_VALUE} (約 21 億) を返すのに対し、
 * こちらは {@code Long.MAX_VALUE} (約 922 京) を返す
 * ({@link InsaneCreativeCellInventory})。どちらも取り出しは実際には無制限だが、
 * 端末の表示やクラフト計算が見るのはこの「在庫量」なので、
 * 21 億を超える自動クラフトの材料元にするならこちらが要る。</p>
 *
 * <p>AE2 の {@code CreativeCellItem} を継承<b>しない</b>のは、AE2 の
 * {@code CreativeCellHandler} が {@code instanceof CreativeCellItem} で拾ってしまい、
 * 先に登録されている向こうのハンドラに 21 億版の在庫を返されてしまうため。
 * 中身は薄いので自前で持ち、{@link InsaneCreativeCellHandler} で拾う。</p>
 */
public class InsaneCreativeCellItem extends Item implements ICellWorkbenchItem {

    public InsaneCreativeCellItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        // 種類の制限なし。AE2 のクリエイティブセル (アイテム用/液体用) も中身は同じで、
        // 分かれているのは見た目だけなので、こちらは 1 種類で全部まかなう。
        return CellConfig.create(is);
    }

    // 1.20.5 で ItemStack の NBT がデータコンポーネントに置き換わったため、
    // ファジー設定は AE2 のセルと同じコンポーネント (AEComponents.STORAGE_CELL_FUZZY_MODE) に持つ。
    // 値の検証とコーデックは AE2 側が持っているので、壊れた値を握り潰す処理は不要になった。
    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        return is.getOrDefault(AEComponents.STORAGE_CELL_FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fuzzyMode) {
        is.set(AEComponents.STORAGE_CELL_FUZZY_MODE, fuzzyMode);
    }
}
