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
        if (!isSupported() || host.getLevel() == null || host.getLevel().isClientSide()) {
            return false;
        }
        QuantumBatchReceipts receipts = host.getBatchReceipts();
        if (receipts.isFull() || receipts.get(request.transactionId(), request.payloadDigest()) != null) {
            // 重複取引は上書きせず拒否する (ACO は拒否を期待している)。
            return false;
        }

        QuantumCpuLogic.OneCraft craft =
                host.getQuantumLogic().assembleOnce(request.pattern(), request.inputsPerExecution());
        if (craft == null) {
            return false;
        }
        // 実際に出たものが Pattern の宣言と食い違っていたら受けない。
        // ここを飛ばすと「1 回の結果に係数を掛ける」の前提が崩れる。
        if (!matchesDeclaration(craft, request.outputsPerExecution(), request.remainingPerExecution())) {
            LOGGER.warn("InsaneAE: Quantum CPU refused an ACO batch at {}; the real assemble did not "
                    + "match the encoded pattern declaration.", host.getBlockPos());
            return false;
        }
        Map<AEKey, BigInteger> scaled = scale(craft, request.executions());
        if (!scaled.equals(request.aggregateExpectedOutputs())) {
            LOGGER.warn("InsaneAE: Quantum CPU refused an ACO batch at {}; one-craft x executions did "
                    + "not equal ACO's aggregate expected outputs.", host.getBlockPos());
            return false;
        }

        // Quantum CPU は物理的な進行を持たない (加速カードで即時)。
        // その場で OUTPUT_READY にして、ACO が credit しに来るのを待つ。
        boolean stored = receipts.put(request.transactionId(), new QuantumBatchReceipts.Receipt(
                request.payloadDigest(), QuantumBatchReceipts.State.OUTPUT_READY, scaled));
        if (stored) {
            host.saveChanges();
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
