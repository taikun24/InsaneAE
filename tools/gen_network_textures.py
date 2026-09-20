#!/usr/bin/env python3
"""超次元 ME ケーブルのテクスチャを tools/textures の下敷きから作る。

    pip install pillow
    python tools/gen_network_textures.py

<b>流れは tools/textures → src/main/resources の一方向だけ。</b>
下敷きを描き換えて流し直せば、そのまま Mod のリソースに入る。

ケーブルは<b>色を変えずにコピーするだけ</b> (理由は下)。

出力 (src/main/resources/assets/insaneae/textures/):

    part/cable/hyper/<色>.png       ケーブルの手持ちアイテム用 (17 色)。<b>色は加工せずそのままコピー</b>
    part/cable/hyper/channels_*.png ケーブルの使用チャンネル目盛り (同上)

--------------------------------------------------------------------------------------
下敷きの置き場所 (MC バージョンごとに分けられる)
--------------------------------------------------------------------------------------
他の生成スクリプトと同じで、次の順に<b>ファイル単位で</b>探す。gradle.properties の
minecraft_version を読むので、チェックアウトしているブランチに合ったものが選ばれる。

    1. tools/textures/<minecraft_version>/   そのバージョン専用の絵
    2. tools/textures/                       バージョン共通の絵

`--templates <dir>` を渡すとその 1 か所だけを見る。

    cable_<色>.png          ケーブルの帯 17 色。ファイル名は AEColor の enum 名
                            そのままで、fluix だけ transparent
    cable_channels_00.png   使用チャンネルの目盛り (色に依らない重ねレイヤ)
    cable_channels_10.png

--------------------------------------------------------------------------------------
知っておくこと
--------------------------------------------------------------------------------------
<b>ケーブルは色相を回さない。</b>ケーブルは 17 色あり、色名がそのまま
アイテム名になっているので、<b>色相を回すと名前と実物がずれる</b>
(「白色の…」が緑になる)。しかも AE2 の CableBuilder は (AECableType, AEColor) の組でしか
テクスチャを引かないため、ワールド上の色は AE2 のもののままで動かせない。
つまり回した瞬間に<b>手持ちとワールドの色も食い違う</b>。

今の下敷きは AE2 の<b>高密度</b>スマートケーブルの帯で、これを<b>細い</b>
スマートケーブルの形のアイテムモデルに巻いている (ModItemModelProvider)。

<b>帯 (cable_<色>) と目盛り (cable_channels_*) は揃えて描くこと。</b>今の下敷きは
目盛りが乗る溝を透明のまま空けてあり、埋めるのは目盛りの側。溝と目盛りの位置が
食い違うと<b>アイテムの絵に穴が空く</b> (手持ちだけ軸の線が抜けて見える。
ワールド側は AE2 のケーブルの絵を使うので出ない)。
"""

from __future__ import annotations

import argparse
import os

from PIL import Image

# 超次元 ME ケーブルの帯の色。
# ファイル名は AEColor の enum 名そのままで、fluix だけ transparent。
CABLE_COLORS = (
    "white", "light_gray", "gray", "black", "lime", "yellow", "orange", "brown",
    "red", "pink", "magenta", "purple", "blue", "light_blue", "cyan", "green",
    "transparent",
)

# 使用チャンネルの目盛り。色に依らない共通の重ねレイヤ。
CABLE_OVERLAYS = ("channels_00", "channels_10")

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "src", "main", "resources", "assets", "insaneae", "textures")


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
    """下敷きを 1 枚読む。無ければ、どこに置けばよいかを言って止まる。"""
    for directory in dirs:
        path = os.path.join(directory, name)
        if os.path.isfile(path):
            return Image.open(path).convert("RGBA").copy()
    raise SystemExit(
        "下敷きが無い: %s\n  探した場所: %s"
        % (name, " , ".join(os.path.relpath(d, REPO) for d in dirs)))


def save(image: Image.Image, *parts: str) -> None:
    path = os.path.join(OUT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print("wrote", os.path.relpath(path, REPO))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--templates", default=None,
                        help="下敷きの置き場所を 1 か所に固定する (既定は tools/textures を探す)")
    args = parser.parse_args()

    dirs = [args.templates] if args.templates else TEMPLATE_DIRS

    for name in CABLE_COLORS + CABLE_OVERLAYS:
        save(load_template(dirs, "cable_%s.png" % name),
             "part", "cable", "hyper", name + ".png")



if __name__ == "__main__":
    main()
