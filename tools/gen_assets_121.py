# -*- coding: utf-8 -*-
# 终极太阳能（NeoForge 1.21.1）资产生成器：从 1.12.2 仓库的 SolarTier/GlassCableTier
# 源码提取档位与名称，生成 1.21.1 格式的 blockstates/models/lang，并复制贴图。
#
# 覆盖：67 档太阳能板（cube 模型+物品模型）+ 68 档玻璃电缆（multipart+generated
# 物品模型）+ 双语 JSON lang。贴图从 1.12.2 资产目录复制。
#
# 用法：py tools/gen_assets_121.py
from PIL import Image
import json
import os
import re
import shutil

OLD = r'G:\solarpower'
NEW = r'G:\ultimatesolar'
OLD_SRC = os.path.join(OLD, 'src', 'main', 'java', 'com', 'example', 'solarpower')
OLD_ASSETS = os.path.join(OLD, 'src', 'main', 'resources', 'assets', 'solarpower')
NEW_ASSETS = os.path.join(NEW, 'src', 'main', 'resources', 'assets', 'solarpower')
NEW_LANG = os.path.join(NEW, 'src', 'main', 'resources', 'assets', 'solarpower', 'lang')

PANEL_IDS = []      # SolarTier 声明顺序（= 创造栏顺序）
CABLE_IDS = []      # GlassCableTier 声明顺序


def scan_tiers():
    src = open(os.path.join(OLD_SRC, 'solar', 'SolarTier.java'), encoding='utf-8').read()
    PANEL_IDS.extend(m.group(1) for m in
                     re.finditer(r'^    [A-Z_0-9]+\("([a-z_0-9]+)",', src, re.M))
    src = open(os.path.join(OLD_SRC, 'solar', 'GlassCableTier.java'), encoding='utf-8').read()
    CABLE_IDS.extend(m.group(1) for m in
                     re.finditer(r'^    [A-Z_0-9]+\("([a-z_0-9]+)",', src, re.M))
    assert len(PANEL_IDS) == 67 and len(CABLE_IDS) == 68, (len(PANEL_IDS), len(CABLE_IDS))


def panel_names():
    names = {}
    for code in ('zh_cn', 'en_us'):
        path = os.path.join(OLD_ASSETS, 'lang', code + '.lang')
        table = {}
        for line in open(path, encoding='utf-8'):
            m = re.match(r'tile\.solarpower\.([a-z_0-9]+)\.name=(.+)', line.strip())
            if m and (m.group(1) in PANEL_IDS or m.group(1) in CABLE_IDS):
                table[m.group(1)] = m.group(2)
        names[code] = table
    return names


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


CABLE_DISPLAY = None


def copy_textures():
    """复制面板与电缆的全部贴图（方块+物品）。"""
    os.makedirs(os.path.join(NEW_ASSETS, 'textures', 'block'), exist_ok=True)
    os.makedirs(os.path.join(NEW_ASSETS, 'textures', 'item'), exist_ok=True)
    old_block = os.path.join(OLD_ASSETS, 'textures', 'block')
    old_item = os.path.join(OLD_ASSETS, 'textures', 'item')
    new_block = os.path.join(NEW_ASSETS, 'textures', 'block')
    new_item = os.path.join(NEW_ASSETS, 'textures', 'item')
    n = 0
    for tid in PANEL_IDS:
        for face in ('_top', '_side'):
            shutil.copyfile(os.path.join(old_block, tid + face + '.png'),
                            os.path.join(new_block, tid + face + '.png'))
            n += 1
    shutil.copyfile(os.path.join(old_block, 'solar_panel_bottom.png'),
                    os.path.join(new_block, 'solar_panel_bottom.png')); n += 1
    for cid in CABLE_IDS:
        shutil.copyfile(os.path.join(old_block, cid + '.png'),
                        os.path.join(new_block, cid + '.png'))
        shutil.copyfile(os.path.join(old_item, cid + '.png'),
                        os.path.join(new_item, cid + '.png'))
        n += 2
    print('textures copied:', n)


def gen_panels():
    bs = os.path.join(NEW_ASSETS, 'blockstates')
    bm = os.path.join(NEW_ASSETS, 'models', 'block')
    im = os.path.join(NEW_ASSETS, 'models', 'item')
    for tid in PANEL_IDS:
        write_json(os.path.join(bs, tid + '.json'),
                   {'variants': {'': {'model': 'solarpower:block/' + tid}}})
        write_json(os.path.join(bm, tid + '.json'), {
            'parent': 'minecraft:block/cube',
            'textures': {
                'particle': 'solarpower:block/%s_top' % tid,
                'down': 'solarpower:block/solar_panel_bottom',
                'up': 'solarpower:block/%s_top' % tid,
                'north': 'solarpower:block/%s_side' % tid,
                'south': 'solarpower:block/%s_side' % tid,
                'east': 'solarpower:block/%s_side' % tid,
                'west': 'solarpower:block/%s_side' % tid,
            }})
        write_json(os.path.join(im, tid + '.json'),
                   {'parent': 'solarpower:block/' + tid})


def gen_cables():
    global CABLE_DISPLAY
    im = os.path.join(NEW_ASSETS, 'models', 'item')
    # 电缆物品模型：generated + 显示变换（取自 1.12 的 glass_cable.json）
    old = json.load(open(os.path.join(OLD_ASSETS, 'models', 'item', 'glass_cable.json'),
                         encoding='utf-8'))
    display = old.get('display')
    for cid in CABLE_IDS:
        obj = {'parent': 'minecraft:item/generated',
               'textures': {'layer0': 'solarpower:item/' + cid}}
        if display:
            obj['display'] = display
        write_json(os.path.join(im, cid + '.json'), obj)


def gen_cable_blockstates():
    """电缆 1.21.1 blockstate：六向连接布尔属性的 multipart（核心 + 六臂）。"""
    bs = os.path.join(NEW_ASSETS, 'blockstates')
    arms = [('north', 'north'), ('south', 'south'), ('west', 'west'),
            ('east', 'east'), ('up', 'up'), ('down', 'down')]
    for cid in CABLE_IDS:
        parts = [{'when': {}, 'apply': {'model': 'solarpower:block/%s_core' % cid}}]
        for prop, arm in arms:
            parts.append({
                'when': {prop: True},
                'apply': {'model': 'solarpower:block/%s_arm_%s' % (cid, arm)},
            })
        write_json(os.path.join(bs, cid + '.json'), {'multipart': parts})


def copy_cable_block_models():
    """电缆核心/六臂方块模型从 1.12 资产原样复制（格式兼容）。"""
    old_bm = os.path.join(OLD_ASSETS, 'models', 'block')
    new_bm = os.path.join(NEW_ASSETS, 'models', 'block')
    os.makedirs(new_bm, exist_ok=True)
    n = 0
    for cid in CABLE_IDS:
        for suf in ('_core', '_arm_north', '_arm_south', '_arm_west',
                    '_arm_east', '_arm_up', '_arm_down'):
            src = os.path.join(old_bm, cid + suf + '.json')
            if os.path.exists(src):
                shutil.copyfile(src, os.path.join(new_bm, cid + suf + '.json'))
                n += 1
    print('cable block models copied:', n)


def gen_lang():
    names = panel_names()
    os.makedirs(NEW_LANG, exist_ok=True)
    for code, lang_name in (('en_us', 'Ultimate Solar'), ('zh_cn', '终极太阳能')):
        obj = {'itemGroup.solarpower': lang_name}
        for tid in PANEL_IDS:
            obj['block.solarpower.' + tid] = names[code][tid]
        for cid in CABLE_IDS:
            obj['block.solarpower.' + cid] = names[code][cid]
        write_json(os.path.join(NEW_LANG, code + '.json'), obj)


if __name__ == '__main__':
    scan_tiers()
    copy_textures()
    gen_panels()
    copy_cable_block_models()
    gen_cables()
    gen_cable_blockstates()
    gen_lang()
    print('done: %d panels, %d cables' % (len(PANEL_IDS), len(CABLE_IDS)))
