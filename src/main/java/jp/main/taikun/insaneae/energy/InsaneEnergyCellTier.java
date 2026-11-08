package jp.main.taikun.insaneae.energy;

/**
 * 「限界突破」エネルギーセルの階層。
 *
 * <p>既存の並びはAE2 / MEGA Cellsが
 * Energy Cell 200k → Dense 1.6M → Superdense 12.8Mと1段8倍で伸ばしているので、
 * その続きとしてSuperdenseの上に13段を足す。最上段 {@link #COSMIC} は
 * 7,036,874,417,766,400,000 AE、つまり約703京。</p>
 *
 * <p>13段で止めている理由は、もう1段×8を足すと約5,629京になり、
 * longの上限9,223,372,036,854,775,807、つまり約922京を超える。
 * AE2の電力は全経路doubleなので即座に壊れるわけではないが、
 * 「longに収まる」ところで切っておくのが安全。</p>
 *
 * <p>精度の注意: AE2の電力はdoubleで、仮数は53 bit、約9.0e15なので、
 * 1.37P (=13,743,895,347,200,000) の {@link #SUPERNOVA} 以降は
 * 「1 AEずつ」の増減が値に反映されなくなる。総量として使うぶんには問題ないが、
 * 満タン近くで1 AE単位の出し入れをしても数値が動かない場合がある。</p>
 *
 * <p>各値の伸ばし方。すべてSuperdenseを起点にする:</p>
 * <ul>
 *   <li>容量は1段8倍。AE2/MEGAと同じ。</li>
 *   <li>充電速度も1段8倍。AE2/MEGAは2倍ずつだが、それだと最上段の
 *       アイテム形態をチャージャーで充電するのに2700億tickかかって実用外になる。
 *       8倍にしておくと満充電までの時間が全階層4000 tickで揃う
 *       ブロックとして設置した場合はネットワークからの注入に速度制限が無いので無関係。</li>
 *   <li>優先度は1段2倍。AE2/MEGAは容量と同じ8倍だが、優先度はintなので
 *       6段目で溢れる。順序さえ保てればよいので倍率を落としてある
 *       優先度が高いセルから先に放電し、後から充電される。</li>
 * </ul>
 */
public enum InsaneEnergyCellTier {
    HYPERDENSE("hyperdense_energy_cell", 1),
    ULTRADENSE("ultradense_energy_cell", 2),
    NEUTRON("neutron_energy_cell", 3),
    DEGENERATE("degenerate_energy_cell", 4),
    COLLAPSAR("collapsar_energy_cell", 5),
    SINGULARITY("singularity_energy_cell", 6),
    PULSAR("pulsar_energy_cell", 7),
    QUASAR("quasar_energy_cell", 8),
    NOVA("nova_energy_cell", 9),
    SUPERNOVA("supernova_energy_cell", 10),
    HYPERNOVA("hypernova_energy_cell", 11),
    GALACTIC("galactic_energy_cell", 12),
    /** 7,036,874,417,766,400,000 AE。これ以上8倍するとlongに収まらない。 */
    COSMIC("cosmic_energy_cell", 13);

    /** MEGA CellsのSuperdense Energy Cellの値。= 本enumの起点。 */
    public static final long SUPERDENSE_MAX_POWER = 12_800_000L;
    public static final long SUPERDENSE_CHARGE_RATE = 3_200L;
    public static final int SUPERDENSE_PRIORITY = 12_800;

    private final String id;
    private final int step;

    InsaneEnergyCellTier(String id, int step) {
        this.id = id;
        this.step = step;
    }

    /** ブロック／アイテムの登録名。例: {@code "hyperdense_energy_cell"}。 */
    public String id() {
        return id;
    }

    /** Superdenseから何段上か。1起点。 */
    public int step() {
        return step;
    }

    /**
     * 蓄電容量 (AE)。8^step倍。
     *
     * <p>longのシフトで作ってからdouble化しているので、
     * 最上段まで1 AEの誤差も無く表現できる。仮数に必要なのは12 bitだけ。</p>
     */
    public double maxPower() {
        return SUPERDENSE_MAX_POWER << (3 * step);
    }

    /** チャージャーでアイテム形態を充電するときの速度 (AE/tick)。容量と同じく8^step倍。 */
    public double chargeRate() {
        return SUPERDENSE_CHARGE_RATE << (3 * step);
    }

    /** ネットワーク内での放電／充電順序。intに収める必要があるので2^step倍。 */
    public int priority() {
        return SUPERDENSE_PRIORITY << step;
    }
}
