package com.zychen027.meteorplusplus.utils.world

import com.zychen027.meteorplusplus.utils.rotation.Rotation
import meteordevelopment.meteorclient.MeteorClient
import net.minecraft.block.*
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.ExperienceOrbEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ArrowEntity
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.*
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 方块放置/破坏工具类 - 来自 LeavesHack
 * 包含大量方块交互相关的实用方法
 */
object BlockUtil {
    private val mc get() = MeteorClient.mc

    /**
     * 需要潜行才能放置的方块列表
     */
    val shiftBlocks = listOf(
        Blocks.ENDER_CHEST, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.CRAFTING_TABLE,
        Blocks.BIRCH_TRAPDOOR, Blocks.BAMBOO_TRAPDOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.CHERRY_TRAPDOOR,
        Blocks.ANVIL, Blocks.BREWING_STAND, Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER,
        Blocks.ACACIA_TRAPDOOR, Blocks.ENCHANTING_TABLE, Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX,
        Blocks.MAGENTA_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX, Blocks.LIME_SHULKER_BOX,
        Blocks.PINK_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX,
        Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX, Blocks.RED_SHULKER_BOX, Blocks.BLACK_SHULKER_BOX
    )

    private const val MIN_EYE_HEIGHT = 0.4
    private const val MAX_EYE_HEIGHT = 1.62
    private const val MOVEMENT_THRESHOLD = 0.0002

    /**
     * 获取严格的点击方向
     */
    fun getClickSideStrict(pos: BlockPos): Direction? {
        var side: Direction? = null
        var minDistance = Double.MAX_VALUE

        for (direction in Direction.values()) {
            if (!isGrimDirection(pos, direction)) continue
            val disSq = mc.player!!.eyePos.squaredDistanceTo(pos.offset(direction).toCenterPos())
            if (disSq > minDistance) continue
            side = direction
            minDistance = disSq
        }
        return side
    }

    /**
     * 获取点到 Box 的最近点
     */
    fun getClosestPointToBox(pos: Vec3d, minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): Vec3d {
        val closestX = max(minX, min(pos.x, maxX))
        val closestY = max(minY, min(pos.y, maxY))
        val closestZ = max(minZ, min(pos.z, maxZ))
        return Vec3d(closestX, closestY, closestZ)
    }

    /**
     * 获取点到 Box 的最近点
     */
    fun getClosestPointToBox(eyePos: Vec3d, boundingBox: Box): Vec3d {
        return getClosestPointToBox(eyePos, boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ)
    }

    /**
     * 获取到实体的最近点
     */
    fun getClosestPoint(entity: Entity): Vec3d {
        return getClosestPointToBox(mc.player!!.eyePos, entity.boundingBox)
    }

    /**
     * 检查方块周围是否有实体 (用于水晶放置)
     */
    fun noEntityBlockCrystal(pos: BlockPos, ignoreCrystal: Boolean, ignoreItem: Boolean): Boolean {
        for (entity in getEntities(Box(pos))) {
            if (!entity.isAlive || 
                (ignoreItem && entity is ItemEntity) || 
                (ignoreCrystal && entity is EndCrystalEntity && mc.player!!.eyePos.distanceTo(getClosestPoint(entity)) <= 3.0)) {
                continue
            }
            return false
        }
        return true
    }

    /**
     * 检查方块是否可以点击
     */
    @Suppress("DEPRECATION")
    fun canClick(pos: BlockPos): Boolean {
        return mc.world!!.getBlockState(pos).isSolid &&
               (!(shiftBlocks.contains(getBlock(pos)) || getBlock(pos) is BedBlock) || mc.player!!.isSneaking)
    }

    /**
     * 检查方块是否可以点击 (忽略潜行)
     */
    @Suppress("DEPRECATION")
    fun canClick(pos: BlockPos, ignoreSneak: Boolean): Boolean {
        return mc.world!!.getBlockState(pos).isSolid &&
               (!(shiftBlocks.contains(getBlock(pos)) || getBlock(pos) is BedBlock) || (mc.player!!.isSneaking || ignoreSneak))
    }

    /**
     * 检查方块是否可以放置
     */
    fun canPlace(pos: BlockPos): Boolean = canPlace(pos, null)

    /**
     * 检查方块是否可以放置
     */
    fun canPlace(pos: BlockPos, directionPredicate: ((Direction) -> Boolean)?): Boolean {
        if (getPlaceSide(pos, directionPredicate) == null) return false
        if (!canReplace(pos)) return false
        return !hasEntity(pos, false)
    }

    /**
     * 检查方块是否可以被替换
     */
    fun canReplace(pos: BlockPos): Boolean {
        if (pos.y >= 320) return false
        return mc.world!!.getBlockState(pos).isReplaceable
    }

    /**
     * 检查方块是否有实体
     */
    fun hasEntity(pos: BlockPos, ignoreCrystal: Boolean): Boolean {
        for (entity in getEntities(Box(pos))) {
            if (!entity.isAlive || 
                entity is ItemEntity || 
                entity is ExperienceOrbEntity || 
                entity is ExperienceBottleEntity || 
                entity is ArrowEntity || 
                (ignoreCrystal && entity is EndCrystalEntity)) {
                continue
            }
            return true
        }
        return false
    }

    /**
     * 获取 Box 内的所有实体
     */
    fun getEntities(box: Box): List<Entity> {
        val list = mutableListOf<Entity>()
        for (entity in mc.world?.entities ?: emptyList()) {
            if (entity == null) continue
            if (entity.boundingBox.intersects(box)) {
                list.add(entity)
            }
        }
        return list
    }

    /**
     * 获取球形范围内的所有方块位置
     */
    fun getSphere(range: Float): ArrayList<BlockPos> {
        return getSphere(range, mc.player!!.eyePos)
    }

    /**
     * 获取球形范围内的所有方块位置
     */
    fun getSphere(range: Float, pos: Vec3d): ArrayList<BlockPos> {
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

    /**
     * 检查方块是否有玩家实体
     */
    fun hasPlayerEntity(pos: BlockPos): Boolean {
        for (entity in getEntities(Box(pos))) {
            if (entity is PlayerEntity) return true
        }
        return false
    }

    /**
     * 检查方块是否有水晶
     */
    fun hasCrystal(pos: BlockPos): Boolean {
        for (entity in getEndCrystals(Box(pos))) {
            if (!entity.isAlive) continue
            return true
        }
        return false
    }

    /**
     * 获取 Box 内的所有水晶
     */
    fun getEndCrystals(box: Box): List<EndCrystalEntity> {
        val list = mutableListOf<EndCrystalEntity>()
        for (entity in mc.world?.entities ?: emptyList()) {
            if (entity is EndCrystalEntity) {
                if (entity.boundingBox.intersects(box)) {
                    list.add(entity)
                }
            }
        }
        return list
    }

    /**
     * 获取方块的可放置方向
     */
    fun getPlaceSide(pos: BlockPos, directionPredicate: ((Direction) -> Boolean)?): Direction? {
        var dis = 114514.0
        var side: Direction? = null

        for (direction in Direction.values()) {
            if (directionPredicate != null && !directionPredicate(direction)) continue
            if (canClick(pos.offset(direction)) && !mc.world!!.getBlockState(pos.offset(direction)).isReplaceable) {
                if (!isGrimDirection(pos.offset(direction), direction.opposite)) continue
                val vecDis = mc.player!!.eyePos.squaredDistanceTo(pos.toCenterPos().add(
                    direction.vector.x * 0.5,
                    direction.vector.y * 0.5,
                    direction.vector.z * 0.5
                ))
                if (side == null || vecDis < dis) {
                    side = direction
                    dis = vecDis
                }
            }
        }
        return side
    }

    /**
     * 获取方块的可放置方向 (忽略潜行)
     */
    fun getPlaceSide(pos: BlockPos, directionPredicate: ((Direction) -> Boolean)?, ignoreSneak: Boolean): Direction? {
        var dis = 114514.0
        var side: Direction? = null

        for (direction in Direction.values()) {
            if (directionPredicate != null && !directionPredicate(direction)) continue
            if (canClick(pos.offset(direction), ignoreSneak) && !mc.world!!.getBlockState(pos.offset(direction)).isReplaceable) {
                if (!isGrimDirection(pos.offset(direction), direction.opposite)) continue
                val vecDis = mc.player!!.eyePos.squaredDistanceTo(pos.toCenterPos().add(
                    direction.vector.x * 0.5,
                    direction.vector.y * 0.5,
                    direction.vector.z * 0.5
                ))
                if (side == null || vecDis < dis) {
                    side = direction
                    dis = vecDis
                }
            }
        }
        return side
    }

    /**
     * 获取方块的所有可放置方向
     */
    fun getPlaceSides(pos: BlockPos, directionPredicate: ((Direction) -> Boolean)?): ArrayList<Direction> {
        val sides = ArrayList<Direction>()

        for (direction in Direction.values()) {
            if (directionPredicate != null && !directionPredicate(direction)) continue
            val neighbor = pos.offset(direction)
            val neighborState = mc.world!!.getBlockState(neighbor)
            if (canClick(neighbor) && !neighborState.isReplaceable) {
                if (!isGrimDirection(neighbor, direction.opposite)) continue
                sides.add(direction)
            }
        }
        return sides
    }

    /**
     * 获取方块的所有可放置方向 (忽略潜行)
     */
    fun getPlaceSides(pos: BlockPos, directionPredicate: ((Direction) -> Boolean)?, ignoreSneak: Boolean): ArrayList<Direction> {
        val sides = ArrayList<Direction>()

        for (direction in Direction.values()) {
            if (directionPredicate != null && !directionPredicate(direction)) continue
            val neighbor = pos.offset(direction)
            val neighborState = mc.world!!.getBlockState(neighbor)
            if (canClick(neighbor, ignoreSneak) && !neighborState.isReplaceable) {
                if (!isGrimDirection(neighbor, direction.opposite)) continue
                sides.add(direction)
            }
        }
        return sides
    }

    /**
     * 检查是否可以看到方块的某个方向
     */
    fun canSee(pos: BlockPos, side: Direction): Boolean {
        val testVec = pos.toCenterPos().add(side.vector.x * 0.5, side.vector.y * 0.5, side.vector.z * 0.5)
        val result = mc.world!!.raycast(
            RaycastContext(
                getEyesPos(),
                testVec,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player!!
            )
        )
        return result == null || result.type == HitResult.Type.MISS
    }

    /**
     * 获取玩家眼睛位置
     */
    fun getEyesPos(): Vec3d = mc.player!!.eyePos

    /**
     * 获取点击方向
     */
    fun getClickSide(pos: BlockPos): Direction? {
        var side: Direction? = null
        var range = 100.0

        for (direction in Direction.values()) {
            if (!canSee(pos, direction)) continue
            if (sqrt(mc.player!!.eyePos.squaredDistanceTo(pos.offset(direction).toCenterPos())) > range) continue
            side = direction
            range = sqrt(mc.player!!.eyePos.squaredDistanceTo(pos.offset(direction).toCenterPos()))
        }

        if (side != null) return side
        side = Direction.UP

        for (direction in Direction.values()) {
            if (!isGrimDirection(pos, direction)) continue
            if (sqrt(mc.player!!.eyePos.squaredDistanceTo(pos.offset(direction).toCenterPos())) > range) continue
            side = direction
            range = sqrt(mc.player!!.eyePos.squaredDistanceTo(pos.offset(direction).toCenterPos()))
        }
        return side
    }

    /**
     * 检查方向是否符合 Grim AntiCheat 的放置检测
     */
    fun isGrimDirection(pos: BlockPos, direction: Direction): Boolean {
        val combined = getCombinedBox(pos, mc.world!!)
        val player = mc.player as? ClientPlayerEntity ?: return true
        
        val eyePositions = Box(
            player.x,
            player.y + MIN_EYE_HEIGHT,
            player.z,
            player.x,
            player.y + MAX_EYE_HEIGHT,
            player.z
        ).expand(MOVEMENT_THRESHOLD)

        if (isIntersected(eyePositions, combined)) return true

        return when (direction) {
            Direction.NORTH -> eyePositions.minZ <= combined.minZ
            Direction.SOUTH -> eyePositions.maxZ >= combined.maxZ
            Direction.EAST -> eyePositions.maxX >= combined.maxX
            Direction.WEST -> eyePositions.minX <= combined.minX
            Direction.UP -> eyePositions.maxY <= combined.maxY
            Direction.DOWN -> eyePositions.minY >= combined.minY
        }
    }

    private fun getCombinedBox(pos: BlockPos, level: World): Box {
        val shape = level.getBlockState(pos).getCollisionShape(level, pos).offset(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        var combined = Box(pos)
        for (box in shape.boundingBoxes) {
            val minX = max(box.minX, combined.minX)
            val minY = max(box.minY, combined.minY)
            val minZ = max(box.minZ, combined.minZ)
            val maxX = min(box.maxX, combined.maxX)
            val maxY = min(box.maxY, combined.maxY)
            val maxZ = min(box.maxZ, combined.maxZ)
            combined = Box(minX, minY, minZ, maxX, maxY, maxZ)
        }
        return combined
    }

    private fun isIntersected(bb: Box, other: Box): Boolean {
        return other.maxX - VoxelShapes.MIN_SIZE > bb.minX &&
               other.minX + VoxelShapes.MIN_SIZE < bb.maxX &&
               other.maxY - VoxelShapes.MIN_SIZE > bb.minY &&
               other.minY + VoxelShapes.MIN_SIZE < bb.maxY &&
               other.maxZ - VoxelShapes.MIN_SIZE > bb.minZ &&
               other.minZ + VoxelShapes.MIN_SIZE < bb.maxZ
    }

    /**
     * 获取方块
     */
    fun getBlock(pos: BlockPos): Block = mc.world!!.getBlockState(pos).block

    /**
     * 放置方块
     */
    fun placeBlock(pos: BlockPos, side: Direction, rotate: Boolean) {
        clickBlock(pos.offset(side), side.opposite, rotate)
    }

    /**
     * 放置半砖
     */
    fun placeSlabBlock(pos: BlockPos, side: Direction, slabSide: Direction, rotate: Boolean) {
        clickSlabBlock(pos.offset(side), side.opposite, slabSide, rotate)
    }

    /**
     * 点击方块
     */
    fun clickBlock(pos: BlockPos, side: Direction, rotate: Boolean) {
        val directionVec = Vec3d(
            pos.x + 0.5 + side.vector.x * 0.5,
            pos.y + 0.5 + side.vector.y * 0.5,
            pos.z + 0.5 + side.vector.z * 0.5
        )
        if (rotate) Rotation.snapAt(directionVec, false)
        mc.player!!.swingHand(Hand.MAIN_HAND)
        val result = BlockHitResult(directionVec, side, pos, false)
        mc.interactionManager?.interactBlock(mc.player!!, Hand.MAIN_HAND, result)
        if (rotate) Rotation.snapBack(false)
    }

    /**
     * 点击半砖方块
     */
    fun clickSlabBlock(pos: BlockPos, side: Direction, slabSide: Direction, rotate: Boolean) {
        var yOffset = 0.5
        if (slabSide == Direction.UP) yOffset += 0.1
        if (slabSide == Direction.DOWN) yOffset -= 0.1

        val directionVec = Vec3d(
            pos.x + 0.5 + side.vector.x * 0.5,
            pos.y + yOffset + side.vector.y * 0.5,
            pos.z + 0.5 + side.vector.z * 0.5
        )
        if (rotate) Rotation.snapAt(directionVec, false)
        mc.player!!.swingHand(Hand.MAIN_HAND)
        val result = BlockHitResult(directionVec, side, pos, false)
        mc.interactionManager?.interactBlock(mc.player!!, Hand.MAIN_HAND, result)
        if (rotate) Rotation.snapBack(false)
    }

    /**
     * 检查方块是否需要潜行
     */
    fun needSneak(block: Block): Boolean = shiftBlocks.contains(block)
}
