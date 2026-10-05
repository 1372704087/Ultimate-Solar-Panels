package com.example.solarpower.energy;

import com.example.solarpower.solar.GlassCableTier;
import com.example.solarpower.tileentity.GlassCableTile;
import com.example.solarpower.tileentity.SolarPanelTile;
import com.example.solarpower.tileentity.StorageBoxTile;
import com.example.solarpower.tileentity.TransformerTile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 简化版 EU 电缆网络（1.21.1）：把相连的玻璃电缆聚成一张网，网内相邻的
 * 太阳能板（源）向相邻的 {@link IEuEnergy} 接收端（汇）输电。
 * <p>规则（对齐 IU 的简化版）：
 * <ul>
 *     <li>整个网络每 tick 传输上限 = 电缆等级的 capacityPerTick；</li>
 *     <li>线损按网络内电缆根数 × 每根线损一次性扣除（不做逐包路径计算）；</li>
 *     <li>电压包等级 = 源（面板）的电压等级，超压的汇会自行拒收。</li>
 *     <li>网络的容量与线损取网内<b>最弱</b>一根电缆（混接按短板计）。</li>
 *     <li><b>超压五档</b>（按级差 = 来包电压档 − 电缆耐压档）：
 *     差 1 级整条网冒烟并累积温度，温度满（约 5 秒）熔断网内<b>最热</b>的电缆
 *     （烧断后立刻再接，新线冷、不会先烧，会接着烧下一根热的）；
 *     差 2 级从接入点起最多烧毁 32 节（无掉落）；
 *     差 3 级从接入点起最多汽化 64 节（无掉落），最近三节发生半径 2/1/0.5 的爆炸；
 *     差 ≥4 级接入点前 64 节汽化并连环爆炸（32 个爆点：半径 3×5、2×8、1×12、0.5×7）。
 *     FE 桥走 L0 包（最低档），永远不会触发。</li>
 * </ul>
 * <p>所有 EU 运算用 {@link BigInteger}，不设数值上限。
 * <p>除面板自行推流（{@link #push}）外，另有 {@link #drain} 与 {@link #fill} 两个桥接入口：
 * 玻璃电缆把它们暴露成 Forge Energy，外部 FE 线缆（Mekanism、其它 FE 机器）就能接到本模组电网上
 * 取电或送电。桥接时的电压包按最低等级（{@link EuTier#L0}）标记，避免汇被误判为超压。
 */
public final class EuCableNet {

    private static final int MAX_NET_SIZE = 4096;

    /** 网络结构缓存：同一 game tick 内按 anchor 命中，避免每块面板各 BFS 一遍。
     *  <p>只在服务端主线程使用（{@code push}/{@code drain}/{@code fill} 都已被 isClientSide 拦住），
     *  因此不需要同步。tick 变化即整表失效——网络结构可能因放置/拆除电缆而改变。 */
    private static Level NET_CACHE_LEVEL;
    private static long NET_CACHE_TICK = Long.MIN_VALUE;
    private static final Map<BlockPos, Net> NET_CACHE_ANCHOR = new HashMap<>();

    private EuCableNet() {
    }

    /**
     * 面板推流入口：扫描 {@code panelPos} 四周的相邻玻璃电缆，**每个相邻电缆面各推流一次**。
     *
     * <p>面板可能同时贴着同一张网的多根电缆（最多 6 个面）。此时该网每 tick 会收到
     * 多次推流，本板向该网的实际推送量可达「单面最大输出 × 相邻面数（最多 6）」——
     * 这是刻意保留的原始行为（放电按面数放大），不做按网络归并。
     *
     * <p>同 tick 内首次调用会 BFS 整张网，之后同网络的面板直接命中缓存。
     *
     * @param packetTier 电压包等级（源面板的电压等级）
     * @param maxOutput  单个相邻面每 tick 的最大输出（面板档位的「最大输出」），null 表示不限
     * @return 本次实际结算的面数（相邻的电缆面、且该网有汇）
     */
    public static int push(Level level, BlockPos panelPos, IEuEnergy source, EuTier packetTier,
                           BigInteger maxOutput) {
        if (level.isClientSide()) {
            return 0;
        }
        int settled = 0;
        for (Direction dir : Direction.values()) {
            BlockPos side = panelPos.relative(dir);
            if (!(level.getBlockEntity(side) instanceof GlassCableTile cableTile)) {
                continue;
            }
            if (handleOverVoltage(level, side, cableTile, packetTier)) {
                continue;
            }
            Net net = collect(level, side, packetTier);
            if (net.isEmpty() || net.sinks().isEmpty()) {
                continue;
            }
            settle(level, net, source, maxOutput);
            settled++;
        }
        return settled;
    }

    /**
     * 储电盒/变压器推流入口：IU 语义的「正面输出」——只扫 {@code facing} 方向上相邻
     * 的电缆，每 tick 至多向那一张网推流一次（不像面板那样按相邻面数放大）。
     * 推流时把设备自己从汇里排除，避免刚推出去的电又被自己吸回来。
     *
     * @return 本次是否实际结算（正面相邻电缆、且该网有其它汇）
     */
    public static int pushFromFacing(Level level, BlockPos devicePos, IEuEnergy source,
                                     EuTier packetTier, BigInteger maxOutput, Direction facing) {
        if (level.isClientSide()) {
            return 0;
        }
        BlockPos side = devicePos.relative(facing);
        if (!(level.getBlockEntity(side) instanceof GlassCableTile cableTile)) {
            return 0;
        }
        if (handleOverVoltage(level, side, cableTile, packetTier)) {
            return 0;
        }
        Net net = collect(level, side, packetTier);
        if (net.isEmpty() || net.sinks().isEmpty()) {
            return 0;
        }
        settle(level, net, source, maxOutput, devicePos);
        return 1;
    }

    private static BigInteger settle(Level level, Net net, IEuEnergy source, BigInteger maxOutput) {
        return settle(level, net, source, maxOutput, null);
    }

    /** 同上；{@code excludeSink} 非空时该坐标不参与汇分配（设备推流时排除自己）。 */
    private static BigInteger settle(Level level, Net net, IEuEnergy source, BigInteger maxOutput,
                                     BlockPos excludeSink) {
        if (net.isEmpty() || net.sinks().isEmpty()) {
            return BigInteger.ZERO;
        }
        // 先用「网络上限、源存量、面板最大输出」这三个只需廉价比较的值定出上限，
        // min 可交换，取最小值的顺序不影响结果；但源为空/上限为 0 时可就此返回，
        // 省掉整张网的空余量扫描（该项要对每个汇做一次大数相减）。
        BigInteger limit = net.capacityPerTick().min(source.getStoredEu());
        if (maxOutput != null) {
            limit = limit.min(maxOutput);
        }
        if (limit.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger budget = limit.min(demandOf(level, net, limit, excludeSink));
        if (budget.signum() <= 0) {
            return BigInteger.ZERO;
        }
        // 先向源抽取实付量（跨档转换损耗可能少于请求），再按实付分配给汇——
        // 保证任何源的损耗实现都不会凭空产生能量。
        // 语义注记：线损部分留在源蓄电内。稳态下源蓄电恒满，对外行为等价。
        BigInteger request = budget.subtract(lossOf(net, budget));
        if (request.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger paid = source.extractEu(request, false);
        if (paid.signum() <= 0) {
            return BigInteger.ZERO;
        }
        return fillSinks(level, net, paid, excludeSink);
    }

    /**
     * 桥接出电：从 anchor 网络相邻的源（太阳能板）抽 {@code amount} EU。
     *
     * @return 扣线损后实际送出的 EU
     */
    public static BigInteger drain(Level level, BlockPos anchor, BigInteger amount, boolean simulate) {
        if (level.isClientSide() || amount == null || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        Net net = collect(level, anchor, EuTier.L0);
        BigInteger available = storedOf(level, net);
        if (net.isEmpty() || available.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger budget = net.capacityPerTick().min(available).min(amount);
        if (budget.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger loss = lossOf(net, budget);
        if (simulate) {
            return budget.subtract(loss);
        }
        BigInteger moved = extractSources(level, net, budget);
        return moved.subtract(loss.min(moved));
    }

    /**
     * 桥接进电：把 {@code amount} EU 送进 anchor 网络相邻的汇。
     *
     * @return 扣线损后实际送达的 EU
     */
    public static BigInteger fill(Level level, BlockPos anchor, BigInteger amount, boolean simulate) {
        if (level.isClientSide() || amount == null || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        Net net = collect(level, anchor, EuTier.L0);
        if (net.isEmpty() || net.sinks().isEmpty()) {
            return BigInteger.ZERO;
        }
        // 同 push：先用常量与请求量定出上限，再拿它去扫汇空余（见 demandOf 的说明）。
        BigInteger limit = net.capacityPerTick().min(amount);
        if (limit.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger budget = limit.min(demandOf(level, net, limit, null));
        if (budget.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger delivered = budget.subtract(lossOf(net, budget));
        return simulate ? delivered : fillSinks(level, net, delivered, null);
    }

    /** 网络相邻源（太阳能板）的存量之和，夹到本档每 tick 上限；供电缆对外报告"可抽取量"。 */
    public static BigInteger storedEu(Level level, BlockPos anchor) {
        if (level.isClientSide()) {
            return BigInteger.ZERO;
        }
        Net net = collect(level, anchor, EuTier.L0);
        return net.isEmpty() ? BigInteger.ZERO : storedOf(level, net).min(net.capacityPerTick());
    }

    /** 从 anchor 出发 BFS 收集整个电缆网络（成员坐标 + 邻接源/汇）。
     *  <p>结果按 {@code level.getGameTime()} 缓存，并把整张网络的**每个成员坐标**
     *  都登记为 key：同一 tick 内同网络的面板无论从哪根电缆接入都命中同一份结果。 */
    private static Net collect(Level level, BlockPos anchor, EuTier packetTier) {
        long now = level.getGameTime();
        if (NET_CACHE_LEVEL == level && NET_CACHE_TICK == now) {
            Net cached = NET_CACHE_ANCHOR.get(anchor);
            if (cached != null) {
                // 缓存里只存「结构」（成员、源、汇候选），电压包等级与本次调用相关：
                // 汇是否收电依赖 packetTier，必须按本次等级重新过滤，不能复用上一调用方的结果。
                return cached.resolveSinks(level, packetTier);
            }
        } else {
            // 进入新 tick：整表失效（网络结构可能因放置/拆除而变）
            NET_CACHE_ANCHOR.clear();
            NET_CACHE_LEVEL = level;
            NET_CACHE_TICK = now;
        }

        GlassCableTile anchorCable = cableAt(level, anchor);
        if (anchorCable == null) {
            Net empty = Net.EMPTY.resolveSinks(level, packetTier);
            NET_CACHE_ANCHOR.put(anchor, Net.EMPTY);
            return empty;
        }

        // --- 第一遍：BFS 收集电缆成员 ---
        List<BlockPos> cables = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(anchor);
        seen.add(anchor);
        while (!queue.isEmpty() && seen.size() <= MAX_NET_SIZE) {
            BlockPos pos = queue.poll();
            cables.add(pos);
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (seen.add(next) && cableAt(level, next) != null) {
                    queue.add(next);
                }
            }
        }

        // --- 第二遍：扫描每个电缆的非电缆邻居，分类为源 / 汇候选 ---
        List<BlockPos> sources = new ArrayList<>();
        Set<BlockPos> sourceSeen = new HashSet<>();
        Set<BlockPos> sinkSeen = new HashSet<>();
        List<BlockPos> sinkCandidates = new ArrayList<>();
        for (BlockPos cable : cables) {
            for (Direction dir : Direction.values()) {
                BlockPos side = cable.relative(dir);
                if (cableAt(level, side) != null) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(side);
                if (be instanceof SolarPanelTile) {
                    if (sourceSeen.add(side)) {
                        sources.add(side);
                    }
                    continue;
                }
                // 储电盒：正面朝向这根电缆时按源计（IU 正面输出语义）；同时它暴露
                // IEuEnergy，落到下面的汇候选分支（五面进电）。
                if (be instanceof StorageBoxTile
                        && ((StorageBoxTile) be).facing() == dir.getOpposite()
                        && sourceSeen.add(side)) {
                    sources.add(side);
                }
                // 变压器：任一面都可作为抽取源（FE 桥 drain 用）；按来包电压分流进双池
                if (be instanceof TransformerTile && sourceSeen.add(side)) {
                    sources.add(side);
                }
                if (be instanceof IEuEnergy && sinkSeen.add(side)) {
                    sinkCandidates.add(side);
                }
            }
        }

        // --- 网络额定值：取网内最弱一根电缆（先比耐压，同压比容量），耐压/容量/线损都由它决定 ---
        GlassCableTier weakest = anchorCable.tier();
        for (BlockPos cablePos : cables) {
            GlassCableTier cableTier = cableAt(level, cablePos).tier();
            if (weaker(cableTier, weakest)) {
                weakest = cableTier;
            }
        }

        // 缓存结构（成员/源/汇候选）；汇的实际收电力每次都按调用方给的 packetTier 重判
        Net base = new Net(weakest, cables, sources, sinkCandidates);
        // 整张网络的每个成员都指向同一份结构：这样同网络的面板无论从哪根电缆接入都能命中
        for (BlockPos cable : cables) {
            NET_CACHE_ANCHOR.put(cable, base);
        }
        return base.resolveSinks(level, packetTier);
    }

    /** 网络相邻汇的空余容量之和；{@code excludeSink} 非空时跳过（设备推流排除自己）。 */
    private static BigInteger demandOf(Level level, Net net, BigInteger limit, BlockPos excludeSink) {
        BigInteger demand = BigInteger.ZERO;
        for (BlockPos pos : net.sinks()) {
            if (pos.equals(excludeSink)) {
                continue;
            }
            IEuEnergy energy = energyAt(level, pos);
            if (energy != null) {
                demand = demand.add(energy.getRoomEu());
                if (demand.compareTo(limit) >= 0) {
                    return limit;
                }
            }
        }
        return demand;
    }

    /** 网络相邻源的存量之和（面板/储电盒/变压器）。 */
    private static BigInteger storedOf(Level level, Net net) {
        BigInteger total = BigInteger.ZERO;
        for (BlockPos pos : net.sources()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SolarPanelTile panel) {
                total = total.add(panel.getEnergy().getStoredEu());
            } else if (be instanceof StorageBoxTile box) {
                total = total.add(box.getStoredEu());
            } else if (be instanceof TransformerTile transformer) {
                total = total.add(transformer.getStoredEu());
            }
        }
        return total;
    }

    /** 线损：网络内电缆根数 × 每根线损，夹到不超过 {@code amount}。 */
    private static BigInteger lossOf(Net net, BigInteger amount) {
        if (amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        long lossAmount = Math.round(net.cables().size() * net.weakest().lossPerBlock());
        return BigInteger.valueOf(lossAmount).min(amount);
    }

    /** 按汇平均分配 {@code amount}，返回实际被接收的 EU。
     *  <p>份额用 {@link #shareOf}：只有 1 个汇时该份额根本用不到（唯一一次循环落到
     *  "最后一个汇"分支），直接从除法里省掉；2/4/8 个汇改走移位。
     *  <p>单个汇的空余走 {@link IEuEnergy#getRoomEu()}，与 {@code receiveEu} 内部的
     *  夹取共用同一份缓存，不额外多算一次大数相减。 */
    private static BigInteger fillSinks(Level level, Net net, BigInteger amount, BlockPos excludeSink) {
        int count = 0;
        for (BlockPos pos : net.sinks()) {
            if (!pos.equals(excludeSink)) {
                count++;
            }
        }
        if (count == 0 || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger perSink = count == 1 ? amount : shareOf(amount, count);
        BigInteger sent = BigInteger.ZERO;
        int index = 0;
        for (BlockPos pos : net.sinks()) {
            if (pos.equals(excludeSink)) {
                continue;
            }
            IEuEnergy energy = energyAt(level, pos);
            if (energy != null) {
                BigInteger room = energy.getRoomEu();
                // 「最后一个有效汇拿余额」语义保持：index 只数未被排除的汇
                BigInteger share = index == count - 1 ? amount.subtract(sent).min(room) : perSink.min(room);
                if (share.signum() > 0) {
                    sent = sent.add(energy.receiveEuOnFace(
                            cableFaceOf(level, pos, net), share, net.packetTier(), false));
                }
            }
            index++;
        }
        return sent;
    }

    /** 按源平均抽取 {@code amount}，返回实际抽出的 EU（面板走内部储能，储电盒/变压器直接抽）。 */
    private static BigInteger extractSources(Level level, Net net, BigInteger amount) {
        int count = net.sources().size();
        if (count == 0 || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger perSource = count == 1 ? amount : shareOf(amount, count);
        BigInteger taken = BigInteger.ZERO;
        for (int i = 0; i < count; i++) {
            BlockEntity be = level.getBlockEntity(net.sources().get(i));
            BigInteger share = i == count - 1 ? amount.subtract(taken) : perSource;
            if (be instanceof SolarPanelTile panel) {
                taken = taken.add(panel.getEnergy().extractEu(share, false));
            } else if (be instanceof StorageBoxTile box) {
                taken = taken.add(box.extractEu(share, false));
            } else if (be instanceof TransformerTile transformer) {
                taken = taken.add(transformer.extractEu(share, false));
            }
        }
        return taken;
    }

    /**
     * 均分份额 {@code amount / count}（向下取整）。
     * <p>调用方已保证 {@code amount} 为正，故 2/4/8 用算术右移与整除逐位等价，
     * 而 {@link BigInteger#shiftRight} 只按字长搬位、不做除法循环。
     * <p>另外 {@code divide(ONE)} 在 JDK 8/21 里**并不会**被短路，
     * 所以只有一个汇/源时必须在上层直接跳过除法。
     */
    private static BigInteger shareOf(BigInteger amount, int count) {
        switch (count) {
            case 2:
                return amount.shiftRight(1);
            case 4:
                return amount.shiftRight(2);
            case 8:
                return amount.shiftRight(3);
            default:
                return amount.divide(BigInteger.valueOf(count));
        }
    }

    private static GlassCableTile cableAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof GlassCableTile ? (GlassCableTile) be : null;
    }

    private static IEuEnergy energyAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof IEuEnergy ? (IEuEnergy) be : null;
    }

    /** 机器入电的面：取其相邻的任意玻璃电缆方向（同一张网的电缆彼此等价）。 */
    /** 机器入电的面：取其相邻且属于本次结算网络的玻璃电缆方向（多网混接按本次网判定）。 */
    private static Direction cableFaceOf(Level level, BlockPos machine, Net net) {
        for (Direction dir : Direction.values()) {
            BlockPos side = machine.relative(dir);
            if (net.cables().contains(side) && level.getBlockEntity(side) instanceof GlassCableTile) {
                return dir;
            }
        }
        return null;
    }

    /** 超压熔断：烧毁这根电缆（烧毁无掉落），并清空本 tick 的网络缓存。 */
    private static void blowCable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        level.destroyBlock(pos, false);
        level.levelEvent(2001, pos, Block.getId(state));
        NET_CACHE_ANCHOR.clear();
    }

    /** 5~10 倍档单次熔断的最大节数。 */
    private static final int MAX_FUSE_SEGMENTS = 32;

    /** 10~20 倍档单次汽化的最大节数。 */
    private static final int MAX_VAPORIZE_SEGMENTS = 64;

    /** 超压 5~10 倍：从接入点起沿网烧毁最多 {@value #MAX_FUSE_SEGMENTS} 节电缆（无掉落）。 */
    private static void blowNet(Level level, Net net) {
        int burned = 0;
        for (BlockPos pos : net.cables()) {
            if (burned >= MAX_FUSE_SEGMENTS) {
                break;
            }
            if (level.getBlockEntity(pos) instanceof GlassCableTile) {
                BlockState state = level.getBlockState(pos);
                level.destroyBlock(pos, false);
                level.levelEvent(2001, pos, Block.getId(state));
                burned++;
            }
        }
        NET_CACHE_ANCHOR.clear();
    }

    /** 超压 10~20 倍：从接入点起最多汽化 {@value #MAX_VAPORIZE_SEGMENTS} 节（不掉落），
     *  并在最近三节发生半径 2/1/0.5 的爆炸（范围伤害/破坏方块）。 */
    private static void explodeNet(Level level, Net net) {
        List<BlockPos> cables = net.cables();
        int vaporized = 0;
        for (BlockPos pos : cables) {
            if (vaporized >= MAX_VAPORIZE_SEGMENTS) {
                break;
            }
            if (level.getBlockEntity(pos) instanceof GlassCableTile) {
                level.destroyBlock(pos, false);
                vaporized++;
            }
        }
        NET_CACHE_ANCHOR.clear();
        float[] radii = {2.0F, 1.0F, 0.5F};
        for (int i = 0; i < radii.length && i < cables.size(); i++) {
            BlockPos pos = cables.get(i);
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    radii[i], Level.ExplosionInteraction.BLOCK);
        }
    }

    /** &gt;20 倍档爆点分布：半径 3×5 → 2×8 → 1×12 → 0.5×7，共 32 个（越近越大）。 */
    private static final float[] DETONATION_RADII = {
            3.0F, 3.0F, 3.0F, 3.0F, 3.0F,                       // 5 × 半径 3
            2.0F, 2.0F, 2.0F, 2.0F, 2.0F, 2.0F, 2.0F, 2.0F,     // 8 × 半径 2
            1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F,
            1.0F, 1.0F, 1.0F,                                    // 12 × 半径 1
            0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F             // 7 × 半径 0.5
    };

    /** 超压 20 倍以上：接入点前 64 节汽化（不掉落），32 个爆点每隔一节布置、由近及远衰减。 */
    private static void detonateNet(Level level, Net net) {
        List<BlockPos> cables = net.cables();
        int zone = Math.min(MAX_VAPORIZE_SEGMENTS, cables.size());
        for (int i = 0; i < zone; i++) {
            BlockPos pos = cables.get(i);
            if (level.getBlockEntity(pos) instanceof GlassCableTile) {
                level.destroyBlock(pos, false);
            }
        }
        NET_CACHE_ANCHOR.clear();
        int blasts = Math.min(DETONATION_RADII.length, zone);
        for (int i = 0; i < blasts; i++) {
            BlockPos pos = cables.get(Math.min(i * 2, zone - 1));
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    DETONATION_RADII[i], Level.ExplosionInteraction.BLOCK);
        }
    }

    /** 2 倍内：整条网暴露加热（温度累积）。烟由各电缆自身的 tick 按
     *  {@link OverVoltage#spawnOverheatSmoke} 随温度渐变显示，冷却期亦持续可见。 */
    private static void heatNet(Level level, Net net) {
        long now = level.getGameTime();
        for (BlockPos pos : net.cables()) {
            if (level.getBlockEntity(pos) instanceof GlassCableTile tile) {
                tile.heatUp(now);
            }
        }
    }

    /**
     * 超压五档处理（按级差 = 来包电压档 − 电缆耐压档）：
     * 差 1 级整条网冒烟 + 温度累积（满了熔断网内最热电缆）；差 2 级从接入点烧 32 节；
     * 差 3 级汽化最近 64 节 + 三连爆炸；差 ≥4 级整条网汽化并连环爆炸。
     *
     * @return true = 本次推送被熔断/爆炸拦截，调用方跳过 settle
     */
    private static boolean handleOverVoltage(Level level, BlockPos anchorPos,
                                             GlassCableTile cableTile, EuTier packetTier) {
        int levels = packetTier.iuTier() - ratedIuTier(cableTile);
        if (levels <= 0) {
            return false;
        }
        Net net = collect(level, anchorPos, packetTier);
        if (net.isEmpty()) {
            return true;
        }
        if (levels >= 4) {
            detonateNet(level, net);             // ≥4 级差：整条网连环爆炸
        } else if (levels == 3) {
            explodeNet(level, net);              // 3 级差：汽化最近 64 节 + 三连爆炸
        } else if (levels == 2) {
            blowNet(level, net);                 // 2 级差：从接入点烧 32 节
        } else {
            heatNet(level, net);                 // 1 级差：整条冒烟 + 温度累积
            long now = level.getGameTime();
            boolean tripped = false;
            BlockPos hottest = null;
            int hottestHeat = -1;
            for (BlockPos pos : net.cables()) {
                if (level.getBlockEntity(pos) instanceof GlassCableTile tile) {
                    if (tile.heatUp(now)) {
                        tripped = true;
                    }
                    // 并列时取 BFS 序更靠前的（离接入点更近）
                    if (tile.heat() > hottestHeat) {
                        hottestHeat = tile.heat();
                        hottest = pos;
                    }
                }
            }
            if (tripped && hottest != null) {
                // 熔断网内最热的电缆：刚接上的冷线不会被优先烧，再接上就接着烧下一根热的
                blowCable(level, hottest);
            }
        }
        return true;
    }

    /** 电缆的额定电压（iuTier）= 其面板档的电压等级。 */
    private static int ratedIuTier(GlassCableTile cable) {
        return cable.tier().source().voltage().iuTier();
    }

    /** a 是否比 b 更弱：先比电压等级，同压再比容量——网络短板由此决定。 */
    private static boolean weaker(GlassCableTier a, GlassCableTier b) {
        int va = a.source().voltage().iuTier();
        int vb = b.source().voltage().iuTier();
        if (va != vb) {
            return va < vb;
        }
        return a.capacityPerTick().compareTo(b.capacityPerTick()) < 0;
    }

    /** 一张电缆网络的结构快照：额定值、成员电缆坐标、邻接源坐标与邻接能量方块（汇候选）。
     *  <p>额定值取网内<b>最弱</b>一根电缆（混接按短板计）：传输被它的容量限流，
     *  线损也按它的每格线损计；超压熔断只作用于直接接源的电缆（见 {@link #blowCable}）。
     *  <p>汇候选是「所有暴露 IEuEnergy 的邻接方块」，是否真能收电取决于电压包等级，
     *  由 {@link #resolveSinks} 按当次调用的 tier 过滤——因此本对象可跨不同 packetTier 复用。 */
    public static final class Net {

        static final Net EMPTY = new Net(null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        /** 网内最弱电缆（容量/线损的短板）；EMPTY 网为 null。 */
        private final GlassCableTier weakest;
        private final List<BlockPos> cables;
        private final List<BlockPos> sources;
        private final List<BlockPos> sinkCandidates;
        /** 本次调用实际可收电的汇（由 resolveSinks 按 packetTier 过滤得出）。 */
        private final EuTier packetTier;
        private final List<BlockPos> sinks;

        /** 结构快照（尚无 packetTier/sinks，由 {@link #resolveSinks} 派生）。 */
        Net(GlassCableTier weakest, List<BlockPos> cables,
            List<BlockPos> sources, List<BlockPos> sinkCandidates) {
            this(weakest, EuTier.L0, cables, sources, sinkCandidates,
                    java.util.Collections.emptyList());
        }

        private Net(GlassCableTier weakest, EuTier packetTier, List<BlockPos> cables,
                    List<BlockPos> sources, List<BlockPos> sinkCandidates, List<BlockPos> sinks) {
            this.weakest = weakest;
            this.packetTier = packetTier;
            this.cables = cables;
            this.sources = sources;
            this.sinkCandidates = sinkCandidates;
            this.sinks = sinks;
        }

        boolean isEmpty() {
            return this.cables.isEmpty();
        }

        /** 按电压包等级过滤汇候选，产出本次调用可用的汇集合。 */
        Net resolveSinks(Level level, EuTier packetTier) {
            if (this.sinkCandidates.isEmpty()) {
                return new Net(this.weakest, packetTier, this.cables,
                        this.sources, this.sinkCandidates, java.util.Collections.emptyList());
            }
            List<BlockPos> sinks = new ArrayList<>(this.sinkCandidates.size());
            for (BlockPos pos : this.sinkCandidates) {
                IEuEnergy energy = energyAt(level, pos);
                if (energy != null && energy.receiveEu(BigInteger.ONE, packetTier, true).signum() > 0) {
                    sinks.add(pos);
                }
            }
            return new Net(this.weakest, packetTier, this.cables,
                    this.sources, this.sinkCandidates, sinks);
        }

        /** 每 tick 传输上限 = 最弱电缆的容量（EMPTY 网为 0）。 */
        BigInteger capacityPerTick() {
            return this.weakest == null ? BigInteger.ZERO : this.weakest.capacityPerTick();
        }

        GlassCableTier weakest() {
            return this.weakest;
        }

        EuTier packetTier() {
            return this.packetTier;
        }

        public List<BlockPos> cables() {
            return this.cables;
        }

        List<BlockPos> sources() {
            return this.sources;
        }

        public List<BlockPos> sinks() {
            return this.sinks;
        }
    }
}
