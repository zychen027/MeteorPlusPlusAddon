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

class PacketMineModule : Module(
    MeteorPlusPlusAddon.PACKETMINE_CATEGORY,
    "PacketMine",
    "PacketMine module ported from LiquidBounce",
    *emptyArray<String>()
) {

    private val sgGeneral = settings.getDefaultGroup()

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

    private var currentTarget: MineTarget? = null

    override fun onActivate() {
        currentTarget = null
    }

    override fun onDeactivate() {
        currentTarget?.abort()
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        if (mc.options.attackKey.wasPressed()) {
            handleInput()
        }

        val target = currentTarget ?: return

        if (target.isInvalidOrOutOfRange(range.get())) {
            currentTarget = null
            return
        }

        target.updateBlockState()
        if (target.blockState.isAir) {
            currentTarget = null
            return
        }

        if (!target.started) {
            startBreaking(target)
        }

        target.updateProgress()
        
        if (target.finished) {
            // GhostHand模式下不需要切换回来，因为没有实际切换客户端槽位
            if (mineMode.get() != MineModeEnum.GhostHand) {
                InvUtils.swapBack()
            }
            currentTarget = null
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
            } else {
                currentTarget = MineTarget(pos)
            }
        }
    }

    private fun startBreaking(target: MineTarget) {
        target.direction = Direction.UP
        val bestSlot = findBestToolSlot(target.blockState)

        if (mineMode.get() == MineModeEnum.GhostHand && bestSlot != -1) {
            handleGhostHandStart(target, bestSlot)
        } else {
            handleNormalStart(target, bestSlot)
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

        mc.player?.swingHand(Hand.MAIN_HAND)
        target.started = true
    }

    private fun handleGhostHandStart(target: MineTarget, bestSlot: Int) {
        val player = mc.player ?: return
        val currentSlot = player.inventory.selectedSlot

        // 1. 发送数据包告诉服务器我们切换到了最佳工具槽位
        mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(bestSlot))

        // 2. 发送开始挖掘数据包 (服务器此刻认为我们拿着最佳工具)
        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                target.targetPos,
                target.direction
            )
        )

        // 3. 发送数据包告诉服务器我们切换回了原来的槽位 (客户端视觉从未改变)
        mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(currentSlot))

        // 挥手动画 (客户端本地)
        player.swingHand(Hand.MAIN_HAND)
        target.started = true
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
