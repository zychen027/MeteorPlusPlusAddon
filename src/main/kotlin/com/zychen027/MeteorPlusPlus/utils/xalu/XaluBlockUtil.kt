package com.zychen027.meteorplusplus.utils.xalu

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

/**
 * 方块工具类 - 移植自 XALU
 */
object XaluBlockUtil {
    private val mc = MinecraftClient.getInstance()

    val shiftBlocks = listOf(
        Blocks.LAVA,
        Blocks.OBSIDIAN,
        Blocks.CRYING_OBSIDIAN,
        Blocks.RESPAWN_ANCHOR,
        Blocks.NETHER_PORTAL
    )

    fun getBlock(pos: BlockPos): Block {
        return mc.world!!.getBlockState(pos).block
    }

    fun hasCrystal(pos: BlockPos): Boolean {
        val block = mc.world!!.getBlockState(pos.down()).block
        return block::class.simpleName?.contains("Crystal", ignoreCase = true) == true
    }

    fun getClickSide(pos: BlockPos): Direction? {
        val player = mc.player ?: return null
        val eyesPos = player.eyePos

        val minX = pos.x
        val maxX = pos.x + 1
        val minY = pos.y
        val maxY = pos.y + 1
        val minZ = pos.z
        val maxZ = pos.z + 1

        // 检查各个面
        val sides = listOf(
            Direction.DOWN to (minY - eyesPos.y),
            Direction.UP to (maxY - eyesPos.y),
            Direction.NORTH to (minZ - eyesPos.z),
            Direction.SOUTH to (maxZ - eyesPos.z),
            Direction.WEST to (minX - eyesPos.x),
            Direction.EAST to (maxX - eyesPos.x)
        )

        // 返回最近的面
        return sides.maxByOrNull { it.second }?.first
    }

    fun canBreak(pos: BlockPos): Boolean {
        val state = mc.world!!.getBlockState(pos)
        return state.getHardness(mc.world!!, pos) != -1f && !state.isAir
    }

    fun getDistance(pos: BlockPos): Double {
        return mc.player!!.eyePos.distanceTo(pos.toCenterPos())
    }

    fun getSphere(range: Int): List<BlockPos> {
        return getSphere(range.toDouble(), mc.player!!.eyePos)
    }

    fun getSphere(range: Double, pos: net.minecraft.util.math.Vec3d): List<BlockPos> {
        val list = ArrayList<BlockPos>()
        val minX = (pos.x - range).toInt()
        val maxX = (pos.x + range).toInt()
        val minZ = (pos.z - range).toInt()
        val maxZ = (pos.z + range).toInt()
        val minY = (pos.y - range).toInt()
        val maxY = (pos.y + range).toInt()

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in minY..maxY) {
                    val curPos = BlockPos(x, y, z)
                    if (curPos.toCenterPos().distanceTo(pos) > range) continue
                    if (!list.contains(curPos)) {
                        list.add(curPos)
                    }
                }
            }
        }
        return list
    }
}
