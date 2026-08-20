package jp.main.taikun.insaneae.quantum;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Optional;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry;

/**
 * 実行中クラフトジョブのうち、まとめ処理 ({@link QuantumBulkCrafting}) が必要とする部分だけを
 * 抜き出した窓口。<b>どの Mod のクラフト CPU かを問わない</b>ようにするための抽象。
 *
 * <h2>なぜ抽象が要るのか</h2>
 * <p>AE2 のクラフト CPU ({@code appeng.crafting.execution.CraftingCpuLogic}) は、
 * 各種アドオンに<b>まるごと複製されている</b>。Advanced AE の Quantum Computer
 * ({@code net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic}) がその代表で、
 * {@code executeCrafting} の中身はほぼ同じなのに<b>クラスも、抱えているジョブの型も別物</b>
 * (AAE は同名の {@code ExecutingCraftingJob} を自前で持っている)。</p>
 *
 * <p>そのため AE2 のクラスを直に触る作りにしていると、そういう CPU を使っている環境では
 * まとめ処理が丸ごと素通りされ、<b>1 クラフトずつの遅い経路に落ちる</b>。
 * 実際にプロファイルで {@code AdvCraftingCPULogic.executeCrafting} →
 * {@code QuantumCpuLogic.pushPattern} が 1 回ずつ呼ばれているのが観測された。</p>
 *
 * <p>そこで「ジョブから何が要るか」だけをここに定義し、CPU ごとの差は実装側に閉じ込める。
 * 新しい CPU に対応するときは<b>この窓口の実装を 1 つ足して、注入点を 1 つ増やすだけ</b>で済む。</p>
 *
 * <p>抱えている中身の型は {@link ListCraftingInventory} も {@link IPatternDetails} も
 * <b>AE2 のものがそのまま使われている</b>ことが多いが、進捗カウンタ
 * ({@code ElapsedTimeTracker}) だけは Advanced AE が自前のコピーを持っている
 * (1.3.6 / 1.6.12 で確認)。そのためカウンタは型を決めず {@link Object} で受け渡し、
 * 呼び方の差は {@link TimeTrackerAdapter} が吸収する。</p>
 *
 * @see Ae2CraftingJobView       AE2 本体のクラフト CPU 用 (コンパイル時に型が分かる)
 * @see ReflectiveCraftingJobView 複製された CPU 用 (フィールド名で辿る)
 */
public interface CraftingJobView {

    /** CPU の在庫。ここから材料を取り出す。 */
    ListCraftingInventory getInventory();

    /** 完成待ちのアイテム。ここに入れておかないと CPU がクラフト結果を受け取らない。 */
    ListCraftingInventory getWaitingFor();

    /**
     * 進捗表示用のカウンタ。AE2 の {@code ElapsedTimeTracker} とは限らない
     * (Advanced AE は自前のコピーを持つ) ので、{@link TimeTrackerAdapter} 経由で触ること。
     */
    Object getTimeTracker();

    /** 保存が必要になったことを CPU に伝える。 */
    void markDirty();

    /** Exact task cursor for an optional ACO BigInteger job. */
    default Optional<AcoBigIntegerJobRegistry.CraftingCursor> exactTasks() {
        return Optional.empty();
    }

    /**
     * ACO の正確な BigInteger 実行がこのジョブを所有しているか。
     *
     * <p>true なら<b>まとめ処理は何もしない</b>。ACO 1.5.23 以降、exact ジョブの実行と納品は
     * ACO の {@code PhysicalCraftingTreeTransaction} が丸ごと持っており、
     * こちらが 1 回でも進めると ACO の台帳に無い実行になる
     * ({@link jp.main.taikun.insaneae.integration.aco.AcoExactJobOwnership})。</p>
     */
    default boolean isOwnedByAcoExactExecution() {
        return false;
    }

    /** Whether the native AE2 task loop must not run after the exact hook. */
    default boolean hasExactCraftingPlan() {
        return exactTasks().isPresent();
    }

    /** Network storage used to refill one bounded execution window. */
    default MEStorage getNetworkStorage() {
        return null;
    }

    /** Action source paired with {@link #getNetworkStorage()}. */
    default IActionSource getActionSource() {
        return null;
    }

    /** 残っているタスク (パターン → 残り回数) をひとつずつ見ていく。 */
    TaskCursor tasks();

    /**
     * タスクの表を舐めるためのカーソル。{@link java.util.Iterator} と同じ約束で、
     * {@link #remove()} は<b>直前に {@link #next()} が返したタスク</b>を表から外す。
     */
    interface TaskCursor {

        /** 次のタスクへ進む。もう無ければ false。 */
        boolean next();

        /** 今のタスクのパターン。 */
        IPatternDetails details();

        /** 今のタスクの残り回数。 */
        long remaining();

        /** 今のタスクの残り回数を書き換える。 */
        void setRemaining(long value);

        /** 今のタスクを表から外す。 */
        void remove();
    }
}
