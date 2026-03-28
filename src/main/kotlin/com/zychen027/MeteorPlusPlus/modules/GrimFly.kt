package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.asm.mixin.IEntityVelocityUpdateS2CPacket
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.IntSetting
import meteordevelopment.meteorclient.settings.SettingGroup
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

/**
 * GrimFly - 绕过 GrimAC 飞行检测
 * 
 * 原始逻辑 (yizhi/grimfly.java):
 * 1. 收到速度包 → 标记开始，取消包
 * 2. 拦截 ConfirmTransaction → 缓存，队列空时触发飞行计数
 * 3. Motion 事件计数到 8 → 发送 START_FALL_FLYING
 * 4. 收到实体交互包/位置回看包 → 释放缓存包
 * 
 * 1.21.8 适配:
 * - ConfirmTransaction 已移除，使用 PlayerInteractEntityC2SPacket 作为触发点
 * - 使用 Tick 事件替代 Motion 事件进行计数
 * 
 * 发包顺序严格遵守原始逻辑
 */
class GrimFly : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "GrimFly",
    "绕过 GrimAC 的飞行检测"
) {
    private val sgGeneral: SettingGroup = settings.getDefaultGroup()

    private val packetDelay = sgGeneral.add(IntSetting.Builder()
        .name("packet-delay")
        .description("原始逻辑：计数到 8 后发送飞行包")
        .defaultValue(8)
        .min(1)
        .sliderMax(20)
        .build()
    )

    // 状态变量 - 严格对应原始代码
    private var grimStarted = false              // 对应 GrimLLLLLLLLLLLLLLLLLLL
    private var shouldTriggerFly = false         // 对应 FuckGrim
    private var motionCounter = 0                // 对应 GrimACIsBestAntiCheatLOL
    private val packetQueue = ArrayDeque<net.minecraft.network.packet.Packet<*>>()  // 对应 GrimAC_better_than_polar

    override fun onActivate() {
        motionCounter = 0
        grimStarted = false
        shouldTriggerFly = false
        packetQueue.clear()
    }

    override fun onDeactivate() {
        motionCounter = 0
        grimStarted = false
        shouldTriggerFly = false
        // 发送所有缓存的包（原始逻辑）
        while (packetQueue.isNotEmpty()) {
            packetQueue.removeFirst()?.let {
                mc.networkHandler?.sendPacket(it)
            }
        }
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        // 对应原始 onMotion 事件中的 PRE 类型逻辑
        if (shouldTriggerFly) {
            motionCounter++
            if (motionCounter >= packetDelay.get()) {
                // 发送 START_FALL_FLYING 包
                repeat(1) {
                    sendStartFlyPacket()
                }
                motionCounter = 0
                shouldTriggerFly = false
            }
        }
    }

    @EventHandler
    private fun onPacketSend(event: PacketEvent.Send) {
        val packet = event.packet

        // 对应原始 C02PacketUseEntity 处理逻辑
        if (packet is PlayerInteractEntityC2SPacket) {
            if (grimStarted && packetQueue.isNotEmpty()) {
                // 释放所有缓存的包
                while (packetQueue.isNotEmpty()) {
                    packetQueue.removeFirst()?.let {
                        mc.networkHandler?.sendPacket(it)
                    }
                }
            }
        }
    }

    @EventHandler
    private fun onPacketReceive(event: PacketEvent.Receive) {
        val packet = event.packet

        // 对应原始 S08PacketPlayerPosLook 处理逻辑
        if (packet is PlayerPositionLookS2CPacket) {
            if (grimStarted && packetQueue.isNotEmpty()) {
                // 释放所有缓存的包
                while (packetQueue.isNotEmpty()) {
                    packetQueue.removeFirst()?.let {
                        mc.networkHandler?.sendPacket(it)
                    }
                }
            }
        }

        // 对应原始 S12PacketEntityVelocity 处理逻辑 - 核心触发逻辑
        if (packet is EntityVelocityUpdateS2CPacket) {
            val velocityPacket = packet as IEntityVelocityUpdateS2CPacket
            if (velocityPacket.entityId == mc.player?.id) {
                // 对应原始：if (GrimLLLLLLLLLLLLLLLLLLL || !GrimAC_better_than_polar.isEmpty()) return
                if (grimStarted || packetQueue.isNotEmpty()) {
                    event.isCancelled = true
                    return
                }
                
                // 对应原始逻辑：
                // GrimACIsBestAntiCheatLOL = 0;
                // GrimLLLLLLLLLLLLLLLLLLL = true;
                // event.setCancelled(true);
                motionCounter = 0
                grimStarted = true
                shouldTriggerFly = true  // 1.21.8 适配：直接触发，无需 ConfirmTransaction
                event.isCancelled = true
            }
        }
    }

    private fun sendStartFlyPacket() {
        val player = mc.player ?: return
        if (player.isCreative || player.isSpectator || player.isOnGround) return

        mc.networkHandler?.sendPacket(
            ClientCommandC2SPacket(
                player,
                ClientCommandC2SPacket.Mode.START_FALL_FLYING
            )
        )
    }
}
