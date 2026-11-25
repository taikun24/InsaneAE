#!/usr/bin/env python3
"""超次元 / 圧縮 / 無制限の ME ケーブルのテクスチャを tools/textures の下敷きから作る。

    pip install pillow
    python tools/gen_network_textures.py

流れは tools/textures → src/main/resources の一方向だけ。

超次元と圧縮は、版ごとの色付きの下敷き (AE2 の高密度スマートケーブルの絵) から作る。

    tools/textures/<版>/cable_<色>.png        帯 17 色
    tools/textures/<版>/cable_core_<色>.png   芯 17 色

超次元は下敷きをそのまま写し、圧縮は枠だけを明るくする (lighten_frame)。
灰色の型紙から色を付け直さないのは、AE2 の絵が色ごとに塗り分けを変えているため。
1.20.1 は色ごとに 6〜11 色を使い分けていて、フルーix (transparent) は灰色と紫を混ぜている。
型紙 1 枚と色の対応表ではこれを再現できず、元の色味が失われる。

無制限は灰色の型紙から作る。超次元と見分けが付くよう、形を描き分けるため。

    tools/textures/cable_insane_band_template.png   帯
    tools/textures/cable_insane_core_template.png   芯

型紙の透明な画素は溝で、目盛りが乗る場所を空けておく。
色は下の PALETTE が決める。型紙の明るさを、色ごとの 5 段の明るさの対応に写す。

出力 (src/main/resources/assets/insaneae/textures/part/cable/<種類>/):

    <色>.png           帯 (17 色)。ファイル名は AEColor の enum 名で、fluix だけ transparent
    core_<色>.png      芯 (17 色)
    channels_*.png     使用チャンネルの目盛り (10 枚)。tools/textures/<版>/cable_channels_*.png
                       を写す。圧縮は帯と同じく枠を明るくし、
                       無制限は型紙の溝の中だけに切り抜く

帯と目盛りは揃えて描くこと。溝と目盛りの位置が食い違うと、アイテムの絵に穴が空く。

手持ちの絵にもワールドの絵にも同じものを使う。手持ちはアイテムモデルが
(帯 + channels_00 + channels_10) を差し、ワールドは {@code CableBusRenderState} の
差し替え (insaneae の client Mixin) が同じ絵に向ける。目盛りは 0〜4 本の 5 段階 ×
奇数/偶数の 2 枚で 10 枚あり、10 枚そろっていないとワールドの目盛りが欠ける。

圧縮は枠だけを明るくする。色を動かすと、色名 (アイテム名) と実物がずれるため。
色の付いた芯には触らないので、17 色の名前はそのまま正しい。
アイテムの絵に出るのは帯のごく一部で、そこは枠が大半を占めるので、枠の明るさで見分けが付く。

色は色相を回さない。色名がアイテム名になっているので、色相を回すと名前と実物がずれる
(「白色の…」が緑になる)。
"""

from __future__ import annotations

import colorsys
import os
import sys

from PIL import Image

# 圧縮ケーブルの枠をどれだけ明るくするか。HLSの明度に足す量
FRAME_LIGHTEN = 0.22

# 「枠」と見なす画素の条件、彩度と明度の上限。色の付いた芯を巻き込まないための線引き
FRAME_MAX_SATURATION = 0.25
FRAME_MAX_LIGHTNESS = 0.35

# 色。ファイル名は AEColor の enum 名そのままで、fluix だけ transparent
CABLE_COLORS = (
    "white", "light_gray", "gray", "black", "lime", "yellow", "orange", "brown",
    "red", "pink", "magenta", "purple", "blue", "light_blue", "cyan", "green",
    "transparent",
)

# 使用チャンネルの目盛り。色にも種類にも依らない重ねレイヤ。奇数側5枚と偶数側5枚
CABLE_OVERLAYS = tuple("channels_%02d" % n for n in (0, 1, 2, 3, 4, 10, 11, 12, 13, 14))

# 色ごとの明るさの対応。下敷きの明るさの範囲を 0〜1 にしたとき、0、1/4、1/2、3/4、1 の
# 位置に置く RGB。(帯, 芯) の順。AE2 の色の見本 (1.21.1 版) から取った値で、無制限だけが使う
PALETTE = {
    "white": (
        [(180, 180, 180), (197, 197, 197), (214, 214, 214), (232, 232, 232), (249, 249, 249)],
        [(180, 180, 180), (199, 199, 199), (218, 218, 218), (236, 236, 236), (255, 255, 255)]),
    "light_gray": (
        [(126, 126, 126), (144, 143, 144), (161, 160, 161), (178, 178, 178), (196, 196, 196)],
        [(126, 126, 126), (152, 151, 152), (178, 178, 178), (205, 205, 205), (231, 231, 231)]),
    "gray": (
        [(79, 79, 79), (96, 95, 96), (113, 112, 113), (131, 129, 131), (148, 146, 148)],
        [(79, 79, 79), (111, 110, 111), (143, 141, 143), (175, 173, 175), (206, 206, 206)]),
    "black": (
        [(19, 19, 19), (29, 29, 29), (39, 39, 39), (49, 49, 49), (59, 59, 59)],
        [(19, 19, 19), (58, 58, 58), (96, 96, 96), (135, 135, 135), (174, 174, 174)]),
    "lime": (
        [(78, 192, 78), (94, 208, 83), (109, 223, 88), (142, 236, 98), (179, 248, 109)],
        [(78, 192, 78), (115, 227, 90), (182, 248, 118), (197, 248, 173), (213, 248, 227)]),
    "yellow": (
        [(255, 207, 64), (255, 220, 81), (253, 233, 97), (248, 244, 112), (244, 255, 128)],
        [(255, 207, 64), (252, 234, 99), (244, 255, 132), (240, 255, 165), (237, 255, 200)]),
    "orange": (
        [(217, 120, 47), (224, 136, 52), (232, 154, 58), (238, 172, 65), (242, 186, 73)],
        [(217, 120, 47), (241, 181, 70), (243, 204, 119), (244, 224, 168), (245, 243, 216)]),
    "brown": (
        [(110, 74, 18), (118, 83, 20), (126, 92, 22), (134, 101, 24), (142, 110, 26)],
        [(110, 74, 18), (156, 130, 53), (183, 169, 105), (210, 208, 156), (237, 247, 208)]),
    "red": (
        [(170, 33, 43), (191, 47, 54), (212, 60, 65), (227, 88, 82), (240, 118, 101)],
        [(170, 33, 43), (210, 59, 63), (238, 113, 98), (243, 174, 157), (247, 234, 216)]),
    "pink": (
        [(216, 110, 170), (231, 127, 177), (245, 143, 183), (254, 166, 194), (251, 202, 213)],
        [(216, 110, 170), (232, 128, 177), (247, 145, 184), (253, 173, 198), (255, 206, 204)]),
    "magenta": (
        [(193, 81, 137), (204, 98, 147), (215, 117, 159), (222, 138, 175), (230, 158, 191)],
        [(193, 81, 137), (211, 109, 154), (225, 143, 180), (239, 175, 196), (255, 206, 204)]),
    "purple": (
        [(110, 92, 184), (132, 93, 197), (150, 96, 208), (163, 104, 214), (176, 111, 221)],
        [(110, 92, 184), (165, 105, 215), (194, 139, 227), (219, 177, 235), (242, 214, 242)]),
    "blue": (
        [(51, 127, 240), (53, 135, 246), (54, 142, 250), (56, 148, 255), (64, 193, 255)],
        [(51, 127, 240), (80, 195, 254), (127, 201, 253), (176, 207, 251), (223, 213, 249)]),
    "light_blue": (
        [(105, 185, 255), (110, 204, 255), (116, 219, 255), (122, 233, 255), (128, 247, 255)],
        [(105, 185, 255), (132, 246, 255), (158, 236, 255), (185, 226, 255), (213, 216, 255)]),
    "cyan": (
        [(34, 176, 174), (43, 196, 180), (60, 211, 188), (81, 222, 194), (101, 232, 201)],
        [(34, 176, 174), (60, 211, 188), (103, 232, 202), (158, 231, 223), (213, 230, 245)]),
    "green": (
        [(7, 155, 107), (16, 171, 108), (26, 187, 106), (38, 202, 93), (50, 216, 80)],
        [(7, 155, 107), (36, 199, 95), (85, 221, 120), (144, 228, 188), (203, 236, 255)]),
    "transparent": (
        [(31, 34, 33), (58, 62, 59), (75, 67, 113), (99, 75, 166), (145, 93, 205)],
        [(31, 34, 33), (85, 90, 85), (142, 145, 142), (198, 200, 198), (255, 255, 255)]),
}

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_crafting_textures import resolve_template_dirs, resource_root  # noqa: E402

OUT = os.path.join(resource_root(REPO), "assets", "insaneae", "textures")
COMMON_TEMPLATES = os.path.join(REPO, "tools", "textures")

TEMPLATE_DIRS = resolve_template_dirs()


def load_template(dirs: list[str], name: str) -> Image.Image:
    """下敷きを 1 枚読む。無ければ、どこに置けばよいかを言って止まる。"""
    for directory in dirs:
        path = os.path.join(directory, name)
        if os.path.isfile(path):
            return Image.open(path).convert("RGBA").copy()
    raise SystemExit(
        "下敷きが無い: %s\n  探した場所: %s"
        % (name, " , ".join(os.path.relpath(d, REPO) for d in dirs)))


def lightness(r: int, g: int, b: int) -> float:
    return max(r, g, b) / 255.0 / 2 + min(r, g, b) / 255.0 / 2


def stop_color(stops: list[tuple[int, int, int]], t: float) -> tuple[int, int, int]:
    """0〜1 の位置 t の色。STOPS の 5 点のあいだを直線で補う。"""
    t = min(1.0, max(0.0, t))
    position = t * (len(stops) - 1)
    index = min(int(position), len(stops) - 2)
    fraction = position - index
    low, high = stops[index], stops[index + 1]
    return tuple(round(low[c] + (high[c] - low[c]) * fraction) for c in range(3))


def colorize(template: Image.Image, stops: list[tuple[int, int, int]]) -> Image.Image:
    """下敷きの明るさを、色ごとの 5 段の対応に写す。透明度は下敷きのまま。"""
    pixels = [(r, g, b) for r, g, b, a in template.getdata() if a > 0]
    lights = [lightness(r, g, b) for r, g, b in pixels]
    low, high = min(lights), max(lights)
    out = Image.new("RGBA", template.size)
    dst = out.load()
    src = template.load()
    width, height = template.size
    for y in range(height):
        for x in range(width):
            r, g, b, a = src[x, y]
            if a == 0:
                dst[x, y] = (0, 0, 0, 0)
                continue
            t = 0.5 if high == low else (lightness(r, g, b) - low) / (high - low)
            rr, gg, bb = stop_color(stops, t)
            dst[x, y] = (rr, gg, bb, a)
    return out


def lighten_frame(image: Image.Image) -> Image.Image:
    """暗くて彩度の低い画素 (金属の枠) だけを明るくする。色の付いた芯は素通し。"""
    out = Image.new("RGBA", image.size)
    src = image.load()
    dst = out.load()
    for y in range(image.size[1]):
        for x in range(image.size[0]):
            r, g, b, a = src[x, y]
            if a == 0:
                dst[x, y] = (0, 0, 0, 0)
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)
            if s > FRAME_MAX_SATURATION or l > FRAME_MAX_LIGHTNESS:
                dst[x, y] = (r, g, b, a)
                continue
            r2, g2, b2 = colorsys.hls_to_rgb(h, min(1.0, l + FRAME_LIGHTEN), s)
            dst[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return out


def clip_to_grooves(overlay: Image.Image, band: Image.Image) -> Image.Image:
    """目盛りを帯の溝 (透明な画素) の中だけに残す。

    目盛りは帯の上に重なるので、溝の幅を型紙で変えても、目盛りが元の幅のままだと
    帯が隠れて見た目が変わらない。目盛りは帯の整数倍の大きさで描かれている。
    """
    scale = overlay.width // band.width
    out = overlay.copy()
    src = band.load()
    dst = out.load()
    for y in range(out.height):
        for x in range(out.width):
            if src[x // scale, y // scale][3] > 0:
                dst[x, y] = (0, 0, 0, 0)
    return out


def save(image: Image.Image, *parts: str) -> None:
    path = os.path.join(OUT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print("wrote", os.path.relpath(path, REPO))


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--templates", default=None,
                        help="下敷きの置き場所を 1 か所に固定する (既定は tools/textures を探す)")
    args = parser.parse_args()

    dirs = [args.templates] if args.templates else TEMPLATE_DIRS

    for color in CABLE_COLORS:
        for prefix in ("", "core_"):
            source = load_template(dirs, "cable_%s%s.png" % (prefix, color))
            save(source, "part", "cable", "hyper", prefix + color + ".png")
            save(lighten_frame(source), "part", "cable", "compressed", prefix + color + ".png")

    band = load_template(dirs, "cable_insane_band_template.png")
    core = load_template(dirs, "cable_insane_core_template.png")
    for color in CABLE_COLORS:
        band_stops, core_stops = PALETTE[color]
        save(colorize(band, band_stops), "part", "cable", "insane", color + ".png")
        save(colorize(core, core_stops), "part", "cable", "insane", "core_" + color + ".png")

    for name in CABLE_OVERLAYS:
        overlay = load_template(dirs, "cable_%s.png" % name)
        save(overlay, "part", "cable", "hyper", name + ".png")
        save(lighten_frame(overlay), "part", "cable", "compressed", name + ".png")
        save(clip_to_grooves(overlay, band), "part", "cable", "insane", name + ".png")

if __name__ == "__main__":
    main()
