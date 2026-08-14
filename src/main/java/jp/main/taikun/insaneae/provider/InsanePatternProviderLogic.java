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
 *
 * <p><b>ただしグリッドのクラフト索引だけは自分で見に来てくれない。</b>
 * 索引の再構築は {@code super.updatePatterns()} の中で予約されるので、こちらが溜めている間は
 * 索引も古いままになる。AE2 本体のクラフト計算は非同期に走るので 1 tick の遅れを吸収するが、
 * <b>発注の時点で索引を写し取る Mod</b> (AE2 Crafting Optimizer) と同居すると
 * 「入れたばかりのパターンが使われない」として表に出る (ゲームテストで確認)。
 * そこで<b>その tick で最初の 1 回だけは即座に流す</b> — まとめ処理の狙いは
 * 「1 tick に何十回も再構築しない」ことなので、1 tick あたりの再構築回数は変わらない。</p>
 */
public class InsanePatternProviderLogic extends PatternProviderLogic {

    /**
     * 入っているパターンの集合。{@code getAvailablePatterns().contains} の O(1) 版
     * ({@link #hasPattern} から引く)。
     */
    private final Set<IPatternDetails> patternSet = new HashSet<>();

    /** パターンの再読み込みが必要か。{@link #flushPatternUpdate()} でまとめて処理する。 */
    private boolean patternsDirty = true;

    /** ホスト (現在の tick を引くのに使う)。 */
    private final PatternProviderLogicHost patternHost;

    /** 最後に再読み込みした tick。同じ tick の 2 回目以降だけをまとめる。 */
    private long lastFlushTick = Long.MIN_VALUE;

    public InsanePatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, int slots) {
        super(mainNode, host, slots);
        this.patternHost = host;
    }

    /**
     * AE2 が 1 枚出し入れするたびに呼ぶ再読み込み。
     * その tick の最初の 1 回は<b>すぐに</b>流し、2 回目以降だけを溜める。
     */
    @Override
    public void updatePatterns() {
        patternsDirty = true;
        if (currentTick() != lastFlushTick) {
            flushPatternUpdate();
        }
    }

    /** 溜めていたパターン更新を実行する。何度呼んでも安全。 */
    public void flushPatternUpdate() {
        if (!patternsDirty) {
            return;
        }
        patternsDirty = false;
        // 先に印を進めること。super.updatePatterns() の中から戻ってきても、
        // 同じ tick なら溜めるだけになって再帰しない。
        lastFlushTick = currentTick();
        super.updatePatterns();
        patternSet.clear();
        patternSet.addAll(super.getAvailablePatterns());
        onPatternsFlushed();
    }

    /**
     * 現在の tick。ワールドに乗る前 (NBT 読み込み中など) は
     * {@link Long#MIN_VALUE} を返して<b>即時の流し込みをしない</b>
     * (初期値と同じ値なので「同じ tick」扱いになる)。
     */
    private long currentTick() {
        var blockEntity = patternHost.getBlockEntity();
        var level = blockEntity == null ? null : blockEntity.getLevel();
        return level == null ? Long.MIN_VALUE : level.getGameTime();
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
