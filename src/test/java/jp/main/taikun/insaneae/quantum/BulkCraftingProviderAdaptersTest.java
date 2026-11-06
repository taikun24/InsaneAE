package jp.main.taikun.insaneae.quantum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BulkCraftingProviderAdaptersTest {

    /** 他 Mod のブロックエンティティ役。InsaneAE の型は実装していない。 */
    private static class ForeignProvider implements ICraftingProvider {
        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            return false;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class ForeignChild extends ForeignProvider {
    }

    private static final class Unrelated extends ForeignProvider {
    }

    private interface Marker extends ICraftingProvider {
    }

    private static final class MarkedProvider extends ForeignProvider implements Marker {
    }

    private static final class SelfBulk extends ForeignProvider implements IBulkCraftingProvider {
        @Override
        public long getBulkCapacity(IPatternDetails details) {
            return 1;
        }

        @Override
        public long pushPatternBulk(IPatternDetails details, KeyCounter[] inputHolder, long times) {
            return times;
        }
    }

    private record Handler(ICraftingProvider owner) implements BulkCraftingHandler {
        @Override
        public long getBulkCapacity(IPatternDetails details) {
            return 64;
        }

        @Override
        public long pushPatternBulk(IPatternDetails details, KeyCounter[] inputHolder, long times) {
            return times;
        }
    }

    @Test
    void subclassesUseTheRegisteredAdapterAndKeepOneHandlerPerProvider() {
        AtomicInteger created = new AtomicInteger();
        BulkCraftingProviderAdapters.register(ForeignProvider.class, provider -> {
            created.incrementAndGet();
            return new Handler(provider);
        });

        ForeignChild provider = new ForeignChild();
        BulkCraftingHandler first = BulkCraftingProviderAdapters.find(provider);
        assertNotNull(first);
        assertSame(provider, ((Handler) first).owner());
        // 窓の間の清算は同じ相手を見分けるので、同じプロバイダには同じアダプタを返す。
        assertSame(first, BulkCraftingProviderAdapters.find(provider));
        assertEquals(1, created.get());

        assertNotSame(first, BulkCraftingProviderAdapters.find(new ForeignChild()));
    }

    @Test
    void theNearestRegistrationWins() {
        BulkCraftingProviderAdapters.register(ForeignProvider.class, Handler::new);
        BulkCraftingProviderAdapters.register(Unrelated.class, provider -> null);

        // 子クラスの登録が null を返す = そのプロバイダは通常の経路のまま。
        assertNull(BulkCraftingProviderAdapters.find(new Unrelated()));
        assertNotNull(BulkCraftingProviderAdapters.find(new ForeignChild()));
    }

    @Test
    void interfacesCanBeRegistered() {
        BulkCraftingProviderAdapters.register(Marker.class, Handler::new);
        assertNotNull(BulkCraftingProviderAdapters.find(new MarkedProvider()));
    }

    @Test
    void ownImplementationComesFirst() {
        BulkCraftingProviderAdapters.register(ForeignProvider.class, Handler::new);
        SelfBulk provider = new SelfBulk();
        assertSame(provider, BulkCraftingProviderAdapters.find(provider));
    }

    @Test
    void aThrowingAdapterFallsBackToTheNormalPath() {
        final class Broken extends ForeignProvider {
        }
        BulkCraftingProviderAdapters.register(Broken.class, provider -> {
            throw new IllegalStateException("boom");
        });
        assertNull(BulkCraftingProviderAdapters.find(new Broken()));
    }

    private static void assertNotSame(Object unexpected, Object actual) {
        org.junit.jupiter.api.Assertions.assertNotSame(unexpected, actual);
    }
}
