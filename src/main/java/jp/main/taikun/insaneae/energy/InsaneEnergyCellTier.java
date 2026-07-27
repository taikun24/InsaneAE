package jp.main.taikun.insaneae.energy;

/**
 * 「限界突破」エネルギーセルの階層。
 *
 * <p>既存の並びは AE2 / MEGA Cells が
 * Energy Cell 200k → Dense 1.6M → Superdense 12.8M と <b>1 段 8 倍</b>で伸ばしているので、
 * その続きとして Superdense の上に 13 段を足す。最上段 {@link #COSMIC} は
 * 7,036,874,417,766,400,000 AE (約 703 京)。</p>
 *
 * <p><b>13 段で止めている理由</b>: もう 1 段 (×8) 足すと約 5,629 京になり、
 * long の上限 9,223,372,036,854,775,807 (約 922 京) を超える。
 * AE2 の電力は全経路 double なので即座に壊れるわけではないが、
 * 「long に収まる」ところで切っておくのが安全。</p>
 *
 * <p><b>精度の注意</b>: AE2 の電力は double (仮数 53 bit ≒ 9.0e15) なので、
 * 1.37P (=13,743,895,347,200,000) の {@link #SUPERNOVA} 以降は
 * 「1 AE ずつ」の増減が値に反映されなくなる。総量として使うぶんには問題ないが、
 * 満タン近くで 1 AE 単位の出し入れをしても数値が動かない場合がある。</p>
 *
 * <p>各値の伸ばし方 (すべて Superdense を起点に):</p>
 * <ul>
 *   <li>容量は 1 段 8 倍 (AE2/MEGA と同じ)。</li>
 *   <li>充電速度も 1 段 8 倍。AE2/MEGA は 2 倍ずつだが、それだと最上段の
 *       <b>アイテム</b>形態をチャージャーで充電するのに 2700 億 tick かかって実用外になる。
 *       8 倍にしておくと満充電までの時間が全階層 4000 tick で揃う
 *       (ブロックとして設置した場合はネットワークからの注入に速度制限が無いので無関係)。</li>
 *   <li>優先度は 1 段 2 倍。AE2/MEGA は容量と同じ 8 倍だが、優先度は int なので
 *       6 段目で溢れる。順序さえ保てればよいので倍率を落としてある
 *       (優先度が高いセルから先に放電し、後から充電される)。</li>
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
    /** 7,036,874,417,766,400,000 AE。これ以上 8 倍すると long に収まらない。 */
    COSMIC("cosmic_energy_cell", 13);

    /** MEGA Cells の Superdense Energy Cell の値 (= 本 enum の起点)。 */
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

    /** Superdense から何段上か (1 起点)。 */
    public int step() {
        return step;
    }

    /**
     * 蓄電容量 (AE)。8^step 倍。
     *
     * <p>long のシフトで作ってから double 化しているので、
     * 最上段まで 1 AE の誤差も無く表現できる (仮数に必要なのは 12 bit だけ)。</p>
     */
    public double maxPower() {
        return SUPERDENSE_MAX_POWER << (3 * step);
    }

    /** チャージャーでアイテム形態を充電するときの速度 (AE/tick)。容量と同じく 8^step 倍。 */
    public double chargeRate() {
        return SUPERDENSE_CHARGE_RATE << (3 * step);
    }

    /** ネットワーク内での放電／充電順序。int に収める必要があるので 2^step 倍。 */
    public int priority() {
        return SUPERDENSE_PRIORITY << step;
    }
}
