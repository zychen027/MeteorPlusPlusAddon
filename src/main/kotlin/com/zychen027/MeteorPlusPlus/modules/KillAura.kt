package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.systems.friends.Friends as MeteorFriends
import meteordevelopment.meteorclient.utils.player.FindItemResult
import meteordevelopment.meteorclient.utils.player.InvUtils
import meteordevelopment.meteorclient.utils.render.color.SettingColor
import meteordevelopment.orbit.EventHandler
import net.minecraft.client.MinecraftClient
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
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.ItemTags
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.function.Predicate
import kotlin.math.*

class KillAura : Module(MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY, "Aura", "杀戮光环") {
    companion object {
        lateinit var INSTANCE: KillAura
    }

    enum class Weapon(val title: String) {
        Sword("剑"), Axe("斧子"), Mace("重锤"), Trident("三叉戟"), Spear("长矛"), All("全部武器"), Any("任意武器");
        override fun toString(): String = title
    }

    enum class SwitchMode(val title: String) {
        Normal("普通"), Silent("静默"), None("无");
        override fun toString(): String = title
    }

    private val sgGeneral = settings.getDefaultGroup()
    private val sgRender = settings.createGroup("Render")
    private val sgSpear = settings.createGroup("矛相关")

    private val targetRange = sgGeneral.add(IntSetting.Builder().name("TargetRange").description("目标距离").defaultValue(6).min(0).sliderMax(8).build())
    private val attackRange = sgGeneral.add(DoubleSetting.Builder().name("Range").description("攻击距离").defaultValue(3.5).min(0.0).sliderMax(8.0).build())
    private val weapon = sgGeneral.add(EnumSetting.Builder<Weapon>().name("Weapon").description("武器选择").defaultValue(Weapon.Sword).build())
    private val autoSwitch = sgGeneral.add(EnumSetting.Builder<SwitchMode>().name("AutoSwitch").description("自动切换武器").defaultValue(SwitchMode.Silent).build())
    private val reset = sgGeneral.add(BoolSetting.Builder().name("Reset").description("自动重置冷却").defaultValue(true).build())
    private val hurtTime = sgGeneral.add(IntSetting.Builder().name("HurtTime").description("伤害时间").defaultValue(10).min(0).sliderMax(10).build())
    private val cooldown = sgGeneral.add(DoubleSetting.Builder().name("Cooldown").description("攻击冷却").defaultValue(0.55).min(0.0).sliderMax(1.0).build())
    private val wallRange = sgGeneral.add(DoubleSetting.Builder().name("WallRange").description("穿墙距离").defaultValue(3.5).min(0.1).sliderMax(7.0).build())
    private val usingPause = sgGeneral.add(BoolSetting.Builder().name("UsingPause").description("使用物品时暂停").defaultValue(true).build())
    private val entities = sgGeneral.add(EntityTypeListSetting.Builder().name("entities").description("攻击目标").onlyAttackable().defaultValue(EntityType.PLAYER).build())
    private val rotate = sgGeneral.add(BoolSetting.Builder().name("Rotate").description("转头").defaultValue(true).build())
    private val ignoreNamed = sgGeneral.add(BoolSetting.Builder().name("ignore-named").description("忽略带有命名的生物").defaultValue(true).build())
    private val ignorePassive = sgGeneral.add(BoolSetting.Builder().name("ignore-passive").description("忽略中立生物").defaultValue(false).build())
    private val ignoreTamed = sgGeneral.add(BoolSetting.Builder().name("ignore-tamed").description("忽略已驯服的生物").defaultValue(true).build())

    private val targetESP = sgRender.add(BoolSetting.Builder().name("TargetESP").description("目标ESP渲染").defaultValue(true).build())
    private val espColor = sgRender.add(ColorSetting.Builder().name("ESPColor").description("ESP渲染颜色").defaultValue(SettingColor(255, 255, 255, 255)).build())

    private val spearJab = sgSpear.add(BoolSetting.Builder()
        .name("spear-jab")
        .description("是否使用长矛戳刺（突进）攻击（右键触发），仅当主手为长矛或全部武器时生效")
        .defaultValue(true)
        .visible { weapon.get() == Weapon.Spear || weapon.get() == Weapon.All }
        .build()
    )

    private val spearCheckHunger = sgSpear.add(BoolSetting.Builder()
        .name("spear-check-hunger")
        .description("当使用戳刺时，是否检测玩家饥饿值 > 6，否则使用普通攻击")
        .defaultValue(true)
        .visible { weapon.get() == Weapon.Spear || weapon.get() == Weapon.All }
        .build()
    )

    private val tick = Timer()
    var target: Entity? = null
        private set

    init { INSTANCE = this }

    override fun onActivate() { tick.setMs(9999999) }

    override fun getInfoString(): String? {
        val player = MinecraftClient.getInstance().player ?: return null
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

    private object RotationHelper {
        fun snapAt(yaw: Float, pitch: Float) {
            val player = MinecraftClient.getInstance().player ?: return
            MinecraftClient.getInstance().networkHandler?.sendPacket(PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround, player.horizontalCollision))
        }
        fun snapAt(vec: Vec3d) { val (yaw, pitch) = getRotation(vec); snapAt(yaw, pitch) }
        fun snapAt(box: Box) { 
            val player = MinecraftClient.getInstance().player ?: return
            snapAt(getClosestPointToEye(Vec3d(player.x, player.getEyeY(), player.z), box)) 
        }
        fun snapBack() {
            val player = MinecraftClient.getInstance().player ?: return
            MinecraftClient.getInstance().networkHandler?.sendPacket(PlayerMoveC2SPacket.Full(player.x, player.y, player.z, player.yaw, player.pitch, player.isOnGround, player.horizontalCollision))
        }
        fun getRotation(vec: Vec3d): Pair<Float, Float> {
            val player = MinecraftClient.getInstance().player ?: return 0f to 0f
            val eyePos = Vec3d(player.x, player.getEyeY(), player.z)
            val deltaX = vec.x - eyePos.x; val deltaY = vec.y - eyePos.y; val deltaZ = vec.z - eyePos.z
            val distance = sqrt(deltaX * deltaX + deltaZ * deltaZ)
            return Math.toDegrees(atan2(-deltaX, deltaZ)).toFloat() to (-Math.toDegrees(atan2(deltaY, distance))).toFloat()
        }
        private fun getClosestPointToEye(eye: Vec3d, box: Box): Vec3d {
            return Vec3d(box.minX.coerceAtLeast(eye.x.coerceAtMost(box.maxX)), box.minY.coerceAtLeast(eye.y.coerceAtMost(box.maxY)), box.minZ.coerceAtLeast(eye.z.coerceAtMost(box.maxZ)))
        }
    }

    private object CombatHelper {
        fun isValid(entity: Entity?, range: Double): Boolean {
            if (entity == null || !entity.isAlive || entity === MinecraftClient.getInstance().player) return false
            if (entity is PlayerEntity && MeteorFriends.get().isFriend(entity)) return false
            if (MinecraftClient.getInstance().player!!.distanceTo(entity) > range) return false
            return true
        }
        fun attackCrystal(crystal: Entity?, rotate: Boolean, usingPause: Boolean) {
            val player = MinecraftClient.getInstance().player ?: return
            if (usingPause && player.isUsingItem) return
            if (crystal != null) {
                RotationHelper.snapAt(Vec3d(crystal.x, crystal.y + 0.25, crystal.z))
                MinecraftClient.getInstance().networkHandler?.sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, player.isSneaking))
                EntityHelper.attackSwingHand()
                if (rotate) RotationHelper.snapBack()
            }
        }
    }

    private object EntityHelper {
        fun attackSwingHand() {
            val player = MinecraftClient.getInstance().player ?: return
            player.swingHand(Hand.MAIN_HAND)
            MinecraftClient.getInstance().networkHandler?.sendPacket(HandSwingC2SPacket(Hand.MAIN_HAND))
        }
        fun useItemInHand(hand: Hand = Hand.MAIN_HAND) {
            val player = MinecraftClient.getInstance().player ?: return
            // 1.21.11 的 PlayerInteractItemC2SPacket 需要 4 个参数：手、序列号(传入0兼容)、yaw、pitch
            MinecraftClient.getInstance().networkHandler?.sendPacket(PlayerInteractItemC2SPacket(hand, 0, player.yaw, player.pitch))
            player.swingHand(hand)
            MinecraftClient.getInstance().networkHandler?.sendPacket(HandSwingC2SPacket(hand))
        }
    }

    private object InventoryHelper {
        fun switchToSlot(slot: Int) {
            val player = MinecraftClient.getInstance().player ?: return
            if (slot in 0..8) {
                player.inventory.selectedSlot = slot
                MinecraftClient.getInstance().networkHandler?.sendPacket(UpdateSelectedSlotC2SPacket(slot))
            }
        }
    }

    @EventHandler
    private fun onRender(event: Render3DEvent) {
        if (target != null && targetESP.get()) {
            // 渲染目标逻辑（为兼容多版本已移除 ShapeBuilder 依赖，你可以在此补充你自己的渲染代码）
        }
    }

    @EventHandler
    private fun onPacket(event: PacketEvent.Send) {
        if (!reset.get()) return
        val packet = event.packet
        if (packet is PlayerInteractEntityC2SPacket || packet is HandSwingC2SPacket) tick.reset()
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        val player = MinecraftClient.getInstance().player ?: return
        val world = MinecraftClient.getInstance().world ?: return

        target = getTarget(targetRange.get().toDouble())
        if (target == null) return

        if (rotate.get()) RotationHelper.snapAt(getAttackVec(target!!))
        doAura()
    }

    private fun doAura() {
        val player = MinecraftClient.getInstance().player ?: return
        if (!check()) return

        var found = false
        var previousSlot = -1

        if (autoSwitch.get() != SwitchMode.None && !itemInHand()) {
            val predicate: Predicate<ItemStack> = when (weapon.get()) {
                Weapon.Axe -> Predicate { stack -> stack.isIn(ItemTags.AXES) }
                Weapon.Sword -> Predicate { stack -> stack.isIn(ItemTags.SWORDS) }
                Weapon.Mace -> Predicate { stack -> stack.isOf(Items.MACE) }
                Weapon.Trident -> Predicate { stack -> stack.isOf(Items.TRIDENT) }
                Weapon.Spear -> Predicate { stack -> isSpear(stack) }
                Weapon.All -> Predicate { stack ->
                    stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.SWORDS) ||
                    stack.isOf(Items.MACE) || stack.isOf(Items.TRIDENT) || isSpear(stack)
                }
                else -> Predicate { _ -> true }
            }

            val weaponResult = InvUtils.findInHotbar(predicate)
            previousSlot = player.inventory.selectedSlot
            if (weaponResult.found()) {
                InventoryHelper.switchToSlot(weaponResult.slot)
                found = true
            }
        }

        if (!itemInHand() && autoSwitch.get() != SwitchMode.None) return
        if (autoSwitch.get() == SwitchMode.Silent && !found && !itemInHand()) return

        if (rotate.get()) RotationHelper.snapAt(getAttackVec(target!!))

        // 长矛戳刺（突进）逻辑
        val useSpearJab = spearJab.get() &&
            (weapon.get() == Weapon.Spear || weapon.get() == Weapon.All) &&
            isSpear(player.mainHandStack)

        val shouldCheckHunger = spearCheckHunger.get()
        val hungerOk = player.hungerManager.foodLevel > 6

        if (useSpearJab && shouldCheckHunger && !hungerOk) {
            // 饥饿值不足，改用普通攻击包
            MinecraftClient.getInstance().networkHandler?.sendPacket(
                PlayerInteractEntityC2SPacket.attack(target, player.isSneaking)
            )
            EntityHelper.attackSwingHand()
        } else if (useSpearJab) {
            // 满足条件，使用右键“戳刺”（突进）
            EntityHelper.useItemInHand(Hand.MAIN_HAND)
        } else {
            // 普通武器或未启用矛戳刺
            MinecraftClient.getInstance().networkHandler?.sendPacket(
                PlayerInteractEntityC2SPacket.attack(target, player.isSneaking)
            )
            EntityHelper.attackSwingHand()
        }

        tick.reset()

        if (rotate.get()) RotationHelper.snapBack()
        if (autoSwitch.get() == SwitchMode.Silent && previousSlot != -1) InventoryHelper.switchToSlot(previousSlot)
    }

    private fun itemInHand(): Boolean {
        val player = MinecraftClient.getInstance().player ?: return false
        val stack = player.mainHandStack
        return when (weapon.get()) {
            Weapon.Axe -> stack.isIn(ItemTags.AXES)
            Weapon.Sword -> stack.isIn(ItemTags.SWORDS)
            Weapon.Mace -> stack.isOf(Items.MACE)
            Weapon.Trident -> stack.isOf(Items.TRIDENT)
            Weapon.Spear -> isSpear(stack)
            Weapon.All -> stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.SWORDS) ||
                stack.isOf(Items.MACE) || stack.isOf(Items.TRIDENT) || isSpear(stack)
            else -> true
        }
    }

    private fun check(): Boolean {
        val player = MinecraftClient.getInstance().player ?: return false
        val target = this.target ?: return false
        if (!CombatHelper.isValid(target, attackRange.get())) return false
        if (!player.canSee(target) && player.distanceTo(target) > wallRange.get()) return false
        if (!tick.passedMs(cooldown.get() * 1000.0)) return false
        if (target is LivingEntity && (target as LivingEntity).hurtTime > hurtTime.get()) return false
        return !usingPause.get() || !player.isUsingItem
    }

    private fun getTarget(range: Double): Entity? {
        val player = MinecraftClient.getInstance().player ?: return null
        val world = MinecraftClient.getInstance().world ?: return null
        var target: Entity? = null
        var distance = range

        for (entity in world.entities) {
            if (!entities.get().contains(entity.type)) continue
            if (ignoreNamed.get() && entity.hasCustomName()) continue
            if (ignoreTamed.get() && entity is Tameable && entity.owner?.uuid == player.uuid) continue
            if (ignorePassive.get()) {
                if (entity is EndermanEntity && !entity.isAngry) continue
                if (entity is ZombifiedPiglinEntity && !entity.isAttacking) continue
                if (entity is WolfEntity && !entity.isAttacking) continue
            }
            if (!CombatHelper.isValid(entity, targetRange.get().toDouble())) continue

            val dist = player.distanceTo(entity).toDouble()
            if (target == null || dist < distance) {
                target = entity
                distance = dist
            }
        }
        return target
    }

    private fun getAttackVec(entity: Entity): Vec3d {
        val player = MinecraftClient.getInstance().player ?: return Vec3d.ZERO
        val eyePos = Vec3d(player.x, player.getEyeY(), player.z)
        val box = entity.boundingBox
        return Vec3d(
            box.minX.coerceAtLeast(eyePos.x.coerceAtMost(box.maxX)),
            box.minY.coerceAtLeast(eyePos.y.coerceAtMost(box.maxY)),
            box.minZ.coerceAtLeast(eyePos.z.coerceAtMost(box.maxZ))
        )
    }

    // 动态判断物品是否为长矛（通过注册名包含 "spear" 判断，兼容任何材质的矛和不同映射）
    private fun isSpear(stack: ItemStack): Boolean {
        return try {
            Registries.ITEM.getId(stack.item).path.contains("spear", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}
