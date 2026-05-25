package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.text.Text

/**
 * YLevelProtect (Y 高度保护)
 * 当玩家 Y 坐标低于设定阈值时自动断开连接，防止掉入虚空或被服务器惩罚。
 *
 * 模式说明：
 * - 简单模式 (Simple): 只要 Y 坐标低于阈值，立即断开连接。
 * - 智能模式 (Smart):
 *   - 如果玩家处于鞘翅滑行状态 (`isGliding`) 且 Y < 阈值，进入 5 秒（可配置）等待期。
 *   - 如果在等待期内 Y 坐标回升到阈值以上，取消断开。
 *   - 如果等待期结束后 Y 坐标仍低于阈值，断开连接。
 *   - 如果玩家未处于滑行状态且 Y < 阈值，立即断开连接（视为意外坠落）。
 */
class YLevelProtect : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "YLevelProtect",
    "Y 坐标过低时自动断开连接，防止虚空伤害。"
) {
    private val sgGeneral = settings.getDefaultGroup()

    private val mode = sgGeneral.add(EnumSetting.Builder<Mode>()
        .name("Mode")
        .description("保护模式：简单模式立即断开，智能模式在滑行时提供缓冲时间。")
        .defaultValue(Mode.Simple)
        .build())

    private val threshold = sgGeneral.add(DoubleSetting.Builder()
        .name("Threshold")
        .description("触发断开连接的 Y 坐标阈值。")
        .defaultValue(0.0)
        .sliderMax(100.0)
        .sliderMin(-100.0)
        .build())

    private val waitTime = sgGeneral.add(IntSetting.Builder()
        .name("SmartWaitTime")
        .description("智能模式下，滑行且 Y 低于阈值时的等待时间（秒）。")
        .defaultValue(5)
        .min(1)
        .sliderMax(20)
        .visible { mode.get() == Mode.Smart }
        .build())

    // 状态变量
    private var warningStartTime = 0L
    private var isWarning = false

    override fun onActivate() {
        isWarning = false
        warningStartTime = 0
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        if (mc.player == null) return

        val y = mc.player!!.y
        val thresh = threshold.get()

        if (y < thresh) {
            when (mode.get()) {
                Mode.Simple -> {
                    disconnect("Y 坐标过低 (Simple Mode)")
                }
                Mode.Smart -> {
                    if (mc.player!!.isGliding) {
                        // 如果正在滑翔，进入警告/等待状态
                        if (!isWarning) {
                            isWarning = true
                            warningStartTime = System.currentTimeMillis()
                        } else {
                            // 检查是否超时
                            val elapsed = System.currentTimeMillis() - warningStartTime
                            if (elapsed > waitTime.get() * 1000L) {
                                disconnect("滑行时 Y 坐标过低且超时 (Smart Mode)")
                            }
                        }
                    } else {
                        // 没在滑翔，直接断开（视为意外坠落）
                        disconnect("意外坠落 (Smart Mode)")
                    }
                }
            }
        } else {
            // 如果回到了安全高度，重置警告状态
            if (isWarning) {
                isWarning = false
                warningStartTime = 0
            }
        }
    }

    private fun disconnect(reason: String) {
        info("§c$reason")
        // MC 1.21.8: disconnect is on the connection
        mc.networkHandler?.connection?.disconnect(Text.literal(reason))
    }

    enum class Mode {
        Simple,
        Smart
    }
}
