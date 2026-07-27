package jp.main.taikun.insaneae.crafting;

import appeng.core.localization.Tooltips;

/**
 * AE2 の {@link Tooltips#getByteAmount(long)} が扱えない巨大バイト数の整形。
 *
 * <p>AE2 15.4.10 の {@code Tooltips} は単位表 {@code units = {k, M, G, T, P, E}} を持つ一方、
 * 除数表 {@code BYTE_NUMS = {1024, 1MiB, 1GiB, 1GiB}} が 1GiB 止まり (末尾が重複) になっている。
 * {@code getByteAmount} は「1000 で割れる間 index を進める」ループなので、
 * <b>1000 GiB 以上</b>で index が {@code BYTE_NUMS.length} に到達し
 * {@code ArrayIndexOutOfBoundsException} で落ちる。</p>
 *
 * <p>InsaneAE の 1T (2^40 = 1024 GiB) 以上の階層、および複数ユニットの合算で
 * 1000 GiB を超えた CPU がこれを踏むため、{@code TooltipsMixin} 経由でこちらに肩代わりさせる。
 * 1000 GiB 未満は AE2 本来の処理をそのまま通すので、既存の表示は一切変わらない。</p>
 */
public final class HugeByteAmounts {

    private static final long GIB = 1024L * 1024L * 1024L;

    /** AE2 が正しく扱える上限。これ以上で AE2 の getByteAmount が配列外になる。 */
    public static final long AE2_LIMIT = 1000L * GIB;

    /** {@code Tooltips.units} における "G" の位置。 */
    private static final int GIB_UNIT_INDEX = 2;

    private HugeByteAmounts() {
    }

    /**
     * 1000 GiB 以上のバイト数を T/P/E まで含めて整形する。
     * 数値部分の書式は AE2 の {@link Tooltips#getAmount(double, long)} をそのまま使うため、
     * AE2 の他の表示と見た目が揃う。
     */
    public static Tooltips.Amount format(long bytes) {
        long divisor = GIB;
        int unit = GIB_UNIT_INDEX;
        // units の最後 ("E") まで進める。Long.MAX_VALUE (8 EiB) でも収まる。
        while (unit < Tooltips.units.length - 1 && bytes / divisor >= 1000) {
            divisor *= 1024L;
            unit++;
        }
        return new Tooltips.Amount(Tooltips.getAmount(bytes, divisor), Tooltips.units[unit]);
    }
}
