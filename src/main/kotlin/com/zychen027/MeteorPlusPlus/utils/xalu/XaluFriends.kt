package com.zychen027.meteorplusplus.utils.xalu

import com.mojang.logging.LogUtils
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.player.PlayerEntity
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.*

/**
 * 好友系统 - 移植自 XALU
 * 用于管理好友列表，Follow 模块会忽略好友
 */
object XaluFriends {
    private val mc = MinecraftClient.getInstance()
    
    // 好友文件路径：MC 版本目录/friends.txt
    private val FRIENDS_FILE: File by lazy {
        File(mc.runDirectory, "friends.txt")
    }
    
    private val friends: MutableList<Friend> = ArrayList()
    private var loaded = false

    fun init() {
        load()
    }

    fun load() {
        if (!FRIENDS_FILE.exists()) {
            FRIENDS_FILE.parentFile?.mkdirs()
            try {
                FRIENDS_FILE.createNewFile()
            } catch (e: IOException) {
                LogUtils.getLogger().error("Failed to create friends file: " + e.message)
            }
            return
        }
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(FileReader(FRIENDS_FILE))
            friends.clear()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val line2 = line!!.trim { it <= ' ' }
                if (line2.isNotEmpty()) {
                    friends.add(Friend(line2))
                }
            }
            loaded = true
        } catch (e: IOException) {
            LogUtils.getLogger().error("Failed to load friends: " + e.message)
        } finally {
            try {
                reader?.close()
            } catch (e: IOException) {
                LogUtils.getLogger().error("Failed to close reader: " + e.message)
            }
        }
    }

    fun save() {
        try {
            FileWriter(FRIENDS_FILE).use { writer ->
                for (friend in friends) {
                    writer.write(friend.name)
                    writer.write("\n")
                }
            }
            LogUtils.getLogger().info("Friends list saved: " + friends.size + " friends")
        } catch (e: IOException) {
            LogUtils.getLogger().error("Failed to save friends: " + e.message)
        }
    }

    fun add(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        if (!isFriend(name)) {
            friends.add(Friend(name))
            save()
            LogUtils.getLogger().info("Friend added: $name")
            return true
        }
        return false
    }

    fun remove(name: String?): Boolean {
        val removed = friends.removeIf { friend: Friend -> friend.name.equals(name, ignoreCase = true) }
        if (removed) {
            save()
            LogUtils.getLogger().info("Friend removed: $name")
        }
        return removed
    }

    fun isFriend(name: String?): Boolean {
        return friends.stream().anyMatch { friend: Friend -> friend.name.equals(name, ignoreCase = true) }
    }

    fun isFriend(player: PlayerEntity): Boolean {
        return isFriend(player.name.string)
    }

    fun getFriends(): List<Friend> {
        return friends
    }
    
    fun getFriendCount(): Int {
        return friends.size
    }

    class Friend(val name: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val friend = other as Friend
            return name.equals(friend.name, ignoreCase = true)
        }

        override fun hashCode(): Int {
            return name.lowercase().hashCode()
        }
        
        override fun toString(): String {
            return name
        }
    }
}
