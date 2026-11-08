package jp.main.taikun.insaneae.upgrade;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jp.main.taikun.insaneae.util.SaturatingMath;

/**
 * 取り付けられている加速カードから機械の速度倍率を求めるヘルパー。Mixinから呼ばれる。
 */
public final class SpeedBoost {

    /**
     * 速度倍率を注入するMixinの優先度。既定 (1000) より高くすること。
     *
     * <p>IOポートや刻印機の速度をいじるModは他にもあり、たとえばIO Port Super Speedなど、
     * 相手が対象メソッドを {@code @Overwrite} 等で差し替えると、そのメソッドには
     * {@code @MixinMerged} が付く。Mixinはマージ済みメソッドへの注入を
     * 「自分のpriorityが相手より高い」ときしか許さない
     * {@code InjectionInfo#checkTarget}が{@code mergedPriority >= ourPriority}で
     * {@code InvalidInjectionException} を投げる)。しかもこれは注入点を数える前の
     * 準備段階の例外なので、{@code require = 0} では抑止できない。</p>
     *
     * <p>よって相手より高くしておく。既定は1000で、そのうえで各注入を{@code require = 0}に
     * してある。こうすると「相手が定数ごと消していた場合は黙って諦める。加速カードが効かない
     * だけ)」に収まり、起動時クラッシュにはならない。</p>
     */
    public static final int MIXIN_PRIORITY = 1500;

    private SpeedBoost() {
    }

    /**
     * この機械に掛ける速度倍率。カードが無ければ1で、素のAE2の挙動になる。
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
     * <p>ExtendedAEの {@code InscriberThread} のように、速度計算が機械本体ではなく
     * 内部スレッドのクラスにあり、しかも {@code host} フィールドの型が相手Modのクラス
     * コンパイル時に参照できない場合に使う。フィールドはクラスごとに1回だけ
     * リフレクションで探して覚えておくので、毎tick呼んでも軽い。</p>
     *
     * <p>hostが見つからない、または{@link IUpgradeableObject}でない場合は1。効かないだけ。</p>
     */
    public static int multiplierFromHost(Object owner) {
        Field field = HOST_FIELDS.computeIfAbsent(owner.getClass(), SpeedBoost::findHostField);
        if (field == MISSING) {
            return 1;
        }
        try {
            return field.get(owner) instanceof IUpgradeableObject upgradeable ?
                    multiplier(upgradeable.getUpgrades())
                    : 1;
        } catch (IllegalAccessException e) {
            return 1;
        }
    }

    /** {@code host}フィールドが無いクラスの印。ConcurrentHashMapはnullを持てないため。 */
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
                break;  // InaccessibleObjectExceptionなど。効かないだけに留める
            }
        }
        return MISSING;
    }

    /** intの範囲で頭打ちにする掛け算。オーバーフローで速度が負になるのを防ぐ。 */
    public static int saturatingMultiply(int value, int multiplier) {
        long result = (long) value * multiplier;
        return (int) Math.min(result, Integer.MAX_VALUE);
    }

    public static long saturatingMultiply(long value, int multiplier) {
        return SaturatingMath.multiply(value, multiplier);
    }
}
