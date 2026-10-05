# -*- coding: utf-8 -*-
# 终极太阳能（NeoForge 1.21）工作台配方生成器：把 1.12.2 ModRecipes.java 的
# 材料链 1:1 移植成 1.21 data recipe JSON。
#
# 覆盖：
#   1. ASP 基础材料 12 条（阳炎系/铱系/辐光系/双核心/分子重组仪本体）
#   2. SSP 光谱组件 4 条（红/绿/蓝组件 + 太阳光分解器）
#   3. 面板材料链（67 玻璃板 / 66 小块·化合物·合金 / 65 核心 / 67 面板）
#   4. 电缆 67 条（基础 + 高级 + 同款十字升级链）
#   5. 修复 66 条变压器配方：电缆原料按 GlassCableTier 真实 id 修正
#
# 矿辞替代（1.21 无 IC2）：ingotUranium→本模组铀锭；dustDiamond→钻石、
# dustSilver→铁锭（原配方需装 IC2，这里取原版替身）；dyeYellow/Blue→对应染料。
#
# 用法：py tools/gen_recipes_121.py
import json
import os
import re

NEW = r'G:\ultimatesolar'
SRC = os.path.join(NEW, 'src', 'main', 'java', 'com', 'example', 'solarpower')
DATA = os.path.join(NEW, 'src', 'main', 'resources', 'data', 'solarpower', 'recipe')

V = 'minecraft:'          # 原版前缀
S = 'solarpower:'         # 模组前缀


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


def shaped(result, count, pattern, **key):
    return {'type': 'minecraft:crafting_shaped', 'category': 'misc',
            'pattern': list(pattern),
            'key': {k: {'item': v} for k, v in key.items()},
            'result': {'id': result, 'count': count}}


def shapeless(result, count, *ingredients):
    return {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
            'ingredients': [{'item': i} for i in ingredients],
            'result': {'id': result, 'count': count}}


def scan_tiers():
    """SolarTier（有序：枚举名 → (方块 id, iuTier)）与 GlassCableTier（枚举名 → (id, 所属 SolarTier 枚举名)）。"""
    src = open(os.path.join(SRC, 'solar', 'SolarTier.java'), encoding='utf-8').read()
    panels = [(m.group(1), m.group(2), int(m.group(3))) for m in
              re.finditer(r'^    ([A-Z_0-9]+)\("([a-z_0-9]+)",\s*(\d+),', src, re.M)]
    assert len(panels) == 67, len(panels)
    src = open(os.path.join(SRC, 'solar', 'GlassCableTier.java'), encoding='utf-8').read()
    cables = [(m.group(1), m.group(2), m.group(3)) for m in
              re.finditer(r'^    ([A-Z_0-9]+)\("([a-z_0-9]+)",\s*[0-9.]+[DF],\s*'
                          r'SolarTier\.([A-Z_0-9]+),', src, re.M)]
    assert len(cables) == 68, len(cables)
    return panels, cables


def base_of(panel_id):
    """面板方块 id → 材料前缀（solar_panel → ''；advanced_solar_panel → 'advanced'）。"""
    suffix = '_solar_panel'
    return panel_id[:-len(suffix)] if panel_id.endswith(suffix) else panel_id


def emit(recipes):
    for result, obj in recipes.items():
        write_json(os.path.join(DATA, result + '.json'), obj)
    print('recipes written:', len(recipes))


def main():
    panels, cables = scan_tiers()
    pnames = [p[1] for p in panels]           # 有序面板方块 id
    pbase = {name: base_of(name) for _, name, _ in panels}
    solar_by_enum = {enum: (name, iut) for enum, name, iut in panels}

    def pane(t):        return 'basic_glass_pane' if t == 0 else pbase[pnames[t]] + '_glass_pane'
    def part(t):        return 'sunnarium_part' if t == 0 else pbase[pnames[t]] + '_sunnarium_part'
    def sunn(t):        return 'sunnarium' if t == 0 else pbase[pnames[t]] + '_sunnarium'
    def alloy(t):       return 'sunnarium_alloy' if t == 0 else pbase[pnames[t]] + '_sunnarium_alloy'
    def core(t):
        if t == 0:
            return None
        return 'quantum_core' if panels[t][0] == 'QUANTUM' else pbase[pnames[t]] + '_core'
    def panel(t):       return pnames[t]

    r = {}
    # ---------- ASP 基础材料（1.12.2 registerCrafting） ----------
    r['sunnarium'] = shaped(S + 'sunnarium', 1, ('SSS', 'SSS', 'SSS'), S=part(0))
    r['enriched_sunnarium'] = shaped(S + 'enriched_sunnarium', 1, ('UUU', 'USU', 'UUU'),
                                     U=S + 'irradiant_uranium', S=S + 'sunnarium')
    r['sunnarium_alloy'] = shaped(S + 'sunnarium_alloy', 1, ('III', 'ISI', 'III'),
                                  I=S + 'iridium_ingot', S=S + 'sunnarium')
    r['enriched_sunnarium_alloy'] = shaped(S + 'enriched_sunnarium_alloy', 1, (' S ', 'SAS', ' S '),
                                           S=S + 'enriched_sunnarium', A=S + 'sunnarium_alloy')
    r['iridium_iron_plate'] = shaped(S + 'iridium_iron_plate', 1, ('III', 'IPI', 'III'),
                                     I=V + 'iron_ingot', P=S + 'iridium_ingot')
    r['reinforced_iridium_iron_plate'] = shaped(S + 'reinforced_iridium_iron_plate', 1,
                                                ('ACA', 'CIC', 'ACA'),
                                                A=V + 'iron_ingot', C=V + 'coal_block',
                                                I=S + 'iridium_iron_plate')
    r['irradiant_uranium'] = shaped(S + 'irradiant_uranium', 1, (' G ', 'GUG', ' G '),
                                    G=V + 'glowstone_dust', U=S + 'uranium_ingot')
    r['irradiant_glass_pane'] = shaped(S + 'irradiant_glass_pane', 1, ('GGG', 'UDU', 'GGG'),
                                       G=V + 'glass_block', U=S + 'irradiant_uranium',
                                       D=V + 'glowstone_dust')
    r['irradiant_reinforced_plate'] = shaped(S + 'irradiant_reinforced_plate', 1,
                                             ('RSR', 'LIL', 'RDR'),
                                             R=V + 'redstone', S=part(0), L=V + 'yellow_dye',
                                             I=S + 'reinforced_iridium_iron_plate',
                                             D=V + 'diamond')
    r['mt_core'] = shaped(S + 'mt_core', 1, ('PRP', 'P P', 'PRP'),
                          P=S + 'irradiant_glass_pane', R=V + 'obsidian')
    r['quantum_core'] = shaped(S + 'quantum_core', 1, ('ANA', 'NEN', 'ANA'),
                               A=S + 'enriched_sunnarium_alloy', N=V + 'nether_star',
                               E=V + 'ender_eye')
    r['molecular_transformer'] = shaped(S + 'molecular_transformer', 1, ('MTM', 'CcC', 'MTM'),
                                        M=V + 'iron_block', T=V + 'redstone_block',
                                        C=V + 'gold_ingot', c=S + 'mt_core')

    # ---------- SSP 光谱组件（registerSspComponents） ----------
    r['greencomponent'] = shapeless(S + 'greencomponent', 1, S + 'irradiant_glass_pane')
    r['bluecomponent'] = shaped(S + 'bluecomponent', 1, ('AAA', 'ABA', 'AAA'),
                                A=V + 'glass_block', B=V + 'blue_dye')
    r['redcomponent'] = shaped(S + 'redcomponent', 1, ('AAA', 'ABA', 'AAA'),
                               A=V + 'glass_block', B=V + 'redstone')
    r['solarsplitter'] = shaped(S + 'solarsplitter', 1, ('ABC', 'ABC', 'ABC'),
                                A=S + 'redcomponent', B=S + 'greencomponent',
                                C=S + 'bluecomponent')

    # ---------- 面板材料链（registerPanelChain） ----------
    r['basic_glass_pane'] = shaped(S + 'basic_glass_pane', 1, ('GGG', 'GDG', 'GGG'),
                                   G=V + 'glass_block', D=V + 'glowstone_dust')
    for i in range(1, len(panels)):
        r[part(i)] = shaped(S + part(i), 1, ('F', 'P'),
                            F=S + 'enriched_sunnarium', P=S + part(i - 1))
        r[sunn(i)] = shaped(S + sunn(i), 1, ('SSS', 'SSS', 'SSS'), S=S + part(i))
        r[alloy(i)] = shaped(S + alloy(i), 1, ('III', 'ICI', 'III'),
                             I=S + 'iridium_ingot', C=S + sunn(i))
        # 量子核心走 ASP 官方配方（上面已注册），跳过通用核心配方
        if panels[i][0] != 'QUANTUM':
            if i == 1:
                r[core(i)] = shaped(S + core(i), 1, (' A ', 'CDC', ' A '),
                                    A=S + alloy(i), C=S + sunn(i), D=V + 'diamond')
            else:
                r[core(i)] = shaped(S + core(i), 1, (' A ', 'CKC', ' A '),
                                    A=S + alloy(i), C=S + sunn(i), K=S + core(i - 1))
    for i in range(1, len(panels)):
        if panels[i][0] == 'SPECTRAL':
            r[pane(i)] = shaped(S + pane(i), 1, ('U', 'S', 'P'),
                                U=S + alloy(i - 1), S=S + 'solarsplitter', P=S + pane(i - 1))
        else:
            r[pane(i)] = shaped(S + pane(i), 1, ('U', 'P'),
                                U=S + alloy(i - 1), P=S + pane(i - 1))
        r[panel(i)] = shaped(S + panel(i), 1, ('KPK', 'KCK', 'KPK'),
                             K=S + core(i), P=S + pane(i), C=S + panel(i - 1))
    r[panel(0)] = shaped(S + panel(0), 1, ('IGI', 'GPG', 'IGI'),
                         I=V + 'iron_ingot', G=V + 'glass_block', P=S + pane(0))

    # ---------- 电缆（registerCableRecipes，产出 ×6 起步） ----------
    # 原配方钻石粉/银粉为 IC2 矿辞，取原版替身：钻石 + 铁锭
    r['glass_cable'] = shaped(S + 'glass_cable', 6, ('GGG', 'DSD', 'GGG'),
                              G=V + 'glass_block', D=V + 'diamond', S=V + 'iron_ingot')
    r['advanced_glass_cable'] = shaped(S + 'advanced_glass_cable', 1, (' G ', 'BPB', ' G '),
                                       G=V + 'glass_block', B=S + 'glass_cable',
                                       P=S + part(1))
    cable_ids = [c[1] for c in cables]
    enum_to_idx = {p[0]: idx for idx, p in enumerate(panels)}
    for i in range(2, len(cables)):
        if cables[i][0] == 'ETERNAL_NIGHT':
            continue
        pt = enum_to_idx[cables[i][2]]
        r[cable_ids[i]] = shaped(S + cable_ids[i], 1, (' G ', 'BPB', ' G '),
                                 G=V + 'glass_block', B=S + cable_ids[i - 1],
                                 P=S + part(pt))
    emit(r)

    # ---------- 修复变压器配方的电缆原料（66 条全部指到了不存在的 id） ----------
    # 变压器方块 = representativePanel(iuTier).id() + "_transformer"（iuTier 是构造参数第 2 列，
    # 不等于声明序）；电缆 = 首条 partTier.iuTier 匹配的电缆（与 1.12.2 cableForIuTier 一致）。
    panel_by_iutier = {}
    for _enum, pid, iut in panels:
        panel_by_iutier.setdefault(iut, pid)
    cable_by_iutier = {}
    for _cname, cid, pt in cables:
        cable_by_iutier.setdefault(solar_by_enum[pt][1], cid)
    fixed = 0
    for t in range(1, 66):
        path = os.path.join(DATA, panel_by_iutier[t] + '_transformer.json')
        obj = json.load(open(path, encoding='utf-8'))
        want = S + cable_by_iutier[t]
        hit = [k for k, v in obj['key'].items() if 'glass_cable' in v.get('item', '')]
        assert len(hit) == 1, (path, hit)
        if obj['key'][hit[0]]['item'] != want:
            obj['key'][hit[0]]['item'] = want
            write_json(path, obj)
            fixed += 1
    print('transformer cable keys fixed:', fixed)


if __name__ == '__main__':
    main()
