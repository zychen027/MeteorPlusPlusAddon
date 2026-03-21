package com.zychen027.meteorplusplus.utils.rotation

import meteordevelopment.meteorclient.MeteorClient
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.atan2
import kotlin.math.sqrt

object Rotation {
    var rotationYaw: Float = 0f
    var rotationPitch: Float = 0f
    private val mc get() = MeteorClient.mc

    fun snapAt(yaw: Float, pitch: Float, overlay: Boolean = true) {
        val player = mc.player ?: return
        if (overlay) {
            rotationYaw = player.yaw
            rotationPitch = player.pitch
        }
        sendPacket(
            PlayerMoveC2SPacket.Full(
                player.x, player.y, player.z,
                yaw, pitch,
                player.isOnGround, player.horizontalCollision
            )
        )
    }

    fun snapBack(overlay: Boolean = true) {
        val player = mc.player ?: return
        sendPacket(
            PlayerMoveC2SPacket.Full(
                player.x, player.y, player.z,
                rotationYaw, rotationPitch,
                player.isOnGround, player.horizontalCollision
            )
        )
        if (overlay) {
            rotationYaw = player.yaw
            rotationPitch = player.pitch
        }
    }

    fun sendPacket(packet: net.minecraft.network.packet.Packet<*>) {
        mc.networkHandler?.sendPacket(packet)
    }

    fun snapAt(directionVec: Vec3d, overlay: Boolean = true) {
        val angle = getRotation(directionVec)
        snapAt(angle[0], angle[1], overlay)
    }

    fun snapAt(box: Box, overlay: Boolean = true) {
        val player = mc.player ?: return
        snapAt(getClosestPointToEye(player.eyePos, box), overlay)
    }

    fun getClosestPointToEye(eyePos: Vec3d, box: Box): Vec3d {
        val x = eyePos.x.coerceIn(box.minX, box.maxX)
        val y = eyePos.y.coerceIn(box.minY, box.maxY)
        val z = eyePos.z.coerceIn(box.minZ, box.maxZ)
        return Vec3d(x, y, z)
    }

    fun getRotation(eyesPos: Vec3d, vec: Vec3d): FloatArray {
        val diffX = vec.x - eyesPos.x
        val diffY = vec.y - eyesPos.y
        val diffZ = vec.z - eyesPos.z
        val diffXZ = sqrt(diffX * diffX + diffZ * diffZ)
        val yaw = (atan2(diffZ, diffX) * 180.0 / Math.PI).toFloat() - 90.0f
        val pitch = (-atan2(diffY, diffXZ) * 180.0 / Math.PI).toFloat()
        return floatArrayOf(MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch))
    }

    fun getRotation(vec: Vec3d): FloatArray {
        val player = mc.player ?: return floatArrayOf(0f, 0f)
        return getRotation(player.eyePos, vec)
    }
}
