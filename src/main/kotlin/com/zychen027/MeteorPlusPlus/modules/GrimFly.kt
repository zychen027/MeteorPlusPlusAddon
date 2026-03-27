package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.asm.mixin.IEntityVelocityUpdateS2CPacket
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.SettingGroup
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

/**
 * GrimFly - 绕过 GrimAC 飞行检测
 * 原理：通过缓存和延迟发送特定数据包来绕过 GrimAC 的移动检测
 * 
 * 移植自 yizhi/grimfly.java
 */
class GrimFly : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "GrimFly",
    "绕过 GrimAC 的飞行检测"
) {
    private val sgGeneral: SettingGroup = settings.getDefaultGroup()

    private var grimStarted = false
    private var shouldTriggerFly = false
    private var motionCounter = 0
    private val packetQueue = ArrayDeque<net.minecraft.network.packet.Packet<*>>()

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
        // 发送所有缓存的包
        while (packetQueue.isNotEmpty()) {
            packetQueue.removeFirst()?.let {
                mc.networkHandler?.sendPacket(it)
            }
        }
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        if (shouldTriggerFly) {
            motionCounter++
            if (motionCounter >= 8) {
                // 发送 START_FALL_FLYING 包
                val player = mc.player ?: return
                repeat(1) {
                    mc.networkHandler?.sendPacket(
                        ClientCommandC2SPacket(
                            player,
                            ClientCommandC2SPacket.Mode.START_FALL_FLYING
                        )
                    )
                }
                motionCounter = 0
                shouldTriggerFly = false
            }
        }
    }

    @EventHandler
    private fun onPacketSend(event: PacketEvent.Send) {
        val packet = event.packet

        // 拦截 PlayerInteractEntityC2SPacket（类似 C02PacketUseEntity）
        if (packet is PlayerInteractEntityC2SPacket) {
            if (grimStarted && packetQueue.isNotEmpty()) {
                // 发送所有缓存的包
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

        // 监听玩家位置回看包（类似 S08PacketPlayerPosLook）
        if (packet is PlayerPositionLookS2CPacket) {
            if (grimStarted && packetQueue.isNotEmpty()) {
                // 发送所有缓存的包
                while (packetQueue.isNotEmpty()) {
                    packetQueue.removeFirst()?.let {
                        mc.networkHandler?.sendPacket(it)
                    }
                }
            }
        }

        // 监听实体速度更新包（类似 S12PacketEntityVelocity）
        if (packet is EntityVelocityUpdateS2CPacket) {
            val velocityPacket = packet as IEntityVelocityUpdateS2CPacket
            if (velocityPacket.entityId == mc.player?.id) {
                if (grimStarted || packetQueue.isNotEmpty()) {
                    event.isCancelled = true
                    return
                }
                motionCounter = 0
                grimStarted = true
                event.isCancelled = true
            }
        }
    }
}
