package com.zychen027.meteorplusplus.modules

import meteordevelopment.meteorclient.events.render.Render2DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.player.InvUtils
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.BlockState
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.Direction
import com.zychen027.meteorplusplus.utils.MineTarget
import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import kotlin.random.Random

class PacketMineModule : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "发包挖掘",
    "高级数据包挖矿，支持 GrimAC 绕过"
) {

    private val sgGeneral = settings.getDefaultGroup()
    private val sgBypass = settings.createGroup("Bypass")
    private val sgGhostHand = settings.createGroup("GhostHand")

    private val range = sgGeneral.add(DoubleSetting.Builder()
        .name("range")
        .description("Mining range.")
        .defaultValue(4.5)
        .min(1.0)
        .max(6.0)
        .build()
    )

    private val mineMode = sgGeneral.add(EnumSetting.Builder<MineModeEnum>()
        .name("mode")
        .description("Mining mode.")
        .defaultValue(MineModeEnum.Normal)
        .build()
    )

    private val showProgress = sgGeneral.add(BoolSetting.Builder()
        .name("show-progress")
        .description("Show mining progress at crosshair.")
        .defaultValue(true)
        .build()
    )

    private val randomDelay = sgBypass.add(BoolSetting.Builder()
        .name("random-delay")
        .description("Add random delay to appear more human.")
        .defaultValue(true)
        .build()
    )

    private val swingHand = sgBypass.add(BoolSetting.Builder()
        .name("swing-hand")
        .description("Swing hand when mining.")
        .defaultValue(true)
        .build()
    )

    private val fakeSwing = sgBypass.add(BoolSetting.Builder()
        .name("fake-swing")
        .description("Send extra swing packets to confuse anti-cheat.")
        .defaultValue(true)
        .build()
    )

    private val ghostHand = sgGhostHand.add(BoolSetting.Builder()
        .name("GhostHand")
        .description("Use GhostHand mode to bypass anti-cheat.")
        .defaultValue(false)
        .build())

    private val ghostHandDelay = sgGhostHand.add(IntSetting.Builder()
        .name("SwitchDelay")
        .description("Delay between slot switch (ticks).")
        .defaultValue(2)
        .min(0)
        .max(10)
        .build())

    private val ghostHandRandomize = sgGhostHand.add(BoolSetting.Builder()
        .name("RandomizeSlot")
        .description("Randomize slot selection.")
        .defaultValue(true)
        .build())

    private var originalSlot = 0
    private var currentSlot = -1
    private var switchTimer = 0
    private var swapped = false
    private var currentTarget: MineTarget? = null
    private var lastSwingTime = 0L
    private var renderProgress = 0.0

    override fun onActivate() {
        currentTarget = null
        originalSlot = mc.player?.inventory?.selectedSlot ?: 0
        currentSlot = -1
        switchTimer = 0
        swapped = false
        renderProgress = 0.0
    }

    override fun onDeactivate() {
        // 恢复原始槽位
        if (swapped && mc.player?.inventory?.selectedSlot != originalSlot) {
            mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(originalSlot))
            mc.player?.inventory?.selectedSlot = originalSlot
        }
        resetTarget(abort = true)
        swapped = false
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        currentTarget?.let { target ->
            renderProgress = target.progress.toDouble().coerceIn(0.0, 1.0)
        }

        // 处理 GhostHand 槽位切换
        if (ghostHand.get()) {
            handleGhostHand()
        }

        when (mineMode.get()) {
            MineModeEnum.GhostHand -> handleGhostHandMining()
            else -> handleNormalTick()
        }
    }

    /**
     * GhostHand 槽位切换逻辑（参考 Aura.kt 的 autoSwitch）
     */
    private fun handleGhostHand() {
        if (switchTimer > 0) {
            switchTimer--
            return
        }

        val target = currentTarget ?: return
        val player = mc.player ?: return

        // 找到最佳工具槽位
        val bestSlot = findBestToolSlot(target.blockState)
        
        if (bestSlot == -1) {
            // 没有找到合适的工具，恢复原始槽位
            if (swapped) {
                mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(originalSlot))
                player.inventory.selectedSlot = originalSlot
                swapped = false
            }
            return
        }

        // 随机化槽位选择
        val targetSlot = if (ghostHandRandomize.get()) {
            (bestSlot + Random.nextInt(-1, 2)).coerceIn(0, 8)
        } else {
            bestSlot
        }

        // 切换槽位（类似 Aura.kt 的 autoSwitch）
        if (!swapped) {
            originalSlot = player.inventory.selectedSlot
            swapped = true
        }

        if (targetSlot != currentSlot) {
            mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(targetSlot))
            player.inventory.selectedSlot = targetSlot
            currentSlot = targetSlot
            switchTimer = ghostHandDelay.get()
        }
    }

    @EventHandler
    private fun onRender2D(event: Render2DEvent) {
        if (!showProgress.get() || currentTarget == null) return
        if (mc.player == null || mc.world == null) return

        val scaledWidth = event.screenWidth
        val scaledHeight = event.screenHeight

        // 准星位置（屏幕中心）
        val centerX = scaledWidth / 2
        val centerY = scaledHeight / 2

        val percentage = (renderProgress * 100).toInt()
        val text = "$percentage%"

        // 根据进度选择颜色 (RGBA)
        val r: Int
        val g: Int
        val b: Int
        when {
            percentage < 30 -> { r = 255; g = 80; b = 80 }
            percentage < 70 -> { r = 255; g = 220; b = 50 }
            else -> { r = 80; g = 255; b = 80 }
        }

        // 获取文本渲染器
        val textRenderer = mc.textRenderer

        // 文本偏移位置（准星右侧）
        val textX = centerX + 10
        val textY = centerY - 4

        // 绘制背景框
        val padding = 4
        val boxX = textX - padding
        val boxY = textY - padding
        val boxWidth = textRenderer.getWidth(text) + padding * 2
        val boxHeight = textRenderer.fontHeight + padding * 2

        // 绘制半透明黑色背景 (ARGB: 0x80000000)
        event.drawContext.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x80000000.toInt())

        // 绘制边框 (ARGB: 0xFFRRGGBB)
        val borderColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        event.drawContext.drawBorder(boxX, boxY, boxWidth, boxHeight, borderColor)

        // 绘制进度条背景
        val barX = boxX + padding
        val barY = boxY + boxHeight + 2
        val barWidth = boxWidth - padding * 2
        val barHeight = 3

        event.drawContext.fill(barX, barY, barX + barWidth, barY + barHeight, 0x80000000.toInt())

        // 绘制进度条填充
        val fillWidth = (barWidth * renderProgress).toInt()
        if (fillWidth > 0) {
            val fillColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            event.drawContext.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor)
        }

        // 绘制文本
        event.drawContext.drawText(textRenderer, text, textX, textY, borderColor, true)
    }

    private fun handleNormalTick() {
        if (mc.options.attackKey.wasPressed()) {
            handleInput()
        }

        currentTarget?.let { target ->
            if (target.isInvalidOrOutOfRange(range.get())) {
                resetTarget(abort = true)
                return
            }

            target.updateBlockState()
            if (target.blockState.isAir) {
                resetTarget(abort = false)
                return
            }

            if (!target.started) {
                startBreaking(target)
            }

            target.updateProgress()

            if (target.finished) {
                InvUtils.swapBack()
                resetTarget(abort = false)
            }
        }
    }

    /**
     * GhostHand 挖掘处理（简化版）
     */
    private fun handleGhostHandMining() {
        val target = currentTarget ?: return

        // 更新挖掘进度
        target.updateProgress()
        renderProgress = target.progress.toDouble().coerceIn(0.0, 1.0)

        // 挖掘完成
        if (target.finished) {
            sendStop(target)
            // 恢复原始槽位
            if (swapped) {
                mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(originalSlot))
                mc.player?.inventory?.selectedSlot = originalSlot
                swapped = false
                currentSlot = -1
            }
            resetTarget(abort = false)
            return
        }

        // 假挥动手臂
        if (shouldFakeSwing() && System.currentTimeMillis() - lastSwingTime > 200) {
            swingHand()
            lastSwingTime = System.currentTimeMillis()
        }
    }

    private fun calculateDelay(): Int {
        val base = ghostHandDelay.get()
        return if (randomDelay.get()) base + Random.nextInt(0, 3) else base
    }

    private fun shouldSwing(): Boolean = swingHand.get() && Random.nextFloat() > 0.3f
    private fun shouldFakeSwing(): Boolean = fakeSwing.get() && Random.nextFloat() > 0.7f

    private fun swingHand() {
        mc.player?.swingHand(if (Random.nextBoolean()) Hand.MAIN_HAND else Hand.OFF_HAND)
        lastSwingTime = System.currentTimeMillis()
    }

    private fun sendStart(target: MineTarget) {
        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                target.targetPos,
                target.direction
            )
        )
    }

    private fun sendStop(target: MineTarget) {
        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                target.targetPos,
                target.direction
            )
        )
    }

    private fun sendAbort(target: MineTarget) {
        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                target.targetPos,
                target.direction
            )
        )
    }

    private fun startBreaking(target: MineTarget) {
        target.direction = Direction.UP
        val bestSlot = findBestToolSlot(target.blockState)

        when (mineMode.get()) {
            MineModeEnum.Immediate -> {
                if (bestSlot != -1) InvUtils.swap(bestSlot, false)
                sendStart(target)
                sendStop(target)
                if (swingHand.get()) swingHand()
                target.started = true
                target.finished = true
            }
            else -> {
                if (bestSlot != -1) InvUtils.swap(bestSlot, false)
                sendStart(target)
                if (swingHand.get()) swingHand()
                target.started = true
            }
        }
    }

    private fun handleInput() {
        val hitResult = mc.crosshairTarget as? BlockHitResult ?: return
        val pos = hitResult.blockPos

        if (currentTarget?.targetPos == pos) {
            resetTarget(abort = true)
        } else {
            currentTarget?.abort()
            currentTarget = MineTarget(pos)
        }
    }

    private fun resetTarget(abort: Boolean) {
        if (abort) currentTarget?.abort()
        currentTarget = null
        renderProgress = 0.0
    }

    private fun findBestToolSlot(state: BlockState): Int {
        val player = mc.player ?: return -1
        var bestSlot = -1
        var bestSpeed = 0f

        for (i in 0..8) {
            val stack = player.inventory.getStack(i)
            if (stack.isEmpty) continue

            val speed = stack.getMiningSpeedMultiplier(state)
            if (speed > bestSpeed) {
                bestSpeed = speed
                bestSlot = i
            }
        }

        return bestSlot
    }

    enum class MineModeEnum { Normal, GhostHand, Immediate, Civ }
}
