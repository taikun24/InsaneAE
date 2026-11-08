package jp.main.taikun.insaneae.quantum;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Optional;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry;

/**
 * 実行中クラフトジョブのうち、まとめ処理 ({@link QuantumBulkCrafting}) が必要とする部分だけを
 * 抜き出した窓口。どのModのクラフトCPUかを問わないようにするための抽象。
 *
 * <h2>なぜ抽象が要るのか</h2>
 * <p>AE2のクラフトCPU ({@code appeng.crafting.execution.CraftingCpuLogic}) は、
 * 各種アドオンにまるごと複製されている。Advanced AEのQuantum Computer
 * ({@code net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic}) がその代表で、
 * {@code executeCrafting} の中身はほぼ同じなのにクラスも、抱えているジョブの型も別物
 * AAEは同名の{@code ExecutingCraftingJob}を自前で持っている。</p>
 *
 * <p>そのためAE2のクラスを直に触る作りにしていると、そういうCPUを使っている環境では
 * まとめ処理が丸ごと素通りされ、1クラフトずつの遅い経路に落ちる。
 * 実際にプロファイルで {@code AdvCraftingCPULogic.executeCrafting} →
 * {@code QuantumCpuLogic.pushPattern} が1回ずつ呼ばれているのが観測された。</p>
 *
 * <p>そこで「ジョブから何が要るか」だけをここに定義し、CPUごとの差は実装側に閉じ込める。
 * 新しいCPUに対応するときはこの窓口の実装を1つ足して、注入点を1つ増やすだけで済む。</p>
 *
 * <p>抱えている中身の型は {@link ListCraftingInventory} も {@link IPatternDetails} も
 * AE2のものがそのまま使われていることが多いが、進捗カウンタ
 * ({@code ElapsedTimeTracker}) だけはAdvanced AEが自前のコピーを持っている
 * 1.3.6と1.6.12で確認した。そのためカウンタは型を決めず{@link Object}で受け渡し、
 * 呼び方の差は {@link TimeTrackerAdapter} が吸収する。</p>
 *
 * @see Ae2CraftingJobView       AE2本体のクラフトCPU用。コンパイル時に型が分かる
 * @see ReflectiveCraftingJobView複製されたCPU用。フィールド名で辿る
 */
public interface CraftingJobView {

    /** CPUの在庫。ここから材料を取り出す。 */
    ListCraftingInventory getInventory();

    /** 完成待ちのアイテム。ここに入れておかないとCPUがクラフト結果を受け取らない。 */
    ListCraftingInventory getWaitingFor();

    /**
     * 進捗表示用のカウンタ。AE2の {@code ElapsedTimeTracker} とは限らない
     * Advanced AEは自前のコピーを持つので、{@link TimeTrackerAdapter}経由で触ること。
     */
    Object getTimeTracker();

    /** 保存が必要になったことをCPUに伝える。 */
    void markDirty();

    /** Exact task cursor for an optional ACO BigInteger job. */
    default Optional<AcoBigIntegerJobRegistry.CraftingCursor> exactTasks() {
        return Optional.empty();
    }

    /**
     * ACOの正確なBigInteger実行がこのジョブを所有しているか。
     *
     * <p>trueならまとめ処理は何もしない。ACO 1.5.23以降、exactジョブの実行と納品は
     * ACOの {@code PhysicalCraftingTreeTransaction} が丸ごと持っており、
     * こちらが1回でも進めるとACOの台帳に無い実行になる
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

    /** 残っているタスクを、パターンと残り回数の組でひとつずつ見ていく。 */
    TaskCursor tasks();

    /**
     * タスクの表を走査するためのカーソル。{@link java.util.Iterator} と同じ約束で、
     * {@link #remove()} は直前に {@link #next()} が返したタスクを表から外す。
     */
    interface TaskCursor {

        /** 次のタスクへ進む。もう無ければfalse。 */
        boolean next();

        /** 今のタスクのパターン。 */
        IPatternDetails details();

        /** 今のタスクの残り回数。 */
        long remaining();

        /** 今のタスクの残り回数を書き換える。 */
        void setRemaining(long value);

        /**
         * 今のタスクを終わったものとして扱う。表から実際に抜くかどうかは実装による
         * {@code MapTaskCursor}は残りを0にするだけで、抜くのはCPU本体のループに任せる。
         */
        void remove();
    }
}
