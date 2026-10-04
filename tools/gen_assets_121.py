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
MACHINE_FAMILIES = [
    # (后缀, 1.12 资产模型名后缀, zh 替换, en 替换, 贴图面)
    ('_storage_box', '_storage_box', ('太阳能发电机', '储电盒'), ('Solar Panel', 'Storage Box'),
     ('_front', '_side', '_topbottom')),
    ('_transformer', '_transformer', ('太阳能发电机', '变压器'), ('Solar Panel', 'Transformer'),
     ('_front', '_side')),
    ('_chargepad', '_chargepad', ('太阳能发电机', '充电座'), ('Solar Panel', 'Charge Pad'),
     ('_top', '_top_active')),
]


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
        for suffix, _old, (zh_from, zh_to), (en_from, en_to), _faces in MACHINE_FAMILIES:
            for tid in PANEL_IDS:
                key = 'block.solarpower.' + tid + suffix
                obj[key] = names[code][tid].replace(zh_from, zh_to) if code == 'zh_cn' \
                    else names[code][tid].replace(en_from, en_to)
        # GUI 文案
        obj['gui.solarpower.storage.energy'] = '蓄电量：%s / %s EU' if code == 'zh_cn' else 'Energy: %s / %s EU'
        obj['gui.solarpower.storage.capacity'] = '容量：%s EU' if code == 'zh_cn' else 'Capacity: %s EU'
        obj['gui.solarpower.storage.output'] = '输出：%s EU/t' if code == 'zh_cn' else 'Output: %s EU/t'
        obj['gui.solarpower.transformer.low'] = ('低压池（%s）：%s / %s EU') if code == 'zh_cn' else ('Low pool (%s): %s / %s EU')
        obj['gui.solarpower.transformer.high'] = ('高压池（%s）：%s / %s EU') if code == 'zh_cn' else ('High pool (%s): %s / %s EU')
        obj['gui.solarpower.transformer.rate'] = '吞吐：%s EU/t（双向）' if code == 'zh_cn' else 'Throughput: %s EU/t (bidirectional)'
        obj['gui.solarpower.transformer.voltage'] = '电压：%s ↔ %s' if code == 'zh_cn' else 'Voltage: %s ↔ %s'
        obj['gui.solarpower.chargepad.energy'] = '蓄电量：%s / %s EU' if code == 'zh_cn' else 'Energy: %s / %s EU'
        obj['gui.solarpower.chargepad.rate'] = '充电速率：%s EU/t' if code == 'zh_cn' else 'Charge rate: %s EU/t'
        obj['gui.solarpower.chargepad.status'] = '状态：%s' if code == 'zh_cn' else 'Status: %s'
        obj['gui.solarpower.chargepad.status.idle'] = '待机（站上来即充电）' if code == 'zh_cn' else 'Idle (stand on top to charge)'
        obj['gui.solarpower.chargepad.status.charging'] = '充电中' if code == 'zh_cn' else 'Charging'
        obj['container.inventory'] = '物品栏' if code == 'zh_cn' else 'Inventory'
        write_json(os.path.join(NEW_LANG, code + '.json'), obj)


def gen_machines():
    """三族机器：贴图复制 + per-tier 方块模型（复用 1.12 资产）+ blockstate + 物品模型。"""
    old_bm = os.path.join(OLD_ASSETS, 'models', 'block')
    for suffix, old_suffix, (zh_from, zh_to), (en_from, en_to), faces in MACHINE_FAMILIES:
        for tid in PANEL_IDS:
            bid = tid + suffix
            # 贴图
            for face in faces:
                src = os.path.join(OLD_ASSETS, 'textures', 'block', tid + old_suffix + face + '.png')
                if os.path.exists(src):
                    shutil.copyfile(src, os.path.join(NEW_ASSETS, 'textures', 'block',
                                                      bid + face + '.png'))
            # 方块模型：直接复制 1.12 版（cube 父级 + 贴图引用，格式兼容 1.21.1）
            old_model = os.path.join(old_bm, tid + old_suffix + '.json')
            if os.path.exists(old_model):
                shutil.copyfile(old_model, os.path.join(NEW_ASSETS, 'models', 'block', bid + '.json'))
            # blockstate：storage/transformer 有朝向；chargepad 两态
            if suffix in ('_storage_box', '_transformer'):
                variants = {}
                for fdir in ('north', 'east', 'south', 'west'):
                    v = {'model': 'solarpower:block/' + bid}
                    if fdir == 'east':
                        v['y'] = 90
                    elif fdir == 'south':
                        v['y'] = 180
                    elif fdir == 'west':
                        v['y'] = 270
                    variants['facing=' + fdir] = v
                write_json(os.path.join(NEW_ASSETS, 'blockstates', bid + '.json'),
                           {'variants': variants})
            else:
                write_json(os.path.join(NEW_ASSETS, 'blockstates', bid + '.json'), {
                    'variants': {
                        'active=false': {'model': 'solarpower:block/' + bid},
                        'active=true': {'model': 'solarpower:block/' + bid + '_active'},
                    }})
                # 充电态模型（同模型 JSON 的 active 版：1.12 资产里是 _active 模型）
                old_active = os.path.join(old_bm, tid + old_suffix + '_active.json')
                if os.path.exists(old_active):
                    shutil.copyfile(old_active, os.path.join(NEW_ASSETS, 'models', 'block',
                                                             bid + '_active.json'))
            # 物品模型 = 方块模型
            write_json(os.path.join(NEW_ASSETS, 'models', 'item', bid + '.json'),
                       {'parent': 'solarpower:block/' + bid})


if __name__ == '__main__':
    scan_tiers()
    copy_textures()
    # GUI 底图（与版本无关的 PNG，直接复用）
    gui_old = os.path.join(OLD_ASSETS, 'textures', 'gui')
    gui_new = os.path.join(NEW_ASSETS, 'textures', 'gui')
    os.makedirs(gui_new, exist_ok=True)
    for f in ('storage_box.png', 'transformer.png'):
        shutil.copyfile(os.path.join(gui_old, f), os.path.join(gui_new, f))
    gen_panels()
    copy_cable_block_models()
    gen_cables()
    gen_cable_blockstates()
    gen_machines()
    gen_lang()
    print('done: %d panels, %d cables' % (len(PANEL_IDS), len(CABLE_IDS)))
