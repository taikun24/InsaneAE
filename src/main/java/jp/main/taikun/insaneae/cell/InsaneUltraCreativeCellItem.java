package jp.main.taikun.insaneae.cell;

/**
 * 超強化クリエイティブセル。中身は強化クリエイティブセルと同じだが、
 * 報告する在庫量がlongではなくBigIntegerになる。
 *
 * <p>AE2の在庫報告 ({@code KeyCounter}) はlongなので、AE2から見える量は
 * 強化クリエイティブセルと同じ922京のままになる。BigIntegerの量を読めるのは
 * ACOの正確な計算経路だけで、そこが読む窓口は {@link InsaneUltraCreativeCellInventory}
 * に生やしてある。ACOが居るときだけMixinで付く。</p>
 *
 * <p>つまりこのセルの存在意義は「922京を超える材料を要求するBigIntegerクラフトを
 * 作成可能にする」の1点。ACOが無ければ強化クリエイティブセルと差は無い。</p>
 */
public class InsaneUltraCreativeCellItem extends InsaneCreativeCellItem {

    public InsaneUltraCreativeCellItem(Properties props) {
        super(props);
    }
}
