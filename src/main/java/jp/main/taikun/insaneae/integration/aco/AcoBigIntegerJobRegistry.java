package jp.main.taikun.insaneae.integration.aco;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.ExecutingCraftingJob;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * BigInteger task counts attached to the ordinary AE2 job object.
 *
 * <p>AE2's visible TaskProgress remains a small compatibility facade. The
 * exact map is the execution source for InsaneAE's bounded Quantum CPU
 * windows, so a task never needs to be represented by one overflowing long.</p>
 */
public final class AcoBigIntegerJobRegistry {
    private static final Map<ExecutingCraftingJob, Job> JOBS = new IdentityHashMap<>();

    private AcoBigIntegerJobRegistry() {
    }

    public static synchronized void install(
            ExecutingCraftingJob job,
            AcoBigIntegerPlanBridge.Plan plan) {
        // 同一Jobへの再通知は状態を上書きせず、二重のExact台帳を作らない。
        JOBS.putIfAbsent(job, new Job(job, plan.patternTimes()));
    }

    public static synchronized Optional<Job> find(ExecutingCraftingJob job) {
        Job exact = JOBS.get(job);
        if (exact != null && exact.isEmpty()) {
            JOBS.remove(job);
            exact = null;
        }
        return Optional.ofNullable(exact);
    }

    public static synchronized void remove(ExecutingCraftingJob job) {
        JOBS.remove(job);
    }

    /** Exact task state for one ordinary AE2 job. */
    public static final class Job {
        private final ExecutingCraftingJob owner;
        private final Map<IPatternDetails, BigInteger> remaining;

        private Job(ExecutingCraftingJob owner, Map<IPatternDetails, BigInteger> patternTimes) {
            this.owner = owner;
            this.remaining = new LinkedHashMap<>();
            patternTimes.forEach((pattern, amount) -> {
                if (amount.signum() > 0) {
                    this.remaining.put(pattern, amount);
                }
            });
        }

        /** Creates a cursor whose remove operation also removes AE2's facade task. */
        public synchronized CraftingCursor cursor(Consumer<IPatternDetails> removeNativeTask) {
            return new CraftingCursor(this, removeNativeTask);
        }

        public synchronized boolean isEmpty() {
            return remaining.isEmpty();
        }

        private synchronized BigInteger get(IPatternDetails pattern) {
            return remaining.getOrDefault(pattern, BigInteger.ZERO);
        }

        private synchronized void set(IPatternDetails pattern, BigInteger amount) {
            if (amount.signum() <= 0) {
                remaining.remove(pattern);
            } else {
                remaining.put(pattern, amount);
            }
        }

        private synchronized void remove(IPatternDetails pattern) {
            remaining.remove(pattern);
        }

        private ExecutingCraftingJob owner() {
            return owner;
        }

        private synchronized ArrayList<IPatternDetails> snapshotKeys() {
            return new ArrayList<>(remaining.keySet());
        }
    }

    /** Cursor used by QuantumBulkCrafting without exposing the registry map. */
    public static final class CraftingCursor {
        private final Job job;
        private final Consumer<IPatternDetails> removeNativeTask;
        private IPatternDetails current;
        private Iterator<IPatternDetails> iterator;

        private CraftingCursor(Job job, Consumer<IPatternDetails> removeNativeTask) {
            this.job = job;
            this.removeNativeTask = removeNativeTask;
        }

        public boolean next() {
            if (iterator == null) {
                iterator = job.snapshotKeys().iterator();
            }
            while (iterator.hasNext()) {
                IPatternDetails candidate = iterator.next();
                if (job.get(candidate).signum() > 0) {
                    current = candidate;
                    return true;
                }
            }
            current = null;
            return false;
        }

        public IPatternDetails details() {
            return current;
        }

        public BigInteger remaining() {
            return job.get(current);
        }

        public void setRemaining(BigInteger amount) {
            job.set(current, amount);
        }

        public void remove() {
            IPatternDetails removed = current;
            job.remove(removed);
            removeNativeTask.accept(removed);
            // 最後のExact taskを消したら、以後はAE2の空Jobとして終了できるよう台帳を外す。
            if (job.isEmpty()) {
                AcoBigIntegerJobRegistry.remove(job.owner());
            }
        }
    }
}
