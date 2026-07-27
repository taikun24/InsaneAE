#!/usr/bin/env python3
"""ME ストレージセル / セルコンポーネントのアイテムテクスチャを「色」から自動生成する。

    pip install pillow

    python tools/gen_cell_textures.py                  # 生成
    python tools/gen_cell_textures.py --dump-templates # 下敷きを書き出す

階層の色は tools/gen_crafting_textures.py と<b>同じ定義を共有する</b> (STORAGE_BAND_COLORS)。
クラフトストレージのブロックと ME セルで色がずれないよう、色をいじるときは
gen_crafting_textures.py 側だけを書き換えて両方流し直すこと。

出力先は src/main/resources/assets/insaneae/textures/item/ で、階層ごとに次の 3 枚。

    cell_component_<tier>.png                     セルコンポーネント
    cell/standard/storage_cell_<tier>.png         通常セルの階層色レイヤ (窓 + 左面の帯)
    cell/portable/portable_cell_side_<tier>.png   ポータブルセルの側面 (階層色の帯)

見た目は AE2 / MEGA Cells の流儀に合わせてある。**筐体 (ハウジング)・LED・画面は
階層に依らないので MEGA / AE2 のテクスチャをレイヤで重ね、ここで生成するのは
階層で色が変わる部分だけ** ({@code ModItemModelProvider} がレイヤを組む)。
つまり通常セル = MEGA のハウジング + ここで出す色レイヤ + AE2 の LED、
ポータブル = AE2 の筐体 + MEGA の画面 + ここで出す側面 + AE2 の LED。

<b>色レイヤのドット位置は借りているハウジングの窓に合わせてある</b> (CELL_OVERLAY_MASK /
PORTABLE_SIDE_PIXELS)。ハウジングを別のものに差し替えるならマスクも描き直すこと。

--------------------------------------------------------------------------------------
下敷きの差し替え
--------------------------------------------------------------------------------------
tools/textures/ に次の名前で置くと、そちらが優先して使われる (無ければ手続き描画)。

    cell_overlay.png     通常セルの色レイヤ。グレースケールで陰影だけ描く (階層色に染まる)
    portable_side.png    ポータブルセルの側面の帯。グレースケールで陰影だけ描く
    component_p0..p4.png セルコンポーネントの 5 パターン。<b>こちらは色付きのまま置く</b>

コンポーネントだけは色付きのテンプレートで、AE2 の 1k/4k/16k/64k/256k 相当の 5 パターンを
帯の中で循環させる (1g,4g,16g,64g,256g → 1t,4t,... と同じ並び)。染めるときは
「テンプレの主要な色相 → 階層色の色相」の回転になるので、1 枚の中の色の差 (濃い緑の陰など)
はそのまま保たれる。
"""

from __future__ import annotations

import argparse
import colorsys
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_crafting_textures import (  # noqa: E402  (パス調整の後に import する必要がある)
    BAND_MIN_BRIGHTNESS,
    STORAGE_BAND_COLORS,
    STORAGE_TIER_OVERRIDES,
    parse_hex,
    scale,
    recolor,
)

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO, "src/main/resources/assets/insaneae/textures/item")
TEMPLATE_DIR = os.path.join(REPO, "tools/textures")

# --------------------------------------------------------------------------------------
# 階層と色
# --------------------------------------------------------------------------------------

# InsaneCraftingUnitType と同じ並び (セルの階層もこれに一致する)。
CELL_TIERS = ["1g", "4g", "16g", "64g", "256g",
              "1t", "4t", "16t", "64t", "256t",
              "1p", "4p", "16p", "64p", "256p",
              "1e", "4e", "8e"]

# 単色ではなく虹色にする階層。x+y で色相を一周させる (最上段の特別扱い)。
RAINBOW_TIERS = {"8e"}
RAINBOW_BASE = "#df00ff"     # (x+y) == 6 の位置の色
RAINBOW_STEP = -1.0 / 32.0   # x+y が 1 増えるごとに回す色相

SIZE = 16

# --------------------------------------------------------------------------------------
# 手続き描画のパラメータ
# --------------------------------------------------------------------------------------

# 通常セルの色レイヤ。MEGA のハウジング (mega_item/fluid/chemical_cell_housing) の
# 上面に開いた窓と、左面の帯のドットをなぞったマスク。
# 'L' = 明るい方 (階層色そのもの) / 'D' = 暗い方 / '.' = 透明。
# LED (9,12) は AE2 の LED レイヤが乗るので必ず空けておくこと。
CELL_OVERLAY_MASK = [
    "................",
    "................",
    "................",
    "................",
    "........D.......",
    "......DDLD......",
    ".....DLLLLD.....",
    ".L....LLLL......",
    ".LD....L........",
    "..LD............",
    "...LD...........",
    "....LD..........",
    ".....L..........",
    "................",
    "................",
    "................",
]

# ポータブルセルの側面の帯。ae2:item/portable_cell_item_housing の左下の斜面に沿う。
# (暗い方, 明るい方) の順で、AE2 の portable_cell_side_<tier> と同じドットを踏む。
PORTABLE_SIDE_PIXELS = [((1 + i, 6 + i), (1 + i, 7 + i)) for i in range(5)]

# 'D' を 'L' の何倍の明るさにするか (MEGA のセルの 2 階調の比に合わせてある)。
DARK_LEVEL = 0.86

# セルコンポーネントの手続き描画 (テンプレートが 1 枚も無いときのフォールバック)。
# '#' = 階層色 / '+' = 明るい地 / '-' = 暗い地 / '.' = 透明。
COMPONENT_FALLBACK = [
    "......####......",
    "...++-####-++...",
    "..+----##----+..",
    ".+--++-##-++--+.",
    ".+-++--##--++-+.",
    ".+-+-------+-+-.",
    "####---##---####",
    "##--#-####-#--##",
    "##--#-####-#--##",
    "####---##---####",
    ".+-+-------+-+-.",
    ".+-++--##--++-+.",
    ".+--++-##-++--+.",
    "..+----##----+..",
    "...++-####-++...",
    "......####......",
]


def tier_color(tier: str) -> tuple[int, int, int]:
    """階層色。gen_crafting_textures.storage_color と同じ計算 (虹色の階層を除く)。

    帯の中の位置で明度を上げていく。虹色の階層は帯の明度計算から外してあるので、
    ブロック側 (STORAGE_TIERS に 8e を含まない) と色が完全に一致する。
    """
    if tier in STORAGE_TIER_OVERRIDES:
        return parse_hex(STORAGE_TIER_OVERRIDES[tier])
    if tier in RAINBOW_TIERS:
        return parse_hex(RAINBOW_BASE)
    band = tier[-1]
    base = parse_hex(STORAGE_BAND_COLORS[band])
    in_band = [t for t in CELL_TIERS
               if t[-1] == band and t not in STORAGE_TIER_OVERRIDES and t not in RAINBOW_TIERS]
    pos = in_band.index(tier) / max(1, len(in_band) - 1)
    return scale(base, BAND_MIN_BRIGHTNESS + (1.0 - BAND_MIN_BRIGHTNESS) * pos)


def pattern_index(tier: str) -> int:
    """コンポーネントのパターン番号。帯の中で 5 パターンを循環させる。"""
    band = tier[-1]
    in_band = [t for t in CELL_TIERS if t[-1] == band]
    return in_band.index(tier) % 5


# --------------------------------------------------------------------------------------
# 色ユーティリティ
# --------------------------------------------------------------------------------------


def dominant_hue(image: Image.Image, min_sat: float = 0.15) -> tuple[float, float]:
    """テンプレートの「主要な色相」と、その色の中の最大明度を返す。

    彩度のある画素だけを見て、一番多い色相をテンプレートの基準色とする。
    """
    counts: dict[int, int] = {}
    peak = 0.0
    for r, g, b, a in image.getdata():
        if a == 0:
            continue
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        if s < min_sat:
            continue
        counts[round(h * 72) % 72] = counts.get(round(h * 72) % 72, 0) + 1
        peak = max(peak, v)
    if not counts:
        return 0.0, 1.0
    return max(counts.items(), key=lambda kv: kv[1])[0] / 72.0, peak or 1.0


def hue_rotate(template: Image.Image, target, rainbow: bool = False,
               min_sat: float = 0.15, step: float | None = None) -> Image.Image:
    """色付きテンプレートを階層色に回す。彩度の無い画素 (筐体のグレー) は触らない。

    「テンプレの主要色相 → target の色相」の回転なので、1 枚の中の色相差
    (濃い緑の陰など) は相対関係のまま保たれる。明度はテンプレの最大明度が
    target の明度になるよう比率で合わせる。
    """
    src = template.convert("RGBA")
    ref_h, ref_v = dominant_hue(src, min_sat)
    tgt_h, tgt_s, tgt_v = colorsys.rgb_to_hsv(*[c / 255 for c in target])
    delta = tgt_h - ref_h
    gain = tgt_v / ref_v if ref_v > 0 else 1.0

    out = []
    width = src.width
    for i, (r, g, b, a) in enumerate(src.getdata()):
        if a == 0:
            out.append((r, g, b, a))
            continue
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        if s < min_sat:
            out.append((r, g, b, a))       # 地のグレーはそのまま
            continue
        shift = delta
        if rainbow:
            x, y = i % width, i // width
            shift += (RAINBOW_STEP if step is None else step) * (x + y - 6)
        nr, ng, nb = colorsys.hsv_to_rgb((h + shift) % 1.0, s, min(1.0, v * gain))
        out.append((round(nr * 255), round(ng * 255), round(nb * 255), a))

    result = Image.new("RGBA", src.size)
    result.putdata(out)
    return result


def rainbow_recolor(template: Image.Image, target, step: float | None = None) -> Image.Image:
    """グレースケールのテンプレートを (x+y) で色相を振りながら染める。

    step は「(x+y) が 1 増えるごとに回す色相」。光る部分が狭い絵ほど大きくしないと
    虹に見えないので、呼び出し側で指定できるようにしてある。
    """
    base = recolor(template, target)
    return hue_rotate(base, target, rainbow=True, min_sat=0.05, step=step)


def paint(template: Image.Image, tier: str, color) -> Image.Image:
    """グレースケールのテンプレートを階層色に染める (虹色の階層も面倒を見る)。"""
    return rainbow_recolor(template, color) if tier in RAINBOW_TIERS else recolor(template, color)


# --------------------------------------------------------------------------------------
# 手続き描画 (テンプレートが無いときのフォールバック / --dump-templates の中身)
# --------------------------------------------------------------------------------------


def grey(level: float) -> tuple[int, int, int, int]:
    value = max(0, min(255, round(level * 255)))
    return (value, value, value, 255)


def draw_cell_overlay() -> Image.Image:
    """通常セルの色レイヤ。ハウジングの窓と左面の帯をなぞった 2 階調のグレースケール。"""
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y, row in enumerate(CELL_OVERLAY_MASK):
        for x, ch in enumerate(row):
            if ch == "L":
                px[x, y] = grey(1.0)      # 一番明るい画素 = 階層色そのものになる
            elif ch == "D":
                px[x, y] = grey(DARK_LEVEL)
    return img


def draw_portable_side() -> Image.Image:
    """ポータブルセルの側面の帯。上側の 1 ドットを陰にした 2 段のグレースケール。"""
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for dark, light in PORTABLE_SIDE_PIXELS:
        px[dark] = grey(0.72)
        px[light] = grey(1.0)
    return img


def draw_component_fallback() -> Image.Image:
    """コンポーネントの下敷きが 1 枚も無いときの手続き描画。"""
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y, row in enumerate(COMPONENT_FALLBACK):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch == "#":
                px[x, y] = (0xff, 0x42, 0xc6, 255)   # 染める対象 (彩度のある画素)
            elif ch == "+":
                px[x, y] = grey(0.82)
            else:
                px[x, y] = grey(0.38)
    return img


# --------------------------------------------------------------------------------------
# 生成
# --------------------------------------------------------------------------------------


def load_template(directory: str, name: str) -> Image.Image | None:
    path = os.path.join(directory, name)
    return Image.open(path).convert("RGBA") if os.path.isfile(path) else None


class Templates:
    def __init__(self, directory: str):
        self.used: list[str] = []
        self.overlay = self._pick(directory, "cell_overlay.png", draw_cell_overlay)
        self.side = self._pick(directory, "portable_side.png", draw_portable_side)
        self.components = [
            self._pick(directory, f"component_p{i}.png", draw_component_fallback)
            for i in range(5)
        ]

    def _pick(self, directory: str, name: str, fallback) -> Image.Image:
        image = load_template(directory, name)
        self.used.append(name if image is not None else f"{name} (手続き描画)")
        return image if image is not None else fallback()

    def cell(self, tier: str, color) -> Image.Image:
        return paint(self.overlay, tier, color)

    def component(self, tier: str, color) -> Image.Image:
        return hue_rotate(self.components[pattern_index(tier)], color,
                          rainbow=tier in RAINBOW_TIERS)

    def portable_side(self, tier: str, color) -> Image.Image:
        return paint(self.side, tier, color)


def write(relative: str, image: Image.Image) -> None:
    path = os.path.join(OUT_DIR, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)


def dump_templates(directory: str) -> None:
    os.makedirs(directory, exist_ok=True)
    draw_cell_overlay().save(os.path.join(directory, "cell_overlay.png"))
    draw_portable_side().save(os.path.join(directory, "portable_side.png"))
    for i in range(5):
        path = os.path.join(directory, f"component_p{i}.png")
        if not os.path.isfile(path):        # 既にある 5 パターンは潰さない
            draw_component_fallback().save(path)
    print(f"下敷きを書き出した -> {directory}\n"
          f"編集して置いておけば次回から使われる (消せば手続き描画に戻る)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--templates", default=TEMPLATE_DIR,
                        help="下敷きを探すディレクトリ")
    parser.add_argument("--dump-templates", action="store_true",
                        help="いまの見た目を編集用の下敷きとして書き出して終了")
    args = parser.parse_args()

    if args.dump_templates:
        dump_templates(args.templates)
        return

    templates = Templates(args.templates)
    print(f"下敷き: {templates.used}\n")

    for tier in CELL_TIERS:
        color = tier_color(tier)
        write(f"cell_component_{tier}.png", templates.component(tier, color))
        write(f"cell/standard/storage_cell_{tier}.png", templates.cell(tier, color))
        write(f"cell/portable/portable_cell_side_{tier}.png",
              templates.portable_side(tier, color))
        mark = " (虹)" if tier in RAINBOW_TIERS else ""
        print(f"{tier:>5s}  #{color[0]:02x}{color[1]:02x}{color[2]:02x}"
              f"  pattern={pattern_index(tier)}{mark}")
    print(f"\n{len(CELL_TIERS) * 3} 枚 -> {OUT_DIR}")


if __name__ == "__main__":
    main()
