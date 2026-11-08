package jp.main.taikun.insaneae.quantum.batch;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

/**
 * ACOのcraftingtable batchで受け取った取引のレシートの記録。
 *
 * <p>ACOの手引き ({@code docs/BATCH_API.md}) が第三者連携に要求しているのは
 * 「自前の永続的なターゲットレシートと、コピーワールドでのkill/restart試験」。
 * 挿入シミュレーションが通ることはdurable acceptanceの証明にならない、と明記されている。
 * そこでレシートはここに持ち、Quantum CPUのNBTへ一緒に保存する。</p>
 *
 * <p>このクラスはACOの型に触れない。ACOが未導入の環境でも
 * {@code QuantumCpuBlockEntity} が普通にロードできる必要があるため、
 * プロトコルの実装、つまりACO型を使う側は別クラスに置いてある。</p>
 */
public final class QuantumBatchReceipts {

    /** 1台のCPUが同時に抱えられる取引の数。溢れたら受け付けない。上書きしない。 */
    public static final int MAX_RECEIPTS = 256;

    private static final String NBT_LIST = "acoBatchReceipts";
    private static final String NBT_ID = "id";
    private static final String NBT_DIGEST = "digest";
    private static final String NBT_STATE = "state";
    private static final String NBT_OUTPUTS = "outputs";
    private static final String NBT_KEY = "key";
    private static final String NBT_AMOUNT = "amount";

    /** レシートの状態。ACOの {@code CraftingTableBatchSnapshot.State} と1:1で対応させる。 */
    public enum State {
        RUNNING,
        OUTPUT_READY,
        ACKNOWLEDGED,
        CANCELLED
    }

    /**
     * 1取引ぶん。
     *
     * @param digest       ACOが付けたpayload digest。照合に使う。
     *                     同じUUIDでもdigestが違えば別物として拒否する。
     * @param exactOutputs完成品の正確な量 (BigInteger)。longへ丸めない。
     */
    public record Receipt(String digest, State state, Map<AEKey, BigInteger> exactOutputs) {
        public Receipt {
            exactOutputs = Map.copyOf(exactOutputs);
        }

        public Receipt withState(State next) {
            return new Receipt(digest, next, exactOutputs);
        }
    }

    private final Map<UUID, Receipt> receipts = new LinkedHashMap<>();

    public boolean isFull() {
        return receipts.size() >= MAX_RECEIPTS;
    }

    /**
     * 新しい取引を記録する。
     *
     * @return記録できたか。同じUUIDが既にあればfalseで、上書きしない。
     *         ACO側は重複取引を「拒否されるべきもの」として扱う。
     */
    public boolean put(UUID transactionId, Receipt receipt) {
        if (transactionId == null || receipt == null || isFull()) {
            return false;
        }
        return receipts.putIfAbsent(transactionId, receipt) == null;
    }

    /** digestまで一致したものだけ返す。食い違えばnull。別の取引として扱う。 */
    @Nullable
    public Receipt get(UUID transactionId, String digest) {
        Receipt receipt = receipts.get(transactionId);
        if (receipt == null || !receipt.digest().equals(digest)) {
            return null;
        }
        return receipt;
    }

    /** 状態を進める。digestが一致しなければ何もしない。 */
    public boolean advance(UUID transactionId, String digest, State next) {
        Receipt receipt = get(transactionId, digest);
        if (receipt == null) {
            return false;
        }
        receipts.put(transactionId, receipt.withState(next));
        return true;
    }

    /** 記録から消す。ACOがcredit済みでforgetを呼んだとき。 */
    public boolean forget(UUID transactionId, String digest) {
        if (get(transactionId, digest) == null) {
            return false;
        }
        receipts.remove(transactionId);
        return true;
    }

    public boolean isEmpty() {
        return receipts.isEmpty();
    }

    // ------------------------------------------------------------------ 保存

    //? if >=1.21 {
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
    //?} else {
    /*public void save(CompoundTag tag) {
    *///?}
        if (receipts.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        receipts.forEach((id, receipt) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(NBT_ID, id);
            entry.putString(NBT_DIGEST, receipt.digest());
            entry.putString(NBT_STATE, receipt.state().name());
            ListTag outputs = new ListTag();
            receipt.exactOutputs().forEach((key, amount) -> {
                CompoundTag output = new CompoundTag();
                //? if >=1.21 {
                output.put(NBT_KEY, key.toTagGeneric(registries));
                //?} else {
                /*output.put(NBT_KEY, key.toTagGeneric());
                *///?}
                // BigIntegerは10進文字列ではなくバイト列で持つ。桁数が大きいと文字列は無駄に長い
                output.putByteArray(NBT_AMOUNT, amount.toByteArray());
                outputs.add(output);
            });
            entry.put(NBT_OUTPUTS, outputs);
            list.add(entry);
        });
        tag.put(NBT_LIST, list);
    }

    //? if >=1.21 {
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
    //?} else {
    /*public void load(CompoundTag tag) {
    *///?}
        receipts.clear();
        if (!tag.contains(NBT_LIST, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(NBT_LIST, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size() && receipts.size() < MAX_RECEIPTS; index++) {
            CompoundTag entry = list.getCompound(index);
            State state;
            try {
                state = State.valueOf(entry.getString(NBT_STATE));
            } catch (IllegalArgumentException unknown) {
                // 知らない状態は捨てる。壊れたレシートをcreditさせるより落とすほうが安全
                continue;
            }
            Map<AEKey, BigInteger> outputs = new LinkedHashMap<>();
            ListTag saved = entry.getList(NBT_OUTPUTS, Tag.TAG_COMPOUND);
            for (int slot = 0; slot < saved.size(); slot++) {
                CompoundTag output = saved.getCompound(slot);
                //? if >=1.21 {
                AEKey key = AEKey.fromTagGeneric(registries, output.getCompound(NBT_KEY));
                //?} else {
                /*AEKey key = AEKey.fromTagGeneric(output.getCompound(NBT_KEY));
                *///?}
                if (key == null) {
                    continue;
                }
                byte[] amount = output.getByteArray(NBT_AMOUNT);
                if (amount.length == 0) {
                    continue;
                }
                BigInteger value = new BigInteger(amount);
                if (value.signum() > 0) {
                    outputs.put(key, value);
                }
            }
            receipts.put(entry.getUUID(NBT_ID), new Receipt(entry.getString(NBT_DIGEST), state, outputs));
        }
    }
}
