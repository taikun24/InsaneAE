package jp.main.taikun.insaneae.cell;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if >=1.21 {
import appeng.api.ids.AEComponents;
//?}

/**
 * 強化クリエイティブセル。セルワークベンチで設定した中身を無限に供給する。
 *
 * <p>AE2のクリエイティブセルとの違いは報告する在庫量だけで、
 * AE2が1種あたり{@code Integer.MAX_VALUE}の約21億を返すのに対し、
 * こちらは{@code Long.MAX_VALUE}の約922京を返す
 * ({@link InsaneCreativeCellInventory})。どちらも取り出しは実際には無制限だが、
 * 端末の表示やクラフト計算が見るのはこの「在庫量」なので、
 * 21億を超える自動クラフトの材料元にするならこちらが要る。</p>
 *
 * <p>AE2の {@code CreativeCellItem} を継承しないのは、AE2の
 * {@code CreativeCellHandler} が {@code instanceof CreativeCellItem} で拾ってしまい、
 * 先に登録されている向こうのハンドラに21億版の在庫を返されてしまうため。
 * 中身は薄いので自前で持ち、{@link InsaneCreativeCellHandler} で拾う。</p>
 */
public class InsaneCreativeCellItem extends Item implements ICellWorkbenchItem {
    //? if <1.21 {
    /*private static final String FUZZY_MODE_TAG = "FuzzyMode";
    *///?}

    public InsaneCreativeCellItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        // 種類の制限なし。AE2のクリエイティブセルはアイテム用と液体用も中身は同じで、
        // 分かれているのは見た目だけなので、こちらは1種類で全部まかなう
        return CellConfig.create(is);
    }

    // ファジー設定はAE2のセルと同じコンポーネント (AEComponents.STORAGE_CELL_FUZZY_MODE) に保存する
    // 値の検証とコーデックはAE2側にあるので、壊れた値を握り潰す処理は要らない
    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        //? if >=1.21 {
        return is.getOrDefault(AEComponents.STORAGE_CELL_FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        //?} else {
        /*var tag = is.getTag();
        if (tag != null && tag.contains(FUZZY_MODE_TAG)) {
            try {
                return FuzzyMode.valueOf(tag.getString(FUZZY_MODE_TAG));
            } catch (IllegalArgumentException ignored) {
                // 壊れたNBTは既定値として扱う
            }
        }
        return FuzzyMode.IGNORE_ALL;
        *///?}
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fuzzyMode) {
        //? if >=1.21 {
        is.set(AEComponents.STORAGE_CELL_FUZZY_MODE, fuzzyMode);
        //?} else {
        /*is.getOrCreateTag().putString(FUZZY_MODE_TAG, fuzzyMode.name());
        *///?}
    }
}
