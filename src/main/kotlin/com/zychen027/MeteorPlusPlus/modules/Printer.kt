package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.utils.entity.InventoryUtil
import com.zychen027.meteorplusplus.utils.math.Timer
import com.zychen027.meteorplusplus.utils.rotation.Rotation
import com.zychen027.meteorplusplus.utils.world.BlockUtil
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent
import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.render.color.SettingColor
import meteordevelopment.orbit.EventHandler
import meteordevelopment.orbit.EventPriority
import net.minecraft.block.*
import net.minecraft.block.enums.SlabType
import net.minecraft.item.ItemPlacementContext
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

/**
 * Printer (投影打印机) - 移植自 LeavesHack
 * 根据 Litematica 投影自动放置方块
 * 
 * MC 1.21.8 API 适配：
 * - BlockHitResult 构造函数移除 overlay 参数
 * - BlockSetType.Axis → BlockState.AXIS
 * - ClientCommandC2SPacket.Mode 枚举值变化
 */
class Printer : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "Printer",
    "根据 Litematica 投影自动放置方块（需要安装 Litematica）"
) {
    private val sgGeneral = settings.getDefaultGroup()
    private val sgShift = settings.createGroup("IgnoreSneak")
    private val sgRender = settings.createGroup("Render")
    private val sgWhitelist = settings.createGroup("Whitelist")

    private val rotate = sgGeneral.add(BoolSetting.Builder()
        .name("Rotate")
        .description("Rotate towards blocks when placing.")
        .defaultValue(true)
        .build())

    private val printingRange = sgGeneral.add(IntSetting.Builder()
        .name("PrintingRange")
        .description("How far to place blocks around the player.")
        .defaultValue(4)
        .min(1)
        .sliderMax(6)
        .build())

    private val inventorySwap = sgGeneral.add(BoolSetting.Builder()
        .name("InventorySwap")
        .defaultValue(true)
        .build())

    private val safeWalk = sgGeneral.add(BoolSetting.Builder()
        .name("SafeWalk")
        .defaultValue(true)
        .build())

    private val ignoreSneak = sgShift.add(BoolSetting.Builder()
        .name("IgnoreSneak")
        .defaultValue(true)
        .build())

    private val shiftTime = sgShift.add(IntSetting.Builder()
        .name("ShiftTime")
        .defaultValue(100)
        .min(0)
        .sliderMax(1000)
        .build())

    private val sneakSpeed = sgShift.add(IntSetting.Builder()
        .name("SneakSpeed")
        .description("潜行速度")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build())

    private val listMode = sgWhitelist.add(EnumSetting.Builder<ListMode>()
        .name("ListMode")
        .description("Selection mode.")
        .defaultValue(ListMode.Blacklist)
        .build())

    private val blacklist = sgWhitelist.add(BlockListSetting.Builder()
        .name("BlackList")
        .description("黑名单")
        .visible { listMode.get() == ListMode.Blacklist }
        .build())

    private val whitelist = sgWhitelist.add(BlockListSetting.Builder()
        .name("WhiteList")
        .description("白名单")
        .visible { listMode.get() == ListMode.Whitelist }
        .build())

    private val shapeMode = sgRender.add(EnumSetting.Builder<ShapeMode>()
        .name("ShapeMode")
        .defaultValue(ShapeMode.Both)
        .build())

    private val lineColor = sgRender.add(ColorSetting.Builder()
        .name("LineColor")
        .defaultValue(SettingColor(255, 255, 255, 255))
        .build())

    private val sideColor = sgRender.add(ColorSetting.Builder()
        .name("SideColor")
        .defaultValue(SettingColor(255, 255, 255, 50))
        .build())

    private val debug = sgGeneral.add(BoolSetting.Builder()
        .name("Debug")
        .description("Dev 用来测试的")
        .defaultValue(false)
        .build())

    private var hasSneak = false
    private val shiftTimer = Timer()

    override fun onActivate() {
        hasSneak = false
        shiftTimer.setMs(99999)
    }

    override fun onDeactivate() {
        if (hasSneak) {
            // MC 1.21.8 移除了 RELEASE_SHIFT_KEY，直接设置 isSneaking
            mc.player!!.isSneaking = false
            hasSneak = false
        }
    }

    @EventHandler
    private fun onRender3D(event: Render3DEvent) {
        if (mc.player == null || mc.world == null) return

        // 获取 Litematica 投影世界 - 使用反射避免硬依赖
        val schematic = try {
            val clazz = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler")
            val method = clazz.getMethod("getSchematicWorld")
            method.invoke(null)
        } catch (e: Exception) {
            if (debug.get()) mc.player!!.sendMessage(Text.literal("§c[Printer] 未找到 Litematica，请安装后使用"), false)
            return
        } ?: return

        if (!shiftTimer.passedMs(shiftTime.get().toLong()) && hasSneak && ignoreSneak.get()) {
            return
        }

        val sphere = BlockUtil.getSphere(printingRange.get().toFloat())
        var placed = 0

        for (pos in sphere) {
            val required = try {
                val method = schematic.javaClass.getMethod("getBlockState", net.minecraft.util.math.BlockPos::class.java)
                method.invoke(schematic, pos) as net.minecraft.block.BlockState
            } catch (e: Exception) {
                continue
            }

            // 黑名单/白名单检查
            if (listMode.get() == ListMode.Blacklist && blacklist.get().contains(required.block)) continue
            if (listMode.get() == ListMode.Whitelist && !whitelist.get().contains(required.block)) continue

            if (!required.isAir &&
                !required.fluidState.isEmpty &&
                (mc.world!!.isAir(pos) || BlockUtil.canReplace(pos)) &&
                !BlockUtil.hasEntity(pos, false)
            ) {
                if (placed >= 1) {
                    if (debug.get()) mc.player!!.sendMessage(Text.literal("已超过最大数量，当前 placed:$placed"), false)
                    return
                }

                val slot = if (inventorySwap.get()) {
                    InventoryUtil.findBlockInventory(required.block)
                } else {
                    InventoryUtil.findBlock(required.block)
                }
                if (slot == -1) continue

                val old = mc.player!!.inventory.selectedSlot
                val sides = BlockUtil.getPlaceSides(pos, null, ignoreSneak.get())
                if (sides.isEmpty()) continue

                event.renderer.box(Box(pos), sideColor.get(), lineColor.get(), shapeMode.get(), 0)

                var target = sides.first()
                val facing = getBlockFacing(required)

                // 处理有方向的方块
                if (facing != null && !isRedstoneComponent(required)) {
                    if (debug.get()) mc.player!!.sendMessage(Text.literal("方块包含方向"), false)
                    var found = false
                    for (i in sides) {
                        if (debug.get()) mc.player!!.sendMessage(Text.literal("side 列表：$i"), false)
                        if (checkState(pos.offset(i), required, i.opposite)) {
                            found = true
                            target = i
                        }
                    }
                    if (!found) {
                        if (debug.get()) mc.player!!.sendMessage(Text.literal("未找到目标方向"), false)
                        continue
                    }
                }

                // 跳过红石粉下方为空气的情况
                if (required.block is RedstoneWireBlock &&
                    (mc.world!!.isAir(pos.down()) || mc.world!!.getBlockState(pos.down()).isReplaceable)
                ) continue

                // 检查是否需要潜行
                if (BlockUtil.needSneak(BlockUtil.getBlock(pos.offset(target))) && !hasSneak) {
                    mc.player!!.isSneaking = true
                    hasSneak = true
                    shiftTimer.reset()
                    return
                }

                placed++
                doSwap(slot)

                // 旋转
                if (rotate.get()) {
                    val directionVec = Vec3d(
                        pos.x + 0.5 + target.vector.x * 0.5,
                        pos.y + 0.5 + target.vector.y * 0.5,
                        pos.z + 0.5 + target.vector.z * 0.5
                    )
                    Rotation.snapAt(directionVec, false)
                }

                // 特殊方块朝向
                if (facing != null && isRedstoneComponent(required)) {
                    if (required.block is ObserverBlock) {
                        blockFacing(facing)
                    } else {
                        blockFacing(facing.opposite)
                    }
                }

                // 放置半砖
                val type = getSlabType(required)
                if (type != null) {
                    when (type) {
                        SlabType.TOP -> {
                            if (BlockUtil.getBlock(pos) !is SlabBlock) {
                                BlockUtil.placeSlabBlock(pos, target, Direction.UP, false)
                            }
                        }
                        SlabType.BOTTOM -> {
                            if (BlockUtil.getBlock(pos) !is SlabBlock) {
                                BlockUtil.placeSlabBlock(pos, target, Direction.DOWN, false)
                            }
                        }
                        else -> {}
                    }
                } else {
                    BlockUtil.placeBlock(pos, target, false)
                }

                // 恢复潜行状态
                if (hasSneak && ignoreSneak.get()) {
                    mc.player!!.isSneaking = false
                    hasSneak = false
                }

                Rotation.snapBack(false)
                event.renderer.box(Box(pos), sideColor.get(), lineColor.get(), shapeMode.get(), 0)

                // 恢复槽位
                if (inventorySwap.get()) {
                    doSwap(slot)
                } else {
                    doSwap(old)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    private fun onMove1(event: PlayerMoveEvent) {
        if (safeWalk.get()) {
            var x = event.movement.x
            var y = event.movement.y
            var z = event.movement.z

            if (mc.player!!.isOnGround()) {
                val increment = 0.05
                while (x != 0.0 && isOffsetBBEmpty(x, -1.0, 0.0)) {
                    if (x < increment && x >= -increment) {
                        x = 0.0
                        continue
                    }
                    if (x > 0.0) x -= increment else x += increment
                }
                while (z != 0.0 && isOffsetBBEmpty(0.0, -1.0, z)) {
                    if (z < increment && z >= -increment) {
                        z = 0.0
                        continue
                    }
                    if (z > 0.0) z -= increment else z += increment
                }
                while (x != 0.0 && z != 0.0 && isOffsetBBEmpty(x, -1.0, z)) {
                    x = if (x < increment && x >= -increment) 0.0 else if (x > 0.0) x - increment else x + increment
                    if (z < increment && z >= -increment) {
                        z = 0.0
                        continue
                    }
                    if (z > 0.0) z -= increment else z += increment
                }
            }

            event.movement = Vec3d(x, y, z)
        }
    }

    private fun isOffsetBBEmpty(offsetX: Double, offsetY: Double, offsetZ: Double): Boolean {
        return !mc.world!!.canCollide(mc.player!!, mc.player!!.boundingBox.offset(offsetX, offsetY, offsetZ))
    }

    @EventHandler
    private fun onMove2(event: PlayerMoveEvent) {
        if (shiftTimer.passedMs((shiftTime.get() * 2).toLong()) && ignoreSneak.get() && hasSneak) {
            mc.player!!.isSneaking = false
            hasSneak = false
            return
        }

        if (!hasSneak) return

        val speed = sneakSpeed.get().toDouble()
        val moveSpeed = 0.2873 / 100 * speed
        // MC 1.21.8: 使用按键状态计算移动方向
        val forward = if (mc.options.forwardKey.isPressed) 1.0 else if (mc.options.backKey.isPressed) -1.0 else 0.0
        val sideways = if (mc.options.leftKey.isPressed) 1.0 else if (mc.options.rightKey.isPressed) -1.0 else 0.0
        val yaw = Math.toRadians(mc.player!!.yaw.toDouble())

        if (forward == 0.0 && sideways == 0.0) {
            event.movement = Vec3d(0.0, event.movement.y, 0.0)
            return
        }

        val x = forward * moveSpeed * -Math.sin(yaw) + sideways * moveSpeed * Math.cos(yaw)
        val z = forward * moveSpeed * Math.cos(yaw) - sideways * moveSpeed * -Math.sin(yaw)
        event.movement = Vec3d(x, event.movement.y, z)
    }

    private fun getSlabType(state: BlockState): SlabType? {
        if (state.block is SlabBlock) {
            return state.get(SlabBlock.TYPE)
        }
        return null
    }

    private fun blockFacing(direction: Direction) {
        when (direction) {
            Direction.EAST -> Rotation.snapAt(-90.0f, 5.0f, false)
            Direction.WEST -> Rotation.snapAt(90.0f, 5.0f, false)
            Direction.NORTH -> Rotation.snapAt(180.0f, 5.0f, false)
            Direction.SOUTH -> Rotation.snapAt(0.0f, 5.0f, false)
            Direction.UP -> Rotation.snapAt(5.0f, -90.0f, false)
            Direction.DOWN -> Rotation.snapAt(5.0f, 90.0f, false)
        }
    }

    private fun isRedstoneComponent(state: BlockState): Boolean {
        val block = state.block
        return block is RedstoneWireBlock ||
               block is AbstractRedstoneGateBlock ||
               block is PressurePlateBlock ||
               block is ObserverBlock ||
               block is TargetBlock ||
               block is TripwireHookBlock ||
               block is DaylightDetectorBlock ||
               block is PistonBlock ||
               block is RedstoneLampBlock ||
               block is FurnaceBlock
    }

    private fun checkState(pos: BlockPos, targetState: BlockState, direction: Direction): Boolean {
        val directionVec = Vec3d(
            pos.x + 0.5 + direction.vector.x * 0.5,
            pos.y + 0.5 + direction.vector.y * 0.5,
            pos.z + 0.5 + direction.vector.z * 0.5
        )
        // MC 1.21.8: BlockHitResult 构造函数 (pos, side, blockPos, insideBlock)
        val hit = BlockHitResult(directionVec, direction, pos, false)
        val ctx = ItemPlacementContext(mc.player, Hand.MAIN_HAND, mc.player!!.mainHandStack, hit)
        val result = targetState.block.getPlacementState(ctx)

        if (result != null && isSameFacing(result, targetState)) {
            return true
        } else if (result == null) {
            if (debug.get()) mc.player!!.sendMessage(Text.literal("result: null"), false)
        }
        return false
    }

    private fun getBlockFacing(state: BlockState): Direction? {
        if (state.block is HopperBlock) {
            return state.get(HopperBlock.FACING)
        }
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            return state.get(Properties.HORIZONTAL_FACING)
        }
        if (state.contains(Properties.FACING)) {
            return state.get(Properties.FACING)
        }
        if (state.contains(Properties.AXIS)) {
            val axis = state.get(Properties.AXIS)
            return when (axis.name) {
                "X" -> Direction.EAST
                "Y" -> Direction.UP
                "Z" -> Direction.SOUTH
                else -> null
            }
        }
        return null
    }

    private fun isSameFacing(a: BlockState, b: BlockState): Boolean {
        if (a.block != b.block) return false
        val fa = getBlockFacing(a)
        val fb = getBlockFacing(b)
        if (debug.get()) mc.player!!.sendMessage(Text.literal("fa: $fa fb: $fb"), false)
        if (fa == null || fb == null) return true
        return fa == fb
    }

    private fun doSwap(slot: Int) {
        if (!inventorySwap.get()) {
            InventoryUtil.switchToSlot(slot)
        } else {
            InventoryUtil.inventorySwap(slot, mc.player!!.inventory.selectedSlot)
        }
    }

    enum class ListMode {
        Whitelist,
        Blacklist
    }
}
