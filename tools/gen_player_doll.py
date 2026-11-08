"""プレイヤーの人形のブロックモデルを、スキン (64x64) から作る。

    python tools/gen_player_doll.py <名前> <スキン.png> [--slim]

出力 (両版共有の src/main/resources/assets/insaneae/):
    textures/block/<名前>.png        スキンをそのまま写したもの
    models/block/<名前>.json         頭・胴・腕・脚と上着レイヤーを半分の大きさで並べたモデル

モデルの正面は北 (-Z)。ブロックステートは datagen の horizontalBlock が向きごとに回す。
スキンは実行時に取りに行かない (オフラインやスキン変更で姿が変わらないように、手元の絵を焼き込む)。
"""
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/insaneae"

# スキン上の位置 u, v と、部位の大きさ 幅w、高さh、奥行きd。単位はスキンのピクセル。
# 右・左はプレイヤー自身から見た向き。
def parts(slim):
    arm = 3 if slim else 4
    return [
        # 名前、本体のu, v、上着のu, v、w, h, d、人形での位置。x, y, zは最小の角で、単位は1/16ブロック
        ("head", (0, 0), (32, 0), (8, 8, 8), (6, 12, 6)),
        ("body", (16, 16), (16, 32), (8, 12, 4), (6, 6, 7)),
        ("right_arm", (40, 16), (40, 32), (arm, 12, 4), (10, 6, 7)),
        ("left_arm", (32, 48), (48, 48), (arm, 12, 4), (6 - arm / 2, 6, 7)),
        ("right_leg", (0, 16), (0, 32), (4, 12, 4), (8, 0, 7)),
        ("left_leg", (16, 48), (0, 48), (4, 12, 4), (6, 0, 7)),
    ]

# 人形はプレイヤーの半分。高さ32pxが16になり、1ブロックに当たる。
SCALE = 0.5
# 上着レイヤーの膨らみ。プレイヤーは頭0.5px、他0.25px。人形の縮尺に合わせる。
INFLATE = {"head": 0.5 * SCALE, "default": 0.25 * SCALE}


def uv(u, v, w, h):
    """スキンのピクセル矩形を、モデルの UV (テクスチャ全体 = 0〜16) に直す。64px なので 1/4。"""
    return [u / 4, v / 4, (u + w) / 4, (v + h) / 4]


def faces(u, v, w, h, d):
    # スキンの展開図: 上段に 上/下、下段に 右/前/左/後ろ。
    # どの面も「外から見て正しい向き」で切り出してあるので、北=前・南=後ろ・東=右・西=左 でそのまま貼れる。
    # 上と下はマイクラの既定の UV 向きと前後・左右が逆なので 180 度回す。
    return {
        "north": {"uv": uv(u + d, v + d, w, h), "texture": "#skin"},
        "south": {"uv": uv(u + 2 * d + w, v + d, w, h), "texture": "#skin"},
        "east": {"uv": uv(u, v + d, d, h), "texture": "#skin"},
        "west": {"uv": uv(u + d + w, v + d, d, h), "texture": "#skin"},
        "up": {"uv": uv(u + d, v, w, d), "texture": "#skin", "rotation": 180},
        "down": {"uv": uv(u + d + w, v, w, d), "texture": "#skin", "rotation": 180},
    }


def element(name, uv_origin, size, pos, inflate):
    w, h, d = size
    x, y, z = pos
    sw, sh, sd = w * SCALE, h * SCALE, d * SCALE
    return {
        "name": name,
        "from": [x - inflate, y - inflate, z - inflate],
        "to": [x + sw + inflate, y + sh + inflate, z + sd + inflate],
        "faces": faces(*uv_origin, w, h, d),
    }


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    slim = "--slim" in sys.argv
    if len(args) != 2:
        print(__doc__)
        sys.exit(2)
    name, skin = args[0], Path(args[1])

    elements = []
    for part, base, overlay, size, pos in parts(slim):
        elements.append(element(part, base, size, pos, 0))
    # 上着レイヤーは本体の後に並べる。透明部分があるのでcutoutで描く。
    for part, base, overlay, size, pos in parts(slim):
        elements.append(element(part + "_overlay", overlay, size, pos, INFLATE.get(part, INFLATE["default"])))

    model = {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"skin": f"insaneae:block/{name}", "particle": f"insaneae:block/{name}"},
        "elements": elements,
    }
    (ASSETS / "models/block").mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)
    out = ASSETS / f"models/block/{name}.json"
    out.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8", newline="\n")
    shutil.copyfile(skin, ASSETS / f"textures/block/{name}.png")
    print("wrote", out)


if __name__ == "__main__":
    main()
