package jp.main.taikun.insaneae.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 大量のパターン枠を持つプロバイダ用の {@link PatternProviderLogic}。
 * 特大パターンプロバイダーと Quantum CPU ({@code QuantumCpuLogic}) が共用する。
 *
 * <p>AE2 のままでは 1620 枠に耐えないので 2 点変えてある:</p>
 * <ul>
 *   <li>AE2 はパターンを<b>1 枚出し入れするたび</b>に {@link #updatePatterns()} を呼び、
 *       全スロットのデコードとグリッドのクラフト索引の再構築を行う。パターン端末や
 *       インポートバスでまとめて動かしたときに刺さるので、ここでは印だけ付けて
 *       {@link #flushPatternUpdate()} に回す (ホスト側が毎 tick 流す)。</li>
 *   <li>「持っているか」の判定 ({@link #hasPattern}) を<b>ハッシュ集合</b>にして、
 *       1 クラフトあたりの線形探索を無くしている。AE2 の {@link #getAvailablePatterns()} は
 *       {@code ArrayList} をそのまま返すので {@code contains} がパターン数に比例する。</li>
 * </ul>
 *
 * <p>遅れは最大 1 tick。ホストの {@code serverTick()} が毎 tick 流すほか、
 * パターンを実際に参照する経路 ({@link #getAvailablePatterns()} / {@link #hasPattern}) が
 * 必ず先に流すので、古い一覧が見えることはない。</p>
 */
public class InsanePatternProviderLogic extends PatternProviderLogic {

    /**
     * 入っているパターンの集合。{@code getAvailablePatterns().contains} の O(1) 版
     * ({@link #hasPattern} から引く)。
     */
    private final Set<IPatternDetails> patternSet = new HashSet<>();

    /** パターンの再読み込みが必要か。{@link #flushPatternUpdate()} でまとめて処理する。 */
    private boolean patternsDirty = true;

    public InsanePatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, int slots) {
        super(mainNode, host, slots);
    }

    /** AE2 が 1 枚出し入れするたびに呼ぶ再読み込み。印だけ付けて後でまとめて処理する。 */
    @Override
    public void updatePatterns() {
        patternsDirty = true;
    }

    /** 溜めていたパターン更新を実行する。何度呼んでも安全。 */
    public void flushPatternUpdate() {
        if (!patternsDirty) {
            return;
        }
        patternsDirty = false;
        super.updatePatterns();
        patternSet.clear();
        patternSet.addAll(super.getAvailablePatterns());
        onPatternsFlushed();
    }

    /** パターン一覧が実際に更新されたときの追加処理 (Quantum CPU が組み立てキャッシュを捨てるのに使う)。 */
    protected void onPatternsFlushed() {
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        flushPatternUpdate();
        return super.getAvailablePatterns();
    }

    /** このプロバイダがそのパターンを持っているか。{@code getAvailablePatterns().contains} の O(1) 版。 */
    protected final boolean hasPattern(IPatternDetails details) {
        flushPatternUpdate();
        return patternSet.contains(details);
    }
}
