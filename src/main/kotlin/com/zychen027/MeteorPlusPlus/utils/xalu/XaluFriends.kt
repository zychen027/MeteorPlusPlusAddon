package com.zychen027.meteorplusplus.utils.xalu

import com.mojang.logging.LogUtils
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.player.PlayerEntity
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.*

/**
 * 好友系统 - 移植自 XALU
 * 用于管理好友列表，Follow 模块会忽略好友
 */
object XaluFriends {
    private const val FRIENDS_FILE = "alien/friends.txt"
    private val friends: MutableList<Friend> = ArrayList()
    private var loaded = false
    private val mc = MinecraftClient.getInstance()

    fun init() {
        load()
    }

    fun load() {
        val file = File(FRIENDS_FILE)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            try {
                file.createNewFile()
                add("xianliu")
            } catch (e: IOException) {
                LogUtils.getLogger().error("Failed to create friends file: " + e.message)
            }
            return
        }
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(FileReader(file))
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
        LogUtils.getLogger().info("Save functionality is disabled. Friends can only be added by editing friends.txt file directly.")
    }

    fun add(name: String?) {
        if (!isFriend(name)) {
            friends.add(Friend(name!!))
            LogUtils.getLogger().info("Friend added to memory. To make it permanent, add it to friends.txt file directly.")
        }
    }

    fun remove(name: String?) {
        friends.removeIf { friend: Friend -> friend.name.equals(name, ignoreCase = true) }
        LogUtils.getLogger().info("Friend removed from memory. To make it permanent, remove it from friends.txt file directly.")
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
    }
}
