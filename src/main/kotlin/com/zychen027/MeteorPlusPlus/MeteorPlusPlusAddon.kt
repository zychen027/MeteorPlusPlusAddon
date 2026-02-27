package com.zychen027.MeteorPlusPlus

import com.zychen027.MeteorPlusPlus.modules.ElytraFly
import com.zychen027.MeteorPlusPlus.modules.KillAura
import com.zychen027.MeteorPlusPlus.modules.PacketEat
import com.zychen027.MeteorPlusPlus.modules.PacketMineModule
import com.zychen027.MeteorPlusPlus.modules.TNTTimer
import meteordevelopment.meteorclient.addons.MeteorAddon
import meteordevelopment.meteorclient.systems.modules.Category
import meteordevelopment.meteorclient.systems.modules.Modules
import net.minecraft.item.Items
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MeteorPlusPlusAddon : MeteorAddon() {
    
    companion object {
        val LOG: Logger = LoggerFactory.getLogger("Meteor++")
        val PACKETMINE_CATEGORY: Category = Category("PacketMine", Items.DIAMOND_PICKAXE.defaultStack)
    }

    override fun onInitialize() {
        LOG.info("Initializing Meteor++ Addon")

        val modules = Modules.get()
        modules.add(KillAura())
        modules.add(ElytraFly())
        modules.add(TNTTimer())
        modules.add(PacketMineModule())
        modules.add(PacketEat())
    }

    override fun onRegisterCategories() {
        Modules.registerCategory(PACKETMINE_CATEGORY)
    }

    override fun getWebsite(): String {
        return "https://github.com/zychen027/MeteorPlusPlus"
    }

    override fun getPackage(): String {
        return "com.zychen027.MeteorPlusPlus"
    }
}
