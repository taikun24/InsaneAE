#!/usr/bin/env python3
"""クラフトストレージ / 協調処理ユニットのブロックテクスチャを「色」から自動生成する。

    pip install pillow

    python tools/gen_crafting_textures.py                  # 生成
    python tools/gen_crafting_textures.py --dump-templates # 下敷きを書き出す

出力先は src/main/resources/assets/insaneae/textures/block/crafting/ で、
階層ごとに次の 3 ファイルを出す。

    <id>_storage.png            16x16   未 formed のブロック面
    <id>_storage_light.png      16xN    formed 時の発光レイヤ (N/16 コマのアニメ)
    <id>_storage_light.png.mcmeta       アニメ定義 (2 コマ以上のときだけ)

色を変えたいときは下の STORAGE_BAND_COLORS / ACCELERATOR_COLOR をいじって流し直すだけ。

--------------------------------------------------------------------------------------
ベーステクスチャ / 色テクスチャを差し替える
--------------------------------------------------------------------------------------
tools/textures/ に次の名前で置くと、そちらが優先して使われる (無いものは下の
描画コードが手続き的に描く)。`--dump-templates` で今の見た目を書き出せるので、
それをペイントソフトで直してから戻すのが早い。

    storage_base.png       未 formed 面の下地。階層に依らず<b>そのまま</b>使われる
    storage_color.png      その上に重ねる中央の色レイヤ。階層色に染まる
    storage_light.png      formed の発光レイヤ。階層色に染まる
    accelerator_base.png / accelerator_color.png / accelerator_light.png

<b>base は染めないので、地に色が付いていても構わない</b> (無彩色で描く必要はない)。
階層に依らない部分はすべて base に描くこと。

染める 2 枚 (color / light) は、次のどちらの描き方でもよい。

    グレースケールで描く  明度だけが残り、色相と彩度は階層色のものになる。
                          陰影だけ描けばよいが、1 枚の中で色相は変えられない。
    色を付けて描く        「基準色相 → 階層色の色相」の回転になる。1 枚の中の
                          色相差はそのまま保たれるので、中央のグラデーションなど
                          意図的な色相のズレを残せる。基準色相は下の
                          STORAGE_TEMPLATE_HUE / ACCELERATOR_TEMPLATE_HUE で宣言する
                          (協調処理ユニットは赤で描く前提にしてある)。

どちらの場合も彩度の無い画素は白のまま残るので、光の粒は白くできる。
アルファはそのまま維持されるので、抜きたい部分は透明にしておくこと。

発光レイヤは高さが 16 ならアニメ無し、16xN ならそのまま N コマとして扱う。
"""

from __future__ import annotations

import argparse
import colorsys
import json
import math
import os
from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO, "src/main/resources/assets/insaneae/textures/block/crafting")
TEMPLATE_DIR = os.path.join(REPO, "tools/textures")

# --------------------------------------------------------------------------------------
# 色の設定 — ここだけ触れば見た目が変わる
# --------------------------------------------------------------------------------------

# クラフトストレージの階層 (ModBlocks の登録順と同じ)。
STORAGE_TIERS = ["1g", "4g", "16g", "64g", "256g",
                 "1t", "4t", "16t", "64t", "256t",
                 "1p", "4p", "16p", "64p", "256p",
                 "1e", "4e"]

# 単位帯ごとの基準色。cell_component_<tier>.png (手描きのアイテムテクスチャ) から
# 拾った色に合わせてあるので、アイテムとブロックの色が揃う。
STORAGE_BAND_COLORS = {
    "g": "#ff42c6",   # マゼンタ
    "t": "#ff5c20",   # オレンジ
    "p": "#d2ff20",   # 黄緑
    "e": "#33ec4c",   # 緑
}
# 帯の中の例外 (最上段だけ別色にしたい等)。
STORAGE_TIER_OVERRIDES = {
}
# 同じ帯の中で下位ほど暗くする度合い。1.0 にすると帯の中は全部同じ色になる。
BAND_MIN_BRIGHTNESS = 0.72

# 協調処理ユニットの階層。
ACCELERATOR_TIERS = ["16x", "64x", "256x", "1k", "4k", "16k", "64k", "256k",
                     "1m", "4m", "16m", "64m", "256m", "1g", "2g"]
# 加速側は「上位ほど白熱していく」1 色系。下位=濃いシアン → 上位=ほぼ白。
ACCELERATOR_COLOR = "#ff00ff"
ACCELERATOR_MIN_BRIGHTNESS = 0.60
ACCELERATOR_MAX_DESATURATION = 0.80   # 最上段でどこまで白に寄せるか

# --------------------------------------------------------------------------------------
# テンプレートを「何色で描いてあるか」
# --------------------------------------------------------------------------------------
# 中央 (color) と発光 (light) のテンプレートに色を付けて描く場合、その基準色相をここで宣言する。
# 宣言しておくと「その色相 → 階層色の色相」の回転になるので、1 枚の中でわざと色相を
# ずらした部分 (中央のグラデーションなど) が相対関係のまま保たれる。
#
# None にすると自動判定 (テンプレの主要色相を基準にする)。
# テンプレがグレースケールのままなら、どちらを指定しても今までどおり
# 「明度だけ残して階層色を乗せる」染め方になるので、塗り替える前に設定しても害はない。
#
# 値は色相 (0.0〜1.0)。RED / GREEN / BLUE を使うと分かりやすい。
RED, GREEN, BLUE = 0.0, 1.0 / 3.0, 2.0 / 3.0

STORAGE_TEMPLATE_HUE = None       # 自動判定
ACCELERATOR_TEMPLATE_HUE = RED    # 中央と発光は赤で描く

# 色相を持つ画素とみなす最小彩度。これ未満は「地のグレー / 白いハイライト」として扱う。
MIN_SAT = 0.15

# --------------------------------------------------------------------------------------
# 手続き描画のパラメータ (テンプレートが無いときに使う)
# --------------------------------------------------------------------------------------

EDGE = (0x19, 0x19, 0x19)         # 一番外側の枠
BEVEL_LIGHT = (0x6e, 0x6e, 0x6e)  # 上/左のハイライト
BEVEL_DARK = (0x2e, 0x2e, 0x2e)   # 下/右の影
CHASSIS = (0x4c, 0x4c, 0x4c)      # 筐体の地
RECESS = (0x23, 0x23, 0x23)       # 中央パネルの落とし込み枠

SIZE = 16
FRAMES = 8

# 発光レイヤのドット絵 (# = 光る)
# ストレージ: 外周のリングから 4 本のリードが中央のチップに伸びる回路パターン。
STORAGE_MASK = [
    "................",
    "................",
    "..############..",
    "..#..........#..",
    "..#..........#..",
    "..#..........#..",
    "..#...####...#..",
    "..#####..#####..",
    "..#####..#####..",
    "..#...####...#..",
    "..#..........#..",
    "..#..........#..",
    "..#..........#..",
    "..############..",
    "................",
    "................",
]

# 協調処理ユニット: 上下向きの二重シェブロン (加速の記号)。
ACCELERATOR_MASK = [
    "................",
    "................",
    "......####......",
    ".....######.....",
    "....##....##....",
    "...##......##...",
    "..##........##..",
    "................",
    "................",
    "..##........##..",
    "...##......##...",
    "....##....##....",
    ".....######.....",
    "......####......",
    "................",
    "................",
]


def mcmeta(frames: int) -> dict:
    return {
        "animation": {
            "interpolate": True,
            "frames": [{"index": 0, "time": 100}] + [{"index": i, "time": 2} for i in range(1, frames)],
        }
    }


# --------------------------------------------------------------------------------------
# 色ユーティリティ
# --------------------------------------------------------------------------------------


def parse_hex(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def scale(color, factor: float, desaturate: float = 0.0):
    """明度を factor 倍し、desaturate のぶんだけ白に寄せる。"""
    h, s, v = colorsys.rgb_to_hsv(*[c / 255 for c in color])
    s = max(0.0, s * (1.0 - desaturate))
    v = min(1.0, v * factor)
    return tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h, s, v))


def storage_color(tier: str) -> tuple[int, int, int]:
    if tier in STORAGE_TIER_OVERRIDES:
        return parse_hex(STORAGE_TIER_OVERRIDES[tier])
    band = tier[-1]
    base = parse_hex(STORAGE_BAND_COLORS[band])
    # 同じ帯の中での位置 (0.0 = 帯の最下段, 1.0 = 帯の最上段)
    in_band = [t for t in STORAGE_TIERS if t[-1] == band and t not in STORAGE_TIER_OVERRIDES]
    pos = in_band.index(tier) / max(1, len(in_band) - 1)
    return scale(base, BAND_MIN_BRIGHTNESS + (1.0 - BAND_MIN_BRIGHTNESS) * pos)


def accelerator_color(tier: str) -> tuple[int, int, int]:
    pos = ACCELERATOR_TIERS.index(tier) / max(1, len(ACCELERATOR_TIERS) - 1)
    factor = ACCELERATOR_MIN_BRIGHTNESS + (1.0 - ACCELERATOR_MIN_BRIGHTNESS) * pos
    return scale(parse_hex(ACCELERATOR_COLOR), factor, ACCELERATOR_MAX_DESATURATION * pos)


def dominant_hue(image: Image.Image, min_sat: float = MIN_SAT) -> tuple[float, float, float]:
    """テンプレートの「主要な色相」と、彩度のある画素の最大彩度・全体の最大明度を返す。

    <b>面積で決める</b>のが要点。色相を 72 分割したヒストグラムに彩度のある画素だけを
    入れ、一番画素数の多い色相を基準にする。「一番明るい画素」を基準にすると、
    白いハイライトが 1 点あるだけで全体の回転量がそれに引きずられてしまう
    (加速機の中央で意図しない色相のズレが出ていたのはこれが原因)。

    彩度のある画素が 1 つも無ければ彩度に 0.0 を返す = グレースケール扱い。
    """
    counts: dict[int, int] = {}
    peak_s = 0.0
    peak_v = 0.0
    for r, g, b, a in image.convert("RGBA").getdata():
        if a == 0:
            continue
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        peak_v = max(peak_v, v)
        if s < min_sat:
            continue
        counts[round(h * 72) % 72] = counts.get(round(h * 72) % 72, 0) + 1
        peak_s = max(peak_s, s)
    if not counts:
        return 0.0, 0.0, peak_v
    return max(counts.items(), key=lambda kv: kv[1])[0] / 72.0, peak_s, peak_v


def recolor(template: Image.Image, target, source_hue: float | None = None) -> Image.Image:
    """テンプレートを階層色に染める。テンプレの描き方で 2 通りに分かれる。

    <b>グレースケールで描いた場合</b> (彩度のある画素が 1 つも無い)
        明度だけを残し、色相と彩度は target のものをそのまま乗せる。
        陰影だけ描けばよいので楽だが、1 枚の中で色相を変えることはできない。

    <b>色を付けて描いた場合</b>
        「テンプレの基準色相 → target の色相」の回転になる。
        1 枚の中の色相差は<b>相対関係のまま保たれる</b>ので、
        わざと色相をずらした陰影やグラデーションがそのまま出る。
        基準色相は source_hue で明示できる (省略時はテンプレの主要色相)。
        たとえば赤 (#ff0000) で描いて source_hue=0.0 を渡せば、
        「赤で描いた絵をそのまま階層色に回した」結果になる。

    どちらの場合も彩度の無い画素 (白いハイライトなど) は彩度 0 のまま残るので、
    光の粒などは白いままにできる。明度はテンプレの最大明度が target の明度に
    なるよう比率で合わせる。アルファはそのまま維持される。
    """
    src = template.convert("RGBA")
    px = list(src.getdata())
    if not any(p[3] > 0 for p in px):
        return src

    ref_h, ref_s, peak_v = dominant_hue(src)
    if source_hue is not None:
        ref_h = source_hue
    # 彩度のある画素が無ければ色相を回しようがないので、グレースケールとして扱う
    # (source_hue を指定してあっても同じ。テンプレを塗るまでは今までどおり動く)。
    greyscale = ref_s <= 0.0
    peak_v = peak_v or 1.0
    tgt_h, tgt_s, tgt_v = colorsys.rgb_to_hsv(*[c / 255 for c in target])

    out = []
    for p in px:
        if p[3] == 0:
            out.append(p)
            continue
        h, s, v = colorsys.rgb_to_hsv(*[c / 255 for c in p[:3]])
        if greyscale:
            nh, ns = tgt_h, tgt_s
        else:
            nh = (h + (tgt_h - ref_h)) % 1.0
            ns = min(1.0, s * (tgt_s / ref_s))
        nv = min(1.0, v * (tgt_v / peak_v))
        r, g, b = colorsys.hsv_to_rgb(nh, ns, nv)
        out.append((round(r * 255), round(g * 255), round(b * 255), p[3]))

    result = Image.new("RGBA", src.size)
    result.putdata(out)
    return result


# --------------------------------------------------------------------------------------
# 手続き描画 (テンプレートが無いときのフォールバック / --dump-templates の中身)
# --------------------------------------------------------------------------------------


def draw_base() -> Image.Image:
    """未 formed 面の下地。中央のアクセント部分は透明のまま空けておく。"""
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if 4 <= x <= 11 and 4 <= y <= 11:
                continue  # 色レイヤに任せる
            if x in (0, SIZE - 1) or y in (0, SIZE - 1):
                rgb = EDGE
            elif 3 <= x <= 12 and 3 <= y <= 12:
                rgb = RECESS
            elif x == 1 or y == 1:
                rgb = BEVEL_LIGHT
            elif x == SIZE - 2 or y == SIZE - 2:
                rgb = BEVEL_DARK
            else:
                rgb = CHASSIS if (x + y) % 2 else scale(CHASSIS, 1.06)
            px[x, y] = (*rgb, 255)
    return img


def draw_color_layer() -> Image.Image:
    """下地に重ねる色レイヤ。グレースケールの陰影だけを持つ 8x8。"""
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y in range(4, 12):
        for x in range(4, 12):
            # 中心から外に向かって暗くする (同心の四角いグラデーション)。
            d = max(abs(x - 7.5), abs(y - 7.5)) / 3.5
            factor = 1.0 - 0.34 * d
            factor += 0.035 if (x * 7 + y * 3) % 3 == 0 else 0.0
            level = round(min(1.0, factor) * 255)
            px[x, y] = (level, level, level, 255)
    return img


def mask_pixels(mask) -> list[tuple[int, int]]:
    return [(x, y) for y, row in enumerate(mask) for x, ch in enumerate(row) if ch == "#"]


def ring_path(mask) -> list[tuple[int, int]]:
    """光の粒を走らせる経路。マスクの一番外側の画素を時計回りに並べたもの。"""
    pts = mask_pixels(mask)
    if not pts:
        return []
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    radius = max(max(abs(x - cx), abs(y - cy)) for x, y in pts)
    outer = [p for p in pts if max(abs(p[0] - cx), abs(p[1] - cy)) >= radius - 0.5]
    outer.sort(key=lambda p: math.atan2(p[1] - cy, p[0] - cx))
    return outer


def draw_light_layer(mask) -> Image.Image:
    """formed の発光レイヤ。グレースケール 8 コマ (16x128)。"""
    pts = mask_pixels(mask)
    path = ring_path(mask)
    inner = {p for p in pts if p not in set(path)}

    img = Image.new("RGBA", (SIZE, SIZE * FRAMES), (0, 0, 0, 0))
    px = img.load()
    for frame in range(FRAMES):
        offset = frame * SIZE
        for (x, y) in pts:
            # 光の粒 (255) を一番明るくしておくこと。グレースケールの下敷きは明度しか
            # 持てないので、粒を最大値にしないと染めたあと地の色に埋もれて見えなくなる。
            level = 228 if (x, y) in inner else 200
            px[x, y + offset] = (level, level, level, 255)
        # 外周を 4 つの光の粒が回る。
        if path:
            for j in range(4):
                idx = (frame * len(path) // FRAMES + j * len(path) // 4) % len(path)
                sx, sy = path[idx]
                px[sx, sy + offset] = (255, 255, 255, 255)
    return img


# --------------------------------------------------------------------------------------
# 生成
# --------------------------------------------------------------------------------------


def load_template(directory: str, name: str) -> Image.Image | None:
    path = os.path.join(directory, name)
    return Image.open(path).convert("RGBA") if os.path.isfile(path) else None


class Kind:
    """ストレージ / 協調処理ユニットのどちらかぶんの下敷き一式。"""

    def __init__(self, prefix: str, mask, directory: str, template_hue: float | None = None):
        self.prefix = prefix
        # 中央 (color) と発光 (light) を「何色で描いてあるか」。base は染めないので関係ない。
        self.template_hue = template_hue
        self.base = load_template(directory, f"{prefix}_base.png") or draw_base()
        self.color = load_template(directory, f"{prefix}_color.png") or draw_color_layer()
        self.light = load_template(directory, f"{prefix}_light.png") or draw_light_layer(mask)
        self.used = [
            f"{prefix}_base.png" if load_template(directory, f"{prefix}_base.png") else "(手続き描画)",
            f"{prefix}_color.png" if load_template(directory, f"{prefix}_color.png") else "(手続き描画)",
            f"{prefix}_light.png" if load_template(directory, f"{prefix}_light.png") else "(手続き描画)",
        ]

    def face(self, color) -> Image.Image:
        # base はそのまま。地に若干色が付いていても染まらないので、
        # 無彩色で描く必要はない (階層に依らない部分はここに描く)。
        img = self.base.copy()
        img.alpha_composite(recolor(self.color, color, self.template_hue))
        return img

    def light_for(self, color) -> tuple[Image.Image, int]:
        img = recolor(self.light, color, self.template_hue)
        frames = max(1, img.height // img.width)
        return img, frames


def write(name: str, image: Image.Image, frames: int) -> None:
    path = os.path.join(OUT_DIR, name)
    image.save(path)
    meta = path + ".mcmeta"
    if frames > 1:
        with open(meta, "w", encoding="utf-8") as fh:
            json.dump(mcmeta(frames), fh, indent=2)
            fh.write("\n")
    elif os.path.exists(meta):
        os.remove(meta)


def dump_templates(directory: str) -> None:
    os.makedirs(directory, exist_ok=True)
    for prefix, mask in (("storage", STORAGE_MASK), ("accelerator", ACCELERATOR_MASK)):
        draw_base().save(os.path.join(directory, f"{prefix}_base.png"))
        draw_color_layer().save(os.path.join(directory, f"{prefix}_color.png"))
        draw_light_layer(mask).save(os.path.join(directory, f"{prefix}_light.png"))
    print(f"下敷きを書き出した -> {directory}\n"
          f"編集して置いておけば次回から使われる (消せば手続き描画に戻る)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--templates", default=TEMPLATE_DIR,
                        help="ベース/色/発光テクスチャを探すディレクトリ")
    parser.add_argument("--dump-templates", action="store_true",
                        help="いまの見た目を編集用の下敷きとして書き出して終了")
    args = parser.parse_args()

    if args.dump_templates:
        dump_templates(args.templates)
        return

    os.makedirs(OUT_DIR, exist_ok=True)
    storage = Kind("storage", STORAGE_MASK, args.templates, STORAGE_TEMPLATE_HUE)
    accelerator = Kind("accelerator", ACCELERATOR_MASK, args.templates, ACCELERATOR_TEMPLATE_HUE)
    print(f"下敷き: storage={storage.used} accelerator={accelerator.used}\n")

    for tier in STORAGE_TIERS:
        color = storage_color(tier)
        write(f"{tier}_storage.png", storage.face(color), 1)
        write(f"{tier}_storage_light.png", *storage.light_for(color))
        print(f"{tier + '_storage':16s} #{color[0]:02x}{color[1]:02x}{color[2]:02x}")
    for tier in ACCELERATOR_TIERS:
        color = accelerator_color(tier)
        write(f"{tier}_accelerator.png", accelerator.face(color), 1)
        write(f"{tier}_accelerator_light.png", *accelerator.light_for(color))
        print(f"{tier + '_accelerator':16s} #{color[0]:02x}{color[1]:02x}{color[2]:02x}")
    print(f"\n-> {OUT_DIR}")


if __name__ == "__main__":
    main()
