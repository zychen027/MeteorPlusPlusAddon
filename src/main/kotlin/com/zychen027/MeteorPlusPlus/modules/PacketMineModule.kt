package com.zychen027.MeteorPlusPlus.modules

import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.player.InvUtils
import meteordevelopment.meteorclient.utils.render.color.Color
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.BlockState
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import com.zychen027.MeteorPlusPlus.MeteorPlusPlusAddon
import com.zychen027.MeteorPlusPlus.utils.MineTarget
import kotlin.random.Random

class PacketMineModule : Module(
    MeteorPlusPlusAddon.PACKETMINE_CATEGORY,
    "PacketMine",
    "PacketMine module ported from LiquidBounce with enhanced anti-cheat bypass",
    *emptyArray<String>()
) {

    private val sgGeneral = settings.getDefaultGroup()
    private val sgBypass = settings.createGroup("Bypass")

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

    // Bypass settings
    private val slotSwitchDelay = sgBypass.add(IntSetting.Builder()
        .name("slot-switch-delay")
        .description("Delay between slot switch packets (ticks).")
        .defaultValue(1)
        .min(0)
        .max(5)
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

    private val abortOnSwitch = sgBypass.add(BoolSetting.Builder()
        .name("abort-on-switch")
        .description("Send abort packet before switching slots.")
        .defaultValue(true)
        .build()
    )

    private val spoofRotation = sgBypass.add(BoolSetting.Builder()
        .name("spoof-rotation")
        .description("Spoof rotation towards the block.")
        .defaultValue(false)
        .build()
    )

    private var currentTarget: MineTarget? = null
    private var ghostHandPhase = 0
    private var ghostHandDelay = 0
    private var ghostHandSlot = -1

    override fun onActivate() {
        currentTarget = null
        ghostHandPhase = 0
        ghostHandDelay = 0
    }

    override fun onDeactivate() {
        currentTarget?.abort()
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        // Handle GhostHand phase delays
        if (ghostHandDelay > 0) {
            ghostHandDelay--
            return
        }

        if (mc.options.attackKey.wasPressed()) {
            handleInput()
        }

        val target = currentTarget ?: return

        if (target.isInvalidOrOutOfRange(range.get())) {
            currentTarget = null
            ghostHandPhase = 0
            return
        }

        target.updateBlockState()
        if (target.blockState.isAir) {
            currentTarget = null
            ghostHandPhase = 0
            return
        }

        if (!target.started) {
            startBreaking(target)
        }

        // Handle GhostHand phases
        if (mineMode.get() == MineModeEnum.GhostHand && ghostHandPhase > 0) {
            handleGhostHandPhase(target)
            return
        }

        target.updateProgress()
        
        if (target.finished) {
            if (mineMode.get() != MineModeEnum.GhostHand) {
                InvUtils.swapBack()
            }
            currentTarget = null
            ghostHandPhase = 0
        }
    }

    @EventHandler
    private fun onRender(event: Render3DEvent) {
        val target = currentTarget ?: return
        val color = Color(255, 255, 0, 100)
        event.renderer.box(target.targetPos, color, color, ShapeMode.Both, 0)
    }

    private fun handleInput() {
        val hitResult = mc.crosshairTarget
        if (hitResult is BlockHitResult) {
            val pos = hitResult.blockPos
            if (currentTarget?.targetPos == pos) {
                currentTarget?.abort()
                currentTarget = null
                ghostHandPhase = 0
            } else {
                currentTarget = MineTarget(pos)
                ghostHandPhase = 0
            }
        }
    }

    private fun startBreaking(target: MineTarget) {
        target.direction = Direction.UP
        val bestSlot = findBestToolSlot(target.blockState)

        when (mineMode.get()) {
            MineModeEnum.GhostHand -> handleGhostHandStart(target, bestSlot)
            MineModeEnum.Immediate -> handleImmediateStart(target, bestSlot)
            MineModeEnum.Civ -> handleCivStart(target, bestSlot)
            else -> handleNormalStart(target, bestSlot)
        }
    }

    private fun handleNormalStart(target: MineTarget, bestSlot: Int) {
        if (bestSlot != -1) {
            InvUtils.swap(bestSlot, false)
        }

        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                target.targetPos,
                target.direction
            )
        )

        if (swingHand.get()) mc.player?.swingHand(Hand.MAIN_HAND)
        target.started = true
    }

    private fun handleImmediateStart(target: MineTarget, bestSlot: Int) {
        if (bestSlot != -1) {
            InvUtils.swap(bestSlot, false)
        }

        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                target.targetPos,
                target.direction
            )
        )

        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                target.targetPos,
                target.direction
            )
        )

        if (swingHand.get()) mc.player?.swingHand(Hand.MAIN_HAND)
        target.started = true
        target.finished = true
    }

    private fun handleCivStart(target: MineTarget, bestSlot: Int) {
        // Civ mode: dig nearby block first to bypass some anti-cheats
        val nearbyPos = findNearbyBlock(target.targetPos)
        
        if (nearbyPos != null && bestSlot != -1) {
            InvUtils.swap(bestSlot, false)
            
            mc.networkHandler?.sendPacket(
                PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                    nearbyPos,
                    Direction.UP
                )
            )
            mc.networkHandler?.sendPacket(
                PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                    nearbyPos,
                    Direction.UP
                )
            )
        }

        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                target.targetPos,
                target.direction
            )
        )

        if (swingHand.get()) mc.player?.swingHand(Hand.MAIN_HAND)
        target.started = true
    }

    private fun handleGhostHandStart(target: MineTarget, bestSlot: Int) {
        val player = mc.player ?: return
        
        if (bestSlot == -1) {
            handleNormalStart(target, -1)
            return
        }

        ghostHandSlot = bestSlot
        ghostHandPhase = 1
        ghostHandDelay = calculateDelay()
        target.started = true
    }

    private fun handleGhostHandPhase(target: MineTarget) {
        val player = mc.player ?: return
        val currentSlot = player.inventory.selectedSlot

        when (ghostHandPhase) {
            1 -> {
                // Phase 1: Send abort if enabled
                if (abortOnSwitch.get()) {
                    mc.networkHandler?.sendPacket(
                        PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                            target.targetPos,
                            target.direction
                        )
                    )
                }
                
                // Switch to tool slot
                mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(ghostHandSlot))
                ghostHandPhase = 2
                ghostHandDelay = calculateDelay()
            }
            2 -> {
                // Phase 2: Start breaking
                mc.networkHandler?.sendPacket(
                    PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                        target.targetPos,
                        target.direction
                    )
                )
                
                if (swingHand.get()) player.swingHand(Hand.MAIN_HAND)
                ghostHandPhase = 3
                ghostHandDelay = calculateDelay()
            }
            3 -> {
                // Phase 3: Switch back to original slot
                mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(currentSlot))
                
                // Continue breaking in background
                target.updateProgress()
                
                if (target.finished) {
                    // Send stop packet
                    mc.networkHandler?.sendPacket(
                        PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                            target.targetPos,
                            target.direction
                        )
                    )
                    currentTarget = null
                    ghostHandPhase = 0
                }
            }
        }
    }

    private fun calculateDelay(): Int {
        val baseDelay = slotSwitchDelay.get()
        return if (randomDelay.get()) {
            baseDelay + Random.nextInt(0, 3)
        } else {
            baseDelay
        }
    }

    private fun findNearbyBlock(center: BlockPos): BlockPos? {
        for (direction in Direction.entries) {
            val pos = center.offset(direction)
            val state = mc.world?.getBlockState(pos) ?: continue
            if (!state.isAir && state.fluidState.isEmpty) {
                return pos
            }
        }
        return null
    }

    private fun findBestToolSlot(state: BlockState): Int {
        val player = mc.player ?: return -1
        var bestSlot = -1
        var bestSpeed = 0.0f

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

    enum class MineModeEnum {
        Normal, Immediate, Civ, GhostHand
    }
}
