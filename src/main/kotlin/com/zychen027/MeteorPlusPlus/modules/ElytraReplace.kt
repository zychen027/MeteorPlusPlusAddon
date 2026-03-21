package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.IntSetting
import meteordevelopment.meteorclient.settings.Setting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.player.ChatUtils
import meteordevelopment.orbit.EventHandler
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.Items
import net.minecraft.screen.slot.SlotActionType

/**
 * 自动替换低耐久度鞘翅的模块
 * 优先从快捷栏替换，如果没有则从背包替换
 */
class ElytraReplace : Module(
	MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "鞘翅替换",
    "自动替换耐久度过低的鞘翅"
) {
    private val sg = settings.defaultGroup

    private val durability: Setting<Int> = sg.add(IntSetting.Builder()
        .name("min-durability")
        .description("耐久度百分比阈值,低于此值时替换鞘翅")
        .defaultValue(5)
        .min(0)
        .max(100)
        .build()
    )

    @EventHandler
    private fun onTick(event: TickEvent.Post) {
        if (mc.player == null) return

        val chestStack = mc.player!!.getEquippedStack(EquipmentSlot.CHEST)

        // 检查是否穿着鞘翅且耐久度过低
        if (chestStack.isOf(Items.ELYTRA) && getDurabilityPercent(chestStack) <= durability.get()) {
            // 1. 先尝试在快捷栏中寻找
            val hotbarSlot = findBetterElytraInHotbar()

            if (hotbarSlot != -1) {
                swapElytra(hotbarSlot)
                ChatUtils.info("§a从快捷栏替换了鞘翅!")
                return
            }

            // 2. 如果快捷栏没有，尝试在背包中寻找
            val inventorySlot = findBetterElytraInInventory()

            if (inventorySlot != -1) {
                swapElytra(inventorySlot)
                ChatUtils.info("§a从背包替换了鞘翅!")
                return
            }
        }
    }

    /**
     * 获取物品的耐久度百分比
     */
    private fun getDurabilityPercent(stack: net.minecraft.item.ItemStack): Float {
        val maxDamage = stack.maxDamage.toFloat()
        val currentDamage = stack.damage.toFloat()
        return 100f - (currentDamage / maxDamage * 100f)
    }

    /**
     * 在快捷栏中查找耐久度更好的鞘翅
     * @return 屏幕槽位索引,如果没找到返回-1
     */
    private fun findBetterElytraInHotbar(): Int {
        for (i in 36..44) {
            val slot = mc.player!!.currentScreenHandler.getSlot(i)
            if (slot.stack.isOf(Items.ELYTRA) && getDurabilityPercent(slot.stack) > durability.get()) {
                return i
            }
        }
        return -1
    }

    /**
     * 在背包中查找耐久度更好的鞘翅
     * @return 屏幕槽位索引,如果没找到返回-1
     */
    private fun findBetterElytraInInventory(): Int {
        for (i in 9..35) {
            val slot = mc.player!!.currentScreenHandler.getSlot(i)
            if (slot.stack.isOf(Items.ELYTRA) && getDurabilityPercent(slot.stack) > durability.get()) {
                return i
            }
        }
        return -1
    }

    /**
     * 执行鞘翅替换操作
     * @param screenSlot 新鞘翅所在的屏幕槽位索引
     */
    private fun swapElytra(screenSlot: Int) {
        val handler = mc.player!!.currentScreenHandler
        val syncId = handler.syncId

        // 1. 拾取新鞘翅
        mc.interactionManager!!.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, mc.player)
        // 2. 与胸甲槽交换
        mc.interactionManager!!.clickSlot(syncId, 6, 0, SlotActionType.PICKUP, mc.player)
        // 3. 放回旧鞘翅
        mc.interactionManager!!.clickSlot(syncId, screenSlot, 0, SlotActionType.PICKUP, mc.player)
    }
}
