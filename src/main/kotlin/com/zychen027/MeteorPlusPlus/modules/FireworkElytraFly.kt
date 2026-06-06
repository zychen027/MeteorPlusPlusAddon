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
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
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

    // 【核心修改】默认值改为 1，最小值 0，判定改为 >=，实现每 tick 极速换装
    private val packetDealy: Setting<Double> = sgGeneral.add(DoubleSetting.Builder()
        .name("PacketDelay")
        .description("发包延迟tick数，设为1极速换装不掉耐久")
        .defaultValue(1.0)
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
    private val fireworkTimer = Timer()
    private val swapTimer = Timer()

    var isFallFlying = false
    var packetDelayInt = 0

    init {
        INSTANCE = this
    }

    override fun onActivate() {
        fireworkTimer.setMs(99999)
        packetDelayInt = 0
        swapTimer.setMs(99999)
        isFallFlying = false
    }

    override fun onDeactivate() {
        if (pressSneak.get()) {
            val currentInput = mc.player?.input?.playerInput ?: PlayerInput.DEFAULT
            mc.networkHandler?.sendPacket(PlayerInputC2SPacket(
                PlayerInput(currentInput.forward, currentInput.backward, currentInput.left, currentInput.right, currentInput.jump, true, currentInput.sprint)
            ))
        }
        if (releaseSneak.get()) {
            val delayMs = releaseDelay.get().toLong()
            java.util.Timer().schedule(object : java.util.TimerTask() {
                override fun run() {
                    mc.execute {
                        val currentInput = mc.player?.input?.playerInput ?: PlayerInput.DEFAULT
                        mc.networkHandler?.sendPacket(PlayerInputC2SPacket(
                            PlayerInput(currentInput.forward, currentInput.backward, currentInput.left, currentInput.right, currentInput.jump, false, currentInput.sprint)
                        ))
                    }
                }
            }, delayMs)
        }
    }

    // ==================== 核心修复 1：Stand 站飞 ====================
    @EventHandler
    fun onTravel(event: TravelEvent) {
        if (!isFallFlying) return
        if (mode.get() == Mode.Legit) return
        if (!control.get()) return

        // 【修复】只要开启 Stand，无论是否按 WASD，都必须强制固定 Y 轴速度，这才是真站飞
        if (stand.get()) {
            setY(fallSpeed.get())
            // 如果没有按 WASD，清除水平漂移；如果按了 WASD，保留原版物理产生的推力
            if (!wantToMove()) {
                setX(0.0)
                setZ(0.0)
            }
            return
        }

        // 非 Stand 模式的常规控制
        if (mc.currentScreen is ChatScreen) {
            setY(fallSpeed.get())
            return
        }

        if (!wantToMove()) {
            setX(0.0)
            setZ(0.0)
            setY(fallSpeed.get())
        }
    }

    private fun setY(f: Double) {
        (mc.player!!.velocity as IVec3d).setY(f)
    }

    private fun setX(f: Double) {
        (mc.player!!.velocity as IVec3d).setX(f)
    }

    private fun setZ(f: Double) {
        (mc.player!!.velocity as IVec3d).setZ(f)
    }

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

    // ==================== 核心修复 2：极速甲飞换装 ====================
    @EventHandler
    fun onTick(event: TickEvent.Pre) {
        if (mc.currentScreen != null && deBug.get()) info("screen" + mc.currentScreen!!.title + " " + mc.currentScreen!!.javaClass.simpleName + " " + mc.currentScreen!!.javaClass.superclass.simpleName + " " + mc.currentScreen!!.title)
        if (mc.currentScreen != null && mc.currentScreen is HandledScreen<*> && mc.currentScreen !is InventoryScreen && mc.currentScreen !is CreativeInventoryScreen) return

        packetDelayInt++
        yaw = getSprintYaw(mc.player!!.yaw)
        pitch = getPitch(mc.player!!.pitch)
        if (deBug.get()) info("Yaw: $yaw Pitch: $pitch")

        if (mode.get() == Mode.GrimDurability) {
            if (moveFix.get()) {
                Rotation.snapAt(yaw, pitch)
            } else {
                mc.networkHandler!!.sendPacket(
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
        }

        var hasFirework = false
        if (checkFirework.get()) {
            for (entity in mc.world!!.entities) {
                if (entity is FireworkRocketEntity) {
                    if (entity.owner == mc.player) {
                        hasFirework = true
                    }
                }
            }
        }
        isUsingFirework = hasFirework

        val elytra = InventoryUtil.findItemInventorySlot(Items.ELYTRA)
        val chestStack = mc.player!!.getEquippedStack(EquipmentSlot.CHEST)
        val wearingElytra = chestStack.isOf(Items.ELYTRA) && chestStack.damage < chestStack.maxDamage - 1

        if (wearingElytra && !isFallFlying && !mc.player!!.isOnGround) {
            mc.networkHandler!!.sendPacket(ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))
            mc.player!!.startGliding()
            isFallFlying = true
        }

        if (mode.get() == Mode.Legit && wearingElytra && isFallFlying && !mc.player!!.isOnGround && unbreaking.get() && swapTimer.passedMs(fakeDelay.get())) {
            mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player)
            mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player)
            mc.networkHandler!!.sendPacket(ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))
            mc.player!!.startGliding()
            swapTimer.reset()
        }

        if (mode.get() == Mode.GrimDurability) {
            // 【修复】改为 >= 判定，配合默认值 1，实现每 tick 必换，达到极速不掉耐久
            if (elytra != -1 && packetDelayInt >= packetDealy.get()) {
                // 1. 第一轮换装
                mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, mc.player)
                mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player)
                mc.interactionManager!!.clickSlot(mc.player!!.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, mc.player)

                // 2. 无视 ElytraA，直接发起飞包
                if (!mc.player!!.isOnGround) {
                    mc.networkHandler!!.sendPacket(ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))
                    mc.player!!.startGliding()
                }

                // 3. 放烟花
                if (!hasFirework && fireWorkMode.get() == FireWorkMode.Auto) {
                    offFirework()
                } else if (fireWorkMode.get() == FireWorkMode.Delay && wantToMove()) {
                    if (!checkFirework.get() || !isUsingFirework) {
                        offFirework()
                    }
                }

                // 4. 第二轮换装
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

        if (mc.player!!.isOnGround || !wearingElytra) {
            isFallFlying = false
        }
    }

    fun offFirework() {
        if (!fireworkTimer.passedMs(delay.get()) && fireWorkMode.get() == FireWorkMode.Delay) return
        if (mc.player!!.mainHandStack.item === Items.FIREWORK_ROCKET) {
            sendSequencedPacket { id: Int -> PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player!!.yaw, mc.player!!.pitch) }
            fireworkTimer.reset()
        } else if (mc.player!!.offHandStack.item === Items.FIREWORK_ROCKET) {
            sendSequencedPacket { id: Int -> PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player!!.yaw, mc.player!!.pitch) }
            fireworkTimer.reset()
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
        val manager = (mc.world as IClientWorldAccessor).getPendingUpdateManager()
        val accessor = manager as IPendingUpdateManagerAccessor
        val sequence = accessor.getSequence()
        accessor.setSequence(sequence + 1)
        mc.networkHandler!!.sendPacket(packetCreator.predict(sequence))
    }

    private fun wantToMove(): Boolean {
        return mc.options.forwardKey.isPressed || mc.options.backKey.isPressed || mc.options.leftKey.isPressed || mc.options.rightKey.isPressed || mc.options.jumpKey.isPressed || mc.options.sneakKey.isPressed
    }

    enum class Mode {
        Legit, GrimDurability
    }

    enum class FireWorkMode {
        Auto, Delay, None
    }

    fun isMoving(): Boolean {
        if (mc.player == null) return false
        val input = mc.player!!.input ?: return false
        val playerInput = input.playerInput ?: return false
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
