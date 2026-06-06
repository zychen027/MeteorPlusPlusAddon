package com.zychen027.meteorplusplus.utils.combat

import meteordevelopment.meteorclient.MeteorClient.mc
import meteordevelopment.meteorclient.systems.friends.Friends
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity

object CombatUtil {

    fun getEnemies(range: Double): List<PlayerEntity> {
        val list = mutableListOf<PlayerEntity>()
        for (player in mc.world?.players ?: emptyList()) {
            if (!isValid(player, range)) continue
            list.add(player)
        }
        return list
    }

    fun isValid(entity: Entity?, range: Double): Boolean {
        if (entity == null || !entity.isAlive) return false
        val player = mc.player ?: return false
        if (entity == player) return false
        if (entity is PlayerEntity && Friends.get().isFriend(entity)) return false
        return player.distanceTo(entity) <= range
    }

    fun isValid(entity: Entity?): Boolean {
        if (entity == null || !entity.isAlive) return false
        if (entity == mc.player) return false
        if (entity is PlayerEntity && Friends.get().isFriend(entity)) return false
        return true
    }

    fun getClosestEnemy(distance: Double): PlayerEntity? {
        var closest: PlayerEntity? = null
        for (player in getEnemies(distance)) {
            if (closest == null) {
                closest = player
                continue
            }
            val playerDist = mc.player?.squaredDistanceTo(player) ?: Double.MAX_VALUE
            val closestDist = mc.player?.squaredDistanceTo(closest) ?: Double.MAX_VALUE
            if (playerDist >= closestDist) continue
            closest = player
        }
        return closest
    }

    /**
     * 重置攻击冷却（适配 1.21.11 yarn 映射）
     *
     * 1.21.11 映射名：
     *   - lastAttackedTime  (field_6230)
     *   - ticksSinceLastAttack (field_6273)
     *
     * 没有 resetLastAttackedTicks() 方法，只能反射写字段
     */
    fun resetAttackCooldown(player: LivingEntity) {
        // 优先尝试 1.21.11 yarn: lastAttackedTime
        try {
            val field = LivingEntity::class.java.getDeclaredField("lastAttackedTime")
            field.isAccessible = true
            field.setInt(player, 0)
            return
        } catch (_: Exception) {}

        // 降级: ticksSinceLastAttack
        try {
            val field = LivingEntity::class.java.getDeclaredField("ticksSinceLastAttack")
            field.isAccessible = true
            field.setInt(player, 0)
            return
        } catch (_: Exception) {}

        // 再降级: 旧版映射名 lastAttackedTicks
        try {
            val field = LivingEntity::class.java.getDeclaredField("lastAttackedTicks")
            field.isAccessible = true
            field.setInt(player, 0)
        } catch (_: Exception) {}
    }
}
