package com.zychen027.meteorplusplus.modules

import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.render.color.Color
import meteordevelopment.meteorclient.utils.render.color.SettingColor
import meteordevelopment.orbit.EventHandler
import net.minecraft.entity.TntEntity
import net.minecraft.util.math.MathHelper
import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import kotlin.math.sin

class TNTTimer : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "TNT爆炸计时",
    "根据TNT爆炸时间显示一个ESP在激活的TNT上"
) {
    private val sgGeneral = settings.getDefaultGroup()

    private val esp = sgGeneral.add(BoolSetting.Builder()
        .name("esp")
        .description("Highlight TNT with glow effect.")
        .defaultValue(true)
        .build()
    )

    private val espColor = sgGeneral.add(ColorSetting.Builder()
        .name("esp-color")
        .description("Color of TNT highlight.")
        .defaultValue(SettingColor(255, 0, 0, 100))
        .visible { esp.get() }
        .build()
    )

    private val tntEntities = mutableListOf<TntEntity>()

    companion object {
        private const val DEFAULT_FUSE = 80
    }

    override fun onActivate() {
        tntEntities.clear()
    }

    override fun onDeactivate() {
        tntEntities.clear()
    }

    @EventHandler
    private fun onTick(event: TickEvent.Post) {
        tntEntities.clear()

        val world = mc.world ?: return
        for (entity in world.entities) {
            if (entity is TntEntity && entity.fuse > 0) {
                tntEntities.add(entity)
            }
        }
    }

    @EventHandler
    private fun onRender3D(event: Render3DEvent) {
        if (tntEntities.isEmpty()) return

        for (tnt in tntEntities) {
            if (tnt.fuse <= 0) continue

            // ESP rendering only
            if (esp.get()) {
                val color = getTntColor(tnt.fuse)
                event.renderer.box(
                    tnt.boundingBox,
                    color,
                    color,
                    ShapeMode.Both,
                    0
                )
            }
        }
    }

    private fun getTntColor(fuse: Int): Color {
        val red = MathHelper.floor(255.0 * (1.0 + 0.5 * sin(2400.0 / (12 + fuse)))).coerceIn(0, 255)
        return Color(red, 0, 0, espColor.get().a)
    }
}
