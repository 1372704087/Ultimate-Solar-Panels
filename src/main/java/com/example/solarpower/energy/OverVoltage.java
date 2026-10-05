package com.example.solarpower.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * 机器超压温度跟踪：收到超过自身电压等级的包即升温（每 tick 冒烟，随温度渐变）；
 * 温度满 {@link #FUSE_TICKS}（连续暴露约 5 秒）由持有方执行爆炸；
 * 暴露中断不清零——按升温 1/4 的速率冷却（约 20 秒凉透），冷却期持续冒烟渐稀渐黑，
 * 抢救及时可避免爆炸。
 */
public final class OverVoltage {

    /** 爆炸阈值：温度满 100（连续暴露约 5 秒）。 */
    public static final int FUSE_TICKS = 100;

    /** 冷却减速：无暴露时每 4 tick 才降 1 点温度。 */
    private static final int COOL_SLOWDOWN = 4;

    private int heat;
    private int coolAcc;
    private boolean flagged;

    /** 本次 tick 内机器拒收了超压包时调用（拒收路径，含汇候选的模拟询问）。 */
    public void flag() {
        this.flagged = true;
    }

    /** 每 tick 开头调用：暴露升温、无暴露按 1/4 速率降温。
     *  返回 true = 温度已满，应爆炸。温度 &gt; 0 期间持续冒烟（随温度渐变）。 */
    public boolean tick(Level level, BlockPos pos) {
        if (this.flagged) {
            this.heat = Math.min(FUSE_TICKS, this.heat + 1);
            this.coolAcc = 0;
        } else if (this.heat > 0) {
            if (++this.coolAcc >= COOL_SLOWDOWN) {
                this.coolAcc = 0;
                this.heat--;
            }
        }
        this.flagged = false;
        if (this.heat > 0 && level instanceof ServerLevel server
                && server.getGameTime() % 2 == 0) {
            spawnOverheatSmoke(server, pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5,
                    this.heat / (float) FUSE_TICKS);
        }
        return this.heat >= FUSE_TICKS;
    }

    /**
     * 过热烟雾：{@code heat} 0→1 由黑烟渐变为暗红——
     * 黑烟随温度升高逐渐减少，暗红尘埃（颜色由灰红加深、体积变大、密度上升）
     * 从 40% 起出现，接近爆点时整团以暗红为主。
     */
    public static void spawnOverheatSmoke(ServerLevel level, double x, double y, double z, float heat) {
        heat = Math.max(0.0F, Math.min(1.0F, heat));
        int smoke = heat < 0.7F ? 2 : 1;
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, smoke, 0.25, 0.3, 0.25, 0.01);
        if (heat > 0.4F) {
            float t = (heat - 0.4F) / 0.6F;
            Vector3f color = new Vector3f(0.25F + 0.30F * t, 0.05F * (1 - t), 0.0F);
            int dust = 1 + Math.round(t * 3);
            level.sendParticles(new DustParticleOptions(color, 1.2F + t),
                    x, y + 0.1, z, dust, 0.2, 0.25, 0.2, 0.0);
        }
    }
}
