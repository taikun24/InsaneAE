package jp.main.taikun.insaneae.cell;

import net.minecraft.world.item.Item;

/**
 * 超次元MEセル。アイテム・液体・化学物質を、種類も量も制限なしに格納する。
 *
 * <p>AE2の {@code IBasicCellItem} は継承しない。AE2のハンドラに拾われると、
 * 容量や種類数の上限をAE2の計算で押し返されるため、{@link InsaneMeCellHandler} で拾う。</p>
 */
public class InsaneMeCellItem extends Item {

    public InsaneMeCellItem(Properties props) {
        super(props.stacksTo(1));
    }
}
