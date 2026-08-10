package jp.main.taikun.insaneae.upgrade;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * 「{@code host} フィールドに機械本体を持つ作業スレッド」から速度倍率を求める。
     *
     * <p>ExtendedAE の {@code InscriberThread} のように、速度計算が機械本体ではなく
     * 内部スレッドのクラスにあり、しかも {@code host} フィールドの型が相手 Mod のクラス
     * (コンパイル時に参照できない) の場合に使う。フィールドはクラスごとに 1 回だけ
     * リフレクションで探して覚えておくので、毎 tick 呼んでも軽い。</p>
     *
     * <p>host が見つからない・{@link IUpgradeableObject} でない場合は 1 (= 効かないだけ)。</p>
     */
    public static int multiplierFromHost(Object owner) {
        Field field = HOST_FIELDS.computeIfAbsent(owner.getClass(), SpeedBoost::findHostField);
        if (field == MISSING) {
            return 1;
        }
        try {
            return field.get(owner) instanceof IUpgradeableObject upgradeable
                    ? multiplier(upgradeable.getUpgrades())
                    : 1;
        } catch (IllegalAccessException e) {
            return 1;
        }
    }

    /** {@code host} フィールドが無いクラスの印 (ConcurrentHashMap は null を持てない)。 */
    private static final Field MISSING;

    static {
        try {
            MISSING = SpeedBoost.class.getDeclaredField("HOST_FIELDS");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final Map<Class<?>, Field> HOST_FIELDS = new ConcurrentHashMap<>();

    private static Field findHostField(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField("host");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // 親クラスを見る
            } catch (RuntimeException e) {
                break;  // InaccessibleObjectException など。効かないだけに留める。
            }
        }
        return MISSING;
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
