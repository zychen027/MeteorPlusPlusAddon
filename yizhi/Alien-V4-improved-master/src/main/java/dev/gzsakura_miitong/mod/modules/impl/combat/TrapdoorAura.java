package dev.gzsakura_miitong.mod.modules.impl.combat;

import dev.gzsakura_miitong.Alien;
import dev.gzsakura_miitong.api.events.eventbus.EventListener;
import dev.gzsakura_miitong.api.events.impl.UpdateEvent;
import dev.gzsakura_miitong.api.utils.combat.CombatUtil;
import dev.gzsakura_miitong.api.utils.math.PredictUtil;
import dev.gzsakura_miitong.api.utils.math.Timer;
import dev.gzsakura_miitong.api.utils.player.EntityUtil;
import dev.gzsakura_miitong.api.utils.player.InventoryUtil;
import dev.gzsakura_miitong.api.utils.world.BlockPosX;
import dev.gzsakura_miitong.api.utils.world.BlockUtil;
import dev.gzsakura_miitong.mod.modules.Module;
import dev.gzsakura_miitong.mod.modules.impl.exploit.Blink;
import dev.gzsakura_miitong.mod.modules.impl.player.PacketMine;
import dev.gzsakura_miitong.mod.modules.settings.impl.BooleanSetting;
import dev.gzsakura_miitong.mod.modules.settings.impl.SliderSetting;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * TrapdoorAura — 活板門光環
 *
 * 在目標頭頂放置活板門（或蜘蛛網/梯子/藤蔓/鷹架），
 * 然後透過開→關循環壓低目標的碰撞箱，使其無法站立。
 */
public class TrapdoorAura extends Module {

    public static TrapdoorAura INSTANCE;

    /* ────────────────── 目標 ────────────────── */

    public PlayerEntity target;
    private PlayerEntity currentTarget;

    /* ────────────────── 設定 — 計時 ────────────────── */

    private final SliderSetting delay       = this.add(new SliderSetting("Delay", 100, 0, 500).setSuffix("ms"));
    private final SliderSetting closeDelay  = this.add(new SliderSetting("CloseDelay", 75, 0, 500).setSuffix("ms"));

    /* ────────────────── 設定 — 範圍 ────────────────── */

    private final SliderSetting range       = this.add(new SliderSetting("Range", 5.0, 0.0, 8.0).setSuffix("m"));
    private final SliderSetting placeRange  = this.add(new SliderSetting("PlaceRange", 4.5, 0.0, 6.0).setSuffix("m"));
    private final SliderSetting predictTicks = this.add(new SliderSetting("PredictTicks", 2.0, 0.0, 50.0, 1.0));

    /* ────────────────── 設定 — 行為 ────────────────── */

    private final BooleanSetting rotate     = this.add(new BooleanSetting("Rotate", true));
    private final BooleanSetting inventory  = this.add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting silent     = this.add(new BooleanSetting("Silent", false));
    private final BooleanSetting ghostHand  = this.add(new BooleanSetting("GhostHand", true));

    /* ────────────────── 設定 — 暫停條件 ────────────────── */

    private final BooleanSetting usingPause = this.add(new BooleanSetting("UsingPause", true));
    private final BooleanSetting pauseEat   = this.add(new BooleanSetting("PauseEat", true));

    /* ────────────────── 設定 — 其它 ────────────────── */

    private final BooleanSetting autoDisable   = this.add(new BooleanSetting("AutoDisable", false));
    private final BooleanSetting ignoreNakeds  = this.add(new BooleanSetting("IgnoreNakeds", false));
    private final BooleanSetting antiStandUp   = this.add(new BooleanSetting("AntiStandUp", true));

    /* ────────────────── 設定 — 方塊種類 ────────────────── */

    private final BooleanSetting placeOtherBlocks = this.add(new BooleanSetting("PlaceOtherBlocks", true));
    private final BooleanSetting useTrapdoors     = this.add(new BooleanSetting("UseTrapdoors", true));
    private final BooleanSetting useWebs          = this.add(new BooleanSetting("UseWebs", false, this.placeOtherBlocks::getValue));
    private final BooleanSetting useLadders       = this.add(new BooleanSetting("UseLadders", false, this.placeOtherBlocks::getValue));
    private final BooleanSetting useVines         = this.add(new BooleanSetting("UseVines", false, this.placeOtherBlocks::getValue));
    private final BooleanSetting useScaffolds     = this.add(new BooleanSetting("UseScaffolds", false, this.placeOtherBlocks::getValue));

    /* ────────────────── 內部狀態 ────────────────── */

    private final Timer timer      = new Timer();
    private final Timer closeTimer = new Timer();

    private boolean  waitingClose;
    private BlockPos pendingClosePos;
    private BlockPos completedPos;

    /* ================================================================ */

    public TrapdoorAura() {
        super("TrapdoorAura", "Place trapdoors above enemies to trap them", Module.Category.Combat);
        this.setChinese("\u6d3b\u677f\u95e8\u5149\u73af"); // 活板門光環
        INSTANCE = this;
    }

    /* ────────────────── 生命週期 ────────────────── */

    @Override
    public void onEnable() {
        this.target = null;
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        this.waitingClose    = false;
        this.pendingClosePos = null;
        this.completedPos    = null;
        this.currentTarget   = null;
        this.timer.reset();
        this.closeTimer.reset();
    }

    /* ────────────────── 主邏輯 ────────────────── */

    @EventListener
    public void onUpdate(UpdateEvent event) {
        /* --- 階段 1：等待關閉已打開的活板門 --- */
        if (this.waitingClose) {
            handlePendingClose();
            return;
        }

        /* --- 暫停檢查 --- */
        if (shouldPause()) return;
        if (!this.timer.passed((long) this.delay.getValue())) return;

        /* --- 尋找目標 --- */
        PlayerEntity target = CombatUtil.getClosestEnemy(this.range.getValue());
        if (target == null) {
            if (this.autoDisable.getValue()) this.disable();
            this.currentTarget = null;
            return;
        }
        this.currentTarget = target;

        if (this.ignoreNakeds.getValue() && isNaked(target)) return;

        /* --- 計算目標頭頂位置 --- */
        Vec3d predictedPos = this.predictTicks.getValue() > 0.0
                ? PredictUtil.getPos(target, this.predictTicks.getValueInt())
                : target.getPos();

        BlockPos headPos = new BlockPosX(
                predictedPos.getX(),
                predictedPos.getY() + 1.0,
                predictedPos.getZ()
        );

        // 超出世界邊界
        if (headPos.getY() >= 320 || headPos.getY() < -64) return;

        /* --- 如果頭頂已有活板門 → 進行開關循環 --- */
        if (isTrapdoor(headPos)) {
            handleExistingTrapdoor(headPos);
            return;
        }

        /* --- 清除已完成標記（目標已移動） --- */
        if (this.completedPos != null && this.completedPos.equals(headPos)) {
            this.completedPos = null;
        }

        /* --- 嘗試放置方塊 --- */
        placeBlockOnTarget(headPos);
    }

    /* ────────────────── 等待關閉 ────────────────── */

    private void handlePendingClose() {
        if (this.pendingClosePos == null || !this.closeTimer.passed((long) this.closeDelay.getValue())) {
            return;
        }
        if (toggleTrapdoor(this.pendingClosePos, false)) {
            this.completedPos = this.pendingClosePos;
            this.timer.reset();
        }
        this.pendingClosePos = null;
        this.waitingClose    = false;
    }

    /* ────────────────── 暫停判斷 ────────────────── */

    private boolean shouldPause() {
        // Blink 暫停
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            return true;
        }
        // 使用物品暫停（吃東西、拉弓等）
        if (this.usingPause.getValue() && mc.player.isUsingItem()) {
            return true;
        }
        return this.pauseEat.getValue() && mc.player.isUsingItem();
    }

    /* ────────────────── 處理已有的活板門 ────────────────── */

    private void handleExistingTrapdoor(BlockPos trapdoorPos) {
        BlockState state = mc.world.getBlockState(trapdoorPos);
        if (!(state.getBlock() instanceof TrapdoorBlock)) return;

        boolean isOpen = Boolean.TRUE.equals(state.get(TrapdoorBlock.OPEN));

        if (isOpen) {
            // 活板門目前開啟 → 關閉它
            if (toggleTrapdoor(trapdoorPos, false)) {
                this.completedPos = trapdoorPos;
                this.timer.reset();
            }
            return;
        }

        // 活板門目前關閉，且已完成過這個位置 → 跳過
        if (this.completedPos != null && this.completedPos.equals(trapdoorPos)) return;

        // 開啟活板門 → 排程關閉
        if (toggleTrapdoor(trapdoorPos, true)) {
            this.waitingClose    = true;
            this.pendingClosePos = trapdoorPos;
            this.closeTimer.reset();
            this.timer.reset();
        }
    }

    /* ────────────────── 放置方塊到目標頭頂 ────────────────── */

    private void placeBlockOnTarget(BlockPos targetPos) {
        if (!canPlaceAt(targetPos)) return;

        int slot = findSuitableSlot();
        if (slot == -1) return;

        int oldSlot = mc.player.getInventory().selectedSlot;

        selectSlot(slot, oldSlot);
        BlockUtil.placeBlock(targetPos, this.rotate.getValue());
        restoreSlot(slot, oldSlot);

        // 若放置了活板門且啟用了 AntiStandUp → 排程開→關循環
        Block placed = mc.world.getBlockState(targetPos).getBlock();
        if (placed instanceof TrapdoorBlock && this.antiStandUp.getValue()) {
            this.waitingClose    = true;
            this.pendingClosePos = targetPos;
            this.closeTimer.reset();
        }

        this.timer.reset();
    }

    /* ────────────────── 放置可行性檢查 ────────────────── */

    private boolean canPlaceAt(BlockPos pos) {
        if (!BlockUtil.canReplace(pos)) return false;

        // GhostHand 模式下忽略玩家碰撞箱
        if (this.ghostHand.getValue()) {
            if (hasEntityIgnoringPlayers(pos)) return false;
        } else {
            if (BlockUtil.hasEntity(pos, false)) return false;
        }

        // 距離檢查
        if (this.placeRange.getValue() > 0.0
                && mc.player.getPos().distanceTo(Vec3d.ofCenter(pos)) > this.placeRange.getValue()) {
            return false;
        }

        // 需要有可放置面 or 啟用 AirPlace
        if (BlockUtil.getPlaceSide(pos, this.placeRange.getValue()) == null && !BlockUtil.allowAirPlace()) {
            return false;
        }

        // 避免與 PacketMine 正在破壞的方塊衝突
        BlockPos breakPos = PacketMine.getBreakPos();
        return breakPos == null || !pos.equals(breakPos);
    }

    /* ────────────────── 開關活板門 ────────────────── */

    /**
     * 嘗試切換活板門的開關狀態。
     *
     * @param trapdoorPos   活板門位置
     * @param desiredOpen   期望狀態（true = 打開, false = 關閉）
     * @return 是否成功切換
     */
    private boolean toggleTrapdoor(BlockPos trapdoorPos, boolean desiredOpen) {
        BlockState state = mc.world.getBlockState(trapdoorPos);
        if (!(state.getBlock() instanceof TrapdoorBlock)) return false;

        boolean isOpen = Boolean.TRUE.equals(state.get(TrapdoorBlock.OPEN));
        if (isOpen == desiredOpen) return false;

        // 距離檢查
        if (this.placeRange.getValue() > 0.0
                && mc.player.getPos().distanceTo(Vec3d.ofCenter(trapdoorPos)) > this.placeRange.getValue()) {
            return false;
        }

        Direction clickSide = BlockUtil.getClickSide(trapdoorPos);
        if (clickSide == null) return false;

        // 旋轉看向目標方塊
        if (this.rotate.getValue()) {
            Vec3d hitVec = Vec3d.ofCenter(trapdoorPos).add(
                    clickSide.getVector().getX() * 0.5,
                    clickSide.getVector().getY() * 0.5,
                    clickSide.getVector().getZ() * 0.5
            );
            Alien.ROTATION.lookAt(hitVec);
        }

        BlockUtil.clickBlock(trapdoorPos, clickSide, this.rotate.getValue());
        return true;
    }

    /* ────────────────── 尋找合適的物品欄位 ────────────────── */

    private int findSuitableSlot() {
        // 優先使用活板門
        if (this.useTrapdoors.getValue()) {
            int trapdoorSlot = this.inventory.getValue()
                    ? InventoryUtil.findClassInventorySlot(TrapdoorBlock.class)
                    : InventoryUtil.findClass(TrapdoorBlock.class);
            if (trapdoorSlot != -1) return trapdoorSlot;
        }

        if (!this.placeOtherBlocks.getValue()) return -1;

        // 依設定依序嘗試其他方塊
        if (this.useWebs.getValue()) {
            int slot = findBlockSlot(Blocks.COBWEB);
            if (slot != -1) return slot;
        }
        if (this.useLadders.getValue()) {
            int slot = findBlockSlot(Blocks.LADDER);
            if (slot != -1) return slot;
        }
        if (this.useVines.getValue()) {
            int slot = findBlockSlot(Blocks.VINE);
            if (slot != -1) return slot;
        }
        if (this.useScaffolds.getValue()) {
            int slot = findBlockSlot(Blocks.SCAFFOLDING);
            if (slot != -1) return slot;
        }

        return -1;
    }

    private int findBlockSlot(Block block) {
        return this.inventory.getValue()
                ? InventoryUtil.findBlockInventorySlot(block)
                : InventoryUtil.findBlock(block);
    }

    /* ────────────────── 物品欄切換 ────────────────── */

    private void selectSlot(int slot, int previousSlot) {
        if (this.inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, previousSlot);
            return;
        }
        if (this.silent.getValue()) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            return;
        }
        InventoryUtil.switchToSlot(slot);
    }

    private void restoreSlot(int slot, int previousSlot) {
        if (this.inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, previousSlot);
            EntityUtil.syncInventory();
            return;
        }
        if (this.silent.getValue()) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            return;
        }
        InventoryUtil.switchToSlot(previousSlot);
    }

    /* ────────────────── 輔助方法 ────────────────── */

    private boolean isTrapdoor(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock() instanceof TrapdoorBlock;
    }

    /**
     * 檢查位置是否有實體（忽略玩家、掉落物、末影水晶）。
     */
    private boolean hasEntityIgnoringPlayers(BlockPos pos) {
        for (Entity entity : BlockUtil.getEntities(new Box(pos))) {
            if (entity != null
                    && entity.isAlive()
                    && !(entity instanceof ItemEntity)
                    && !(entity instanceof EndCrystalEntity)
                    && !(entity instanceof PlayerEntity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判斷玩家是否未穿戴任何裝備。
     */
    private boolean isNaked(PlayerEntity player) {
        for (int i = 0; i < 4; i++) {
            if (!player.getInventory().getArmorStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /* ────────────────── 公開 API ────────────────── */

    public PlayerEntity getCurrentTarget() {
        return this.currentTarget;
    }

    @Override
    public String getInfo() {
        if (this.currentTarget != null) {
            return this.currentTarget.getGameProfile().getName();
        }
        return null;
    }

    public String getStatus() {
        if (this.currentTarget == null) return "\u7b49\u5f85\u76ee\u6807"; // 等待目標
        return this.waitingClose ? "\u5173\u95ed\u7b49\u5f85\u4e2d" : "\u6fc0\u6d3b\u4e2d"; // 關閉等待中 : 激活中
    }
}
