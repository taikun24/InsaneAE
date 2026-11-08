package jp.main.taikun.insaneae.cell;

import net.minecraft.world.item.Item;

/**
 * 超次元MEセル。アイテム・液体・化学物質を、種類も量も制限なしに格納する。
 *
 * <p>中身は {@link InsaneMeCellInventory} が BigInteger で持つ。AE2 へは
 * 1 種あたり {@code Long.MAX_VALUE} で報告する (KeyCounter が long のため)。
 * 中身は {@link InsaneMeCellData} がアイテムの側に保存する。</p>
 *
 * <p>AE2 の {@code IBasicCellItem} は継承しない。AE2 のハンドラに拾われると、
 * 容量や種類数の上限を AE2 の計算で押し返されるため、{@link InsaneMeCellHandler} で拾う。</p>
 */
public class InsaneMeCellItem extends Item {

    public InsaneMeCellItem(Properties props) {
        super(props.stacksTo(1));
    }
}
