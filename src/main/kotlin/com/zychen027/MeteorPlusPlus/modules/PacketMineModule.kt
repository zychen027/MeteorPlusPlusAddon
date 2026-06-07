package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.utils.entity.EntityUtil
import com.zychen027.meteorplusplus.utils.entity.InventoryUtil
import com.zychen027.meteorplusplus.utils.entity.InventoryUtil.MineSwitchMode
import com.zychen027.meteorplusplus.utils.math.Timer
import com.zychen027.meteorplusplus.utils.world.BlockUtil
import com.zychen027.meteorplusplus.utils.world.BlockPosX
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent
import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.render.color.SettingColor
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.Blocks
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import java.util.TimerTask
import java.util.function.IntFunction

/**
 * PacketMine (发包挖掘) - 移植自 LeavesHack
 * 针对 Grim 反作弊优化绕过逻辑，支持双挖、FastBypass、虚假落地等
 */
class PacketMineModule : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "PacketMine",
    "发包挖掘，针对 Grim 反作弊优化"
) {
    private val sgGeneral = settings.getDefaultGroup()
    private val sgBypass = settings.createGroup("Bypass")
    private val sgRender = settings.createGroup("Render")

    // ==================== 通用设置 ====================
    private val range = sgGeneral.add(DoubleSetting.Builder()
        .name("range")
        .description("挖掘范围（Grim FarBreak 限制：不超过 4.5）")
        .defaultValue(4.5)
        .min(1.0).sliderMax(6.0)
        .build())

    private val maxBreaks = sgGeneral.add(IntSetting.Builder()
        .name("max-breaks")
        .description("最大挖掘次数")
        .defaultValue(3)
        .min(1).sliderMax(10)
        .build())

    private val instantMine = sgGeneral.add(BoolSetting.Builder()
        .name("instant-mine")
        .description("瞬间挖掘（完成后继续发包）")
        .defaultValue(true)
        .build())

    private val usingPause = sgGeneral.add(BoolSetting.Builder()
        .name("using-pause")
        .description("使用时暂停挖掘（绕过 FastBreak）")
        .defaultValue(true)
        .build())

    private val onlyMain = sgGeneral.add(BoolSetting.Builder()
        .name("only-main")
        .description("仅主手使用时暂停")
        .defaultValue(true)
        .visible { usingPause.get() }
        .build())

    // ==================== Grim 绕过设置 ====================
    private val checkGround = sgBypass.add(BoolSetting.Builder()
        .name("check-ground")
        .description("检查是否在地面上（Grim 检测）")
        .defaultValue(true)
        .build())

    private val bypassGround = sgBypass.add(BoolSetting.Builder()
        .name("bypass-ground")
        .description("绕过地面检测（发送虚假落地包）")
        .defaultValue(true)
        .visible { checkGround.get() }
        .build())

    private val mineDelay = sgBypass.add(IntSetting.Builder()
        .name("mine-delay")
        .description("挖掘延迟 ms（Grim FastBreak: 275ms 阈值）")
        .defaultValue(350)
        .min(0).sliderMax(1000)
        .build())

    private val mineDamage = sgBypass.add(DoubleSetting.Builder()
        .name("damage")
        .description("挖掘伤害倍率（Grim FastBreak 绕过）")
        .defaultValue(0.9)
        .min(0.1).sliderMax(2.0)
        .build())

    private val switchTime = sgBypass.add(IntSetting.Builder()
        .name("switch-time")
        .description("工具切换延迟 ms")
        .defaultValue(100)
        .min(0).sliderMax(1000)
        .build())

    private val instantDelay = sgBypass.add(IntSetting.Builder()
        .name("instant-delay")
        .description("瞬间挖掘延迟 ms")
        .defaultValue(50)
        .min(0).sliderMax(1000)
        .visible { instantMine.get() }
        .build())

    private val autoSwitch = sgBypass.add(EnumSetting.Builder<MineSwitchMode>()
        .name("auto-switch")
        .description("自动切换工具")
        .defaultValue(MineSwitchMode.Silent)
        .build())

    // ==================== LeavesHack 风格扩展 ====================
    private val farCancel = sgGeneral.add(BoolSetting.Builder()
        .name("far-cancel")
        .description("过远取消")
        .defaultValue(true)
        .build())

    private val swing = sgGeneral.add(BoolSetting.Builder()
        .name("swing")
        .description("挥手")
        .defaultValue(true)
        .build())

    private val fastBypass = sgBypass.add(BoolSetting.Builder()
        .name("fast-bypass")
        .description("快速挖掘绕过(发送非法坐标Start包)")
        .defaultValue(true)
        .build())

    private val doubleBreak = sgBypass.add(BoolSetting.Builder()
        .name("double-break")
        .description("双挖")
        .defaultValue(false)
        .build())

    private val switchDamage = sgBypass.add(IntSetting.Builder()
        .name("switch-damage")
        .description("自动切镐挖掘进度阈值")
        .defaultValue(95)
        .min(0).sliderMax(100)
        .build())

    private val packetDelay = sgBypass.add(IntSetting.Builder()
        .name("packet-delay")
        .description("绕过包发送延迟 ms")
        .defaultValue(0)
        .min(0).sliderMax(1000)
        .build())

    // ==================== 渲染设置 ====================
    private val shapeMode = sgRender.add(EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build())

    private val sideStartColor = sgRender.add(ColorSetting.Builder()
        .name("side-start")
        .defaultValue(SettingColor(255, 255, 255, 0))
        .build())

    private val sideEndColor = sgRender.add(ColorSetting.Builder()
        .name("side-end")
        .defaultValue(SettingColor(255, 255, 255, 50))
        .build())

    private val lineStartColor = sgRender.add(ColorSetting.Builder()
        .name("line-start")
        .defaultValue(SettingColor(255, 255, 255, 0))
        .build())

    private val lineEndColor = sgRender.add(ColorSetting.Builder()
        .name("line-end")
        .defaultValue(SettingColor(255, 255, 255, 255))
        .build())

    private val secondSideStartColor = sgRender.add(ColorSetting.Builder()
        .name("second-side-start")
        .defaultValue(SettingColor(255, 255, 255, 0))
        .build())

    private val secondSideEndColor = sgRender.add(ColorSetting.Builder()
        .name("second-side-end")
        .defaultValue(SettingColor(255, 255, 255, 50))
        .build())

    private val secondLineStartColor = sgRender.add(ColorSetting.Builder()
        .name("second-line-start")
        .defaultValue(SettingColor(255, 255, 255, 0))
        .build())

    private val secondLineEndColor = sgRender.add(ColorSetting.Builder()
        .name("second-line-end")
        .defaultValue(SettingColor(255, 255, 255, 255))
        .build())

    private val animationExp = sgRender.add(DoubleSetting.Builder()
        .name("animation-exp")
        .description("渲染动画指数")
        .defaultValue(3.0)
        .min(0.0).sliderMax(10.0)
        .build())

    // ==================== 状态变量 ====================
    private var targetPos: BlockPos? = null
    private var secondPos: BlockPos? = null
    private var started = false
    private var secondStarted = false
    private var completed = false
    private var progress = 0.0f
    private var secondProgress = 0.0f
    private var renderProgressVal = 1.0
    private var secondRender = 1.0
    private var lastTime = 0L
    private var secondLastTime = 0L
    private var oldSlot = -1
    private var hasSwitch = false
    private var secondHasSwitch = false
    private var maxBreaksCount = 0
    private var publicProgress = 0
    private var secondPublicProgress = 0
    private var selfClickPos: BlockPos? = null

    private val timer = Timer()
    private val secondTimer = Timer()
    private val mineTimer = Timer()
    private val instantTimer = Timer()
    private val switchTimer = Timer()

    // ==================== 反射工具 ====================
    // 由于 Yarn 映射下 sendSequencedPacket 是 private，使用反射强制调用
    private val sendSequencedPacketMethod by lazy {
        try {
            ClientPlayerInteractionManager::class.java.getDeclaredMethod(
                "sendSequencedPacket",
                ClientWorld::class.java,
                IntFunction::class.java
            )?.apply { isAccessible = true }
        } catch (e: Exception) {
            null
        }
    }

    private fun sendSequencedPacket(creator: (Int) -> PlayerActionC2SPacket) {
        if (sendSequencedPacketMethod != null && mc.world != null && mc.interactionManager != null) {
            sendSequencedPacketMethod!!.invoke(mc.interactionManager, mc.world, IntFunction { creator(it) })
        } else {
            // 如果反射失败，回退到不带序列号的发包（可能会被Grim拦截，但至少不会崩溃）
            InventoryUtil.sendPacket(creator(0))
        }
    }

    override fun onActivate() {
        resetState()
    }

    override fun onDeactivate() {
        if (hasSwitch) {
            InventoryUtil.switchToSlot(oldSlot)
            hasSwitch = false
        }
        if (secondHasSwitch) {
            InventoryUtil.switchToSlot(oldSlot)
            secondHasSwitch = false
        }
        targetPos = null
        secondPos = null
    }

    private fun resetState() {
        maxBreaksCount = 0
        hasSwitch = false
        secondHasSwitch = false
        mineTimer.setMs(999999)
        instantTimer.setMs(999999)
        timer.setMs(999999)
        secondTimer.setMs(999999)
        targetPos = null
        secondPos = null
        started = false
        secondStarted = false
        publicProgress = 0
        secondPublicProgress = 0
        progress = 0f
        secondProgress = 0f
        lastTime = System.currentTimeMillis()
        secondLastTime = System.currentTimeMillis()
        renderProgressVal = 1.0
        secondRender = 1.0
    }

    @EventHandler
    private fun onStartBreakingBlock(event: StartBreakingBlockEvent) {
        if (!canBreak(event.blockPos)) return
        event.cancel()
        if (!mineTimer.passedMs(mineDelay.get().toLong())) return
        selfClickPos = event.blockPos
        mine(event.blockPos)
    }

    private fun mine(pos: BlockPos) {
        mineTimer.reset()
        maxBreaksCount = 0

        if (doubleBreak.get()) {
            if (targetPos != null && secondPos == null && targetPos != pos) {
                if (completed) {
                    if (mineDelay.get() > 0) {
                        mineTimer.reset()
                        targetPos = null
                        publicProgress = 0
                        started = false
                        progress = 0f
                        completed = false
                        return
                    }
                    targetPos = pos
                    secondStarted = false
                    secondProgress = 0f
                    secondPublicProgress = 0
                    publicProgress = 0
                    started = false
                    progress = 0f
                    completed = false
                } else {
                    secondPos = targetPos
                    targetPos = pos
                    secondStarted = false
                    secondProgress = 0f
                    secondPublicProgress = 0
                    started = false
                }
            } else if (targetPos == null || targetPos != pos) {
                publicProgress = 0
                targetPos = pos
                started = false
                progress = 0f
                completed = false
            }
        } else {
            if (pos != targetPos) {
                publicProgress = 0
                targetPos = pos
                started = false
                progress = 0f
                completed = false
            }
        }
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        if (mc.player == null || mc.world == null) return

        // 检查挖掘次数
        if (maxBreaksCount >= maxBreaks.get() * 10) {
            maxBreaksCount = 0
            targetPos = null
            return
        }

        // 检查范围（Grim FarBreak 绕过）
        if (targetPos != null) {
            val dist = mc.player!!.eyePos.squaredDistanceTo(targetPos!!.toCenterPos())
            if (dist > range.get() * range.get()) {
                targetPos = null
                return
            }
        }

        if (secondPos != null && doubleBreak.get()) {
            val dist2 = mc.player!!.eyePos.squaredDistanceTo(secondPos!!.toCenterPos())
            if (farCancel.get() && Math.sqrt(dist2) > range.get()) {
                secondPos = null
                return
            }
        }

        // 工具切换延迟
        if (switchTimer.passedMs(switchTime.get().toLong()) && hasSwitch && autoSwitch.get() == MineSwitchMode.Delay) {
            InventoryUtil.switchToSlot(oldSlot)
            hasSwitch = false
        }
        if (switchTimer.passedMs(switchTime.get().toLong()) && secondHasSwitch && autoSwitch.get() == MineSwitchMode.Delay) {
            InventoryUtil.switchToSlot(oldSlot)
            secondHasSwitch = false
        }
    }

    @EventHandler
    private fun onRender(event: Render3DEvent) {
        if (mc.player == null || mc.world == null) return

        if (targetPos == null && secondPos == null) {
            selfClickPos = null
        }

        if (publicProgress >= 100) {
            if (!instantMine.get()) targetPos = null
        }

        if (secondPublicProgress >= 100) {
            secondPos = null
        }

        if (timer.passedMs(switchTime.get().toLong()) && hasSwitch && autoSwitch.get() != MineSwitchMode.None) {
            if (autoSwitch.get() == MineSwitchMode.Delay) InventoryUtil.switchToSlot(oldSlot)
            if (autoSwitch.get() == MineSwitchMode.Silent) InventoryUtil.sendPacket(UpdateSelectedSlotC2SPacket(oldSlot))
            hasSwitch = false
        }

        if (secondTimer.passedMs(switchTime.get().toLong()) && secondHasSwitch && autoSwitch.get() != MineSwitchMode.None) {
            if (autoSwitch.get() == MineSwitchMode.Delay) InventoryUtil.switchToSlot(oldSlot)
            if (autoSwitch.get() == MineSwitchMode.Silent) InventoryUtil.sendPacket(UpdateSelectedSlotC2SPacket(oldSlot))
            secondHasSwitch = false
        }

        // 第二目标挖掘（双挖）
        if (secondPos != null && doubleBreak.get()) {
            val secondMax = getMineTicks(secondPos!!, getTool(secondPos!!))
            val secondDelta = (System.currentTimeMillis() - secondLastTime) / 1000.0
            secondPublicProgress = (secondProgress / (secondMax * mineDamage.get()) * 100).toInt()
            secondLastTime = System.currentTimeMillis()

            if (!secondStarted) {
                sendStart(secondPos!!)
                secondStarted = true
                secondProgress = 0f
                return
            }

            val secondDamage = mineDamage.get()
            if (!checkGround.get() || mc.player!!.isOnGround) {
                secondProgress += (secondDelta * 20).toFloat()
            } else if (checkGround.get() && !mc.player!!.isOnGround) {
                secondProgress += (secondDelta * 4).toFloat()
            }

            renderSecondAnimation(event, secondDelta, secondDamage)

            if (secondProgress >= secondMax * secondDamage) {
                sendStopSecond()
            }
        }

        // 双挖切换工具
        if (doubleBreak.get()) {
            if (!usingPause.get() || !checkPause(onlyMain.get())) {
                if ((secondPublicProgress >= switchDamage.get() || publicProgress >= switchDamage.get()) && !hasSwitch && secondPos != null) {
                    val bestSlot = getTool(secondPos!!)
                    if (!hasSwitch) oldSlot = mc.player!!.inventory.selectedSlot
                    if (autoSwitch.get() != MineSwitchMode.None && bestSlot != -1) {
                        if (autoSwitch.get() == MineSwitchMode.Delay) InventoryUtil.switchToSlot(bestSlot)
                        if (autoSwitch.get() == MineSwitchMode.Silent) InventoryUtil.sendPacket(UpdateSelectedSlotC2SPacket(bestSlot))
                        timer.reset()
                        hasSwitch = true
                    }
                }
            }
        }

        // 主目标挖掘
        if (targetPos != null) {
            val max = getMineTicks(targetPos!!, getTool(targetPos!!))
            publicProgress = (progress / (max * mineDamage.get()) * 100).toInt()

            if (maxBreaksCount >= maxBreaks.get() * 10) {
                maxBreaksCount = 0
                targetPos = null
                return
            }

            if (progress >= max * mineDamage.get() && completed) {
                if (isAir(targetPos!!) || mc.world!!.getBlockState(targetPos!!).isReplaceable) {
                    maxBreaksCount = 0
                }
                if (!isAir(targetPos!!) && !mc.world!!.getBlockState(targetPos!!).isReplaceable && !(usingPause.get() && checkPause(onlyMain.get()))) {
                    maxBreaksCount++
                }
            }

            if (instantMine.get() && completed) {
                val side = getColor(sideStartColor.get(), sideEndColor.get(), 1.0)
                val line = getColor(lineStartColor.get(), lineEndColor.get(), 1.0)
                event.renderer.box(Box(targetPos!!), side, line, shapeMode.get(), 0)

                if (!mc.world!!.isAir(targetPos!!) && !mc.world!!.getBlockState(targetPos!!).isReplaceable && instantTimer.passedMs(instantDelay.get().toLong())) {
                    sendStop()
                    instantTimer.reset()
                }
                return
            }

            val delta = (System.currentTimeMillis() - lastTime) / 1000.0
            lastTime = System.currentTimeMillis()

            if (!started) {
                sendStart(targetPos!!)
                return
            }

            val damage = mineDamage.get()
            if (!checkGround.get() || mc.player!!.isOnGround) {
                progress += (delta * 20).toFloat()
            } else if (checkGround.get() && !mc.player!!.isOnGround) {
                progress += (delta * 4).toFloat()
            }

            renderAnimation(event, delta, damage)

            if (progress >= max * damage) {
                sendStop()
                completed = true
                if (!instantMine.get() && secondPos == null) targetPos = null
            }
        }
    }

    private fun sendStart(pos: BlockPos) {
        val direction = BlockUtil.getClickSide(pos)
        // Grim 核心绕过：使用反射发送带序列号的包
        sendSequencedPacket { sequence ->
            PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence)
        }

        if (fastBypass.get()) {
            val bypassPos = BlockPosX(mc.player!!.x, 321.0, mc.player!!.z)
            sendSequencedPacket { sequence ->
                PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, bypassPos, Direction.DOWN, sequence)
            }
        }

        if (doubleBreak.get() && pos == targetPos) {
            val delay = packetDelay.get()
            if (delay > 0) {
                val timer = java.util.Timer()
                timer.schedule(object : TimerTask() {
                    override fun run() {
                        mc.execute {
                            sendSequencedPacket { sequence ->
                                PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence)
                            }
                        }
                        timer.cancel()
                    }
                }, delay.toLong())
            }
        }

        if (swing.get()) EntityUtil.attackSwingHand()

        if (pos == targetPos) {
            started = true
            progress = 0f
        } else {
            secondStarted = true
            secondProgress = 0f
        }
    }

    private fun sendStop() {
        if (usingPause.get() && checkPause(onlyMain.get())) {
            return
        }

        if (!doubleBreak.get() || secondPos == null) {
            val bestSlot = getTool(targetPos!!)
            if (!hasSwitch) oldSlot = mc.player!!.inventory.selectedSlot
            if (autoSwitch.get() != MineSwitchMode.None && bestSlot != -1) {
                if (autoSwitch.get() == MineSwitchMode.Delay) InventoryUtil.switchToSlot(bestSlot)
                if (autoSwitch.get() == MineSwitchMode.Silent) InventoryUtil.sendPacket(UpdateSelectedSlotC2SPacket(bestSlot))
                timer.reset()
                hasSwitch = true
            }
        }

        // Yarn 映射修正：使用 isGliding 而不是 isFallFlying
        if (bypassGround.get() && !mc.player!!.isGliding && targetPos != null && !isAir(targetPos!!) && !mc.player!!.isOnGround) {
            mc.networkHandler?.sendPacket(
                PlayerMoveC2SPacket.Full(
                    mc.player!!.x, mc.player!!.y + 1.0E-9, mc.player!!.z,
                    mc.player!!.yaw, mc.player!!.pitch, true, mc.player!!.horizontalCollision
                )
            )
            mc.player!!.onLanding()
        }

        if (swing.get()) EntityUtil.attackSwingHand()

        val direction = BlockUtil.getClickSide(targetPos!!)
        sendSequencedPacket { sequence ->
            PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, targetPos!!, direction, sequence)
        }
    }

    private fun sendStopSecond() {
        // Yarn 映射修正：使用 isGliding 而不是 isFallFlying
        if (bypassGround.get() && !mc.player!!.isGliding && secondPos != null && !isAir(secondPos!!) && !mc.player!!.isOnGround) {
            mc.networkHandler?.sendPacket(
                PlayerMoveC2SPacket.Full(
                    mc.player!!.x, mc.player!!.y + 1.0E-9, mc.player!!.z,
                    mc.player!!.yaw, mc.player!!.pitch, true, mc.player!!.horizontalCollision
                )
            )
            mc.player!!.onLanding()
        }

        if (swing.get()) EntityUtil.attackSwingHand()

        val direction = BlockUtil.getClickSide(secondPos!!)
        sendSequencedPacket { sequence ->
            PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, secondPos!!, direction, sequence)
        }
    }

    private fun checkPause(onlyMain: Boolean): Boolean {
        return mc.options.useKey.isPressed && (!onlyMain || mc.player!!.activeHand == Hand.MAIN_HAND)
    }

    private fun isAir(breakPos: BlockPos): Boolean {
        return mc.world!!.isAir(breakPos) || mc.world!!.getBlockState(breakPos).block == Blocks.FIRE
    }

    private fun canBreak(pos: BlockPos): Boolean {
        return mc.world != null && mc.player != null && mc.world!!.getBlockState(pos).getHardness(mc.world!!, pos) != -1f
    }

    private fun getTool(pos: BlockPos): Int {
        var index = -1
        var currentFastest = 1.0f
        for (i in 0..8) {
            val stack = mc.player!!.inventory.getStack(i)
            if (!stack.isEmpty) {
                val effLevel = InventoryUtil.getEnchantmentLevel(stack, Enchantments.EFFICIENCY).toFloat()
                val destroySpeed = stack.getMiningSpeedMultiplier(mc.world!!.getBlockState(pos))
                if (effLevel + destroySpeed > currentFastest) {
                    currentFastest = effLevel + destroySpeed
                    index = i
                }
            }
        }
        return index
    }

    private fun getMineTicks(pos: BlockPos, slot: Int): Float {
        if (mc.world == null || mc.player == null) return 20f
        val state = mc.world!!.getBlockState(pos)
        val hardness = state.getHardness(mc.world!!, pos)

        if (hardness < 0f) return Float.MAX_VALUE
        if (hardness == 0f) return 1f

        val stack = if (slot == -1) ItemStack.EMPTY else mc.player!!.inventory.getStack(slot)
        val canHarvest = stack.isSuitableFor(state)
        var speed = stack.getMiningSpeedMultiplier(state)
        val efficiency = InventoryUtil.getEnchantmentLevel(stack, Enchantments.EFFICIENCY)

        if (efficiency > 0 && speed > 1f) {
            speed += (efficiency * efficiency + 1).toFloat()
        }

        if (mc.player!!.hasStatusEffect(StatusEffects.HASTE)) {
            val amp = mc.player!!.getStatusEffect(StatusEffects.HASTE)!!.amplifier
            speed *= 1f + (amp + 1) * 0.2f
        }

        if (mc.player!!.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            val amp = mc.player!!.getStatusEffect(StatusEffects.MINING_FATIGUE)!!.amplifier
            speed *= when (amp) {
                0 -> 0.3f
                1 -> 0.09f
                2 -> 0.0027f
                else -> 0.00081f
            }
        }

        val damage = speed / hardness / (if (canHarvest) 30f else 100f)
        if (damage <= 0f) return Float.MAX_VALUE

        return 1f / damage
    }

    private fun renderAnimation(event: Render3DEvent, delta: Double, damage: Double) {
        renderProgressVal = MathHelper.clamp(renderProgressVal + delta * 2, -2.0, 2.0)
        val max = getMineTicks(targetPos!!, getTool(targetPos!!)).toDouble()

        var p = 1 - MathHelper.clamp(progress / (max * damage).toFloat(), 0f, 1f)
        p = Math.pow(p.toDouble(), animationExp.get()).toFloat()
        p = 1 - p

        val size = p / 2
        val box = Box(
            targetPos!!.x + 0.5 - size, targetPos!!.y + 0.5 - size, targetPos!!.z + 0.5 - size,
            targetPos!!.x + 0.5 + size, targetPos!!.y + 0.5 + size, targetPos!!.z + 0.5 + size
        )

        val side = getColor(sideStartColor.get(), sideEndColor.get(), p.toDouble())
        val line = getColor(lineStartColor.get(), lineEndColor.get(), p.toDouble())

        event.renderer.box(box, side, line, shapeMode.get(), 0)
    }

    private fun renderSecondAnimation(event: Render3DEvent, delta: Double, damage: Double) {
        secondRender = MathHelper.clamp(secondRender + delta * 2, -2.0, 2.0)
        val max = getMineTicks(secondPos!!, getTool(secondPos!!)).toDouble()

        var p = 1 - MathHelper.clamp(secondProgress / (max * damage).toFloat(), 0f, 1f)
        p = Math.pow(p.toDouble(), animationExp.get()).toFloat()
        p = 1 - p

        val size = p / 2
        val box = Box(
            secondPos!!.x + 0.5 - size, secondPos!!.y + 0.5 - size, secondPos!!.z + 0.5 - size,
            secondPos!!.x + 0.5 + size, secondPos!!.y + 0.5 + size, secondPos!!.z + 0.5 + size
        )

        val side = getColor(secondSideStartColor.get(), secondSideEndColor.get(), p.toDouble())
        val line = getColor(secondLineStartColor.get(), secondLineEndColor.get(), p.toDouble())

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

    private fun lerp(start: Double, end: Double, d: Double): Double {
        return start + (end - start) * d
    }

    override fun getInfoString(): String? {
        if (targetPos == null) return null
        val max = getMineTicks(targetPos!!, getTool(targetPos!!))
        if (progress >= max * mineDamage.get()) return "§f[100%]"
        return "§f[$publicProgress%]"
    }
}
