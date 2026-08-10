package jp.main.taikun.insaneae.integration.aco;

import appeng.api.stacks.AEKey;
import com.mojang.logging.LogUtils;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

/**
 * 完成品待ち台帳の保存形式。<b>どの台帳実装が有効かに関係なく常にこの形式</b>で読み書きする。
 * ACO 有効時に ACO 側の形式で保存してしまうと、後から ACO を抜いたときに読めなくなるため。
 *
 * <p>形式: {@code {schemaVersion: 1, entries: [{key: <AEKey generic tag>, amount: byte[]}]}}。
 * amount は {@link BigInteger#toByteArray()} (符号付きビッグエンディアン)。</p>
 *
 * <p><b>読み込みは何があっても例外を投げない。</b>ここはブロック読み込み経路なので、
 * 投げるとチャンク読み込みが壊れる。解決できないキー (そのアイテムの Mod が抜かれた場合など)・
 * 壊れた量・異常に巨大な NBT は、警告ログを出してそのエントリだけ捨てる。</p>
 */
public final class PendingOutputNbt {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SCHEMA_VERSION = 1;
    private static final String NBT_SCHEMA = "schemaVersion";
    private static final String NBT_ENTRIES = "entries";
    private static final String NBT_KEY = "key";
    private static final String NBT_AMOUNT = "amount";

    /** 1 エントリの量の上限ビット数。壊れた NBT を無制限に展開しないための歯止め。 */
    private static final int MAXIMUM_BITS = 1_048_576;
    /** エントリ数の上限。同上。 */
    private static final int MAX_ENTRIES = 1_048_576;

    private PendingOutputNbt() {
    }

    public static CompoundTag save(PendingOutputLedger ledger) {
        CompoundTag saved = new CompoundTag();
        saved.putInt(NBT_SCHEMA, SCHEMA_VERSION);
        ListTag entries = new ListTag();
        for (Map.Entry<AEKey, BigInteger> entry : ledger.snapshot().entrySet()) {
            if (entry.getValue().signum() <= 0) {
                continue;
            }
            CompoundTag value = new CompoundTag();
            value.put(NBT_KEY, entry.getKey().toTagGeneric());
            value.putByteArray(NBT_AMOUNT, entry.getValue().toByteArray());
            entries.add(value);
        }
        saved.put(NBT_ENTRIES, entries);
        return saved;
    }

    /** {@code saved} の中身を {@code ledger} へ流し込む (追記)。読めないエントリは警告して飛ばす。 */
    public static void load(PendingOutputLedger ledger, CompoundTag saved) {
        if (saved == null) {
            return;
        }
        int schema = saved.getInt(NBT_SCHEMA);
        if (schema != 0 && schema != SCHEMA_VERSION) {
            // 将来の形式。読める保証が無いので全部落とすが、ワールドは壊さない。
            LOGGER.warn("InsaneAE: unknown Quantum CPU pending output schema {}; dropping pending outputs",
                    schema);
            return;
        }
        ListTag entries = saved.getList(NBT_ENTRIES, Tag.TAG_COMPOUND);
        int limit = Math.min(entries.size(), MAX_ENTRIES);
        if (entries.size() > MAX_ENTRIES) {
            LOGGER.warn("InsaneAE: Quantum CPU pending outputs have {} entries; loading only the first {}",
                    entries.size(), limit);
        }
        for (int index = 0; index < limit; index++) {
            CompoundTag value = entries.getCompound(index);
            AEKey key = AEKey.fromTagGeneric(value.getCompound(NBT_KEY));
            if (key == null) {
                // そのアイテムの Mod が抜かれた場合にここへ来る。1 エントリ捨てるだけで済ませる。
                LOGGER.warn("InsaneAE: dropping a Quantum CPU pending output whose key cannot be resolved: {}",
                        value.getCompound(NBT_KEY));
                continue;
            }
            byte[] raw = value.getByteArray(NBT_AMOUNT);
            if (raw.length == 0 || raw.length > MAXIMUM_BITS / 8) {
                LOGGER.warn("InsaneAE: dropping a Quantum CPU pending output with a corrupt amount for {}", key);
                continue;
            }
            BigInteger amount = new BigInteger(raw);
            if (amount.signum() <= 0) {
                LOGGER.warn("InsaneAE: dropping a non-positive Quantum CPU pending output for {}", key);
                continue;
            }
            ledger.add(key, amount);
        }
    }
}
