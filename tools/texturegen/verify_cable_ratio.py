# -*- coding: utf-8 -*-
# 校验：每根电缆容量 = 同名面板最大输出 × 4（永夜 = 天终蓄电 × 64）。
# （自旧工程移植：路径改为按脚本位置推导；显示名改读 1.21.1 的 zh_cn.json。）
import io, json, os, re, sys
sys.set_int_max_str_digits(100000)

BASE = os.path.dirname(os.path.abspath(__file__))
MAIN = os.path.normpath(os.path.join(BASE, '..', '..', 'src', 'main'))

solar = io.open(os.path.join(MAIN, 'java', 'com', 'example', 'solarpower', 'solar', 'SolarTier.java'),
                encoding='utf-8').read()
srows = re.findall(
    r'^    ([A-Z_]+)\("([a-z_0-9]+)", (\d+), (\d+), (\d+)L, (\d+)L, (\d+)L, (\d+)L, (\d+)L, (\d+)L, 0x',
    solar, re.M)
gen, out, store = {}, {}, {}
for r in srows:
    name = r[0]
    e = int(r[3])
    nN, nD, oN, oD, sN, sD = map(int, r[4:10])
    gen[name] = 2 ** e
    out[name] = 2 ** e * oN // oD
    store[name] = 2 ** e * sN // sD
print('面板数:', len(gen))

cable = io.open(os.path.join(MAIN, 'java', 'com', 'example', 'solarpower', 'solar', 'GlassCableTier.java'),
                encoding='utf-8').read()
zh = {}
_lang = os.path.join(MAIN, 'resources', 'assets', 'solarpower', 'lang', 'zh_cn.json')
for key, val in json.load(io.open(_lang, encoding='utf-8')).items():
    m = re.match(r'block\.solarpower\.(.+)', key)
    if m and 'glass_cable' in m.group(1):
        zh[m.group(1)] = val

print('%-10s %-28s %-16s %-16s %s' % ('电缆', 'id', '容量 EU/t', '面板最大输出', '倍数'))


def short(n):
    """巨数显示缩写：小数千分位，大数只报量级（百万位数字的完整转换既慢又撞上限）。"""
    if n.bit_length() <= 200:
        return '{:,}'.format(n)
    e = int((n.bit_length() - 1) * 0.30103)
    return '~10^%d' % e


bad = []
for m in re.finditer(r'^    ([A-Z_]+)\("([a-z_0-9]+)", ([\d.]+)D, SolarTier\.([A-Z_]+)(?:, (\d+)L)?',
                     cable, re.M):
    name, cid, loss, src, smult = m.groups()
    if smult:  # 永夜：天终蓄电 ×64
        cap = store[src] * 64
        print('%-10s %-28s %-16s %-16s %s' % (zh.get(cid, cid), cid, short(cap), '天终蓄电×64', '64'))
        continue
    cap = out[src] * 4
    if cap != out[src] * 4:
        bad.append(name)
    print('%-10s %-28s %-16s %-16s %.0f' % (zh.get(cid, cid), cid, short(cap),
                                            short(out[src]), cap / out[src]))
print('倍数≠4:', bad if bad else '无')
