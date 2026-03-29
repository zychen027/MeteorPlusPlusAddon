package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.utils.xalu.XaluInventoryUtil
import com.zychen027.meteorplusplus.utils.xalu.XaluTimer
import com.zychen027.meteorplusplus.utils.xalu.XaluBlockUtil
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent
import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.render.color.SettingColor
import meteordevelopment.meteorclient.utils.world.BlockUtils
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.Blocks
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import java.util.*

/**
 * SpeedMine+ (加速挖掘) - 移植自 XALU
 * 完整的发包挖掘模块，包含 Grim 反作弊绕过
 * 
 * 反作弊绕过策略：
 * 1. 使用 sequenced packet 绕过序列检测
 * 2. Ground 检测绕过（发送虚假落地包）
 * 3. UsingPause 检测（使用物品时暂停挖掘）
 * 4. 挖掘进度计算（考虑效率、急迫、挖掘疲劳）
 */
class SpeedMinePlus : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "SpeedMine+",
    "加速挖掘，包含完整反作弊绕过"
) {
    private val sgGeneral = settings.getDefaultGroup()
    private val sgRender = settings.createGroup("Render")
    private val sgDebug = settings.createGroup("Debug")

    // ==================== 通用设置 ====================
    private val usingPause = sgGeneral.add(BoolSetting.Builder()
        .name("UsingPause")
        .description("使用物品时暂停挖掘")
        .defaultValue(true)
        .build())

    private val onlyMain = sgGeneral.add(BoolSetting.Builder()
        .name("OnlyMain")
        .description("仅检查主手")
        .defaultValue(true)
        .visible { usingPause.get() }
        .build())

    private val autoSwitch = sgGeneral.add(EnumSetting.Builder<XaluInventoryUtil.SwitchMode>()
        .name("AutoSwitch")
        .description("自动切换工具模式")
        .defaultValue(XaluInventoryUtil.SwitchMode.Silent)
        .build())

    private val range = sgGeneral.add(IntSetting.Builder()
        .name("Range")
        .description("挖掘范围")
        .defaultValue(6)
        .min(0)
        .sliderMax(12)
        .build())

    private val maxBreaks = sgGeneral.add(IntSetting.Builder()
        .name("TryBreakTime")
        .description("最大挖掘尝试次数")
        .defaultValue(3)
        .min(0)
        .sliderMax(10)
        .build())

    private val farCancel = sgGeneral.add(BoolSetting.Builder()
        .name("FarCancel")
        .description("距离过远时取消")
        .defaultValue(true)
        .build())

    private val instantMine = sgGeneral.add(BoolSetting.Builder()
        .name("InstantMine")
        .description("启用瞬间挖掘")
        .defaultValue(true)
        .build())

    private val instantDelay = sgGeneral.add(IntSetting.Builder()
        .name("InstantDelay")
        .description("瞬间挖掘延迟")
        .defaultValue(50)
        .min(0)
        .sliderMax(1000)
        .build())

    private val checkGround = sgGeneral.add(BoolSetting.Builder()
        .name("CheckGround")
        .description("检查是否在地面上")
        .defaultValue(true)
        .build())

    private val bypassGround = sgGeneral.add(BoolSetting.Builder()
        .name("BypassGround")
        .description("绕过地面检测")
        .defaultValue(true)
        .build())

    private val switchTime = sgGeneral.add(IntSetting.Builder()
        .name("SwitchTime")
        .description("切换延迟")
        .defaultValue(100)
        .min(0)
        .sliderMax(1000)
        .build())

    private val mineDelay = sgGeneral.add(IntSetting.Builder()
        .name("MineDelay")
        .description("挖掘延迟")
        .defaultValue(350)
        .min(0)
        .sliderMax(1000)
        .build())

    private val mineDamage = sgGeneral.add(DoubleSetting.Builder()
        .name("Damage")
        .description("挖掘伤害倍率")
        .defaultValue(1.38)
        .sliderMax(2.0)
        .build())

    // ==================== 检查设置 ====================
    private val checkBreakable = sgGeneral.add(BoolSetting.Builder()
        .name("CheckBreakable")
        .defaultValue(true)
        .build())

    private val checkDistance = sgGeneral.add(BoolSetting.Builder()
        .name("CheckDistance")
        .defaultValue(true)
        .build())

    private val checkMaxBreaks = sgGeneral.add(BoolSetting.Builder()
        .name("CheckMaxBreaks")
        .defaultValue(true)
        .build())

    private val checkAir = sgGeneral.add(BoolSetting.Builder()
        .name("CheckAir")
        .defaultValue(true)
        .build())

    private val checkReplaceable = sgGeneral.add(BoolSetting.Builder()
        .name("CheckReplaceable")
        .defaultValue(true)
        .build())

    private val checkWorld = sgGeneral.add(BoolSetting.Builder()
        .name("CheckWorld")
        .defaultValue(true)
        .build())

    private val checkTargetNull = sgGeneral.add(BoolSetting.Builder()
        .name("CheckTargetNull")
        .defaultValue(true)
        .build())

    private val checkStarted = sgGeneral.add(BoolSetting.Builder()
        .name("CheckStarted")
        .defaultValue(true)
        .build())

    private val checkProgressComplete = sgGeneral.add(BoolSetting.Builder()
        .name("CheckProgressComplete")
        .defaultValue(true)
        .build())

    private val checkCompleted = sgGeneral.add(BoolSetting.Builder()
        .name("CheckCompleted")
        .defaultValue(true)
        .build())

    private val checkFallFlying = sgGeneral.add(BoolSetting.Builder()
        .name("CheckFallFlying")
        .defaultValue(true)
        .build())

    private val checkAutoSwitch = sgGeneral.add(BoolSetting.Builder()
        .name("CheckAutoSwitch")
        .defaultValue(true)
        .build())

    private val checkBestSlot = sgGeneral.add(BoolSetting.Builder()
        .name("CheckBestSlot")
        .defaultValue(true)
        .build())

    private val checkInstantDelay = sgGeneral.add(BoolSetting.Builder()
        .name("CheckInstantDelay")
        .defaultValue(true)
        .build())

    private val checkSwitchDelay = sgGeneral.add(BoolSetting.Builder()
        .name("CheckSwitchDelay")
        .defaultValue(true)
        .build())

    private val checkTargetChanged = sgGeneral.add(BoolSetting.Builder()
        .name("CheckTargetChanged")
        .defaultValue(true)
        .build())

    private val checkHardness = sgGeneral.add(BoolSetting.Builder()
        .name("CheckHardness")
        .defaultValue(true)
        .build())

    private val checkCanHarvest = sgGeneral.add(BoolSetting.Builder()
        .name("CheckCanHarvest")
        .defaultValue(true)
        .build())

    private val checkEfficiency = sgGeneral.add(BoolSetting.Builder()
        .name("CheckEfficiency")
        .defaultValue(true)
        .build())

    private val checkHaste = sgGeneral.add(BoolSetting.Builder()
        .name("CheckHaste")
        .defaultValue(true)
        .build())

    private val checkMiningFatigue = sgGeneral.add(BoolSetting.Builder()
        .name("CheckMiningFatigue")
        .defaultValue(true)
        .build())

    private val checkDamage = sgGeneral.add(BoolSetting.Builder()
        .name("CheckDamage")
        .defaultValue(true)
        .build())

    private val checkRender = sgGeneral.add(BoolSetting.Builder()
        .name("CheckRender")
        .defaultValue(true)
        .build())

    private val checkAnimation = sgGeneral.add(BoolSetting.Builder()
        .name("CheckAnimation")
        .defaultValue(true)
        .build())

    private val checkSwingHand = sgGeneral.add(BoolSetting.Builder()
        .name("CheckSwingHand")
        .defaultValue(true)
        .build())

    private val checkSilentSwitch = sgGeneral.add(BoolSetting.Builder()
        .name("CheckSilentSwitch")
        .defaultValue(true)
        .build())

    // ==================== 渲染设置 ====================
    private val animationExp = sgRender.add(DoubleSetting.Builder()
        .name("AnimationExp")
        .defaultValue(3.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 10.0)
        .build())

    private val shapeMode = sgRender.add(EnumSetting.Builder<ShapeMode>()
        .name("ShapeMode")
        .defaultValue(ShapeMode.Both)
        .build())

    private val sideStartColor = sgRender.add(ColorSetting.Builder()
        .name("SideStart")
        .defaultValue(SettingColor(255, 255, 255, 0))
        .build())

    private val sideEndColor = sgRender.add(ColorSetting.Builder()
        .name("SideEnd")
        .defaultValue(SettingColor(255, 255, 255, 50))
        .build())

    private val lineStartColor = sgRender.add(ColorSetting.Builder()
        .name("LineStart")
        .defaultValue(SettingColor(255, 255, 255, 0))
        .build())

    private val lineEndColor = sgRender.add(ColorSetting.Builder()
        .name("LineEnd")
        .defaultValue(SettingColor(255, 255, 255, 255))
        .build())

    // ==================== Debug 设置 ====================
    private val debugClickSide = sgDebug.add(BoolSetting.Builder()
        .name("DebugClickSide")
        .defaultValue(false)
        .build())

    private val debugProgress = sgDebug.add(BoolSetting.Builder()
        .name("DebugProgress")
        .defaultValue(false)
        .build())

    private val debugTarget = sgDebug.add(BoolSetting.Builder()
        .name("DebugTarget")
        .defaultValue(false)
        .build())

    private val debugSwitch = sgDebug.add(BoolSetting.Builder()
        .name("DebugSwitch")
        .defaultValue(false)
        .build())

    // ==================== 状态变量 ====================
    private var render = 1.0
    private var oldSlot = -1
    private val timer = XaluTimer()
    private val mineTimer = XaluTimer()
    private val instantTimer = XaluTimer()
    private var hasSwitch = false

    companion object {
        var maxBreaksCount = 0
        var targetPos: BlockPos? = null
        var progress = 0f
        private var started = false
        private var completed = false
        var selfClickPos: BlockPos? = null
        var publicProgress = 0
    }

    override fun onActivate() {
        maxBreaksCount = 0
        hasSwitch = false
        mineTimer.setMs(999999)
        instantTimer.setMs(999999)
        timer.setMs(999999)
        targetPos = null
        started = false
        progress = 0f
        render = 1.0
    }

    override fun onDeactivate() {
        if (hasSwitch && oldSlot != -1) {
            XaluInventoryUtil.switchToSlot(oldSlot)
            hasSwitch = false
        }
    }

    override fun getInfoString(): String? {
        if (targetPos == null) return null
        val max = getMineTicks(getTool(targetPos!!)).toDouble()
        return if (progress >= max * mineDamage.get()) "§f[100%]" else "§f[$publicProgress%]"
    }

    @EventHandler
    private fun onStartBreakingBlock(event: StartBreakingBlockEvent) {
        if (checkBreakable.get() && !BlockUtils.canBreak(event.blockPos)) return
        event.cancel()

        if (mineTimer.passedMs(mineDelay.get().toLong())) {
            if (!checkTargetChanged.get() || targetPos == null || targetPos != event.blockPos) {
                mineTimer.reset()
                selfClickPos = event.blockPos
                mine(event.blockPos)
            }
        }
    }

    @EventHandler
    private fun onRender(event: Render3DEvent) {
        if (mc.player == null || mc.world == null) return

        // 工具切换延迟
        if (checkSwitchDelay.get() && timer.passedMs(switchTime.get().toLong()) && hasSwitch && autoSwitch.get() == XaluInventoryUtil.SwitchMode.Delay) {
            info("Switching back to slot $oldSlot")
            XaluInventoryUtil.switchToSlot(oldSlot, autoSwitch.get())
            hasSwitch = false
        }

        // 目标为空检查
        if (checkTargetNull.get() && targetPos == null) {
            publicProgress = 0
            return
        }

        if (debugTarget.get()) {
            info("Target: ${targetPos!!.x}, ${targetPos!!.y}, ${targetPos!!.z}")
        }

        // 最大挖掘次数检查
        if (checkMaxBreaks.get() && maxBreaksCount >= maxBreaks.get() * 10) {
            info("Max breaks reached, resetting")
            maxBreaksCount = 0
            targetPos = null
            return
        }

        // 距离检查
        if (checkDistance.get() && farCancel.get() && mc.player!!.eyePos.squaredDistanceTo(targetPos!!.toCenterPos()) > range.get().toDouble()) {
            info("Target too far, cancelling")
            targetPos = null
            return
        }

        val max = getMineTicks(getTool(targetPos!!)).toDouble()
        publicProgress = ((progress / (max * mineDamage.get())) * 100).toInt()

        if (debugProgress.get()) {
            info("Progress: $progress/${max * mineDamage.get()} ($publicProgress%)")
        }

        // 挖掘完成检查
        if (checkProgressComplete.get() && progress >= max * mineDamage.get() && completed) {
            val isAirBlock = checkAir.get() && isAir(targetPos!!)
            val isReplaceableBlock = checkReplaceable.get() && mc.world!!.getBlockState(targetPos!!).isReplaceable

            if (isAirBlock || isReplaceableBlock) {
                maxBreaksCount = 0
            }
            if (!isAirBlock && !isReplaceableBlock && (!usingPause.get() || !checkPause(onlyMain.get()))) {
                maxBreaksCount++
            }
        }

        // 瞬间挖掘
        if (checkCompleted.get() && instantMine.get() && completed) {
            val side = getColor(sideStartColor.get(), sideEndColor.get(), 1.0)
            val line = getColor(lineStartColor.get(), lineEndColor.get(), 1.0)
            event.renderer.box(Box(targetPos!!), side, line, shapeMode.get(), 0)

            val isAirBlock2 = !checkAir.get() || !mc.world!!.isAir(targetPos!!)
            val isReplaceableBlock2 = !checkReplaceable.get() || !mc.world!!.getBlockState(targetPos!!).isReplaceable

            if (isAirBlock2 && isReplaceableBlock2) {
                if (!checkInstantDelay.get() || instantTimer.passedMs(instantDelay.get().toLong())) {
                    info("Instant mining")
                    sendStop()
                    instantTimer.reset()
                    return
                }
                return
            }
        }

        val delta = (System.currentTimeMillis() - System.currentTimeMillis()) / 1000.0

        // 开始挖掘
        if (checkStarted.get() && !started) {
            val clickSide = XaluBlockUtil.getClickSide(targetPos!!)
            if (debugClickSide.get()) {
                info("Click side: ${clickSide?.name ?: "null"}")
            }
            sendStart()
            return
        }

        val damage = mineDamage.get()

        // 地面检查
        if (!checkGround.get() || mc.player!!.isOnGround) {
            progress += (delta * 20.0).toFloat()
        } else if (checkGround.get() && !mc.player!!.isOnGround) {
            progress += (delta * 4.0).toFloat()
        }

        // 渲染动画
        if (checkAnimation.get()) {
            renderAnimation(event, delta, damage)
        }

        // 挖掘完成
        if (checkProgressComplete.get() && progress >= max * damage) {
            info("Mining complete")
            sendStop()
            selfClickPos = null
            completed = true
            if (!instantMine.get()) {
                targetPos = null
            }
        }
    }

    private fun mine(pos: BlockPos) {
        maxBreaksCount = 0
        completed = false
        targetPos = pos
        started = false
        progress = 0f
    }

    private fun sendStart() {
        val clickSide = XaluBlockUtil.getClickSide(targetPos!!)
        if (debugClickSide.get()) {
            info("Sending START_DESTROY_BLOCK with side: ${clickSide?.name ?: "null"}")
        }
        sendSequencedPacket { id ->
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                targetPos!!,
                clickSide,
                id
            )
        }
        if (checkSwingHand.get()) {
            mc.player!!.swingHand(Hand.MAIN_HAND)
        }
        started = true
        progress = 0f
    }

    private fun sendStop() {
        if (usingPause.get() && checkPause(onlyMain.get())) {
            info("Paused due to using item")
            return
        }

        val bestSlot = getTool(targetPos!!)
        if (!hasSwitch) {
            oldSlot = mc.player!!.inventory.selectedSlot
        }

        if (checkAutoSwitch.get() && autoSwitch.get() != XaluInventoryUtil.SwitchMode.None &&
            (!checkBestSlot.get() || bestSlot != -1)
        ) {
            if (debugSwitch.get()) {
                info("Switching to slot $bestSlot (mode: ${autoSwitch.get().name})")
            }
            XaluInventoryUtil.switchToSlot(bestSlot, autoSwitch.get())
            timer.reset()
            hasSwitch = true
        }

        // Grim 地面检测绕过
        if (bypassGround.get() &&
            (!checkFallFlying.get() || !mc.player!!.isGliding) &&
            targetPos != null &&
            (!checkAir.get() || !isAir(targetPos!!))
        ) {
            info("Bypassing ground check")
            mc.networkHandler?.sendPacket(
                PlayerMoveC2SPacket.Full(
                    mc.player!!.x,
                    mc.player!!.y + 1.0E-9,
                    mc.player!!.z,
                    mc.player!!.yaw,
                    mc.player!!.pitch,
                    true,
                    mc.player!!.horizontalCollision
                )
            )
            mc.player!!.onLanding()
        }

        val clickSide = XaluBlockUtil.getClickSide(targetPos!!)
        if (debugClickSide.get()) {
            info("Sending STOP_DESTROY_BLOCK with side: ${clickSide?.name ?: "null"}")
        }

        if (checkSwingHand.get()) {
            mc.player!!.swingHand(Hand.MAIN_HAND)
        }

        sendSequencedPacket { id ->
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                targetPos!!,
                clickSide,
                id
            )
        }

        if (checkSilentSwitch.get() && autoSwitch.get() == XaluInventoryUtil.SwitchMode.Silent && hasSwitch) {
            if (debugSwitch.get()) {
                info("Switching back to slot $oldSlot")
            }
            XaluInventoryUtil.switchToSlot(oldSlot, XaluInventoryUtil.SwitchMode.Silent)
            hasSwitch = false
        }
    }

    private fun sendSequencedPacket(packetCreator: (Int) -> Any) {
        if (mc.networkHandler == null || mc.world == null) return
        // MC 1.21.8: 直接发送包，不使用序列
        mc.networkHandler!!.sendPacket(packetCreator(0) as net.minecraft.network.packet.Packet<*>)
    }

    private fun isAir(breakPos: BlockPos): Boolean {
        return mc.world!!.isAir(breakPos) ||
               (mc.world!!.getBlockState(breakPos).block == Blocks.FIRE && XaluBlockUtil.hasCrystal(breakPos))
    }

    private fun getMineTicks(slot: Int): Float {
        if (checkTargetNull.get() && targetPos == null) return 20f
        if (checkWorld.get() && (mc.world == null || mc.player == null)) return 20f

        val state = mc.world!!.getBlockState(targetPos!!)
        val hardness = state.getHardness(mc.world!!, targetPos!!)

        if (checkHardness.get() && hardness < 0f) return Float.MAX_VALUE
        if (checkHardness.get() && hardness == 0f) return 1f

        val stack = if (slot == -1) ItemStack.EMPTY else mc.player!!.inventory.getStack(slot)
        val canHarvest = if (checkCanHarvest.get()) stack.isSuitableFor(state) else true
        var speed = stack.getMiningSpeedMultiplier(state)

        // 效率附魔
        if (checkEfficiency.get()) {
            val efficiency = XaluInventoryUtil.getEnchantmentLevel(stack, Enchantments.EFFICIENCY)
            if (efficiency > 0 && speed > 1f) {
                speed += (efficiency * efficiency + 1).toFloat()
            }
        }

        // 急迫效果
        if (checkHaste.get() && mc.player!!.hasStatusEffect(StatusEffects.HASTE)) {
            val amp = mc.player!!.getStatusEffect(StatusEffects.HASTE)!!.amplifier
            speed *= 1f + (amp + 1) * 0.2f
        }

        // 挖掘疲劳效果
        if (checkMiningFatigue.get() && mc.player!!.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            val amp = mc.player!!.getStatusEffect(StatusEffects.MINING_FATIGUE)!!.amplifier
            speed *= when (amp) {
                0 -> 0.3f
                1 -> 0.09f
                2 -> 0.0027f
                else -> 0.00081f
            }
        }

        val damage = if (checkDamage.get()) {
            (speed / hardness) / (if (canHarvest) 30f else 100f)
        } else {
            (speed / hardness) / 30f
        }

        if (!checkDamage.get() || damage > 0f) {
            return 1f / damage
        }
        return Float.MAX_VALUE
    }

    private fun renderAnimation(event: Render3DEvent, delta: Double, damage: Double) {
        render = MathHelper.clamp(render + delta * 2, -2.0, 2.0)
        val max = getMineTicks(getTool(targetPos!!)).toDouble()
        var p = 1.0 - Math.pow(1.0 - MathHelper.clamp(progress / (max * damage).toFloat(), 0f, 1f).toDouble(), animationExp.get())
        p = 1 - p

        val size = p / 2
        val box = Box(
            targetPos!!.x + 0.5 - size,
            targetPos!!.y + 0.5 - size,
            targetPos!!.z + 0.5 - size,
            targetPos!!.x + 0.5 + size,
            targetPos!!.y + 0.5 + size,
            targetPos!!.z + 0.5 + size
        )

        val side = getColor(sideStartColor.get(), sideEndColor.get(), p)
        val line = getColor(lineStartColor.get(), lineEndColor.get(), p)

        event.renderer.box(box, side, line, shapeMode.get(), 0)
    }

    private fun getColor(start: SettingColor, end: SettingColor, progress: Double): SettingColor {
        return SettingColor(
            lerp(start.r.toDouble(), end.r.toDouble(), progress).toInt(),
            lerp(start.g.toDouble(), end.g.toDouble(), progress).toInt(),
            lerp(start.b.toDouble(), end.b.toDouble(), progress).toInt(),
            lerp(start.a.toDouble(), end.a.toDouble(), progress).toInt()
        )
    }

    private fun lerp(start: Double, end: Double, d: Double): Int {
        return (start + (end - start) * d).toInt()
    }

    private fun getTool(pos: BlockPos): Int {
        var index = -1
        var currentFastest = 1f

        for (i in 0..8) {
            val stack = mc.player!!.inventory.getStack(i)
            if (!stack.isEmpty) {
                val digSpeed = XaluInventoryUtil.getEnchantmentLevel(stack, Enchantments.EFFICIENCY).toFloat()
                val destroySpeed = stack.getMiningSpeedMultiplier(mc.world!!.getBlockState(pos))
                if (digSpeed + destroySpeed > currentFastest) {
                    currentFastest = digSpeed + destroySpeed
                    index = i
                }
            }
        }

        return index
    }

    private fun checkPause(onlyMain: Boolean): Boolean {
        return mc.options.useKey.isPressed && (!onlyMain || mc.player!!.activeHand == Hand.MAIN_HAND)
    }
}
