package jp.main.taikun.insaneae.energy;

import appeng.block.AEBaseEntityBlock;

/**
 * AE ソーラーパネルのブロック。階層ごとに 1 インスタンス。
 *
 * <p>{@link SolarPanelBlockEntity} は階層を知る必要があるが、
 * ブロックステートには持たせず、ブロック側から渡している。</p>
 */
public class SolarPanelBlock extends AEBaseEntityBlock<SolarPanelBlockEntity> {

    private final SolarPanelTier tier;

    public SolarPanelBlock(SolarPanelTier tier) {
        super(metalProps());
        this.tier = tier;
    }

    public SolarPanelTier getTier() {
        return tier;
    }
}
