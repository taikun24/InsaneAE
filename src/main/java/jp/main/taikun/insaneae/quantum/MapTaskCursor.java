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

    @Override
    public void remove() {
        iterator.remove();
        current = null;
    }
}
