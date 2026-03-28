package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.utils.entity.InventoryUtil
import com.zychen027.meteorplusplus.utils.math.Timer
import com.zychen027.meteorplusplus.utils.world.BlockUtil
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent
import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.render.color.SettingColor
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.network.PendingUpdateManager
import net.minecraft.client.network.SequencedPacketCreator
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

/**
 * PacketMine (发包挖掘) - 移植自 LeavesHack
 * 针对 Grim 反作弊优化绕过逻辑
 * 
 * Grim 反作弊检测点：
 * - FastBreak: 检测挖掘速度过快（balance > 1000ms 取消）
 * - FarBreak: 检测挖掘距离过远（超过 reach 取消）
 * - InvalidBreak: 检测无效的挖掘面
 * - MultiBreak: 检测同时挖掘多个方块
 * 
 * 绕过策略：
 * 1. 添加挖掘延迟（至少 275ms，Grim FastBreak 检测阈值）
 * 2. 限制挖掘范围（不超过 4.5 格，Grim FarBreak 检测）
 * 3. 使用正确的挖掘顺序（START -> 等待 -> STOP）
 * 4. 添加 Ground 状态检测（Grim 检测玩家是否在地面上）
 * 5. 使用 sequenced packet 绕过序列检测
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
        .name("Range")
        .description("挖掘范围（Grim FarBreak 限制：不超过 4.5）")
        .defaultValue(4.5)
        .min(1.0)
        .sliderMax(6.0)
        .build())

    private val maxBreaks = sgGeneral.add(IntSetting.Builder()
        .name("MaxBreaks")
        .description("最大挖掘次数")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .build())

    private val instantMine = sgGeneral.add(BoolSetting.Builder()
        .name("InstantMine")
        .description("瞬间挖掘（完成后继续发包）")
        .defaultValue(true)
        .build())

    private val usingPause = sgGeneral.add(BoolSetting.Builder()
        .name("UsingPause")
        .description("使用时暂停挖掘（绕过 FastBreak）")
        .defaultValue(true)
        .build())

    private val onlyMain = sgGeneral.add(BoolSetting.Builder()
        .name("OnlyMain")
        .description("仅主手使用时暂停")
        .defaultValue(true)
        .visible { usingPause.get() }
        .build())

    // ==================== Grim 绕过设置 ====================
    private val checkGround = sgBypass.add(BoolSetting.Builder()
        .name("CheckGround")
        .description("检查是否在地面上（Grim 检测）")
        .defaultValue(true)
        .build())

    private val bypassGround = sgBypass.add(BoolSetting.Builder()
        .name("BypassGround")
        .description("绕过地面检测（发送虚假落地包）")
        .defaultValue(true)
        .visible { checkGround.get() }
        .build())

    private val mineDelay = sgBypass.add(IntSetting.Builder()
        .name("MineDelay")
        .description("挖掘延迟 ms（Grim FastBreak: 275ms 阈值）")
        .defaultValue(350)
        .min(0)
        .sliderMax(1000)
        .build())

    private val mineDamage = sgBypass.add(DoubleSetting.Builder()
        .name("Damage")
        .description("挖掘伤害倍率（Grim FastBreak 绕过）")
        .defaultValue(1.38)
        .min(0.1)
        .sliderMax(2.0)
        .build())

    private val switchTime = sgBypass.add(IntSetting.Builder()
        .name("SwitchTime")
        .description("工具切换延迟 ms")
        .defaultValue(100)
        .min(0)
        .sliderMax(1000)
        .build())

    private val instantDelay = sgBypass.add(IntSetting.Builder()
        .name("InstantDelay")
        .description("瞬间挖掘延迟 ms")
        .defaultValue(50)
        .min(0)
        .sliderMax(1000)
        .visible { instantMine.get() }
        .build())

    private val autoSwitch = sgBypass.add(EnumSetting.Builder<SwitchMode>()
        .name("AutoSwitch")
        .description("自动切换工具")
        .defaultValue(SwitchMode.Silent)
        .build())

    // ==================== 渲染设置 ====================
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

    private val animationExp = sgRender.add(DoubleSetting.Builder()
        .name("AnimationExp")
        .description("渲染动画指数")
        .defaultValue(3.0)
        .min(0.0)
        .sliderMax(10.0)
        .build())

    // ==================== 状态变量 ====================
    private var targetPos: BlockPos? = null
    private var started = false
    private var completed = false
    private var progress = 0.0f
    private var renderProgress = 1.0
    private var lastTime = 0L
    private var oldSlot = -1
    private var hasSwitch = false
    private var maxBreaksCount = 0
    private var publicProgress = 0

    private val timer = Timer()
    private val mineTimer = Timer()
    private val instantTimer = Timer()
    private val switchTimer = Timer()

    override fun onActivate() {
        resetState()
    }

    override fun onDeactivate() {
        if (hasSwitch && oldSlot != -1) {
            InventoryUtil.switchToSlot(oldSlot)
            hasSwitch = false
        }
        targetPos = null
    }

    private fun resetState() {
        maxBreaksCount = 0
        hasSwitch = false
        mineTimer.setMs(999999)
        instantTimer.setMs(999999)
        timer.setMs(999999)
        targetPos = null
        started = false
        progress = 0f
        lastTime = System.currentTimeMillis()
        renderProgress = 1.0
    }

    @EventHandler
    private fun onStartBreakingBlock(event: StartBreakingBlockEvent) {
        if (!canBreak(event.blockPos)) return
        event.cancel()

        if (!mineTimer.passedMs(mineDelay.get().toLong())) return

        if (targetPos == null || targetPos != event.blockPos) {
            mineTimer.reset()
            mine(event.blockPos)
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

        // 工具切换延迟
        if (switchTimer.passedMs(switchTime.get().toLong()) && hasSwitch && autoSwitch.get() == SwitchMode.Delay) {
            InventoryUtil.switchToSlot(oldSlot)
            hasSwitch = false
        }
    }

    @EventHandler
    private fun onRender(event: Render3DEvent) {
        if (mc.player == null || mc.world == null) return
        if (targetPos == null) {
            publicProgress = 0
            return
        }

        val max = getMineTicks(getTool(targetPos!!)).toDouble()
        publicProgress = ((progress / (max * mineDamage.get())) * 100).toInt()

        // 瞬间挖掘模式
        if (instantMine.get() && completed) {
            val side = getColor(sideStartColor.get(), sideEndColor.get(), 1.0)
            val line = getColor(lineStartColor.get(), lineEndColor.get(), 1.0)
            event.renderer.box(Box(targetPos!!), side, line, shapeMode.get(), 0)

            if (!mc.world!!.isAir(targetPos!!) &&
                !mc.world!!.getBlockState(targetPos!!).isReplaceable &&
                instantTimer.passedMs(instantDelay.get().toLong())
            ) {
                sendStop()
                instantTimer.reset()
            }
            return
        }

        // 计算时间增量
        val delta = (System.currentTimeMillis() - lastTime) / 1000.0
        lastTime = System.currentTimeMillis()

        // 发送开始挖掘包
        if (!started) {
            sendStart()
            return
        }

        // 计算挖掘进度（Grim FastBreak 绕过）
        val damage = mineDamage.get()
        if (!checkGround.get() || mc.player!!.isOnGround) {
            progress += (delta * 20).toFloat() // 地面挖掘速度
        } else {
            progress += (delta * 4).toFloat() // 空中挖掘速度（降低）
        }

        // 渲染动画
        renderAnimation(event, delta, damage)

        // 挖掘完成
        if (progress >= max * damage) {
            sendStop()
            completed = true
            if (!instantMine.get()) targetPos = null
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
        val side = BlockUtil.getClickSide(targetPos!!)
        sendSequencedPacket { id ->
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                targetPos!!,
                side,
                id
            )
        }
        mc.player!!.swingHand(Hand.MAIN_HAND)
        started = true
        progress = 0f
    }

    private fun sendStop() {
        // 检查是否在使用物品（Grim FastBreak 绕过）
        if (usingPause.get() && checkPause(onlyMain.get())) {
            return
        }

        // 切换工具
        val bestSlot = getTool(targetPos!!)
        if (!hasSwitch) oldSlot = mc.player!!.inventory.selectedSlot

        if (autoSwitch.get() != SwitchMode.None && bestSlot != -1) {
            InventoryUtil.switchToSlot(bestSlot)
            timer.reset()
            hasSwitch = true
        }

        // Grim 地面检测绕过（发送虚假落地包）
        if (bypassGround.get() && !mc.player!!.isGliding && targetPos != null && !isAir(targetPos!!)) {
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

        // 发送停止挖掘包
        mc.player!!.swingHand(Hand.MAIN_HAND)
        val side = BlockUtil.getClickSide(targetPos!!)
        sendSequencedPacket { id ->
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                targetPos!!,
                side,
                id
            )
        }

        // Silent 模式切换回原槽位
        if (autoSwitch.get() == SwitchMode.Silent && hasSwitch) {
            InventoryUtil.switchToSlot(oldSlot)
            hasSwitch = false
        }

        maxBreaksCount++
    }

    private fun sendSequencedPacket(packetCreator: SequencedPacketCreator) {
        if (mc.networkHandler == null || mc.player == null) return
        // MC 1.21.8: 直接发送包，不使用序列
        mc.networkHandler!!.sendPacket(packetCreator.predict(0))
    }

    private fun checkPause(onlyMain: Boolean): Boolean {
        return mc.options.useKey.isPressed && (!onlyMain || mc.player!!.activeHand == Hand.MAIN_HAND)
    }

    private fun isAir(breakPos: BlockPos): Boolean {
        return mc.world!!.isAir(breakPos) ||
               mc.world!!.getBlockState(breakPos).block == Blocks.FIRE
    }

    private fun canBreak(pos: BlockPos): Boolean {
        return mc.world != null && mc.player != null &&
               mc.world!!.getBlockState(pos).getHardness(mc.world!!, pos) != -1f
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

    private fun getMineTicks(slot: Int): Float {
        if (targetPos == null || mc.world == null || mc.player == null) return 20f

        val state = mc.world!!.getBlockState(targetPos!!)
        val hardness = state.getHardness(mc.world!!, targetPos!!)
        if (hardness < 0f) return Float.MAX_VALUE
        if (hardness == 0f) return 1f

        val stack = if (slot == -1) ItemStack.EMPTY else mc.player!!.inventory.getStack(slot)
        val canHarvest = stack.isSuitableFor(state)
        var speed = stack.getMiningSpeedMultiplier(state)

        // 效率附魔
        val efficiency = InventoryUtil.getEnchantmentLevel(stack, Enchantments.EFFICIENCY)
        if (efficiency > 0 && speed > 1f) {
            speed += (efficiency * efficiency + 1).toFloat()
        }

        // 急迫效果
        if (mc.player!!.hasStatusEffect(StatusEffects.HASTE)) {
            val amp = mc.player!!.getStatusEffect(StatusEffects.HASTE)!!.amplifier
            speed *= 1f + (amp + 1) * 0.2f
        }

        // 挖掘疲劳效果
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
        renderProgress = MathHelper.clamp(renderProgress + delta * 2, -2.0, 2.0)
        val max = getMineTicks(getTool(targetPos!!)).toDouble()
        var p = 1 - MathHelper.clamp(progress / (max * damage).toFloat(), 0f, 1f)
        p = Math.pow(p.toDouble(), animationExp.get()).toFloat()
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

        val side = getColor(sideStartColor.get(), sideEndColor.get(), p.toDouble())
        val line = getColor(lineStartColor.get(), lineEndColor.get(), p.toDouble())

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
        val max = getMineTicks(getTool(targetPos!!))
        if (progress >= max * mineDamage.get()) return "§f[100%]"
        return "§f[$publicProgress%]"
    }

    enum class SwitchMode {
        Normal,
        Silent,
        Delay,
        None
    }
}
