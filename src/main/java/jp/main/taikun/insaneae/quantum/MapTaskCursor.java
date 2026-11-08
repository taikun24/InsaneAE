package jp.main.taikun.insaneae.quantum;

import appeng.api.crafting.IPatternDetails;

import java.util.Iterator;
import java.util.Map;

/**
 * {@code Map<IPatternDetails, TaskProgress>} を走査するカーソル。
 *
 * <p>AE2も、それを複製したCPUも、タスクをこの形のMapで持っている。
 * 違うのは値の型だけなので、それぞれ自前の{@code TaskProgress}を使う。
 * 残り回数の読み書きだけを {@link #read} / {@link #write} に切り出してある。</p>
 */
abstract class MapTaskCursor implements CraftingJobView.TaskCursor {

    private final Iterator<? extends Map.Entry<?, ?>> iterator;
    private Map.Entry<?, ?> current;

    MapTaskCursor(Map<?, ?> tasks) {
        this.iterator = tasks.entrySet().iterator();
    }

    /** 残り回数を読む。{@code progress}は表の値で、CPUごとに型が違う。 */
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
     * 「このタスクは終わった」を伝える。表からは抜かない。
     *
     * <h2>抜いてはいけない理由</h2>
     * <p>タスクの表はCPU本体のもので、誰が抜いたかを見ている第三者がいる。
     * ACOはBigIntegerの正確な台帳を別に持っており、コミットのたびに
     * 「ジョブに入っているパターンの集合」が自分の台帳と一致するかを
     * {@code Set.equals} で検査する ({@code AdvancedAeExecutingCraftingJobTransactionAccessMixin})。
     * こちらが表から直接抜くと、ACOの台帳からは消えたことが見えないので集合が食い違い、</p>
     *
     * <pre>
     * IllegalStateException: exact task definitions do not match the Advanced AE job
     * → Quarantined Advanced AE exact CPU ...
     * </pre>
     *
     * <p>でジョブごと隔離される。残り0のタスクを抜くのは元々CPU本体のループが
     * 各tickの先頭でやっていることなので、そちらに任せれば台帳も一緒に更新される。
     * こちらは残りを0にするところまでで止める。</p>
     */
    @Override
    public void remove() {
        write(current.getValue(), 0L);
        current = null;
    }
}
