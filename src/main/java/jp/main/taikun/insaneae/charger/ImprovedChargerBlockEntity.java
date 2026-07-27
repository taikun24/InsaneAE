package jp.main.taikun.insaneae.charger;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalBlockPos;
import appeng.blockentity.grid.AENetworkInvBlockEntity;
import appeng.blockentity.misc.ChargerBlockEntity;
import appeng.blockentity.misc.ChargerRecipes;
import appeng.recipes.handlers.ChargerRecipe;
import appeng.util.Platform;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * AE2 のチャージャーの「限界突破」版。
 *
 * <p>AE2 のチャージャーは <b>1 スロット 1 個</b>しか持てず、変換も
 * {@code random.nextFloat() > 0.8} の抽選で 1 個ずつ、しかも内部バッファ 1600 AE を
 * 800 AE/tick でしか補充しないため、実効で 1 個 / 5tick 程度しか出ない。
 * こちらは以下を変えている:</p>
 *
 * <ul>
 *   <li>入力 / 出力の 2 スロット構成で、それぞれ 64 個まで持てる。</li>
 *   <li>抽選を廃止し、<b>1 tick で買えるだけまとめて変換</b>する
 *       (1 個あたりの電力コスト 1600 AE は AE2 と同じ)。</li>
 *   <li>電力は内部バッファを介さず<b>グリッドから直接</b>引く。
 *       電力さえ足りていれば 64 個が 1 tick で終わる。</li>
 *   <li>充電可能アイテム ({@link IAEItemPowerStorage}) は<b>速度上限なし</b>で満充電にする。
 *       AE2 はアイテム側の {@code chargeRate} で刻むが、こちらはグリッドの電力だけが制約。</li>
 * </ul>
 *
 * <p>速度は既に「ネットワークの電力量で決まる」ところまで来ているので、
 * 加速カードのスロットは持たせていない (挿しても意味が無いため)。</p>
 */
public class ImprovedChargerBlockEntity extends AENetworkInvBlockEntity implements IGridTickable {

    /** 1 個変換するのに必要な電力。AE2 のチャージャーと同じ。 */
    public static final double POWER_PER_CONVERSION = ChargerBlockEntity.POWER_MAXIMUM_AMOUNT;

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    private static final int SLOT_CAPACITY = 64;

    private final AppEngInternalInventory inv =
            new AppEngInternalInventory(this, 2, SLOT_CAPACITY, new ChargerFilter());

    /** 稼働中か。パーティクル演出のためクライアントにも送る。 */
    private boolean working;

    public ImprovedChargerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        getMainNode().setIdlePowerUsage(0.0).addService(IGridTickable.class, this);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return inv;
    }

    @Override
    public void onChangeInventory(InternalInventory inv, int slot) {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        markForUpdate();
    }

    public boolean isWorking() {
        return working;
    }

    @Override
    protected boolean readFromStream(FriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        working = data.readBoolean();
        inv.setItemDirect(INPUT_SLOT, data.readItem());
        inv.setItemDirect(OUTPUT_SLOT, data.readItem());
        return changed;
    }

    @Override
    protected void writeToStream(FriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(working);
        data.writeItem(inv.getStackInSlot(INPUT_SLOT));
        data.writeItem(inv.getStackInSlot(OUTPUT_SLOT));
    }

    /**
     * 右クリック時の挙動。手持ちが入るなら入れ、そうでなければ
     * 出力 → 入力の順に取り出す (AE2 のチャージャーと同じ操作感)。
     */
    public void activate(Player player) {
        if (!Platform.hasPermissions(new DimensionalBlockPos(this), player)) {
            return;
        }
        ItemStack held = player.getInventory().getSelected();
        if (!held.isEmpty() && inv.insertItem(INPUT_SLOT, held, true).getCount() < held.getCount()) {
            ItemStack leftover = inv.insertItem(INPUT_SLOT, held, false);
            player.getInventory().setItem(player.getInventory().selected, leftover);
            return;
        }
        for (int slot : new int[]{OUTPUT_SLOT, INPUT_SLOT}) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                inv.setItemDirect(slot, ItemStack.EMPTY);
                Platform.spawnDrops(player.level(), getBlockPos().above(), List.of(stack));
                return;
            }
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, inv.getStackInSlot(INPUT_SLOT).isEmpty(), true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        boolean wasWorking = working;
        working = false;

        IGrid grid = getMainNode().getGrid();
        ItemStack input = inv.getStackInSlot(INPUT_SLOT);
        if (grid != null && !input.isEmpty()) {
            if (Platform.isChargeable(input)) {
                chargeItem(grid.getEnergyService(), input);
            } else {
                convert(grid.getEnergyService(), input);
            }
        }

        if (working != wasWorking) {
            markForUpdate();
        }
        return working ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    /**
     * 充電可能アイテムを満充電にする。
     *
     * <p>スタックしていても NBT は 1 つしか無い (＝全部が同時に満タンになる) ので、
     * <b>個数ぶんの電力をまとめて払う</b>こと。これを忘れると 1 個ぶんの電力で
     * 64 個が満充電になる抜け道になる。</p>
     */
    private void chargeItem(IEnergyService energy, ItemStack stack) {
        IAEItemPowerStorage storage = (IAEItemPowerStorage) stack.getItem();
        double missingPerItem = storage.getAEMaxPower(stack) - storage.getAECurrentPower(stack);
        if (missingPerItem <= 0) {
            return;
        }
        int count = stack.getCount();
        double extracted = energy.extractAEPower(
                missingPerItem * count, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted <= 0) {
            return;
        }
        storage.injectAEPower(stack, extracted / count, Actionable.MODULATE);
        inv.setItemDirect(INPUT_SLOT, stack);
        working = true;
    }

    /** チャージャーレシピ (水晶 → 帯電水晶 など) を、電力で買えるぶんだけまとめて処理する。 */
    private void convert(IEnergyService energy, ItemStack input) {
        ChargerRecipe recipe = ChargerRecipes.findRecipe(level, input);
        if (recipe == null) {
            return;
        }
        ItemStack output = inv.getStackInSlot(OUTPUT_SLOT);
        int outputSpace = SLOT_CAPACITY - output.getCount();
        if (!output.isEmpty() && output.getItem() != recipe.result) {
            outputSpace = 0;
        }
        int limit = Math.min(input.getCount(), outputSpace);
        if (limit <= 0) {
            return;
        }

        // 買える個数を電力から決める (SIMULATE してから必要ぶんだけ MODULATE)。
        double available = energy.extractAEPower(
                POWER_PER_CONVERSION * limit, Actionable.SIMULATE, PowerMultiplier.ONE);
        int converted = (int) Math.min(limit, Math.floor(available / POWER_PER_CONVERSION));
        if (converted <= 0) {
            return;
        }
        energy.extractAEPower(POWER_PER_CONVERSION * converted, Actionable.MODULATE, PowerMultiplier.ONE);

        input.shrink(converted);
        inv.setItemDirect(INPUT_SLOT, input);
        if (output.isEmpty()) {
            inv.setItemDirect(OUTPUT_SLOT, new ItemStack(recipe.result, converted));
        } else {
            output.grow(converted);
            inv.setItemDirect(OUTPUT_SLOT, output);
        }
        working = true;
    }

    /** 入力スロットには処理できる物だけ、取り出しは「もう処理済み」の物だけ許す。 */
    private final class ChargerFilter implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            if (slot != INPUT_SLOT) {
                return false;
            }
            return Platform.isChargeable(stack) || (level != null && ChargerRecipes.allowInsert(level, stack));
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            if (slot == OUTPUT_SLOT) {
                return true;
            }
            ItemStack stack = inv.getStackInSlot(slot);
            if (Platform.isChargeable(stack)) {
                IAEItemPowerStorage storage = (IAEItemPowerStorage) stack.getItem();
                // 満充電になったものだけ搬出させる (充電途中で持ち出されないように)。
                return storage.getAECurrentPower(stack) >= storage.getAEMaxPower(stack);
            }
            return level == null || ChargerRecipes.allowExtract(level, stack);
        }
    }
}
