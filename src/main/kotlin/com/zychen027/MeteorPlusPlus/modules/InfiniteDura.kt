package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.screen.slot.SlotActionType

/**
 * InfiniteDura (鞘翅无限耐久) - 移植自 Alien-V4 ElytraFly
 * 
 * 绕过原理：
 * 通过快速点击胸甲槽位（Slot 6）刷新装备状态，欺骗客户端/服务器耐久度同步检测
 * 配合 START_FALL_FLYING 数据包维持滑翔状态，无视耐久损耗
 */
class InfiniteDura : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "InfiniteDura",
    "鞘翅无限耐久（通过刷新槽位状态绕过耐久检测）"
) {
    private val sgGeneral = settings.getDefaultGroup()

    private val setFlag = sgGeneral.add(BoolSetting.Builder()
        .name("SetFlag")
        .description("本地调用 startGliding() 以同步客户端状态")
        .defaultValue(false)
        .build())

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        if (mc.player == null || mc.world == null) return

        val chestplate = mc.player!!.getEquippedStack(EquipmentSlot.CHEST)
        val hasElytra = chestplate.item == Items.ELYTRA

        // 核心条件：装备鞘翅且不在地面上
        if (hasElytra && !mc.player!!.isOnGround) {
            // 核心绕过逻辑：快速点击胸甲槽位刷新状态
            val syncId = mc.player!!.currentScreenHandler.syncId
            mc.interactionManager?.clickSlot(syncId, 6, 0, SlotActionType.PICKUP, mc.player!!)
            mc.interactionManager?.clickSlot(syncId, 6, 0, SlotActionType.PICKUP, mc.player!!)

            // 发送开始滑翔数据包（维持服务器端滑翔状态）
            mc.networkHandler?.sendPacket(
                ClientCommandC2SPacket(mc.player!!, ClientCommandC2SPacket.Mode.START_FALL_FLYING)
            )

            // 可选：本地调用 startGliding() 同步客户端视角与状态
            if (setFlag.get()) {
                mc.player!!.startGliding()
            }
        }
    }
}
