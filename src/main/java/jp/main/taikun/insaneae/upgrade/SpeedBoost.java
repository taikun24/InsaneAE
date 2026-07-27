package jp.main.taikun.insaneae.upgrade;

import appeng.api.upgrades.IUpgradeInventory;

/**
 * 取り付けられている加速カードから機械の速度倍率を求めるヘルパー。Mixin から呼ばれる。
 */
public final class SpeedBoost {

    /**
     * 速度倍率を注入する Mixin の優先度。<b>既定 (1000) より高くすること</b>。
     *
     * <p>IO ポートや刻印機の速度をいじる Mod は他にもあり (例: IO Port Super Speed)、
     * 相手が対象メソッドを {@code @Overwrite} 等で差し替えると、そのメソッドには
     * {@code @MixinMerged} が付く。Mixin は<b>マージ済みメソッドへの注入を
     * 「自分の priority が相手より高い」ときしか許さない</b>
     * ({@code InjectionInfo#checkTarget} が {@code mergedPriority >= ourPriority} で
     * {@code InvalidInjectionException} を投げる)。しかもこれは注入点を数える前の
     * 準備段階の例外なので、<b>{@code require = 0} では抑止できない</b>。</p>
     *
     * <p>よって相手 (既定の 1000) より高くしておき、そのうえで各注入を {@code require = 0} に
     * してある。こうすると「相手が定数ごと消していた場合は黙って諦める (加速カードが効かない
     * だけ)」に収まり、起動時クラッシュにはならない。</p>
     */
    public static final int MIXIN_PRIORITY = 1500;

    private SpeedBoost() {
    }

    /**
     * この機械に掛ける速度倍率。カードが無ければ 1 (＝素の AE2 の挙動)。
     * 複数種を挿した場合は掛け算になる。
     */
    public static int multiplier(IUpgradeInventory upgrades) {
        if (upgrades == null) {
            return 1;
        }
        int total = 1;
        for (InsaneSpeedCardType type : InsaneSpeedCardType.values()) {
            int installed = upgrades.getInstalledUpgrades(type.item());
            for (int i = 0; i < installed; i++) {
                total = saturatingMultiply(total, type.multiplier());
            }
        }
        return total;
    }

    /** int の範囲で頭打ちにする掛け算 (オーバーフローで速度が負になるのを防ぐ)。 */
    public static int saturatingMultiply(int value, int multiplier) {
        long result = (long) value * multiplier;
        return (int) Math.min(result, Integer.MAX_VALUE);
    }

    /** long 版。 */
    public static long saturatingMultiply(long value, int multiplier) {
        if (multiplier != 0 && value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }
}
