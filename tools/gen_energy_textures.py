#!/usr/bin/env python3
"""エネルギーセルのブロックテクスチャを「階層の色」から自動生成する。

    pip install pillow

    python tools/gen_energy_textures.py                  # 生成
    python tools/gen_energy_textures.py --dump-templates # 下敷きを書き出す

出力先は src/main/resources/assets/insaneae/textures/block/energy/ で、
階層ごとに次の 6 枚 (13 階層 × 6 = 78 枚)。

    <tier>_energy_cell.png              筐体 + 階層色 (<b>残量に依らず常に出る</b>)
    <tier>_energy_cell_<fullness>_light.png  発光レイヤ (残量ぶんの段だけ・透過)

つまり<b>ベース (筐体) + 色ベース (常時) + ライト (充電ぶん)</b> の 3 層で、
前 2 つを焼き込んだものが 1 枚目、3 つめが 2 枚目にあたる。ブロックモデルは
{@code ModBlockStateProvider} が「筐体キューブ + ひとまわり大きい発光キューブ
(emissivity 15)」の 2 要素で組むので、<b>光る部分は暗所でも光る</b>。

見た目は AE2 のエネルギーセルと同じ考え方で、中央のコアから十字に伸びる 4 段の
セグメントが残量ぶんだけ光る。消灯中の段も階層色で(暗く)描いてあるので、
空っぽでも何階層のセルかは見て分かる。<b>階層の色は 1 段ごとに色相を回した
虹のグラデーション</b>で、最上段 (cosmic) だけはドットごとに色相を一周させた虹色になる。

--------------------------------------------------------------------------------------
下敷きの差し替え
--------------------------------------------------------------------------------------
tools/textures/ に次の名前で置くと、そちらが優先して使われる (無ければ手続き描画)。

    energy_base.png    筐体。<b>そのまま</b>使われる
    energy_color.png   常時出る階層色のレイヤ (省略可 — 下記)
    energy_light.png   発光レイヤ。残量で点く部分を全部描いておく

<b>energy_color.png を置かなかった場合は energy_base.png から自動で切り出す</b>。
彩度のある画素 (AE2 のエネルギーセルなら水色の結晶パネル) を色レイヤとして抜き出し、
残りのグレーだけを筐体として使う。つまり<b>置いた下敷きが AE2 のセルそのものでも、
結晶の部分だけが階層色に染まる</b>。色付きの下敷きは色相を回すだけなので、
淡い色合いはそのまま保たれる (グレースケールで置いた場合は明度から染める)。

発光レイヤの「何段目のセグメントか」は<b>中心からの距離 (リング) で自動的に決まる</b>。
中心の 2x2 (リング 0) に描いた画素は常時点灯、そこから外に向かって 1 段ずつ
残量 1,2,3,4 に対応する。段数が残量の最大値と合わないときは警告を出す。
"""

from __future__ import annotations

import argparse
import colorsys
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_crafting_textures import (hue_rotate, load_template, rainbow_recolor,
                                   read_minecraft_version, recolor,
                                   resolve_template_dirs, write_dir)  # noqa: E402  (パス調整の後に import する)

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO, "src/main/resources/assets/insaneae/textures/block/energy")
TEMPLATE_DIRS = resolve_template_dirs(REPO)

# --------------------------------------------------------------------------------------
# 階層と色 — ここだけ触れば見た目が変わる
# --------------------------------------------------------------------------------------

# InsaneEnergyCellTier と同じ並び。ファイル名は "<tier>_energy_cell_<残量>.png"。
ENERGY_TIERS = ["hyperdense", "ultradense", "neutron", "degenerate", "collapsar",
                "singularity", "pulsar", "quasar", "nova", "supernova",
                "hypernova", "galactic", "cosmic"]

# 最上段はドットごとに色相を回す虹色にする。
RAINBOW_TIERS = {"cosmic"}

# 階層の色 = 色相を HUE_START から HUE_END まで均等に回したもの。
# MEGA の Superdense (白) の続きなので、シアン → 青 → 紫 → ピンクと「熱く」していく。
HUE_START = 0.50
HUE_END = 0.92
SATURATION = 0.82
VALUE = 1.00

# 虹色の階層の基準色と、(x+y) 1 ドットあたりに回す色相。
# 光るのは十字の部分だけ (対角で 16 ドットぶん) なので、そこで一周させる。
RAINBOW_BASE = "#00e5ff"
RAINBOW_STEP = -1.0 / 16.0

MAX_FULLNESS = 4          # EnergyCellBlock.MAX_FULLNESS
SIZE = 16

# --------------------------------------------------------------------------------------
# 手続き描画のパラメータ
# --------------------------------------------------------------------------------------

# 光る部分の段。'0' = コア (常時点灯) / '1'〜'4' = 残量で点くセグメント / '.' = 光らない。
# 残量 f のとき '0'〜'f' が点く (f=0 でもコアだけは光る)。
SEGMENT_MASK = [
    "................",
    "................",
    "................",
    ".......44.......",
    ".......33.......",
    ".......22.......",
    ".......11.......",
    "...4321001234...",
    "...4321001234...",
    ".......11.......",
    ".......22.......",
    ".......33.......",
    ".......44.......",
    "................",
    "................",
    "................",
]

EDGE = (0x14, 0x14, 0x14)          # 一番外側の枠
BEVEL_LIGHT = (0x5a, 0x5a, 0x5a)   # 上/左のハイライト
BEVEL_DARK = (0x24, 0x24, 0x24)    # 下/右の影
CHASSIS = (0x3a, 0x3a, 0x3a)       # 筐体の地
RECESS = (0x1e, 0x1e, 0x1e)        # 光る部分の落とし込み
RIVET = (0x4f, 0x4f, 0x4f)         # 四隅のリベット

# 下敷きから色レイヤを切り出すときの彩度のしきい値。これ以上を「色」と見なす。
COLOR_SATURATION = 0.12


def tier_color(tier: str) -> tuple[int, int, int]:
    """階層の色。色相を 1 段ずつ回す (虹色の階層は基準色を返す)。"""
    if tier in RAINBOW_TIERS:
        return parse_hex(RAINBOW_BASE)
    ramp = [t for t in ENERGY_TIERS if t not in RAINBOW_TIERS]
    pos = ramp.index(tier) / max(1, len(ramp) - 1)
    hue = HUE_START + (HUE_END - HUE_START) * pos
    return tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, SATURATION, VALUE))


def parse_hex(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def segment_at(x: int, y: int) -> int | None:
    """手続き描画用。その画素が何段目か (0 = コア)。光らない画素なら None。"""
    ch = SEGMENT_MASK[y][x]
    return None if ch == "." else int(ch)


def ring(x: int, y: int) -> int:
    """中心 (7.5, 7.5) からのリング番号。中央の 2x2 が 0、そこから外へ 1, 2, ...。"""
    return round(max(abs(x - 7.5), abs(y - 7.5)) - 0.5)


def segment_rings(light: Image.Image) -> dict[int, int]:
    """発光レイヤに描かれているリング → 段番号 の対応を作る。

    中心の 2x2 (リング 0) はコア = 段 0 (常時点灯) 扱い。それ以外は内側から順に
    段 1, 2, 3... を振る。つまり<b>下敷きの絵がそのまま段の定義になる</b>。
    """
    px = light.load()
    rings = sorted({ring(x, y) for y in range(SIZE) for x in range(SIZE) if px[x, y][3] > 0})
    mapping: dict[int, int] = {}
    step = 0
    for r in rings:
        if r == 0:
            mapping[r] = 0          # コア
        else:
            step += 1
            mapping[r] = step
    if step != MAX_FULLNESS:
        print(f"警告: 発光レイヤの段数が {step} 段。残量は 0〜{MAX_FULLNESS} なので "
              f"{MAX_FULLNESS} 段で描くこと (多い/少ない段は端の残量に丸められる)")
    return mapping


def is_greyscale(image: Image.Image, threshold: float = 0.05) -> bool:
    """下敷きが「陰影だけ」かどうか。色が付いていれば色相を回す方で染める。"""
    for r, g, b, a in image.getdata():
        if a == 0:
            continue
        if colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[1] >= threshold:
            return False
    return True


def split_color(base: Image.Image) -> tuple[Image.Image, Image.Image | None]:
    """筐体の下敷きを「グレーの地」と「彩度のある部分 (= 色レイヤ)」に分ける。"""
    plain = Image.new("RGBA", base.size)
    color = Image.new("RGBA", base.size)
    plain_px, color_px = plain.load(), color.load()
    src = base.load()
    found = False
    for y in range(base.height):
        for x in range(base.width):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            if colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[1] >= COLOR_SATURATION:
                color_px[x, y] = (r, g, b, a)
                found = True
            else:
                plain_px[x, y] = (r, g, b, a)
    return (plain, color) if found else (base, None)


# --------------------------------------------------------------------------------------
# 手続き描画 (テンプレートが無いときのフォールバック / --dump-templates の中身)
# --------------------------------------------------------------------------------------


def draw_base() -> Image.Image:
    """筐体。光る部分は色レイヤに任せて透明のまま空けておく。"""
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if segment_at(x, y) is not None:
                continue
            if x in (0, SIZE - 1) or y in (0, SIZE - 1):
                rgb = EDGE
            elif x == 1 or y == 1:
                rgb = BEVEL_LIGHT
            elif x == SIZE - 2 or y == SIZE - 2:
                rgb = BEVEL_DARK
            elif _next_to_segment(x, y):
                rgb = RECESS               # 光る部分のまわりの落とし込み
            elif (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
                rgb = RIVET                # 四隅のリベット
            else:
                rgb = CHASSIS if (x + y) % 2 else tuple(c + 5 for c in CHASSIS)
            px[x, y] = (*rgb, 255)
    return img


def _next_to_segment(x: int, y: int) -> bool:
    return any(0 <= x + dx < SIZE and 0 <= y + dy < SIZE
               and segment_at(x + dx, y + dy) is not None
               for dx in (-1, 0, 1) for dy in (-1, 0, 1))


def draw_light() -> Image.Image:
    """発光レイヤ。コアが一番明るく、外の段ほど少し暗いグレースケール。"""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            segment = segment_at(x, y)
            if segment is None:
                continue
            # コア (0) を 255 にしておくこと。グレースケールの下敷きは明度しか持てないので、
            # 一番明るい画素が階層色そのものになる。
            level = 255 - segment * 26
            px[x, y] = (level, level, level, 255)
    return img


def draw_color() -> Image.Image:
    """常時出る色レイヤ。形は発光レイヤと同じで、少し落ち着いた陰影にしてある。"""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            segment = segment_at(x, y)
            if segment is None:
                continue
            level = 255 - segment * 18
            if _next_to_chassis(x, y):
                level -= 34            # 縁は筐体に馴染ませる
            px[x, y] = (level, level, level, 255)
    return img


def _next_to_chassis(x: int, y: int) -> bool:
    return any(not (0 <= x + dx < SIZE and 0 <= y + dy < SIZE)
               or segment_at(x + dx, y + dy) is None
               for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))


# --------------------------------------------------------------------------------------
# 生成
# --------------------------------------------------------------------------------------


def paint(template: Image.Image, tier: str, color) -> Image.Image:
    """テンプレートを階層色に染める (虹色の階層も面倒を見る)。

    グレースケールの下敷きは明度から染め、色付きの下敷きは色相を回すだけにする
    (淡い色合いを潰さないため)。
    """
    rainbow = tier in RAINBOW_TIERS
    if is_greyscale(template):
        return rainbow_recolor(template, color, RAINBOW_STEP) if rainbow \
            else recolor(template, color)
    return hue_rotate(template, color, rainbow=rainbow, step=RAINBOW_STEP)


def masked(light: Image.Image, fullness: int, rings: dict[int, int]) -> Image.Image:
    """発光レイヤのうち、残量ぶんの段だけを残す。"""
    out = Image.new("RGBA", light.size)
    src = light.load()
    dst = out.load()
    for y in range(SIZE):
        for x in range(SIZE):
            segment = rings.get(ring(x, y))
            if src[x, y][3] > 0 and segment is not None and segment <= fullness:
                dst[x, y] = src[x, y]
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--templates", default=TEMPLATE_DIRS, help="下敷きを探すディレクトリ")
    parser.add_argument("--dump-templates", action="store_true",
                        help="いまの見た目を編集用の下敷きとして書き出して終了")
    args = parser.parse_args()

    # どの下敷きを使ったのか毎回出す (バージョンごとに分けていると取り違えやすいので)。
    print(f"下敷きディレクトリ: {args.templates}"
          f"  (gradle.properties の minecraft_version = {read_minecraft_version() or '不明'})")

    if args.dump_templates:
        out = write_dir(args.templates)
        os.makedirs(out, exist_ok=True)
        draw_base().save(os.path.join(out, "energy_base.png"))
        draw_color().save(os.path.join(out, "energy_color.png"))
        draw_light().save(os.path.join(out, "energy_light.png"))
        print(f"下敷きを書き出した -> {out}")
        return

    base = load_template(args.templates, "energy_base.png")
    color_layer = load_template(args.templates, "energy_color.png")
    light = load_template(args.templates, "energy_light.png")
    used = [f"base={'energy_base.png' if base else '(手続き描画)'}",
            f"light={'energy_light.png' if light else '(手続き描画)'}"]
    base = base or draw_base()
    light = light or draw_light()

    if color_layer is not None:
        used.append("color=energy_color.png")
    else:
        # 色レイヤが無ければ筐体の下敷きから彩度のある部分を抜き出す。
        base, color_layer = split_color(base)
        used.append("color=(energy_base.png の彩度のある部分)" if color_layer
                    else "color=(手続き描画)")
        color_layer = color_layer if color_layer is not None else draw_color()
    print(f"下敷き: {' '.join(used)}\n")

    rings = segment_rings(light)
    os.makedirs(OUT_DIR, exist_ok=True)
    for tier in ENERGY_TIERS:
        color = tier_color(tier)

        # 1 枚目: 筐体 + 常時出る階層色。残量に依らずこれが下地になる。
        always = base.copy()
        always.alpha_composite(paint(color_layer, tier, color))
        always.save(os.path.join(OUT_DIR, f"{tier}_energy_cell.png"))

        # 2 枚目以降: 残量ぶんだけの発光レイヤ (モデル側で emissivity 15 で重ねる)。
        lit = paint(light, tier, color)
        for fullness in range(MAX_FULLNESS + 1):
            masked(lit, fullness, rings).save(
                os.path.join(OUT_DIR, f"{tier}_energy_cell_{fullness}_light.png"))

        mark = " (虹)" if tier in RAINBOW_TIERS else ""
        print(f"{tier:>12s}  #{color[0]:02x}{color[1]:02x}{color[2]:02x}{mark}")
    print(f"\n{len(ENERGY_TIERS) * (MAX_FULLNESS + 2)} 枚 -> {OUT_DIR}")


if __name__ == "__main__":
    main()
