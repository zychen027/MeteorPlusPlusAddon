package com.zychen027.meteorplusplus.utils.nofall_packet_sender

import net.minecraft.entity.Entity
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket

/**
 * 对齐 Java 版 Packet_v1_21_11：
 * - 1.21.11 的 PlayerMoveC2SPacket 子类构造
 * - createVehicleMove 使用 VehicleMoveC2SPacket.fromVehicle(entity)
 */
class Packet_v1_21_11 : VPacket {

    override fun createOnGroundOnly(isOnGround: Boolean, collision: Boolean): PlayerMoveC2SPacket {
        return PlayerMoveC2SPacket.OnGroundOnly(isOnGround, collision)
    }

    override fun createPositionAndOnGround(
        x: Double, y: Double, z: Double,
        isOnGround: Boolean, collision: Boolean
    ): PlayerMoveC2SPacket {
        return PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, isOnGround, collision)
    }

    override fun createLookAndOnGround(
        yaw: Float, pitch: Float,
        isOnGround: Boolean, collision: Boolean
    ): PlayerMoveC2SPacket {
        return PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, isOnGround, collision)
    }

    override fun createFull(
        x: Double, y: Double, z: Double,
        yaw: Float, pitch: Float,
        isOnGround: Boolean, collision: Boolean
    ): PlayerMoveC2SPacket {
        return PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, isOnGround, collision)
    }

    override fun createVehicleMove(entity: Entity): VehicleMoveC2SPacket {
        // 对齐 Java 版 Packet_v1_21_11：直接调用 fromVehicle
        return VehicleMoveC2SPacket.fromVehicle(entity)
    }
}
