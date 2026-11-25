package jp.main.taikun.insaneae.cell.storage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import jp.main.taikun.insaneae.cell.InsaneMeCellData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
//? if >=1.21 {
import jp.main.taikun.insaneae.registries.ModDataComponents;
//?}

/**
 * {@code /insaneae cells list [page]} と {@code /insaneae cells recover <uuid>}。
 *
 * <p>中身はワールド側にあるので、セルのアイテムを溶岩に落とすなどして失っても保存データは残る。
 * 一覧でUUIDを探し、同じUUIDのセルを作り直して取り戻すためのもの。AE2 Thingsの
 * {@code /ae2things recover}と同じ考え方。</p>
 */
public final class CellCommands {

    private static final int PAGE_SIZE = 10;

    private CellCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("insaneae")
                .then(Commands.literal("cells")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(context -> list(context, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> list(context,
                                                IntegerArgumentType.getInteger(context, "page")))))
                        .then(Commands.literal("recover")
                                .then(Commands.argument("uuid", UuidArgument.uuid())
                                        .executes(CellCommands::recover)))));
    }

    private static int list(CommandContext<CommandSourceStack> context, int page) {
        CommandSourceStack source = context.getSource();
        CellContentStore store = CellContentStore.get();
        if (store == null) {
            source.sendFailure(Component.translatable("commands.insaneae.cells.unavailable"));
            return 0;
        }
        List<Row> entries = rows(store);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(page, pages);
        source.sendSuccess(() -> Component.translatable("commands.insaneae.cells.list.header",
                entries.size(), current, pages), false);
        for (Row row : entries.subList((current - 1) * PAGE_SIZE,
                Math.min(entries.size(), current * PAGE_SIZE))) {
            String id = row.id().toString();
            Component line = Component.literal(id)
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                    "/insaneae cells recover " + id))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("commands.insaneae.cells.list.click"))))
                    .append(Component.literal(" " + row.item()).withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("commands.insaneae.cells.list.contents",
                            row.types(), row.count()).withStyle(ChatFormatting.WHITE));
            source.sendSuccess(() -> line, false);
        }
        return entries.size();
    }

    /** 一覧の1行。超次元MEセルの量はlongに収まらないので、数は文字列で持つ。 */
    private record Row(UUID id, ResourceLocation item, int types, String count) {
    }

    private static List<Row> rows(CellContentStore store) {
        List<Row> rows = new ArrayList<>();
        store.entries().forEach((id, cell) -> {
            //? if >=1.21 {
            rows.add(new Row(id, cell.item(), cell.stacks().size(),
                    Long.toString(CellSummary.of(cell.stacks()).count())));
            //?} else {
            /*rows.add(new Row(id, cell.item(), cell.types(), Long.toString(cell.count())));
            *///?}
        });
        store.bigEntries().forEach((id, cell) -> rows.add(new Row(id, cell.item(), cell.amounts().size(),
                cell.amounts().values().stream().reduce(BigInteger.ZERO, BigInteger::add).toString())));
        rows.sort(Comparator.comparing(row -> row.id().toString()));
        return rows;
    }

    private static int recover(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        UUID id = UuidArgument.getUuid(context, "uuid");
        CellContentStore store = CellContentStore.get();
        CellContentStore.Entry entry = store != null ? store.get(id) : null;
        if (entry == null && store != null && store.getBig(id) != null) {
            return recoverBig(source, player, id, store.getBig(id));
        }
        if (entry == null) {
            source.sendFailure(Component.translatable("commands.insaneae.cells.not_found", id.toString()));
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.get(entry.item());
        if (item == Items.AIR) {
            source.sendFailure(Component.translatable("commands.insaneae.cells.unknown_item",
                    entry.item().toString()));
            return 0;
        }
        ItemStack stack = new ItemStack(item);
        //? if >=1.21 {
        stack.set(ModDataComponents.CELL_ID.get(), id);
        stack.set(ModDataComponents.CELL_SUMMARY.get(), CellSummary.of(entry.stacks()));
        //?} else {
        /*stack.getOrCreateTag().putUUID(CellNbt.ID, id);
        stack.getOrCreateTag().put(CellNbt.SUMMARY, CellNbt.summary(entry));
        stack.getOrCreateTag().putLong(CellNbt.ITEM_COUNT, entry.count());
        *///?}
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.translatable("commands.insaneae.cells.recovered", id.toString()), true);
        return 1;
    }

    /** 超次元MEセルはUUIDだけで中身が引けるので、要約は付けない。 */
    private static int recoverBig(CommandSourceStack source, ServerPlayer player, UUID id,
                                  CellContentStore.BigEntry entry) {
        Item item = BuiltInRegistries.ITEM.get(entry.item());
        if (item == Items.AIR) {
            source.sendFailure(Component.translatable("commands.insaneae.cells.unknown_item",
                    entry.item().toString()));
            return 0;
        }
        ItemStack stack = new ItemStack(item);
        InsaneMeCellData.setId(stack, id);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.translatable("commands.insaneae.cells.recovered", id.toString()), true);
        return 1;
    }
}
