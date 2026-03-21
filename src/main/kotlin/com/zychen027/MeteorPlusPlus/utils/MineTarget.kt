package com.zychen027.meteorplusplus.utils

import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class MineTarget(val targetPos: BlockPos) {
    val mc = MinecraftClient.getInstance()

    var finished = false
    var progress = 0f
    var started = false
    var direction: Direction? = null

    // Bug 1 修复：使用 Elvis 运算符提供默认值，避免 NPE
    var blockState: BlockState = mc.world?.getBlockState(targetPos)
        ?: Blocks.AIR.defaultState

    fun updateBlockState() {
        mc.world?.getBlockState(targetPos)?.let { blockState = it }
    }

    fun isInvalidOrOutOfRange(maxRange: Double): Boolean {
        val state = mc.world?.getBlockState(targetPos) ?: return true
        if (state.isAir) return true

        val player = mc.player ?: return true
        val distSq = player.squaredDistanceTo(
            targetPos.x.toDouble(),
            targetPos.y.toDouble(),
            targetPos.z.toDouble()
        )
        return distSq > maxRange * maxRange
    }

    fun updateProgress() {
        if (finished) return

        val player = mc.player ?: return
        val world = mc.world ?: return

        if (!started) return

        val delta = blockState.calcBlockBreakingDelta(player, world, targetPos)

        progress += delta

        val stage = (progress * 10.0f).toInt()

        mc.worldRenderer?.setBlockBreakingInfo(player.id, targetPos, stage)

        if (progress >= 1.0f) {
            finishBreaking()
        }
    }

    private fun finishBreaking() {
        mc.networkHandler?.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                targetPos,
                direction ?: Direction.UP
            )
        )

        mc.player?.swingHand(Hand.MAIN_HAND)
        finished = true

        mc.worldRenderer?.setBlockBreakingInfo(mc.player?.id ?: -1, targetPos, -1)
    }

    fun abort() {
        if (!started || finished) return

        val networkHandler = mc.networkHandler ?: return
        val dir = direction ?: Direction.DOWN

        networkHandler.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                targetPos,
                dir
            )
        )

        mc.worldRenderer?.setBlockBreakingInfo(mc.player?.id ?: -1, targetPos, -1)
    }
}
