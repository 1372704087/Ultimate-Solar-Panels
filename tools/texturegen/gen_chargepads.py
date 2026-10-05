# -*- coding: utf-8 -*-
# 「XX充电座」方块家族生成器（67 档：每档顶面 2 张贴图 + blockstate + 模型 + lang）。
#
# 模板：cable_pixel_editor.html 里的「充电座」图案（IU chargepad_mfsu_top 红色 1:1
# 转写）。顶面按档换色（R=accent，B=混白 0.45；垫面灰阶固定）；
# active 顶面 = 垫面灰阶全部混青色 45%（充电中的发光态，同 IU top_active 语义）。
# 侧面/底面不生成——直接复用储电盒的 side/topbottom 贴图（IU 里两者本就同图）。
#
# 产出：textures/block/<id>_top|_top_active.png（67×2）、blockstates、
#       models/block/<id>{,_active}.json、models/item/<id>.json、双语 lang。
#
# 用法：py gen_chargepads.py [--write]
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
TEX_DIR = os.path.join(ASSETS, 'textures', 'block')
PREVIEW_DIR = os.path.join(BASE, 'preview')
LANG_DIR = os.path.join(ASSETS, 'lang')
SRC_SOLARTIER = os.path.normpath(os.path.join(BASE, '..', '..', 'src', 'main', 'java',
                                              'com', 'example', 'solarpower', 'solar',
                                              'SolarTier.java'))

SUFFIX = '_chargepad'

# ===== 充电座顶面（IU chargepad_mfsu_top 转写，编辑器「充电座」图案同步） =====
TEMPLATE_TOP = [
"DDDDDOBOOBODDDDD",
"DNGGNGRRRRGNGGND",
"DNOOOOOOOOOOEEND",
"DNOGGWWWWWWGGEND",
"DGOGOOOOOEEEGEGD",
"ONOWOGGWWGGEWENO",
"BROWOGEEEEGEWERB",
"OROWEWEGWEWEWERO",
"OROWEWEWGOWEWERO",
"BROWEGEEOOGEWERB",
"ONOWEGGWWGGEWENO",
"DGEGEEEEEEEEGEGD",
"DNEGGWWWWWWGGEND",
"DNEEEEEEEEEEEEND",
"DNGGNGRRRRGNGGND",
"DDDDDOBOOBODDDDD",
]

# 固定色（IU 原图采样）：垫暗/垫深灰/垫蓝灰/垫中灰/垫青灰/垫亮
FIXED = {
    'E': (0x6E, 0x6E, 0x6E),
    'O': (0x96, 0x96, 0x96),
    'D': (0x9C, 0xAC, 0xAD),
    'W': (0xA8, 0xA8, 0xA8),
    'N': (0xB9, 0xC3, 0xC3),
    'G': (0xD4, 0xD4, 0xD4),
}
BRIGHT_MIX = 0.45
# active 发光：垫面灰阶向青色混 45%（R/B 端子保持原色）
GLOW = (79, 209, 197)
GLOW_MIX = 0.45


def tier_accents():
    src = open(SRC_SOLARTIER, encoding='utf-8').read()
    starts = [m for m in re.finditer(r'^    [A-Z_0-9]+\("([a-z_0-9]+)",', src, re.M)]
    accents = {}
    for i, m in enumerate(starts):
        end = starts[i + 1].start() if i + 1 < len(starts) else len(src)
        hx = re.findall(r'0x([0-9A-Fa-f]{6})', src[m.start():end])
        assert len(hx) == 1, (m.group(1), hx)
        accents[m.group(1)] = tuple(int(hx[0][j:j + 2], 16) for j in (0, 2, 4))
    return accents


def bright_of(accent):
    return tuple(round(c + (255 - c) * BRIGHT_MIX) for c in accent)


def glow_of(color):
    return tuple(round(c + (GLOW[i] - c) * GLOW_MIX) for i, c in enumerate(color))


def box_id(tier_id):
    return tier_id + SUFFIX


def build(accent):
    """顶面两张：普通态（R=accent B=亮）与充电态（垫面灰阶混青发光）。"""
    color = dict(FIXED, R=accent, B=bright_of(accent))
    tops = []
    for active in (False, True):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        px = img.load()
        for y, row in enumerate(TEMPLATE_TOP):
            assert len(row) == 16, row
            for x, ch in enumerate(row):
                if ch == '.':
                    continue
                c = color[ch]
                if active and ch in FIXED:
                    c = glow_of(c)
                px[x, y] = c + (255,)
        tops.append(img)
    return tops


def blockstate_json(panel_ids):
    """单方块 blockstate：type=0..66（SolarTier 下标）× active 两态 = 134 变体，
    type=N 指向该档的方块模型（模型里侧面/底面引用同档储电盒贴图）。"""
    variants = {}
    for t, tid in enumerate(panel_ids):
        variants['type=%d,active=false' % t] = {
            'model': 'solarpower:block/%s_chargepad' % tid}
        variants['type=%d,active=true' % t] = {
            'model': 'solarpower:block/%s_chargepad_active' % tid}
    return {'variants': variants}


def block_model_json(active):
    """侧面/底面复用同档储电盒的 side/topbottom 贴图（IU 两者本就同图，零重复文件）。"""
    return {
        'parent': 'block/cube',
        'textures': {
            'north': 'solarpower:block/{panel}_storage_box_side',
            'south': 'solarpower:block/{panel}_storage_box_side',
            'east': 'solarpower:block/{panel}_storage_box_side',
            'west': 'solarpower:block/{panel}_storage_box_side',
            'up': 'solarpower:block/{id}_top_active' if active else 'solarpower:block/{id}_top',
            'down': 'solarpower:block/{panel}_storage_box_topbottom',
            'particle': 'solarpower:block/{panel}_storage_box_side',
        },
    }


def item_model_json():
    return {'parent': 'solarpower:block/{id}'}


def append_lang_lines(triples):
    """triples: [(meta, zh, en)]；键 tile.solarpower.chargepad.<meta>.name，幂等。"""
    for code, col in (('zh_cn', 1), ('en_us', 2)):
        path = os.path.join(LANG_DIR, code + '.lang')
        with open(path, encoding='utf-8') as f:
            content = f.read()
        existing = {m.group(1) for m in
                    re.finditer(r'tile\.solarpower\.chargepad\.(\d+)\.name', content)}
        missing = []
        for meta, zh, en in triples:
            if str(meta) in existing:
                continue
            missing.append((str(meta), zh if code == 'zh_cn' else en))
        if not missing:
            print('%s: lang already up to date' % code)
            continue
        with open(path, 'a', encoding='utf-8', newline='\n') as f:
            if not content.endswith('\n'):
                f.write('\n')
            f.write('\n# ===== 充电座（gen_chargepads.py 追加，meta=SolarTier 下标）=====\n')
            for meta, name in missing:
                f.write('tile.solarpower.chargepad.%s.name=%s\n' % (meta, name))
        print('%s: appended %d lang lines' % (code, len(missing)))


if __name__ == '__main__':
    import sys
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    accents = tier_accents()
    assert len(accents) == 67, len(accents)
    if '--write' in sys.argv:
        for d in (TEX_DIR, BLOCKSTATE_DIR, BLOCK_MODEL_DIR, ITEM_MODEL_DIR):
            os.makedirs(d, exist_ok=True)
        # 面板显示名（lang 反查，用于派生充电座名）
        panel_names = {}
        for code in ('zh_cn', 'en_us'):
            path = os.path.join(LANG_DIR, code + '.lang')
            table = {}
            for line in open(path, encoding='utf-8'):
                m = re.match(r'tile\.solarpower\.([a-z_0-9]+)\.name=(.+)', line.strip())
                if m and m.group(1) in accents:
                    table[m.group(1)] = m.group(2)
            panel_names[code] = table
        panel_ids = list(accents)
        for t, tid in enumerate(panel_ids):
            bid = box_id(tid)
            normal, active = build(accents[tid])
            normal.save(os.path.join(TEX_DIR, bid + '_top.png'))
            active.save(os.path.join(TEX_DIR, bid + '_top_active.png'))
            subst = lambda obj: json.loads(json.dumps(obj)
                                           .replace('{id}', bid)
                                           .replace('{panel}', tid))
            # 每档两个方块模型保留（单方块 blockstate 的 type=N 变体指向它们）
            with open(os.path.join(BLOCK_MODEL_DIR, bid + '.json'), 'w',
                      encoding='utf-8', newline='\n') as f:
                f.write(json.dumps(subst(block_model_json(False)), indent=2) + '\n')
            with open(os.path.join(BLOCK_MODEL_DIR, bid + '_active.json'), 'w',
                      encoding='utf-8', newline='\n') as f:
                f.write(json.dumps(subst(block_model_json(True)), indent=2) + '\n')
        # block/block/ 双路径 shim（普通态 + 充电态，同储电盒）
        shim_dir = os.path.join(BLOCK_MODEL_DIR, 'block')
        os.makedirs(shim_dir, exist_ok=True)
        for t, tid in enumerate(panel_ids):
            bid = box_id(tid)
            for suffix in ('', '_active'):
                with open(os.path.join(shim_dir, bid + suffix + '.json'), 'w',
                          encoding='utf-8', newline='\n') as f:
                    f.write(json.dumps({'parent': 'solarpower:block/' + bid + suffix},
                                       indent=2) + '\n')
        # 单方块 blockstate（type 索引变体）；旧逐档 blockstate/item model 清理
        with open(os.path.join(BLOCKSTATE_DIR, 'chargepad.json'), 'w',
                  encoding='utf-8', newline='\n') as f:
            f.write(json.dumps(blockstate_json(panel_ids), indent=2) + '\n')
        removed = 0
        for d, suf in ((BLOCKSTATE_DIR, '_chargepad.json'),
                       (ITEM_MODEL_DIR, '_chargepad.json')):
            for f in os.listdir(d):
                if f.endswith(suf) and f != 'chargepad.json':
                    os.remove(os.path.join(d, f)); removed += 1
        print('removed %d stale per-tier jsons' % removed)
        print('written %d chargepad texture sets + block models ->' % len(accents), TEX_DIR)
        append_lang_lines([(t,
                            panel_names['zh_cn'][tid].replace('太阳能发电机', '充电座'),
                            panel_names['en_us'][tid].replace('Solar Panel', 'Charge Pad'))
                           for t, tid in enumerate(panel_ids)])
    # 预览：抽 8 档 普通/充电态
    picks = [list(accents)[i] for i in
             sorted(set(int(round(i * (len(accents) - 1) / 7)) for i in range(8)))]
    S, pad, cols = 8, 10, 2
    rows = len(picks)
    W = cols * (16 * S + pad) + pad
    H = rows * (16 * S + pad + 30) + pad
    sheet = Image.new('RGBA', (W, H), (48, 50, 56, 255))
    d = ImageDraw.Draw(sheet)
    for r, tid in enumerate(picks):
        accent = accents[tid]
        imgs = build(accent)
        for c, im in enumerate(imgs):
            x = pad + c * (16 * S + pad)
            y = pad + r * (16 * S + pad + 30)
            big = im.resize((16 * S, 16 * S), Image.NEAREST)
            sheet.paste(big, (x, y), big)
            if r == 0:
                d.text((x, y - 12), ['idle', 'charging'][c], fill=(220, 226, 234, 255))
        d.text((pad, y + 16 * S + 2),
               '%s  #%02x%02x%02x' % (box_id(tid), *accent),
               fill=(240, 244, 250, 255))
    out = os.path.join(PREVIEW_DIR, 'chargepads_preview.png')
    sheet.save(out)
    print('preview ->', out)
