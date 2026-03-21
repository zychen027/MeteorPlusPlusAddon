package com.zychen027.meteorplusplus.modules.elytraautopilot.hud

import com.zychen027.meteorplusplus.modules.elytraautopilot.ElytraAutoPilot
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.network.ClientPlayerEntity

/**
 * HUD 显示 - 来自 ElytraAutoPilot
 * 适配 MC 1.21.8 Matrix3x2fStack
 */
object Hud {
    private var tickCounter = 0

    /**
     * HUD tick
     */
    fun tick() {
        tickCounter++
    }

    /**
     * 绘制 HUD
     */
    fun drawHud(context: DrawContext, textRenderer: TextRenderer, player: ClientPlayerEntity) {
        val module = ElytraAutoPilot.INSTANCE ?: return
        
        if (!module.showGui.get()) return

        val lines = mutableListOf<String>()

        // 启用状态
        if (module.showEnabled.get()) {
            val status = when {
                ElytraAutoPilot.autoFlight -> "§a 自动飞行中"
                ElytraAutoPilot.onTakeoff -> "§e 起飞中"
                else -> "§c 已禁用"
            }
            lines.add("状态：$status")
        }

        // 高度
        if (module.showAltitude.get()) {
            lines.add("高度：${player.y.toInt()}")
        }

        // 高度要求
        if (module.showHeightReq.get()) {
            lines.add("最低高度：${module.minHeight.get()}")
        }

        // 速度
        if (module.showSpeed.get()) {
            val speed = String.format("%.2f", ElytraAutoPilot.currentVelocity)
            lines.add("速度：$speed")
        }

        // 平均速度
        if (module.showAvgSpeed.get()) {
            val avgSpeed = String.format("%.2f", (ElytraAutoPilot.velHigh + ElytraAutoPilot.velLow) / 2)
            lines.add("平均速度：$avgSpeed")
        }

        // 水平速度
        if (module.showHorizontalSpeed.get()) {
            val hSpeed = String.format("%.2f", ElytraAutoPilot.currentVelocityHorizontal)
            lines.add("水平速度：$hSpeed")
        }

        // 飞行目标
        if (module.showFlyTo.get() && ElytraAutoPilot.isflytoActive) {
            lines.add("目标：x=${ElytraAutoPilot.argXpos}, z=${ElytraAutoPilot.argZpos}")
        }

        // 预计到达时间
        if (module.showEta.get() && ElytraAutoPilot.isflytoActive) {
            val distance = ElytraAutoPilot.distance
            val eta = if (ElytraAutoPilot.currentVelocity > 0.01) {
                String.format("%.1f", distance / ElytraAutoPilot.currentVelocity / 20)
            } else "∞"
            lines.add("预计时间：${eta}s")
        }

        // 自动降落
        if (module.showAutoLand.get()) {
            val landStatus = when {
                ElytraAutoPilot.forceLand -> "§c 强制降落"
                ElytraAutoPilot.isLanding -> "§e 降落中"
                else -> "§a 就绪"
            }
            lines.add("降落：$landStatus")
        }

        // 绘制 - 适配 MC 1.21.8 Matrix3x2fStack
        val x = module.guiX.get()
        val y = module.guiY.get()
        val scale = module.guiScale.get() / 100.0f

        val matrices = context.matrices
        matrices.pushMatrix()
        matrices.scale(scale, scale)

        for ((i, line) in lines.withIndex()) {
            context.drawTextWithShadow(
                textRenderer,
                line,
                (x / scale).toInt(),
                (y / scale).toInt() + i * 10,
                0xFFFFFF
            )
        }

        matrices.popMatrix()
    }
}
