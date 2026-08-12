package jp.main.taikun.insaneae.quantum;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

/**
 * クラフト CPU の {@code executeCrafting} にまとめ処理を差し込むときの<b>帳簿</b>。
 *
 * <p>差し込み方は AE2 でも複製された CPU でも同じ 3 点セットになる。</p>
 * <ol>
 *   <li>先頭でまとめ処理を試す ({@link #begin})</li>
 *   <li>使ったぶんを CPU の 1 tick 予算から引く ({@link #reduce})</li>
 *   <li>まとめたぶんを戻り値に足し戻す ({@link #takePushed})</li>
 * </ol>
 *
 * <h2>なぜ状態を持つのか、なぜ「消費して 0 に戻す」形なのか</h2>
 * <p>2 と 3 は Mixin の別々の注入なので、<b>1 と 2 のどちらが先に走るかは保証されない</b>。
 * さらに、同じ {@code executeCrafting} の先頭に<b>他の Mod も打ち切り付きで注入している</b>
 * (AE2 Crafting Optimizer がまさにそう)。先に打ち切ったほうが勝つので、
 * <b>こちらの 1 だけが走って 3 が走らない</b>という順番が普通に起こりうる。</p>
 *
 * <p>そこで、溜めた値は<b>読んだ側が必ず 0 に戻す</b>ようにしてある。こうすると
 * 取りこぼしても次の呼び出しで清算されるだけで、ずれが溜まり続けることがない。
 * 最悪でも「1 回ぶん予算がずれる」で収まり、アイテムが増減することは無い。</p>
 */
public final class BulkCraftingHook {

    /** 次の呼び出しで CPU の予算から引くぶん。 */
    private int debt;

    /** 今の呼び出しで戻り値に足し戻すぶん。 */
    private int pushed;

    /**
     * まとめ処理を試す。CPU の {@code executeCrafting} の先頭で呼ぶ。
     *
     * @return 0 以上ならその値で {@code executeCrafting} を<b>打ち切ってよい</b>
     *         (予算を使い切った)。-1 なら CPU 本来の処理を続けること。
     */
    public int begin(CraftingJobView view, int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level) {
        int done = QuantumBulkCrafting.execute(view, maxPatterns, craftingService, energyService, level);
        // ACOのBigInteger正本をInsaneAEが所有するJobは、done=0でもAE2本来のlong taskへ
        // 戻してはいけない。飽和したTaskProgressを実行すると、材料不足・二重計上・再注文の
        // いずれかになるため、このtickは待機して次の窓で再試行する。
        if (view != null && view.hasExactCraftingPlan()) {
            return done;
        }
        if (done <= 0) {
            return -1;
        }
        if (done >= maxPatterns) {
            // 予算を使い切ったので、この tick に CPU がやることはもう無い。
            return done;
        }
        // <b>ここで打ち切ってはいけない。</b>executeCrafting は「その tick にジョブ内の全パターンを
        // 各プロバイダへ押し出す」唯一のループなので、打ち切ると同じジョブに含まれる他のパターン
        // (機械に投げる加工パターンなど) がその tick はまったく処理されない。
        // Quantum CPU の予算は毎 tick 補充されるため、仕事がある限り他のプロバイダが永久に飢える。
        debt += done;
        pushed = done;
        return -1;
    }

    /** 溜まっている引き算を消費して、CPU に渡す 1 tick 予算を返す。 */
    public int reduce(int maxPatterns) {
        int used = debt;
        debt = 0;
        return Math.max(0, maxPatterns - used);
    }

    /** 戻り値に足し戻すぶんを返して 0 に戻す。 */
    public int takePushed() {
        int value = pushed;
        pushed = 0;
        return value;
    }
}
