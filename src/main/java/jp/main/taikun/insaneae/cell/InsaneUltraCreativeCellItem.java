package jp.main.taikun.insaneae.cell;

/**
 * 超強化クリエイティブセル。中身は強化クリエイティブセルと同じだが、
 * <b>報告する在庫量が long ではなく BigInteger</b> になる。
 *
 * <p>AE2 の在庫報告 ({@code KeyCounter}) は long なので、AE2 から見える量は
 * 強化クリエイティブセルと同じ 922 京のままになる。BigInteger の量を読めるのは
 * ACO の正確な計算経路だけで、そこが読む窓口は {@link InsaneUltraCreativeCellInventory}
 * に生やしてある (ACO が居るときだけ Mixin で付く)。</p>
 *
 * <p>つまりこのセルの存在意義は「922京を超える材料を要求する BigInteger クラフトを
 * <b>作成可能にする</b>」の 1 点。ACO が無ければ強化クリエイティブセルと差は無い。</p>
 */
public class InsaneUltraCreativeCellItem extends InsaneCreativeCellItem {

    public InsaneUltraCreativeCellItem(Properties props) {
        super(props);
    }
}
