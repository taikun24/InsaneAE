package jp.main.taikun.insaneae.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import jp.main.taikun.insaneae.menu.PatternPagingHandshake;
import jp.main.taikun.insaneae.quantum.IBulkCraftingProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
//? if <1.21 {
/*import java.util.Objects;
*///?}

/**
 * 大量のパターン枠を持つプロバイダ用の {@link PatternProviderLogic}。
 * 特大パターンプロバイダーとQuantum CPU ({@code QuantumCpuLogic}) が共用する。
 *
 * <p>AE2のままでは1620枠に耐えないので3点変えてある:</p>
 * <ul>
 *   <li>AE2はパターンを1枚出し入れするたびに {@link #updatePatterns()} を呼び、
 *       全スロットのデコードとグリッドのクラフト索引の再構築を行う。パターン端末や
 *       インポートバスでまとめて動かしたときに刺さるので、ここでは印だけ付けて
 *       {@link #flushPatternUpdate()}に回す。ホスト側が毎tick流す。</li>
 *   <li>「持っているか」の判定 ({@link #hasPattern}) をハッシュ集合にして、
 *       1クラフトあたりの線形探索を無くしている。AE2の {@link #getAvailablePatterns()} は
 *       {@code ArrayList} をそのまま返すので {@code contains} がパターン数に比例する。</li>
 *   <li>再読み込みのたびの全枠デコードを、変わっていないパターンについては省く
 *       ({@link #decodeCached})。</li>
 * </ul>
 *
 * <p>遅れは最大1 tick。ホストの {@code serverTick()} が毎tick流すほか、
 * パターンを実際に参照する経路 ({@link #getAvailablePatterns()} / {@link #hasPattern}) が
 * 必ず先に流すので、古い一覧が見えることはない。</p>
 *
 * <p>ただしグリッドのクラフト索引だけは自分で見に来てくれない。
 * 索引の再構築は {@code super.updatePatterns()} の中で予約されるので、こちらが溜めている間は
 * 索引も古いままになる。AE2本体のクラフト計算は非同期に走るので1 tickの遅れを吸収するが、
 * 発注の時点で索引を写し取るMod (AE2 Crafting Optimizer) と同居すると
 * 「入れたばかりのパターンが使われない」として表に出る。ゲームテストで確認した。
 * そこでそのtickで最初の1回だけは即座に流す — まとめ処理の狙いは
 * 「1 tickに何十回も再構築しない」ことなので、1 tickあたりの再構築回数は変わらない。</p>
 *
 * <p>加工パターンは {@link ProcessingBulkPush} でまとめて押し出せる。</p>
 */
public class InsanePatternProviderLogic extends PatternProviderLogic implements IBulkCraftingProvider {

    /**
     * 入っているパターンの集合。{@code getAvailablePatterns().contains} のO(1) 版
     * {@link #hasPattern}から引く。
     */
    private final Set<IPatternDetails> patternSet = new HashSet<>();

    /** パターンの再読み込みが必要か。{@link #flushPatternUpdate()} でまとめて処理する。 */
    private boolean patternsDirty = true;

    /** ホスト。現在のtickを引くのに使う。 */
    private final PatternProviderLogicHost patternHost;

    private final IManagedGridNode gridNode;

    /** 最後に再読み込みしたtick。同じtickの2回目以降だけをまとめる。 */
    private long lastFlushTick = Long.MIN_VALUE;

    public InsanePatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, int slots) {
        super(mainNode, host, slots);
        this.patternHost = host;
        this.gridNode = mainNode;
    }

    @Override
    public long getBulkCapacity(IPatternDetails details) {
        return ProcessingBulkPush.capacity(this, details);
    }

    @Override
    public long pushPatternBulk(IPatternDetails details, KeyCounter[] inputHolder, long times) {
        return ProcessingBulkPush.push(this, details, inputHolder, times);
    }

    @Nullable
    BlockEntity hostBlockEntity() {
        return patternHost.getBlockEntity();
    }

    boolean isNodeActive() {
        return gridNode.isActive();
    }

    /**
     * AE2が1枚出し入れするたびに呼ぶ再読み込み。
     * そのtickの最初の1回はすぐに流し、2回目以降だけを溜める。
     */
    @Override
    public void updatePatterns() {
        patternsDirty = true;
        if (currentTick() != lastFlushTick) {
            flushPatternUpdate();
        } else {
            PENDING.add(this);
        }
    }

    /**
     * 更新を溜めているプロバイダ。クラフト計算を始める直前に流す ({@link #flushAllPending})。
     * サーバスレッドからしか触らない。
     */
    private static final Set<InsanePatternProviderLogic> PENDING =
            Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * 溜めているパターン更新をすべて流す。{@code CraftingServicePatternFlushMixin} が
     * クラフト計算の開始時に呼ぶ。
     *
     * <p>ACO 2.0は計算を始めた瞬間にグリッドのクラフト索引を写し取るので、同じtickに
     * 入れた2枚目以降のパターンが溜まったままだと、その計算では使われない。</p>
     */
    public static void flushAllPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        for (InsanePatternProviderLogic logic : List.copyOf(PENDING)) {
            logic.flushPatternUpdate();
        }
        PENDING.clear();
    }

    /**
     * パターン枠のインベントリ。メニューを組み立てている最中だけ
     * 1ページぶんの窓を返す。{@link PatternPagingHandshake}を参照。
     *
     * <p>{@code PatternProviderMenu} のコンストラクタは、ここで返した枠数だけスロットを並べる。
     * 1620枠すべてをスロットにすると毎tickの同期がそのぶん走るので、窓だけ見せる。
     * それ以外の呼び出し、つまりパターンの出し入れ、クラフト計算、NBTは、全枠のまま。</p>
     */
    @Override
    public InternalInventory getPatternInv() {
        return PatternPagingHandshake.windowFor(patternHost, super.getPatternInv());
    }

    /** 溜めていたパターン更新を実行する。何度呼んでも安全。 */
    public void flushPatternUpdate() {
        if (!patternsDirty) {
            return;
        }
        patternsDirty = false;
        PENDING.remove(this);
        // 先に印を進めること。super.updatePatterns() の中から戻ってきても、
        // 同じtickなら溜めるだけになって再帰しない
        lastFlushTick = currentTick();
        nextDecodeCache.clear();
        super.updatePatterns();
        // 今回出てきたパターンだけを次へ持ち越す。抜いたパターンの記憶が溜まらない
        var used = nextDecodeCache;
        nextDecodeCache = decodeCache;
        decodeCache = used;
        nextDecodeCache.clear();
        patternSet.clear();
        patternSet.addAll(super.getAvailablePatterns());
        onPatternsFlushed();
    }

    /**
     * パターンのデコード結果を覚えておく表 (issue #16)。
     *
     * <p>AE2は1枚変わるたびに全枠をデコードし直す。1620枠ではそれだけで1回30msほどかかり、
     * パターンを続けて出し入れすると毎回引っかかる。変わっていない枠は前回の結果をそのまま返す。
     * デコードに失敗したnullも覚える。</p>
     *
     * <p>鍵は枠に入っている{@link ItemStack}そのもの、つまり同一性。中身で引く
     * ({@code AEItemKey.of}) とNBTの複製が全枠ぶん走り、それだけで半分近く食う。
     * 枠の中で中身だけ書き換えられた場合に備えて、中身のハッシュも一緒に照合する。</p>
     *
     * <p>{@code super.updatePatterns()} の中から {@code PatternProviderDecodeCacheMixin} 経由で呼ばれる。
     * Mixinが当たっていなければ呼ばれず、表は空のまま。毎回デコードする元の動作になる。</p>
     */
    private Map<ItemStack, Decoded> decodeCache = new IdentityHashMap<>();
    private Map<ItemStack, Decoded> nextDecodeCache = new IdentityHashMap<>();

    private record Decoded(int contentHash, @Nullable IPatternDetails details) {
    }

    /** 表を作ったときのレシピ一覧。/reloadで入れ物ごと替わるので、そのときは表を捨てる。 */
    @Nullable
    private RecipeManager decodeCacheRecipes;

    /** {@code PatternDetailsHelper.decodePattern} の記憶つき版。 */
    @Nullable
    public IPatternDetails decodeCached(ItemStack stack, @Nullable Level level) {
        if (level == null || stack.isEmpty()) {
            return PatternDetailsHelper.decodePattern(stack, level);
        }
        RecipeManager recipes = level.getRecipeManager();
        if (recipes != decodeCacheRecipes) {
            decodeCache.clear();
            decodeCacheRecipes = recipes;
        }
        //? if >=1.21 {
        int contentHash = ItemStack.hashItemAndComponents(stack);
        //?} else {
        /*int contentHash = Objects.hash(stack.getItem(), stack.getTag());
        *///?}
        Decoded decoded = decodeCache.get(stack);
        if (decoded == null || decoded.contentHash() != contentHash) {
            decoded = new Decoded(contentHash, PatternDetailsHelper.decodePattern(stack, level));
        }
        nextDecodeCache.put(stack, decoded);
        return decoded.details();
    }

    /**
     * 現在のtick。ワールドに乗る前、NBT読み込み中などは
     * {@link Long#MIN_VALUE} を返して即時の流し込みをしない
     * 初期値と同じ値なので、「同じtick」扱いになる。
     */
    private long currentTick() {
        var blockEntity = patternHost.getBlockEntity();
        var level = blockEntity == null ? null : blockEntity.getLevel();
        return level == null ? Long.MIN_VALUE : level.getGameTime();
    }

    /** パターン一覧が実際に更新されたときの追加処理。Quantum CPUが組み立てキャッシュを捨てるのに使う。 */
    protected void onPatternsFlushed() {
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        flushPatternUpdate();
        return super.getAvailablePatterns();
    }

    /** このプロバイダがそのパターンを持っているか。{@code getAvailablePatterns().contains} のO(1) 版。 */
    protected final boolean hasPattern(IPatternDetails details) {
        flushPatternUpdate();
        return patternSet.contains(details);
    }
}
