package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.Blocks
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket

class NoFall : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "A NoFall",
    "完整移植所有8种模式，多种Grim兼容的防摔逻辑，适配1.21。"
) {
    private val sgGeneral = settings.getDefaultGroup()

    private val noFallToggle = sgGeneral.add(BoolSetting.Builder()
        .name("toggle")
        .description("主开关")
        .defaultValue(true)
        .build()
    )

    private val bypassMode = sgGeneral.add(EnumSetting.Builder<NoFallMode>()
        .name("bypass-mode")
        .description("NoFall 模式 (包含全部原版8种模式)")
        .defaultValue(NoFallMode.LAZY_GRIM_PLUS)
        .build()
    )

    private val safeDistanceModify = sgGeneral.add(IntSetting.Builder()
        .name("safe-distance-modify")
        .description("额外安全摔落距离")
        .defaultValue(0)
        .range(-10, 20)
        .build()
    )

    private val equipmentIdBypass = sgGeneral.add(StringSetting.Builder()
        .name("equipment-id-bypass-nofall")
        .description("装备ID正则（匹配则不应用NoFall，如史莱姆靴）")
        .defaultValue("^(SLIME.*_BOOTS)$")
        .build()
    )

    private val disableWhenAllowFlying = sgGeneral.add(BoolSetting.Builder()
        .name("disable-when-allow-flying")
        .description("创造飞行时禁用NoFall")
        .defaultValue(true)
        .build()
    )

    private var lastOnGroundHeight = Double.NEGATIVE_INFINITY
    private var lastHeight = 0.0
    private var lastServerY = 0.0
    private var lastServerOnGround = false
    private var safeDistance = 0.0
    private var entityStage = EntityStage.ABSENT

    private enum class EntityStage {
        INITIALIZING, ALIVE, ABSENT, INVULNERABLE
    }

    private enum class NoFallMode {
        NO_BYPASS,
        LAZY_MODE,
        BYPASS_GRIM,
        LAZY_BYPASS_GRIM,
        LAZY_GRIM_PLUS,
        LAZY_GRIM_PLUS_2,
        TEST,
        TEST2
    }

    private object ItemStackUtils {
        fun matchesEquipment(stack: ItemStack, regex: String): Boolean {
            if (stack.isEmpty) return false
            val id = net.minecraft.registry.Registries.ITEM.getId(stack.item).path
            return id.matches(Regex(regex))
        }
    }

    @EventHandler
    private fun onReceivePacket(event: PacketEvent.Receive) {
        if (mc.player == null || !noFallToggle.get()) return
        if (event.packet !is EntityVelocityUpdateS2CPacket) return
        val packet = event.packet as EntityVelocityUpdateS2CPacket

        if (packet.entityId != mc.player!!.id) return

        val currentMode = bypassMode.get()
        if (currentMode == NoFallMode.LAZY_GRIM_PLUS || currentMode == NoFallMode.TEST2) {
            // 1.21 Yarn 映射：packet.velocity 返回 Vec3d，不需要除 8000
            val velY = packet.velocity.y
            if (velY < -0.1 && mc.player!!.fallDistance > safeDistance) {
                event.cancel()
            }
        }
    }

    @EventHandler
    private fun onSendPacket(event: PacketEvent.Send) {
        if (mc.player == null || !noFallToggle.get()) return
        val packet = event.packet
        if (packet !is PlayerMoveC2SPacket) return

        val currentY = packet.getY(0.0)
        val currentOnGround = packet.isOnGround

        if (currentY != 0.0) {
            lastServerY = currentY
        }
        lastServerOnGround = currentOnGround

        if (entityStage != EntityStage.ALIVE) return

        val player = mc.player!!
        val fallDist = player.fallDistance
        val deltaY = currentY - lastOnGroundHeight
        val currentMode = bypassMode.get()

        var modifyY = false
        var newY = currentY
        var modifyOnGround = false
        var newOnGround = currentOnGround

        when (currentMode) {
            NoFallMode.NO_BYPASS -> {}
            NoFallMode.LAZY_MODE -> {
                if (fallDist > safeDistance && deltaY < -safeDistance) {
                    modifyY = true; newY = lastOnGroundHeight
                }
            }
            NoFallMode.BYPASS_GRIM -> {
                modifyY = true; newY = lastOnGroundHeight
                if (fallDist > safeDistance) {
                    modifyOnGround = true; newOnGround = true
                }
            }
            NoFallMode.LAZY_BYPASS_GRIM, NoFallMode.LAZY_GRIM_PLUS -> {
                if (fallDist > safeDistance) {
                    modifyY = true; newY = lastOnGroundHeight
                }
            }
            NoFallMode.LAZY_GRIM_PLUS_2 -> {
                if (fallDist > safeDistance) {
                    modifyY = true; newY = lastOnGroundHeight + 1e-6
                }
            }
            NoFallMode.TEST -> {
                modifyY = true; newY = lastServerY
            }
            NoFallMode.TEST2 -> {
                modifyY = true; newY = lastOnGroundHeight
            }
        }

        if (modifyY || modifyOnGround) {
            event.cancel()
            // 1.21 Packet 变为不可变 Record，取消原包后直接发送构造的新 Full 包
            mc.networkHandler?.sendPacket(
                PlayerMoveC2SPacket.Full(
                    player.x,
                    if (modifyY) newY else player.y,
                    player.z,
                    player.yaw,
                    player.pitch,
                    if (modifyOnGround) newOnGround else player.isOnGround,
                    player.horizontalCollision
                )
            )
        }
    }

    @EventHandler
    private fun onPreTick(event: TickEvent.Pre) {
        if (mc.player == null || !noFallToggle.get()) return
        val player = mc.player!!

        updateEntityStage(player)
        if (entityStage != EntityStage.ALIVE) return

        if (checkInvulnerableEquipment()) return

        updateState(player)

        val currentMode = bypassMode.get()
        when (currentMode) {
            NoFallMode.NO_BYPASS -> applyNoBypass(player)
            NoFallMode.LAZY_MODE -> applyLazyMode(player)
            NoFallMode.BYPASS_GRIM -> applyBypassGrim(player)
            NoFallMode.LAZY_BYPASS_GRIM -> applyLazyBypassGrim(player)
            NoFallMode.LAZY_GRIM_PLUS -> applyLazyGrimPlus(player)
            NoFallMode.LAZY_GRIM_PLUS_2 -> applyLazyGrimPlus2(player)
            NoFallMode.TEST -> applyTest(player)
            NoFallMode.TEST2 -> applyTest2(player)
        }
    }

    private fun updateEntityStage(player: net.minecraft.client.network.ClientPlayerEntity) {
        val abilities = player.abilities
        val invulnerable = abilities.invulnerable
        val creativeFlightAllowed = abilities.allowFlying

        if (invulnerable || (disableWhenAllowFlying.get() && creativeFlightAllowed)) {
            entityStage = EntityStage.INVULNERABLE
            return
        }

        if (!player.isAlive) {
            entityStage = EntityStage.ABSENT
            return
        }

        entityStage = EntityStage.ALIVE
    }

    private fun updateState(player: net.minecraft.client.network.ClientPlayerEntity) {
        lastHeight = player.y
        safeDistance = player.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE) + safeDistanceModify.get()

        if (player.isOnGround || player.isTouchingWater || player.getBlockStateAtPos().isOf(Blocks.BUBBLE_COLUMN) || player.isClimbing || player.getBlockStateAtPos().isOf(Blocks.COBWEB)) {
            lastOnGroundHeight = lastHeight
        } else if (lastHeight > lastOnGroundHeight) {
            lastOnGroundHeight = lastHeight
        }
    }

    private fun applyNoBypass(player: net.minecraft.client.network.ClientPlayerEntity) {
        if (player.fallDistance > safeDistance) {
            val deltaY = player.y - lastOnGroundHeight
            if (deltaY < -safeDistance) {
                player.setPosition(player.x, lastOnGroundHeight, player.z)
                // 1.21 fallDistance 是 Double
                player.fallDistance = 0.0
            }
        }
    }

    private fun applyLazyMode(player: net.minecraft.client.network.ClientPlayerEntity) {
        if (player.fallDistance > safeDistance) {
            player.fallDistance = safeDistance
        }
    }

    private fun applyBypassGrim(player: net.minecraft.client.network.ClientPlayerEntity) {
        if (player.fallDistance > safeDistance) {
            player.fallDistance = safeDistance
        }
    }

    private fun applyLazyBypassGrim(player: net.minecraft.client.network.ClientPlayerEntity) {
        if (player.fallDistance > safeDistance) {
            player.fallDistance = safeDistance
        }
    }

    private fun applyLazyGrimPlus(player: net.minecraft.client.network.ClientPlayerEntity) {
        if (player.fallDistance > safeDistance) {
            player.fallDistance = safeDistance
        }
    }

    private fun applyLazyGrimPlus2(player: net.minecraft.client.network.ClientPlayerEntity) {
        if (player.fallDistance > safeDistance) {
            player.fallDistance = safeDistance
        }
    }

    private fun applyTest(player: net.minecraft.client.network.ClientPlayerEntity) {
        if (player.fallDistance > safeDistance) {
            player.fallDistance = 0.0
        }
    }

    private fun applyTest2(player: net.minecraft.client.network.ClientPlayerEntity) {
        if (player.fallDistance > safeDistance) {
            player.fallDistance = 0.0
        }
    }

    private fun checkInvulnerableEquipment(): Boolean {
        val regex = equipmentIdBypass.get()
        for (slot in EquipmentSlot.values()) {
            val stack = mc.player?.getEquippedStack(slot) ?: continue
            if (ItemStackUtils.matchesEquipment(stack, regex)) {
                return true
            }
        }
        return false
    }
}
