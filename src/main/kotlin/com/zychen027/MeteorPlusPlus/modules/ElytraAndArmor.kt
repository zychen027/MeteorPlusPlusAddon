package com.zychen027.meteorplusplus.modules

import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import meteordevelopment.meteorclient.events.meteor.KeyEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.DoubleSetting
import meteordevelopment.meteorclient.settings.Setting
import meteordevelopment.meteorclient.settings.SettingGroup
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.Utils
import meteordevelopment.meteorclient.utils.misc.input.KeyAction
import meteordevelopment.meteorclient.utils.player.InvUtils
import meteordevelopment.orbit.EventHandler
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.entry.RegistryEntry
import com.zychen027.meteorplusplus.MeteorPlusPlusAddon

class ElytraAndArmor : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY, 
    "鞘翅胸甲切换", 
    "自动在鞘翅和胸甲之间切换。落地自动穿甲，双击空格穿鞘翅。"
) {
    private val sgGeneral: SettingGroup = settings.getDefaultGroup()
    
    private val enchantments: Object2IntMap<RegistryEntry<Enchantment>> = Object2IntOpenHashMap()
    
    private val delay: Setting<Double> = sgGeneral.add(DoubleSetting.Builder()
        .name("空格按压延迟")
        .description("双击空格切换鞘翅的有效时间窗口（秒）。")
        .defaultValue(0.317)
        .min(0.0)
        .range(0.0, 1.0)
        .build()
    )

    private var lastPressTime: Long = 0L

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        val player = mc.player ?: return
        
        val chestStack = player.getEquippedStack(EquipmentSlot.CHEST)
        
        // 检查当前胸甲槽是否不是胸甲（使用 Items 比较）
        if (!isChestplate(chestStack)) {
            // 在地面且（槽位为空或为鞘翅）时，尝试装备最佳胸甲
            if (player.isOnGround && (chestStack.isEmpty || chestStack.isOf(Items.ELYTRA))) {
                val bestSlot = getBestArmorSlot()
                if (bestSlot != -1) {
                    InvUtils.move().from(bestSlot).toArmor(2)
                }
            }
        }
    }

    /**
     * 检查物品是否为胸甲
     */
    private fun isChestplate(stack: ItemStack): Boolean {
        return stack.isOf(Items.DIAMOND_CHESTPLATE) ||
               stack.isOf(Items.NETHERITE_CHESTPLATE) ||
               stack.isOf(Items.IRON_CHESTPLATE) ||
               stack.isOf(Items.GOLDEN_CHESTPLATE) ||
               stack.isOf(Items.CHAINMAIL_CHESTPLATE) ||
               stack.isOf(Items.LEATHER_CHESTPLATE)
    }

    /**
     * 获取物品栏中最好的胸甲槽位
     */
    private fun getBestArmorSlot(): Int {
        val player = mc.player ?: return -1
        var bestSlot = -1
        var bestScore = -1

        // 遍历背包槽位 (0-35)
        for (i in 0..35) {
            val stack = player.inventory.getStack(i)
            
            if (isChestplate(stack)) {
                val score = getScore(stack)
                if (score > bestScore) {
                    bestScore = score
                    bestSlot = i
                }
            }
        }
        return bestSlot
    }

    /**
     * 计算护甲评分
     */
    private fun getScore(stack: ItemStack): Int {
        if (!isChestplate(stack)) return -1
        
        var score = 0
        
        // 基础护甲值 (根据材质)
        score += when (stack.item) {
            Items.NETHERITE_CHESTPLATE -> 8
            Items.DIAMOND_CHESTPLATE -> 8
            Items.IRON_CHESTPLATE -> 6
            Items.CHAINMAIL_CHESTPLATE -> 5
            Items.GOLDEN_CHESTPLATE -> 5
            Items.LEATHER_CHESTPLATE -> 3
            else -> 0
        }
        
        // 韧性加成
        score += when (stack.item) {
            Items.NETHERITE_CHESTPLATE -> 3
            Items.DIAMOND_CHESTPLATE -> 2
            else -> 0
        }
        
        // 击退抗性
        if (stack.isOf(Items.NETHERITE_CHESTPLATE)) {
            score += 10 // 0.1 * 100
        }
        
        // 获取附魔并计算加分
        Utils.getEnchantments(stack, enchantments)
        
        score += Utils.getEnchantmentLevel(enchantments, Enchantments.PROTECTION)
        score += Utils.getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION)
        score += Utils.getEnchantmentLevel(enchantments, Enchantments.FIRE_PROTECTION)
        score += Utils.getEnchantmentLevel(enchantments, Enchantments.PROJECTILE_PROTECTION)
        score += Utils.getEnchantmentLevel(enchantments, Enchantments.UNBREAKING)
        score += 2 * Utils.getEnchantmentLevel(enchantments, Enchantments.MENDING)
        
        return score
    }

    @EventHandler
    private fun onKey(event: KeyEvent) {
        if (event.key == 32 && event.action == KeyAction.Press) {
            val now = System.currentTimeMillis()
            
            if (lastPressTime == 0L) {
                lastPressTime = now
                return
            }
            
            val timeDiff = (now - lastPressTime).toDouble()
            val threshold = delay.get() * 1000.0
            
            if (timeDiff > threshold) {
                lastPressTime = now
                return
            }
            
            val player = mc.player ?: return
            val chestStack = player.getEquippedStack(EquipmentSlot.CHEST)
            
            // 如果已经是鞘翅，重置时间但不切换
            if (chestStack.isOf(Items.ELYTRA)) {
                lastPressTime = now
                return
            }
            
            // 如果胸甲槽为空或者是普通护甲，切换为鞘翅
            if (chestStack.isEmpty || isChestplate(chestStack)) {
                mc.currentScreen?.close()
                
                val elytraSlot = InvUtils.find(Items.ELYTRA)
                if (elytraSlot.found()) {
                    InvUtils.move().from(elytraSlot.slot()).toArmor(2)
                }
            }
        }
    }
}
