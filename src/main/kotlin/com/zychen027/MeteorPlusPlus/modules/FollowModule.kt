package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.systems.friends.Friends as MeteorFriends
import meteordevelopment.orbit.EventHandler
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import java.util.ArrayList
import kotlin.math.*

class FollowModule : Module(MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY, "Follow", "Automatically follows a player") {
    companion object {
        lateinit var INSTANCE: FollowModule
    }

    enum class TargetMode(val title: String) {
        Closest("Closest"),
        MouseClosest("Mouse Closest"),
        Specific("Specific");

        override fun toString(): String = title
    }

    private var target: PlayerEntity? = null
    private var serverRotation = floatArrayOf(0.0f, 0.0f)
    private var savedRotation = floatArrayOf(0.0f, 0.0f)
    private var targetNotFoundWarned = false

    private val sgGeneral = settings.getDefaultGroup()
    private val sgTargeting = settings.createGroup("Targeting")

    private val targetMode = sgTargeting.add(EnumSetting.Builder<TargetMode>().name("target-mode").description("How to select target").defaultValue(TargetMode.Closest).build())
    private val targetName = sgTargeting.add(StringSetting.Builder().name("target-name").description("Specific player name to follow (leave empty for auto)").defaultValue("").visible { targetMode.get() == TargetMode.Specific }.build())
    private val targetRange = sgTargeting.add(DoubleSetting.Builder().name("target-range").description("Maximum range to search for targets").defaultValue(50.0).min(1.0).sliderMax(100.0).build())
    private val ignoreFriends = sgTargeting.add(BoolSetting.Builder().name("ignore-friends").description("Ignore friends").defaultValue(true).build())
    private val switchTarget = sgTargeting.add(BoolSetting.Builder().name("switch-target").description("Switch to closer target if available").defaultValue(true).build())
    private val followDistance = sgGeneral.add(DoubleSetting.Builder().name("distance").description("Follow distance").defaultValue(3.0).min(1.0).sliderMax(10.0).build())
    private val silentRotation = sgGeneral.add(BoolSetting.Builder().name("silent-rotation").description("Silently rotate without moving client view").defaultValue(true).build())
    private val stopOnSneak = sgGeneral.add(BoolSetting.Builder().name("stop-on-sneak").description("Stop following when sneaking").defaultValue(true).build())

    init { INSTANCE = this }

    override fun onActivate() {
        if (mc.world != null && mc.player != null) {
            savedRotation[0] = mc.player!!.yaw
            savedRotation[1] = mc.player!!.pitch
            serverRotation[0] = mc.player!!.yaw
            serverRotation[1] = mc.player!!.pitch
            targetNotFoundWarned = false
            findTarget()
        } else {
            toggle()
        }
    }

    override fun onDeactivate() {
        target = null
        targetNotFoundWarned = false
        if (mc.player != null) {
            mc.options.forwardKey.setPressed(false)
            mc.options.backKey.setPressed(false)
            mc.options.leftKey.setPressed(false)
            mc.options.rightKey.setPressed(false)
            mc.options.jumpKey.setPressed(false)
            mc.player!!.setYaw(savedRotation[0])
            mc.player!!.setPitch(savedRotation[1])
        }
    }

    override fun getInfoString(): String? {
        return if (target == null) null else target!!.name.string
    }

    private fun findTarget() {
        if (mc.world != null && mc.player != null) {
            if (targetMode.get() == TargetMode.Specific) findSpecificTarget() else findAutoTarget()
        }
    }

    private fun findSpecificTarget() {
        if (mc.world == null || mc.player == null) return
        val name = targetName.get()
        if (name.isEmpty()) {
            if (!targetNotFoundWarned) {
                mc.player!!.sendMessage(Text.literal("§c[Follow] 请设置要跟随的玩家名字"), false)
                targetNotFoundWarned = true
            }
            return
        }
        try {
            for (player in ArrayList(mc.world!!.players)) {
                if (player != null && player !== mc.player &&
                    (!ignoreFriends.get() || !MeteorFriends.get().isFriend(player)) &&
                    player.name != null && player.name.string.equals(name, ignoreCase = true)
                ) {
                    target = player
                    targetNotFoundWarned = false
                    mc.player!!.sendMessage(Text.literal("§a[Follow] 正在跟随: $name"), false)
                    return
                }
            }
        } catch (e: Exception) { return }
        if (!targetNotFoundWarned) {
            mc.player!!.sendMessage(Text.literal("§c[Follow] 找不到玩家: $name"), false)
            targetNotFoundWarned = true
        }
    }

    private fun findAutoTarget() {
        if (mc.world == null || mc.player == null) return
        try {
            val players = ArrayList(mc.world!!.players)
            target = players.stream()
                .filter { player -> player != null && player !== mc.player }
                .filter { player -> player.isAlive }
                .filter { player -> player.distanceTo(mc.player!!) <= targetRange.get() }
                .filter { player -> !ignoreFriends.get() || !MeteorFriends.get().isFriend(player) }
                .min(comparator)
                .orElse(null)
            if (target != null) {
                targetNotFoundWarned = false
                mc.player!!.sendMessage(Text.literal("§a[Follow] 正在跟随: ${target!!.name.string}"), false)
            } else if (!targetNotFoundWarned) {
                mc.player!!.sendMessage(Text.literal("§c[Follow] 找不到目标"), false)
                targetNotFoundWarned = true
            }
        } catch (e: Exception) { target = null }
    }

    private val comparator: Comparator<PlayerEntity>
        get() = when (targetMode.get()) {
            TargetMode.Closest -> Comparator.comparingDouble { p -> try { mc.player!!.distanceTo(p).toDouble() } catch (e: Exception) { Double.MAX_VALUE } }
            TargetMode.MouseClosest -> Comparator.comparingDouble { p -> try { getDistanceToMouse(p) } catch (e: Exception) { Double.MAX_VALUE } }
            else -> Comparator.comparingDouble { p -> try { mc.player!!.distanceTo(p).toDouble() } catch (e: Exception) { Double.MAX_VALUE } }
        }

    private fun getDistanceToMouse(player: PlayerEntity): Double {
        if (mc.player != null && player != null) {
            val playerPos = Vec3d(player.x, player.y, player.z)
            val eyePos = Vec3d(mc.player!!.x, mc.player!!.getEyeY(), mc.player!!.z)
            val lookVec = getRotationVector(mc.player!!.yaw, mc.player!!.pitch)
            val playerToTarget = playerPos.subtract(eyePos).normalize()
            return 1.0 - lookVec.dotProduct(playerToTarget)
        } else { return Double.MAX_VALUE }
    }

    private fun getRotationVector(yaw: Float, pitch: Float): Vec3d {
        val f = pitch * (Math.PI / 180.0).toFloat()
        val g = -yaw * (Math.PI / 180.0).toFloat()
        val h = cos(f); val i = sin(f); val j = cos(g); val k = sin(g)
        return Vec3d((j * h).toDouble(), (-i).toDouble(), (k * h).toDouble())
    }

    private fun getRotationToTarget(targetPos: Vec3d): FloatArray {
        if (mc.player != null) {
            val eyePos = Vec3d(mc.player!!.x, mc.player!!.getEyeY(), mc.player!!.z)
            val deltaX = targetPos.x - eyePos.x
            val deltaY = targetPos.y - eyePos.y
            val deltaZ = targetPos.z - eyePos.z
            val distance = sqrt(deltaX * deltaX + deltaZ * deltaZ)
            val yaw = Math.toDegrees(atan2(-deltaX, deltaZ)).toFloat()
            val pitch = (-Math.toDegrees(atan2(deltaY, distance))).toFloat()
            return floatArrayOf(yaw, pitch)
        } else { return floatArrayOf(0.0f, 0.0f) }
    }

    private fun rotate(yaw: Float, pitch: Float) {
        if (mc.player != null) {
            if (silentRotation.get()) silentRotate(yaw, pitch) else {
                mc.player!!.setYaw(yaw)
                mc.player!!.setPitch(pitch)
            }
        }
    }

    private fun silentRotate(yaw: Float, pitch: Float) {
        if (mc.player != null) {
            serverRotation[0] = yaw
            serverRotation[1] = pitch
            if (mc.networkHandler != null) {
                mc.networkHandler!!.sendPacket(PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player!!.isOnGround, mc.player!!.horizontalCollision))
            }
        }
    }

    fun getServerRotation(): FloatArray { return serverRotation.clone() }

    private fun isTargetValid(): Boolean {
        if (target != null && mc.player != null && mc.world != null) {
            try { return target!!.isAlive && target!!.distanceTo(mc.player!!) <= targetRange.get() && mc.world!!.players.contains(target) } catch (e: Exception) { return false }
        } else { return false }
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        if (mc.world != null && mc.player != null) {
            if (target == null || !isTargetValid()) {
                target = null
                findTarget()
                if (target == null) {
                    mc.options.forwardKey.setPressed(false)
                    mc.options.jumpKey.setPressed(false)
                    return
                }
            }
            if (stopOnSneak.get() && mc.player!!.isSneaking) {
                mc.options.forwardKey.setPressed(false)
                mc.options.backKey.setPressed(false)
                mc.options.leftKey.setPressed(false)
                mc.options.rightKey.setPressed(false)
                mc.options.jumpKey.setPressed(false)
            } else {
                try {
                    val distance = mc.player!!.distanceTo(target!!).toDouble()
                    val targetDist = followDistance.get()
                    if (distance > targetDist + 0.5) {
                        val targetPos = Vec3d(target!!.x, target!!.y, target!!.z)
                        val rotations = getRotationToTarget(targetPos)
                        rotate(rotations[0], rotations[1])
                        mc.options.forwardKey.setPressed(true)
                        if (targetPos.y - mc.player!!.y > 1.0) mc.options.jumpKey.setPressed(true) else mc.options.jumpKey.setPressed(false)
                    } else if (distance < targetDist - 0.5) {
                        mc.options.forwardKey.setPressed(false)
                        mc.options.jumpKey.setPressed(false)
                    } else {
                        mc.options.forwardKey.setPressed(false)
                        mc.options.jumpKey.setPressed(false)
                    }
                } catch (e: Exception) {
                    target = null
                    mc.options.forwardKey.setPressed(false)
                    mc.options.jumpKey.setPressed(false)
                }
            }
        }
    }

    fun setTarget(name: String) {
        targetName.set(name)
        targetMode.set(TargetMode.Specific)
        targetNotFoundWarned = false
        if (isActive) findTarget()
    }

    fun getTarget(): PlayerEntity? { return target }
}
