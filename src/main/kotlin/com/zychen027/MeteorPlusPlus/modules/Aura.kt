package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.utils.combat.CombatUtil
import com.zychen027.meteorplusplus.utils.entity.InventoryUtil
import com.zychen027.meteorplusplus.utils.math.Timer
import com.zychen027.meteorplusplus.utils.rotation.Rotation
import com.zychen027.meteorplusplus.utils.world.BlockUtil
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.player.InvUtils
import meteordevelopment.orbit.EventHandler
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.Tameable
import net.minecraft.entity.mob.EndermanEntity
import net.minecraft.entity.mob.ZombifiedPiglinEntity
import net.minecraft.entity.passive.WolfEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.AxeItem
import net.minecraft.item.ItemStack
import net.minecraft.item.MaceItem
import net.minecraft.item.TridentItem
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.registry.tag.ItemTags
import net.minecraft.util.Hand
import net.minecraft.util.math.Vec3d
import java.util.*

/**
 * Aura (杀戮光环) - 移植自 LeavesHack
 * 自动攻击范围内的敌人
 * 包含 HurtTime 检测、Cooldown 控制、自动切换武器等绕过反作弊功能
 */
class KillAura : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "杀戮光环",
    "自动攻击范围内的敌人"
) {
    private val sgGeneral = settings.getDefaultGroup()

    private val targetRange = sgGeneral.add(IntSetting.Builder()
        .name("TargetRange")
        .defaultValue(6)
        .min(0)
        .sliderMax(8)
        .build())

    val attackRange = sgGeneral.add(DoubleSetting.Builder()
        .name("Range")
        .defaultValue(3.5)
        .min(0.0)
        .sliderMax(8.0)
        .build())

    private val weapon = sgGeneral.add(EnumSetting.Builder<Weapon>()
        .name("Weapon")
        .description("Only attacks an entity when a specified weapon is in your hand.")
        .defaultValue(Weapon.Sword)
        .build())

    private val autoSwitch = sgGeneral.add(BoolSetting.Builder()
        .name("AutoSwitch")
        .description("Switches to your selected weapon when attacking the target.")
        .defaultValue(false)
        .build())

    private val reset = sgGeneral.add(BoolSetting.Builder()
        .name("Reset")
        .defaultValue(true)
        .build())

    val hurtTime = sgGeneral.add(IntSetting.Builder()
        .name("HurtTime")
        .defaultValue(10)
        .min(0)
        .sliderMax(10)
        .build())

    val cooldown = sgGeneral.add(DoubleSetting.Builder()
        .name("Cooldown")
        .defaultValue(0.55)
        .min(0.0)
        .sliderMax(1.0)
        .build())

    private val wallRange = sgGeneral.add(DoubleSetting.Builder()
        .name("WallRange")
        .defaultValue(3.5)
        .min(0.1)
        .sliderMax(7.0)
        .build())

    private val usingPause = sgGeneral.add(BoolSetting.Builder()
        .name("UsingPause")
        .defaultValue(true)
        .build())

    private val entities = sgGeneral.add(EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build())

    private val rotate = sgGeneral.add(BoolSetting.Builder()
        .name("Rotate")
        .defaultValue(true)
        .build())

    private val ignoreNamed = sgGeneral.add(BoolSetting.Builder()
        .name("ignore-named")
        .description("Whether or not to attack mobs with a name.")
        .defaultValue(true)
        .build())

    private val ignorePassive = sgGeneral.add(BoolSetting.Builder()
        .name("ignore-passive")
        .description("Will only attack sometimes passive mobs if they are targeting you.")
        .defaultValue(false)
        .build())

    private val ignoreTamed = sgGeneral.add(BoolSetting.Builder()
        .name("ignore-tamed")
        .description("Will avoid attacking mobs you tamed.")
        .defaultValue(true)
        .build())

    private val tick = Timer()
    var target: Entity? = null
    var swapped = false
    var previousSlot = -1

    override fun onActivate() {
        tick.setMs(9999999)
        swapped = false
        previousSlot = -1
    }

    override fun getInfoString(): String? {
        return target?.let { "§f[${it.name.string}]" }
    }

    @EventHandler
    private fun onPacket(event: PacketEvent.Send) {
        if (!reset.get()) return
        
        val packet = event.packet
        if (packet is PlayerInteractEntityC2SPacket) {
            tick.reset()
        }
        if (packet is HandSwingC2SPacket) {
            tick.reset()
        }
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        val player = mc.player ?: return
        val world = mc.world ?: return

        target = getTarget(targetRange.get())
        if (target == null) return

        doAura()
    }

    private fun doAura() {
        if (!check()) return

        if (autoSwitch.get() && !itemInHand()) {
            val predicate = when (weapon.get()) {
                Weapon.Axe -> { stack: ItemStack -> stack.item is AxeItem }
                Weapon.Sword -> { stack: ItemStack -> stack.isIn(ItemTags.SWORDS) }
                Weapon.Mace -> { stack: ItemStack -> stack.item is MaceItem }
                Weapon.Trident -> { stack: ItemStack -> stack.item is TridentItem }
                Weapon.All -> { stack: ItemStack -> 
                    stack.item is AxeItem || stack.isIn(ItemTags.SWORDS) || 
                    stack.item is MaceItem || stack.item is TridentItem 
                }
                Weapon.Any -> { _ -> true }
            }

            val weaponResult = InvUtils.findInHotbar(predicate)
            if (!swapped) {
                previousSlot = mc.player!!.inventory.selectedSlot
                swapped = true
            }
            if (weaponResult.found()) {
                InventoryUtil.switchToSlot(weaponResult.slot())
            }
        }

        if (!itemInHand()) return

        val target = target ?: return
        if (rotate.get()) {
            val hitVec = getAttackVec(target)
            Rotation.snapAt(hitVec, false)
        }

        mc.networkHandler?.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player!!.isSneaking))
        mc.player!!.resetLastAttackedTicks()
        mc.player!!.swingHand(Hand.MAIN_HAND)
        tick.reset()

        if (rotate.get()) {
            Rotation.snapBack(false)
        }
    }

    private fun itemInHand(): Boolean {
        val stack = mc.player!!.mainHandStack
        return when (weapon.get()) {
            Weapon.Axe -> stack.item is AxeItem
            Weapon.Sword -> stack.isIn(ItemTags.SWORDS)
            Weapon.Mace -> stack.item is MaceItem
            Weapon.Trident -> stack.item is TridentItem
            Weapon.All -> stack.item is AxeItem || stack.isIn(ItemTags.SWORDS) || 
                         stack.item is MaceItem || stack.item is TridentItem
            Weapon.Any -> true
        }
    }

    private fun check(): Boolean {
        if (!tick.passedMs((cooldown.get() * 1000).toLong())) return false
        if (target is LivingEntity && (target as LivingEntity).hurtTime > hurtTime.get()) return false
        return usingPause.get() || !mc.player!!.isUsingItem
    }

    private fun getTarget(range: Int): Entity? {
        var target: Entity? = null
        val rangeDouble = range.toDouble()
        val wallRangeDouble = wallRange.get()

        for (entity in mc.world?.entities ?: emptyList()) {
            if (!entities.get().contains(entity.type)) continue
            if (ignoreNamed.get() && entity.hasCustomName()) continue

            if (ignoreTamed.get()) {
                if (entity is Tameable) {
                    val owner = entity.owner
                    if (owner != null && owner.uuid == mc.player!!.uuid) continue
                }
            }

            if (ignorePassive.get()) {
                if (entity is EndermanEntity && !entity.isAngry) continue
                if (entity is ZombifiedPiglinEntity && !entity.isAttacking) continue
                if (entity is WolfEntity && !entity.isAttacking) continue
            }

            if (!mc.player!!.canSee(entity) && mc.player!!.distanceTo(entity) > wallRangeDouble) continue
            if (!CombatUtil.isValid(entity, attackRange.get())) continue

            if (target == null) {
                target = entity
            } else {
                if (mc.player!!.distanceTo(entity) < mc.player!!.distanceTo(target)) {
                    target = entity
                }
            }
        }
        return target
    }

    private fun getAttackVec(entity: Entity): Vec3d {
        return BlockUtil.getClosestPointToBox(mc.player!!.eyePos, entity.boundingBox)
    }

    enum class Weapon {
        Sword,
        Axe,
        Mace,
        Trident,
        All,
        Any
    }
}
