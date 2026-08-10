package jp.main.taikun.insaneae.integration.aco;

import appeng.api.stacks.AEKey;
import com.mojang.logging.LogUtils;
import java.math.BigInteger;
import java.util.Map;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/** ACOが導入・有効な場合だけ公開BigInteger APIを選ぶ入口。 */
public final class OptionalAcoBigIntegerIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ACO_MOD_ID = "ae2_crafting_optimizer";

    private OptionalAcoBigIntegerIntegration() {
    }

    public static PendingOutputLedger createOutputLedger() {
        // ACOが無い場合は同じ正確会計の内蔵実装へ戻し、通常のInsaneAE導入を壊さない。
        if (!ModList.get().isLoaded(ACO_MOD_ID)) {
            return new LocalBigIntegerOutputLedger();
        }
        PendingOutputLedger ledger = AcoBigIntegerOutputLedger.tryCreate().orElse(null);
        if (ledger != null) {
            LOGGER.info("InsaneAE: ACO BigInteger amount ledger integration enabled");
            // 実行時に ACO 側が例外を投げても serverTick を巻き込まないよう、退避付きで包む。
            return new FailsafeLedger(ledger);
        }
        return new LocalBigIntegerOutputLedger();
    }

    /**
     * 外部実装 (ACO) が実行時に例外を投げたら、<b>その場で内蔵台帳に乗り換える</b>ラッパー。
     *
     * <p>{@link PendingOutputLedger} は「例外を投げない」約束だが、外部実装にそれを
     * 強制はできない。ここで受け止めないと serverTick やクラフト処理ごと落ちる。
     * 乗り換え時はまず {@code snapshot()} で中身の救出を試み、それも失敗したときだけ
     * 空で再出発する (損害は「未搬入の完成品」に限られ、ワールドは壊れない)。</p>
     */
    private static final class FailsafeLedger implements PendingOutputLedger {
        private PendingOutputLedger delegate;
        private boolean failed;

        FailsafeLedger(PendingOutputLedger delegate) {
            this.delegate = delegate;
        }

        private synchronized PendingOutputLedger fallback(RuntimeException failure) {
            if (!failed) {
                failed = true;
                LOGGER.error("InsaneAE: ACO BigInteger ledger threw; switching to the local ledger", failure);
                LocalBigIntegerOutputLedger local = new LocalBigIntegerOutputLedger();
                try {
                    for (Map.Entry<AEKey, BigInteger> entry : delegate.snapshot().entrySet()) {
                        local.add(entry.getKey(), entry.getValue());
                    }
                } catch (RuntimeException rescueFailure) {
                    LOGGER.error("InsaneAE: could not rescue pending outputs from the ACO ledger; "
                            + "pending (undelivered) outputs are lost", rescueFailure);
                }
                delegate = local;
            }
            return delegate;
        }

        @Override
        public void add(AEKey key, BigInteger amount) {
            try {
                delegate.add(key, amount);
            } catch (RuntimeException failure) {
                fallback(failure).add(key, amount);
            }
        }

        @Override
        public long drain(AEKey key, long maximum) {
            try {
                return delegate.drain(key, maximum);
            } catch (RuntimeException failure) {
                return fallback(failure).drain(key, maximum);
            }
        }

        @Override
        public Map<AEKey, BigInteger> snapshot() {
            try {
                return delegate.snapshot();
            } catch (RuntimeException failure) {
                return fallback(failure).snapshot();
            }
        }

        @Override
        public boolean isEmpty() {
            try {
                return delegate.isEmpty();
            } catch (RuntimeException failure) {
                return fallback(failure).isEmpty();
            }
        }

        @Override
        public void clear() {
            try {
                delegate.clear();
            } catch (RuntimeException failure) {
                fallback(failure).clear();
            }
        }
    }
}
