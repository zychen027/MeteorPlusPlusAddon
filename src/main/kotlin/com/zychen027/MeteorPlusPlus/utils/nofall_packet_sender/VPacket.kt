package com.zychen027.meteorplusplus.utils.nofall_packet_sender

import net.minecraft.entity.Entity
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.util.math.Vec3d

/**
 * 对齐 Java 版 VPacket：
 * - 静态工厂方法 newOnGroundOnly / newPositionAndOnGround / newLookAndOnGround / newFull / newVehicleMove
 * - 静态工具方法 getCollisionFlag / getVelocity
 * - 实例方法 createXxx
 * - instance 字段
 */
interface VPacket {

    companion object {
        val instance: VPacket = Packet_v1_21_11()

        // ===== 反射获取 horizontalCollision 字段 (修复 protected 访问限制) =====
        private val collisionField: java.lang.reflect.Field? by lazy {
            try {
                // 在 1.21.1 中，该字段位于 PlayerMoveC2SPacket 父类中
                val field = PlayerMoveC2SPacket::class.java.getDeclaredField("horizontalCollision")
                field.isAccessible = true
                field
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        // ===== 静态工厂方法 =====

        @JvmStatic
        fun newOnGroundOnly(isOnGround: Boolean, collision: Boolean): PlayerMoveC2SPacket {
            return instance.createOnGroundOnly(isOnGround, collision)
        }

        @JvmStatic
        fun newPositionAndOnGround(
            x: Double, y: Double, z: Double,
            isOnGround: Boolean, collision: Boolean
        ): PlayerMoveC2SPacket {
            return instance.createPositionAndOnGround(x, y, z, isOnGround, collision)
        }

        @JvmStatic
        fun newLookAndOnGround(
            yaw: Float, pitch: Float,
            isOnGround: Boolean, collision: Boolean
        ): PlayerMoveC2SPacket {
            return instance.createLookAndOnGround(yaw, pitch, isOnGround, collision)
        }

        @JvmStatic
        fun newFull(
            x: Double, y: Double, z: Double,
            yaw: Float, pitch: Float,
            isOnGround: Boolean, collision: Boolean
        ): PlayerMoveC2SPacket {
            return instance.createFull(x, y, z, yaw, pitch, isOnGround, collision)
        }

        @JvmStatic
        fun newVehicleMove(entity: Entity): VehicleMoveC2SPacket {
            return instance.createVehicleMove(entity)
        }

        // ===== 静态工具方法（对齐 Java VPacket） =====

        @JvmStatic
        fun getCollisionFlag(packet: PlayerMoveC2SPacket): Boolean {
            // 使用反射获取 protected 的 horizontalCollision 字段
            return collisionField?.getBoolean(packet) ?: false
        }

        @JvmStatic
        fun getVelocity(packet: EntityVelocityUpdateS2CPacket): Vec3d {
            return packet.velocity
        }
    }

    // ===== 实例方法 =====

    fun createOnGroundOnly(isOnGround: Boolean, collision: Boolean): PlayerMoveC2SPacket
    fun createPositionAndOnGround(
        x: Double, y: Double, z: Double,
        isOnGround: Boolean, collision: Boolean
    ): PlayerMoveC2SPacket

    fun createLookAndOnGround(
        yaw: Float, pitch: Float,
        isOnGround: Boolean, collision: Boolean
    ): PlayerMoveC2SPacket

    fun createFull(
        x: Double, y: Double, z: Double,
        yaw: Float, pitch: Float,
        isOnGround: Boolean, collision: Boolean
    ): PlayerMoveC2SPacket

    fun createVehicleMove(entity: Entity): VehicleMoveC2SPacket
}
