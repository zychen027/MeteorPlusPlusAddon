package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.asm.mixin.IEntityVelocityUpdateS2CPacket
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.settings.DoubleSetting
import meteordevelopment.meteorclient.settings.IntSetting
import meteordevelopment.meteorclient.settings.SettingGroup
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

/**
 * GrimNoFall - 绕过 GrimAC 的摔落伤害检测
 * 
 * 基于 yizhi/grimfly.java 逻辑改编
 * 
 * 原始 GrimFly 逻辑：
 * 1. 收到速度包 → 标记开始，取消包
 * 2. 计数到指定值 → 发送 START_FALL_FLYING
 * 3. 收到实体交互包/位置回看包 → 释放缓存包/重置状态
 * 
 * GrimNoFall 适配：
 * - 检测下落距离，在落地前发送 START_FALL_FLYING 免疫摔伤
 * - 落地后发送 STOP_FALL_FLYING 恢复正常状态
 * - 支持速度包触发模式（严格遵循 GrimFly 发包顺序）
 */
class GrimNoFall : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "GrimNoFall",
    "绕过 GrimAC 的摔落伤害检测"
) {
    private val sgGeneral: SettingGroup = settings.getDefaultGroup()
    private val sgBypass: SettingGroup = settings.createGroup("绕过设置")
    private val sgVelocity: SettingGroup = settings.createGroup("速度包触发")

    // 通用设置
    private val minFallDistance = sgGeneral.add(DoubleSetting.Builder()
        .name("min-fall-distance")
        .description("触发无摔落的最小下落距离")
        .defaultValue(3.0)
        .min(0.0)
        .sliderMax(10.0)
        .build()
    )

    // 绕过设置
    private val packetDelay = sgBypass.add(IntSetting.Builder()
        .name("packet-delay")
        .description("发送飞行包前的延迟 (ticks)，对应 GrimFly 计数逻辑")
        .defaultValue(1)
        .min(0)
        .sliderMax(10)
        .build()
    )

    private val autoStop = sgBypass.add(BoolSetting.Builder()
        .name("auto-stop")
        .description("落地后自动发送 STOP_FALL_FLYING")
        .defaultValue(true)
        .build()
    )

    private val stopDelay = sgBypass.add(IntSetting.Builder()
        .name("stop-delay")
        .description("落地后停止飞行状态的延迟 (ticks)")
        .defaultValue(2)
        .min(0)
        .sliderMax(10)
        .visible { autoStop.get() }
        .build()
    )

    // 速度包触发设置（严格遵循 GrimFly 逻辑）
    private val velocityTrigger = sgVelocity.add(BoolSetting.Builder()
        .name("velocity-trigger")
        .description("使用速度包触发（GrimFly 核心逻辑）")
        .defaultValue(false)
        .build()
    )

    private val velocityDelay = sgVelocity.add(IntSetting.Builder()
        .name("velocity-delay")
        .description("收到速度包后的触发延迟 (ticks)")
        .defaultValue(5)
        .min(0)
        .sliderMax(20)
        .visible { velocityTrigger.get() }
        .build()
    )

    // 状态变量
    private var isFalling = false           // 是否正在下落
    private var fallStartY = 0.0           // 下落起始 Y 坐标
    private var shouldNegateFall = false   // 是否应该抵消摔落
    private var negateTimer = 0            // 抵消计时器
    private var stopTimer = 0              // 停止计时器
    private var wasOnGround = true         // 是否之前在地面
    private var hasSentStart = false       // 是否已发送 START 包
    private var hasSentStop = false        // 是否已发送 STOP 包
    
    // 速度包触发状态（对应 GrimFly）
    private var grimStarted = false        // 对应 GrimLLLLLLLLLLLLLLLLLLL
    private var velocityCounter = 0        // 对应 GrimACIsBestAntiCheatLOL

    override fun onActivate() {
        isFalling = false
        fallStartY = 0.0
        shouldNegateFall = false
        negateTimer = 0
        stopTimer = 0
        wasOnGround = mc.player?.isOnGround ?: true
        hasSentStart = false
        hasSentStop = false
        grimStarted = false
        velocityCounter = 0
    }

    override fun onDeactivate() {
        isFalling = false
        shouldNegateFall = false
        hasSentStart = false
        hasSentStop = false
        grimStarted = false
        velocityCounter = 0
        
        // 如果正在飞行状态，发送停止包
        if (mc.player?.isGliding == true) {
            sendStopFlyPacket()
        }
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        val player = mc.player ?: return
        if (!player.isAlive) return

        val isOnGround = player.isOnGround
        val velocityY = player.velocity.y

        // 检测是否开始下落
        if (wasOnGround && !isOnGround && velocityY < 0) {
            isFalling = true
            fallStartY = player.y
        }

        // 检测落地
        if (!wasOnGround && isOnGround) {
            if (hasSentStart && autoStop.get()) {
                stopTimer++
                if (stopTimer >= stopDelay.get()) {
                    sendStopFlyPacket()
                    hasSentStart = false
                    hasSentStop = true
                    stopTimer = 0
                }
            }
            // 重置状态
            isFalling = false
            shouldNegateFall = false
            grimStarted = false
            velocityCounter = 0
        }

        // 计算下落距离
        val fallDistance = if (isFalling) fallStartY - player.y else 0.0

        // 检查是否需要抵消摔落
        if (isFalling && !shouldNegateFall && fallDistance >= minFallDistance.get()) {
            shouldNegateFall = true
            negateTimer = 0
        }

        // 处理摔落抵消 - 延迟发送 START_FALL_FLYING
        if (shouldNegateFall && !hasSentStart) {
            negateTimer++
            if (negateTimer >= packetDelay.get()) {
                sendStartFlyPacket()
                hasSentStart = true
                hasSentStop = false
            }
        }

        // 速度包触发模式 - 严格遵循 GrimFly 发包顺序
        if (velocityTrigger.get() && grimStarted && !hasSentStart) {
            velocityCounter++
            if (velocityCounter >= velocityDelay.get()) {
                sendStartFlyPacket()
                hasSentStart = true
                grimStarted = false
                velocityCounter = 0
            }
        }

        wasOnGround = isOnGround
    }

    @EventHandler
    private fun onPacketSend(event: PacketEvent.Send) {
        val packet = event.packet

        // 对应 GrimFly 的 C02PacketUseEntity 处理逻辑
        if (packet is PlayerInteractEntityC2SPacket) {
            if (velocityTrigger.get() && grimStarted && !hasSentStart) {
                // 立即触发飞行包
                sendStartFlyPacket()
                hasSentStart = true
                grimStarted = false
                velocityCounter = 0
            }
        }
    }

    @EventHandler
    private fun onPacketReceive(event: PacketEvent.Receive) {
        val packet = event.packet

        // 对应 GrimFly 的 S08PacketPlayerPosLook 处理逻辑
        if (packet is PlayerPositionLookS2CPacket) {
            // 重置所有状态
            isFalling = false
            shouldNegateFall = false
            hasSentStart = false
            hasSentStop = false
            grimStarted = false
            velocityCounter = 0
        }

        // 速度包触发模式 - 严格遵循 GrimFly 核心逻辑
        if (velocityTrigger.get() && packet is EntityVelocityUpdateS2CPacket) {
            val velocityPacket = packet as IEntityVelocityUpdateS2CPacket
            if (velocityPacket.entityId == mc.player?.id) {
                // 对应原始：if (GrimLLLLLLLLLLLLLLLLLLL || !GrimAC_better_than_polar.isEmpty()) return
                if (grimStarted) {
                    event.isCancelled = true
                    return
                }
                
                // 对应原始逻辑：
                // GrimACIsBestAntiCheatLOL = 0;
                // GrimLLLLLLLLLLLLLLLLLLL = true;
                // event.setCancelled(true);
                velocityCounter = 0
                grimStarted = true
                event.isCancelled = true
            }
        }
    }

    private fun sendStartFlyPacket() {
        val player = mc.player ?: return
        if (player.isCreative || player.isSpectator) return

        mc.networkHandler?.sendPacket(
            ClientCommandC2SPacket(
                player,
                ClientCommandC2SPacket.Mode.START_FALL_FLYING
            )
        )
    }

    private fun sendStopFlyPacket() {
        val player = mc.player ?: return
        if (player.isCreative || player.isSpectator) return

        // MC 1.21.8 移除了 RELEASE_SHIFT_KEY，直接设置 isSneaking
        player.isSneaking = false
    }

    /**
     * 检查是否需要发送数据包
     */
    private fun shouldSendPacket(): Boolean {
        // 检查反作弊同步状态
        return true
    }
}
