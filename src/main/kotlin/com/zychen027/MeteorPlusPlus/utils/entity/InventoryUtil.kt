package com.zychen027.meteorplusplus.utils.entity

import com.zychen027.meteorplusplus.utils.world.BlockUtil
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntMaps
import meteordevelopment.meteorclient.MeteorClient
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.SlabBlock
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.screen.slot.SlotActionType

/**
 * 背包物品管理工具类 - 来自 LeavesHack
 */
object InventoryUtil {
    private var lastSlot: Int = -1
    private var lastSelect: Int = -1

    private val mc get() = MeteorClient.mc

    /**
     * 获取玩家装备中指定附魔的最高等级
     */
    fun getEquipmentLevel(player: PlayerEntity, enchantmentKey: RegistryKey<Enchantment>): Int {
        var maxLevel = 0
        val armorItems = listOf(
            player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET),
            player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS),
            player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST),
            player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD)
        )
        for (stack in armorItems) {
            if (stack.isEmpty) continue
            val level = getEnchantmentLevel(stack, enchantmentKey)
            if (level > maxLevel) maxLevel = level
        }
        return maxLevel
    }

    /**
     * 获取物品指定附魔的等级
     */
    fun getEnchantmentLevel(itemStack: ItemStack, enchantment: RegistryKey<Enchantment>): Int {
        if (itemStack.isEmpty) return 0
        val itemEnchantments: Object2IntMap<RegistryEntry<Enchantment>> = Object2IntArrayMap()
        getEnchantments(itemStack, itemEnchantments)
        return getEnchantmentLevel(itemEnchantments, enchantment)
    }

    /**
     * 从附魔映射中获取指定附魔的等级
     */
    fun getEnchantmentLevel(itemEnchantments: Object2IntMap<RegistryEntry<Enchantment>>, enchantment: RegistryKey<Enchantment>): Int {
        for (entry in Object2IntMaps.fastIterable(itemEnchantments)) {
            if (entry.key.matchesKey(enchantment)) return entry.intValue
        }
        return 0
    }

    /**
     * 获取物品的附魔信息
     */
    fun getEnchantments(itemStack: ItemStack, enchantments: Object2IntMap<RegistryEntry<Enchantment>>) {
        enchantments.clear()
        if (!itemStack.isEmpty) {
            val itemEnchantments = if (itemStack.item == Items.ENCHANTED_BOOK) {
                itemStack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).enchantmentEntries
            } else {
                itemStack.enchantments.enchantmentEntries
            }
            for (entry in itemEnchantments) {
                enchantments.put(entry.key, entry.intValue)
            }
        }
    }

    /**
     * 背包物品交换 (用于 InventorySwap 模式)
     */
    fun inventorySwap(slot: Int, selectedSlot: Int) {
        if (slot == lastSlot) {
            switchToSlot(lastSelect)
            lastSlot = -1
            lastSelect = -1
            return
        }
        if (slot - 36 == selectedSlot) return
        if (slot - 36 >= 0) {
            lastSlot = slot
            lastSelect = selectedSlot
            switchToSlot(slot - 36)
            return
        }
        mc.interactionManager?.clickSlot(
            mc.player!!.currentScreenHandler.syncId,
            slot,
            selectedSlot,
            SlotActionType.SWAP,
            mc.player!!
        )
    }

    /**
     * 在背包中查找指定物品的槽位
     */
    fun findItemInventorySlot(item: Item): Int {
        for (i in 0 until 45) {
            val stack = mc.player!!.inventory.getStack(i)
            if (stack.item == item) return if (i < 9) i + 36 else i
        }
        return -1
    }

    /**
     * 在快捷栏中查找方块物品
     */
    fun findBlock(): Int {
        for (i in 0..8) {
            val stack = getStackInSlot(i)
            if (stack.item is BlockItem && 
                !BlockUtil.shiftBlocks.contains(Block.getBlockFromItem(stack.item)) && 
                ((stack.item as BlockItem).block != Blocks.COBWEB)) {
                return i
            }
        }
        return -1
    }

    /**
     * 在快捷栏中查找半砖方块
     */
    fun findSlabBlock(): Int {
        for (i in 0..8) {
            val stack = getStackInSlot(i)
            if (stack.item is BlockItem) {
                val block = (stack.item as BlockItem).block
                if (block is SlabBlock) return i
            }
        }
        return -1
    }

    /**
     * 获取指定槽位的物品堆
     */
    fun getStackInSlot(i: Int): ItemStack {
        return mc.player!!.inventory.getStack(i)
    }

    /**
     * 切换到指定槽位
     */
    fun switchToSlot(slot: Int) {
        mc.player!!.inventory.selectedSlot = slot
        mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(slot))
    }

    /**
     * 在快捷栏中查找指定物品
     */
    fun findItem(input: Item): Int {
        for (i in 0..8) {
            val item = getStackInSlot(i).item
            if (Item.getRawId(item) != Item.getRawId(input)) continue
            return i
        }
        return -1
    }

    /**
     * 在快捷栏中查找指定类型的物品
     */
    fun findClass(clazz: Class<*>): Int {
        for (i in 0..8) {
            val stack = getStackInSlot(i)
            if (stack.isEmpty) continue
            if (clazz.isInstance(stack.item)) return i
            if (stack.item !is BlockItem || !clazz.isInstance((stack.item as BlockItem).block)) continue
            return i
        }
        return -1
    }

    /**
     * 在快捷栏中查找指定方块
     */
    fun findBlock(block: Block): Int {
        for (i in 0..8) {
            val stack = getStackInSlot(i)
            if (stack.item is BlockItem) {
                if ((stack.item as BlockItem).block == block) return i
            }
        }
        return -1
    }

    /**
     * 在背包中查找指定方块 (包含背包所有槽位)
     */
    fun findBlockInventory(block: Block): Int {
        for (i in 0 until 45) {
            val stack = getStackInSlot(i)
            if (stack.item is BlockItem) {
                if ((stack.item as BlockItem).block == block) return if (i < 9) i + 36 else i
            }
        }
        return -1
    }
}
