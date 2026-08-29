package com.zychen027.meteorplusplus.hud

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.systems.hud.HudElement
import meteordevelopment.meteorclient.systems.hud.HudElementInfo
import meteordevelopment.meteorclient.systems.hud.HudRenderer
import meteordevelopment.meteorclient.utils.render.color.Color
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.entity.effect.StatusEffect

class HowDidWeGetHereHud : HudElement(INFO) {

    companion object {
        @JvmField
        val INFO = HudElementInfo(
            MeteorPlusPlusAddon.HUD_GROUP, "how-did-we-get-here",
            "显示距离\"为什么会变成这样呢？\"成就的效果状态。"
        ) { HowDidWeGetHereHud() }

        private val REQUIRED_EFFECTS: Array<RegistryEntry<StatusEffect>> = arrayOf(
            StatusEffects.ABSORPTION,
            StatusEffects.BAD_OMEN,
            StatusEffects.BLINDNESS,
            StatusEffects.BREATH_OF_THE_NAUTILUS,
            StatusEffects.CONDUIT_POWER,
            StatusEffects.DARKNESS,
            StatusEffects.DOLPHINS_GRACE,
            StatusEffects.FIRE_RESISTANCE,
            StatusEffects.GLOWING,
            StatusEffects.HASTE,
            StatusEffects.HERO_OF_THE_VILLAGE,
            StatusEffects.HUNGER,
            StatusEffects.INFESTED,
            StatusEffects.INVISIBILITY,
            StatusEffects.JUMP_BOOST,
            StatusEffects.LEVITATION,
            StatusEffects.MINING_FATIGUE,
            StatusEffects.NAUSEA,
            StatusEffects.NIGHT_VISION,
            StatusEffects.OOZING,
            StatusEffects.POISON,
            StatusEffects.RAID_OMEN,
            StatusEffects.REGENERATION,
            StatusEffects.RESISTANCE,
            StatusEffects.SLOW_FALLING,
            StatusEffects.SLOWNESS,
            StatusEffects.SPEED,
            StatusEffects.STRENGTH,
            StatusEffects.TRIAL_OMEN,
            StatusEffects.WATER_BREATHING,
            StatusEffects.WEAKNESS,
            StatusEffects.WEAVING,
            StatusEffects.WIND_CHARGED,
            StatusEffects.WITHER,
        )

        private val REQUIRED_NAMES = arrayOf(
            "伤害吸收", "不祥之兆", "失明", "鹦鹉螺之息",
            "潮涌能量", "黑暗", "海豚的恩惠", "抗火",
            "发光", "急迫", "村庄英雄", "饥饿",
            "寄生", "隐身", "跳跃提升", "飘浮",
            "挖掘疲劳", "反胃", "夜视", "中毒",
            "生命恢复", "抗性提升", "缓降", "缓慢",
            "迅捷", "力量", "水下呼吸", "虚弱",
            "凋零", "渗浆", "盘丝", "蓄风",
            "袭击之兆", "试炼之兆"
        )

        private val GREEN = Color(0, 255, 0)
        private val RED = Color(255, 60, 60)
    }

    override fun render(renderer: HudRenderer) {
        val mc = MinecraftClient.getInstance()
        if (mc.player == null) {
            val noPlayer = "§7未找到玩家"
            setSize(renderer.textWidth(noPlayer, true), renderer.textHeight(true))
            renderer.text(noPlayer, x.toDouble(), y.toDouble(), Color.GRAY, true)
            return
        }

        var width = 0.0
        var height = 0.0

        val lines = ArrayList<Pair<String, Color>>()

        var got = 0
        for (i in REQUIRED_EFFECTS.indices) {
            val has = mc.player!!.hasStatusEffect(REQUIRED_EFFECTS[i])
            if (has) got++

            val prefix = if (has) "§a✓ " else "§c✗ "
            val color = if (has) GREEN else RED
            lines.add(Pair(prefix + REQUIRED_NAMES[i], color))

            val lineW = renderer.textWidth(prefix + REQUIRED_NAMES[i], true)
            if (lineW > width) width = lineW
            height += renderer.textHeight(true)
        }

        val header = if (got == REQUIRED_EFFECTS.size) {
            "§a§l已集齐全部 ${REQUIRED_EFFECTS.size} 个效果！"
        } else {
            "§e进度: $got/${REQUIRED_EFFECTS.size}"
        }

        val headerW = renderer.textWidth(header, true)
        if (headerW > width) width = headerW
        height += renderer.textHeight(true)

        setSize(width, height)

        renderer.text(header, x.toDouble(), y.toDouble(), Color.WHITE, true)
        var curY = y.toDouble() + renderer.textHeight(true)

        for ((text, color) in lines) {
            renderer.text(text, x.toDouble(), curY, color, true)
            curY += renderer.textHeight(true)
        }
    }
}
