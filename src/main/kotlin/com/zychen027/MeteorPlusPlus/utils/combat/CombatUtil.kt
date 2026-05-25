package com.zychen027.meteorplusplus.utils.combat

import com.google.common.collect.Lists
import meteordevelopment.meteorclient.MeteorClient
import meteordevelopment.meteorclient.systems.friends.Friends
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity

/**
 * 战斗工具类 - 来自 LeavesHack
 * 用于获取和验证敌人目标
 */
object CombatUtil {

    private val mc get() = MeteorClient.mc

    /**
     * 获取范围内的所有敌人
     */
    fun getEnemies(range: Double): List<PlayerEntity> {
        val list = mutableListOf<PlayerEntity>()
        for (player in Lists.newArrayList(mc.world?.players ?: emptyList())) {
            if (!isValid(player, range)) continue
            list.add(player)
        }
        return list
    }

    /**
     * 验证实体是否为有效目标 (带范围检查)
     */
    fun isValid(entity: Entity?, range: Double): Boolean {
        val player = mc.player ?: return false
        if (entity == null) return false
        if (!entity.isAlive) return false
        if (entity == player) return false
        if (entity is PlayerEntity && Friends.get().isFriend(entity)) return false
        
        // 修复：直接使用 Entity 自带的 distanceTo 方法，彻底避开 1.21.11 中 getPos() 映射变更问题
        if (player.distanceTo(entity) > range) return false
        return true
    }

    /**
     * 验证实体是否为有效目标 (无范围检查)
     */
    fun isValid(entity: Entity?): Boolean {
        val player = mc.player ?: return false
        if (entity == null) return false
        if (!entity.isAlive) return false
        if (entity == player) return false
        if (entity is PlayerEntity && Friends.get().isFriend(entity)) return false
        return true
    }

    /**
     * 获取最近的敌人
     */
    fun getClosestEnemy(distance: Double): PlayerEntity? {
        val player = mc.player ?: return null
        val enemies = getEnemies(distance)
        if (enemies.isEmpty()) return null

        // 修复：通过先获取首个敌人，保证 closest 不为空，避开编译器对可空类型的错误推断（消除 String.compareTo 报错）
        var closest = enemies[0]
        for (i in 1 until enemies.size) {
            val otherPlayer = enemies[i]
            // 修复：直接使用 Entity 自带的 squaredDistanceTo 方法
            if (player.squaredDistanceTo(otherPlayer) < player.squaredDistanceTo(closest)) {
                closest = otherPlayer
            }
        }
        return closest
    }
}
