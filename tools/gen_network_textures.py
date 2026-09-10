#!/usr/bin/env python3
"""超次元 ME ケーブル / コントローラのテクスチャを AE2 のものから用意する。

    pip install pillow
    python tools/gen_network_textures.py --ae2-jar <applied-energistics-2-*.jar>

コントローラは超特大インターフェイスや特大パターンプロバイダーと同じ方針で、
<b>AE2 の絵の色相を回したもの</b>を出す。回す角度は HUE_SHIFT の 1 箇所だけ。
ケーブルは<b>色を変えずにコピーするだけ</b> (理由は下)。

出力 (src/main/resources/assets/insaneae/textures/):

    part/cable/hyper/<色>.png       ケーブルの手持ちアイテム用 (17 色)。<b>色は加工せずそのままコピー</b>
    part/cable/hyper/channels_*.png ケーブルの使用チャンネル目盛り (同上)
    block/hyper_controller.png             停止中
    block/hyper_controller_powered.png     稼働中 (発光レイヤを焼き込み済み)
    block/hyper_controller_conflicted.png  競合 (同上)

<b>発光レイヤは焼き込む</b>。このプロジェクトは生成物から発光指定
(neoforge_data の block_light) を落とす方針なので、AE2 のように
「本体 + 発光キューブの 2 要素」ではなく 1 枚に合成しておき、
モデル側は cube_all だけで済ませる。AE2 の *_lights.png は縦に並んだ
アニメーション (16x192 = 12 コマ) なので、先頭コマだけを使う。

<b>ケーブルだけは色相を回さず、そのままコピーする。</b>ケーブルは 17 色あり、
色名がそのままアイテム名になっているので、<b>色相を回すと名前と実物がずれる</b>
(「白色の…」が緑になる)。しかも AE2 の CableBuilder は (AECableType, AEColor) の組でしか
テクスチャを引かないため、ワールド上の色は AE2 のもののままで動かせない。
つまり回した瞬間に<b>手持ちとワールドの色も食い違う</b>。

コピー元は<b>高密度</b>スマートケーブルの帯で、これを<b>細い</b>スマートケーブルの形の
アイテムモデルに巻く (ModItemModelProvider)。色は AE2 と 1 ドットも変わらないまま、
模様で AE2 のスマートケーブル (帯が違う) とも高密度ケーブル (太い) とも見分けが付く。

<b>コピーしてあるので、以降は普通に描き換えてよい</b> (このスクリプトを流し直すと
AE2 のものに戻るので、描き換えたらここも直すこと)。
"""

from __future__ import annotations

import argparse
import colorsys
import os
import zipfile

from PIL import Image

# 色相をどれだけ回すか (度)。AE2 の fluix 紫 → 青緑。<b>コントローラにだけ掛ける</b>。
HUE_SHIFT = 160.0

# 超次元 ME ケーブルの帯としてコピーしてくる AE2 のテクスチャ名。
# ファイル名は AEColor の enum 名そのままで、fluix だけ transparent。
CABLE_COLORS = (
    "white", "light_gray", "gray", "black", "lime", "yellow", "orange", "brown",
    "red", "pink", "magenta", "purple", "blue", "light_blue", "cyan", "green",
    "transparent",
)

# 使用チャンネルの目盛り。色に依らない共通の重ねレイヤで、
# <b>細いスマートケーブル側</b>のものを使う (帯だけが高密度、形はスマートなので)。
CABLE_OVERLAYS = ("channels_00", "channels_10")

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "src", "main", "resources", "assets", "insaneae", "textures")


def shift_hue(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    out = Image.new("RGBA", image.size)
    src = image.load()
    dst = out.load()
    delta = (HUE_SHIFT % 360.0) / 360.0
    for y in range(image.size[1]):
        for x in range(image.size[0]):
            r, g, b, a = src[x, y]
            if a == 0:
                dst[x, y] = (0, 0, 0, 0)
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)
            r2, g2, b2 = colorsys.hls_to_rgb((h + delta) % 1.0, l, s)
            dst[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return out


def first_frame(image: Image.Image) -> Image.Image:
    """縦に並んだアニメーションの先頭コマを切り出す (正方形でなければ幅ぶんだけ取る)。"""
    w, h = image.size
    if h > w and h % w == 0:
        return image.crop((0, 0, w, w))
    return image


def load(jar: zipfile.ZipFile, path: str) -> Image.Image:
    with jar.open("assets/ae2/textures/" + path) as handle:
        return Image.open(handle).convert("RGBA").copy()


def save(image: Image.Image, *parts: str) -> None:
    path = os.path.join(OUT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print("wrote", os.path.relpath(path, REPO))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ae2-jar", required=True, help="AE2 の jar (Gradle のキャッシュにある)")
    args = parser.parse_args()

    with zipfile.ZipFile(args.ae2_jar) as jar:
        for name in CABLE_COLORS:
            save(load(jar, "part/cable/dense_smart/%s.png" % name),
                 "part", "cable", "hyper", name + ".png")
        for name in CABLE_OVERLAYS:
            save(load(jar, "part/cable/smart/%s.png" % name),
                 "part", "cable", "hyper", name + ".png")

        offline = load(jar, "block/controller.png")
        powered = load(jar, "block/controller_powered.png")
        lights = first_frame(load(jar, "block/controller_lights.png"))
        conflict = first_frame(load(jar, "block/controller_conflict.png"))

    save(shift_hue(offline), "block", "hyper_controller.png")
    save(shift_hue(Image.alpha_composite(powered, lights)), "block", "hyper_controller_powered.png")
    save(shift_hue(Image.alpha_composite(powered, conflict)), "block", "hyper_controller_conflicted.png")


if __name__ == "__main__":
    main()
