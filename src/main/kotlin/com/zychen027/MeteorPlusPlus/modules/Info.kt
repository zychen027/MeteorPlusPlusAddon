package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.player.ChatUtils
import meteordevelopment.orbit.EventHandler

class Help : Module(
	MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "A 帮助",
    "使用前看我：1.本插件不保证 100% 靠谱。2.使用前请先去单人测试。3.插件在测试，坠机后果自负（虽然大概率不会）。"
) {
    @EventHandler
    private fun onTick(event: TickEvent.Post) {
        ChatUtils.info("§a 开始享受 Meteor++！如果觉得好用请赞助。")
		ChatUtils.info("§a 插件免费，如果是付费的说明你被骗了~")
    }
}
