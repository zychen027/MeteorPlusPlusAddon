package com.zychen027.meteorplusplus

import com.zychen027.meteorplusplus.commands.BetterTabCommand
import com.zychen027.meteorplusplus.modules.*
import meteordevelopment.meteorclient.addons.MeteorAddon
import meteordevelopment.meteorclient.commands.Commands
import meteordevelopment.meteorclient.systems.hud.Hud
import meteordevelopment.meteorclient.systems.hud.HudGroup
import meteordevelopment.meteorclient.systems.modules.Category
import meteordevelopment.meteorclient.systems.modules.Modules
import net.minecraft.item.Items
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MeteorPlusPlusAddon : MeteorAddon() {
    companion object {
        val LOG: Logger = LoggerFactory.getLogger("Meteor++")
        val METEORPLUSPLUS_CATEGORY: Category = Category("Meteor++", Items.DRAGON_EGG.defaultStack)
        @JvmField
        val HUD_GROUP: HudGroup = HudGroup("Meteor++")
    }

    override fun onInitialize() {
        LOG.info("Initializing Meteor++ Addon")

        val modules = Modules.get()

        // ==================== 鞘翅模块 ====================)
        modules.add(ElytraReplace())
        modules.add(ElytraAndArmor())

        // ==================== 其他模块 ====================
        modules.add(PacketEat())
        modules.add(TNTTimer())
        modules.add(Help())
		modules.add(YLevelProtect())

        // ==================== 新增模块 ====================
        modules.add(BetterTab())
        modules.add(PacketKickFix())
        modules.add(SpearModelFix())

        // ==================== 注册命令 ====================
        Commands.add(BetterTabCommand())

        // ==================== 注册 HUD ====================
        Hud.get().register(com.zychen027.meteorplusplus.hud.HowDidWeGetHereHud.INFO)
    }

    override fun onRegisterCategories() {
        Modules.registerCategory(METEORPLUSPLUS_CATEGORY)
    }

    override fun getWebsite(): String {
        return "https://github.com/zychen027/MeteorPlusPlusAddon"
    }

    override fun getPackage(): String {
        return "com.zychen027.meteorplusplus"
    }
}
