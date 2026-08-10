package jp.main.taikun.insaneae.integration.aco;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ACOが無い場合にも使える、同じ会計規則の最小実装。
 *
 * <p>{@link PendingOutputLedger} の約束どおり<b>例外を投げない</b>。
 * 不正な引数 (null・0 以下) は黙って無視する。</p>
 */
final class LocalBigIntegerOutputLedger implements PendingOutputLedger {
    private final Map<AEKey, BigInteger> amounts = new LinkedHashMap<>();

    @Override
    public synchronized void add(AEKey key, BigInteger amount) {
        if (key == null || amount == null || amount.signum() <= 0) {
            return;
        }
        amounts.merge(key, amount, BigInteger::add);
    }

    @Override
    public synchronized long drain(AEKey key, long maximum) {
        if (key == null || maximum <= 0L) {
            return 0L;
        }
        BigInteger current = amounts.get(key);
        if (current == null || current.signum() <= 0) {
            return 0L;
        }
        BigInteger drained = current.min(BigInteger.valueOf(maximum));
        BigInteger remaining = current.subtract(drained);
        if (remaining.signum() == 0) {
            amounts.remove(key);
        } else {
            amounts.put(key, remaining);
        }
        return drained.longValueExact();
    }

    @Override
    public synchronized Map<AEKey, BigInteger> snapshot() {
        return Map.copyOf(amounts);
    }

    @Override
    public synchronized boolean isEmpty() {
        return amounts.isEmpty();
    }

    @Override
    public synchronized void clear() {
        amounts.clear();
    }
}
