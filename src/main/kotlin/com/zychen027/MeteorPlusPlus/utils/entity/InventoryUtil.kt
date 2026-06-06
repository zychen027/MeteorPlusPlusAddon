package com.zychen027.meteorplusplus.utils.entity

import net.minecraft.client.MinecraftClient
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.Packet
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry

object InventoryUtil {
    private val mc = MinecraftClient.getInstance()

    enum class MineSwitchMode {
        Normal, Silent, Delay, None
    }

    fun switchToSlot(slot: Int) {
        mc.player?.inventory?.selectedSlot = slot
    }

    fun sendPacket(packet: Packet<*>) {
        mc.networkHandler?.sendPacket(packet)
    }

    /**
     * 适配 MC 1.21.11 Yarn 映射：
     * - 使用 getOptional 代替 getWrapperOrThrow / get
     */
    fun getEnchantmentLevel(stack: ItemStack, key: RegistryKey<Enchantment>): Int {
        val registryManager = mc.world?.registryManager ?: return 0
        
        // 1. 获取附魔注册表包装器 (1.21.11 Yarn 映射为 getOptional)
        val registry = registryManager.getOptional(RegistryKeys.ENCHANTMENT).orElse(null) ?: return 0
        
        // 2. 将 RegistryKey 转换为 RegistryEntry (1.21.11 Yarn 映射为 getOptional)
        val entry: RegistryEntry<Enchantment> = registry.getOptional(key).orElse(null) ?: return 0
        
        // 3. 获取附魔等级
        return EnchantmentHelper.getLevel(entry, stack)
    }
}
