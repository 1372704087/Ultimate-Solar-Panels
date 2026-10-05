# -*- coding: utf-8 -*-
# 「XX变压器」方块家族生成器（1.21.1 仓库适配版：67 档，SolarTier 全档各有方块）。
# （1.12.2 原版 65 档流程保留在旧工程 G:\solarpower\texturegen；本版只输出 1.21.1 资产。）
#
# 模板：cable_pixel_editor.html 里的「变压器」图案（IU hv_transformer_front 红色
# 1:1 转写）作正面；侧面 = mfsu_front 设计（IU 变压器侧面与储能仓正面同图）。
#
# 换色：红三阶 M/R/B = 本档面板 accent 的 deep(×0.545)/原色/混白 0.45。
#   accent 取 SolarTier 本档自己的主题色（与储电盒/充电座同规则；2026-10-05 前跟随
#   电压档代表色，导致基础/高级变压器同为 1 级电压而外观雷同，已改）。
#   灯珠（O/W/N）与石灰（E/G/D/N）固定。每档 2 张贴图（正面/侧面，其余面复用侧面）。
#
# 产出（--write，全部 67 档）：
#   textures/block/<id>_front|_side.png
#   blockstates/<id>_transformer.json（facing 四向）
#   models/block/<id>_transformer.json（cube：北=正面，其余=侧面）
#   models/item/<id>_transformer.json（parent 指向方块模型）
#   items/<id>_transformer.json（1.21.4+ 客户端物品定义，1.21.1 忽略，保持仓库一致）
#   lang/zh_cn.json、en_us.json（block.solarpower.<id>_transformer，只补缺失，幂等）
#
# 用法：py gen_transformers.py [--write]
from PIL import Image, ImageDraw
import json
import os
import re

BASE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(BASE, '..', '..', 'src', 'main', 'resources',
                                       'assets', 'solarpower'))
BLOCKSTATE_DIR = os.path.join(ASSETS, 'blockstates')
BLOCK_MODEL_DIR = os.path.join(ASSETS, 'models', 'block')
ITEM_MODEL_DIR = os.path.join(ASSETS, 'models', 'item')
ITEM_DEF_DIR = os.path.join(ASSETS, 'items')
TEX_DIR = os.path.join(ASSETS, 'textures', 'block')
PREVIEW_DIR = os.path.join(BASE, 'preview')
LANG_DIR = os.path.join(ASSETS, 'lang')
SRC_EUTIER = os.path.normpath(os.path.join(BASE, '..', '..', 'src', 'main', 'java',
                                           'com', 'example', 'solarpower', 'energy',
                                           'EuTier.java'))
SRC_SOLARTIER = os.path.normpath(os.path.join(BASE, '..', '..', 'src', 'main', 'java',
                                              'com', 'example', 'solarpower', 'solar',
                                              'SolarTier.java'))

SUFFIX = '_transformer'

# ===== 变压器正面（IU hv_transformer_front 转写，编辑器「变压器」图案同步） =====
TEMPLATE_FRONT = [
"DDDDDDBDDBDDDDDD",
"DDGEEGRMMRGEEGDD",
"DGEOOEGRRGEOOEGD",
"DEONWOEBBEONWOED",
"DEOWWOEBBEOWWOED",
"DGEOOEMEEMEOOEGD",
"BRGEEMEOOEMEEGRB",
"DMRBBEONWOEBBRMD",
"DMRBBEOWWOEBBRMD",
"BRGEEMEOOEMEEGRB",
"DGEOOEMEEMEOOEGD",
"DEONWOEBBEONWOED",
"DEOWWOEBBEOWWOED",
"DGEOOEGRRGEOOEGD",
"DDGEEGRMMRGEEGDD",
"DDDDDDBDDBDDDDDD",
]
# ===== 侧面（IU 变压器侧面 = mfsu_front，与储电盒正面同图） =====
TEMPLATE_SIDE = [
"DDDDDDBBBBDDDDDD",
"DMMRRRMGGMRRRMMD",
"DGGGGMNGGNMGGGGD",
"DMRBRMNGGNMRBRMD",
"DGGGGGNGGNGGGGGD",
"DNDDNGEEEEGNDDND",
"RRGGREEOOEERGGRR",
"MMRBMEOWWOEMBRMM",
"MMRBMEOWWOEMBRMM",
"RRGGREEOOEERGGRR",
"DNDDNGEEEEGNDDND",
"DGGGGGNGGNGGGGGD",
"DMRBRMNGGNMRBRMD",
"DGGGGMNGGNMGGGGD",
"DMMRRRMGGMRRRMMD",
"DDDDDDBBBBDDDDDD",
]

# 各面固定色（IU 原图采样）：正面有灯珠三色，侧面是储能仓正面配色（能量芯橙）
FIXED_FRONT = {
    'E': (0x3D, 0x3D, 0x3D), 'G': (0xD8, 0xDE, 0xDE), 'D': (0x9C, 0xAC, 0xAD),
    'O': (0xC9, 0xBD, 0x55), 'W': (0xF2, 0xF1, 0x70), 'N': (0xF5, 0xFF, 0xD1),
}
FIXED_SIDE = {
    'E': (0x3D, 0x3D, 0x3D), 'G': (0xD8, 0xDE, 0xDE), 'D': (0x9C, 0xAC, 0xAD),
    'N': (0xB9, 0xC3, 0xC3), 'O': (0xC4, 0x83, 0x01), 'W': (0xE7, 0xB7, 0x07),
}
DEEP_FACTOR = 0.545
BRIGHT_MIX = 0.45


def solar_tiers():
    """SolarTier.java 全部档位（声明序）：[(enum名, id, iuTier)]，1.21.1 为 67 档。"""
    src = open(SRC_SOLARTIER, encoding='utf-8').read()
    body = src[src.index('enum SolarTier'):]
    tiers = [(m.group(1), m.group(2), int(m.group(3)))
             for m in re.finditer(r'^    ([A-Z_0-9]+)\("([a-z_0-9]+)",\s*(\d+),', body, re.M)]
    assert len(tiers) == 67, len(tiers)
    return tiers


def accents_by_tier():
    """SolarTier 每档自己的 accentColor：{tier id: (r,g,b)}（与储电盒/充电座同规则）。"""
    src = open(SRC_SOLARTIER, encoding='utf-8').read()
    starts = [m for m in re.finditer(r'^    [A-Z_0-9]+\("([a-z_0-9]+)",\s*(\d+),', src, re.M)]
    accents = {}
    for i, m in enumerate(starts):
        end = starts[i + 1].start() if i + 1 < len(starts) else len(src)
        hx = re.findall(r'0x([0-9A-Fa-f]{6})', src[m.start():end])
        assert len(hx) == 1
        accents[m.group(1)] = tuple(int(hx[0][j:j + 2], 16) for j in (0, 2, 4))
    return accents


def deep_of(accent):
    return tuple(round(c * DEEP_FACTOR) for c in accent)


def bright_of(accent):
    return tuple(round(c + (255 - c) * BRIGHT_MIX) for c in accent)


def build(accent):
    color_front = dict(FIXED_FRONT, M=deep_of(accent), R=accent, B=bright_of(accent))
    color_side = dict(FIXED_SIDE, M=deep_of(accent), R=accent, B=bright_of(accent))
    imgs = []
    for template, color in ((TEMPLATE_FRONT, color_front), (TEMPLATE_SIDE, color_side)):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        px = img.load()
        for y, row in enumerate(template):
            assert len(row) == 16, row
            for x, ch in enumerate(row):
                if ch == '.':
                    continue
                px[x, y] = color[ch] + (255,)
        imgs.append(img)
    return imgs


FACING_Y = {'north': None, 'east': 90, 'south': 180, 'west': 270}


def write_json(path, obj):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(json.dumps(obj, indent=2, ensure_ascii=False) + '\n')


def blockstate_json(bid):
    """单方块 blockstate：facing 四向（TransformerBlock 只有水平朝向属性）。"""
    variants = {}
    for fdir, y in FACING_Y.items():
        v = {'model': 'solarpower:block/%s' % bid}
        if y is not None:
            v['y'] = y
        variants['facing=%s' % fdir] = v
    return {'variants': variants}


def block_model_json(bid):
    return {
        'parent': 'minecraft:block/cube',
        'textures': {
            'north': 'solarpower:block/%s_front' % bid,
            'south': 'solarpower:block/%s_side' % bid,
            'east': 'solarpower:block/%s_side' % bid,
            'west': 'solarpower:block/%s_side' % bid,
            'up': 'solarpower:block/%s_side' % bid,
            'down': 'solarpower:block/%s_side' % bid,
            'particle': 'solarpower:block/%s_side' % bid,
        },
    }


def item_model_json(bid):
    return {'parent': 'solarpower:block/%s' % bid}


def item_def_json(bid):
    """assets/solarpower/items/<id>.json——1.21.4+ 客户端物品定义（1.21.1 忽略）。"""
    return {'model': {'type': 'minecraft:model', 'model': 'solarpower:item/%s' % bid}}


def sync_lang(entries):
    """entries: {key: (zh, en)}；只补缺失键，保留原文件键序与 2 空格缩进。"""
    for code, col in (('zh_cn', 0), ('en_us', 1)):
        path = os.path.join(LANG_DIR, code + '.json')
        with open(path, encoding='utf-8') as f:
            table = json.load(f)
        missing = {k: v[col] for k, v in entries.items() if k not in table}
        if not missing:
            print('%s: lang already up to date' % code)
            continue
        table.update(missing)
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(json.dumps(table, indent=2, ensure_ascii=False) + '\n')
        print('%s: added %d lang keys' % (code, len(missing)))


if __name__ == '__main__':
    import sys
    tiers = solar_tiers()
    accents = accents_by_tier()
    if '--write' in sys.argv:
        for d in (TEX_DIR, BLOCKSTATE_DIR, BLOCK_MODEL_DIR, ITEM_MODEL_DIR, ITEM_DEF_DIR):
            os.makedirs(d, exist_ok=True)
        # lang 反查面板显示名 → 变压器名（太阳能发电机→变压器 / Solar Panel→Transformer）
        panel_names = {}
        for code in ('zh_cn', 'en_us'):
            path = os.path.join(LANG_DIR, code + '.json')
            with open(path, encoding='utf-8') as f:
                panel_names[code] = json.load(f)
        lang_entries = {}
        for _enum, tid, iu in tiers:
            bid = tid + SUFFIX
            accent = accents[tid]
            front, side = build(accent)
            front.save(os.path.join(TEX_DIR, bid + '_front.png'))
            side.save(os.path.join(TEX_DIR, bid + '_side.png'))
            write_json(os.path.join(BLOCKSTATE_DIR, bid + '.json'), blockstate_json(bid))
            write_json(os.path.join(BLOCK_MODEL_DIR, bid + '.json'), block_model_json(bid))
            write_json(os.path.join(ITEM_MODEL_DIR, bid + '.json'), item_model_json(bid))
            write_json(os.path.join(ITEM_DEF_DIR, bid + '.json'), item_def_json(bid))
            zh = panel_names['zh_cn']['block.solarpower.' + tid].replace('太阳能发电机', '变压器')
            en = panel_names['en_us']['block.solarpower.' + tid].replace('Solar Panel', 'Transformer')
            lang_entries['block.solarpower.' + bid] = (zh, en)
        sync_lang(lang_entries)
        print('written %d transformer texture sets + blockstates/models ->' % len(tiers), TEX_DIR)
    # 预览：67 档抽 8 档正/侧面
    picks = [tiers[int(round(i * (len(tiers) - 1) / 7))] for i in range(8)]
    S, pad, cols = 8, 10, 2
    rows = len(picks)
    W = cols * (16 * S + pad) + pad
    H = rows * (16 * S + pad + 30) + pad
    sheet = Image.new('RGBA', (W, H), (48, 50, 56, 255))
    d = ImageDraw.Draw(sheet)
    for r, (_enum, tid, iu) in enumerate(picks):
        accent = accents[tid]
        imgs = build(accent)
        for c, im in enumerate(imgs):
            x = pad + c * (16 * S + pad)
            y = pad + r * (16 * S + pad + 30)
            big = im.resize((16 * S, 16 * S), Image.NEAREST)
            sheet.paste(big, (x, y), big)
            if r == 0:
                d.text((x, y - 12), ['front', 'side'][c], fill=(220, 226, 234, 255))
        d.text((pad, y + 16 * S + 2),
               '%s  #%02x%02x%02x' % (tid + SUFFIX, *accent),
               fill=(240, 244, 250, 255))
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    out = os.path.join(PREVIEW_DIR, 'transformers_preview.png')
    sheet.save(out)
    print('preview ->', out)
