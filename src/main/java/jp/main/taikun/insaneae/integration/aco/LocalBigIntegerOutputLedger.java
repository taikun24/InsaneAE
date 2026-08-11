package jp.main.taikun.insaneae.integration.aco;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** ACOが無い場合にも使える、同じ会計規則の最小実装。 */
final class LocalBigIntegerOutputLedger implements PendingOutputLedger {
    private static final int SCHEMA_VERSION = 1;
    /** ACOの既定上限と同じ値。巨大な壊れたNBTを無制限に展開しないための固定値。 */
    private static final int MAXIMUM_BITS = 1_048_576;
    /** 保存データのエントリ数上限。NBT破損時のメモリ使用量を制限する。 */
    private static final int MAX_ENTRIES = 1_048_576;
    private final Map<AEKey, BigInteger> amounts = new LinkedHashMap<>();

    @Override
    public synchronized void add(AEKey key, BigInteger amount) {
        Objects.requireNonNull(key, "key");
        requirePositive(amount, "amount");
        BigInteger current = amounts.getOrDefault(key, BigInteger.ZERO);
        BigInteger next = current.add(amount);
        checkBits(next, "ledger amount");
        if (current.signum() == 0 && amounts.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("Quantum CPU pending output entry limit exceeded");
        }
        amounts.put(key, next);
    }

    @Override
    public synchronized long drain(AEKey key, long maximum) {
        if (maximum <= 0L) {
            throw new IllegalArgumentException("maximum must be positive");
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
    public synchronized CompoundTag save() {
        CompoundTag saved = new CompoundTag();
        saved.putInt("schemaVersion", SCHEMA_VERSION);
        ListTag entries = new ListTag();
        for (Map.Entry<AEKey, BigInteger> entry : amounts.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.put("key", entry.getKey().toTagGeneric());
            value.putByteArray("amount", entry.getValue().toByteArray());
            entries.add(value);
        }
        saved.put("entries", entries);
        return saved;
    }

    @Override
    public synchronized void load(CompoundTag saved) {
        Objects.requireNonNull(saved, "saved");
        int schema = saved.getInt("schemaVersion");
        if (schema != 0 && schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported Quantum CPU pending output schema: " + schema);
        }
        ListTag entries = saved.getList("entries", Tag.TAG_COMPOUND);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Quantum CPU pending output entry limit exceeded");
        }
        Map<AEKey, BigInteger> loaded = new LinkedHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag value = entries.getCompound(index);
            AEKey key = AEKey.fromTagGeneric(value.getCompound("key"));
            if (key == null) {
                throw new IllegalArgumentException("saved Quantum CPU pending output key is invalid");
            }
            BigInteger amount = new BigInteger(value.getByteArray("amount"));
            requirePositive(amount, "saved amount");
            checkBits(amount, "saved amount");
            if (loaded.put(key, amount) != null) {
                throw new IllegalArgumentException("saved Quantum CPU pending output has duplicate keys");
            }
        }
        amounts.clear();
        amounts.putAll(loaded);
    }

    @Override
    public synchronized void clear() {
        amounts.clear();
    }

    private static void requirePositive(BigInteger value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void checkBits(BigInteger value, String context) {
        if (value.signum() < 0 || value.bitLength() > MAXIMUM_BITS) {
            throw new ArithmeticException(context + " exceeds the configured BigInteger bit limit");
        }
    }
}
