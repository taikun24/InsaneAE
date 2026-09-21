package jp.main.taikun.insaneae.client.cable;

import appeng.api.util.AEColor;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.network.CompressedCablePart;
import jp.main.taikun.insaneae.network.HyperCablePart;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * ワールドに置いたケーブルを<b>手持ちの絵と同じ絵で描く</b>ための差し替え表。
 *
 * <h2>なぜ差し替えが要るか</h2>
 * <p>AE2 のケーブルはワールドでは部品のモデルを使わない。{@code CableBusBakedModel} が
 * {@code CableBusRenderState} (中身は {@code AECableType} と {@link AEColor} と
 * チャンネル数だけ) を見て {@code CableBuilder} に描かせる作りで、
 * <b>テクスチャは (ケーブル種別, 色) の組でしか引けない</b>。
 * この Mod のケーブルは 2 本とも {@code AECableType.SMART} を名乗るので、
 * 素のままだと<b>置いた瞬間 AE2 のスマートケーブルの絵</b>になり、手持ちと食い違う。</p>
 *
 * <h2>やっていること</h2>
 * <p>AE2 が引いたスプライトを<b>出力の直前ですり替える</b>。
 * 描く形 (立方体の大きさ・UV・発光) は AE2 のものをそのまま使うので、
 * 差し替えるのは絵だけで済む。</p>
 * <ol>
 *   <li>{@code CableBuilderMixin} … 焼き上げ時に「AE2 のスプライト → こちらのスプライト」の
 *       対応表をここに作る。AE2 と同じ {@code bakedTextureGetter} を使うので、
 *       <b>キーは AE2 が実際に使うインスタンスそのもの</b>になる。</li>
 *   <li>{@code CableBusBakedModelMixin} … 1 本ぶんの板を組む間だけ
 *       {@link #beginCable} で種類を立てる。描画スレッドは複数あるので
 *       {@link ThreadLocal} で持つ。</li>
 *   <li>{@code CubeBuilderMixin} … 立っている間は {@link #swap} を通し、
 *       対応表に載っているスプライトだけ差し替える。</li>
 * </ol>
 *
 * <p>描画キャッシュ ({@code CableBusBakedModel} の {@code LoadingCache}) の鍵は
 * レンダーステートなので、<b>種類をその {@code equals}/{@code hashCode} にも混ぜる</b>
 * 必要がある ({@code CableBusRenderStateMixin})。忘れると、同じ色・同じ接続の
 * AE2 のケーブルと板を共有して<b>片方の絵がもう片方に出る</b>。</p>
 */
public final class InsaneCableSprites {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** ケーブルの種類。テクスチャの置き場所 ({@code part/cable/<dir>/}) がそのまま値。 */
    public enum Kind {
        COMPRESSED("compressed"),
        HYPER("hyper");

        private final String directory;

        Kind(String directory) {
            this.directory = directory;
        }

        public String directory() {
            return directory;
        }
    }

    /** チャンネル目盛りの段階。AE2 の {@code SmartCableTextures} と同じ並び。 */
    private static final String[] CHANNEL_SUFFIXES = {
            "channels_00", "channels_01", "channels_02", "channels_03", "channels_04",
            "channels_10", "channels_11", "channels_12", "channels_13", "channels_14",
    };

    /**
     * AE2 のスプライト → こちらのスプライト。
     *
     * <p>{@link IdentityHashMap} なのは、鍵が<b>アトラス上の同じインスタンス</b>だと
     * 分かっているため (同じ絵で別インスタンスということが起きない)。</p>
     */
    private static final Map<Kind, Map<TextureAtlasSprite, TextureAtlasSprite>> REPLACEMENTS =
            new HashMap<>();

    /** いま板を組んでいるケーブルの種類。AE2 のケーブルを描いている間は null。 */
    private static final ThreadLocal<Kind> CURRENT = new ThreadLocal<>();

    private InsaneCableSprites() {
    }

    /**
     * 焼き上げ時に対応表を作り直す ({@code CableBuilder} の構築ごと = リソース再読み込みごと)。
     *
     * @param bakedTextureGetter AE2 が使っているものと<b>同じ</b>スプライト取得関数
     */
    public static void rebuild(Function<Material, TextureAtlasSprite> bakedTextureGetter) {
        Map<Kind, Map<TextureAtlasSprite, TextureAtlasSprite>> built = new HashMap<>();
        for (Kind kind : Kind.values()) {
            Map<TextureAtlasSprite, TextureAtlasSprite> map = new IdentityHashMap<>();
            for (AEColor color : AEColor.values()) {
                String name = color.name().toLowerCase(Locale.ROOT);
                // 繋がっている向きの帯。AE2 のスマートケーブルの帯を置き換える。
                put(map, bakedTextureGetter, ae2("part/cable/smart/" + name), ours(kind, name));
                // 芯 (中央の立方体)。SMART の芯は AE2 では covered のものが使われる。
                put(map, bakedTextureGetter, ae2("part/cable/core/covered/" + name),
                        ours(kind, "core_" + name));
            }
            for (String suffix : CHANNEL_SUFFIXES) {
                put(map, bakedTextureGetter, ae2("part/cable/smart/" + suffix), ours(kind, suffix));
            }
            built.put(kind, map);
        }
        synchronized (REPLACEMENTS) {
            REPLACEMENTS.clear();
            REPLACEMENTS.putAll(built);
        }
    }

    /**
     * 1 組ぶんの対応を入れる。
     *
     * <p>差し替え先が<b>欠けている絵</b>だったら入れずに警告する。黙って入れると
     * ワールドのケーブルが紫黒になるだけで原因が分からないため
     * (テクスチャの名前を変えたときにここに出る)。</p>
     */
    private static void put(Map<TextureAtlasSprite, TextureAtlasSprite> map,
            Function<Material, TextureAtlasSprite> getter, Material from, Material to) {
        TextureAtlasSprite source = getter.apply(from);
        TextureAtlasSprite target = getter.apply(to);
        if (source == null || target == null) {
            return;
        }
        if (MissingTextureAtlasSprite.getLocation().equals(target.contents().name())) {
            LOGGER.warn("ケーブルの絵が見つからない: {} (アトラスに載っているか確認すること)",
                    to.texture());
            return;
        }
        map.put(source, target);
    }

    private static Material ae2(String path) {
        return new Material(TextureAtlas.LOCATION_BLOCKS,
                ResourceLocation.fromNamespaceAndPath("ae2", path));
    }

    private static Material ours(Kind kind, String name) {
        return new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.fromNamespaceAndPath(
                InsaneAE.MODID, "part/cable/" + kind.directory() + "/" + name));
    }

    /**
     * その部品がこの Mod のケーブルなら、その種類。違うなら null。
     *
     * <p>ワールドの絵を差し替える口はここ 1 箇所なので、ケーブルを足したら
     * <b>ここと {@link Kind} に 1 行ずつ</b>足せば全部追従する。</p>
     */
    public static Kind kindOf(Object part) {
        if (part instanceof HyperCablePart) {
            return Kind.HYPER;
        }
        if (part instanceof CompressedCablePart) {
            return Kind.COMPRESSED;
        }
        return null;
    }

    /** この板はこの種類のケーブルのもの、と印を付ける。必ず {@link #endCable} と対で呼ぶこと。 */
    public static void beginCable(Kind kind) {
        CURRENT.set(kind);
    }

    /** 印を外す。外し忘れると<b>次に同じスレッドで描くものまで差し替わる</b>。 */
    public static void endCable() {
        CURRENT.remove();
    }

    /**
     * 印が立っている間だけ、対応表に載っているスプライトを差し替える。
     *
     * <p>載っていないもの (ファサードなど) はそのまま返すので、
     * 差し替えが効く範囲は<b>このケーブル自身の板だけ</b>。</p>
     */
    public static TextureAtlasSprite swap(TextureAtlasSprite sprite) {
        Kind kind = CURRENT.get();
        if (kind == null || sprite == null) {
            return sprite;
        }
        Map<TextureAtlasSprite, TextureAtlasSprite> map = REPLACEMENTS.get(kind);
        if (map == null) {
            return sprite;
        }
        TextureAtlasSprite replacement = map.get(sprite);
        return replacement != null ? replacement : sprite;
    }
}
