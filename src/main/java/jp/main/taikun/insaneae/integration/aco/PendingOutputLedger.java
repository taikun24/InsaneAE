package jp.main.taikun.insaneae.integration.aco;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/** Quantum CPUの完成品待ちをlongへ縮めずに扱う内部境界。 */
public interface PendingOutputLedger {
    void add(AEKey key, BigInteger amount);

    long drain(AEKey key, long maximum);

    Map<AEKey, BigInteger> snapshot();

    boolean isEmpty();

    CompoundTag save();

    void load(CompoundTag saved);

    void clear();
}
