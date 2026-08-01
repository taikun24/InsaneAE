package jp.main.taikun.insaneae.quantum;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.inv.ListCraftingInventory;

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
 * <p>幸い、抱えている中身の型は {@link ListCraftingInventory} も {@link ElapsedTimeTracker} も
 * {@link IPatternDetails} も<b>AE2 のものがそのまま使われている</b> (複製されているのは
 * 入れ物のクラスだけ) ので、抽象化が要るのは「どこから取り出すか」だけになる。</p>
 *
 * @see Ae2CraftingJobView       AE2 本体のクラフト CPU 用 (コンパイル時に型が分かる)
 * @see ReflectiveCraftingJobView 複製された CPU 用 (フィールド名で辿る)
 */
public interface CraftingJobView {

    /** CPU の在庫。ここから材料を取り出す。 */
    ListCraftingInventory getInventory();

    /** 完成待ちのアイテム。ここに入れておかないと CPU がクラフト結果を受け取らない。 */
    ListCraftingInventory getWaitingFor();

    /** 進捗表示用のカウンタ。 */
    ElapsedTimeTracker getTimeTracker();

    /** 保存が必要になったことを CPU に伝える。 */
    void markDirty();

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
