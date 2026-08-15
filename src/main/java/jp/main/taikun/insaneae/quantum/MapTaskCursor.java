package jp.main.taikun.insaneae.quantum;

import appeng.api.crafting.IPatternDetails;

import java.util.Iterator;
import java.util.Map;

/**
 * {@code Map<IPatternDetails, TaskProgress>} を舐めるカーソル。
 *
 * <p>AE2 も、それを複製した CPU も、タスクを<b>この形の表</b>で持っている。
 * 違うのは値の型 (それぞれ自前の {@code TaskProgress}) だけなので、
 * 残り回数の読み書きだけを {@link #read} / {@link #write} に切り出してある。</p>
 */
abstract class MapTaskCursor implements CraftingJobView.TaskCursor {

    private final Iterator<? extends Map.Entry<?, ?>> iterator;
    private Map.Entry<?, ?> current;

    MapTaskCursor(Map<?, ?> tasks) {
        this.iterator = tasks.entrySet().iterator();
    }

    /** 残り回数を読む。{@code progress} は表の値 (CPU ごとに型が違う)。 */
    protected abstract long read(Object progress);

    /** 残り回数を書く。 */
    protected abstract void write(Object progress, long value);

    @Override
    public boolean next() {
        if (!iterator.hasNext()) {
            current = null;
            return false;
        }
        current = iterator.next();
        return true;
    }

    @Override
    public IPatternDetails details() {
        return (IPatternDetails) current.getKey();
    }

    @Override
    public long remaining() {
        return read(current.getValue());
    }

    @Override
    public void setRemaining(long value) {
        write(current.getValue(), value);
    }

    /**
     * 「このタスクは終わった」を伝える。<b>表からは抜かない。</b>
     *
     * <h2>抜いてはいけない理由</h2>
     * <p>タスクの表は CPU 本体のもので、<b>誰が抜いたかを見ている第三者がいる</b>。
     * ACO は BigInteger の正確な台帳を別に持っており、コミットのたびに
     * 「ジョブに入っているパターンの集合」が自分の台帳と一致するかを
     * {@code Set.equals} で検査する ({@code AdvancedAeExecutingCraftingJobTransactionAccessMixin})。
     * こちらが表から直接抜くと、ACO の台帳からは<b>消えたことが見えない</b>ので集合が食い違い、</p>
     *
     * <pre>
     * IllegalStateException: exact task definitions do not match the Advanced AE job
     * → Quarantined Advanced AE exact CPU ...
     * </pre>
     *
     * <p>でジョブごと隔離される。残り 0 のタスクを抜くのは元々 CPU 本体のループが
     * 各 tick の先頭でやっていることなので、<b>そちらに任せれば台帳も一緒に更新される</b>。
     * こちらは残りを 0 にするところまでで止める。</p>
     */
    @Override
    public void remove() {
        write(current.getValue(), 0L);
        current = null;
    }
}
