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
虹のグラデーション</b>で、水色 → 青 → 紫 → ピンクと進む。ただし<b>色相を回すだけだと
明るさが桁違いに変わる</b>ので、相対輝度は ENERGY_LUMA に揃えてある (揃えないと
中段の青紫だけが真っ黒に沈む)。最上段 (cosmic) だけは<b>残量の段ごとに色相が変わる
同心の虹色</b>になり、充電が進むにつれて色が増えていく。

--------------------------------------------------------------------------------------
下敷きの差し替え
--------------------------------------------------------------------------------------
tools/textures/ に次の名前で置くと、そちらが優先して使われる (無ければ手続き描画)。

    energy_base.png    筐体 (ベース)。<b>染まらない</b>。地に色が付いていても構わない
    energy_color.png   常時出る階層色のレイヤ (色)。残量に関係なく必ず出る
    energy_light.png   充電ぶんの発光レイヤ (充電)。残量で点く部分を全部描いておく

<b>この 3 枚に分けて描くこと。</b>クラフトストレージ側の
crafting_base / crafting_color / crafting_light と同じ役割分担で、
base は染まらないので階層に依らない部分をすべてここに描けばよい。

    energy_color.png を置かなかった場合に限り、後方互換として
    energy_base.png から<b>彩度で</b>色レイヤを切り出す (警告が出る)。
    この方式は地に少しでも色が付いていると一緒に染まってしまうので、
    `--dump-templates` で 3 枚に分けた叩き台を書き出して移行すること。

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

from gen_crafting_textures import (RED, fit_luminance, load_template, rainbow_recolor,
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

# 階層色の<b>相対輝度</b>を (最下段, 最上段) の範囲に揃える。
#
# 色相を回すだけだと「人が感じる明るさ」が色によって桁違いに変わる。この階層は
# 水色 → 青 → 紫 → ピンクと回すので、明度を 1.0 で固定すると水色は輝度 0.79、
# 中段の青紫は 0.11 と<b>7 倍も差が出て</b>、中段のセルだけが真っ黒に沈んでいた。
# 輝度で揃えたうえで、上位ほど少しだけ明るくして階層の進みが分かるようにする。
#
# 光る部分は暗い落とし込みの中にあるので、地 (一番明るいところで 0.888) ほど
# 明るくする必要はない。上げすぎると白っぽくなって色が飛ぶ。
ENERGY_LUMA = (0.16, 0.32)

# 虹色の階層の基準色と、色相の振り方。
#
# <b>中心からの距離 (ring) で振る</b>のが要点。このテクスチャは残量の段が
# 中心からのリングそのものなので、リングごとに色相を回すと<b>段と虹の輪が一致し</b>、
# 充電が進むごとに色が変わって見える。斜め (diag) で振ると十字に伸びる腕の上で
# 色相がばらけ、同じ段なのに色が違う濁った見た目になった。
#
# 輝度は保つ (keep_luma)。ここは 4 リングしかないので 1 リングあたりの回転が大きく、
# 保たないと「金色に光る輪」と「黒く沈む輪」が交互に出てしまう。
RAINBOW_BASE = "#00e5ff"
RAINBOW_STEP = -1.0 / 4.0
RAINBOW_AXIS = "ring"
RAINBOW_KEEP_LUMA = True

MAX_FULLNESS = 4          # EnergyCellBlock.MAX_FULLNESS
SIZE = 16

# 色 (energy_color.png) と充電 (energy_light.png) を<b>何色で描いてあるか</b>。
# 宣言しておくと「その色相 → 階層色の色相」の回転になるので、1 枚の中でわざとずらした
# 色相差がそのまま保たれる。None にすると下敷きの主要色相を自動判定する。
# クラフトストレージの協調処理ユニットと同じく赤で描く前提。
# 下敷きがグレースケールなら、この指定は無視されて明度から染める方式になる。
ENERGY_TEMPLATE_HUE = RED

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
    """階層の色。色相を 1 段ずつ回し、<b>相対輝度は ENERGY_LUMA に揃える</b>。

    虹色の階層は基準色を返す (色相はドットごとに振られるので、ここでは輝度だけ合わせる)。
    """
    lo, hi = ENERGY_LUMA
    if tier in RAINBOW_TIERS:
        return fit_luminance(parse_hex(RAINBOW_BASE), hi)
    ramp = [t for t in ENERGY_TIERS if t not in RAINBOW_TIERS]
    pos = ramp.index(tier) / max(1, len(ramp) - 1)
    hue = HUE_START + (HUE_END - HUE_START) * pos
    rgb = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, SATURATION, 1.0))
    return fit_luminance(rgb, lo + (hi - lo) * pos)


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
    check_symmetry(light)
    return mapping


def check_symmetry(light: Image.Image) -> None:
    """発光レイヤが上下左右対称かを見て、崩れていたら画素を挙げて警告する。

    このテクスチャは中心から十字に伸びる 4 本の腕でできていて、<b>腕は同じ長さで
    同じ段から始まる</b>のが前提。1 ドット描き落とすとその腕だけ段がひとつ外へずれ、
    残量が増えたときに<b>1 本だけ光り出しが遅れて 1px 外側にずれて見える</b>。
    ゲームを起動しないと気付きにくいので、生成のたびに見ておく。
    """
    px = light.load()
    lit = {(x, y) for y in range(SIZE) for x in range(SIZE) if px[x, y][3] > 0}
    for label, flip in (("上下", lambda p: (p[0], SIZE - 1 - p[1])),
                        ("左右", lambda p: (SIZE - 1 - p[0], p[1]))):
        missing = sorted({flip(p) for p in lit} - lit)
        if missing:
            print(f"警告: 発光レイヤが{label}対称になっていない。"
                  f" 描き落としの疑いがある画素 {missing[:8]}"
                  f"{' ほか' if len(missing) > 8 else ''}")


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

    <b>クラフトストレージ側と同じ {@code recolor} を使う。</b>下敷きがグレースケールなら
    明度から染め、色を付けて描いてあるなら「{@link ENERGY_TEMPLATE_HUE} → 階層色の色相」の
    回転になり、陰影は<b>輝度の比</b>で保たれる。

    以前は色付きの下敷きに対して色相を回すだけ ({@code hue_rotate}) にしていたが、
    それだと<b>下敷きの彩度がそのまま上限になる</b>。今の下敷きは彩度 0.55 までしか
    使っていないので、階層色の彩度 (SATURATION) をいくら上げても全階層が
    パステルのまま眠い色になっていた。
    """
    if tier in RAINBOW_TIERS:
        return rainbow_recolor(template, color, RAINBOW_STEP, ENERGY_TEMPLATE_HUE,
                               RAINBOW_AXIS, RAINBOW_KEEP_LUMA)
    return recolor(template, color, ENERGY_TEMPLATE_HUE)


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
        # 後方互換: 色レイヤが無ければ筐体の下敷きから彩度のある部分を抜き出す。
        # 地に少しでも色が付いていると一緒に染まってしまうので、分けたほうがよい。
        base, color_layer = split_color(base)
        if color_layer is not None:
            print("  警告: energy_color.png が無いので energy_base.png から彩度で切り出した。"
                  " 地に色が付いていると一緒に染まってしまうので、"
                  " --dump-templates で 3 枚に分けた叩き台を書き出して移行すること。")
            used.append("color=(energy_base.png の彩度のある部分)")
        else:
            used.append("color=(手続き描画)")
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
