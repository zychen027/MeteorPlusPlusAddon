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
    "PacketMine",
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

    private val ghostHandDelay = sgGhostHand.add(IntSetting.Builder()
        .name("switch-delay")
        .description("Delay between slot switch packets (ticks).")
        .defaultValue(2)
        .min(0)
        .max(5)
        .build()
    )

    private val ghostHandRandomize = sgGhostHand.add(BoolSetting.Builder()
        .name("randomize-slot")
        .description("Randomize slot selection to bypass GrimAC.")
        .defaultValue(true)
        .build()
    )

    private val ghostHandDecoyPackets = sgGhostHand.add(IntSetting.Builder()
        .name("decoy-packets")
        .description("Number of decoy packets to send.")
        .defaultValue(2)
        .min(0)
        .max(5)
        .build()
    )

    private val ghostHandSlotJitter = sgGhostHand.add(BoolSetting.Builder()
        .name("slot-jitter")
        .description("Add small random slot changes to bypass detection.")
        .defaultValue(true)
        .build()
    )

    private var currentTarget: MineTarget? = null
    private var ghostHandState = GhostHandState.IDLE
    private var ghostHandTimer = 0
    private var ghostHandTargetSlot = -1
    private var originalSlot = 0
    private var lastSwingTime = 0L
    private var renderProgress = 0.0

    override fun onActivate() {
        currentTarget = null
        ghostHandState = GhostHandState.IDLE
        renderProgress = 0.0
        originalSlot = mc.player?.inventory?.selectedSlot ?: 0
    }

    override fun onDeactivate() {
        resetTarget(abort = true)
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        currentTarget?.let { target ->
            renderProgress = target.progress.toDouble().coerceIn(0.0, 1.0)
        }

        when (mineMode.get()) {
            MineModeEnum.GhostHand -> handleGhostHandTick()
            else -> handleNormalTick()
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

    private fun handleGhostHandTick() {
        if (ghostHandTimer > 0) {
            ghostHandTimer--
            return
        }

        when (ghostHandState) {
            GhostHandState.IDLE -> {
                if (mc.options.attackKey.wasPressed()) {
                    handleInput()
                }
            }
            GhostHandState.STARTING -> handleGhostHandStarting()
            GhostHandState.MINING -> handleGhostHandMining()
            GhostHandState.FINISHING -> handleGhostHandFinishing()
        }
    }

    private fun handleGhostHandStarting() {
        val target = currentTarget ?: return run { resetTarget(abort = true) }
        val player = mc.player ?: return run { resetTarget(abort = true) }

        when {
            ghostHandTimer == 0 && ghostHandState == GhostHandState.STARTING -> {
                repeat(ghostHandDecoyPackets.get()) { sendDecoyPacket() }
                originalSlot = player.inventory.selectedSlot

                ghostHandTargetSlot = findBestToolSlot(target.blockState).let { slot ->
                    if (ghostHandRandomize.get() && slot != -1) {
                        (slot + Random.nextInt(-1, 2)).coerceIn(0, 8)
                    } else {
                        slot
                    }
                }

                ghostHandTimer = calculateDelay()
                if (abortOnSwitch()) sendAbort(target)
            }

            ghostHandTimer == 0 -> {
                if (ghostHandTargetSlot != -1) {
                    val actualSlot = if (ghostHandSlotJitter.get()) {
                        (ghostHandTargetSlot + Random.nextInt(-1, 2)).coerceIn(0, 8)
                    } else {
                        ghostHandTargetSlot
                    }
                    mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(actualSlot))
                }

                sendStart(target)
                if (shouldSwing()) swingHand()

                ghostHandState = GhostHandState.MINING
                target.started = true
                ghostHandTimer = calculateDelay()
            }
        }
    }

    private fun handleGhostHandMining() {
        val target = currentTarget ?: return run { resetTarget(abort = true) }

        target.updateProgress()
        renderProgress = target.progress.toDouble().coerceIn(0.0, 1.0)

        if (target.finished) {
            ghostHandState = GhostHandState.FINISHING
            ghostHandTimer = calculateDelay()
        } else if (shouldFakeSwing() && System.currentTimeMillis() - lastSwingTime > 200) {
            swingHand()
            lastSwingTime = System.currentTimeMillis()
        }
    }

    private fun handleGhostHandFinishing() {
        val target = currentTarget ?: return run { resetTarget(abort = true) }

        if (ghostHandTimer == 0 && ghostHandState == GhostHandState.FINISHING) {
            sendStop(target)
            ghostHandTimer = calculateDelay()
        } else if (ghostHandTimer == 0) {
            mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(originalSlot))
            resetTarget(abort = false)
        }
    }

    private fun sendDecoyPacket() {
        val player = mc.player ?: return
        val world = mc.world ?: return

        val pos = player.blockPos.offset(
            Direction.random(player.random),
            Random.nextInt(2, 5)
        )

        if (world.getBlockState(pos).isAir) return

        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                pos,
                Direction.random(player.random)
            )
        )

        if (Random.nextBoolean()) {
            mc.networkHandler?.sendPacket(
                PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                    pos,
                    Direction.random(player.random)
                )
            )
        }
    }

    private fun calculateDelay(): Int {
        val base = ghostHandDelay.get()
        return if (randomDelay.get()) base + Random.nextInt(0, 3) else base
    }

    private fun shouldSwing(): Boolean = swingHand.get() && Random.nextFloat() > 0.3f
    private fun shouldFakeSwing(): Boolean = fakeSwing.get() && Random.nextFloat() > 0.7f
    private fun abortOnSwitch(): Boolean = Random.nextBoolean()

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
            MineModeEnum.GhostHand -> {
                ghostHandState = GhostHandState.STARTING
                ghostHandTimer = calculateDelay()
                ghostHandTargetSlot = bestSlot
            }
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

            if (mineMode.get() == MineModeEnum.GhostHand) {
                ghostHandState = GhostHandState.STARTING
                ghostHandTimer = calculateDelay()
            }
        }
    }

    private fun resetTarget(abort: Boolean) {
        if (abort) currentTarget?.abort()
        currentTarget = null
        renderProgress = 0.0
        if (mineMode.get() == MineModeEnum.GhostHand) ghostHandState = GhostHandState.IDLE
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
    private enum class GhostHandState { IDLE, STARTING, MINING, FINISHING }
}
