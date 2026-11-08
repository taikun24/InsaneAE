package jp.main.taikun.insaneae.crafting;

import appeng.core.localization.Tooltips;

/**
 * AE2の {@link Tooltips#getByteAmount(long)} が扱えない巨大バイト数の整形。
 *
 * <p>AE2 15.4.10の {@code Tooltips} は単位表 {@code units = {k, M, G, T, P, E}} を持つ一方、
 * 除数表 {@code BYTE_NUMS = {1024, 1MiB, 1GiB, 1GiB}} が1GiB止まりになっている。末尾が重複している。
 * {@code getByteAmount} は「1000で割れる間indexを進める」ループなので、
 * 1000 GiB以上でindexが {@code BYTE_NUMS.length} に到達し
 * {@code ArrayIndexOutOfBoundsException} で落ちる。</p>
 *
 * <p>InsaneAEの1T (2^40 = 1024 GiB) 以上の階層、および複数ユニットの合算で
 * 1000 GiBを超えたCPUがこの例外に当たるため、{@code TooltipsMixin} 経由でこちらに肩代わりさせる。
 * 1000 GiB未満はAE2本来の処理をそのまま通すので、既存の表示は一切変わらない。</p>
 */
public final class HugeByteAmounts {

    private static final long GIB = 1024L * 1024L * 1024L;

    /** AE2が正しく扱える上限。これ以上でAE2のgetByteAmountが配列外になる。 */
    public static final long AE2_LIMIT = 1000L * GIB;

    /**
     * AE2の {@code Tooltips.units} と同じ並び。AE2 19.2.18で向こうがprivateになり
     * 直接読むと {@code IllegalAccessError} で落ちるため、こちらで持つ (issue #17)。
     */
    private static final String[] UNITS = {"k", "M", "G", "T", "P", "E"};

    /** {@link #UNITS} における "G" の位置。 */
    private static final int GIB_UNIT_INDEX = 2;

    private HugeByteAmounts() {
    }

    /**
     * 1000 GiB以上のバイト数をT/P/Eまで含めて整形する。
     * 数値部分の書式はAE2の {@link Tooltips#getAmount(double, long)} をそのまま使うため、
     * AE2の他の表示と見た目が揃う。
     */
    public static Tooltips.Amount format(long bytes) {
        long divisor = GIB;
        int unit = GIB_UNIT_INDEX;
        // unitsの最後 ("E") まで進める。Long.MAX_VALUE (8 EiB) でも収まる
        while (unit < UNITS.length - 1 && bytes / divisor >= 1000) {
            divisor *= 1024L;
            unit++;
        }
        return new Tooltips.Amount(Tooltips.getAmount(bytes, divisor), UNITS[unit]);
    }
}
