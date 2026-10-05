# Ultimate Solar Panels（终极太阳能）

**Minecraft 1.21.1 / NeoForge 21.1.x 模组**——工业级档位太阳能发电与 EU 电网系统。
从 1.12.2 老模组完整移植并重制：67 档太阳能面板、全档玻璃电缆电网、储电盒 / 变压器 / 充电座 / 分子重组仪，以及完整的超压熔断与爆炸机制。

> Ultimate Solar — industrial-tier solar panels and an EU grid system for NeoForge 1.21.1.

## 版本要求

| 项目 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x（构建用 21.1.255） |
| Java | 21 |

## 内容一览

- **67 档太阳能面板**：从基础（32 EU/t）一路爬到天终（2^8990097 EU/t），蓄能与发电量全部走 `BigInteger`，不设数值上限；
- **68 档玻璃电缆**：六向自动连接、逐档线损与传输容量，构成真正的电压等级电网；
- **67 档储电盒**：容量 = 同档面板缓冲 ×64，拆除后电量随物品保留（DataComponents）；
- **67 档变压器**：关闭 / 自动 / 升压 / 降压四种工作模式，跨档转换带 5% 损耗；
- **67 档充电座**：纯受电，站上去即给全身 FE 装备充电，充电中顶面发光；
- **分子重组仪**：致敬 Advanced Solar Panels 的经典机器（煤炭 → 钻石等配方），带进度与比较器输出；
- **Forge Energy 桥**：所有设备对外暴露 FE（1 EU = 4 FE），可与 Mekanism 等其它模组互通。

## 电压体系

每档面板对应一个电压等级（L0~L66），机器与电缆按耐压判定兼容性，**与发电速度是两个量纲**：

| 面板 | 电压档 | 包上限 | 最大输出 |
|---|---|---|---|
| 基础 | L0 | 8 EU | 64 EU/t |
| 高级 | L1 (LV) | 32 EU | 256 EU/t |
| 混合 | L2 (MV) | 128 EU | 1024 EU/t |
| 完美 | L3 (HV) | 512 EU | 4096 EU/t |
| …… | 每档 ×4 | | |
| 天终 | L66 | 2^135 EU | 2^8990097 EU/t |

- **电缆**：容量 = 同档面板最大输出 ×4，带逐档线损；
- **变压器**（档位 = 工作电压 T）：桥接 T−1 ↔ T，升压吞吐 = 本档面板输出、降压吞吐 = 前一档面板输出，跨档转换损耗 5%；
- **超压即熔断**（按级差分五档）：差 1 级全网冒烟升温（满 5 秒烧断最热电缆）→ 差 2 级烧 32 节 → 差 3 级汽化 64 节 + 三连爆炸 → 差 ≥4 级整网连环爆炸（32 个爆点，越近半径越大）；
- **机器超压**：冒烟渐变暗红，持续 5 秒爆炸（中断源后 20 秒自然冷却获救）。

## 变压器模式

| 模式 | 行为 |
|---|---|
| 关闭 | 不收电、不输出（安全隔离，新放置默认） |
| 自动 | 双向转换：五面电（≤低一级）升到当前档从正面出；正面电（≤当前档）降到低一级从五面出 |
| 升压 | 只从五面收低一档电，转成当前档从正面输出 |
| 降压 | 只从正面收当前档电，降到低一级从五面输出 |

各面只收**不超过所在面额定**的电压，超面额定（接错档）会超压冒烟直至爆炸；输出面静默拒收。

## 安装

1. 安装 NeoForge 21.1.x 的 1.21.1 客户端/服务端；
2. 将 `ultimatesolar-1.0.0-mc1.21.1.jar` 放入 `mods` 目录；
3. 启动游戏。

## 从源码构建

```bash
# 需要 JDK 21
git clone https://github.com/1372704087/Ultimate-Solar-Panels.git
cd Ultimate-Solar-Panels
./gradlew build        # 产物在 build/libs/
```

> 仓库未附带 Gradle Wrapper 时，使用本地 Gradle 9.x 直接执行 `gradle build` 即可。

## 贴图与工具

全部贴图由 [`tools/texturegen`](tools/texturegen) 生成管线原创绘制（调色板见 `tier_palettes.json` / `cable_palettes.json`）：

```bash
py tools/texturegen/gen_tops.py            # 预览
py tools/texturegen/gen_tops.py --write    # 写入项目贴图
```

资产/配方/模型的 1.21.1 生成脚本见 [`tools`](tools)（`gen_assets_121.py`、`gen_recipes_121.py`）。

## 目录结构

```
src/main/java/...        模组代码（能量体系 / 机器 / 网络结算）
src/main/resources/      资产：blockstates、模型、lang、配方、贴图
tools/texturegen/        贴图生成管线（28 个脚本 + 调色板 + README）
tools/                   1.21.1 资产与配方生成脚本
```

## 致谢

- 工业时代 2（IC2）与 Industrial Upgrade——电压等级与变压器语义；
- Advanced Solar Panels——分子重组仪机制与素材；
- Super Solar Panels——光谱组件族素材。

## 许可

All Rights Reserved（详见 `src/main/resources/META-INF/neoforge.mods.toml`）。
