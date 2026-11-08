package jp.main.taikun.insaneae.cell;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
//? if >=1.21 {
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import jp.main.taikun.insaneae.registries.ModDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
*///?}

/**
 * 超次元MEセルの中身を、アイテムの側に保存する。
 *
 * <p>量は BigInteger の文字列で書く。long に収まる量でも同じ形にするので、
 * 読み書きの経路は1つで済む。</p>
 *
 * <p>1.21 はデータコンポーネント ({@code ModDataComponents.INSANE_CELL_CONTENTS})、
 * 1.20.1 は NBT に書く。AE2 のキーの読み書きは版ごとに形が違う
 * (1.21 は {@code CODEC} と {@code STREAM_CODEC}、1.20.1 は {@code toTagGeneric()}) ので、
 * ここで分けている。</p>
 */
public final class InsaneMeCellData {

    /** 1.20.1 の NBT の鍵。 */
    private static final String KEY = "insaneae_cell_contents";

    private InsaneMeCellData() {
    }

    /** 1 種類ぶんの中身。 */
    public record Entry(AEKey key, BigInteger amount) {
    }

    /** アイテムから中身を読む。壊れた項目は飛ばす。 */
    public static Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> read(ItemStack stack) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> out = new Object2ObjectLinkedOpenHashMap<>();
        //? if >=1.21 {
        List<Entry> entries = stack.get(ModDataComponents.INSANE_CELL_CONTENTS.get());
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry.amount().signum() > 0) {
                    out.put(entry.key(), entry.amount());
                }
            }
        }
        //?} else {
        /*var tag = stack.getTag();
        if (tag != null && tag.contains(KEY, net.minecraft.nbt.Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int index = 0; index < list.size(); index++) {
                CompoundTag item = list.getCompound(index);
                AEKey key = AEKey.fromTagGeneric(item.getCompound("key"));
                if (key == null) {
                    continue;
                }
                try {
                    BigInteger amount = new BigInteger(item.getString("amount"));
                    if (amount.signum() > 0) {
                        out.put(key, amount);
                    }
                } catch (NumberFormatException ignored) {
                    // 壊れた量は無かったものとして扱う
                }
            }
        }
        *///?}
        return out;
    }

    /** 中身をアイテムへ書く。空になったら保存の鍵ごと消す。 */
    public static void write(ItemStack stack, Map<AEKey, BigInteger> amounts) {
        //? if >=1.21 {
        if (amounts.isEmpty()) {
            stack.remove(ModDataComponents.INSANE_CELL_CONTENTS.get());
            return;
        }
        var entries = new java.util.ArrayList<Entry>(amounts.size());
        for (Map.Entry<AEKey, BigInteger> entry : amounts.entrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getValue()));
        }
        stack.set(ModDataComponents.INSANE_CELL_CONTENTS.get(), entries);
        //?} else {
        /*if (amounts.isEmpty()) {
            if (stack.getTag() != null) {
                stack.getTag().remove(KEY);
            }
            return;
        }
        ListTag list = new ListTag();
        for (Map.Entry<AEKey, BigInteger> entry : amounts.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.put("key", entry.getKey().toTagGeneric());
            item.putString("amount", entry.getValue().toString());
            list.add(item);
        }
        stack.getOrCreateTag().put(KEY, list);
        *///?}
    }

    //? if >=1.21 {
    /** 1.21 のデータコンポーネントの保存形式。 */
    public static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AEKey.CODEC.fieldOf("key").forGetter(Entry::key),
            Codec.STRING.xmap(BigInteger::new, BigInteger::toString).fieldOf("amount").forGetter(Entry::amount)
    ).apply(instance, Entry::new));

    public static final Codec<List<Entry>> CODEC = ENTRY_CODEC.listOf();

    /** 同期用。クライアントのツールチップやスロットの一致のため、サーバーの中身と揃えて送る。 */
    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_STREAM_CODEC = StreamCodec.composite(
            AEKey.STREAM_CODEC, Entry::key,
            ByteBufCodecs.STRING_UTF8.map(BigInteger::new, BigInteger::toString), Entry::amount,
            Entry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, List<Entry>> STREAM_CODEC =
            ByteBufCodecs.collection(java.util.ArrayList::new, ENTRY_STREAM_CODEC);
    //?}
}
