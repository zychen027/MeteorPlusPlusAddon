package com.zychen027.meteorplusplus

import com.zychen027.meteorplusplus.commands.BetterTabCommand
import com.zychen027.meteorplusplus.commands.ElytraAutoPilotCommand
import com.zychen027.meteorplusplus.modules.elytraautopilot.ElytraAutoPilot
import com.zychen027.meteorplusplus.modules.*
import meteordevelopment.meteorclient.addons.MeteorAddon
import meteordevelopment.meteorclient.commands.Commands
import meteordevelopment.meteorclient.systems.modules.Category
import meteordevelopment.meteorclient.systems.modules.Modules
import net.minecraft.item.Items
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MeteorPlusPlusAddon : MeteorAddon() {
    companion object {
        val LOG: Logger = LoggerFactory.getLogger("Meteor++")
        val METEORPLUSPLUS_CATEGORY: Category = Category("Meteor++", Items.AIR.defaultStack)
    }

    override fun onInitialize() {
        LOG.info("Initializing Meteor++ Addon")
        val modules = Modules.get()

        // ==================== 战斗模块 ====================
        modules.add(KillAura())

        // ==================== 鞘翅模块 ====================
        modules.add(ElytraFly())
        modules.add(ElytraReplace())
        modules.add(ElytraAutoPilot())
        modules.add(ElytraAndArmor()) // <--- 新增注册

        // ==================== 世界模块 ====================
        modules.add(PacketMineModule())
        modules.add(Printer())

        // ==================== 移动模块 ====================
        modules.add(GrimFly())

        // ==================== 其他模块 ====================
        modules.add(PacketEat())
        modules.add(TNTTimer())

        // ==================== 新增模块 ====================
        modules.add(BetterTab())

        // ==================== 注册命令 ====================
        Commands.add(BetterTabCommand())
        Commands.add(ElytraAutoPilotCommand())
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
