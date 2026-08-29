package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.component.DataComponentTypes
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket

class PacketEat : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "PacketEat",
    "允许你在不中断其他动作的情况下进食。"
) {
    private val sgGeneral = settings.getDefaultGroup()

    private val deSync = sgGeneral.add(BoolSetting.Builder()
        .name("去同步")
        .description("连续发送交互数据包以去同步进食动画。")
        .defaultValue(false)
        .build()
    )

    private val noRelease = sgGeneral.add(BoolSetting.Builder()
        .name("no-release")
        .description("Cancels the release item packet so the server thinks you are still eating.")
        .defaultValue(true)
        .build()
    )

    @EventHandler
    private fun onTick(event: TickEvent.Post) {
        val player = mc.player ?: return

        if (deSync.get() && player.isUsingItem) {
            val activeStack = player.activeItem

            if (activeStack.get(DataComponentTypes.FOOD) != null) {
                val hand = player.activeHand
                // Bug 11 修复：使用已解包的 player，避免再次使用 mc.player!!
                player.networkHandler.sendPacket(
                    PlayerInteractItemC2SPacket(hand, 0, player.yaw, player.pitch)
                )
            }
        }
    }

    @EventHandler
    private fun onPacketSend(event: PacketEvent.Send) {
        val player = mc.player ?: return

        if (noRelease.get() && event.packet is PlayerActionC2SPacket) {
            val packet = event.packet as PlayerActionC2SPacket

            if (packet.action == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
                val activeStack = player.activeItem

                if (activeStack.get(DataComponentTypes.FOOD) != null) {
                    event.cancel()
                }
            }
        }
    }
}
