package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.player.ChatUtils
import meteordevelopment.orbit.EventHandler
import net.minecraft.client.network.PlayerListEntry
import java.util.*

/**
 * BetterTab - 仿照 Rusherhack 的 BetterTab 模块
 * 将指定玩家在 Tab 菜单中置顶显示
 */
class BetterTab : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "更好的Tab菜单",
    "将指定玩家在 Tab 菜单中置顶显示。"
) {
    private val sgGeneral = settings.getDefaultGroup()
    private val sgPinned = settings.createGroup("置顶玩家")

    private val enable = sgGeneral.add(BoolSetting.Builder()
        .name("启用")
        .description("启用 BetterTab 功能。")
        .defaultValue(true)
        .build())

    private val showInTab = sgGeneral.add(BoolSetting.Builder()
        .name("Tab 中显示标识")
        .description("在 Tab 中显示置顶玩家标识。")
        .defaultValue(true)
        .build())

    private val pinnedPlayersList = sgPinned.add(StringListSetting.Builder()
        .name("置顶玩家列表")
        .description("要置顶到 Tab 顶部的玩家列表。")
        .build())

    // 运行时使用的可变列表
    @Volatile
    private var pinnedPlayers: MutableList<String> = mutableListOf()

    @Volatile
    var isRendering = false

    companion object {
        @JvmStatic
        var INSTANCE: BetterTab? = null
    }

    init {
        INSTANCE = this
    }

    override fun onActivate() {
        updatePinnedPlayers()
    }

    override fun onDeactivate() {
        pinnedPlayers.clear()
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        // 定期更新置顶玩家列表
        updatePinnedPlayers()
    }

    /**
     * 更新置顶玩家列表
     */
    private fun updatePinnedPlayers() {
        try {
            val newList = ArrayList<String>()
            for (player in pinnedPlayersList.get()) {
                val name = player.trim()
                if (name.isNotEmpty()) {
                    newList.add(name)
                }
            }
            pinnedPlayers = newList
        } catch (e: Exception) {
            // 忽略异常
        }
    }

    /**
     * 获取置顶玩家列表
     */
    fun getPinnedPlayers(): List<String> {
        return Collections.unmodifiableList(pinnedPlayers)
    }

    /**
     * 添加置顶玩家
     */
    fun addPinnedPlayer(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isNotEmpty() && !pinnedPlayers.contains(normalizedName)) {
            pinnedPlayers = ArrayList(pinnedPlayers).apply { add(normalizedName) }
            pinnedPlayersList.set(ArrayList(pinnedPlayers))
            ChatUtils.info("§a已添加置顶玩家：$normalizedName")
        }
    }

    /**
     * 移除置顶玩家
     */
    fun removePinnedPlayer(name: String) {
        val normalizedName = name.trim()
        if (pinnedPlayers.remove(normalizedName)) {
            pinnedPlayers = ArrayList(pinnedPlayers)
            pinnedPlayersList.set(ArrayList(pinnedPlayers))
            ChatUtils.info("§e已移除置顶玩家：$normalizedName")
        }
    }

    /**
     * 检查玩家是否被置顶
     */
    fun isPlayerPinned(name: String): Boolean {
        return pinnedPlayers.contains(name.trim())
    }

    /**
     * 清空所有置顶玩家
     */
    fun clearPinnedPlayers() {
        pinnedPlayers.clear()
        pinnedPlayersList.set(ArrayList())
        ChatUtils.info("§c已清空所有置顶玩家")
    }

    /**
     * 获取当前 Tab 中的玩家列表
     */
    fun getTabPlayers(): List<PlayerListEntry> {
        return mc.player?.networkHandler?.playerList?.toList() ?: emptyList()
    }

    /**
     * 获取当前在线的置顶玩家
     */
    fun getOnlinePinnedPlayers(): List<PlayerListEntry> {
        val tabPlayers = getTabPlayers()
        return tabPlayers.filter { entry ->
            entry.profile.name?.let { isPlayerPinned(it) } ?: false
        }
    }

    override fun getInfoString(): String? {
        val count = pinnedPlayers.size
        return if (count > 0) "§f[$count 置顶]" else null
    }
}
