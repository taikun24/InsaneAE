package jp.main.taikun.insaneae.energy;

/**
 * AEソーラーパネルの階層。1段ごとにビット幅が12 bit増える
 * 2^11-1、2^23-1、2^35-1、2^47-1の順に伸びる。
 *
 * <p>比較用のAE2の数字は、水晶共鳴発生器が20 AE/tで、しかも1グリッドに1台しか働かないこと、
 * 発破室が4〜40 AE/t。最下段の2047 AE/tでも「日中・晴天・空が見えている」ときの値なので、
 * 夜と天候を均せば実効はこれより下がる。</p>
 *
 * <p>ELITE以上はintに収まらない ({@code 2^35-1} > {@link Integer#MAX_VALUE}) ので
 * 発電量はlongで持つ。AE2の電力は全経路doubleなので注入側は問題ない。</p>
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
