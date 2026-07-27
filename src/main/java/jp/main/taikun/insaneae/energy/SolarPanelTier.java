package jp.main.taikun.insaneae.energy;

/**
 * AE ソーラーパネルの階層。1 段ごとに<b>ビット幅が 8 bit 増える</b>
 * (2^8-1 → 2^16-1 → 2^24-1 → 2^32-1)。
 *
 * <p>比較用の AE2 の数字: 水晶共鳴発生器が 20 AE/t (しかも 1 グリッドに 1 台しか働かない)、
 * 発破室が 4〜40 AE/t。最下段の 255 AE/t でも「日中・晴天・空が見えている」ときの値なので、
 * 夜と天候を均せば実効はこれより下がる。</p>
 *
 * <p>最上段は int に収まらない ({@code 2^32-1} > {@link Integer#MAX_VALUE}) ので
 * <b>発電量は long で持つ</b>。AE2 の電力は全経路 double なので注入側は問題ない。</p>
 */
public enum SolarPanelTier {
    BASIC("solar_panel", (1L << 11) - 1),
    ADVANCED("advanced_solar_panel", (1L << 23) - 1),
    ELITE("elite_solar_panel", (1L << 35) - 1),
    ULTIMATE("ultimate_solar_panel", (1L << 47) - 1);

    private final String id;
    private final long rate;

    SolarPanelTier(String id, long rate) {
        this.id = id;
        this.rate = rate;
    }

    /** ブロック／アイテムの登録名。例: {@code "advanced_solar_panel"}。 */
    public String id() {
        return id;
    }

    /** 真上に太陽があり快晴のときの発電量 (AE/tick)。 */
    public long ratePerTick() {
        return rate;
    }
}
