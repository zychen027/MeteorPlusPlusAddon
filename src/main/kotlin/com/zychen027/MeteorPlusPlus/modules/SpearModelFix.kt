package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.systems.modules.Module
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtInt
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries

class SpearModelFix : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "SpearModelFix",
    "修复ViaFabricPlus降级协议时矛物品被错误翻译为剑的问题。"
) {
    companion object {
        @JvmField
        var isEnabled = false

        private val SWORD_TO_SPEAR = mapOf(
            Items.WOODEN_SWORD to Items.WOODEN_SPEAR,
            Items.STONE_SWORD to Items.STONE_SPEAR,
            Items.COPPER_SWORD to Items.COPPER_SPEAR,
            Items.IRON_SWORD to Items.IRON_SPEAR,
            Items.GOLDEN_SWORD to Items.GOLDEN_SPEAR,
            Items.DIAMOND_SWORD to Items.DIAMOND_SPEAR,
            Items.NETHERITE_SWORD to Items.NETHERITE_SPEAR
        )

        private val SWORD_IDS: Set<String> = SWORD_TO_SPEAR.keys.map { Registries.ITEM.getId(it).toString() }.toSet()

        @JvmStatic
        fun getSpearItemForSword(swordItem: Item): Item? {
            return SWORD_TO_SPEAR[swordItem]
        }

        @JvmStatic
        fun isSpear(stack: ItemStack): Boolean {
            if (stack.isEmpty) return false
            if (stack.contains(DataComponentTypes.KINETIC_WEAPON)) return true
            val viaId = getViaBackwardsId(stack)
            if (viaId != null && viaId >= 1296) return true
            val name = stack.customName?.string ?: return false
            return name.contains("Spear", ignoreCase = true)
        }

        @JvmStatic
        fun isSwordThatShouldBeSpear(stack: ItemStack): Boolean {
            if (stack.isEmpty) return false
            val id = Registries.ITEM.getId(stack.item).toString()
            return id in SWORD_IDS && isSpear(stack)
        }

        @JvmStatic
        fun getViaBackwardsId(stack: ItemStack): Int? {
            val component = stack.get(DataComponentTypes.CUSTOM_DATA) ?: return null
            val nbt = component.copyNbt()
            val backup = nbt.get("VB|Protocol1_21_11To1_21_9") as? NbtCompound ?: return null
            val idTag = backup.get("id") as? NbtInt ?: return null
            return idTag.intValue()
        }

        @JvmStatic
        fun getKineticWeaponComponent(stack: ItemStack): Any? {
            val spearItem = getSpearItemForSword(stack.item) ?: return null
            return spearItem.components.get(DataComponentTypes.KINETIC_WEAPON)
        }

        @JvmStatic
        fun getSwingAnimationComponent(stack: ItemStack): Any? {
            val spearItem = getSpearItemForSword(stack.item) ?: return null
            return spearItem.components.get(DataComponentTypes.SWING_ANIMATION)
        }

        @JvmStatic
        fun getAttackRangeComponent(stack: ItemStack): Any? {
            val spearItem = getSpearItemForSword(stack.item) ?: return null
            return spearItem.components.get(DataComponentTypes.ATTACK_RANGE)
        }

        @JvmStatic
        fun getPiercingWeaponComponent(stack: ItemStack): Any? {
            val spearItem = getSpearItemForSword(stack.item) ?: return null
            return spearItem.components.get(DataComponentTypes.PIERCING_WEAPON)
        }

        @JvmStatic
        fun getUseEffectsComponent(stack: ItemStack): Any? {
            val spearItem = getSpearItemForSword(stack.item) ?: return null
            return spearItem.components.get(DataComponentTypes.USE_EFFECTS)
        }
    }

    override fun onActivate() {
        isEnabled = true
    }

    override fun onDeactivate() {
        isEnabled = false
    }
}
