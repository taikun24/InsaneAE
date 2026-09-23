package jp.main.taikun.insaneae.upgrade;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.upgrades.IUpgradeableObject;
import jp.main.taikun.insaneae.config.InsaneAEConfig;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「1 tick に 1 回ぶんしか進まない」機械を、加速カードの倍率ぶん<b>同じ tick の中で何回も回す</b>。
 *
 * <p>加速カードは機械の速度値に倍率を掛けるだけなので ({@link SpeedBoost})、
 * <b>速度値では飛ばせない待ち時間</b>を持つ機械には効かない。代表が刻印機で、
 * 加工そのものは 1 tick で終わっても、そのあと {@code smash} (プレス動作) の
 * 16 tick が必ず挟まる。つまり素の AE2 では<b>どれだけ加速しても 16 tick に 1 個</b>が上限になる。</p>
 *
 * <p>この待ち時間は機械の内部状態 (刻印機なら {@code finalStep}) を tick ごとに 1 つ進める形で
 * 書かれているため、<b>tick を呼ぶ回数そのものを増やす</b>のが一番素直で、かつ機械の種類に
 * 依存しない。AE2 の tick 配送は {@code TickManagerService#unsafeTickingRequest} 1 箇所に
 * 集まっているので、そこの戻り値に割り込んで追い tick を入れている
 * ({@code TickManagerServiceMixin})。</p>
 *
 * <p><b>どの機械にも掛けるわけではない。</b>輸出入バスや IO ポートのように
 * 「1 tick の処理量」そのものが速度値で決まる機械は、追い tick を入れると倍率が二乗になる。
 * よって {@link #isCadenceLimited} の名簿に載っている「待ち時間で頭打ちになる機械」だけを回す。</p>
 */
public final class TickBoost {

    private TickBoost() {
    }

    /**
     * 待ち時間で頭打ちになる機械の名簿 (完全修飾名)。親クラスも辿るので継承先にも効く。
     *
     * <p>他 Mod の刻印機は名前空間が違うだけで構造は AE2 と同じなので、
     * {@link #NAME_HINTS} の単純名一致でも拾う。</p>
     */
    private static final Set<String> CADENCE_LIMITED = Set.of(
            // AE2 刻印機: 加工後に smash 16 tick。
            "appeng.blockentity.misc.InscriberBlockEntity",
            // Advanced AE の Reaction Chamber: 1 tick に 1 個までしか取り出さない。
            "net.pedroksl.advanced_ae.common.entities.ReactionChamberEntity");

    /** 名簿に無くても、クラスの単純名がこれを含むなら同じ構造とみなす。 */
    private static final String[] NAME_HINTS = {"Inscriber", "ReactionChamber"};

    /** クラスごとの判定結果。毎 tick・毎機械で呼ばれるので 1 回だけ調べて覚える。 */
    private static final Map<Class<?>, Boolean> CACHE = new ConcurrentHashMap<>();

    /**
     * AE2 が 1 回 tick したあとに呼ばれ、必要なら<b>同じ tick の中で追加の tick を回す</b>。
     *
     * @param first AE2 本来の tick の戻り値
     * @return 最後に回した tick の戻り値 (追い tick をしなかったなら {@code first} そのまま)
     */
    public static TickRateModulation burst(IGridTickable tickable, IGridNode node, TickRateModulation first) {
        if (tickable == null || node == null || !hasMoreWork(first)) {
            return first;
        }
        if (!(tickable instanceof IUpgradeableObject upgradeable) || !isCadenceLimited(tickable.getClass())) {
            return first;
        }
        int multiplier = SpeedBoost.multiplier(upgradeable.getUpgrades());
        if (multiplier <= 1) {
            return first;
        }
        // 回数の上限は設定で決める。刻印機は 1 個作るのに 16 tick 要るので、
        // 既定の 256 でも 1 tick に 16 個 (＝素の 16 tick に 1 個の 256 倍) まで出る。
        int extra = Math.min(multiplier, InsaneAEConfig.machineTickBurst()) - 1;
        TickRateModulation last = first;
        for (int i = 0; i < extra; i++) {
            if (!node.isActive()) {
                break;
            }
            // 追い tick は「1 tick 経った」ぶんとして回す。元の ticksSinceLastCall を
            // そのまま渡すと、tick 間隔を見て進む機械で倍率が二重に掛かってしまう。
            TickRateModulation result = tickable.tickingRequest(node, 1);
            if (result != null) {
                last = result;
            }
            if (!hasMoreWork(result)) {
                break;  // 材料切れ・出力満杯。これ以上回しても無駄。
            }
        }
        return last;
    }

    /** まだ仕事が残っている (＝もう 1 回回す価値がある) か。 */
    private static boolean hasMoreWork(TickRateModulation modulation) {
        return modulation == TickRateModulation.URGENT || modulation == TickRateModulation.FASTER;
    }

    private static boolean isCadenceLimited(Class<?> type) {
        return CACHE.computeIfAbsent(type, TickBoost::lookup);
    }

    private static boolean lookup(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (CADENCE_LIMITED.contains(c.getName())) {
                return true;
            }
            String simple = c.getSimpleName();
            for (String hint : NAME_HINTS) {
                if (simple.contains(hint)) {
                    return true;
                }
            }
        }
        return false;
    }
}
