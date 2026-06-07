package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.asm.mixin.IClientWorldAccessor
import com.zychen027.meteorplusplus.asm.mixin.IPendingUpdateManagerAccessor
import com.zychen027.meteorplusplus.utils.efa.asm.IVec3d
import com.zychen027.meteorplusplus.utils.efa.entity.InventoryUtil
import com.zychen027.meteorplusplus.utils.efa.events.ElytraUpdateEvent
import com.zychen027.meteorplusplus.utils.efa.events.TravelEvent
import com.zychen027.meteorplusplus.utils.efa.math.Timer
import com.zychen027.meteorplusplus.utils.efa.rotation.Rotation
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.client.network.SequencedPacketCreator
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.projectile.FireworkRocketEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Hand
import net.minecraft.util.PlayerInput

class FireworkElytraFly : Module(MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY, "FireworkElytraFly", "烟花鞘翅飞行") {

    private val sgGeneral = settings.getDefaultGroup()
    private val sgRotation = settings.createGroup("Rotation")
    private val sgInventory = settings.createGroup("Inventory")

    enum class Mode { Legit, GrimDurability }
    enum class FireWorkMode { Auto, Delay, None }

    // ==================== 合并原 GlobalSetting 的设置项 ====================
    val moveFix: Setting<Boolean> = sgRotation.add(BoolSetting.Builder()
        .name("1.21+")
        .description("1.21+移动修复")
        .defaultValue(true)
        .build())

    val grimRotation: Setting<Boolean> = sgRotation.add(BoolSetting.Builder()
        .name("GrimRotation")
        .description("Grim旋转修复")
        .defaultValue(true)
        .visible { !this@FireworkElytraFly.moveFix.get() }
        .build())

    val snapBack: Setting<Boolean> = sgRotation.add(BoolSetting.Builder()
        .name("SnapBack")
        .description("转头自动回正")
        .defaultValue(true)
        .visible { !this@FireworkElytraFly.moveFix.get() }
        .build())

    val noBadPackets: Setting<Boolean> = sgInventory.add(BoolSetting.Builder()
        .name("NoBadPackets")
        .description("屏蔽错误数据包")
        .defaultValue(false)
        .build())

    val clientSwitch: Setting<Boolean> = sgInventory.add(BoolSetting.Builder()
        .name("ClientSwitch")
        .description("客户端切换")
        .defaultValue(true)
        .build())

    // ==================== 模块自身设置项 ====================
    val mode: Setting<Mode> = sgGeneral.add(EnumSetting.Builder<Mode>()
        .name("Mode")
        .description("运行模式(Legit合法，GrimDurability甲飞)")
        .defaultValue(Mode.GrimDurability)
        .build())

    val fireWorkMode: Setting<FireWorkMode> = sgGeneral.add(EnumSetting.Builder<FireWorkMode>()
        .name("FireWorkMode")
        .description("烟花使用模式(Delay延迟放，Auto自动放)")
        .defaultValue(FireWorkMode.Auto)
        .build())

    private val packetDealy: Setting<Double> = sgGeneral.add(DoubleSetting.Builder()
        .name("PacketDelay")
        .description("发包延迟tick数，设为0极速换装")
        .defaultValue(0.0)
        .min(0.0)
        .sliderMax(10.0)
        .build())

    val unbreaking: Setting<Boolean> = sgGeneral.add(BoolSetting.Builder()
        .name("Unbreaking")
        .description("无限耐久")
        .defaultValue(true)
        .build())

    private val fakeDelay: Setting<Double> = sgGeneral.add(DoubleSetting.Builder()
        .name("FakeDelay")
        .description("无限耐久操作延迟")
        .defaultValue(800.0)
        .sliderMax(1000.0)
        .build())

    val stand: Setting<Boolean> = sgGeneral.add(BoolSetting.Builder()
        .name("Stand")
        .description("站飞")
        .defaultValue(true)
        .build())

    val is121Server: Setting<Boolean> = sgGeneral.add(BoolSetting.Builder()
        .name("1.21+-Server")
        .description("服务器版本为1.21+时开启，发送Jump包防止Grim拦截(下置ViaVersion服请关闭以免踢出)")
        .defaultValue(true)
        .build())

    val releaseSneak: Setting<Boolean> = sgGeneral.add(BoolSetting.Builder()
        .name("ReleaseSneak")
        .description("自动shift")
        .defaultValue(true)
        .build())

    val pressSneak: Setting<Boolean> = sgGeneral.add(BoolSetting.Builder()
        .name("PressSneak")
        .description("自动shift")
        .defaultValue(true)
        .build())

    val releaseDelay: Setting<Int> = sgGeneral.add(IntSetting.Builder()
        .name("ReleaseDelay")
        .description("shift延迟")
        .defaultValue(100)
        .sliderMax(1000)
        .build())

    private val delay: Setting<Double> = sgGeneral.add(DoubleSetting.Builder()
        .name("FireWorkDelay")
        .description("烟花操作延迟")
        .defaultValue(1000.0)
        .visible { fireWorkMode.get() == FireWorkMode.Delay }
        .sliderMax(3000.0)
        .build())

    private val checkFirework: Setting<Boolean> = sgGeneral.add(BoolSetting.Builder()
        .name("CheckFirework")
        .description("自动检查烟花")
        .defaultValue(true)
        .build())

    val inventorySwap: Setting<Boolean> = sgGeneral.add(BoolSetting.Builder()
        .name("InventorySwap")
        .description("背包鬼手")
        .defaultValue(true)
        .build())

    val control: Setting<Boolean> = sgGeneral.add(BoolSetting.Builder()
        .name("Control")
        .description("甲飞控制")
        .defaultValue(true)
        .build())

    private val fallSpeed: Setting<Double> = sgGeneral.add(DoubleSetting.Builder()
        .name("FallSpeed")
        .description("下落速度")
        .defaultValue(0.02)
        .sliderRange(0.0, 3.0)
        .build())

    private val deBug: Setting<Boolean> = sgGeneral.add(BoolSetting.Builder()
        .name("DeBug")
        .description("dev查bug的，没iq不要开")
        .defaultValue(false)
        .build())

    var yaw = Rotation.rotationYaw
    var pitch = Rotation.rotationPitch
    var isUsingFirework = false
        private set

    private val fireworkTimer = Timer()
    private val swapTimer = Timer()

    var isFallFlying = false
        private set

    var packetDelayInt = 0
        private set

    init {
        INSTANCE = this
    }

    override fun onActivate() {
        // 修复：使用 Long 类型字面量
        fireworkTimer.setMs(99999L)
        packetDelayInt = 0
        swapTimer.setMs(99999L)
        isFallFlying = false
    }

    override fun onDeactivate() {
        // 1.21.2+ 必须使用 PlayerInputC2SPacket 同步 Shift 状态
        if (pressSneak.get()) {
            val currentInput = mc.player?.input?.playerInput ?: PlayerInput.DEFAULT
            mc.networkHandler?.sendPacket(PlayerInputC2SPacket(PlayerInput(currentInput.forward, currentInput.backward, currentInput.left, currentInput.right, currentInput.jump, true, currentInput.sprint)))
        }
        if (releaseSneak.get()) {
            val delayMs = releaseDelay.get().toLong()
            java.util.Timer().schedule(object : java.util.TimerTask() {
                override fun run() {
                    mc.execute {
                        val currentInput = mc.player?.input?.playerInput ?: PlayerInput.DEFAULT
                        mc.networkHandler?.sendPacket(PlayerInputC2SPacket(PlayerInput(currentInput.forward, currentInput.backward, currentInput.left, currentInput.right, currentInput.jump, false, currentInput.sprint)))
                    }
                }
            }, delayMs)
        }
    }

    // ==================== 严格对齐原版 LeavesHack 的站飞与 Control 逻辑 ====================
    @EventHandler
    fun onTravel(event: TravelEvent) {
        if (!isFallFlying) return
        if (mode.get() == Mode.Legit) return
        if (!control.get()) return
        
        if (mc.currentScreen is ChatScreen) {
            setY(fallSpeed.get())
            return
        }
        
        // 原版逻辑：移动时不干预 Y 轴，由原版物理接管（配合 ElytraUpdate 取消实现站飞）
        if (!wantToMove()) {
            setX(0.0)
            setZ(0.0)
            setY(fallSpeed.get())
        }
    }

    private fun setY(f: Double) { (mc.player!!.velocity as IVec3d).setY(f) }
    private fun setX(f: Double) { (mc.player!!.velocity as IVec3d).setX(f) }
    private fun setZ(f: Double) { (mc.player!!.velocity as IVec3d).setZ(f) }

    // ==================== 严格对齐原版 LeavesHack 的 onElytraUpdate ====================
    @EventHandler
    fun onElytraUpdate(event: ElytraUpdateEvent) {
        if (stand.get()) event.cancel()
    }

    override fun getInfoString(): String? {
        if (mc.player == null || mc.world == null) return null
        var fireworks = 0
        if (inventorySwap.get()) {
            for (i in 0..44) {
                val stack: ItemStack = mc.player!!.inventory.getStack(i)
                if (stack.item === Items.FIREWORK_ROCKET) fireworks += stack.count
            }
        } else {
            for (i in 0..8) {
                val stack: ItemStack = mc.player!!.inventory.getStack(i)
                if (stack.item === Items.FIREWORK_ROCKET) fireworks += stack.count
            }
        }
        return "§f[F:$fireworks]"
    }

    // ==================== 1.21.2+ 安全起飞适配 ====================
    private fun startFallFlyingSafe() {
        if (mc.player == null) return
        if (is121Server.get()) {
            val currentInput = mc.player!!.input.playerInput ?: PlayerInput.DEFAULT
            // 1.21+ 原版服：先发 Jump 包，满足 Grim 拦截检查
            mc.networkHandler!!.sendPacket(PlayerInputC2SPacket(
                PlayerInput(currentInput.forward, currentInput.backward, currentInput.left, currentInput.right, true, currentInput.sneak, currentInput.sprint)
            ))
        }
        // 直接调用 startGliding()，绕过 1.21.1 本地 input.jumping 的强制检查
        // startGliding() 内部会自动发送 START_FALL_FLYING 包，无需再手动发送
        mc.player!!.startGliding()
    }

    @EventHandler
    fun onTick(event: TickEvent.Pre) {
        if (mc.currentScreen != null && deBug.get()) {
            info("screen" + mc.currentScreen!!.title.string + " " + mc.currentScreen!!.javaClass.simpleName + " " + mc.currentScreen!!.javaClass.superclass.simpleName + " " + mc.currentScreen!!.title.string)
        }

        if (mc.currentScreen != null && mc.currentScreen is HandledScreen<*> && mc.currentScreen !is InventoryScreen && mc.currentScreen !is CreativeInventoryScreen) return

        packetDelayInt++
        yaw = getSprintYaw(mc.player!!.yaw)
        pitch = getPitch(mc.player!!.pitch)

        if (deBug.get()) info("Yaw: $yaw Pitch: $pitch")

        if (mode.get() == Mode.GrimDurability) {
            if (moveFix.get()) {
                Rotation.snapAt(yaw, pitch)
            } else {
                // 1.21.2+ 的 Full 包增加了 horizontalCollision 参数
                mc.networkHandler!!.sendPacket(PlayerMoveC2SPacket.Full(mc.player!!.x, mc.player!!.y, mc.player!!.z, yaw, pitch, mc.player!!.isOnGround, mc.player!!.horizontalCollision))
            }
        }

        var hasFirework = false
        if (checkFirework.get()) {
            for (entity in mc.world!!.entities) {
                if (entity is FireworkRocketEntity && entity.owner == mc.player) {
                    hasFirework = true
                    break
                }
            }
        }
        isUsingFirework = hasFirework

        val elytra = InventoryUtil.findItemInventorySlot(Items.ELYTRA)
        val chestStack = mc.player!!.getEquippedStack(EquipmentSlot.CHEST)
        val wearingElytra = chestStack.isOf(Items.ELYTRA) && chestStack.damage < chestStack.maxDamage - 1

        if (wearingElytra && !isFallFlying && !mc.player!!.isOnGround) {
            startFallFlyingSafe()
            isFallFlying = true
        }

        // 修复：将 DoubleSetting 获取的值转为 Long 传入 passedMs
        if (mode.get() == Mode.Legit && wearingElytra && isFallFlying && !mc.player!!.isOnGround && unbreaking.get() && swapTimer.passedMs(fakeDelay.get().toLong())) {
            mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player)
            mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player)
            startFallFlyingSafe()
            swapTimer.reset()
        }

        if (mode.get() == Mode.GrimDurability) {
            if (elytra != -1 && packetDelayInt > packetDealy.get()) {
                mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, mc.player)
                mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player)
                mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, mc.player)
                
                if (!mc.player!!.isOnGround) {
                    startFallFlyingSafe()
                }
                
                if (!hasFirework && fireWorkMode.get() == FireWorkMode.Auto) {
                    offFirework()
                } else if (fireWorkMode.get() == FireWorkMode.Delay && wantToMove()) {
                    if (!checkFirework.get() || !isUsingFirework) {
                        offFirework()
                    }
                }
                
                mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, mc.player)
                mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player)
                mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, mc.player)
                packetDelayInt = 0
            }
        } else {
            if (wearingElytra && isFallFlying) {
                if (!hasFirework && fireWorkMode.get() == FireWorkMode.Auto) {
                    offFirework()
                } else if (fireWorkMode.get() == FireWorkMode.Delay && wantToMove()) {
                    if (!checkFirework.get() || !isUsingFirework) {
                        offFirework()
                    }
                }
            }
        }

        // 修复落地或脱下鞘翅后状态未重置的Bug
        if (mc.player!!.isOnGround || !wearingElytra) {
            isFallFlying = false
        }
    }

    fun offFirework() {
        // 修复：将 DoubleSetting 获取的值转为 Long 传入 passedMs
        if (!fireworkTimer.passedMs(delay.get().toLong()) && fireWorkMode.get() == FireWorkMode.Delay) return

        if (mc.player!!.mainHandStack.item === Items.FIREWORK_ROCKET) {
            sendSequencedPacket { id: Int -> PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player!!.yaw, mc.player!!.pitch) }; fireworkTimer.reset()
        } else if (mc.player!!.offHandStack.item === Items.FIREWORK_ROCKET) {
            sendSequencedPacket { id: Int -> PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player!!.yaw, mc.player!!.pitch) }; fireworkTimer.reset()
        } else if (inventorySwap.get()) {
            val fireworkSlot = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET)
            if (fireworkSlot != -1) {
                InventoryUtil.inventorySwap(fireworkSlot, mc.player!!.inventory.selectedSlot)
                sendSequencedPacket { id: Int -> PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player!!.yaw, mc.player!!.pitch) }
                InventoryUtil.inventorySwap(fireworkSlot, mc.player!!.inventory.selectedSlot)
                Rotation.sendPacket(CloseHandledScreenC2SPacket(mc.player!!.currentScreenHandler.syncId))
                fireworkTimer.reset()
            }
        } else {
            val fireworkSlot = InventoryUtil.findItem(Items.FIREWORK_ROCKET)
            if (fireworkSlot != -1) {
                val old = mc.player!!.inventory.selectedSlot
                InventoryUtil.switchToSlot(fireworkSlot)
                sendSequencedPacket { id: Int -> PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player!!.yaw, mc.player!!.pitch) }
                InventoryUtil.switchToSlot(old)
                fireworkTimer.reset()
            }
        }
    }

    fun sendSequencedPacket(packetCreator: SequencedPacketCreator) {
        if (mc.networkHandler == null || mc.world == null) return
        // 1.21.2+ 更稳妥的序列号获取与递增方式，避免触发额外校验
        val manager = (mc.world as IClientWorldAccessor).getPendingUpdateManager()
        val accessor = manager as IPendingUpdateManagerAccessor
        val sequence = accessor.getSequence()
        accessor.setSequence(sequence + 1)
        mc.networkHandler!!.sendPacket(packetCreator.predict(sequence))
    }

    private fun wantToMove(): Boolean {
        return mc.options.forwardKey.isPressed || mc.options.backKey.isPressed || mc.options.leftKey.isPressed || mc.options.rightKey.isPressed || mc.options.jumpKey.isPressed || mc.options.sneakKey.isPressed
    }

    fun isMoving(): Boolean {
        if (mc.player == null) return false
        val input = mc.player!!.input ?: return false
        val playerInput = input.playerInput ?: return false
        // 1.21.2+ 输入系统重做，使用 PlayerInput 记录
        return playerInput.forward || playerInput.backward || playerInput.left || playerInput.right
    }

    fun getSprintYaw(yaw: Float): Float {
        var yawVar = yaw
        if (mc.options.forwardKey.isPressed && !mc.options.backKey.isPressed) {
            if (mc.options.leftKey.isPressed && !mc.options.rightKey.isPressed) {
                yawVar -= 45f
            } else if (mc.options.rightKey.isPressed && !mc.options.leftKey.isPressed) {
                yawVar += 45f
            }
        } else if (mc.options.backKey.isPressed && !mc.options.forwardKey.isPressed) {
            yawVar += 180f
            if (mc.options.leftKey.isPressed && !mc.options.rightKey.isPressed) {
                yawVar += 45f
            } else if (mc.options.rightKey.isPressed && !mc.options.leftKey.isPressed) {
                yawVar -= 45f
            }
        } else if (mc.options.leftKey.isPressed && !mc.options.rightKey.isPressed) {
            yawVar -= 90f
        } else if (mc.options.rightKey.isPressed && !mc.options.leftKey.isPressed) {
            yawVar += 90f
        }
        return yawVar
    }

    private fun getPitch(pitch: Float): Float {
        var pitchVar = pitch
        if (mc.currentScreen !is ChatScreen) {
            if (mc.options.sneakKey.isPressed && mc.options.jumpKey.isPressed) {
                pitchVar = -3f
            } else if (mc.options.jumpKey.isPressed) {
                pitchVar = if (isMoving()) -45f else -90f
            } else if (mc.options.sneakKey.isPressed) {
                pitchVar = if (isMoving()) 45f else 90f
            }
            if (isMoving() && !mc.options.sneakKey.isPressed && !mc.options.jumpKey.isPressed) {
                pitchVar = -1.9f
            }
        }
        return pitchVar
    }

    fun isPhased(): Boolean = mc.world!!.canCollide(mc.player, mc.player!!.boundingBox)

    companion object {
        @JvmField
        var INSTANCE: FireworkElytraFly? = null
    }
}
