package jp.main.taikun.insaneae.energy;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.util.AECableType;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * AE ソーラーパネル。日射量に応じて毎 tick グリッドに発電する。
 *
 * <p><b>AE2 の {@code IPassiveEnergyGenerator} は使っていない。</b>
 * あれは {@code EnergyService#onServerStartTick} が
 * 「グリッド内で最も発電量の多い 1 台以外を全部 {@code setSuppressed(true)} で止める」作りなので、
 * パネルを何枚並べても 1 枚ぶんしか発電しなくなる。</p>
 *
 * <p><b>{@code IGridTickable} も使えない。</b> AE2 のティックマネージャは
 * <b>アクティブなノードしか呼ばない</b>ので、電力ゼロのネットワークでは
 * 「電力が無い → パネルが呼ばれない → 永遠に電力が来ない」とデッドロックする。
 * 発電機は自力で起動できないといけないので、ブロック側の ticker から動かしている。</p>
 */
public class SolarPanelBlockEntity extends AENetworkedBlockEntity implements ServerTickingBlockEntity {

    /** 真上に太陽があるときの日射量 (バニラの日照センサーと同じ 0〜15 スケール)。 */
    private static final int MAX_SUNLIGHT = 15;
    /** 日射量を測り直す間隔。毎 tick 明るさを引くのは無駄なのでキャッシュする。 */
    private static final int SUNLIGHT_RECHECK_INTERVAL = 20;

    private final SolarPanelTier tier;

    private int cachedSunlight;
    private long nextSunlightCheck;

    public SolarPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, SolarPanelTier tier) {
        super(type, pos, state);
        this.tier = tier;
        getMainNode().setIdlePowerUsage(0.0);
    }

    public SolarPanelTier getTier() {
        return tier;
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    /** 現在の発電量 (AE/tick)。0 なら夜・雨・空が見えていない。 */
    public double getCurrentRate() {
        return (double) tier.ratePerTick() * cachedSunlight / MAX_SUNLIGHT;
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (now >= nextSunlightCheck) {
            nextSunlightCheck = now + SUNLIGHT_RECHECK_INTERVAL;
            cachedSunlight = computeSunlight();
        }
        if (cachedSunlight <= 0) {
            return;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid != null) {
            grid.getEnergyService().injectPower(getCurrentRate(), Actionable.MODULATE);
        }
    }

    /**
     * 0〜15 の日射量。<b>バニラの日照センサー ({@code DaylightDetectorBlock}) と同じ式</b>なので、
     * 夜・雨・雷雨・屋根の下・スカイライトの無いディメンションが自動的に 0 (または減衰) になる。
     *
     * <p>{@code canSeeSky} は<b>使わない</b>こと。あれはハイトマップ由来で光量データと食い違うことがあり
     * (ブロックを置き換えた直後など)、「明るさは 15 なのに canSeeSky は false」で発電が止まる。
     * 日照センサーと同じく空の明るさだけを見れば、光量エンジンの結果とそのまま一致する。</p>
     */
    private int computeSunlight() {
        if (level == null || !level.dimensionType().hasSkyLight()) {
            return 0;
        }
        BlockPos above = getBlockPos().above();
        int light = level.getBrightness(LightLayer.SKY, above) - level.getSkyDarken();
        if (light <= 0) {
            return 0;
        }
        float sunAngle = level.getSunAngle(1.0F);
        float shift = sunAngle < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
        sunAngle += (shift - sunAngle) * 0.2F;
        return Mth.clamp(Math.round(light * Mth.cos(sunAngle)), 0, MAX_SUNLIGHT);
    }
}
