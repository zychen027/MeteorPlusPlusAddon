package com.zychen027.meteorplusplus.modules.elytraautopilot

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.modules.elytraautopilot.exceptions.InvalidLocationException
import com.zychen027.meteorplusplus.modules.elytraautopilot.hud.Hud
import com.zychen027.meteorplusplus.modules.elytraautopilot.types.FlyToLocation
import com.zychen027.meteorplusplus.modules.elytraautopilot.utils.ElytraManager
import meteordevelopment.meteorclient.events.render.Render2DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.player.ChatUtils
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.Blocks
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

/**
 * ElytraAutoPilot (鞘翅自动驾驶) - 完整移植自 ElytraAutoPilot mod
 */
class ElytraAutoPilot : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "ElytraAutoPilot",
    "自动鞘翅飞行，支持定点导航和自动降落。使用 .eap 命令控制。"
) {
    private val sgGeneral = settings.getDefaultGroup()
    private val sgFlight = settings.createGroup("飞行配置")
    private val sgLanding = settings.createGroup("降落")
    private val sgSwap = settings.createGroup("物品交换")
    private val sgAdvanced = settings.createGroup("高级")
    private val sgGui = settings.createGroup("HUD 显示")

    // ==================== HUD 显示设置 ====================
    val showGui = sgGui.add(BoolSetting.Builder().name("显示 HUD").defaultValue(true).build())
    val guiScale = sgGui.add(IntSetting.Builder().name("HUD 缩放").defaultValue(100).min(50).sliderMax(200).build())
    val guiX = sgGui.add(IntSetting.Builder().name("HUD X").defaultValue(5).build())
    val guiY = sgGui.add(IntSetting.Builder().name("HUD Y").defaultValue(5).build())
    val showEnabled = sgGui.add(BoolSetting.Builder().name("显示状态").defaultValue(true).build())
    val showAltitude = sgGui.add(BoolSetting.Builder().name("显示高度").defaultValue(true).build())
    val showHeightReq = sgGui.add(BoolSetting.Builder().name("显示最低高度").defaultValue(true).build())
    val showSpeed = sgGui.add(BoolSetting.Builder().name("显示速度").defaultValue(true).build())
    val showAvgSpeed = sgGui.add(BoolSetting.Builder().name("显示平均速度").defaultValue(false).build())
    val showHorizontalSpeed = sgGui.add(BoolSetting.Builder().name("显示水平速度").defaultValue(false).build())
    val showFlyTo = sgGui.add(BoolSetting.Builder().name("显示目标").defaultValue(true).build())
    val showEta = sgGui.add(BoolSetting.Builder().name("显示预计时间").defaultValue(true).build())
    val showAutoLand = sgGui.add(BoolSetting.Builder().name("显示降落状态").defaultValue(true).build())

    // ==================== 飞行配置 ====================
    val maxHeight = sgFlight.add(IntSetting.Builder().name("最大高度").defaultValue(360).min(0).sliderMax(1000).build())
    val minHeight = sgFlight.add(IntSetting.Builder().name("最低高度").defaultValue(180).min(0).sliderMax(500).build())
    val poweredFlight = sgFlight.add(BoolSetting.Builder().name("动力飞行").defaultValue(false).build())

    // ==================== 降落设置 ====================
    val autoLanding = sgLanding.add(BoolSetting.Builder().name("自动降落").defaultValue(true).build())
    val riskyLanding = sgLanding.add(BoolSetting.Builder().name("危险降落").defaultValue(false).build())
    val autoLandSpeed = sgLanding.add(DoubleSetting.Builder().name("降落速度").defaultValue(3.0).min(0.1).sliderMax(10.0).build())

    // ==================== 物品交换 ====================
    val elytraHotswap = sgSwap.add(BoolSetting.Builder().name("鞘翅热交换").defaultValue(true).build())
    val fireworkHotswap = sgSwap.add(BoolSetting.Builder().name("烟花热交换").defaultValue(true).build())
    val elytraAutoSwap = sgSwap.add(BoolSetting.Builder().name("鞘翅自动交换").defaultValue(false).build())
    val elytraReplaceDurability = sgSwap.add(IntSetting.Builder().name("鞘翅更换耐久").defaultValue(20).min(1).sliderMax(100).build())
    val emergencyLand = sgSwap.add(BoolSetting.Builder().name("紧急降落").defaultValue(true).build())

    // ==================== 高级设置 ====================
    val pullUpAngle = sgAdvanced.add(DoubleSetting.Builder().name("上升角度").defaultValue(-46.63).sliderMax(0.0).sliderMin(-90.0).build())
    val pullDownAngle = sgAdvanced.add(DoubleSetting.Builder().name("下降角度").defaultValue(37.20).sliderMax(90.0).sliderMin(0.0).build())
    val pullUpMinVelocity = sgAdvanced.add(DoubleSetting.Builder().name("上升最小速度").defaultValue(1.91).sliderMax(5.0).build())
    val pullDownMaxVelocity = sgAdvanced.add(DoubleSetting.Builder().name("下降最大速度").defaultValue(2.33).sliderMax(5.0).build())
    val pullUpSpeed = sgAdvanced.add(DoubleSetting.Builder().name("上升速度").defaultValue(2.16).sliderMax(10.0).build())
    val pullDownSpeed = sgAdvanced.add(DoubleSetting.Builder().name("下降速度").defaultValue(0.21).sliderMax(10.0).build())
    val turningSpeed = sgAdvanced.add(DoubleSetting.Builder().name("转向速度").defaultValue(3.0).sliderMax(10.0).build())
    val takeOffPull = sgAdvanced.add(DoubleSetting.Builder().name("起飞拉升").defaultValue(10.0).sliderMax(20.0).build())
    val playSoundOnLanding = sgAdvanced.add(StringSetting.Builder().name("降落音效").defaultValue("minecraft:block.note_block.pling").build())

    // ==================== 飞行位置列表 ====================
    private val savedLocations = sgGeneral.add(StringListSetting.Builder().name("保存的位置").build())

    // ==================== 模块状态 ====================
    companion object {
        var INSTANCE: ElytraAutoPilot? = null
            private set

        // 飞行状态
        var autoFlight = false
        var onTakeoff = false
        var isflytoActive = false
        var isLanding = false
        var forceLand = false
        var isChained = false

        // 飞行控制
        var pullUp = false
        var pullDown = false
        var isDescending = false
        var pitchMod = 1.0
        var velHigh = 0.0
        var velLow = 0.0

        // 目标位置
        var argXpos = 0
        var argZpos = 0
        var distance = 0.0

        // 速度计算
        var currentVelocity = 0.0
        var currentVelocityHorizontal = 0.0
        var previousPosition: Vec3d? = null

        // 地面高度
        var groundheight = -1.0
        var calculateHud = false

        // 起飞冷却
        private var takeoffCooldown = 0
        private var jumpPressedTicks = 0
    }

    init {
        INSTANCE = this
    }

    override fun onActivate() {
        resetState()
    }

    override fun onDeactivate() {
        resetState()
        stopMovement()
    }

    private fun resetState() {
        autoFlight = false
        onTakeoff = false
        isflytoActive = false
        isLanding = false
        forceLand = false
        isChained = false
        pullUp = false
        pullDown = false
        isDescending = false
        pitchMod = 1.0
        takeoffCooldown = 0
        jumpPressedTicks = 0
        stopMovement()
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        val player = mc.player ?: return
        val world = mc.world ?: return

        // 检查是否在滑翔
        calculateHud = player.isGliding
        if (!calculateHud) {
            autoFlight = false
            groundheight = -1.0
        } else {
            groundheight = getGroundHeight(player)
        }

        // 起飞冷却处理
        if (takeoffCooldown > 0) {
            takeoffCooldown--
            if (takeoffCooldown == 0) {
                onTakeoff = true
            }
        }

        // 起飞状态处理
        if (onTakeoff) {
            handleTakeoff(player, world)
        }

        // 自动飞行处理
        if (autoFlight && calculateHud) {
            handleAutoFlight(player, world)
        }

        // 计算速度
        if (calculateHud) {
            computeVelocity()
        } else {
            previousPosition = null
            currentVelocity = 0.0
            currentVelocityHorizontal = 0.0
        }
    }

    @EventHandler
    private fun onRender2D(event: Render2DEvent) {
        if (calculateHud && showGui.get()) {
            val player = mc.player ?: return
            Hud.drawHud(event.drawContext, mc.textRenderer, player)
        }
    }

    // ==================== 起飞逻辑 ====================

    fun takeoff(name: String = ""): Boolean {
        if (name.isNotEmpty()) {
            for (loc in savedLocations.get()) {
                try {
                    val location = FlyToLocation.convertStringToLocation(loc)
                    if (location.name.equals(name, ignoreCase = true)) {
                        argXpos = location.x
                        argZpos = location.z
                        isChained = true
                        return startTakeoffSequence()
                    }
                } catch (e: InvalidLocationException) { }
            }
            ChatUtils.info("未找到位置：$name")
            return false
        }
        return startTakeoffSequence()
    }

    private fun startTakeoffSequence(): Boolean {
        val player = mc.player ?: return false
        val world = player.world ?: return false

        // 检查鞘翅
        if (elytraAutoSwap.get()) {
            val elytraSlot = ElytraManager.getElytraIndex(player)
            if (elytraSlot == -100) {
                ChatUtils.info("背包中没有鞘翅！")
                return false
            }
            if (!ElytraManager.equipElytra(player)) {
                ChatUtils.info("装备鞘翅失败！")
                return false
            }
        } else {
            val chestplateSlot = player.getEquippedStack(EquipmentSlot.CHEST)
            if (chestplateSlot.item != Items.ELYTRA) {
                ChatUtils.info("未装备鞘翅！")
                return false
            }
            if (chestplateSlot.damage >= chestplateSlot.maxDamage - 1) {
                ChatUtils.info("鞘翅已损坏！")
                return false
            }
        }

        // 检查烟花
        if (!hasFirework(player)) {
            if (!ElytraManager.tryRestockFirework(player)) {
                ChatUtils.info("需要烟花！")
                return false
            }
        }

        // 检查上方方块
        val topY = world.topYInclusive
        for (y in player.blockPos.y + 2..topY) {
            val blockPos = BlockPos(player.blockPos.x, y, player.blockPos.z)
            if (!world.getBlockState(blockPos).isAir) {
                ChatUtils.info("上方有方块阻挡！")
                return false
            }
        }

        // 开始起飞序列
        takeoffCooldown = 5
        onTakeoff = true
        jumpPressedTicks = 0
        ChatUtils.info("开始起飞...")
        return true
    }

    private fun handleTakeoff(player: net.minecraft.client.network.ClientPlayerEntity, world: net.minecraft.world.World) {
        // 检查是否达到起飞高度
        if (groundheight > minHeight.get()) {
            onTakeoff = false
            autoFlight = true
            isflytoActive = isChained
            isChained = false
            pitchMod = 3.0
            mc.options.useKey.isPressed = false
            mc.options.jumpKey.isPressed = false
            if (isflytoActive) {
                ChatUtils.info("正在飞往：x=$argXpos, z=$argZpos")
            }
            return
        }

        // 进入滑行状态
        if (!player.isGliding) {
            player.networkHandler.sendPacket(
                ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING)
            )
            return
        }

        // 保持跳跃
        mc.options.jumpKey.isPressed = true

        // 检查并使用烟花
        if (!hasFirework(player)) {
            if (!ElytraManager.tryRestockFirework(player)) {
                ChatUtils.info("烟花不足，起飞取消！")
                onTakeoff = false
                mc.options.jumpKey.isPressed = false
                return
            }
        }

        // 使用烟花
        if (player.pitch <= -85f) {
            mc.options.useKey.isPressed = true
        }
    }

    // ==================== 自动飞行逻辑 ====================

    private fun handleAutoFlight(player: net.minecraft.client.network.ClientPlayerEntity, world: net.minecraft.world.World) {
        val altitude = player.y

        // 紧急降落检查
        val durability = ElytraManager.getElytraDurability(player)
        if (emergencyLand.get() && durability < elytraReplaceDurability.get()) {
            forceLand = true
        }

        // 水中/熔岩中停止
        if (player.isTouchingWater || player.isInLava) {
            isflytoActive = false
            isLanding = false
            autoFlight = false
            return
        }

        // 高度控制
        if (isDescending) {
            pullUp = false
            pullDown = true
            if (altitude > maxHeight.get()) velHigh = 0.3
            else if (altitude > maxHeight.get() - 10) velLow = 0.28475
            if (currentVelocity >= pullDownMaxVelocity.get() + maxOf(velHigh, velLow)) {
                isDescending = false
                pullDown = false
                pullUp = true
                pitchMod = 1.0
            }
        } else {
            velHigh = 0.0
            velLow = 0.0
            pullUp = true
            pullDown = false
            if (currentVelocity <= pullUpMinVelocity.get() || altitude > maxHeight.get() - 10) {
                isDescending = true
            }
        }

        // FlyTo 寻路
        if (isflytoActive && !isLanding && !forceLand) {
            handleFlyTo(player)
        }

        // 降落处理
        if (isLanding || forceLand) {
            handleLanding(player)
        }
    }

    private fun handleFlyTo(player: net.minecraft.client.network.ClientPlayerEntity) {
        val playerPos = player.pos
        val dx = argXpos.toDouble() - playerPos.x
        val dz = argZpos.toDouble() - playerPos.z

        // 计算目标朝向
        val targetYaw = MathHelper.wrapDegrees((MathHelper.atan2(dz, dx) * 57.2957763671875).toFloat() - 90.0f)
        var yaw = MathHelper.wrapDegrees(player.yaw)

        // 转向
        val yawDiff = MathHelper.angleBetween(yaw, targetYaw)
        if (kotlin.math.abs(yawDiff) < turningSpeed.get()) {
            player.yaw = targetYaw
        } else {
            player.yaw = MathHelper.wrapDegrees(yaw + kotlin.math.sign(yawDiff) * turningSpeed.get().toFloat())
        }

        // 计算距离
        distance = kotlin.math.sqrt(dx * dx + dz * dz)

        // 到达检测
        if (distance < 25) {
            ChatUtils.info("已到达目标，开始降落...")
            isLanding = true
        }
    }

    private fun handleLanding(player: net.minecraft.client.network.ClientPlayerEntity) {
        if (!forceLand && !autoLanding.get()) {
            isflytoActive = false
            isLanding = false
            return
        }

        isDescending = true
        val fpsDelta = mc.renderTickCounter.dynamicDeltaTicks
        val speedMod = 60.0 / (20.0f / fpsDelta)

        if (riskyLanding.get() && groundheight > 60) {
            // 危险降落：垂直俯冲
            player.pitch = 90f
        } else {
            // 平滑降落
            val targetPitch = when {
                groundheight > 50 -> 50f
                groundheight < 20 -> 30f
                else -> (((groundheight - 20) / 30 * 20 + 30).toFloat())
            }
            player.pitch = (player.pitch + pullDownSpeed.get().toFloat() * 3 * speedMod).toFloat().coerceAtMost(targetPitch)
        }

        // 触地检测
        if (groundheight > 0 && player.y <= groundheight + 2) {
            ChatUtils.info("已降落！")
            autoFlight = false
            isLanding = false
            isflytoActive = false
            mc.options.useKey.isPressed = false
        }
    }

    // ==================== 屏幕更新逻辑 ====================

    @EventHandler
    private fun onScreenTick(event: meteordevelopment.meteorclient.events.render.Render3DEvent) {
        val player = mc.player ?: return
        if (mc.isPaused && mc.isInSingleplayer) return

        val fpsDelta = mc.renderTickCounter.dynamicDeltaTicks
        val fpsResult = 20.0f / fpsDelta
        val speedMod = 60.0 / fpsResult

        // 起飞拉升
        if (onTakeoff && player.pitch > -90f) {
            player.pitch = (player.pitch - takeOffPull.get() * speedMod).toFloat().coerceAtLeast(-90f)
        }

        // 飞行俯仰角控制
        if (autoFlight && calculateHud && !(isLanding || forceLand)) {
            if (pullUp) {
                player.pitch = (player.pitch - pullUpSpeed.get() * speedMod).toFloat()
                    .coerceAtLeast(pullUpAngle.get().toFloat())
                mc.options.useKey.isPressed = poweredFlight.get() && currentVelocity < 1.25
            }
            if (pullDown) {
                player.pitch = (player.pitch + pullDownSpeed.get() * pitchMod * speedMod).toFloat()
                    .coerceAtMost(pullDownAngle.get().toFloat())
                mc.options.useKey.isPressed = poweredFlight.get() && currentVelocity < 1.25
            }
        }
    }

    // ==================== 工具方法 ====================

    private fun hasFirework(player: net.minecraft.entity.player.PlayerEntity): Boolean {
        return player.mainHandStack.item == Items.FIREWORK_ROCKET ||
               player.offHandStack.item == Items.FIREWORK_ROCKET
    }

    private fun computeVelocity() {
        val player = mc.player ?: return
        val newPosition = player.pos

        if (previousPosition != null) {
            val diff = Vec3d(
                newPosition.x - previousPosition!!.x,
                newPosition.y - previousPosition!!.y,
                newPosition.z - previousPosition!!.z
            )
            currentVelocity = diff.length()
            currentVelocityHorizontal = Vec3d(diff.x, 0.0, diff.z).length()
        }

        previousPosition = newPosition
    }

    private fun stopMovement() {
        mc.options.forwardKey.isPressed = false
        mc.options.backKey.isPressed = false
        mc.options.leftKey.isPressed = false
        mc.options.rightKey.isPressed = false
        mc.options.jumpKey.isPressed = false
        mc.options.sneakKey.isPressed = false
        mc.options.useKey.isPressed = false
    }

    private fun getGroundHeight(player: net.minecraft.entity.player.PlayerEntity): Double {
        val world = player.world ?: return -1.0
        val pos = player.blockPos

        for (y in pos.y downTo world.bottomY) {
            val checkPos = BlockPos(pos.x, y, pos.z)
            val state = world.getBlockState(checkPos)
            if (!state.isAir && state.block != Blocks.VOID_AIR &&
                state.block != Blocks.LAVA && state.block != Blocks.WATER
            ) {
                if (world.getBlockState(checkPos.up()).isAir &&
                    world.getBlockState(checkPos.up(2)).isAir
                ) {
                    return (y + 2).toDouble()
                }
            }
        }
        return -1.0
    }

    // ==================== 命令方法 ====================

    fun flyTo(x: Int, z: Int) {
        val player = mc.player ?: return
        if (!player.isGliding) {
            ChatUtils.info("需要先鞘翅飞行！")
            return
        }
        val currentGroundHeight = getGroundHeight(player)
        if (currentGroundHeight <= minHeight.get()) {
            ChatUtils.info("高度不足，需要 ${minHeight.get()} 格以上")
            return
        }
        autoFlight = true
        argXpos = x
        argZpos = z
        isflytoActive = true
        ChatUtils.info("正在飞往：x=$x, z=$z")
    }

    fun flyTo(name: String): Boolean {
        val player = mc.player ?: return false
        if (!player.isGliding) {
            ChatUtils.info("需要先鞘翅飞行！")
            return false
        }
        val currentGroundHeight = getGroundHeight(player)
        for (loc in savedLocations.get()) {
            try {
                val location = FlyToLocation.convertStringToLocation(loc)
                if (location.name.equals(name, ignoreCase = true)) {
                    if (currentGroundHeight <= minHeight.get()) {
                        ChatUtils.info("高度不足，需要 ${minHeight.get()} 格以上")
                        return false
                    }
                    flyTo(location.x, location.z)
                    ChatUtils.info("正在飞往：${location.name}")
                    return true
                }
            } catch (e: InvalidLocationException) { }
        }
        return false
    }

    fun land() {
        if (autoFlight) {
            ChatUtils.info("正在降落...")
            val soundEvent = SoundEvent.of(Identifier.of(playSoundOnLanding.get()))
            mc.player?.playSound(soundEvent, 1.3f, 1f)
            forceLand = true
            mc.options.useKey.isPressed = false
        }
    }

    fun addFlyLocation(name: String, x: Int, z: Int): Boolean {
        val newLocation = "$name;$x;$z"
        for (loc in savedLocations.get().toList()) {
            try {
                val existing = FlyToLocation.convertStringToLocation(loc)
                if (existing.name.equals(name, ignoreCase = true)) return false
            } catch (e: InvalidLocationException) { }
        }
        savedLocations.get().add(newLocation)
        return true
    }

    fun removeFlyLocation(name: String): Boolean {
        for (loc in savedLocations.get().toList()) {
            try {
                val existing = FlyToLocation.convertStringToLocation(loc)
                if (existing.name.equals(name, ignoreCase = true)) {
                    savedLocations.get().remove(loc)
                    return true
                }
            } catch (e: InvalidLocationException) { }
        }
        return false
    }

    fun getFlyLocations(): List<String> = savedLocations.get().toList()

    override fun getInfoString(): String? = when {
        autoFlight && isflytoActive -> "§a 飞行中 - 目标：$argXpos, $argZpos"
        autoFlight && isLanding -> "§c 降落中"
        onTakeoff -> "§e 起飞中"
        else -> null
    }
}
