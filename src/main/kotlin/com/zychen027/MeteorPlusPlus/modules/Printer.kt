package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.utils.entity.InventoryUtil
import com.zychen027.meteorplusplus.utils.math.Timer
import com.zychen027.meteorplusplus.utils.rotation.Rotation
import com.zychen027.meteorplusplus.utils.world.BlockUtil
import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.render.color.SettingColor
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.*
import net.minecraft.block.enums.SlabType
import net.minecraft.item.ItemPlacementContext
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.screen.slot.SlotActionType
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
 */
class Printer : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "投影打印机",
    "根据 Litematica 投影自动放置方块。"
) {
    private val sgGeneral = settings.getDefaultGroup()
    private val sgShift = settings.createGroup("忽略潜行")
    private val sgRender = settings.createGroup("渲染")

    // ==================== 通用设置 ====================
    private val rotate = sgGeneral.add(BoolSetting.Builder()
        .name("旋转")
        .description("放置时旋转向方块。")
        .defaultValue(true)
        .build())

    private val printingRange = sgGeneral.add(IntSetting.Builder()
        .name("打印范围")
        .description("玩家周围放置方块的距离。")
        .defaultValue(5)
        .min(1)
        .sliderMax(6)
        .build())

    private val inventorySwap = sgGeneral.add(BoolSetting.Builder()
        .name("背包交换")
        .description("从背包中拿取方块。")
        .defaultValue(true)
        .build())

    // ==================== 忽略潜行设置 ====================
    private val ignoreSneak = sgShift.add(BoolSetting.Builder()
        .name("忽略潜行")
        .description("放置时忽略潜行检查。")
        .defaultValue(true)
        .build())

    private val shiftTime = sgShift.add(IntSetting.Builder()
        .name("潜行时间")
        .description("潜行延迟时间（毫秒）。")
        .defaultValue(100)
        .min(0)
        .sliderMax(1000)
        .build())

    // ==================== 渲染设置 ====================
    private val shapeMode = sgRender.add(EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .defaultValue(ShapeMode.Both)
        .build())

    private val lineColor = sgRender.add(ColorSetting.Builder()
        .name("线条颜色")
        .defaultValue(SettingColor(255, 255, 255, 255))
        .build())

    private val sideColor = sgRender.add(ColorSetting.Builder()
        .name("填充颜色")
        .defaultValue(SettingColor(255, 255, 255, 50))
        .build())

    private var hasSneak = false
    private val shiftTimer = Timer()

    override fun onActivate() {
        hasSneak = false
        shiftTimer.setMs(99999)
    }

    override fun onDeactivate() {
        if (hasSneak) {
            mc.options.sneakKey.isPressed = false
            hasSneak = false
        }
    }

    @EventHandler
    private fun onRender3D(event: Render3DEvent) {
        val player = mc.player ?: return
        val world = mc.world ?: return

        // 获取 Litematica 投影世界 - 使用反射
        val schematic = try {
            val clazz = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler")
            val method = clazz.getMethod("getSchematicWorld")
            method.invoke(null)
        } catch (e: Exception) {
            return
        } ?: return

        // 检查潜行计时器
        val shiftTimeValue = shiftTime.get()
        if (!shiftTimer.passedMs(shiftTimeValue.toDouble()) && hasSneak) return

        // 获取球形范围内的方块
        val sphere = BlockUtil.getSphere(printingRange.get().toFloat())
        var placed = 0

        // 获取 getBlockState 方法（适配不同版本的 Litematica）
        val getBlockStateMethod = try {
            // 尝试新版本 API (BlockPos 参数)
            schematic.javaClass.getMethod("getBlockState", net.minecraft.util.math.BlockPos::class.java)
        } catch (e: Exception) {
            try {
                // 尝试旧版本 API (x, y, z 参数)
                schematic.javaClass.getMethod("getBlockState", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            } catch (e2: Exception) {
                return // 无法找到合适的方法，退出
            }
        }

        for (pos in sphere) {
            // 调用 getBlockState 方法
            val required = try {
                if (getBlockStateMethod.parameterCount == 1) {
                    // BlockPos 版本
                    getBlockStateMethod.invoke(schematic, pos) as net.minecraft.block.BlockState
                } else {
                    // x, y, z 版本
                    getBlockStateMethod.invoke(schematic, pos.x, pos.y, pos.z) as net.minecraft.block.BlockState
                }
            } catch (e: Exception) {
                continue // 跳过无法获取的方块
            }

            // 检查方块是否需要放置
            @Suppress("DEPRECATION")
            if (!required.isAir && !required.isLiquid &&
                (world.isAir(pos) || BlockUtil.canReplace(pos)) &&
                !BlockUtil.hasEntity(pos, false)
            ) {
                // 检查是否已达到单次渲染周期最大放置数
                if (placed >= 1) {
                    return
                }

                // 查找方块槽位
                val slot = if (inventorySwap.get()) {
                    InventoryUtil.findBlockInventory(required.block)
                } else {
                    InventoryUtil.findBlock(required.block)
                }
                if (slot == -1) continue

                val old = player.inventory.selectedSlot
                val sides = BlockUtil.getPlaceSides(pos, null, ignoreSneak.get())
                if (sides.isEmpty()) continue

                // 渲染方块框
                event.renderer.box(Box(pos), sideColor.get(), lineColor.get(), shapeMode.get(), 0)

                var target = sides.first()
                val facing = getBlockFacing(required)

                // 处理有方向的方块
                if (facing != null && !isRedstoneComponent(required)) {
                    var found = false
                    for (i in sides) {
                        if (checkState(pos.offset(i), required, i.opposite)) {
                            found = true
                            target = i
                        }
                    }
                    if (!found) {
                        continue
                    }
                }

                // 跳过红石粉下方为空气的情况
                if (required.block is RedstoneWireBlock &&
                    (world.isAir(pos.down()) || world.getBlockState(pos.down()).isReplaceable)
                ) continue

                // 检查是否需要潜行
                if (BlockUtil.needSneak(BlockUtil.getBlock(pos.offset(target))) && !hasSneak) {
                    mc.options.sneakKey.isPressed = true
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
                    Rotation.snapAt(directionVec)
                }

                // 特殊方块朝向
                if (facing != null && isRedstoneComponent(required)) {
                    blockFacing(facing.opposite)
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
                if (BlockUtil.needSneak(BlockUtil.getBlock(pos.offset(target)))) {
                    mc.options.sneakKey.isPressed = false
                    hasSneak = false
                }

                // 恢复旋转
                Rotation.snapBack()
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

    /**
     * 获取半砖类型
     */
    private fun getSlabType(state: BlockState): SlabType? {
        if (state.block is SlabBlock) {
            return state.get(SlabBlock.TYPE)
        }
        return null
    }

    /**
     * 设置方块朝向
     */
    private fun blockFacing(direction: Direction) {
        when (direction) {
            Direction.EAST -> Rotation.snapAt(-90.0f, 5.0f)
            Direction.WEST -> Rotation.snapAt(90.0f, 5.0f)
            Direction.NORTH -> Rotation.snapAt(180.0f, 5.0f)
            Direction.SOUTH -> Rotation.snapAt(0.0f, 5.0f)
            Direction.UP -> Rotation.snapAt(5.0f, -90.0f)
            Direction.DOWN -> Rotation.snapAt(5.0f, 90.0f)
        }
    }

    /**
     * 检查是否为红石组件
     */
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

    /**
     * 检查方块状态是否正确
     */
    private fun checkState(pos: BlockPos, targetState: BlockState, direction: Direction): Boolean {
        val player = mc.player ?: return false
        val directionVec = Vec3d(
            pos.x + 0.5 + direction.vector.x * 0.5,
            pos.y + 0.5 + direction.vector.y * 0.5,
            pos.z + 0.5 + direction.vector.z * 0.5
        )
        val hit = BlockHitResult(
            directionVec,
            direction,
            pos,
            false
        )
        val ctx = ItemPlacementContext(
            player,
            Hand.MAIN_HAND,
            player.mainHandStack,
            hit
        )
        val result = targetState.block.getPlacementState(ctx)
        return result != null && isSameFacing(result, targetState)
    }

    /**
     * 获取方块的朝向
     */
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
            return when (axis?.name) {
                "X" -> Direction.EAST
                "Y" -> Direction.UP
                "Z" -> Direction.SOUTH
                else -> null
            }
        }
        return null
    }

    /**
     * 检查两个方块朝向是否相同
     */
    private fun isSameFacing(a: BlockState, b: BlockState): Boolean {
        if (a.block != b.block) return false

        val fa = getBlockFacing(a)
        val fb = getBlockFacing(b)

        if (fa == null || fb == null) return true
        return fa == fb
    }

    /**
     * 切换槽位
     */
    private fun doSwap(slot: Int) {
        val player = mc.player ?: return
        if (slot < 0 || slot > 44) return
        
        // 确保槽位在有效范围内
        val networkSlot = if (slot < 9) slot + 36 else slot
        
        if (!inventorySwap.get()) {
            // 直接切换到快捷栏槽位
            if (slot in 0..8) {
                player.inventory.selectedSlot = slot
                mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(slot))
            }
        } else {
            // 使用 inventory swap 模式
            val selectedSlot = player.inventory.selectedSlot
            if (slot == selectedSlot) return
            
            // 如果物品在背包中，使用 SWAP 操作
            if (slot >= 9) {
                mc.interactionManager?.clickSlot(
                    player.currentScreenHandler.syncId,
                    slot,
                    selectedSlot,
                    SlotActionType.SWAP,
                    player
                )
            } else {
                // 如果物品在快捷栏中，直接切换
                player.inventory.selectedSlot = slot
                mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(slot))
            }
        }
    }
}
