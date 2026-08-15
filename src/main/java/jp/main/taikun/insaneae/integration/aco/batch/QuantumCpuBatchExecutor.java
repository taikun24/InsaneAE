package jp.main.taikun.insaneae.integration.aco.batch;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import com.syaru.ae2craftingoptimizer.api.contract.IntegrationCapabilities;
import com.syaru.ae2craftingoptimizer.api.contract.IntegrationCapabilitiesRegistry;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchMode;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchRequest;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.quantum.QuantumCpuLogic;
import jp.main.taikun.insaneae.quantum.batch.QuantumBatchReceipts;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * ACO の craftingtable batch を Quantum CPU で実行する本体。
 *
 * <h2>なぜこの形なのか</h2>
 * <p>AE2 は在庫も完成待ちも全部 long ({@code KeyCounter} / {@code MEStorage.insert} /
 * {@code ListCraftingInventory})。だから「BigInteger 個の完成品」は
 * <b>そもそも AE2 のネットワークに存在できない</b>。ACO の BigInteger は
 * 中間パターンの<b>実行回数</b>であって在庫量ではない。</p>
 *
 * <p>そこで ACO の公開境界では、正確な会計は ACO が持ち、実行側は
 * <b>本物の組み立てを 1 回だけ回して係数を掛ける</b>という分担になっている。
 * ここは AE2 の long 会計を一切通らないので、完成待ちの桁あふれが起きようがない。
 * 要求回数はスレッド数もループ回数も増やさない。</p>
 *
 * <h2>約束事</h2>
 * <ul>
 *   <li><b>fail closed</b> — 必要な API バージョンが無ければ受けない
 *       ({@code docs/EXACT_COUNT_API.md} の指示)。受けなければ ACO は別のターゲットを探す。</li>
 *   <li><b>BigInteger に {@code longValue()} を呼ばない。</b>係数はそのまま掛ける。</li>
 *   <li>材料の出納は ACO の escrow が持つ。ここでは<b>在庫にも完成品台帳にも触らない</b>。</li>
 *   <li>レシートは {@link QuantumBatchReceipts} に持ち、CPU の NBT へ永続化する。</li>
 * </ul>
 */
public final class QuantumCpuBatchExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** これ未満の craftingtable API では受けない。 */
    private static final int REQUIRED_CRAFTING_TABLE_API = 1;

    /** 一度 WARN で出した拒否理由。同じものを繰り返さないため。 */
    private static final java.util.Set<String> LOGGED_REFUSALS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 最初の 1 件を受理したときだけ INFO を出す。連携が生きている証拠になる。 */
    private static final java.util.concurrent.atomic.AtomicBoolean FIRST_ACCEPT =
            new java.util.concurrent.atomic.AtomicBoolean();

    private QuantumCpuBatchExecutor() {
    }

    /** ACO 側の API バージョンが足りているか。足りなければ<b>一切受けない</b>。 */
    public static boolean isSupported() {
        Optional<IntegrationCapabilities> capabilities = IntegrationCapabilitiesRegistry.peek();
        return capabilities
                .filter(caps -> caps.craftingTableBatchApiVersion() >= REQUIRED_CRAFTING_TABLE_API)
                .isPresent();
    }

    public static boolean accept(QuantumCpuBlockEntity host, CraftingTableBatchRequest request) {
        // 断る理由は<b>必ず残す</b>。ここで黙って false を返すと、ACO は別のターゲットを探し、
        // 見つからなければ long 窓の経路へ落ちる。使う側からは「速くならない」としか見えず、
        // 拒否したのか呼ばれてすらいないのかが区別できなくなる。
        if (!isSupported()) {
            return refuse(host, "ACO の craftingtable API バージョンが足りない");
        }
        if (host.getLevel() == null || host.getLevel().isClientSide()) {
            return false;
        }
        QuantumBatchReceipts receipts = host.getBatchReceipts();
        if (receipts.isFull()) {
            return refuse(host, "レシート台帳が満杯 (未清算の取引が溜まっている)");
        }
        if (receipts.get(request.transactionId(), request.payloadDigest()) != null) {
            // 重複取引は上書きせず拒否する (ACO は拒否を期待している)。
            return false;
        }

        QuantumCpuLogic.OneCraft craft =
                host.getQuantumLogic().assembleOnce(request.pattern(), request.inputsPerExecution());
        if (craft == null) {
            return refuse(host, "1 回ぶんの組み立てができなかった "
                    + "(Molecular Assembler 用のパターンでない / 材料が揃っていない): "
                    + request.pattern().getPrimaryOutput());
        }
        // 実際に出たものが Pattern の宣言と食い違っていたら受けない。
        // ここを飛ばすと「1 回の結果に係数を掛ける」の前提が崩れる。
        if (!matchesDeclaration(craft, request.outputsPerExecution(), request.remainingPerExecution())) {
            return refuse(host, "実際の組み立て結果が Pattern の宣言と違う: 実際=" + perCraft(craft)
                    + " 宣言=" + declaredTotals(request));
        }
        Map<AEKey, BigInteger> scaled = scale(craft, request.executions());
        if (!scaled.equals(request.aggregateExpectedOutputs())) {
            return refuse(host, "1 回ぶん × 実行回数 が ACO の集計と一致しない: 回数="
                    + request.executions() + " こちら=" + scaled
                    + " ACO=" + request.aggregateExpectedOutputs());
        }

        // Quantum CPU は物理的な進行を持たない (加速カードで即時)。
        // その場で OUTPUT_READY にして、ACO が credit しに来るのを待つ。
        boolean stored = receipts.put(request.transactionId(), new QuantumBatchReceipts.Receipt(
                request.payloadDigest(), QuantumBatchReceipts.State.OUTPUT_READY, scaled));
        if (stored) {
            host.saveChanges();
            if (FIRST_ACCEPT.compareAndSet(false, true)) {
                // 「呼ばれてすらいない」と「断っている」を切り分けるための 1 行。
                LOGGER.info("InsaneAE: Quantum CPU at {} accepted its first ACO batch "
                        + "({} mode, {} executions in one assemble)",
                        host.getBlockPos(), request.mode(), request.executions());
            }
        }
        return stored;
    }

    public static boolean owns(QuantumCpuBlockEntity host, UUID transactionId, String digest) {
        return host.getBatchReceipts().get(transactionId, digest) != null;
    }

    public static Optional<CraftingTableBatchSnapshot> snapshot(QuantumCpuBlockEntity host,
            UUID transactionId, String digest) {
        QuantumBatchReceipts.Receipt receipt = host.getBatchReceipts().get(transactionId, digest);
        if (receipt == null) {
            return Optional.empty();
        }
        // 進行は持たないので 1/1 固定。ACO は state と exactOutputs だけを見る。
        return Optional.of(new CraftingTableBatchSnapshot(transactionId, digest,
                toAcoState(receipt.state()), 1, 1, receipt.exactOutputs(), ""));
    }

    public static boolean acknowledge(QuantumCpuBlockEntity host, UUID transactionId, String digest) {
        boolean advanced = host.getBatchReceipts()
                .advance(transactionId, digest, QuantumBatchReceipts.State.ACKNOWLEDGED);
        if (advanced) {
            host.saveChanges();
        }
        return advanced;
    }

    public static boolean forget(QuantumCpuBlockEntity host, UUID transactionId, String digest) {
        boolean forgotten = host.getBatchReceipts().forget(transactionId, digest);
        if (forgotten) {
            host.saveChanges();
        }
        return forgotten;
    }

    public static boolean cancel(QuantumCpuBlockEntity host, UUID transactionId, String digest) {
        boolean cancelled = host.getBatchReceipts()
                .advance(transactionId, digest, QuantumBatchReceipts.State.CANCELLED);
        if (cancelled) {
            host.saveChanges();
        }
        return cancelled;
    }

    /**
     * 断った理由をログに残して {@code false} を返す。
     *
     * <p>同じ理由は<b>最初の 1 回だけ WARN</b>。ACO は {@code bigIntegerRetryBackoffTicks}
     * ごとに何度でも聞きに来るので、そのまま出すとログが埋まる。</p>
     */
    private static boolean refuse(QuantumCpuBlockEntity host, String reason) {
        if (LOGGED_REFUSALS.add(reason)) {
            LOGGER.warn("InsaneAE: Quantum CPU at {} refused an ACO batch: {}", host.getBlockPos(), reason);
        } else {
            LOGGER.debug("InsaneAE: Quantum CPU at {} refused an ACO batch: {}", host.getBlockPos(), reason);
        }
        return false;
    }

    private static Map<AEKey, BigInteger> perCraft(QuantumCpuLogic.OneCraft craft) {
        Map<AEKey, BigInteger> totals = new LinkedHashMap<>();
        addStack(totals, craft.output());
        for (ItemStack remainder : craft.remainders()) {
            addStack(totals, remainder);
        }
        return totals;
    }

    private static Map<AEKey, BigInteger> declaredTotals(CraftingTableBatchRequest request) {
        Map<AEKey, BigInteger> declared = new LinkedHashMap<>();
        addDeclared(declared, request.outputsPerExecution());
        addDeclared(declared, request.remainingPerExecution());
        return declared;
    }

    /** 実際の組み立て結果が Pattern の宣言どおりか。 */
    private static boolean matchesDeclaration(QuantumCpuLogic.OneCraft craft,
            List<GenericStack> declaredOutputs, List<GenericStack> declaredRemainders) {
        Map<AEKey, BigInteger> actual = new LinkedHashMap<>();
        addStack(actual, craft.output());
        for (ItemStack remainder : craft.remainders()) {
            addStack(actual, remainder);
        }
        Map<AEKey, BigInteger> declared = new LinkedHashMap<>();
        addDeclared(declared, declaredOutputs);
        addDeclared(declared, declaredRemainders);
        return actual.equals(declared);
    }

    /** 1 回ぶんの結果に係数を掛ける。<b>ここが BigInteger のまま残る唯一の掛け算。</b> */
    private static Map<AEKey, BigInteger> scale(QuantumCpuLogic.OneCraft craft, BigInteger executions) {
        Map<AEKey, BigInteger> perCraft = new LinkedHashMap<>();
        addStack(perCraft, craft.output());
        for (ItemStack remainder : craft.remainders()) {
            addStack(perCraft, remainder);
        }
        Map<AEKey, BigInteger> scaled = new LinkedHashMap<>();
        perCraft.forEach((key, amount) -> scaled.put(key, amount.multiply(executions)));
        return scaled;
    }

    private static void addStack(Map<AEKey, BigInteger> into, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        into.merge(AEItemKey.of(stack), BigInteger.valueOf(stack.getCount()), BigInteger::add);
    }

    private static void addDeclared(Map<AEKey, BigInteger> into, List<GenericStack> stacks) {
        if (stacks == null) {
            return;
        }
        for (GenericStack stack : stacks) {
            if (stack != null && stack.amount() > 0) {
                into.merge(stack.what(), BigInteger.valueOf(stack.amount()), BigInteger::add);
            }
        }
    }

    private static CraftingTableBatchSnapshot.State toAcoState(QuantumBatchReceipts.State state) {
        return switch (state) {
            case RUNNING -> CraftingTableBatchSnapshot.State.RUNNING;
            case OUTPUT_READY -> CraftingTableBatchSnapshot.State.OUTPUT_READY;
            case ACKNOWLEDGED -> CraftingTableBatchSnapshot.State.ACKNOWLEDGED;
            case CANCELLED -> CraftingTableBatchSnapshot.State.CANCELLED;
        };
    }

    /** ACO が BigInteger 実行として渡してきたか (診断・テスト用)。 */
    public static boolean isBigIntegerJob(CraftingTableBatchRequest request) {
        return request.mode() == CraftingTableBatchMode.BIG_INTEGER_JOB;
    }
}
