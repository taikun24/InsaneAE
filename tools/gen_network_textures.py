#!/usr/bin/env python3
"""超次元 ME ケーブル / コントローラのテクスチャを tools/textures の下敷きから作る。

    pip install pillow

    python tools/gen_network_textures.py                     # 生成
    python tools/gen_network_textures.py --seed <ae2.jar>    # 下敷きを AE2 の jar から用意する

コントローラは超特大インターフェイスや特大パターンプロバイダーと同じ方針で、
<b>下敷きの色相を回したもの</b>を出す。回す角度は HUE_SHIFT の 1 箇所だけ。
ケーブルは<b>色を変えずにコピーするだけ</b> (理由は下)。

出力 (src/main/resources/assets/insaneae/textures/):

    part/cable/hyper/<色>.png       ケーブルの手持ちアイテム用 (17 色)。<b>色は加工せずそのままコピー</b>
    part/cable/hyper/channels_*.png ケーブルの使用チャンネル目盛り (同上)
    block/hyper_controller.png             停止中
    block/hyper_controller_powered.png     稼働中 (発光レイヤを焼き込み済み)
    block/hyper_controller_conflicted.png  競合 (同上)

--------------------------------------------------------------------------------------
下敷きの置き場所 (MC バージョンごとに分けられる)
--------------------------------------------------------------------------------------
他の生成スクリプトと同じで、次の順に<b>ファイル単位で</b>探す。gradle.properties の
minecraft_version を読むので、チェックアウトしているブランチに合ったものが選ばれる。

    1. tools/textures/<minecraft_version>/   そのバージョン専用の絵
    2. tools/textures/                       バージョン共通の絵

<b>下敷きはリポジトリに入っているので、普通に描き換えてよい。</b>
流し直しても AE2 の絵には戻らない (戻したいときだけ --seed を使う)。
`--templates <dir>` を渡すとその 1 か所だけを見る。

    cable_<色>.png          ケーブルの帯 17 色。ファイル名は AEColor の enum 名
                            そのままで、fluix だけ transparent
    cable_channels_00.png   使用チャンネルの目盛り (色に依らない重ねレイヤ)
    cable_channels_10.png
    controller.png          コントローラ 停止中
    controller_powered.png  コントローラ 稼働中の本体
    controller_lights.png   稼働中の発光レイヤ
    controller_conflict.png 競合時の発光レイヤ

--------------------------------------------------------------------------------------
知っておくこと
--------------------------------------------------------------------------------------
<b>発光レイヤは焼き込む</b>。このプロジェクトは生成物から発光指定
(neoforge_data の block_light) を落とす方針なので、AE2 のように
「本体 + 発光キューブの 2 要素」ではなく 1 枚に合成しておき、
モデル側は cube_all だけで済ませる。発光レイヤが縦に並んだアニメーション
(16x192 = 12 コマ) のときは先頭コマだけを使う。

<b>ケーブルだけは色相を回さず、そのままコピーする。</b>ケーブルは 17 色あり、
色名がそのままアイテム名になっているので、<b>色相を回すと名前と実物がずれる</b>
(「白色の…」が緑になる)。しかも AE2 の CableBuilder は (AECableType, AEColor) の組でしか
テクスチャを引かないため、ワールド上の色は AE2 のもののままで動かせない。
つまり回した瞬間に<b>手持ちとワールドの色も食い違う</b>。

--seed が持ってくるのは<b>高密度</b>スマートケーブルの帯で、これを<b>細い</b>
スマートケーブルの形のアイテムモデルに巻く (ModItemModelProvider)。色は AE2 と
1 ドットも変わらないまま、模様で AE2 のスマートケーブル (帯が違う) とも
高密度ケーブル (太い) とも見分けが付く。

<b>帯と目盛りは同じ一族から取ること。</b>AE2 の帯は目盛りが乗る溝を透明のまま
空けてあり、埋められるのは同じ一族の目盛りだけ。細いスマート側の目盛りは溝の位置が
違うので、混ぜると<b>アイテムの絵に穴が空く</b> (手持ちだけ軸の線が抜けて見える。
ワールド側は AE2 のケーブルの絵なので出ない)。
"""

from __future__ import annotations

import argparse
import colorsys
import os
import zipfile

from PIL import Image

# 色相をどれだけ回すか (度)。AE2 の fluix 紫 → 青緑。<b>コントローラにだけ掛ける</b>。
HUE_SHIFT = 160.0

# 超次元 ME ケーブルの帯の色。
# ファイル名は AEColor の enum 名そのままで、fluix だけ transparent。
CABLE_COLORS = (
    "white", "light_gray", "gray", "black", "lime", "yellow", "orange", "brown",
    "red", "pink", "magenta", "purple", "blue", "light_blue", "cyan", "green",
    "transparent",
)

# 使用チャンネルの目盛り。色に依らない共通の重ねレイヤ。
CABLE_OVERLAYS = ("channels_00", "channels_10")

# コントローラの下敷き (下敷きの名前 → 出力での役割はコードのほうを参照)。
CONTROLLER_TEMPLATES = ("controller.png", "controller_powered.png",
                        "controller_lights.png", "controller_conflict.png")

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "src", "main", "resources", "assets", "insaneae", "textures")

# --seed で AE2 の jar から持ってくるときの対応表 (下敷きの名前 → jar の中のパス)。
# <b>帯も目盛りも高密度スマート側</b>から取る (上の「知っておくこと」を参照)。
SEED_SOURCES = {
    **{"cable_%s.png" % name: "part/cable/dense_smart/%s.png" % name
       for name in CABLE_COLORS + CABLE_OVERLAYS},
    **{name: "block/" + name for name in CONTROLLER_TEMPLATES},
}


def read_minecraft_version(repo: str = REPO) -> str | None:
    """gradle.properties の minecraft_version を読む。読めなければ None。"""
    try:
        with open(os.path.join(repo, "gradle.properties"), encoding="utf-8") as fh:
            for line in fh:
                key, sep, value = line.partition("=")
                if sep and key.strip() == "minecraft_version":
                    return value.strip() or None
    except OSError:
        pass
    return None


def resolve_template_dirs(repo: str = REPO) -> list[str]:
    """下敷きを探す順番。<b>ファイル単位</b>で前から順に見る (他の生成スクリプトと同じ)。"""
    root = os.path.join(repo, "tools", "textures")
    version = read_minecraft_version(repo)
    if version:
        versioned = os.path.join(root, version)
        if os.path.isdir(versioned):
            return [versioned, root]
    return [root]


TEMPLATE_DIRS = resolve_template_dirs()


def load_template(dirs: list[str], name: str) -> Image.Image:
    """下敷きを 1 枚読む。無ければ、どうやって用意するかを言って止まる。"""
    for directory in dirs:
        path = os.path.join(directory, name)
        if os.path.isfile(path):
            return Image.open(path).convert("RGBA").copy()
    raise SystemExit(
        "下敷きが無い: %s\n"
        "  探した場所: %s\n"
        "  AE2 の絵から用意するなら: python tools/gen_network_textures.py --seed <ae2.jar>"
        % (name, " , ".join(os.path.relpath(d, REPO) for d in dirs)))


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


def save(image: Image.Image, *parts: str) -> None:
    path = os.path.join(OUT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print("wrote", os.path.relpath(path, REPO))


def seed(jar_path: str, dirs: list[str]) -> None:
    """AE2 の jar から下敷きを用意する。書き出し先は探索パスの先頭 (一番具体的なところ)。"""
    target = dirs[0]
    os.makedirs(target, exist_ok=True)
    with zipfile.ZipFile(jar_path) as jar:
        for name, path in SEED_SOURCES.items():
            with jar.open("assets/ae2/textures/" + path) as handle:
                image = Image.open(handle).convert("RGBA").copy()
            image.save(os.path.join(target, name))
            print("seeded", os.path.relpath(os.path.join(target, name), REPO))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--templates", default=None,
                        help="下敷きの置き場所を 1 か所に固定する (既定は tools/textures を探す)")
    parser.add_argument("--seed", metavar="AE2_JAR",
                        help="下敷きを AE2 の jar から用意し直す (描き換えたものは上書きされる)")
    args = parser.parse_args()

    dirs = [args.templates] if args.templates else TEMPLATE_DIRS
    if args.seed:
        seed(args.seed, dirs)

    for name in CABLE_COLORS + CABLE_OVERLAYS:
        save(load_template(dirs, "cable_%s.png" % name),
             "part", "cable", "hyper", name + ".png")

    offline = load_template(dirs, "controller.png")
    powered = load_template(dirs, "controller_powered.png")
    lights = first_frame(load_template(dirs, "controller_lights.png"))
    conflict = first_frame(load_template(dirs, "controller_conflict.png"))

    save(shift_hue(offline), "block", "hyper_controller.png")
    save(shift_hue(Image.alpha_composite(powered, lights)), "block", "hyper_controller_powered.png")
    save(shift_hue(Image.alpha_composite(powered, conflict)), "block", "hyper_controller_conflicted.png")


if __name__ == "__main__":
    main()
