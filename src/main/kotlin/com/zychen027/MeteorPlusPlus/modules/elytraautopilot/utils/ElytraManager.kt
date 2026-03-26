package com.zychen027.meteorplusplus.modules.elytraautopilot.utils

import com.zychen027.meteorplusplus.modules.elytraautopilot.ElytraAutoPilot
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.registry.RegistryKeys
import net.minecraft.screen.slot.SlotActionType

/**
 * 鞘翅管理器 - 来自 ElytraAutoPilot
 * 处理鞘翅装备、交换、耐久检查和烟花补充
 */
object ElytraManager {
    private const val CHESTPLATE_INDEX = 6
    private var lastUID = -1
    var autoSwapIsActive = false

    /**
     * 获取鞘翅耐久度
     */
    fun getElytraDurability(player: ClientPlayerEntity): Int {
        val elytra = getChestplateSlot(player)
        return elytra.maxDamage - elytra.damage
    }

    /**
     * 装备鞘翅
     */
    fun equipElytra(player: ClientPlayerEntity): Boolean {
        val elytraIndex = getElytraIndex(player)
        if (elytraIndex != -100) {
            val stack = player.getEquippedStack(EquipmentSlot.CHEST)
            lastUID = getItemUID(stack)
            swapChestplateSlot(elytraIndex, player)
            autoSwapIsActive = true
            // 发送数据包告诉服务器玩家正在飞行
            player.networkHandler.sendPacket(
                ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING)
            )
            return true
        }
        return false
    }

    /**
     * 获取胸甲槽位的物品
     */
    fun getChestplateSlot(player: ClientPlayerEntity): ItemStack {
        return player.getEquippedStack(EquipmentSlot.CHEST)
    }

    /**
     * 装备胸甲（恢复）
     */
    fun equipChestplate(player: ClientPlayerEntity): Boolean {
        val chestplateIndex = getLastChestplateIndex(player)
        if (chestplateIndex != -100) {
            swapChestplateSlot(chestplateIndex, player)
            lastUID = -100
            autoSwapIsActive = false
            return true
        }
        return false
    }

    /**
     * 交换胸甲槽位
     */
    private fun swapChestplateSlot(slot: Int, player: ClientPlayerEntity) {
        val interactionManager = MinecraftClient.getInstance().interactionManager
        interactionManager?.clickSlot(0, slot, 0, SlotActionType.PICKUP, player)
        interactionManager?.clickSlot(0, CHESTPLATE_INDEX, 0, SlotActionType.PICKUP, player)
        interactionManager?.clickSlot(0, slot, 0, SlotActionType.PICKUP, player)
        player.networkHandler.sendPacket(
            ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING)
        )
    }

    /**
     * 获取物品 UID
     */
    private fun getItemUID(stack: ItemStack): Int {
        if (stack.isEmpty) return -100
        return stack.name.string.hashCode() + stack.enchantments.hashCode() + stack.damage
    }

    /**
     * 获取最后一个胸甲位置
     */
    private fun getLastChestplateIndex(player: ClientPlayerEntity): Int {
        val inv = player.inventory
        if (inv == null) return -100

        for (slot in slotArray()) {
            val stack = inv.getStack(slot)
            if (getItemUID(stack) == lastUID) {
                return dataSlotToNetworkSlot(slot)
            }
        }
        return -100
    }

    /**
     * 获取鞘翅在背包中的位置
     */
    fun getElytraIndex(player: PlayerEntity): Int {
        val inv = player.inventory ?: return -100
        val world = player.world
        var bestSlot = -100
        var bestItemStack: ItemStack? = null
        var bestPriority = Int.MAX_VALUE

        val module = ElytraAutoPilot.INSTANCE ?: return -100

        for (slot in slotArray()) {
            val stack = inv.getStack(slot)
            if (!stack.isOf(Items.ELYTRA) || 
                stack.damage >= (stack.maxDamage - module.elytraReplaceDurability.get())) {
                continue
            }

            val hasMending = elytraHasMending(stack, world)
            val unbreakingLevel = elytraGetUnbreakingLevel(stack, world)

            val priority = when {
                hasMending && unbreakingLevel > 0 -> 1
                hasMending -> 2
                unbreakingLevel > 0 -> 3
                else -> 4
            }

            if (priority < bestPriority || 
                (priority == bestPriority && (bestItemStack == null || stack.damage > bestItemStack.damage))) {
                bestSlot = slot
                bestItemStack = stack
                bestPriority = priority
            }
        }

        return dataSlotToNetworkSlot(bestSlot)
    }

    /**
     * 检查鞘翅是否有经验修补
     */
    private fun elytraHasMending(elytra: ItemStack, world: net.minecraft.world.World): Boolean {
        val registry = world.registryManager.getOptional(RegistryKeys.ENCHANTMENT)
        if (registry.isEmpty) return false
        val enchantmentRegistry = registry.get()
        val mendingEntry = enchantmentRegistry.getEntry(Enchantments.MENDING.value)
        if (mendingEntry.isEmpty) return false
        val result = EnchantmentHelper.getLevel(mendingEntry.get(), elytra)
        return result > 0
    }

    /**
     * 获取鞘翅的耐久附魔等级
     */
    private fun elytraGetUnbreakingLevel(elytra: ItemStack, world: net.minecraft.world.World): Int {
        val registry = world.registryManager.getOptional(RegistryKeys.ENCHANTMENT)
        if (registry.isEmpty) return 0
        val enchantmentRegistry = registry.get()
        val unbreakingEntry = enchantmentRegistry.getEntry(Enchantments.UNBREAKING.value)
        if (unbreakingEntry.isEmpty) return 0
        return EnchantmentHelper.getLevel(unbreakingEntry.get(), elytra)
    }

    /**
     * 数据槽位转换为网络槽位
     */
    fun dataSlotToNetworkSlot(index: Int): Int {
        var idx = index
        when (idx) {
            100 -> idx = 8
            101 -> idx = 7
            102 -> idx = 6
            103 -> idx = 5
            -106, 40 -> idx = 45
            in -100 until 9 -> if (idx != -100) idx += 36
            in 80..83 -> idx -= 79
        }
        return idx
    }

    /**
     * 获取槽位数组（优先顺序）
     */
    fun slotArray(): IntArray {
        val range = IntArray(37)
        for (i in 0..8) range[i] = 8 - i
        for (i in 9 until 36) range[i] = 35 - (i - 9)
        range[36] = 40
        return range
    }

    /**
     * 尝试补充烟花
     */
    fun tryRestockFirework(player: PlayerEntity): Boolean {
        val module = ElytraAutoPilot.INSTANCE ?: return false

        if (!module.fireworkHotswap.get()) return false

        // 查找背包中的烟花（不包括快捷栏）
        var newFirework: ItemStack? = null
        var fireworkSlot = -1
        for (i in 9 until 36) {
            val stack = player.inventory.getStack(i)
            if (stack.item == Items.FIREWORK_ROCKET) {
                newFirework = stack
                fireworkSlot = i
                break
            }
        }

        if (newFirework != null && fireworkSlot >= 0) {
            // 优先补充到主手
            val selectedSlot = player.inventory.selectedSlot
            val interactionManager = MinecraftClient.getInstance().interactionManager
            
            // 使用 SWAP 操作将烟花交换到快捷栏
            interactionManager?.clickSlot(
                player.playerScreenHandler.syncId,
                fireworkSlot,
                selectedSlot,
                SlotActionType.SWAP,
                player
            )
            return true
        }
        return false
    }

    /**
     * 尝试补充鞘翅
     */
    fun tryRestockElytra(player: ClientPlayerEntity): Boolean {
        val module = ElytraAutoPilot.INSTANCE ?: return false
        
        if (!module.elytraHotswap.get()) return false
        
        return equipElytra(player)
    }

    /**
     * 检查是否可以补充鞘翅
     */
    fun canRestockElytra(player: ClientPlayerEntity): Boolean {
        return getElytraIndex(player) != -100
    }
}
