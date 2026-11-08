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
 * 「1 tickに1回ぶんしか進まない」機械を、加速カードの倍率ぶん同じtickの中で何回も回す。
 *
 * <p>加速カードは機械の速度値に倍率を掛けるだけなので ({@link SpeedBoost})、
 * 速度値では飛ばせない待ち時間を持つ機械には効かない。代表が刻印機で、
 * 加工そのものは1 tickで終わっても、そのあと{@code smash}、つまりプレス動作の
 * 16 tickが必ず挟まる。つまり素のAE2ではどれだけ加速しても16 tickに1個が上限になる。</p>
 *
 * <p>この待ち時間は機械の内部状態、刻印機なら{@code finalStep}をtickごとに1つ進める形で
 * 書かれているため、tickを呼ぶ回数そのものを増やすのが一番素直で、かつ機械の種類に
 * 依存しない。AE2のtick配送は {@code TickManagerService#unsafeTickingRequest} 1箇所に
 * 集まっているので、そこの戻り値に割り込んで追いtickを入れている
 * ({@code TickManagerServiceMixin})。</p>
 *
 * <p>どの機械にも掛けるわけではない。輸出入バスやIOポートのように
 * 「1 tickの処理量」そのものが速度値で決まる機械は、追いtickを入れると倍率が二乗になる。
 * よって {@link #isCadenceLimited} の名簿に載っている「待ち時間で頭打ちになる機械」だけを回す。</p>
 */
public final class TickBoost {

    private TickBoost() {
    }

    /**
     * 待ち時間で頭打ちになる機械の名簿。完全修飾名で書く。親クラスも辿るので継承先にも効く。
     *
     * <p>他Modの刻印機は名前空間が違うだけで構造はAE2と同じなので、
     * {@link #NAME_HINTS} の単純名一致でも拾う。</p>
     */
    private static final Set<String> CADENCE_LIMITED = Set.of(
            // AE2刻印機: 加工後にsmash 16 tick
            "appeng.blockentity.misc.InscriberBlockEntity",
            // Advanced AEのReaction Chamber: 1 tickに1個までしか取り出さない
            "net.pedroksl.advanced_ae.common.entities.ReactionChamberEntity");

    /** 名簿に無くても、クラスの単純名がこれを含むなら同じ構造とみなす。 */
    private static final String[] NAME_HINTS = {"Inscriber", "ReactionChamber"};

    /** クラスごとの判定結果。毎tick・毎機械で呼ばれるので1回だけ調べて覚える。 */
    private static final Map<Class<?>, Boolean> CACHE = new ConcurrentHashMap<>();

    /**
     * AE2が1回tickしたあとに呼ばれ、必要なら同じtickの中で追加のtickを回す。
     *
     * @param first AE2本来のtickの戻り値
     * @return仕事をしたtickのうち最も急ぎの戻り値。追いtickをしなかったなら{@code first}そのまま
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
        // 回数の上限は設定で決める。刻印機は1個作るのに16 tick要るので、
        // 既定の256でも1 tickに16個まで出る。素の16 tickに1個の256倍に当たる
        int extra = Math.min(multiplier, InsaneAEConfig.machineTickBurst()) - 1;
        TickRateModulation best = first;
        for (int i = 0; i < extra; i++) {
            if (!node.isActive()) {
                break;
            }
            // 追いtickは「1 tick経った」ぶんとして回す。元のticksSinceLastCallを
            // そのまま渡すと、tick間隔を見て進む機械で倍率が二重に掛かってしまう
            TickRateModulation result = tickable.tickingRequest(node, 1);
            if (!hasMoreWork(result)) {
                break;  // 材料切れ・出力満杯。これ以上回しても無駄
            }
            best = moreUrgent(best, result);
        }
        // 最後の追いtickが返すSLEEP / SLOWERは「このtickの中で材料が尽きた」だけで、
        // 機械が暇になったわけではない。それをAE2へ返すとtick間隔が延び、材料が少しずつ
        // パターンプロバイダーからの搬入という届く使い方では、最長20 tickに1回まで落ちる。issue #15
        // 次の材料が届くまでの待ち方は、仕事をしたtickの戻り値に任せる
        return best;
    }

    /** AE2の列挙はSLEEP, IDLE, SLOWER, SAME, FASTER, URGENTの順に急ぎになる。 */
    private static TickRateModulation moreUrgent(TickRateModulation a, TickRateModulation b) {
        return b.ordinal() > a.ordinal() ? b : a;
    }

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
