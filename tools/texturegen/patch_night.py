# -*- coding: utf-8 -*-
# [遗留·一次性] 已应用于旧 1.12.2 工程（夜间发电 IU 化 + 夜间 LED 蓝色化）。
# ROOT 指向旧仓库；勿改指新仓库运行——其中的 Java 补丁是按 1.12.2 代码结构写的。
# Night generation (IU-style) + sky-blue night LED:
#   gui texture: sky-blue lit LED sprite at v=24
#   SolarTier / BlockEntity / Menu / Screen + solar-sim.html
from PIL import Image
import io, re, os

ROOT = r'G:\solarpower'

# ---------- 1) GUI texture ----------
tex_path = os.path.join(ROOT, r'src/main/resources/assets/solarpower/textures/gui/solar_panel.png')
im = Image.open(tex_path).convert('RGBA')
px = im.load()
if px[220, 24][3] == 0:
    mapping = {
        (120, 246, 146, 255): (115, 205, 246, 255),   # body green -> sky blue
        (198, 255, 200, 255): (198, 238, 255, 255),   # top-left bevel
        (46, 54, 46, 255):    (44, 52, 62, 255),      # dark ring
        (126, 255, 150, 90):  (120, 200, 255, 90),    # glow
    }
    for y in range(0, 10):
        for x in range(220, 230):
            px[x, y + 24] = mapping.get(px[x, y], px[x, y])
    im.save(tex_path)
    print('texture: night LED added at v=24')
else:
    print('texture: night LED already present')

def patch(rel, pairs):
    p = os.path.join(ROOT, rel)
    s = io.open(p, encoding='utf-8').read()
    for old, new in pairs:
        assert old in s, rel + ': pattern not found -> ' + old[:60]
        s = s.replace(old, new, 1)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('patched', rel)

# ---------- 2) SolarTier ----------
patch(r'src/main/java/com/example/solarpower/solar/SolarTier.java', [
    (''' * <p>不包含 IU 的日光/月光/日夜玻璃与模块系统；夜里不发电。''',
     ''' * <p>夜间（月光）发电量按 IU 规则为白天的一半，不足 1 EU/t 的档位（基础）夜晚不发电；
 * 不包含 IU 的月亮相位/太阳系数/日夜玻璃与模块加成系统。'''),
    ('''    /** 满日照下的发电量（EU/t）。 */
    public long generationEu() {
        return this.generationEu;
    }''',
     '''    /** 满日照下的发电量（EU/t）。 */
    public long generationEu() {
        return this.generationEu;
    }

    /** 夜间（月光）发电量（EU/t）：IU 规则为白天的一半，向下取整且不足 1 归零。 */
    public long nightGenerationEu() {
        long night = this.generationEu / 2L;
        return night < 1L ? 0L : night;
    }'''),
])

# ---------- 3) SolarPanelBlockEntity ----------
patch(r'src/main/java/com/example/solarpower/blockentity/SolarPanelBlockEntity.java', [
    (''' *     <li>只有白天、晴天、且上方能看到天空（有天空光照的维度）时才发电，夜里不发电；''',
     ''' *     <li>有天空光照的维度且上方能看到天空时发电；白天按档位发电，夜晚（月光）为白天的一半，
 *         基础档位夜晚不发电；'''),
    ('''    /** 当前发电量（EU/t），仅用于界面显示。 */
    public long getGenerating() {
        return this.generating;
    }''',
     '''    /** 当前发电量（EU/t），仅用于界面显示。 */
    public long getGenerating() {
        return this.generating;
    }

    /** 当前是否为夜晚（仅用于界面指示灯配色）。 */
    public boolean isNight() {
        return this.level != null && !this.level.isDay();
    }'''),
    ('''    /** 判断当前环境能否发电，规则对齐 IC2 太阳能板。 */
    private long computeGeneration(Level level, BlockPos pos) {
        if (!level.dimensionType().hasSkyLight()) {
            return 0L; // 下界/末地这类没有天空光照的维度
        }
        if (!level.isDay()) {
            return 0L; // 夜晚
        }
        if (level.isRaining() || level.isThundering()) {
            return 0L; // 雨天与雷暴
        }
        if (!level.canSeeSky(pos.above())) {
            return 0L; // 上方被方块遮挡
        }
        return this.tier.generationEu();
    }''',
     '''    /** IU 雨水系数（EnumType.DEFAULT.coefficient_rain）：雨天/雷暴不归零，而是乘 0.65。 */
    private static final double RAIN_COEFFICIENT = 0.65D;

    /**
     * 判断当前环境能否发电，规则对齐 IU 的 {@code TileSolarPanel}：
     * 有天空光照的维度且上方能看到天空才发电；白天按档位发电，夜晚（月光）为白天的一半；
     * 雨天与雷暴不归零，而是乘 0.65。
     */
    private long computeGeneration(Level level, BlockPos pos) {
        if (!level.dimensionType().hasSkyLight()) {
            return 0L; // 下界/末地这类没有天空光照的维度
        }
        if (!level.canSeeSky(pos.above())) {
            return 0L; // 上方被方块遮挡
        }
        long base = level.isDay() ? this.tier.generationEu() : this.tier.nightGenerationEu();
        return level.isRaining() || level.isThundering() ? (long) (base * RAIN_COEFFICIENT) : base;
    }'''),
])

# ---------- 4) Menu ----------
patch(r'src/main/java/com/example/solarpower/menu/SolarPanelMenu.java', [
    ('''    public static final int DATA_GENERATING = 2;
    private static final int DATA_COUNT = 3;''',
     '''    public static final int DATA_GENERATING = 2;
    public static final int DATA_NIGHT = 3;
    private static final int DATA_COUNT = 4;'''),
    ('''                        case DATA_GENERATING -> (int) panel.getGenerating();
                        default -> 0;''',
     '''                        case DATA_GENERATING -> (int) panel.getGenerating();
                        case DATA_NIGHT -> panel.isNight() ? 1 : 0;
                        default -> 0;'''),
    ('''    public long getGenerating() {
        return this.data.get(DATA_GENERATING);
    }''',
     '''    public long getGenerating() {
        return this.data.get(DATA_GENERATING);
    }

    /** 是否处于夜晚（用于界面指示灯：白天绿、夜晚天蓝）。 */
    public boolean isNight() {
        return this.data.get(DATA_NIGHT) != 0;
    }'''),
])

# ---------- 5) Screen ----------
patch(r'src/main/java/com/example/solarpower/client/SolarPanelScreen.java', [
    ('''    private static final int LED_U = 220;
    private static final int LED_LIT_V = 0;
    private static final int LED_DIM_V = 12;''',
     '''    private static final int LED_U = 220;
    private static final int LED_LIT_V = 0;
    private static final int LED_NIGHT_V = 24;
    private static final int LED_DIM_V = 12;'''),
    ('''        guiGraphics.blit(TEXTURE,
                this.leftPos + LED_X,
                this.topPos + LED_Y,
                LED_U, this.menu.getGenerating() > 0L ? LED_LIT_V : LED_DIM_V,
                LED_SIZE, LED_SIZE);''',
     '''        // 发电中：白天绿灯，夜晚（月光发电）天蓝灯；熄灭为暗灯
        long generating = this.menu.getGenerating();
        int ledV = generating > 0L ? (this.menu.isNight() ? LED_NIGHT_V : LED_LIT_V) : LED_DIM_V;
        guiGraphics.blit(TEXTURE,
                this.leftPos + LED_X,
                this.topPos + LED_Y,
                LED_U, ledV,
                LED_SIZE, LED_SIZE);'''),
])

# ---------- 6) solar-sim.html ----------
def night_entry(m):
    gen = int(m.group('gen'))
    night = gen // 2
    return ('{{ zh: "{zh}", iu: {iu}, gen: {gen}, cap: {cap}, night: {night}, tex: "{tex}" }}'
            .format(zh=m.group('zh'), iu=m.group('iu'), gen=gen, cap=m.group('cap'),
                    night=night, tex=m.group('tex')))

p = os.path.join(ROOT, 'solar-sim.html')
s = io.open(p, encoding='utf-8').read()
s, n = re.subn(
    r'\{ zh: "(?P<zh>[^"]+)", iu: (?P<iu>\d+), gen: (?P<gen>\d+), cap: (?P<cap>\d+), tex: "(?P<tex>[^"]+)" \}',
    night_entry, s)
assert n == 15, n
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('solar-sim.html TIERS night values added:', n)

patch('solar-sim.html', [
    ('var LED = 10, LED_U = 220, LED_DIM_V = 12;',
     'var LED = 10, LED_U = 220, LED_LIT_V = 0, LED_NIGHT_V = 24, LED_DIM_V = 12;'),
    ('    elLed.style.backgroundPosition = "-" + (LED_U * S) + "px " + (r.gen > 0 ? "0px" : "-" + (LED_DIM_V * S) + "px");',
     '''    var ledV = r.gen > 0 ? (r.night ? LED_NIGHT_V : LED_LIT_V) : LED_DIM_V;
    elLed.style.backgroundPosition = "-" + (LED_U * S) + "px -" + (ledV * S) + "px";'''),
    ('''  function evaluate() {
    var sky     = state.dim === "overworld";
    var day     = (state.time % 24000) < 12000 && state.dim === "overworld";
    var weather = !(state.weather === "rain" || state.weather === "thunder");
    var view    = !state.covered;
    var gen = (sky && day && weather && view) ? cur().gen : 0;
    return { sky: sky, day: day, weather: weather, view: view, gen: gen };
  }''',
     '''  function evaluate() {
    var sky     = state.dim === "overworld";
    var day     = (state.time % 24000) < 12000 && state.dim === "overworld";
    var weather = !(state.weather === "rain" || state.weather === "thunder");
    var view    = !state.covered;
    var night   = !day;
    // IU 规则：夜晚（月光）为白天的一半；雨天/雷暴不归零而是 ×0.65
    var base = day ? cur().gen : cur().night;
    var gen = (sky && view) ? (weather ? base : Math.floor(base * 0.65)) : 0;
    return { sky: sky, day: day, night: night, weather: weather, view: view, gen: gen };
  }'''),
    ('''    { id: "day",     code: "if (!level.isDay())                      return 0;" },
    { id: "weather", code: "if (level.isRaining() || level.isThundering()) return 0;" },''',
     '''    { id: "day",     code: "long base = level.isDay() ? tier.generationEu() : tier.nightGenerationEu();" },
    { id: "weather", code: "if (level.isRaining() || level.isThundering()) base *= 0.65D;" },'''),
    ('''  function reason(r) {
    if (!r.sky)     return "维度没有天空光照（下界 / 末地），不发电";
    if (!r.day)     return "当前是夜晚，太阳能板不工作";
    if (!r.weather) return "正在下雨 / 雷暴，输出被天气取消";
    if (!r.view)    return "上方被方块遮挡，接收不到阳光";
    return "日照充足，正在以 " + fmt(r.gen) + " EU/t 充电（" + voltage(cur().iu)[0] + "）";
  }''',
     '''  function reason(r) {
    var v = voltage(cur().iu)[0];
    if (!r.sky)     return "维度没有天空光照（下界 / 末地），不发电";
    if (!r.view)    return "上方被方块遮挡，接收不到阳光";
    if (!r.day)     return "夜晚，该档位没有月光发电（夜间发电要求白天 ≥ 2 EU/t）";
    if (!r.weather) return (r.night ? "雨夜，月光发电 ×0.65：" : "雨天，发电 ×0.65：")
                            + fmt(r.gen) + " EU/t（" + v + "）";
    return r.night ? "夜晚，月光模式下以 " + fmt(r.gen) + " EU/t 充电（" + v + "）"
                   : "日照充足，正在以 " + fmt(r.gen) + " EU/t 充电（" + v + "）";
  }'''),
    ('不含日光/月光机制，夜晚不发电，暂未加入合成表',
     '夜晚按 IU 月光机制以白天一半功率发电（雨天 ×0.65，基础档夜晚不发电），暂未加入合成表'),
    ('下界/末地在真实 mod 中因 <code>dimensionType().hasSkyLight()</code> 为 false 而直接返回 0；夜晚、雨天、被遮挡同样不发电。',
     '下界/末地在真实 mod 中因 <code>dimensionType().hasSkyLight()</code> 为 false 而直接返回 0；'
     '被遮挡不发电；夜晚为白天功率的一半（基础档无夜间发电）；雨天/雷暴按 IU 系数 ×0.65。'),
])
print('all done')
