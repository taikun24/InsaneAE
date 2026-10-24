package jp.main.taikun.insaneae.integration.aco;

import appeng.api.stacks.AEKey;
import com.mojang.logging.LogUtils;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Quantum CPU の完成品待ちの台帳。実体 (ACO か内蔵) を<b>初めて何かを積むときに</b>選び、
 * 実体が例外を投げたら<b>その場で内蔵台帳に乗り換える</b>。
 *
 * <h2>なぜ遅らせるのか</h2>
 * <p>ブロックエンティティは、チャンクの読み込みやクライアント側の表示、他 Mod の走査などで
 * 頻繁に作られる。そのたびに ACO の台帳を作ると、反射の呼び出しが毎回走るうえ、
 * 何も積まれないまま捨てられることがほとんどになる。完成品待ちが実際に発生するまでは
 * 「空の台帳」として振る舞い、実体を作らない。</p>
 *
 * <h2>乗り換え</h2>
 * <p>{@link PendingOutputLedger} は「例外を投げない」約束だが、外部実装にそれを
 * 強制はできない。ここで受け止めないと serverTick やクラフト処理ごと落ちる。
 * 乗り換え時はまず {@code snapshot()} で中身の救出を試み、それも失敗したときだけ
 * 空で再出発する (損害は「未搬入の完成品」に限られ、ワールドは壊れない)。</p>
 */
final class LazyFailsafeLedger implements PendingOutputLedger {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Supplier<PendingOutputLedger> opener;

    /** 実体。まだ何も積まれていなければ null。 */
    @Nullable
    private volatile PendingOutputLedger delegate;
    private boolean failedOver;

    LazyFailsafeLedger(Supplier<PendingOutputLedger> opener) {
        this.opener = opener;
    }

    private synchronized PendingOutputLedger open() {
        PendingOutputLedger current = delegate;
        if (current == null) {
            current = opener.get();
            delegate = current;
        }
        return current;
    }

    private synchronized PendingOutputLedger fallback(RuntimeException failure) {
        if (!failedOver) {
            failedOver = true;
            LOGGER.error("InsaneAE: ACO BigInteger ledger threw; switching to the local ledger", failure);
            LocalBigIntegerOutputLedger local = new LocalBigIntegerOutputLedger();
            PendingOutputLedger broken = delegate;
            if (broken != null) {
                try {
                    for (Map.Entry<AEKey, BigInteger> entry : broken.snapshot().entrySet()) {
                        local.add(entry.getKey(), entry.getValue());
                    }
                } catch (RuntimeException rescueFailure) {
                    LOGGER.error("InsaneAE: could not rescue pending outputs from the ACO ledger; "
                            + "pending (undelivered) outputs are lost", rescueFailure);
                }
            }
            delegate = local;
        }
        return delegate;
    }

    @Override
    public void add(AEKey key, BigInteger amount) {
        if (key == null || amount == null || amount.signum() <= 0) {
            // 何も積まないなら実体を作る理由も無い。
            return;
        }
        try {
            open().add(key, amount);
        } catch (RuntimeException failure) {
            fallback(failure).add(key, amount);
        }
    }

    @Override
    public long drain(AEKey key, long maximum) {
        PendingOutputLedger current = delegate;
        if (current == null) {
            return 0L;
        }
        try {
            return current.drain(key, maximum);
        } catch (RuntimeException failure) {
            return fallback(failure).drain(key, maximum);
        }
    }

    @Override
    public Map<AEKey, BigInteger> snapshot() {
        PendingOutputLedger current = delegate;
        if (current == null) {
            return Map.of();
        }
        try {
            return current.snapshot();
        } catch (RuntimeException failure) {
            return fallback(failure).snapshot();
        }
    }

    @Override
    public boolean isEmpty() {
        PendingOutputLedger current = delegate;
        if (current == null) {
            return true;
        }
        try {
            return current.isEmpty();
        } catch (RuntimeException failure) {
            return fallback(failure).isEmpty();
        }
    }

    @Override
    public void clear() {
        PendingOutputLedger current = delegate;
        if (current == null) {
            return;
        }
        try {
            current.clear();
        } catch (RuntimeException failure) {
            fallback(failure).clear();
        }
    }
}
