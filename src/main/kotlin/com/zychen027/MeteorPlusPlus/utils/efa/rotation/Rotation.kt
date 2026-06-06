package com.zychen027.meteorplusplus.utils.efa.rotation

import com.zychen027.meteorplusplus.asm.mixin.IPlayerMoveC2SPacketAccessor
import com.zychen027.meteorplusplus.modules.FireworkElytraFly
import com.zychen027.meteorplusplus.utils.efa.entity.MoveFixUtil
import com.zychen027.meteorplusplus.utils.efa.events.KeyboardInputEvent
import meteordevelopment.meteorclient.MeteorClient
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.orbit.EventHandler
import meteordevelopment.orbit.EventPriority
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.PositionFlag
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

object Rotation {
    @JvmField
    var rotationYaw = 0f
    @JvmField
    var rotationPitch = 0f
    @JvmField
    var rotation = false
    @JvmField
    var targetYaw = 0f
    @JvmField
    var targetPitch = 0f
    @JvmField
    var lastYaw = 0f
    @JvmField
    var lastPitch = 0f
    @JvmField
    var lastGround = false

    init {
        MeteorClient.EVENT_BUS.subscribe(this)
    }

    @JvmStatic
    fun snapAt(yaw: Float, pitch: Float) {
        if (FireworkElytraFly.INSTANCE?.moveFix?.get() == true) {
            rotation = true
            targetPitch = pitch
            targetYaw = yaw
        } else {
            if (FireworkElytraFly.INSTANCE?.grimRotation?.get() == true) {
                sendPacket(
                    PlayerMoveC2SPacket.Full(
                        MeteorClient.mc.player!!.x,
                        MeteorClient.mc.player!!.y,
                        MeteorClient.mc.player!!.z,
                        yaw,
                        pitch,
                        MeteorClient.mc.player!!.isOnGround,
                        MeteorClient.mc.player!!.horizontalCollision
                    )
                )
            } else {
                sendPacket(
                    PlayerMoveC2SPacket.LookAndOnGround(
                        yaw,
                        pitch,
                        MeteorClient.mc.player!!.isOnGround,
                        MeteorClient.mc.player!!.horizontalCollision
                    )
                )
            }
        }
    }

    @JvmStatic
    fun snapBack() {
        if (FireworkElytraFly.INSTANCE?.snapBack?.get() != true) return
        if (FireworkElytraFly.INSTANCE?.moveFix?.get() == true) return
        sendPacket(
            PlayerMoveC2SPacket.Full(
                MeteorClient.mc.player!!.x,
                MeteorClient.mc.player!!.y,
                MeteorClient.mc.player!!.z,
                rotationYaw,
                rotationPitch,
                MeteorClient.mc.player!!.isOnGround,
                MeteorClient.mc.player!!.horizontalCollision
            )
        )
    }

    @JvmStatic
    fun sendPacket(packet: net.minecraft.network.packet.Packet<*>) {
        MeteorClient.mc.networkHandler!!.sendPacket(packet)
    }

    @JvmStatic
    fun snapAt(directionVec: Vec3d) {
        val angle = getRotation(directionVec)
        snapAt(angle[0], angle[1])
    }

    @JvmStatic
    fun snapAt(box: Box) {
        snapAt(getClosestPointToEye(MeteorClient.mc.player!!.eyePos, box))
    }

    @EventHandler
    fun onKeyInput(event: KeyboardInputEvent) {
        if (!rotation) return
        MoveFixUtil.fixMovement(event, targetYaw)
    }

    @EventHandler(priority = -999)
    fun onPacketSend(event: PacketEvent.Send) {
        if (MeteorClient.mc.player == null || event.isCancelled) return
        if (event.packet is PlayerMoveC2SPacket) {
            val packet = event.packet as PlayerMoveC2SPacket
            if (packet.changesLook()) {
                val accessor = packet as IPlayerMoveC2SPacketAccessor
                lastYaw = accessor.getYaw()
                lastPitch = accessor.getPitch()
            }
            lastGround = packet.isOnGround
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onReceivePacket(event: PacketEvent.Receive) {
        if (MeteorClient.mc.player == null) return
        if (event.packet is PlayerPositionLookS2CPacket) {
            val packet = event.packet as PlayerPositionLookS2CPacket
            
            // 【终极修复】
            // 1. Yarn 中字段名为 change，其 getter 为 change()，Kotlin 语法可直接用 .change
            val pos = packet.change 
            
            // 2. 不需要导入 PositionMoveRotation 类！Kotlin 会通过隐式推断直接调用其 yaw() 和 pitch() 方法
            val yaw = pos.yaw()
            val pitch = pos.pitch()
            
            // 3. Yarn 中 relatives 的 getter 为 relatives()，Kotlin 语法用 .relatives
            val flags = packet.relatives

            if (flags.contains(PositionFlag.Y_ROT)) {
                lastYaw += yaw
            } else {
                lastYaw = yaw
            }
            if (flags.contains(PositionFlag.X_ROT)) {
                lastPitch += pitch
            } else {
                lastPitch = pitch
            }
        }
    }

    @JvmStatic
    fun getClosestPointToEye(eyePos: Vec3d, box: Box): Vec3d {
        var x = eyePos.x
        var y = eyePos.y
        var z = eyePos.z
        if (eyePos.x < box.minX) x = box.minX else if (eyePos.x > box.maxX) x = box.maxX
        if (eyePos.y < box.minY) y = box.minY else if (eyePos.y > box.maxY) y = box.maxY
        if (eyePos.z < box.minZ) z = box.minZ else if (eyePos.z > box.maxZ) z = box.maxZ
        return Vec3d(x, y, z)
    }

    @JvmStatic
    fun getRotation(eyesPos: Vec3d, vec: Vec3d): FloatArray {
        val diffX = vec.x - eyesPos.x
        val diffY = vec.y - eyesPos.y
        val diffZ = vec.z - eyesPos.z
        val diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ)
        var yaw = Math.toDegrees(Math.atan2(diffZ, diffX)).toFloat() - 90.0f
        var pitch = -Math.toDegrees(Math.atan2(diffY, diffXZ)).toFloat()
        yaw = MathHelper.wrapDegrees(yaw)
        pitch = MathHelper.wrapDegrees(pitch)
        return floatArrayOf(yaw, pitch)
    }

    @JvmStatic
    fun getRotation(vec: Vec3d): FloatArray {
        val eyesPos = MeteorClient.mc.player!!.eyePos
        return getRotation(eyesPos, vec)
    }
}
