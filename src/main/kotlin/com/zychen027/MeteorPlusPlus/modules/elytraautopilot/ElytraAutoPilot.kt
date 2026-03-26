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
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

/**
 * ElytraAutoPilot (鞘翅自动驾驶) - 移植自 ElytraAutoPilot mod
 * 自动鞘翅飞行、定点导航、自动降落
 *
 * 命令：
 * - .eap flyto <x> <z> - 飞往坐标
 * - .eap flyto <名称> - 飞往命名位置
 * - .eap takeoff - 起飞
 * - .eap land - 降落
 * - .eap flylocation set <名称> <x> <z> - 保存位置
 * - .eap flylocation remove <名称> - 删除位置
 * - .eap flylocation list - 列出位置
 */
class ElytraAutoPilot : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "ElytraAutoPilot",
    "自动鞘翅飞行，支持定点导航和自动降落。使用 .eap 命令控制。"
) {
    // 起飞冷却时间 (tick)
    private val TAKEOFF_COOLDOWN_TICKS = 5
    private var takeoffCooldown = 0
    private val sgGeneral = settings.getDefaultGroup()
    private val sgFlight = settings.createGroup("飞行配置")
    private val sgLanding = settings.createGroup("降落")
    private val sgSwap = settings.createGroup("物品交换")
    private val sgAdvanced = settings.createGroup("高级")
    private val sgGui = settings.createGroup("HUD 显示")

    // ==================== HUD 显示设置 ====================
    val showGui = sgGui.add(BoolSetting.Builder()
        .name("显示 HUD")
        .description("是否显示 HUD。")
        .defaultValue(true)
        .build())

    val guiScale = sgGui.add(IntSetting.Builder()
        .name("HUD 缩放")
        .description("HUD 缩放比例。")
        .defaultValue(100)
        .min(50)
        .sliderMax(200)
        .build())

    val guiX = sgGui.add(IntSetting.Builder()
        .name("HUD X")
        .description("HUD X 坐标。")
        .defaultValue(5)
        .build())

    val guiY = sgGui.add(IntSetting.Builder()
        .name("HUD Y")
        .description("HUD Y 坐标。")
        .defaultValue(5)
        .build())

    val showEnabled = sgGui.add(BoolSetting.Builder()
        .name("显示状态")
        .defaultValue(true)
        .build())

    val showAltitude = sgGui.add(BoolSetting.Builder()
        .name("显示高度")
        .defaultValue(true)
        .build())

    val showHeightReq = sgGui.add(BoolSetting.Builder()
        .name("显示最低高度")
        .defaultValue(true)
        .build())

    val showSpeed = sgGui.add(BoolSetting.Builder()
        .name("显示速度")
        .defaultValue(true)
        .build())

    val showAvgSpeed = sgGui.add(BoolSetting.Builder()
        .name("显示平均速度")
        .defaultValue(false)
        .build())

    val showHorizontalSpeed = sgGui.add(BoolSetting.Builder()
        .name("显示水平速度")
        .defaultValue(false)
        .build())

    val showFlyTo = sgGui.add(BoolSetting.Builder()
        .name("显示目标")
        .defaultValue(true)
        .build())

    val showEta = sgGui.add(BoolSetting.Builder()
        .name("显示预计时间")
        .defaultValue(true)
        .build())

    val showAutoLand = sgGui.add(BoolSetting.Builder()
        .name("显示降落状态")
        .defaultValue(true)
        .build())

    // ==================== 飞行配置 ====================
    val maxHeight = sgFlight.add(IntSetting.Builder()
        .name("最大高度")
        .description("飞行时的最大高度。")
        .defaultValue(360)
        .min(0)
        .sliderMax(1000)
        .build())

    val minHeight = sgFlight.add(IntSetting.Builder()
        .name("最低高度")
        .description("飞行所需的最低高度。")
        .defaultValue(180)
        .min(0)
        .sliderMax(500)
        .build())

    // ==================== 降落设置 ====================
    val autoLanding = sgLanding.add(BoolSetting.Builder()
        .name("自动降落")
        .description("到达目标后自动降落。")
        .defaultValue(true)
        .build())

    val autoLandSpeed = sgLanding.add(DoubleSetting.Builder()
        .name("降落速度")
        .description("自动降落时的转向速度。")
        .defaultValue(3.0)
        .min(0.1)
        .sliderMax(10.0)
        .build())

    val turningSpeed = sgLanding.add(DoubleSetting.Builder()
        .name("转向速度")
        .description("飞行时的转向速度。")
        .defaultValue(3.0)
        .min(0.1)
        .sliderMax(10.0)
        .build())

    // ==================== 物品交换 ====================
    val elytraHotswap = sgSwap.add(BoolSetting.Builder()
        .name("鞘翅热交换")
        .description("低耐久时自动更换鞘翅。")
        .defaultValue(true)
        .build())

    val emergencyLand = sgSwap.add(BoolSetting.Builder()
        .name("紧急降落")
        .description("低耐久或烟花不足时紧急降落。")
        .defaultValue(true)
        .build())

    val elytraReplaceDurability = sgSwap.add(IntSetting.Builder()
        .name("鞘翅更换耐久")
        .description("低于此耐久时更换鞘翅。")
        .defaultValue(20)
        .min(1)
        .sliderMax(100)
        .build())

    // ==================== 飞行位置列表 ====================
    private val savedLocations = sgGeneral.add(StringListSetting.Builder()
        .name("保存的位置")
        .description("保存的飞行位置。")
        .build())

    // ==================== 模块状态 (companion object) ====================
    companion object {
        var calculateHud = false
        var autoFlight = false
        var previousPosition: Vec3d? = null
        var currentVelocity = 0.0
        var currentVelocityHorizontal = 0.0

        var isDescending = false
        var pullUp = false
        var pullDown = false

        var velHigh = 0.0
        var velLow = 0.0

        var argXpos = 0
        var argZpos = 0
        var isChained = false
        var isflytoActive = false
        var forceLand = false
        var isLanding = false

        var onTakeoff = false
        var pitchMod = 1.0
        var groundheight = -1.0
        var distance = 0.0

        // 模块实例引用
        var INSTANCE: ElytraAutoPilot? = null
            private set
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
        isDescending = false
        pullUp = false
        pullDown = false
        isLanding = false
        forceLand = false
        isflytoActive = false
        pitchMod = 1.0
        velHigh = 0.0
        velLow = 0.0
        distance = 0.0
    }

    @EventHandler
    private fun onTick(event: TickEvent.Post) {
        val player = mc.player ?: return
        val world = mc.world ?: return

        // 更新 HUD 显示状态
        calculateHud = player.isGliding

        if (!calculateHud) {
            autoFlight = false
            groundheight = -1.0
        } else {
            groundheight = getGroundHeight(player)
        }

        // 处理起飞冷却
        if (takeoffCooldown > 0) {
            takeoffCooldown--
            if (takeoffCooldown == 0) {
                onTakeoff = true
            }
        }

        // 处理起飞状态
        if (onTakeoff) {
            handleTakeoff(player)
        }

        // 处理自动飞行状态
        if (autoFlight) {
            handleAutoFlight(player)
        } else {
            resetFlightState()
        }

        // 计算速度
        if (calculateHud) {
            computeVelocity()
        } else {
            previousPosition = null
        }

        // 计算到目标的距离
        if (isflytoActive) {
            val playerPos = player.pos
            val targetPos = Vec3d(argXpos.toDouble(), playerPos.y, argZpos.toDouble())
            val dx = playerPos.x - targetPos.x
            val dz = playerPos.z - targetPos.z
            distance = kotlin.math.sqrt(dx * dx + dz * dz)
        }
    }

    /**
     * 处理起飞逻辑（参考原始模组）
     */
    private fun handleTakeoff(player: net.minecraft.client.network.ClientPlayerEntity) {
        val pitch = player.pitch

        // 抬头到 -90 度（垂直向上）
        if (pitch > -90f) {
            player.pitch = (pitch - 10f).coerceAtLeast(-90f)
            // 发送旋转数据包
            mc.networkHandler?.sendPacket(
                PlayerMoveC2SPacket.Full(
                    player.x, player.y, player.z,
                    player.yaw, player.pitch,
                    player.isOnGround, player.horizontalCollision
                )
            )
        }

        // 达到 -90 度后，使用烟花加速
        if (pitch <= -90f) {
            player.pitch = -90f

            // 检查是否有烟花
            val itemMain = player.mainHandStack.item
            val itemOff = player.offHandStack.item
            val hasFirework = (itemMain == Items.FIREWORK_ROCKET || itemOff == Items.FIREWORK_ROCKET)

            if (!hasFirework) {
                // 尝试从背包补充烟花
                if (!tryRestockFirework(player)) {
                    mc.options.useKey.isPressed = false
                    mc.options.jumpKey.isPressed = false
                    mc.options.forwardKey.isPressed = false
                    onTakeoff = false
                    ChatUtils.info("§c 烟花不足，起飞取消")
                    return
                }
            } else {
                // 使用烟花
                mc.options.useKey.isPressed = (currentVelocity < 0.75f)
            }

            // 检查是否达到起飞高度
            if (groundheight >= minHeight.get()) {
                // 起飞成功，进入自动飞行模式
                onTakeoff = false
                mc.options.useKey.isPressed = false
                mc.options.jumpKey.isPressed = false
                autoFlight = true
                pitchMod = 3.0

                if (isChained) {
                    isflytoActive = true
                    isChained = false
                    ChatUtils.info("§a 正在飞往：x=$argXpos, z=$argZpos")
                }
                return
            }
        }

        // 起飞过程中按住前进键
        mc.options.forwardKey.isPressed = true
    }

    /**
     * 处理自动飞行逻辑（参考原始模组）
     */
    private fun handleAutoFlight(player: net.minecraft.client.network.ClientPlayerEntity) {
        val altitude = player.y
        val pitch = player.pitch

        // 紧急降落检查
        val durability = ElytraManager.getElytraDurability(player)
        if (emergencyLand.get() && durability < elytraReplaceDurability.get()) {
            forceLand = true
        }

        // 水中或熔岩中停止
        if (player.isTouchingWater || player.isInLava) {
            isflytoActive = false
            isLanding = false
            autoFlight = false
            mc.options.forwardKey.isPressed = false
            return
        }

        // 处理寻路/降落状态
        if (isflytoActive || forceLand) {
            if (isLanding || forceLand) {
                // 降落逻辑
                if (!forceLand && !autoLanding.get()) {
                    isflytoActive = false
                    isLanding = false
                    mc.options.forwardKey.isPressed = false
                    return
                }
                isDescending = true
                handleLanding(player)
            } else {
                // 寻路转向逻辑
                handleFlyTo(player)
            }
        }

        // 飞行高度控制（上升/下降循环）
        if (!isLanding && !forceLand) {
            if (isDescending) {
                pullUp = false
                pullDown = true
                if (altitude > maxHeight.get()) {
                    velHigh = 0.3
                } else if (altitude > maxHeight.get() - 10) {
                    velLow = 0.28475
                }
                val velMod = maxOf(velHigh, velLow)
                if (currentVelocity >= 2.33 + velMod) {
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
                if (currentVelocity <= 1.91 || altitude > maxHeight.get() - 10) {
                    isDescending = true
                    pullDown = true
                    pullUp = false
                }
            }

            // 应用俯仰角控制
            if (pullUp) {
                player.pitch = (pitch - 2f).coerceAtLeast(-46.63f)
            }
            if (pullDown) {
                player.pitch = (pitch + 2f * pitchMod.toFloat()).coerceAtMost(37.20f)
            }
        }

        // 关键：按住前进键让玩家向前飞行
        mc.options.forwardKey.isPressed = true
    }

    /**
     * 处理寻路转向
     */
    private fun handleFlyTo(player: net.minecraft.client.network.ClientPlayerEntity) {
        val playerPos = player.pos
        val f = argXpos.toDouble() - playerPos.x
        val d = argZpos.toDouble() - playerPos.z

        // 计算目标朝向
        val targetYaw = MathHelper.wrapDegrees((MathHelper.atan2(d, f) * 57.2957763671875).toFloat() - 90.0f)
        var yaw = MathHelper.wrapDegrees(player.yaw)

        // 平滑转向
        val yawDiff = MathHelper.wrapDegrees(targetYaw - yaw)
        val turningSpeedVal = turningSpeed.get().toFloat()
        if (kotlin.math.abs(yawDiff) < turningSpeedVal * 2) {
            player.yaw = targetYaw
        } else {
            val clampedDiff = yawDiff.coerceIn(-turningSpeedVal, turningSpeedVal)
            player.yaw = yaw + clampedDiff
        }

        // 检查是否到达目的地
        distance = kotlin.math.sqrt(f * f + d * d)
        if (distance < 20) {
            ChatUtils.info("§b 已到达目的地，开始降落")
            player.playSound(
                SoundEvent.of(Identifier.of("minecraft:block.note_block.pling")),
                1.3f, 1f
            )
            isLanding = true
        }
    }

    /**
     * 处理降落逻辑
     */
    private fun handleLanding(player: net.minecraft.client.network.ClientPlayerEntity) {
        val pitch = player.pitch
        val fallPitchMax = 50f
        val fallPitchMin = 30f
        val fallPitch: Float = when {
            groundheight > 50 -> fallPitchMax
            groundheight < 20 -> fallPitchMin
            else -> ((groundheight - 20) / 30 * 20 + fallPitchMin).toFloat()
        }

        pitchMod = 3.0
        player.yaw = (pitch + autoLandSpeed.get().toFloat()).coerceAtMost(360f)
        player.pitch = (pitch + 2f * pitchMod.toFloat()).coerceAtMost(fallPitch)
    }

    /**
     * 重置飞行状态
     */
    private fun resetFlightState() {
        velHigh = 0.0
        velLow = 0.0
        isLanding = false
        forceLand = false
        isflytoActive = false
        pullUp = false
        pitchMod = 1.0
        pullDown = false
        mc.options.forwardKey.isPressed = false
    }

    @EventHandler
    private fun onRender2D(event: Render2DEvent) {
        if (calculateHud) {
            val player = mc.player ?: return
            Hud.drawHud(event.drawContext, mc.textRenderer, player)
        }
    }

    // ==================== 命令调用的公共方法 ====================

    /**
     * 飞往坐标
     */
    fun flyTo(x: Int, z: Int) {
        val player = mc.player ?: return
        if (!player.isGliding) {
            ChatUtils.info("需要先鞘翅飞行！")
            return
        }
        // 检查玩家的海拔高度（Y 坐标）
        val playerAltitude = player.y
        if (playerAltitude < minHeight.get()) {
            ChatUtils.info("高度不足，需要 ${minHeight.get()} 格以上 (当前 Y=${playerAltitude.toInt()})")
            return
        }
        autoFlight = true
        argXpos = x
        argZpos = z
        isflytoActive = true
        isChained = false
        ChatUtils.info("正在飞往：x=$x, z=$z")
    }

    /**
     * 飞往命名位置
     */
    fun flyTo(name: String): Boolean {
        val player = mc.player ?: return false
        if (!player.isGliding) {
            ChatUtils.info("需要先鞘翅飞行！")
            return false
        }
        // 检查玩家的海拔高度（Y 坐标）
        val playerAltitude = player.y
        for (loc in savedLocations.get()) {
            try {
                val location = FlyToLocation.convertStringToLocation(loc)
                if (location.name.equals(name, ignoreCase = true)) {
                    if (playerAltitude < minHeight.get()) {
                        ChatUtils.info("高度不足，需要 ${minHeight.get()} 格以上 (当前 Y=${playerAltitude.toInt()})")
                        return false
                    }
                    flyTo(location.x, location.z)
                    ChatUtils.info("正在飞往：${location.name} (x=${location.x}, z=${location.z})")
                    return true
                }
            } catch (e: InvalidLocationException) {
                // 忽略无效位置
            }
        }
        return false
    }

    /**
     * 起飞（可带位置名称）
     */
    fun takeoff(name: String = ""): Boolean {
        val player = mc.player ?: return false

        if (name.isNotEmpty()) {
            // 带位置名称的起飞
            for (loc in savedLocations.get()) {
                try {
                    val location = FlyToLocation.convertStringToLocation(loc)
                    if (location.name.equals(name, ignoreCase = true)) {
                        argXpos = location.x
                        argZpos = location.z
                        isChained = true
                        startTakeoff()
                        return true
                    }
                } catch (e: InvalidLocationException) {
                    // 忽略无效位置
                }
            }
            return false
        } else {
            startTakeoff()
            return true
        }
    }

    /**
     * 开始起飞流程（参考原始模组）
     */
    private fun startTakeoff() {
        val player = mc.player ?: return

        // 检查鞘翅
        val chestplateSlot = ElytraManager.getChestplateSlot(player)
        if (chestplateSlot.item != Items.ELYTRA) {
            ChatUtils.info("§c 未装备鞘翅！")
            return
        }

        val elytraDamage = chestplateSlot.maxDamage - chestplateSlot.damage
        if (elytraDamage <= 1) {
            ChatUtils.info("§c 鞘翅已损坏！")
            return
        }

        // 检查烟花
        val itemMain = player.mainHandStack.item
        val itemOff = player.offHandStack.item
        val hasFirework = (itemMain == Items.FIREWORK_ROCKET || itemOff == Items.FIREWORK_ROCKET)

        if (!hasFirework) {
            // 尝试从背包补充烟花到手中
            if (!tryRestockFirework(player)) {
                ChatUtils.info("§c 需要烟花！")
                return
            }
        }

        // 检查上方是否有方块（检查玩家头顶 2-10 格）
        val world = player.world
        for (y in 2..10) {
            val blockPos = player.blockPos.up(y)
            val state = world.getBlockState(blockPos)
            if (!state.isAir && state.block != Blocks.VOID_AIR) {
                ChatUtils.info("§c 上方有方块阻挡！")
                return
            }
        }

        // 设置起飞冷却，冷却结束后进入 onTakeoff 状态
        takeoffCooldown = TAKEOFF_COOLDOWN_TICKS
        mc.options.jumpKey.isPressed = true
        ChatUtils.info("§e 正在起飞...")
    }

    /**
     * 尝试从背包补充烟花到手中（参考原始模组）
     */
    private fun tryRestockFirework(player: net.minecraft.entity.player.PlayerEntity): Boolean {
        // 在背包主手槽位中查找烟花
        for (i in 0 until player.inventory.size()) {
            val stack = player.inventory.getStack(i)
            if (stack.item == Items.FIREWORK_ROCKET) {
                val handSlot = if (player.offHandStack.isEmpty) 45 else 36 + player.inventory.selectedSlot

                mc.interactionManager?.clickSlot(
                    player.playerScreenHandler.syncId,
                    handSlot,
                    i,
                    SlotActionType.SWAP,
                    player
                )
                return true
            }
        }
        return false
    }

    /**
     * 降落
     */
    fun land() {
        if (autoFlight) {
            ChatUtils.info("§b 正在降落...")
            forceLand = true
            mc.options.useKey.isPressed = false
        }
    }

    /**
     * 添加飞行位置
     */
    fun addFlyLocation(name: String, x: Int, z: Int): Boolean {
        val newLocation = "$name;$x;$z"
        for (loc in savedLocations.get().toList()) {
            try {
                val existing = FlyToLocation.convertStringToLocation(loc)
                if (existing.name.equals(name, ignoreCase = true)) {
                    return false
                }
            } catch (e: InvalidLocationException) {
                // 忽略无效位置
            }
        }
        savedLocations.get().add(newLocation)
        return true
    }

    /**
     * 移除飞行位置
     */
    fun removeFlyLocation(name: String): Boolean {
        for (loc in savedLocations.get().toList()) {
            try {
                val existing = FlyToLocation.convertStringToLocation(loc)
                if (existing.name.equals(name, ignoreCase = true)) {
                    savedLocations.get().remove(loc)
                    return true
                }
            } catch (e: InvalidLocationException) {
                savedLocations.get().remove(loc)
            }
        }
        return false
    }

    /**
     * 获取飞行位置列表
     */
    fun getFlyLocations(): List<String> {
        return savedLocations.get().toList()
    }

    // ==================== 内部逻辑 ====================

    private fun computeVelocity() {
        val player = mc.player ?: return
        val newPosition = player.pos

        if (previousPosition == null) {
            previousPosition = newPosition
            return
        }

        val difference = Vec3d(
            newPosition.x - previousPosition!!.x,
            newPosition.y - previousPosition!!.y,
            newPosition.z - previousPosition!!.z
        )
        val differenceHorizontal = Vec3d(
            newPosition.x - previousPosition!!.x,
            0.0,
            newPosition.z - previousPosition!!.z
        )
        previousPosition = newPosition

        currentVelocity = difference.length()
        currentVelocityHorizontal = differenceHorizontal.length()
    }

    private fun stopMovement() {
        mc.options.forwardKey.isPressed = false
        mc.options.backKey.isPressed = false
        mc.options.leftKey.isPressed = false
        mc.options.rightKey.isPressed = false
        mc.options.jumpKey.isPressed = false
        mc.options.sneakKey.isPressed = false
    }

    /**
     * 计算玩家距离地面的高度
     * @return 玩家脚下到地面的距离，如果无法计算返回 -1
     */
    private fun getGroundHeight(player: net.minecraft.entity.player.PlayerEntity): Double {
        val world = player.world ?: return -1.0
        val pos = player.blockPos
        val playerY = pos.y

        // 从玩家脚下向下搜索，最多检查 500 格
        for (y in playerY downTo maxOf(world.bottomY, playerY - 500)) {
            val checkPos = net.minecraft.util.math.BlockPos(pos.x, y, pos.z)
            val state = world.getBlockState(checkPos)
            
            // 跳过空气、虚空、熔岩和水
            if (state.isAir || state.block == Blocks.VOID_AIR || 
                state.block == Blocks.LAVA || state.block == Blocks.WATER) {
                continue
            }
            
            // 找到固体方块，检查上方是否有 2 格空间
            val groundY = y
            val spaceAbove1 = world.getBlockState(net.minecraft.util.math.BlockPos(pos.x, groundY + 1, pos.z))
            val spaceAbove2 = world.getBlockState(net.minecraft.util.math.BlockPos(pos.x, groundY + 2, pos.z))
            
            if (spaceAbove1.isAir && spaceAbove2.isAir) {
                return (groundY + 2 - playerY).toDouble()
            }
        }
        
        // 没找到地面，返回 -1
        return -1.0
    }

    override fun getInfoString(): String? {
        return when {
            autoFlight -> "§a 飞行中"
            onTakeoff -> "§e 起飞中"
            isLanding -> "§c 降落中"
            else -> null
        }
    }
}
