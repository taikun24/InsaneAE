package jp.main.taikun.insaneae.quantum;

import appeng.api.networking.crafting.ICraftingProvider;
import com.google.common.collect.MapMaker;
import jp.main.taikun.insaneae.util.LogOnce;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * {@link IBulkCraftingProvider} を実装できないプロバイダを、まとめ処理の経路に乗せるための登録口。
 *
 * <p>AE2 のクラフトサービスに登録されているプロバイダ (多くはブロックエンティティそのもの) に
 * InsaneAE の型を実装させると、InsaneAE を任意依存にしている Mod は InsaneAE が無い環境で
 * 起動時に落ちる。代わりに、InsaneAE が入っているときだけ呼ばれるクラスからここへ登録すれば、
 * プロバイダのクラス自体は InsaneAE の型を参照しなくて済む。</p>
 *
 * <pre>{@code
 * // InsaneAE が入っているときだけロードされるクラスで、commonSetup などから
 * BulkCraftingProviderAdapters.register(MyAssemblerBlockEntity.class, be -> new MyBulkHandler(be));
 * }</pre>
 *
 * <p>探すのは {@code provider instanceof IBulkCraftingProvider} が外れたときだけ。登録したクラスの
 * 子クラスにも当たる (一番近い親の登録が使われる)。忙しいかどうかは元のプロバイダの
 * {@code isBusy()} で判定する。アダプタはプロバイダ 1 つにつき 1 回だけ作られ、
 * そのプロバイダが残っている間は同じものが使われる (同一性で覚え、弱参照なので解放は妨げない)。
 * そのプロバイダでは使わないときは、アダプタの関数から null を返せばよい。</p>
 */
public final class BulkCraftingProviderAdapters {

    private static final Logger LOGGER = LoggerFactory.getLogger(BulkCraftingProviderAdapters.class);

    private static final Map<Class<?>, Function<ICraftingProvider, BulkCraftingHandler>> ADAPTERS =
            new ConcurrentHashMap<>();
    /** 具象クラス → 使う登録。登録が増えたら捨てる。 */
    private static final Map<Class<?>, Optional<Function<ICraftingProvider, BulkCraftingHandler>>> RESOLVED =
            new ConcurrentHashMap<>();
    /** プロバイダ → そのアダプタ。キーは同一性・弱参照。 */
    private static final ConcurrentMap<ICraftingProvider, Optional<BulkCraftingHandler>> HANDLERS =
            new MapMaker().weakKeys().makeMap();

    private BulkCraftingProviderAdapters() {
    }

    /**
     * {@code type} (とその子クラス) のプロバイダに使うアダプタを登録する。同じクラスへの 2 回目の登録は上書き。
     *
     * @param adapter プロバイダを受け取り、そのプロバイダのまとめ処理を返す。使わないなら null
     */
    @SuppressWarnings("unchecked")
    public static <T extends ICraftingProvider> void register(
            Class<T> type, Function<? super T, ? extends BulkCraftingHandler> adapter) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(adapter, "adapter");
        ADAPTERS.put(type, provider -> adapter.apply((T) provider));
        RESOLVED.clear();
        HANDLERS.clear();
    }

    /** そのプロバイダのまとめ処理。自分で実装していればそれ、無ければ登録されたアダプタ、どちらも無ければ null。 */
    @Nullable
    public static BulkCraftingHandler find(ICraftingProvider provider) {
        if (provider instanceof IBulkCraftingProvider bulk) {
            return bulk;
        }
        if (ADAPTERS.isEmpty()) {
            return null;
        }
        Optional<Function<ICraftingProvider, BulkCraftingHandler>> adapter =
                RESOLVED.computeIfAbsent(provider.getClass(), BulkCraftingProviderAdapters::resolve);
        if (adapter.isEmpty()) {
            return null;
        }
        return HANDLERS.computeIfAbsent(provider, p -> create(adapter.get(), p)).orElse(null);
    }

    private static Optional<BulkCraftingHandler> create(
            Function<ICraftingProvider, BulkCraftingHandler> adapter, ICraftingProvider provider) {
        try {
            return Optional.ofNullable(adapter.apply(provider));
        } catch (RuntimeException | LinkageError failure) {
            // 他 Mod のアダプタの不具合でクラフト自体を止めない。このプロバイダは通常の経路で動かす。
            if (LogOnce.first("bulk-adapter-failed:" + provider.getClass().getName())) {
                LOGGER.error("InsaneAE: bulk crafting adapter for {} failed; using the normal path",
                        provider.getClass().getName(), failure);
            }
            return Optional.empty();
        }
    }

    /** 具象クラスから親へたどり、一番近い登録を返す (クラスの親を先に、インターフェイスは後に見る)。 */
    private static Optional<Function<ICraftingProvider, BulkCraftingHandler>> resolve(Class<?> concrete) {
        for (Class<?> type = concrete; type != null; type = type.getSuperclass()) {
            Function<ICraftingProvider, BulkCraftingHandler> adapter = ADAPTERS.get(type);
            if (adapter != null) {
                return Optional.of(adapter);
            }
        }
        Deque<Class<?>> queue = new ArrayDeque<>();
        Set<Class<?>> seen = new HashSet<>();
        for (Class<?> type = concrete; type != null; type = type.getSuperclass()) {
            for (Class<?> iface : type.getInterfaces()) {
                queue.add(iface);
            }
        }
        while (!queue.isEmpty()) {
            Class<?> iface = queue.poll();
            if (!seen.add(iface)) {
                continue;
            }
            Function<ICraftingProvider, BulkCraftingHandler> adapter = ADAPTERS.get(iface);
            if (adapter != null) {
                return Optional.of(adapter);
            }
            for (Class<?> parent : iface.getInterfaces()) {
                queue.add(parent);
            }
        }
        return Optional.empty();
    }
}
