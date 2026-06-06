package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.systems.modules.Module

class PacketKickFix : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "PacketKickFix",
    "修复解码器问题，防止因坏包/解码异常而被踢出服务器。"
) {
    // 使用伴生对象维护一个静态变量，供 Mixin 高效读取，避免在 Mixin 中调用 Meteor 的模块管理器导致潜在问题
    companion object {
        @JvmField
        var isFixEnabled = false
    }

    override fun onActivate() {
        isFixEnabled = true
    }

    override fun onDeactivate() {
        isFixEnabled = false
    }
}
