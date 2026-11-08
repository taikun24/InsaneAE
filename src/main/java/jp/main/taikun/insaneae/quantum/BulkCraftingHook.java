package jp.main.taikun.insaneae.quantum;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

/**
 * クラフトCPUの {@code executeCrafting} にまとめ処理を差し込むときの帳簿。
 *
 * <p>差し込み方はAE2でも複製されたCPUでも同じ3点セットになる。</p>
 * <ol>
 *   <li>先頭でまとめ処理を試す ({@link #begin})</li>
 *   <li>使ったぶんをCPUの1 tick予算から引く ({@link #reduce})</li>
 *   <li>まとめたぶんを戻り値に足し戻す ({@link #takePushed})</li>
 * </ol>
 *
 * <h2>なぜ状態を持つのか、なぜ「消費して0に戻す」形なのか</h2>
 * <p>2と3はMixinの別々の注入なので、1と2のどちらが先に走るかは保証されない。
 * さらに、同じ {@code executeCrafting} の先頭に他のModも打ち切り付きで注入している
 * AE2 Crafting Optimizerがまさにそう。先に打ち切ったほうが勝つので、
 * こちらの1だけが走って3が走らないという順番が普通に起こりうる。</p>
 *
 * <p>そこで、溜めた値は読んだ側が必ず0に戻すようにしてある。こうすると
 * 取りこぼしても次の呼び出しで清算されるだけで、ずれが溜まり続けることがない。
 * 最悪でも「1回ぶん予算がずれる」で収まり、アイテムが増減することは無い。</p>
 */
public final class BulkCraftingHook {

    /** 次の呼び出しでCPUの予算から引くぶん。 */
    private int debt;

    /** 今の呼び出しで戻り値に足し戻すぶん。 */
    private int pushed;

    /**
     * まとめ処理を試す。CPUの {@code executeCrafting} の先頭で呼ぶ。
     *
     * @return 0以上ならその値で {@code executeCrafting} を打ち切ってよい
     *         予算を使い切った。-1ならCPU本来の処理を続けること。
     */
    public int begin(CraftingJobView view, int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level) {
        // ACO 1.5.23以降、exactジョブの実行と納品はACOが丸ごと所有する
        // 同じ @HEADでこちらのほうが先に走るので、ACOが打ち切る前に降りること
        // 0を返して打ち切るのはACO自身の挙動と同じで、AE2本来のlongタスクを
        // 走らせてはいけない。ACOが渡した1回実行facadeを実行してしまう
        if (view != null && view.isOwnedByAcoExactExecution()) {
            return 0;
        }
        int done = QuantumBulkCrafting.execute(view, maxPatterns, craftingService, energyService, level);
        // ACOのBigInteger正本をInsaneAEが所有するJobは、done=0でもAE2本来のlong taskへ
        // 戻してはいけない。飽和したTaskProgressを実行すると、材料不足・二重計上・再注文の
        // いずれかになるため、このtickは待機して次の窓で再試行する
        if (view != null && view.hasExactCraftingPlan()) {
            return done;
        }
        if (done <= 0) {
            return -1;
        }
        if (done >= maxPatterns) {
            return done;
        }
        // ここで打ち切ってはいけない。executeCraftingは「そのtickにジョブ内の全パターンを
        // 各プロバイダへ押し出す」唯一のループなので、打ち切ると同じジョブに含まれる他のパターン
        // 機械に投げる加工パターンなどが、そのtickはまったく処理されない
        // Quantum CPUの予算は毎tick補充されるため、仕事がある限り他のプロバイダが永久に飢える
        debt += done;
        pushed = done;
        return -1;
    }

    /** 溜まっている引き算を消費して、CPUに渡す1 tick予算を返す。 */
    public int reduce(int maxPatterns) {
        int used = debt;
        debt = 0;
        return Math.max(0, maxPatterns - used);
    }

    /** 戻り値に足し戻すぶんを返して0に戻す。 */
    public int takePushed() {
        int value = pushed;
        pushed = 0;
        return value;
    }
}
