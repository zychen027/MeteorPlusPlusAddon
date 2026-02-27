package com.zychen027.MeteorPlusPlus.modules

import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.player.InvUtils
import meteordevelopment.meteorclient.utils.render.NametagUtils
import meteordevelopment.meteorclient.utils.render.color.Color
import meteordevelopment.meteorclient.renderer.text.TextRenderer
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.BlockState
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.joml.Vector3d
import com.zychen027.MeteorPlusPlus.MeteorPlusPlusAddon
import com.zychen027.MeteorPlusPlus.utils.MineTarget
import kotlin.random.Random

class PacketMineModule : Module(
    MeteorPlusPlusAddon.PACKETMINE_CATEGORY,
    "PacketMine",
    "Advanced packet mining with GrimAC bypass"
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

    private val textScale = sgGeneral.add(DoubleSetting.Builder()
        .name("text-scale")
        .description("Scale of the progress text.")
        .defaultValue(1.0)
        .min(0.5)
        .max(2.0)
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

    override fun onActivate() {
        currentTarget = null
        ghostHandState = GhostHandState.IDLE
        originalSlot = mc.player?.inventory?.selectedSlot ?: 0
    }

    override fun onDeactivate() {
        resetTarget(abort = true)
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        when (mineMode.get()) {
            MineModeEnum.GhostHand -> handleGhostHandTick()
            else -> handleNormalTick()
        }
    }

    @EventHandler
    private fun onRender(event: Render3DEvent) {
        renderProgress()
    }

    private fun renderProgress() {
        val target = currentTarget ?: return

        val percentage = (target.progress.toDouble().coerceIn(0.0, 1.0) * 100).toInt()
        val text = "$percentage%"

        val scale: Double = textScale.get()

        val pos = Vector3d(
            target.targetPos.x.toDouble() + 0.5,
            target.targetPos.y.toDouble() + 1.2,
            target.targetPos.z.toDouble() + 0.5
        )

        // to2D expects (Vector3d, Double)
        if (!NametagUtils.to2D(pos, scale)) return

        val color = when {
            percentage < 30 -> Color(255, 80, 80)
            percentage < 70 -> Color(255, 220, 50)
            else -> Color(80, 255, 80)
        }

        val tr = TextRenderer.get()
        // begin expects (Double, Boolean, Boolean)
        tr.begin(scale, false, true)

        // getWidth/getHeight return Float — cast to Double for render()
        val width = tr.getWidth(text, false).toDouble()
        val height = tr.getHeight(false).toDouble()

        // render expects (String, Double, Double, Color, Boolean)
        tr.render(text, -width / 2.0, -height / 2.0, color, true)

        tr.end()
        NametagUtils.end()
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
