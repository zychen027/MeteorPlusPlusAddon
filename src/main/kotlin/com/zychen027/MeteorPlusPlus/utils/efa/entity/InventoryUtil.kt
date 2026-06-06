package com.zychen027.meteorplusplus.utils.efa.entity

import com.zychen027.meteorplusplus.modules.FireworkElytraFly
import com.zychen027.meteorplusplus.utils.efa.world.BlockUtil
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntMaps
import meteordevelopment.meteorclient.MeteorClient
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.SlabBlock
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.screen.slot.SlotActionType

object InventoryUtil {
    private var lastSlot = -1
    private var serverSlot = -1
    private var lastSelect = -1

    init {
        MeteorClient.EVENT_BUS.subscribe(this)
    }

    @EventHandler
    fun onPacketSend(event: PacketEvent.Send) {
        if (event.packet is UpdateSelectedSlotC2SPacket) {
            val packet = event.packet as UpdateSelectedSlotC2SPacket
            if (FireworkElytraFly.INSTANCE?.noBadPackets?.get() == true && packet.selectedSlot == serverSlot) {
                event.cancel()
            }
            serverSlot = packet.selectedSlot
        }
    }

    @JvmStatic
    fun getEquipmentLevel(player: PlayerEntity, enchantmentKey: RegistryKey<Enchantment>): Int {
        var maxLevel = 0
        for (slot in listOf(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)) {
            val stack = player.getEquippedStack(slot)
            if (!stack.isEmpty()) {
                val level = getEnchantmentLevel(stack, enchantmentKey)
                if (level > maxLevel) {
                    maxLevel = level
                }
            }
        }
        return maxLevel
    }

    @JvmStatic
    fun getEnchantmentLevel(itemStack: ItemStack, enchantment: RegistryKey<Enchantment>): Int {
        if (itemStack.isEmpty()) return 0
        val itemEnchantments: Object2IntMap<RegistryEntry<Enchantment>> = Object2IntArrayMap()
        getEnchantments(itemStack, itemEnchantments)
        return getEnchantmentLevel(itemEnchantments, enchantment)
    }

    @JvmStatic
    fun getEnchantmentLevel(itemEnchantments: Object2IntMap<RegistryEntry<Enchantment>>, enchantment: RegistryKey<Enchantment>): Int {
        for (entry in Object2IntMaps.fastIterable(itemEnchantments)) {
            if (entry.key.matchesKey(enchantment)) return entry.intValue
        }
        return 0
    }

    @JvmStatic
    fun getEnchantments(itemStack: ItemStack, enchantments: Object2IntMap<RegistryEntry<Enchantment>>) {
        enchantments.clear()
        if (!itemStack.isEmpty()) {
            val itemEnchantments = if (itemStack.item == Items.ENCHANTED_BOOK)
                itemStack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).enchantmentEntries
            else
                itemStack.enchantments.enchantmentEntries

            for (entry in itemEnchantments) {
                enchantments.put(entry.key, entry.intValue)
            }
        }
    }

    @JvmStatic
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
        MeteorClient.mc.interactionManager!!.clickSlot(
            MeteorClient.mc.player!!.currentScreenHandler.syncId,
            slot,
            selectedSlot,
            SlotActionType.SWAP,
            MeteorClient.mc.player
        )
    }

    @JvmStatic
    fun findItemInventorySlot(item: Item): Int {
        for (i in 0..44) {
            val stack = MeteorClient.mc.player!!.inventory.getStack(i)
            if (stack.item == item) return if (i < 9) i + 36 else i
        }
        return -1
    }

    @JvmStatic
    fun findBlock(): Int {
        for (i in 0..8) {
            val stack = getStackInSlot(i)
            if (stack.item is BlockItem && !BlockUtil.shiftBlocks.contains(Block.getBlockFromItem(stack.item)) && (stack.item as BlockItem).block !== Blocks.COBWEB) return i
        }
        return -1
    }

    @JvmStatic
    fun findSlabBlock(): Int {
        for (i in 0..8) {
            val stack = getStackInSlot(i)
            if (stack.item is BlockItem) {
                val block = (stack.item as BlockItem).block
                if (block is SlabBlock) {
                    return i
                }
            }
        }
        return -1
    }

    @JvmStatic
    fun getStackInSlot(i: Int): ItemStack = MeteorClient.mc.player!!.inventory.getStack(i)

    @JvmStatic
    fun switchToSlot(slot: Int) {
        if (FireworkElytraFly.INSTANCE?.clientSwitch?.get() == true) MeteorClient.mc.player!!.inventory.selectedSlot = slot
        sendPacket(UpdateSelectedSlotC2SPacket(slot))
    }

    enum class MineSwitchMode {
        Delay, Silent, None
    }

    @JvmStatic
    fun findItem(input: Item): Int {
        for (i in 0..8) {
            val item = getStackInSlot(i).item
            if (Item.getRawId(item) != Item.getRawId(input)) continue
            return i
        }
        return -1
    }

    @JvmStatic
    fun findClass(clazz: Class<*>): Int {
        for (i in 0..8) {
            val stack = getStackInSlot(i)
            if (stack.isEmpty()) continue
            if (clazz.isInstance(stack.item)) {
                return i
            }
            if (stack.item !is BlockItem || !clazz.isInstance((stack.item as BlockItem).block)) continue
            return i
        }
        return -1
    }

    @JvmStatic
    fun findClassInventory(clazz: Class<*>): Int {
        for (i in 0..44) {
            val stack = MeteorClient.mc.player!!.inventory.getStack(i)
            if (stack.isEmpty()) continue
            if (clazz.isInstance(stack.item)) {
                return if (i < 9) i + 36 else i
            }
            if (stack.item !is BlockItem || !clazz.isInstance((stack.item as BlockItem).block)) continue
            return if (i < 9) i + 36 else i
        }
        return -1
    }

    @JvmStatic
    fun sendPacket(packet: net.minecraft.network.packet.Packet<*>) {
        MeteorClient.mc.networkHandler!!.sendPacket(packet)
    }

    @JvmStatic
    fun findBlock(block: Block): Int {
        for (i in 0..8) {
            val stack = getStackInSlot(i)
            if (stack.item is BlockItem) {
                if ((stack.item as BlockItem).block === block) {
                    return i
                }
            }
        }
        return -1
    }

    @JvmStatic
    fun findBlockInventory(block: Block): Int {
        for (i in 0..44) {
            val stack = MeteorClient.mc.player!!.inventory.getStack(i)
            if (stack.item is BlockItem) {
                if ((stack.item as BlockItem).block === block) {
                    return if (i < 9) i + 36 else i
                }
            }
        }
        return -1
    }
}
