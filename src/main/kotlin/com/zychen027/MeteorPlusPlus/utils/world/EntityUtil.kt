package com.zychen027.meteorplusplus.utils.entity

import net.minecraft.client.MinecraftClient
import net.minecraft.util.Hand

object EntityUtil {
    private val mc = MinecraftClient.getInstance()

    fun attackSwingHand() {
        mc.player?.swingHand(Hand.MAIN_HAND)
    }
}
