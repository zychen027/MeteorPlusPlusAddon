package com.zychen027.MeteorPlusPlus.modules

import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.friends.Friends
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.entity.EntityUtils
import meteordevelopment.meteorclient.utils.entity.SortPriority
import meteordevelopment.meteorclient.utils.entity.Target
import meteordevelopment.meteorclient.utils.entity.TargetUtils
import meteordevelopment.meteorclient.utils.player.FindItemResult
import meteordevelopment.meteorclient.utils.player.InvUtils
import meteordevelopment.meteorclient.utils.player.PlayerUtils
import meteordevelopment.meteorclient.utils.player.Rotations
import meteordevelopment.meteorclient.utils.world.TickRate
import meteordevelopment.orbit.EventHandler
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.Tameable
import net.minecraft.entity.mob.EndermanEntity
import net.minecraft.entity.mob.PiglinEntity
import net.minecraft.entity.mob.ZombifiedPiglinEntity
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.entity.passive.WolfEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.AxeItem
import net.minecraft.item.MaceItem
import net.minecraft.item.TridentItem
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.registry.tag.ItemTags
import net.minecraft.util.Hand
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.world.GameMode
import com.zychen027.MeteorPlusPlus.MeteorPlusPlusAddon

class KillAura : Module(
    MeteorPlusPlusAddon.PACKETMINE_CATEGORY,
    "KillAura",
    "Auto-attack entities around you."
) {
    private val sgGeneral = settings.getDefaultGroup()
    private val sgTargeting = settings.createGroup("Targeting")
    private val sgTiming = settings.createGroup("Timing")

    private val weapon = sgGeneral.add(EnumSetting.Builder<Weapon>()
        .name("weapon")
        .description("Only attacks when specified weapon is in hand.")
        .defaultValue(Weapon.All)
        .build()
    )

    private val rotation = sgGeneral.add(EnumSetting.Builder<RotationMode>()
        .name("rotate")
        .description("When to rotate towards target.")
        .defaultValue(RotationMode.Always)
        .build()
    )

    private val autoSwitch = sgGeneral.add(BoolSetting.Builder()
        .name("auto-switch")
        .description("Switches to weapon when attacking.")
        .defaultValue(false)
        .build()
    )

    private val swapBack = sgGeneral.add(BoolSetting.Builder()
        .name("swap-back")
        .description("Switches back to previous slot after attacking.")
        .defaultValue(false)
        .visible { autoSwitch.get() }
        .build()
    )

    private val onlyOnClick = sgGeneral.add(BoolSetting.Builder()
        .name("only-on-click")
        .description("Only attacks when holding left click.")
        .defaultValue(false)
        .build()
    )

    private val onlyOnLook = sgGeneral.add(BoolSetting.Builder()
        .name("only-on-look")
        .description("Only attacks when looking at an entity.")
        .defaultValue(false)
        .build()
    )

    private val shieldMode = sgGeneral.add(EnumSetting.Builder<ShieldMode>()
        .name("shield-mode")
        .description("How to handle shields.")
        .defaultValue(ShieldMode.Break)
        .visible { autoSwitch.get() && weapon.get() != Weapon.Axe }
        .build()
    )

    private val entities = sgTargeting.add(EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build()
    )

    private val priority = sgTargeting.add(EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to sort targets.")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    )

    private val maxTargets = sgTargeting.add(IntSetting.Builder()
        .name("max-targets")
        .description("Maximum targets to attack at once.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .visible { !onlyOnLook.get() }
        .build()
    )

    private val range = sgTargeting.add(DoubleSetting.Builder()
        .name("range")
        .description("Attack range.")
        .defaultValue(4.5)
        .min(0.0)
        .sliderMax(6.0)
        .build()
    )

    private val wallsRange = sgTargeting.add(DoubleSetting.Builder()
        .name("walls-range")
        .description("Range through walls.")
        .defaultValue(3.5)
        .min(0.0)
        .sliderMax(6.0)
        .build()
    )

    private val mobAgeFilter = sgTargeting.add(EnumSetting.Builder<EntityAge>()
        .name("mob-age-filter")
        .description("Which age of mobs to target.")
        .defaultValue(EntityAge.Adult)
        .build()
    )

    private val ignoreNamed = sgTargeting.add(BoolSetting.Builder()
        .name("ignore-named")
        .description("Ignore named mobs.")
        .defaultValue(false)
        .build()
    )

    private val ignorePassive = sgTargeting.add(BoolSetting.Builder()
        .name("ignore-passive")
        .description("Ignore passive mobs unless angry.")
        .defaultValue(true)
        .build()
    )

    private val ignoreTamed = sgTargeting.add(BoolSetting.Builder()
        .name("ignore-tamed")
        .description("Ignore tamed mobs.")
        .defaultValue(false)
        .build()
    )

    private val pauseOnLag = sgTiming.add(BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pause when server is lagging.")
        .defaultValue(true)
        .build()
    )

    private val pauseOnUse = sgTiming.add(BoolSetting.Builder()
        .name("pause-on-use")
        .description("Pause while using items.")
        .defaultValue(false)
        .build()
    )

    private val tpsSync = sgTiming.add(BoolSetting.Builder()
        .name("TPS-sync")
        .description("Sync attack delay with server TPS.")
        .defaultValue(true)
        .build()
    )

    private val customDelay = sgTiming.add(BoolSetting.Builder()
        .name("custom-delay")
        .description("Use custom delay instead of vanilla cooldown.")
        .defaultValue(false)
        .build()
    )

    private val hitDelay = sgTiming.add(IntSetting.Builder()
        .name("hit-delay")
        .description("Custom hit delay in ticks.")
        .defaultValue(11)
        .min(0)
        .sliderMax(60)
        .visible { customDelay.get() }
        .build()
    )

    private val switchDelay = sgTiming.add(IntSetting.Builder()
        .name("switch-delay")
        .description("Delay after switching hotbar slots.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    )

    private val targets = mutableListOf<Entity>()
    private var switchTimer = 0
    private var hitTimer = 0
    private var attacking = false
    private var swapped = false
    private var previousSlot = -1

    override fun onActivate() {
        previousSlot = -1
        swapped = false
    }

    override fun onDeactivate() {
        targets.clear()
        stopAttacking()
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        val player = mc.player ?: return
        
        if (!player.isAlive || PlayerUtils.getGameMode() == GameMode.SPECTATOR) {
            stopAttacking()
            return
        }
        
        if (pauseOnUse.get() && (mc.interactionManager?.isBreakingBlock == true || player.isUsingItem)) {
            stopAttacking()
            return
        }
        
        if (onlyOnClick.get() && !mc.options.attackKey.isPressed) {
            stopAttacking()
            return
        }
        
        if (TickRate.INSTANCE.timeSinceLastTick >= 1f && pauseOnLag.get()) {
            stopAttacking()
            return
        }

        if (onlyOnLook.get()) {
            val targeted = mc.targetedEntity
            if (targeted == null || !entityCheck(targeted)) {
                stopAttacking()
                return
            }
            targets.clear()
            targets.add(targeted)
        } else {
            targets.clear()
            TargetUtils.getList(targets, this::entityCheck, priority.get(), maxTargets.get())
        }

        if (targets.isEmpty()) {
            stopAttacking()
            return
        }

        val primary = targets.first()

        if (autoSwitch.get()) {
            val weaponResult = findWeapon()

            if (shouldShieldBreak()) {
                val axeResult = InvUtils.findInHotbar { it.item is AxeItem }
                if (axeResult.found()) {
                    InvUtils.swap(axeResult.slot(), false)
                    return
                }
            }

            if (!swapped) {
                previousSlot = player.inventory.selectedSlot
                swapped = true
            }
            InvUtils.swap(weaponResult.slot(), false)
        }

        if (!itemInHand()) {
            stopAttacking()
            return
        }

        attacking = true

        if (rotation.get() == RotationMode.Always) {
            Rotations.rotate(Rotations.getYaw(primary), Rotations.getPitch(primary, Target.Body))
        }

        if (delayCheck()) {
            targets.forEach { attack(it) }
        }
    }

    private fun findWeapon(): FindItemResult {
        return when (weapon.get()) {
            Weapon.Axe -> InvUtils.findInHotbar { it.item is AxeItem }
            Weapon.Sword -> InvUtils.findInHotbar { it.isIn(ItemTags.SWORDS) }
            Weapon.Mace -> InvUtils.findInHotbar { it.item is MaceItem }
            Weapon.Trident -> InvUtils.findInHotbar { it.item is TridentItem }
            Weapon.All -> InvUtils.findInHotbar { 
                it.item is AxeItem || it.isIn(ItemTags.SWORDS) || it.item is MaceItem || it.item is TridentItem 
            }
            else -> InvUtils.findInHotbar { true }
        }
    }

    @EventHandler
    private fun onSendPacket(event: PacketEvent.Send) {
        if (event.packet is UpdateSelectedSlotC2SPacket) {
            switchTimer = switchDelay.get()
        }
    }

    private fun stopAttacking() {
        if (!attacking) return
        attacking = false
        
        if (swapBack.get() && swapped) {
            InvUtils.swap(previousSlot, false)
            swapped = false
        }
    }

    private fun shouldShieldBreak(): Boolean {
        for (target in targets) {
            if (target is PlayerEntity && target.isBlocking && shieldMode.get() == ShieldMode.Break) {
                return true
            }
        }
        return false
    }

    private fun entityCheck(entity: Entity): Boolean {
        val player = mc.player ?: return false
        
        if (entity == player || entity == mc.cameraEntity) return false
        if (entity is LivingEntity && entity.isDead || !entity.isAlive) return false

        val hitbox = entity.boundingBox
        if (!PlayerUtils.isWithin(
            MathHelper.clamp(player.x, hitbox.minX, hitbox.maxX),
            MathHelper.clamp(player.y, hitbox.minY, hitbox.maxY),
            MathHelper.clamp(player.z, hitbox.minZ, hitbox.maxZ),
            range.get()
        )) return false

        if (!entities.get().contains(entity.type)) return false
        if (ignoreNamed.get() && entity.hasCustomName()) return false
        if (!PlayerUtils.canSeeEntity(entity) && !PlayerUtils.isWithin(entity, wallsRange.get())) return false

        if (ignoreTamed.get() && entity is Tameable) {
            val owner = entity.owner
            if (owner != null && owner == player) return false
        }

        if (ignorePassive.get()) {
            if (entity is EndermanEntity && !entity.isAngry) return false
            if (entity is PiglinEntity && !entity.isAttacking) return false
            if (entity is ZombifiedPiglinEntity && !entity.isAttacking) return false
            if (entity is WolfEntity && !entity.isAttacking) return false
        }

        if (entity is PlayerEntity) {
            if (entity.isCreative) return false
            if (!Friends.get().shouldAttack(entity)) return false
            if (shieldMode.get() == ShieldMode.Ignore && entity.isBlocking) return false
        }

        if (entity is AnimalEntity) {
            return when (mobAgeFilter.get()) {
                EntityAge.Baby -> entity.isBaby
                EntityAge.Adult -> !entity.isBaby
                EntityAge.Both -> true
            }
        }

        return true
    }

    private fun delayCheck(): Boolean {
        val player = mc.player ?: return false
        
        if (switchTimer > 0) {
            switchTimer--
            return false
        }

        var delay = if (customDelay.get()) hitDelay.get().toFloat() else 0.5f
        if (tpsSync.get()) delay /= (TickRate.INSTANCE.tickRate / 20f)

        if (customDelay.get()) {
            if (hitTimer < delay) {
                hitTimer++
                return false
            }
            return true
        } else {
            return player.getAttackCooldownProgress(delay) >= 1
        }
    }

    private fun attack(target: Entity) {
        val player = mc.player ?: return
        
        if (rotation.get() == RotationMode.OnHit) {
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body))
        }

        mc.interactionManager?.attackEntity(player, target)
        player.swingHand(Hand.MAIN_HAND)
        hitTimer = 0
    }

    private fun itemInHand(): Boolean {
        val player = mc.player ?: return false
        val stack = player.mainHandStack
        
        if (shouldShieldBreak()) return stack.item is AxeItem

        return when (weapon.get()) {
            Weapon.Axe -> stack.item is AxeItem
            Weapon.Sword -> stack.isIn(ItemTags.SWORDS)
            Weapon.Mace -> stack.item is MaceItem
            Weapon.Trident -> stack.item is TridentItem
            Weapon.All -> stack.item is AxeItem || stack.isIn(ItemTags.SWORDS) || stack.item is MaceItem || stack.item is TridentItem
            else -> true
        }
    }

    fun getTarget(): Entity? {
        return if (targets.isNotEmpty()) targets.first() else null
    }

    override fun getInfoString(): String? {
        return if (targets.isNotEmpty()) EntityUtils.getName(getTarget()) else null
    }

    enum class Weapon {
        Sword, Axe, Mace, Trident, All, Any
    }

    enum class RotationMode {
        Always, OnHit, None
    }

    enum class ShieldMode {
        Ignore, Break, None
    }

    enum class EntityAge {
        Baby, Adult, Both
    }
}
