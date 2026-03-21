package com.zychen027.meteorplusplus.modules

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Hand
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import kotlin.math.*

class ElytraFly : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "ElytraFly",
    "鞘翅飞行，支持多种模式。"
) {
    private val sgGeneral = settings.getDefaultGroup()
    private val sgControl = settings.createGroup("控制")
    private val sgBoost = settings.createGroup("加速")
    private val sgBounce = settings.createGroup("弹跳")
    private val sgPitch = settings.createGroup("俯仰")
    private val sgRotation = settings.createGroup("旋转")
    private val sgFirework = settings.createGroup("Firework")
    private val sgPacket = settings.createGroup("Packet")

    // ==================== General Settings ====================
    private val mode = sgGeneral.add(EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Elytra flight mode.")
        .defaultValue(Mode.Control)
        .build()
    )

    private val autoStart = sgGeneral.add(BoolSetting.Builder()
        .name("auto-start")
        .description("Automatically start flying when falling.")
        .defaultValue(true)
        .visible { mode.get() != Mode.Bounce }
        .build()
    )

    private val instantFlyTimeout = sgGeneral.add(DoubleSetting.Builder()
        .name("instant-fly-timeout")
        .description("Timeout for instant fly in seconds.")
        .defaultValue(0.1)
        .min(0.0)
        .max(1.0)
        .visible { autoStart.get() && mode.get() != Mode.Bounce }
        .build()
    )

    private val autoStop = sgGeneral.add(BoolSetting.Builder()
        .name("auto-stop")
        .description("Stop flying when entering unloaded chunks.")
        .defaultValue(true)
        .build()
    )

    private val releaseSneak = sgGeneral.add(BoolSetting.Builder()
        .name("release-sneak")
        .description("Release sneak key when disabling module.")
        .defaultValue(false)
        .build()
    )

    private val infiniteDura = sgGeneral.add(BoolSetting.Builder()
        .name("infinite-durability")
        .description("Prevent elytra durability loss.")
        .defaultValue(false)
        .build()
    )

    // ==================== Control Mode Settings ====================
    private val speed = sgControl.add(DoubleSetting.Builder()
        .name("speed")
        .description("Horizontal flight speed.")
        .defaultValue(1.0)
        .min(0.1)
        .max(10.0)
        .visible { mode.get() == Mode.Control }
        .build()
    )

    private val upSpeed = sgControl.add(DoubleSetting.Builder()
        .name("up-speed")
        .description("Upward movement speed.")
        .defaultValue(1.0)
        .min(0.0)
        .max(10.0)
        .visible { mode.get() == Mode.Control }
        .build()
    )

    private val downSpeed = sgControl.add(DoubleSetting.Builder()
        .name("down-speed")
        .description("Downward movement speed.")
        .defaultValue(1.0)
        .min(0.1)
        .max(10.0)
        .visible { mode.get() == Mode.Control }
        .build()
    )

    private val fallSpeed = sgControl.add(DoubleSetting.Builder()
        .name("fall-speed")
        .description("Fall speed when not moving vertically.")
        .defaultValue(0.00001)
        .min(0.0)
        .max(1.0)
        .visible { mode.get() == Mode.Control }
        .build()
    )

    private val speedLimit = sgControl.add(BoolSetting.Builder()
        .name("speed-limit")
        .description("Limit maximum horizontal speed.")
        .defaultValue(true)
        .visible { mode.get() == Mode.Control }
        .build()
    )

    private val maxSpeed = sgControl.add(DoubleSetting.Builder()
        .name("max-speed")
        .description("Maximum horizontal flight speed.")
        .defaultValue(2.5)
        .min(0.1)
        .max(10.0)
        .visible { mode.get() == Mode.Control && speedLimit.get() }
        .build()
    )

    private val noDrag = sgControl.add(BoolSetting.Builder()
        .name("no-drag")
        .description("Disable air drag for faster flight.")
        .defaultValue(false)
        .visible { mode.get() == Mode.Control }
        .build()
    )

    // ==================== Boost Mode Settings ====================
    private val boostSpeed = sgBoost.add(DoubleSetting.Builder()
        .name("boost-speed")
        .description("Velocity boost multiplier.")
        .defaultValue(1.0)
        .min(0.1)
        .max(4.0)
        .visible { mode.get() == Mode.Boost }
        .build()
    )

    // ==================== Bounce Mode Settings ====================
    private val autoJump = sgBounce.add(BoolSetting.Builder()
        .name("auto-jump")
        .description("Automatically jump to start flying.")
        .defaultValue(true)
        .visible { mode.get() == Mode.Bounce }
        .build()
    )

    private val bounceSprint = sgBounce.add(BoolSetting.Builder()
        .name("bounce-sprint")
        .description("Automatically sprint in bounce mode.")
        .defaultValue(true)
        .visible { mode.get() == Mode.Bounce }
        .build()
    )

    // ==================== Pitch Mode Settings ====================
    private val pitchMinSpeed = sgPitch.add(DoubleSetting.Builder()
        .name("pitch-min-speed")
        .description("Minimum speed threshold.")
        .defaultValue(25.0)
        .min(10.0)
        .max(70.0)
        .visible { mode.get() == Mode.Pitch }
        .build()
    )

    private val pitchMaxSpeed = sgPitch.add(DoubleSetting.Builder()
        .name("pitch-max-speed")
        .description("Maximum speed threshold.")
        .defaultValue(150.0)
        .min(50.0)
        .max(170.0)
        .visible { mode.get() == Mode.Pitch }
        .build()
    )

    private val pitchMaxHeight = sgPitch.add(DoubleSetting.Builder()
        .name("pitch-max-height")
        .description("Maximum height before descending.")
        .defaultValue(200.0)
        .min(-50.0)
        .max(360.0)
        .visible { mode.get() == Mode.Pitch }
        .build()
    )

    // ==================== Rotation Mode Settings ====================
    private val rotationFreeze = sgRotation.add(BoolSetting.Builder()
        .name("freeze")
        .description("Freeze in place when not moving.")
        .defaultValue(false)
        .visible { mode.get() == Mode.Rotation }
        .build()
    )

    private val rotationMotionStop = sgRotation.add(BoolSetting.Builder()
        .name("motion-stop")
        .description("Stop motion when not pressing keys.")
        .defaultValue(false)
        .visible { mode.get() == Mode.Rotation }
        .build()
    )

    // ==================== Firework Settings ====================
    private val firework = sgFirework.add(BoolSetting.Builder()
        .name("enabled")
        .description("Automatically use firework rockets.")
        .defaultValue(false)
        .build()
    )

    private val fireworkDelay = sgFirework.add(IntSetting.Builder()
        .name("delay")
        .description("Delay between firework uses in milliseconds.")
        .defaultValue(1000)
        .min(0)
        .max(20000)
        .visible { firework.get() }
        .build()
    )

    private val fireworkCheckSpeed = sgFirework.add(BoolSetting.Builder()
        .name("check-speed")
        .description("Only use firework when below minimum speed.")
        .defaultValue(false)
        .visible { firework.get() }
        .build()
    )

    private val fireworkMinSpeed = sgFirework.add(DoubleSetting.Builder()
        .name("min-speed")
        .description("Minimum speed to use firework.")
        .defaultValue(70.0)
        .min(0.1)
        .max(200.0)
        .visible { firework.get() && fireworkCheckSpeed.get() }
        .build()
    )

    private val fireworkInventorySwap = sgFirework.add(BoolSetting.Builder()
        .name("inventory-swap")
        .description("Use fireworks from inventory.")
        .defaultValue(true)
        .visible { firework.get() }
        .build()
    )

    // ==================== Packet Mode Settings ====================
    private val packetMode = sgPacket.add(BoolSetting.Builder()
        .name("packet-fly")
        .description("Use packet mode for flying without elytra equipped.")
        .defaultValue(false)
        .build()
    )

    private val packetDelay = sgPacket.add(IntSetting.Builder()
        .name("packet-delay")
        .description("Delay between packets in ticks.")
        .defaultValue(0)
        .min(0)
        .max(20)
        .visible { packetMode.get() }
        .build()
    )

    // ==================== State Variables ====================
    private var hasElytra = false
    private var flying = false
    private var lastFireworkTime = 0L
    private var lastInstantFlyTime = 0L
    private var infinitePitch = 0f
    private var pitchDirection = false
    private var packetDelayCounter = 0

    enum class Mode {
        Control, Boost, Bounce, Freeze, Rotation, Pitch
    }

    override fun onActivate() {
        hasElytra = false
        flying = false
        lastFireworkTime = System.currentTimeMillis()
        lastInstantFlyTime = System.currentTimeMillis()
        infinitePitch = 0f
        packetDelayCounter = 0
    }

    override fun onDeactivate() {
        if (releaseSneak.get()) {
            mc.options.sneakKey.isPressed = false
        }
    }

    // ==================== Event Handlers ====================

    @EventHandler
    private fun onTickPre(event: TickEvent.Pre) {
        val player = mc.player ?: return

        hasElytra = checkForElytra()
        if (!hasElytra && !packetMode.get()) return

        if (infiniteDura.get() && hasElytra && !player.isOnGround) {
            flying = true
            handleInfiniteDurability()
        }

        if (packetMode.get()) {
            handlePacketMode()
            return
        }

        if (mode.get() == Mode.Pitch) {
            updateInfinitePitch()
        }

        if (mode.get() == Mode.Bounce && hasElytra) {
            if (autoJump.get()) {
                mc.options.jumpKey.isPressed = true
            }
            if (bounceSprint.get()) {
                player.isSprinting = true
            }
        }

        if (!isPlayerFallFlying() && hasElytra && autoStart.get() && mode.get() != Mode.Bounce) {
            if (!player.isOnGround && player.velocity.y < 0.0) {
                val now = System.currentTimeMillis()
                if (now - lastInstantFlyTime >= (instantFlyTimeout.get() * 1000).toLong()) {
                    sendStartFlyPacket()
                    lastInstantFlyTime = now
                }
            }
        }

        if (mode.get() == Mode.Boost && isPlayerFallFlying()) {
            handleBoostMode()
        }

        if (firework.get() && isPlayerFallFlying()) {
            handleFirework()
        }
    }

    @EventHandler
    private fun onTickPost(event: TickEvent.Post) {
        if (mode.get() == Mode.Bounce && hasElytra) {
            if (!isPlayerFallFlying()) {
                sendStartFlyPacket()
            }
        }
    }

    @EventHandler
    private fun onMove(event: PlayerMoveEvent) {
        val player = mc.player ?: return

        if (!hasElytra && !packetMode.get()) return
        if (!isPlayerFallFlying()) return

        if (autoStop.get()) {
            val chunkX = (player.x / 16.0).toInt()
            val chunkZ = (player.z / 16.0).toInt()

            val world = mc.world ?: return
            if (!world.chunkManager.isChunkLoaded(chunkX, chunkZ)) {
                return
            }
        }

        when (mode.get()) {
            Mode.Control -> handleControlMode()
            Mode.Freeze -> handleFreezeMode()
            Mode.Rotation -> handleRotationMove()
            Mode.Pitch -> handlePitchMode()
            else -> {}
        }
    }

    @EventHandler
    private fun onPacketSend(event: PacketEvent.Send) {
        val player = mc.player ?: return

        if (mode.get() == Mode.Bounce && hasElytra) {
            if (event.packet is ClientCommandC2SPacket) {
                val packet = event.packet as ClientCommandC2SPacket
                if (packet.mode == ClientCommandC2SPacket.Mode.START_FALL_FLYING) {
                    if (!bounceSprint.get()) {
                        player.isSprinting = true
                    }
                }
            }
        }
    }

    @EventHandler
    private fun onPacketReceive(event: PacketEvent.Receive) {
        if (event.packet is PlayerPositionLookS2CPacket) {
            if (mode.get() == Mode.Bounce && hasElytra) {
                flying = false
            }
        }
    }

    // ==================== Mode Handlers ====================

    private fun handleControlMode() {
        val player = mc.player ?: return

        val yawRad = Math.toRadians(player.yaw.toDouble())

        var motionX = 0.0
        var motionY = 0.0
        var motionZ = 0.0

        val forward = getMovementInput()
        val sideways = getSidewaysInput()

        if (forward != 0f || sideways != 0f) {
            val speedVal = speed.get()

            if (forward != 0f) {
                motionX += -MathHelper.sin(yawRad.toFloat()) * forward * speedVal
                motionZ += MathHelper.cos(yawRad.toFloat()) * forward * speedVal
            }
            if (sideways != 0f) {
                motionX += MathHelper.cos(yawRad.toFloat()) * sideways * speedVal * 0.5
                motionZ += MathHelper.sin(yawRad.toFloat()) * sideways * speedVal * 0.5
            }
        }

        motionY = when {
            mc.options.jumpKey.isPressed -> upSpeed.get()
            mc.options.sneakKey.isPressed -> -downSpeed.get()
            else -> -fallSpeed.get()
        }

        if (!noDrag.get()) {
            motionX *= 0.99
            motionY *= 0.99
            motionZ *= 0.99
        }

        val horizontalSpeed = sqrt(motionX * motionX + motionZ * motionZ)
        if (speedLimit.get() && horizontalSpeed > maxSpeed.get()) {
            val factor = maxSpeed.get() / horizontalSpeed
            motionX *= factor
            motionZ *= factor
        }

        player.velocity = Vec3d(motionX, motionY, motionZ)
    }

    private fun handleBoostMode() {
        val player = mc.player ?: return

        val yawRad = Math.toRadians(player.yaw.toDouble()).toFloat()

        if (mc.options.forwardKey.isPressed) {
            val boost = boostSpeed.get().toFloat() / 10.0f
            player.addVelocity(
                (-MathHelper.sin(yawRad) * boost).toDouble(),
                0.0,
                (MathHelper.cos(yawRad) * boost).toDouble()
            )
        }
    }

    private fun handleFreezeMode() {
        val player = mc.player ?: return

        if (!isMoving() && !mc.options.jumpKey.isPressed && !mc.options.sneakKey.isPressed) {
            player.velocity = Vec3d(0.0, 0.0, 0.0)
        } else {
            handleControlMode()
        }
    }

    private fun handleRotationMove() {
        val player = mc.player ?: return

        if (!isMoving() && !mc.options.jumpKey.isPressed && !mc.options.sneakKey.isPressed) {
            if (rotationFreeze.get()) {
                player.velocity = Vec3d(0.0, 0.0, 0.0)
                return
            }
            if (rotationMotionStop.get()) {
                player.velocity = Vec3d(player.velocity.x, 0.0, player.velocity.z)
            }
        }

        val yawRad = Math.toRadians(player.yaw.toDouble())
        val forward = getMovementInput()

        if (forward != 0f) {
            player.velocity = Vec3d(
                -MathHelper.sin(yawRad.toFloat()) * forward * speed.get(),
                player.velocity.y,
                MathHelper.cos(yawRad.toFloat()) * forward * speed.get()
            )
        }
    }

    private fun handlePitchMode() {
        val player = mc.player ?: return

        val lookVec = getRotationVector(infinitePitch, player.yaw)
        val motionX = player.velocity.x + lookVec.x * 0.1
        val motionZ = player.velocity.z + lookVec.z * 0.1

        player.velocity = Vec3d(motionX, player.velocity.y, motionZ)
    }

    // ==================== Packet Mode Handler ====================

    private fun handlePacketMode() {
        val player = mc.player ?: return
        if (player.isOnGround) return

        packetDelayCounter++
        if (packetDelayCounter <= packetDelay.get()) return

        val elytraSlot = findElytraSlot()
        if (elytraSlot != -1) {
            doClickSlot(6, elytraSlot, SlotActionType.SWAP)
            sendStartFlyPacket()
            if (firework.get() && shouldUseFirework()) {
                useFirework()
            }
            doClickSlot(6, elytraSlot, SlotActionType.SWAP)
            packetDelayCounter = 0
        } else {
            val invElytra = findElytraInInventory()
            if (invElytra != -1) {
                doClickSlot(invElytra, 0, SlotActionType.PICKUP)
                doClickSlot(6, 0, SlotActionType.PICKUP)
                sendStartFlyPacket()
                if (firework.get() && shouldUseFirework()) {
                    useFirework()
                }
                doClickSlot(6, 0, SlotActionType.PICKUP)
                doClickSlot(invElytra, 0, SlotActionType.PICKUP)
                packetDelayCounter = 0
            }
        }
    }

    // ==================== Helper Methods ====================

    private fun doClickSlot(slot: Int, button: Int, action: SlotActionType) {
        val player = mc.player ?: return
        mc.interactionManager?.clickSlot(
            player.currentScreenHandler.syncId,
            slot,
            button,
            action,
            player
        )
    }

    private fun checkForElytra(): Boolean {
        val player = mc.player ?: return false
        val chestStack = player.getEquippedStack(EquipmentSlot.CHEST)
        return chestStack.item == Items.ELYTRA && chestStack.damage < chestStack.maxDamage - 1
    }

    // 使用 LivingEntity.isFallFlying() 方法检查滑翔状态
	private fun isPlayerFallFlying(): Boolean {
		val player = mc.player ?: return false
		return player.isGliding
	}

    private fun sendStartFlyPacket() {
        val player = mc.player ?: return
        mc.networkHandler?.sendPacket(
            ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING)
        )
        flying = true
    }

    private fun handleInfiniteDurability() {
        doClickSlot(6, 0, SlotActionType.PICKUP)
        doClickSlot(6, 0, SlotActionType.PICKUP)
        sendStartFlyPacket()
    }

    private fun updateInfinitePitch() {
        val player = mc.player ?: return

        val currentSpeed = sqrt(
            (player.x - player.lastRenderX).pow(2.0) +
            (player.z - player.lastRenderZ).pow(2.0)
        ) * 72.0

        if (player.y < pitchMaxHeight.get()) {
            if (currentSpeed < pitchMinSpeed.get() && !pitchDirection) {
                pitchDirection = true
            }
            if (currentSpeed > pitchMaxSpeed.get() && pitchDirection) {
                pitchDirection = false
            }
        } else {
            pitchDirection = true
        }

        infinitePitch += (if (pitchDirection) 3f else -3f)
        infinitePitch = infinitePitch.coerceIn(-40f, 40f)
    }

    private fun handleFirework() {
        if (!shouldUseFirework()) return

        val now = System.currentTimeMillis()
        if (now - lastFireworkTime < fireworkDelay.get()) return

        useFirework()
        lastFireworkTime = now
    }

    private fun shouldUseFirework(): Boolean {
        val player = mc.player ?: return false

        if (fireworkCheckSpeed.get()) {
            val spd = sqrt(player.velocity.x.pow(2.0) + player.velocity.z.pow(2.0)) * 72.0
            if (spd > fireworkMinSpeed.get()) return false
        }

        return true
    }

    private fun useFirework() {
        val player = mc.player ?: return

        var fireworkSlot = -1
        for (i in 0..8) {
            if (player.inventory.getStack(i).item == Items.FIREWORK_ROCKET) {
                fireworkSlot = i
                break
            }
        }

        if (fireworkSlot != -1) {
            val oldSlot = player.inventory.selectedSlot
            player.inventory.selectedSlot = fireworkSlot
            mc.interactionManager?.interactItem(player, Hand.MAIN_HAND)
            player.inventory.selectedSlot = oldSlot
        } else if (fireworkInventorySwap.get()) {
            val invSlot = findItemInInventory(Items.FIREWORK_ROCKET)
            if (invSlot != -1) {
                val oldSlot = player.inventory.selectedSlot
                val hotbarIndex = 36 + oldSlot
                doClickSlot(invSlot, hotbarIndex, SlotActionType.SWAP)
                mc.interactionManager?.interactItem(player, Hand.MAIN_HAND)
                doClickSlot(invSlot, hotbarIndex, SlotActionType.SWAP)
            }
        }
    }

    private fun findElytraSlot(): Int {
        val player = mc.player ?: return -1
        for (i in 0..8) {
            if (player.inventory.getStack(i).item == Items.ELYTRA) {
                return i
            }
        }
        return -1
    }

    private fun findElytraInInventory(): Int {
        val player = mc.player ?: return -1
        for (i in 9..35) {
            if (player.inventory.getStack(i).item == Items.ELYTRA) {
                return i
            }
        }
        return -1
    }

    private fun findItemInInventory(item: net.minecraft.item.Item): Int {
        val player = mc.player ?: return -1
        for (i in 9..35) {
            if (player.inventory.getStack(i).item == item) {
                return i
            }
        }
        return -1
    }

    private fun getMovementInput(): Float {
        var forward = 0f
        if (mc.options.forwardKey.isPressed) forward += 1f
        if (mc.options.backKey.isPressed) forward -= 1f
        return forward
    }

    private fun getSidewaysInput(): Float {
        var sideways = 0f
        if (mc.options.leftKey.isPressed) sideways += 1f
        if (mc.options.rightKey.isPressed) sideways -= 1f
        return sideways
    }

    private fun isMoving(): Boolean {
        return mc.options.forwardKey.isPressed ||
               mc.options.backKey.isPressed ||
               mc.options.leftKey.isPressed ||
               mc.options.rightKey.isPressed
    }

    private fun getRotationVector(pitch: Float, yaw: Float): Vec3d {
        val f = pitch * (Math.PI.toFloat() / 180)
        val g = -yaw * (Math.PI.toFloat() / 180)
        val h = MathHelper.cos(g)
        val i = MathHelper.sin(g)
        val j = MathHelper.cos(f)
        val k = MathHelper.sin(f)
        return Vec3d((i * j).toDouble(), (-k).toDouble(), (h * j).toDouble())
    }
}
