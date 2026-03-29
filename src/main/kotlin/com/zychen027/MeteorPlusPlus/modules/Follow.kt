package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.utils.xalu.XaluFriends
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.player.ChatUtils
import meteordevelopment.orbit.EventHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.Vec3d
import java.util.*
import kotlin.math.*

/**
 * Follow (自动跟随) - 移植自 XALU
 * 自动跟随玩家，包含反作弊绕过
 * 
 * 反作弊绕过策略：
 * 1. 静默旋转（Silent Rotation）- 只发送旋转包给服务器，不改变客户端视角
 * 2. 按键模拟 - 使用 mc.options 模拟按键而非直接设置速度
 * 3. 距离容差 - 添加 0.5 格容差避免抖动
 * 4. 好友检测 - 可选忽略好友
 */
class Follow : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "Follow",
    "自动跟随玩家，包含反作弊绕过"
) {
    private val sgGeneral = settings.getDefaultGroup()
    private val sgTargeting = settings.createGroup("Targeting")

    // ==================== 目标设置 ====================
    private val targetMode = sgTargeting.add(EnumSetting.Builder<TargetMode>()
        .name("TargetMode")
        .description("如何选择目标")
        .defaultValue(TargetMode.Closest)
        .build())

    private val targetName = sgTargeting.add(StringSetting.Builder()
        .name("TargetName")
        .description("要跟随的特定玩家名字（留空则自动选择）")
        .defaultValue("")
        .visible { targetMode.get() == TargetMode.Specific }
        .build())

    private val targetRange = sgTargeting.add(DoubleSetting.Builder()
        .name("TargetRange")
        .description("搜索目标的最大范围")
        .defaultValue(50.0)
        .min(1.0)
        .sliderMax(100.0)
        .build())

    private val ignoreFriends = sgTargeting.add(BoolSetting.Builder()
        .name("IgnoreFriends")
        .description("忽略好友")
        .defaultValue(true)
        .build())

    private val switchTarget = sgTargeting.add(BoolSetting.Builder()
        .name("SwitchTarget")
        .description("如果有更近的目标则切换")
        .defaultValue(true)
        .build())

    // ==================== 通用设置 ====================
    private val followDistance = sgGeneral.add(DoubleSetting.Builder()
        .name("Distance")
        .description("跟随距离")
        .defaultValue(3.0)
        .min(1.0)
        .sliderMax(10.0)
        .build())

    private val silentRotation = sgGeneral.add(BoolSetting.Builder()
        .name("SilentRotation")
        .description("静默旋转（只发送包给服务器，不改变客户端视角）")
        .defaultValue(true)
        .build())

    private val stopOnSneak = sgGeneral.add(BoolSetting.Builder()
        .name("StopOnSneak")
        .description("潜行时停止跟随")
        .defaultValue(true)
        .build())

    // ==================== 状态变量 ====================
    private var target: PlayerEntity? = null
    private var serverRotation = floatArrayOf(0f, 0f)
    private var savedRotation = floatArrayOf(0f, 0f)
    private var targetNotFoundWarned = false

    override fun onActivate() {
        if (mc.world == null || mc.player == null) {
            toggle()
            return
        }
        savedRotation[0] = mc.player!!.yaw
        savedRotation[1] = mc.player!!.pitch
        serverRotation[0] = mc.player!!.yaw
        serverRotation[1] = mc.player!!.pitch
        targetNotFoundWarned = false
        findTarget()
    }

    override fun onDeactivate() {
        target = null
        targetNotFoundWarned = false
        if (mc.player != null) {
            mc.options.forwardKey.isPressed = false
            mc.options.backKey.isPressed = false
            mc.options.leftKey.isPressed = false
            mc.options.rightKey.isPressed = false
            mc.options.jumpKey.isPressed = false
            mc.player!!.yaw = savedRotation[0]
            mc.player!!.pitch = savedRotation[1]
        }
    }

    override fun getInfoString(): String? {
        return target?.name?.string
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        if (mc.world == null || mc.player == null) return

        // 检查目标是否有效
        if (target == null || !isTargetValid()) {
            target = null
            findTarget()
            if (target == null) {
                mc.options.forwardKey.isPressed = false
                mc.options.jumpKey.isPressed = false
                return
            }
        }

        // 潜行时停止
        if (stopOnSneak.get() && mc.player!!.isSneaking) {
            mc.options.forwardKey.isPressed = false
            mc.options.backKey.isPressed = false
            mc.options.leftKey.isPressed = false
            mc.options.rightKey.isPressed = false
            mc.options.jumpKey.isPressed = false
            return
        }

        try {
            val distance = mc.player!!.distanceTo(target!!)
            val targetDist = followDistance.get()

            if (distance > targetDist + 0.5) {
                // 距离太远，开始跟随
                val targetPos = target!!.pos
                if (targetPos == null) {
                    target = null
                    return
                }
                val rotations = getRotationToTarget(targetPos)
                rotate(rotations[0], rotations[1])
                mc.options.forwardKey.isPressed = true

                // 检查是否需要跳跃
                if (targetPos.y - mc.player!!.y > 1.0) {
                    mc.options.jumpKey.isPressed = true
                } else {
                    mc.options.jumpKey.isPressed = false
                }
            } else if (distance < targetDist - 0.5) {
                // 距离太近，停止
                mc.options.forwardKey.isPressed = false
                mc.options.jumpKey.isPressed = false
            } else {
                // 距离合适，保持
                mc.options.forwardKey.isPressed = false
                mc.options.jumpKey.isPressed = false
            }
        } catch (e: Exception) {
            target = null
            mc.options.forwardKey.isPressed = false
            mc.options.jumpKey.isPressed = false
        }
    }

    private fun findTarget() {
        if (mc.world == null || mc.player == null) return

        if (targetMode.get() == TargetMode.Specific) {
            findSpecificTarget()
        } else {
            findAutoTarget()
        }
    }

    private fun findSpecificTarget() {
        if (mc.world == null || mc.player == null) return

        val name = targetName.get()
        if (name.isEmpty()) {
            if (!targetNotFoundWarned) {
                ChatUtils.info("§c[XALU] 请设置要跟随的玩家名字")
                targetNotFoundWarned = true
            }
            return
        }

        try {
            val players = ArrayList(mc.world!!.players)
            for (player in players) {
                if (player != null && player != mc.player &&
                    (!ignoreFriends.get() || !XaluFriends.isFriend(player))
                ) {
                    if (player.name.string.equals(name, ignoreCase = true)) {
                        target = player
                        targetNotFoundWarned = false
                        ChatUtils.info("§a[XALU] 正在跟随：$name")
                        return
                    }
                }
            }
            if (!targetNotFoundWarned) {
                ChatUtils.info("§c[XALU] 找不到玩家：$name")
                targetNotFoundWarned = true
            }
        } catch (e: Exception) {
            // 忽略异常
        }
    }

    private fun findAutoTarget() {
        if (mc.world == null || mc.player == null) return

        try {
            val players = ArrayList(mc.world!!.players)
            target = players.stream()
                .filter { player: PlayerEntity? -> player != null && player != mc.player }
                .filter { player: PlayerEntity -> player.isAlive }
                .filter { player: PlayerEntity -> player.distanceTo(mc.player!!) <= targetRange.get() }
                .filter { player: PlayerEntity -> !ignoreFriends.get() || !XaluFriends.isFriend(player) }
                .min(getComparator())
                .orElse(null)

            if (target != null) {
                targetNotFoundWarned = false
                ChatUtils.info("§a[XALU] 正在跟随：${target!!.name.string}")
            } else if (!targetNotFoundWarned) {
                ChatUtils.info("§c[XALU] 找不到目标")
                targetNotFoundWarned = true
            }
        } catch (e: Exception) {
            target = null
        }
    }

    private fun getComparator(): Comparator<PlayerEntity> {
        return when (targetMode.get()) {
            TargetMode.Closest -> Comparator.comparingDouble { p: PlayerEntity ->
                try {
                    mc.player!!.distanceTo(p).toDouble()
                } catch (e: Exception) {
                    Double.MAX_VALUE
                }
            }
            TargetMode.MouseClosest -> Comparator.comparingDouble { p: PlayerEntity ->
                try {
                    getDistanceToMouse(p)
                } catch (e: Exception) {
                    Double.MAX_VALUE
                }
            }
            TargetMode.Specific -> Comparator.comparingDouble { p: PlayerEntity ->
                try {
                    mc.player!!.distanceTo(p).toDouble()
                } catch (e: Exception) {
                    Double.MAX_VALUE
                }
            }
        }
    }

    private fun getDistanceToMouse(player: PlayerEntity): Double {
        val playerPos = player.pos ?: return Double.MAX_VALUE
        val eyePos = mc.player!!.eyePos ?: return Double.MAX_VALUE

        val yaw = mc.player!!.yaw
        val pitch = mc.player!!.pitch
        val lookVec = getRotationVector(yaw, pitch)
        val playerToTarget = playerPos.subtract(eyePos).normalize()

        return 1.0 - lookVec.dotProduct(playerToTarget)
    }

    private fun getRotationVector(yaw: Float, pitch: Float): Vec3d {
        val f = pitch * (Math.PI.toFloat() / 180f)
        val g = (-yaw) * (Math.PI.toFloat() / 180f)
        val h = cos(f)
        val i = sin(f)
        val j = cos(g)
        val k = sin(g)
        return Vec3d((j * h).toDouble(), (-i).toDouble(), (k * h).toDouble())
    }

    private fun getRotationToTarget(targetPos: Vec3d): FloatArray {
        if (mc.player == null) {
            return floatArrayOf(0f, 0f)
        }

        val playerPos = mc.player!!.eyePos

        val deltaX = targetPos.x - playerPos.x
        val deltaY = targetPos.y - playerPos.y
        val deltaZ = targetPos.z - playerPos.z
        val distance = sqrt((deltaX * deltaX) + (deltaZ * deltaZ))

        val yaw = Math.toDegrees(atan2(-deltaX, deltaZ)).toFloat()
        val pitch = (-Math.toDegrees(atan2(deltaY, distance))).toFloat()

        return floatArrayOf(yaw, pitch)
    }

    private fun rotate(yaw: Float, pitch: Float) {
        if (mc.player == null) return

        if (silentRotation.get()) {
            silentRotate(yaw, pitch)
        } else {
            mc.player!!.yaw = yaw
            mc.player!!.pitch = pitch
        }
    }

    private fun silentRotate(yaw: Float, pitch: Float) {
        if (mc.player == null) return

        serverRotation[0] = yaw
        serverRotation[1] = pitch

        // 发送旋转包给服务器（静默旋转）
        mc.networkHandler?.sendPacket(
            PlayerMoveC2SPacket.Full(
                mc.player!!.x,
                mc.player!!.y,
                mc.player!!.z,
                yaw,
                pitch,
                mc.player!!.isOnGround,
                mc.player!!.horizontalCollision
            )
        )
    }

    private fun isTargetValid(): Boolean {
        if (target == null || mc.player == null || mc.world == null) {
            return false
        }

        try {
            if (target!!.isAlive && target!!.distanceTo(mc.player!!) <= targetRange.get()) {
                return mc.world!!.players.contains(target)
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

    fun setTarget(name: String?) {
        targetName.set(name)
        targetMode.set(TargetMode.Specific)
        targetNotFoundWarned = false
        if (isActive) {
            findTarget()
        }
    }

    fun getTarget(): PlayerEntity? {
        return target
    }

    fun getServerRotation(): FloatArray {
        return serverRotation.clone()
    }

    enum class TargetMode {
        Closest,
        MouseClosest,
        Specific
    }
}
