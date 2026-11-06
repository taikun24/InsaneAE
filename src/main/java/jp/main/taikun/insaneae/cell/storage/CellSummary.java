package jp.main.taikun.insaneae.cell.storage;

import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jp.main.taikun.insaneae.util.SaturatingMath;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * UUID で管理しているセルの、アイテム側に残す要約。
 *
 * <p>{@code preview} は量の多い順の先頭だけ。AE2 のツールチップは中身を
 * {@code tooltipMaxCellContentShown} (既定 5) 件まで描き、それより多いと「…」を付けるので、
 * その判定が付くよう既定値より少し多めに持たせている。</p>
 */
public record CellSummary(int types, long count, List<GenericStack> preview) {

    public static final int PREVIEW_SIZE = 16;

    public static final Codec<CellSummary> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.INT.fieldOf("types").forGetter(CellSummary::types),
            Codec.LONG.fieldOf("count").forGetter(CellSummary::count),
            GenericStack.FAULT_TOLERANT_LIST_CODEC.optionalFieldOf("preview", List.of())
                    .forGetter(CellSummary::preview))
            .apply(builder, CellSummary::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CellSummary> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CellSummary::types,
            ByteBufCodecs.VAR_LONG, CellSummary::count,
            GenericStack.STREAM_CODEC.apply(ByteBufCodecs.list()), CellSummary::preview,
            CellSummary::new);

    public CellSummary {
        preview = List.copyOf(preview);
    }

    public static CellSummary of(List<GenericStack> stacks) {
        long count = 0;
        // 先頭 PREVIEW_SIZE 件だけを残す。全体を並べ替えると数億種のセルで重い。
        PriorityQueue<GenericStack> top = new PriorityQueue<>(PREVIEW_SIZE + 1,
                Comparator.comparingLong(GenericStack::amount));
        for (GenericStack stack : stacks) {
            count = SaturatingMath.add(count, stack.amount());
            top.add(stack);
            if (top.size() > PREVIEW_SIZE) {
                top.poll();
            }
        }
        List<GenericStack> preview = new ArrayList<>(top);
        preview.sort(Comparator.comparingLong(GenericStack::amount).reversed());
        return new CellSummary(stacks.size(), count, preview);
    }
}
