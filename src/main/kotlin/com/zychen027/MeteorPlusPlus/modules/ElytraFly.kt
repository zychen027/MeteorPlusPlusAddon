package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.utils.math.Timer
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.MovementType
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

/**
 * ElytraFly (鞘翅飞行) - 移植自 Alien-V4
 * 仅移植 Freeze 模式，包含完整的绕过反作弊逻辑
 * 
 * MC 1.21.8 API 适配：
 * - startFallFlying → startGliding
 * - stopFallFlying → stopGliding
 * - isFallFlying → isGliding
 */
class ElytraFly : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "ElytraFly",
    "鞘翅飞行 - Freeze 模式"
) {
    private val sgGeneral = settings.getDefaultGroup()
    private val sgPacket = settings.createGroup("Packet")
    private val sgOther = settings.createGroup("Other")

    // ==================== Freeze 模式设置 ====================
    private val freeze = sgGeneral.add(BoolSetting.Builder()
        .name("Freeze")
        .description("静止时悬停")
        .defaultValue(false)
        .build())

    private val motionStop = sgGeneral.add(BoolSetting.Builder()
        .name("MotionStop")
        .description("静止时停止移动")
        .defaultValue(false)
        .build())
    private val packet = sgPacket.add(BoolSetting.Builder()
        .name("Packet")
        .description("数据包模式")
        .defaultValue(false)
        .build())

    private val packetDelay = sgPacket.add(IntSetting.Builder()
        .name("PacketDelay")
        .description("数据包延迟 (ticks)")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .visible { packet.get() }
        .build())

    private val setFlag = sgPacket.add(BoolSetting.Builder()
        .name("SetFlag")
        .description("设置标志")
        .defaultValue(false)
        .visible { packet.get() }
        .build())

    // ==================== 其他设置 ====================
    private val infiniteDura = sgOther.add(BoolSetting.Builder()
        .name("InfiniteDura")
        .description("无限耐久（仅视觉）")
        .defaultValue(false)
        .build())

    private val instantFly = sgOther.add(BoolSetting.Builder()
        .name("AutoStart")
        .description("自动起飞")
        .defaultValue(true)
        .build())

    private val timeout = sgOther.add(DoubleSetting.Builder()
        .name("Timeout")
        .description("超时 (秒)")
        .defaultValue(0.0)
        .min(0.1)
        .sliderMax(1.0)
        .build())

    private val autoStop = sgOther.add(BoolSetting.Builder()
        .name("AutoStop")
        .description("自动停止（区块未加载）")
        .defaultValue(true)
        .build())

    // ==================== 状态变量 ====================
    private val instantFlyTimer = Timer()
    private val fireworkTimer = Timer()
    private var hasElytra = false
    private var flying = false
    private var packetDelayInt = 0

    override fun onActivate() {
        if (mc.player == null || mc.world == null) return
        hasElytra = false
        instantFlyTimer.reset()
        fireworkTimer.setMs(99999999)
    }

    override fun onDeactivate() {
        if (mc.player == null || mc.world == null) return
        mc.player!!.isSneaking = false
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        if (mc.player == null || mc.world == null) return

        // 检查是否有鞘翅
        if (packet.get()) {
            hasElytra = findElytraInInventory() != -1
        } else {
            hasElytra = false
            val chestplate = mc.player!!.getEquippedStack(EquipmentSlot.CHEST)
            if (chestplate.item == Items.ELYTRA) {
                hasElytra = true
            }

            // 无限耐久逻辑
            if (infiniteDura.get() && !mc.player!!.isOnGround && hasElytra) {
                flying = true
                mc.interactionManager?.clickSlot(mc.player!!.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player!!)
                mc.interactionManager?.clickSlot(mc.player!!.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player!!)
                mc.networkHandler?.sendPacket(ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))
                if (setFlag.get()) mc.player!!.startGliding()
            }
        }

        // Packet 模式处理
        if (packet.get()) {
            handlePacketMode()
            return
        }

        // 自动起飞逻辑（核心反作弊绕过）
        if (!isFallFlying() && hasElytra && instantFly.get() &&
            !mc.player!!.isOnGround && mc.player!!.velocity.y < 0.0 && !infiniteDura.get()
        ) {
            if (!instantFlyTimer.passed((timeout.get() * 1000).toLong())) return
            instantFlyTimer.reset()
            mc.networkHandler?.sendPacket(ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))
            if (setFlag.get()) mc.player!!.startGliding()
        }

        // 速度检测与烟花使用
        val speed = calculateSpeed()
        if ((!checkSpeed.get() || speed <= minSpeed.get()) &&
            firework.get() &&
            fireworkTimer.passed(fireworkDelay.get().toLong()) &&
            isMoving() &&
            (!mc.player!!.isUsingItem || !usingPause.get()) &&
            isFallFlying()
        ) {
            useFirework()
            fireworkTimer.reset()
        }
    }

    @EventHandler
    private fun onMove(event: PlayerMoveEvent) {
        if (mc.player == null || mc.world == null) return

        // Freeze 模式核心逻辑 - 完整绕过反作弊
        if (hasElytra && isFallFlying()) {
            if (!isMoving() && !mc.options.jumpKey.isPressed && !mc.options.sneakKey.isPressed) {
                if (freeze.get()) {
                    // 完全静止 - 取消移动事件
                    event.movement = Vec3d.ZERO
                    mc.player!!.setVelocity(0.0, 0.0, 0.0)
                    return
                }
            }
        }

        // 自动停止（区块未加载时）- 反作弊绕过
        if (autoStop.get() && isFallFlying()) {
            val chunkX = (mc.player!!.x / 16.0).toInt()
            val chunkZ = (mc.player!!.z / 16.0).toInt()
            if (!mc.world!!.chunkManager.isChunkLoaded(chunkX, chunkZ)) {
                event.movement = Vec3d.ZERO
            }
        }
    }

    @EventHandler
    private fun onPacketSend(event: PacketEvent.Send) {
        if (mc.player == null || mc.world == null) return
    }

    @EventHandler
    private fun onPacketReceive(event: PacketEvent.Receive) {
        if (mc.player == null || mc.world == null) return
    }

    // ==================== 工具方法 ====================

    private fun isFallFlying(): Boolean {
        return mc.player!!.isGliding || (packet.get() && hasElytra && !mc.player!!.isOnGround) || flying
    }

    private fun isMoving(): Boolean {
        return mc.options.forwardKey.isPressed || mc.options.backKey.isPressed ||
               mc.options.leftKey.isPressed || mc.options.rightKey.isPressed
    }

    private fun findElytraInInventory(): Int {
        for (i in 0 until mc.player!!.inventory.size()) {
            if (mc.player!!.inventory.getStack(i).item == Items.ELYTRA) {
                return i
            }
        }
        return -1
    }

    private fun calculateSpeed(): Double {
        val player = mc.player ?: return 0.0
        // MC 1.21.8: prevX/prevY/prevZ 已移除，使用 lastRenderX/Y/Z
        val x = player.x - player.lastRenderX
        val y = player.y - player.lastRenderY
        val z = player.z - player.lastRenderZ
        val dist = kotlin.math.sqrt(x * x + z * z + y * y) / 1000.0
        val div = 1.388888888888889E-5
        val timer = 1.0f
        return dist / div * timer
    }

    private fun handlePacketMode() {
        if (mc.player!!.isOnGround) return

        packetDelayInt++
        if (packetDelayInt <= packetDelay.get()) return

        val elytra = findElytraInInventory()
        if (elytra != -1) {
            // 交换鞘翅到胸甲槽
            mc.interactionManager?.clickSlot(mc.player!!.currentScreenHandler.syncId, 6, elytra, SlotActionType.SWAP, mc.player!!)
            // 发送起飞数据包
            mc.networkHandler?.sendPacket(ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))
            mc.player!!.startGliding()
            // 交换回来
            mc.interactionManager?.clickSlot(mc.player!!.currentScreenHandler.syncId, 6, elytra, SlotActionType.SWAP, mc.player!!)
            packetDelayInt = 0
        }
    }

    private fun useFirework() {
        if (mc.player!!.mainHandStack.item == Items.FIREWORK_ROCKET) {
            mc.interactionManager?.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND)
        } else {
            val fireworkSlot = findFireworkInInventory()
            if (fireworkSlot != -1) {
                val selectedSlot = mc.player!!.inventory.selectedSlot
                mc.interactionManager?.clickSlot(
                    mc.player!!.currentScreenHandler.syncId,
                    fireworkSlot,
                    selectedSlot,
                    SlotActionType.SWAP,
                    mc.player!!
                )
                mc.interactionManager?.interactItem(mc.player, net.minecraft.util.Hand.MAIN_HAND)
                mc.interactionManager?.clickSlot(
                    mc.player!!.currentScreenHandler.syncId,
                    fireworkSlot,
                    selectedSlot,
                    SlotActionType.SWAP,
                    mc.player!!
                )
            }
        }
    }

    private fun findFireworkInInventory(): Int {
        for (i in 0 until mc.player!!.inventory.size()) {
            if (mc.player!!.inventory.getStack(i).item == Items.FIREWORK_ROCKET) {
                return i
            }
        }
        return -1
    }

    // ==================== 设置项（烟花相关） ====================
    private val firework = sgOther.add(BoolSetting.Builder()
        .name("Firework")
        .description("自动使用烟花")
        .defaultValue(false)
        .build())

    private val fireworkDelay = sgOther.add(IntSetting.Builder()
        .name("FireworkDelay")
        .description("烟花延迟 (ms)")
        .defaultValue(1000)
        .min(0)
        .sliderMax(20000)
        .visible { firework.get() }
        .build())

    private val usingPause = sgOther.add(BoolSetting.Builder()
        .name("UsingPause")
        .description("使用时暂停")
        .defaultValue(true)
        .visible { firework.get() }
        .build())

    private val checkSpeed = sgOther.add(BoolSetting.Builder()
        .name("CheckSpeed")
        .description("检查速度")
        .defaultValue(false)
        .build())

    private val minSpeed = sgOther.add(DoubleSetting.Builder()
        .name("MinSpeed")
        .description("最小速度")
        .defaultValue(70.0)
        .min(0.1)
        .sliderMax(200.0)
        .visible { checkSpeed.get() }
        .build())
}
