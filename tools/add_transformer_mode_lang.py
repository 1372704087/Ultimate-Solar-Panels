# -*- coding: utf-8 -*-
"""变压器放电方向模式：向 zh_cn.json / en_us.json 补按钮与 tooltip 键（幂等，保序保缩进）。"""
import io
import json
import os

LANG = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                     '..', 'src', 'main', 'resources',
                                     'assets', 'solarpower', 'lang'))

ADD = {
    'zh_cn': {
        'gui.solarpower.transformer.mode.0': '模式：双向（按来压分流）',
        'gui.solarpower.transformer.mode.1': '模式：升压（低压转高压）',
        'gui.solarpower.transformer.mode.2': '模式：降压（高压转低压）',
        'tooltip.solarpower.transformer_mode': '可在界面底部按钮切换放电方向：双向 / 升压（低压转高压）/ 降压（高压转低压）',
    },
    'en_us': {
        'gui.solarpower.transformer.mode.0': 'Mode: Auto (route by input)',
        'gui.solarpower.transformer.mode.1': 'Mode: Step-up (LV to HV)',
        'gui.solarpower.transformer.mode.2': 'Mode: Step-down (HV to LV)',
        'tooltip.solarpower.transformer_mode': 'Toggle the discharge direction with the button below: Auto / Step-up (LV to HV) / Step-down (HV to LV)',
    },
}

for code, entries in ADD.items():
    path = os.path.join(LANG, code + '.json')
    with io.open(path, encoding='utf-8') as f:
        table = json.load(f)
    missing = {k: v for k, v in entries.items() if k not in table}
    if not missing:
        print('%s: already up to date' % code)
        continue
    table.update(missing)
    with io.open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(json.dumps(table, indent=2, ensure_ascii=False) + '\n')
    print('%s: added %d keys' % (code, len(missing)))
