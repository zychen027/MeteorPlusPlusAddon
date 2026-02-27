package com.zychen027.MeteorPlusPlus.modules

import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.settings.SettingGroup
import meteordevelopment.meteorclient.systems.modules.Categories
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.component.DataComponentTypes
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.util.Hand

class PacketEat : Module(
    Categories.Player,
    "PacketEat",
    "Allows you to eat without interrupting other actions."
) {
    private val sgGeneral = settings.getDefaultGroup()

    private val deSync = sgGeneral.add(BoolSetting.Builder()
        .name("de-sync")
        .description("Sends interaction packets continuously to desync eating animation.")
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
                // 使用正确的方式发送sequenced packet
                mc.player!!.networkHandler.sendPacket(
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
