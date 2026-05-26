package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.util.math.Vec3d
import java.lang.reflect.Field
import java.util.Random
import kotlin.math.sqrt

class ElytraGrimAccelerate : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "A 鞘翅Grim加速",
    "完整移植原版逻辑，在Grim服务器上通过构造假移动包实现鞘翅加速。"
) {
    private val sgGeneral = settings.getDefaultGroup()

    private val enable = sgGeneral.add(BoolSetting.Builder()
        .name("enable")
        .description("主开关")
        .defaultValue(true)
        .build()
    )

    private val setBackMode = sgGeneral.add(EnumSetting.Builder<ElytraSetBackMode>()
        .name("set-back-mode")
        .description("假移动包生成模式")
        .defaultValue(ElytraSetBackMode.SIMULATION)
        .build()
    )

    private val maxVelocity = sgGeneral.add(DoubleSetting.Builder()
        .name("max-accelerate-velocity")
        .description("停止加速的最大水平速度")
        .defaultValue(4.0)
        .min(0.0)
        .sliderMax(100.0)
        .build()
    )

    private val minVelocity = sgGeneral.add(DoubleSetting.Builder()
        .name("min-accelerate-velocity")
        .description("开始加速的最小水平速度")
        .defaultValue(3.6)
        .min(0.0)
        .sliderMax(100.0)
        .build()
    )

    private var setBackTick = 0
    private var setBackCount = 0
    private var receiveSetBackTick = false
    private var lastWorkingTick = 0
    private var currentTryWorking = false
    private var currentWorking = false
    private var storedPacket: PlayerMoveC2SPacket? = null
    private val rand = Random()

    private enum class ElytraSetBackMode {
        SIMULATION,
        CRASH_PACKETS
    }

    private object PacketReflection {
        private val fields = HashMap<String, Field>()

        init {
            cacheField(PlayerMoveC2SPacket::class.java, "cause")
        }

        private fun cacheField(clazz: Class<*>, name: String) {
            try {
                val field = clazz.getDeclaredField(name)
                field.isAccessible = true
                fields[name] = field
            } catch (_: Exception) {}
        }

        fun setString(packet: Any, fieldName: String, value: String) {
            try {
                fields[fieldName]?.set(packet, value)
            } catch (_: Exception) {}
        }
    }

    private object VPacket {
        fun newFull(
            x: Double,
            y: Double,
            z: Double,
            yaw: Float,
            pitch: Float,
            onGround: Boolean,
            horizontalCollision: Boolean
        ): PlayerMoveC2SPacket {
            return PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, onGround, horizontalCollision)
        }
    }

    @EventHandler
    private fun onVelocityUpdate(event: PacketEvent.Receive) {
        if (mc.player == null || !enable.get()) return
        if (event.packet !is EntityVelocityUpdateS2CPacket) return
        val packet = event.packet as EntityVelocityUpdateS2CPacket

        if (packet.entityId != mc.player!!.id) return

        // 1.21 Yarn 映射：packet.velocity 返回 Vec3d，且已经是真实速度，不需要再除 8000
        val velocity = packet.velocity
        val player = mc.player!!

        // 1.21 Yarn 映射：isFallFlying 变更为 isGliding
        val shouldWork = player.isGliding || lastWorkingTick + 10 > player.age

        if (shouldWork && player.isGliding) {
            if (velocity.x * velocity.x + velocity.z * velocity.z < 0.01) {
                event.cancel()
                return
            }

            val currentVelocity = player.velocity
            val currentHorizontal = Vec3d(currentVelocity.x, 0.0, currentVelocity.z)
            val packetHorizontal = Vec3d(velocity.x, 0.0, velocity.z)

            if (currentVelocity.x * currentVelocity.x + currentVelocity.z * currentVelocity.z > 0.01 &&
                currentHorizontal.dotProduct(packetHorizontal) < 0.0
            ) {
                event.cancel()
            }
        }
    }

    @EventHandler
    private fun onTeleportConfirm(event: PacketEvent.Send) {
        if (event.packet !is TeleportConfirmC2SPacket) return
        setBackTick = mc.player?.age ?: 0
        setBackCount++
        receiveSetBackTick = true
    }

    @EventHandler
    private fun onSendMovementPacket(event: PacketEvent.Send) {
        if (mc.player == null || !enable.get()) return
        if (event.packet !is PlayerMoveC2SPacket) return
        if (!currentTryWorking) return

        val velocity = mc.player!!.velocity
        val speed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)

        if (currentWorking) {
            if (speed > maxVelocity.get()) currentWorking = false
        } else {
            if (speed < minVelocity.get()) currentWorking = true
        }

        if (!currentWorking) return

        event.cancel()
        val player = mc.player!!

        val tickCount = player.age % 3
        val addY = 2.5 * (tickCount + 1)

        val currentMode = setBackMode.get()
        val fakePacket = when (currentMode) {
            ElytraSetBackMode.SIMULATION -> VPacket.newFull(
                player.x,
                player.y + addY,
                player.z,
                player.yaw,
                player.pitch,
                player.isOnGround,
                player.horizontalCollision
            )
            ElytraSetBackMode.CRASH_PACKETS -> VPacket.newFull(
                3.9999999E7,
                player.y + addY,
                Double.NEGATIVE_INFINITY,
                player.yaw,
                player.pitch,
                true,
                player.horizontalCollision
            )
        }

        PacketReflection.setString(fakePacket, "cause", "TRIGGER_SIMULATION")
        storedPacket = fakePacket
    }

    @EventHandler
    private fun onPreTick(event: TickEvent.Pre) {
        if (mc.player == null || !enable.get()) {
            currentTryWorking = false
            return
        }

        val player = mc.player!!
        val canFireworkControl = false
        currentTryWorking = player.isGliding && !player.isOnGround && !canFireworkControl

        if (currentTryWorking) {
            lastWorkingTick = player.age
        }
    }

    @EventHandler
    private fun onPostTick(event: TickEvent.Post) {
        if (!enable.get()) return
        val packet = storedPacket ?: return

        mc.networkHandler?.sendPacket(TeleportConfirmC2SPacket(rand.nextInt(Int.MAX_VALUE - 1)))
        mc.networkHandler?.sendPacket(packet)

        storedPacket = null
        receiveSetBackTick = false
        currentTryWorking = false
    }
}
