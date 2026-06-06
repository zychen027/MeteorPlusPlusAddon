package com.zychen027.meteorplusplus.modules

import com.zychen027.meteorplusplus.MeteorPlusPlusAddon
import com.zychen027.meteorplusplus.utils.nofall_packet_sender.VPacket
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.Blocks
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.item.MaceItem
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.PlayerInput
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d

class NoFall : Module(
    MeteorPlusPlusAddon.METEORPLUSPLUS_CATEGORY,
    "A NoFall",
    "移植 NoFallFuckGrimLazyPlusV2 模式，针对1.21.11 GrimAC 优化"
) {

    private val sgGeneral = settings.getDefaultGroup()
    private val sgTargeting = settings.createGroup("Targeting")

    private val noFallToggle = sgGeneral.add(BoolSetting.Builder()
        .name("toggle")
        .description("主开关")
        .defaultValue(true)
        .build()
    )

    private val safeDistanceModify = sgTargeting.add(IntSetting.Builder()
        .name("safe-distance-modify")
        .description("额外安全摔落距离")
        .defaultValue(0)
        .range(-10, 20)
        .build()
    )

    private val equipmentIdBypass = sgTargeting.add(StringSetting.Builder()
        .name("equipment-id-bypass-nofall")
        .description("装备ID正则（匹配则不应用NoFall，如史莱姆靴）")
        .defaultValue("^(SLIME.*_BOOTS)$")
        .build()
    )

    private val disableWhenAllowFlying = sgGeneral.add(BoolSetting.Builder()
        .name("disable-when-allow-flying")
        .description("创造飞行时禁用NoFall")
        .defaultValue(true)
        .build()
    )

    // ===== Java 对齐字段 =====

    var lastOnGroundHeight = Integer.MIN_VALUE.toDouble()
    var lastHeight = 0.0
    var lastServerY = 0.0
    var lastServerOnGround = false
    var safeDistance = 0.0
    var holdingMace = false

    private var entityStage = 0
    private val ENTITY_STAGE_INITIALIZING = 0
    private val ENTITY_STAGE_ALIVE = 1
    private val ENTITY_STAGE_ABSENT = 2
    private val ENTITY_STAGE_INVULNERABLE = 3

    private val delegate = NoFallFuckGrimLazyPlusV2(this)

    // ===== 防止无限递归爆栈的布尔锁 =====
    var isSendingPacket = false

    // ===== Java 对齐工具类 =====

    private object ItemStackUtils {
        fun matchesEquipment(stack: ItemStack, regex: String): Boolean {
            if (stack.isEmpty) return false
            val id = net.minecraft.registry.Registries.ITEM.getId(stack.item).path
            val pd = Regex(regex)
            if (pd.containsMatchIn(id)) return true

            val sfid = getSfId(stack)
            if (sfid != null && pd.containsMatchIn(sfid)) return true
            return false
        }

        fun getSfId(stack: ItemStack): String? {
            return null
        }
    }

    data class HorizontalCollision(
        val forward: Boolean,
        val backward: Boolean,
        val left: Boolean,
        val right: Boolean
    ) {
        fun hasAnyCollision(): Boolean = forward || backward || left || right
    }

    companion object {
        fun applyInputWay(world: ClientWorld, player: ClientPlayerEntity): HorizontalCollision {
            val testDistance = 1e-4
            val yaw = player.yaw

            val forwardVec = Vec3d.fromPolar(0f, yaw).multiply(testDistance)
            val backwardVec = forwardVec.negate()
            val leftVec = Vec3d.fromPolar(0f, yaw + 90).multiply(testDistance)
            val rightVec = leftVec.negate()

            val forwardCollide = MovTasks.hasHorizontalCollision(world, player, forwardVec)
            val backwardCollide = MovTasks.hasHorizontalCollision(world, player, backwardVec)
            val leftCollide = MovTasks.hasHorizontalCollision(world, player, leftVec)
            val rightCollide = MovTasks.hasHorizontalCollision(world, player, rightVec)

            return HorizontalCollision(forwardCollide, backwardCollide, leftCollide, rightCollide)
        }
    }

    // ===== 事件处理 =====

    @EventHandler
    private fun onReceivePacket(event: PacketEvent.Receive) {
        if (mc.player == null || !noFallToggle.get()) return
        val packet = event.packet

        if (packet is EntityVelocityUpdateS2CPacket) {
            if (packet.entityId != mc.player!!.id) return
            val vel = VPacket.getVelocity(packet)
            val velEvent = Event(vel)
            delegate.onPlayerVelocity(velEvent)
        } else if (packet is PlayerPositionLookS2CPacket) {
            // 修复：使用 Yarn 1.21.1 的正确映射获取坐标
            val pos = packet.change().position()
            val info = MovTasks.MovInfo(pos, true, true, Vec2f(mc.player!!.yaw, mc.player!!.pitch))
            val infoEvent = Event(info)
            delegate.onSetback(infoEvent)
        }
    }

    @EventHandler
    private fun onSendPacket(event: PacketEvent.Send) {
        // 防止递归：如果是本模块自己发的包，直接忽略
        if (mc.player == null || !noFallToggle.get() || isSendingPacket) return
        val packet = event.packet
        if (packet !is PlayerMoveC2SPacket) return

        val currentY = packet.getY(0.0)
        val currentOnGround = packet.isOnGround

        if (currentY != 0.0) lastServerY = currentY
        lastServerOnGround = currentOnGround

        val player = mc.player!!

        val status = LegalMovementManager.PlayerStatus(
            player,
            Vec3d(player.x, player.y, player.z),
            currentOnGround,
            VPacket.getCollisionFlag(packet)
        )
        val manager = LegalMovementManager(status)
        val mgrEvent = Event(manager)

        delegate.applyBeforeMovementPacketModify(mgrEvent)
        if (mgrEvent.cancelled) {
            event.cancel()
        }
        delegate.postModify(mgrEvent, isActive)
    }

    @EventHandler
    private fun onPreTick(event: TickEvent.Pre) {
        if (mc.player == null || !noFallToggle.get()) return
        val player = mc.player!!

        Tasks.onTick()
        updateEntityStage(player)

        if (entityStage != ENTITY_STAGE_ALIVE) return
        if (checkInvulnerableEquipment()) return

        updateState(player)

        val status = LegalMovementManager.PlayerStatus(
            player,
            Vec3d(player.x, player.y, player.z),
            player.isOnGround,
            player.horizontalCollision
        )
        val manager = LegalMovementManager(status)
        val mgrEvent = Event(manager)

        delegate.applyPreTickModify(mgrEvent)
        delegate.applyAfterInputTick(mgrEvent)
    }

    private fun updateEntityStage(player: ClientPlayerEntity) {
        val abilities = player.abilities
        if (abilities.invulnerable ||
            (disableWhenAllowFlying.get() && abilities.allowFlying) ||
            checkInvulnerableEquipment()
        ) {
            entityStage = ENTITY_STAGE_INVULNERABLE
            return
        }

        if (!player.isAlive) {
            entityStage = ENTITY_STAGE_ABSENT
            return
        }

        entityStage = ENTITY_STAGE_ALIVE
    }

    private fun updateState(player: ClientPlayerEntity) {
        holdingMace = player.mainHandStack.item is MaceItem

        lastHeight = player.y
        safeDistance = player.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE) + safeDistanceModify.get()

        val resetCondition = player.isOnGround ||
            player.isTouchingWater ||
            player.getBlockStateAtPos().isOf(Blocks.BUBBLE_COLUMN)

        if (resetCondition) {
            lastOnGroundHeight = lastHeight
            delegate.counter = 0
        } else if (lastHeight > lastOnGroundHeight) {
            lastOnGroundHeight = lastHeight
            delegate.counter = 0
        }
    }

    private fun checkInvulnerableEquipment(): Boolean {
        val regex = equipmentIdBypass.get()
        for (slot in EquipmentSlot.values()) {
            val stack = mc.player?.getEquippedStack(slot) ?: continue
            if (ItemStackUtils.matchesEquipment(stack, regex)) return true
        }
        return false
    }

    class NoFallFuckGrimLazyPlusV2(module: NoFall) : NoFallDelegate(module) {

        enum class Step {
            COMMON,
            WAIT_FOR_RESYNC,
            APPLY_JUMP,
            RESYNC_FLOOD
        }

        companion object {
            private const val latency = 5
            private const val DELTA_Y = 9e-8
        }

        var lastStartWaitResyncTick = 0
        var lastStartWaitPos: Vec3d? = null
        var afterSetbackFlag = true
        var lastStartWaitAcceptPos: Vec3d? = null
        var lastStartWaitAcceptTick = 0
        var lastCacheInput: PlayerInputUtils.Input? = null
        var flag1 = false
        var flag2 = false
        var cnt1 = 0
        var cnt2 = 0
        var step = Step.COMMON
        var dupResync = 0

        var lastRotTick = 0
        var modifyYaw = 0.0f
        var forThisTickInput: PlayerInputUtils.Input? = null
        var applyJumpThisTick = false
        var lastFixTick = 0
        var storedPacketMove: Packet<*>? = null

        init {
            afterSetbackFlag = true
        }

        override fun onSetback(setBack: Event<MovTasks.MovInfo>) {
            afterSetbackFlag = true
            val nowV3d = setBack.context.vec3d()
            if (lastStartWaitPos != null &&
                lastStartWaitPos!!.squaredDistanceTo(nowV3d) < 1 &&
                Tasks.getTick() < lastStartWaitResyncTick + latency
            ) {
                lastStartWaitPos = null
                lastStartWaitAcceptPos = nowV3d
                lastStartWaitAcceptTick = Tasks.getTick()
                step = Step.WAIT_FOR_RESYNC
            }
            super.onSetback(setBack)
        }

        override fun applyPreTickModify(movementManagerEvent: Event<LegalMovementManager>) {
            val args = movementManagerEvent.context.playerStatus.entity
            val forceNoFall = ClientPlayerAccess.of(args).isForceNoFall

            if (forceNoFall) {
                runningThisTick = true
                counter = 0
                module.lastOnGroundHeight = module.lastServerY
                // 加锁防止递归
                module.isSendingPacket = true
                try {
                    module.mc.networkHandler?.sendPacket(
                        VPacket.newPositionAndOnGround(
                            args.x,
                            module.lastServerY + DELTA_Y,
                            args.z,
                            false,
                            args.horizontalCollision
                        )
                    )
                } finally {
                    module.isSendingPacket = false
                }
                noFallSetbackResponse = true
                ClientPlayerAccess.of(args).isForceNoFall = false
            } else if (module.isActive) {
                counter += 1
            }

            if (counter > 100) {
                noFallSetbackResponse = false
            }

            var modifyRot = false
            if (module.isActive) {
                val entity = movementManagerEvent.context.playerStatus.entity
                var input = PlayerInputUtils.of(module.mc.options)
                val playerInput = input.copy()
                var resyncCnt = false

                if (step == Step.WAIT_FOR_RESYNC) {
                    if (lastStartWaitResyncTick + latency * 2 >= Tasks.getTick()) {
                        if (lastStartWaitAcceptPos != null && Tasks.getTick() <= lastStartWaitAcceptTick + 1) {
                            resyncCnt = true
                            step = Step.APPLY_JUMP
                            module.mc.player?.setPosition(lastStartWaitAcceptPos)
                            lastStartWaitPos = lastStartWaitAcceptPos
                            lastStartWaitResyncTick = Tasks.getTick()
                            lastStartWaitAcceptPos = null
                            module.mc.player?.setOnGround(true)

                            input = input.copy()
                            input.jump(true)
                                .forward(false)
                                .backward(false)
                                .left(false)
                                .right(false)
                                .sprint(false)

                            val co = applyInputWay(entity)
                            if (!co.hasAnyCollision()) {
                                input.forward(false)
                            } else {
                                input.forward(true)
                                if (playerInput.hasWASDMovement()) {
                                    when {
                                        playerInput.isForward -> {
                                            modifyRot = true
                                            EntityUtils.setEntityYawSafe(module.mc.player, module.mc.player!!.yaw + 180)
                                        }
                                        playerInput.isBackward -> {}
                                        playerInput.isLeft -> {
                                            modifyRot = true
                                            EntityUtils.setEntityYawSafe(module.mc.player, module.mc.player!!.yaw + 90)
                                        }
                                        playerInput.isRight -> {
                                            modifyRot = true
                                            EntityUtils.setEntityYawSafe(module.mc.player, module.mc.player!!.yaw - 90)
                                        }
                                    }
                                } else {
                                    when {
                                        !co.forward -> {}
                                        !co.backward -> {
                                            modifyRot = true
                                            EntityUtils.setEntityYawSafe(module.mc.player, module.mc.player!!.yaw + 180)
                                        }
                                        !co.left -> {
                                            modifyRot = true
                                            EntityUtils.setEntityYawSafe(module.mc.player, module.mc.player!!.yaw - 90)
                                        }
                                        !co.right -> {
                                            modifyRot = true
                                            EntityUtils.setEntityYawSafe(module.mc.player, module.mc.player!!.yaw + 90)
                                        }
                                    }
                                }
                            }

                            lastCacheInput = input.copy()
                            if (modifyRot) {
                                movementManagerEvent.context.pushImportantRotation(false, true)
                                modifyYaw = module.mc.player!!.yaw
                                lastRotTick = Tasks.getTick()
                            }
                            forThisTickInput = input
                            applyJumpThisTick = true
                        } else {
                            if (Tasks.getTick() < lastRotTick + latency) {
                                module.mc.player?.yaw = modifyYaw
                                modifyRot = true
                                movementManagerEvent.context.pushImportantRotation(false, true)
                            }
                            if (lastStartWaitResyncTick + 2 >= Tasks.getTick()) {
                                forThisTickInput = lastCacheInput?.copy()
                                applyJumpThisTick = true
                            }
                        }
                    } else {
                        step = Step.COMMON
                    }
                } else if (step == Step.APPLY_JUMP) {
                    step = Step.COMMON
                }

                dupResync = maxOf(0, dupResync + (if (resyncCnt) 2 else -1))
            }

            if (modifyRot) {
                movementManagerEvent.context.markForResetRot()
            }
        }

        override fun onPlayerVelocity(playerVec: Event<Vec3d>) {}

        override fun applyAfterInputTick(movementManagerEvent: Event<LegalMovementManager>) {
            if (applyJumpThisTick && forThisTickInput != null) {
                if (dupResync >= 3 && lastStartWaitResyncTick == Tasks.getTick()) {
                    dupResync = 0
                    lastFixTick = Tasks.getTick()
                    forThisTickInput!!.forward(true).backward(false).jump(false)
                }
                forThisTickInput!!.applyInput(movementManagerEvent.context.playerStatus.entity.input)
            }
            forThisTickInput = null
        }

        override fun onJump(jumpCooldown: Event<Int>) {
            if (applyJumpThisTick) {
                jumpCooldown.context = 0
            }
        }

        override fun applyBeforeMovementPacketModify(movementManagerEvent: Event<LegalMovementManager>) {
            if (lastFixTick == Tasks.getTick()) {
                movementManagerEvent.cancel()
            }
            if (applyJumpThisTick) {
                applyJumpThisTick = false
            }

            if (module.isActive) {
                val entity = movementManagerEvent.context.playerStatus

                if (step == Step.APPLY_JUMP) {
                    step = Step.COMMON
                } else {
                    if (step == Step.COMMON) {
                        val shouldCheck = entity.entity.y <= module.lastOnGroundHeight - module.safeDistance &&
                            Tasks.getTick() > lastStartWaitResyncTick + latency

                        if (shouldCheck && !entity.entity.isOnGround) {
                            afterSetbackFlag = false
                            counter = 0
                            module.lastOnGroundHeight = entity.pos.y

                            storedPacketMove = VPacket.newOnGroundOnly(true, entity.horizontalCollision)
                            movementManagerEvent.cancel()

                            lastStartWaitPos = Vec3d(module.mc.player!!.x, module.mc.player!!.y, module.mc.player!!.z)
                            lastStartWaitResyncTick = Tasks.getTick()

                            module.mc.player?.setPosition(
                                entity.pos.withAxis(Direction.Axis.Y, module.mc.player!!.y)
                            )
                            step = Step.WAIT_FOR_RESYNC
                            noFallSetbackResponse = true
                            module.mc.player?.setOnGround(true)

                            lastCacheInput = PlayerInputUtils.of(module.mc.player!!.input)
                            lastCacheInput!!.forward(false).backward(false).left(false).right(false).jump(false)
                        }
                    } else if (step == Step.WAIT_FOR_RESYNC) {
                        // waiting, do not send movement
                    }
                }
            }
        }

        override fun postModify(movementManagerEvent: Event<LegalMovementManager>, enabledThisTick: Boolean): Boolean {
            if (storedPacketMove != null) {
                module.mc.player?.setOnGround(true)
                // 加锁防止递归
                module.isSendingPacket = true
                try {
                    module.mc.networkHandler?.sendPacket(storedPacketMove)
                } finally {
                    module.isSendingPacket = false
                }
            }
            storedPacketMove = null
            return true
        }
    }

    // ==================== 缺失的简单工具类 ====================

    object Tasks {
        private var tick = 0
        fun onTick() { tick++ }
        fun getTick(): Int = tick
    }

    class Event<T>(var context: T) {
        var cancelled = false
        fun cancel() { cancelled = true }
    }

    class MovTasks {
        class MovInfo(
            private val vec: Vec3d,
            val oGroundOverride: Boolean,
            val updatePlayer: Boolean,
            val rotationOverride: Vec2f
        ) {
            fun vec3d(): Vec3d = vec
        }

        companion object {
            fun hasHorizontalCollision(world: ClientWorld, player: ClientPlayerEntity, offset: Vec3d): Boolean {
                val box = player.boundingBox.stretch(offset.x, offset.y, offset.z)
                return !world.isSpaceEmpty(player, box)
            }
        }
    }

    class LegalMovementManager(val playerStatus: PlayerStatus) {
        class PlayerStatus(
            val entity: ClientPlayerEntity,
            val pos: Vec3d,
            val onGround: Boolean,
            val horizontalCollision: Boolean
        )

        fun pushImportantRotation(b1: Boolean, b2: Boolean) {}
        fun markForResetRot() {}
    }

    class PlayerInputUtils {
        class Input {
            var isForward = false
            var isBackward = false
            var isLeft = false
            var isRight = false
            var isJump = false
            var isSprint = false

            fun forward(v: Boolean): Input { isForward = v; return this }
            fun backward(v: Boolean): Input { isBackward = v; return this }
            fun left(v: Boolean): Input { isLeft = v; return this }
            fun right(v: Boolean): Input { isRight = v; return this }
            fun jump(v: Boolean): Input { isJump = v; return this }
            fun sprint(v: Boolean): Input { isSprint = v; return this }

            fun copy(): Input {
                return Input().also {
                    it.isForward = isForward
                    it.isBackward = isBackward
                    it.isLeft = isLeft
                    it.isRight = isRight
                    it.isJump = isJump
                    it.isSprint = isSprint
                }
            }

            fun hasWASDMovement(): Boolean = isForward || isBackward || isLeft || isRight

            fun applyInput(mcInput: net.minecraft.client.input.Input) {
                mcInput.playerInput = PlayerInput(
                    isForward, isBackward, isLeft, isRight, isJump,
                    mcInput.playerInput?.sneak ?: false, isSprint
                )
            }
        }

        companion object {
            fun of(options: net.minecraft.client.option.GameOptions): Input {
                val input = Input()
                input.isForward = options.forwardKey.isPressed
                input.isBackward = options.backKey.isPressed
                input.isLeft = options.leftKey.isPressed
                input.isRight = options.rightKey.isPressed
                input.isJump = options.jumpKey.isPressed
                input.isSprint = options.sprintKey.isPressed
                return input
            }

            fun of(mcInput: net.minecraft.client.input.Input): Input {
                val input = Input()
                val pi = mcInput.playerInput ?: PlayerInput(false, false, false, false, false, false, false)
                input.isForward = pi.forward
                input.isBackward = pi.backward
                input.isLeft = pi.left
                input.isRight = pi.right
                input.isJump = pi.jump
                input.isSprint = pi.sprint
                return input
            }
        }
    }

    object EntityUtils {
        fun setEntityYawSafe(entity: net.minecraft.entity.Entity?, yaw: Float) {
            entity?.yaw = yaw
        }
    }

    interface ClientPlayerAccess {
        var isForceNoFall: Boolean

        companion object {
            private val map = java.util.WeakHashMap<ClientPlayerEntity, ClientPlayerAccess>()

            fun of(player: ClientPlayerEntity): ClientPlayerAccess {
                return map.getOrPut(player) {
                    object : ClientPlayerAccess {
                        override var isForceNoFall = false
                    }
                }
            }
        }
    }

    abstract class NoFallDelegate(val module: NoFall) {
        var runningThisTick = false
        var counter = 0
        var noFallSetbackResponse = false

        open fun onSetback(setBack: Event<MovTasks.MovInfo>) {}
        open fun applyPreTickModify(movementManagerEvent: Event<LegalMovementManager>) {}
        open fun onPlayerVelocity(playerVec: Event<Vec3d>) {}
        open fun applyAfterInputTick(movementManagerEvent: Event<LegalMovementManager>) {}
        open fun onJump(jumpCooldown: Event<Int>) {}
        open fun applyBeforeMovementPacketModify(movementManagerEvent: Event<LegalMovementManager>) {}
        open fun postModify(movementManagerEvent: Event<LegalMovementManager>, enabledThisTick: Boolean): Boolean = true

        fun applyInputWay(entity: net.minecraft.entity.Entity): HorizontalCollision {
            val world = module.mc.world ?: return HorizontalCollision(false, false, false, false)
            return NoFall.applyInputWay(world, entity as ClientPlayerEntity)
        }
    }
}
