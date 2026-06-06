package com.zychen027.meteorplusplus.utils.world

import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object BlockUtil {
    private val mc = MinecraftClient.getInstance()

    fun getClickSide(pos: BlockPos): Direction {
        val playerPos = mc.player?.eyePos ?: return Direction.UP
        val centerX = pos.x + 0.5
        val centerY = pos.y + 0.5
        val centerZ = pos.z + 0.5

        val dX = playerPos.x - centerX
        val dY = playerPos.y - centerY
        val dZ = playerPos.z - centerZ

        if (Math.abs(dX) > Math.abs(dY) && Math.abs(dX) > Math.abs(dZ)) {
            return if (dX > 0) Direction.EAST else Direction.WEST
        }
        if (Math.abs(dY) > Math.abs(dZ)) {
            return if (dY > 0) Direction.UP else Direction.DOWN
        }
        return if (dZ > 0) Direction.SOUTH else Direction.NORTH
    }
}

// LeavesHack 中用于发送非法坐标绕过检测的类
class BlockPosX(x: Double, y: Double, z: Double) : BlockPos(x.toInt(), y.toInt(), z.toInt())
