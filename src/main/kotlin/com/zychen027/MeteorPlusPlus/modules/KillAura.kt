package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.utils.rotation.Rotation
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.systems.friends.Friends as MeteorFriends
import meteordevelopment.meteorclient.utils.player.InvUtils
import meteordevelopment.meteorclient.utils.render.color.SettingColor
import meteordevelopment.orbit.EventHandler
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.Tameable
import net.minecraft.entity.mob.EndermanEntity
import net.minecraft.entity.mob.ZombifiedPiglinEntity
import net.minecraft.entity.passive.WolfEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult
import net.minecraft.world.RaycastContext
import net.minecraft.registry.tag.ItemTags

class KillAura : Module(MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY, "ZT-KillAura", "杀戮光环") {

    companion object {
        lateinit var INSTANCE: KillAura
    }

    enum class Weapon(val title: String) {
        Sword("剑"), Axe("斧子"), Mace("重锤"), Trident("三叉戟"), All("全部武器"), Any("任意武器");
        override fun toString(): String = title
    }

    enum class SwitchMode(val title: String) {
        Normal("普通"), Silent("静默"), None("无");
        override fun toString(): String = title
    }

    private val sgGeneral = settings.getDefaultGroup()
    private val sgRender = settings.createGroup("Render")

    private val targetRange = sgGeneral.add(IntSetting.Builder()
        .name("TargetRange").description("目标扫描距离").defaultValue(6).min(0).sliderMax(8).build())
    private val attackRange = sgGeneral.add(DoubleSetting.Builder()
        .name("Range").description("攻击距离").defaultValue(3.5).min(0.0).sliderMax(8.0).build())
    private val weapon = sgGeneral.add(EnumSetting.Builder<Weapon>()
        .name("Weapon").description("武器选择").defaultValue(Weapon.Sword).build())
    private val autoSwitch = sgGeneral.add(EnumSetting.Builder<SwitchMode>()
        .name("AutoSwitch").description("自动切换武器").defaultValue(SwitchMode.Silent).build())
    private val reset = sgGeneral.add(BoolSetting.Builder()
        .name("Reset").description("自动重置冷却").defaultValue(true).build())
    private val hurtTime = sgGeneral.add(IntSetting.Builder()
        .name("HurtTime").description("伤害时间").defaultValue(10).min(0).sliderMax(10).build())
    private val cooldown = sgGeneral.add(DoubleSetting.Builder()
        .name("Cooldown").description("攻击冷却").defaultValue(0.55).min(0.0).sliderMax(1.0).build())
    private val wallRange = sgGeneral.add(DoubleSetting.Builder()
        .name("WallRange").description("穿墙距离").defaultValue(3.5).min(0.1).sliderMax(7.0).build())
    private val usingPause = sgGeneral.add(BoolSetting.Builder()
        .name("UsingPause").description("使用物品时暂停").defaultValue(true).build())
    private val entities = sgGeneral.add(EntityTypeListSetting.Builder()
        .name("entities").description("攻击目标").onlyAttackable().defaultValue(EntityType.PLAYER).build())
    private val rotate = sgGeneral.add(BoolSetting.Builder()
        .name("Rotate").description("转头").defaultValue(true).build())
    
    // 移植 GlobalSetting 的 MoveFix 设置，常开
    val moveFix = sgGeneral.add(BoolSetting.Builder()
        .name("MoveFix").description("修复旋转时的移动以绕过GrimAC").defaultValue(true).build())

    private val ignoreNamed = sgGeneral.add(BoolSetting.Builder()
        .name("ignore-named").description("忽略带有命名的生物").defaultValue(true).build())
    private val ignorePassive = sgGeneral.add(BoolSetting.Builder()
        .name("ignore-passive").description("忽略中立生物").defaultValue(false).build())
    private val ignoreTamed = sgGeneral.add(BoolSetting.Builder()
        .name("ignore-tamed").description("忽略已驯服的生物").defaultValue(true).build())

    private val targetESP = sgRender.add(BoolSetting.Builder()
        .name("TargetESP").description("目标ESP渲染").defaultValue(true).build())
    private val espColor = sgRender.add(ColorSetting.Builder()
        .name("ESPColor").description("ESP渲染颜色").defaultValue(SettingColor(255, 255, 255, 255)).build())

    private val tick = Timer()
    var target: Entity? = null
        private set
    
    // 暴露给 Mixin 的旋转状态
    var isRotating = false
        private set
    var targetYaw = 0f
        private set

    init {
        INSTANCE = this
    }

    override fun onActivate() {
        tick.setMs(9999999)
        target = null
        isRotating = false
    }

    override fun onDeactivate() {
        target = null
        if (isRotating) Rotation.snapBack()
        isRotating = false
    }

    override fun getInfoString(): String? {
        return if (target == null) null else "§f[${target!!.name.string}]"
    }

    private class Timer {
        private var time = -1L
        init { reset() }
        fun reset(): Timer { time = System.nanoTime(); return this }
        fun passedMs(ms: Double): Boolean = passedMs(ms.toLong())
        fun passedMs(ms: Long): Boolean = passedNS(ms * 1_000_000L)
        private fun passedNS(ns: Long): Boolean = System.nanoTime() - time >= ns
        fun setMs(ms: Long) { time = System.nanoTime() - ms * 1_000_000L }
    }

    @EventHandler
    private fun onRender(event: Render3DEvent) {
        if (target != null && targetESP.get()) {
            // ESP 渲染逻辑
        }
    }

    @EventHandler
    private fun onPacket(event: PacketEvent.Send) {
        // 原有的冷却重置逻辑
        if (!reset.get()) return
        val packet = event.packet
        if (packet is PlayerInteractEntityC2SPacket) {
            try {
                val typeField = packet::class.java.getDeclaredField("type")
                typeField.isAccessible = true
                val typeObj = typeField.get(packet)
                val innerTypeField = typeObj::class.java.getDeclaredField("type")
                innerTypeField.isAccessible = true
                if (innerTypeField.get(typeObj).toString() == "ATTACK") tick.reset()
            } catch (_: Exception) {
                tick.reset()
            }
        }
        if (packet is HandSwingC2SPacket) tick.reset()
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        target = getTarget(targetRange.get().toDouble())
        if (target == null) {
            if (rotate.get() && isRotating) {
                Rotation.snapBack()
                isRotating = false
            }
            return
        }
        doAura()
    }

    private fun isValid(entity: Entity, range: Double): Boolean {
        if (!entity.isAlive) return false
        val player = mc.player ?: return false
        if (entity == player) return false
        if (entity is PlayerEntity && MeteorFriends.get().isFriend(entity)) return false
        return player.distanceTo(entity) <= range
    }

    private fun canSeeEntity(entity: Entity): Boolean {
        val player = mc.player ?: return false
        val eyePos = player.eyePos
        val attackVec = Rotation.getClosestPointToEye(eyePos, entity.boundingBox)
        val result = mc.world?.raycast(
            RaycastContext(eyePos, attackVec, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player)
        )
        return result == null || result.type == HitResult.Type.MISS
    }

    private fun getTarget(range: Double): Entity? {
        val player = mc.player ?: return null
        val world = mc.world ?: return null
        var closestTarget: Entity? = null
        var minDistance = range

        for (entity in world.entities) {
            if (!entities.get().contains(entity.type)) continue
            if (!isValid(entity, range)) continue
            if (ignoreNamed.get() && entity.hasCustomName()) continue
            if (ignoreTamed.get() && entity is Tameable && entity.owner?.uuid == player.uuid) continue
            if (ignorePassive.get()) {
                if (entity is EndermanEntity && !entity.isAngry) continue
                if (entity is ZombifiedPiglinEntity && !entity.isAttacking) continue
                if (entity is WolfEntity && !entity.isAttacking) continue
            }
            val dist = player.distanceTo(entity)
            val canSee = canSeeEntity(entity)
            val effectiveRange = if (canSee) attackRange.get() else wallRange.get()
            if (dist <= effectiveRange && dist < minDistance) {
                closestTarget = entity
                minDistance = dist.toDouble()
            }
        }
        return closestTarget
    }

    private fun doAura() {
        val player = mc.player ?: return
        val currentTarget = target ?: return
        if (!check()) return

        var found = false
        var previousSlot = -1

        if (autoSwitch.get() != SwitchMode.None && !itemInHand()) {
            val predicate: java.util.function.Predicate<ItemStack> = when (weapon.get()) {
                Weapon.Axe -> java.util.function.Predicate { it.isIn(ItemTags.AXES) }
                Weapon.Sword -> java.util.function.Predicate { it.isIn(ItemTags.SWORDS) }
                Weapon.Mace -> java.util.function.Predicate { it.isOf(Items.MACE) }
                Weapon.Trident -> java.util.function.Predicate { it.isOf(Items.TRIDENT) }
                Weapon.All -> java.util.function.Predicate { it.isIn(ItemTags.AXES) || it.isIn(ItemTags.SWORDS) || it.isOf(Items.MACE) || it.isOf(Items.TRIDENT) }
                else -> java.util.function.Predicate { true }
            }
            val weaponResult = InvUtils.findInHotbar(predicate)
            previousSlot = player.inventory.selectedSlot
            if (weaponResult.found()) {
                player.inventory.selectedSlot = weaponResult.slot
                mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(weaponResult.slot))
                found = true
            }
        }

        if (autoSwitch.get() != SwitchMode.None && !itemInHand() && !found) return

        val networkHandler = mc.networkHandler ?: return

        if (rotate.get()) {
            val rotation = Rotation.getRotation(Rotation.getClosestPointToEye(player.eyePos, currentTarget.boundingBox))
            targetYaw = rotation[0]
            Rotation.snapAt(currentTarget.boundingBox)
            isRotating = true
        }

        networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(currentTarget, player.isSneaking))

        if (reset.get()) {
            resetAttackTick(player)
        }

        player.swingHand(Hand.MAIN_HAND)
        networkHandler.sendPacket(HandSwingC2SPacket(Hand.MAIN_HAND))

        tick.reset()

        if (rotate.get() && isRotating) {
            Rotation.snapBack()
            isRotating = false
        }

        if (autoSwitch.get() == SwitchMode.Silent && previousSlot != -1) {
            player.inventory.selectedSlot = previousSlot
            mc.networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(previousSlot))
        }
    }

    private fun check(): Boolean {
        val player = mc.player ?: return false
        val currentTarget = target ?: return false
        if (!tick.passedMs(cooldown.get() * 1000.0)) return false
        if (currentTarget is LivingEntity && (currentTarget as LivingEntity).hurtTime > hurtTime.get()) return false
        if (usingPause.get() && player.isUsingItem) return false
        return true
    }

    private fun itemInHand(): Boolean {
        val stack = mc.player?.mainHandStack ?: return false
        return when (weapon.get()) {
            Weapon.Axe -> stack.isIn(ItemTags.AXES)
            Weapon.Sword -> stack.isIn(ItemTags.SWORDS)
            Weapon.Mace -> stack.isOf(Items.MACE)
            Weapon.Trident -> stack.isOf(Items.TRIDENT)
            Weapon.All -> stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.SWORDS) || stack.isOf(Items.MACE) || stack.isOf(Items.TRIDENT)
            else -> true
        }
    }

    private fun resetAttackTick(player: PlayerEntity) {
        try {
            val field = LivingEntity::class.java.getDeclaredField("lastAttackedTicks")
            field.isAccessible = true
            field.setInt(player, 0)
        } catch (e: NoSuchFieldException) {
            try {
                val fallbackField = LivingEntity::class.java.getDeclaredField("field_6156")
                fallbackField.isAccessible = true
                fallbackField.setInt(player, 0)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
