package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.utils.xalu.XaluFriends
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler

/**
 * Friends List (好友列表) - 移植自 XALU
 * 显示和管理好友列表
 */
class FriendsModule : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "FriendsList",
    "显示和管理好友列表"
) {
    private val sgGeneral = settings.getDefaultGroup()

    private val autoReload = sgGeneral.add(BoolSetting.Builder()
        .name("AutoReload")
        .description("启用模块时自动重新加载好友列表")
        .defaultValue(true)
        .build())

    override fun onActivate() {
        if (autoReload.get()) {
            XaluFriends.load()
        }
        displayFriendsList()
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        toggle()
    }

    private fun displayFriendsList() {
        info("§6===== 好友列表 =====")
        var count = 0
        for (friend in XaluFriends.getFriends()) {
            info("§f- §a${friend.name}")
            count++
        }
        if (count == 0) {
            info("§c没有找到好友。请编辑 friends.txt 文件添加好友。")
        } else {
            info("§6总计：§f$count §6个好友")
        }
        info("§6==================")
    }
}
