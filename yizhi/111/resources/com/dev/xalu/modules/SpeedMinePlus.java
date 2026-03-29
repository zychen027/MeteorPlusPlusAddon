package com.dev.xalu.modules;

import com.dev.xalu.XALUAddon;
import com.dev.xalu.utils.entity.InventoryUtil;
import com.dev.xalu.utils.math.Timer;
import com.dev.xalu.utils.world.BlockUtil;
import java.util.Objects;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1294;
import net.minecraft.class_1799;
import net.minecraft.class_1887;
import net.minecraft.class_1893;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_2680;
import net.minecraft.class_2828;
import net.minecraft.class_2846;
import net.minecraft.class_3532;
import net.minecraft.class_5321;
import net.minecraft.class_7202;
import net.minecraft.class_7204;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/SpeedMinePlus.class */
public class SpeedMinePlus extends Module {
    public static SpeedMinePlus INSTANCE;
    private final SettingGroup sgGeneral;
    private final SettingGroup sgRender;
    private final SettingGroup sgDebug;
    private final Setting<Boolean> usingPause;
    private final Setting<Boolean> onlyMain;
    private final Setting<InventoryUtil.SwitchMode> autoSwitch;
    public final Setting<Integer> range;
    public final Setting<Integer> maxBreaks;
    private final Setting<Boolean> farCancel;
    private final Setting<Boolean> instantMine;
    private final Setting<Integer> instantDelay;
    private final Setting<Boolean> checkGround;
    private final Setting<Boolean> bypassGround;
    private final Setting<Integer> switchTime;
    private final Setting<Integer> mineDelay;
    private final Setting<Double> mineDamage;
    private final Setting<Boolean> checkBreakable;
    private final Setting<Boolean> checkDistance;
    private final Setting<Boolean> checkMaxBreaks;
    private final Setting<Boolean> checkAir;
    private final Setting<Boolean> checkReplaceable;
    private final Setting<Boolean> checkWorld;
    private final Setting<Boolean> checkTargetNull;
    private final Setting<Boolean> checkStarted;
    private final Setting<Boolean> checkProgressComplete;
    private final Setting<Boolean> checkCompleted;
    private final Setting<Boolean> checkFallFlying;
    private final Setting<Boolean> checkAutoSwitch;
    private final Setting<Boolean> checkBestSlot;
    private final Setting<Boolean> checkInstantDelay;
    private final Setting<Boolean> checkSwitchDelay;
    private final Setting<Boolean> checkTargetChanged;
    private final Setting<Boolean> checkHardness;
    private final Setting<Boolean> checkCanHarvest;
    private final Setting<Boolean> checkEfficiency;
    private final Setting<Boolean> checkHaste;
    private final Setting<Boolean> checkMiningFatigue;
    private final Setting<Boolean> checkDamage;
    private final Setting<Boolean> checkRender;
    private final Setting<Boolean> checkAnimation;
    private final Setting<Boolean> checkSwingHand;
    private final Setting<Boolean> checkSilentSwitch;
    private final Setting<Double> animationExp;
    private final Setting<ShapeMode> shapeMode;
    private final Setting<SettingColor> sideStartColor;
    private final Setting<SettingColor> sideEndColor;
    private final Setting<SettingColor> lineStartColor;
    private final Setting<SettingColor> lineEndColor;
    private final Setting<Boolean> debugClickSide;
    private final Setting<Boolean> debugProgress;
    private final Setting<Boolean> debugTarget;
    private final Setting<Boolean> debugSwitch;
    public static int maxBreaksCount;
    public static class_2338 targetPos;
    private static float progress;
    private long lastTime;
    private static boolean started;
    private double render;
    private int oldSlot;
    private Timer timer;
    private Timer mineTimer;
    private Timer instantTimer;
    private boolean hasSwitch;
    public static class_2338 selfClickPos = null;
    public static int publicProgress = 0;
    private static boolean completed = false;

    public SpeedMinePlus() {
        super(XALUAddon.CATEGORY, "SpeedMine+", "SpeedMine with debug options");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgRender = this.settings.createGroup("Render");
        this.sgDebug = this.settings.createGroup("Debug");
        this.usingPause = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("UsingPause")).description("Pause mining when using item")).defaultValue(true)).build());
        SettingGroup settingGroup = this.sgGeneral;
        BoolSetting.Builder builder = (BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("OnlyMain")).description("Only check main hand")).defaultValue(true);
        Setting<Boolean> setting = this.usingPause;
        Objects.requireNonNull(setting);
        this.onlyMain = settingGroup.add(((BoolSetting.Builder) builder.visible(setting::get)).build());
        this.autoSwitch = this.sgGeneral.add(((EnumSetting.Builder) ((EnumSetting.Builder) ((EnumSetting.Builder) new EnumSetting.Builder().name("AutoSwitch")).description("Auto switch tool mode")).defaultValue(InventoryUtil.SwitchMode.Silent)).build());
        this.range = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("Range")).description("Mining range")).defaultValue(6)).min(0).sliderMax(12).build());
        this.maxBreaks = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("TryBreakTime")).description("Max break attempts")).defaultValue(3)).min(0).sliderMax(10).build());
        this.farCancel = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("FarCancel")).description("Cancel when too far")).defaultValue(true)).build());
        this.instantMine = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("InstantMine")).description("Enable instant mining")).defaultValue(true)).build());
        this.instantDelay = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("InstantDelay")).description("Delay before instant break")).defaultValue(50)).min(0).sliderMax(1000).build());
        this.checkGround = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckGround")).description("Check if on ground")).defaultValue(true)).build());
        this.bypassGround = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("BypassGround")).description("Bypass ground check")).defaultValue(true)).build());
        this.switchTime = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("SwitchTime")).description("Delay before switching back")).defaultValue(100)).min(0).sliderMax(1000).build());
        this.mineDelay = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("MineDelay")).description("Delay between mines")).defaultValue(350)).min(0).sliderMax(1000).build());
        this.mineDamage = this.sgGeneral.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("Damage")).description("Mining damage multiplier")).defaultValue(1.38d).sliderMax(2.0d).build());
        this.checkBreakable = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckBreakable")).description("Check if block is breakable")).defaultValue(true)).build());
        this.checkDistance = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckDistance")).description("Check if target is in range")).defaultValue(true)).build());
        this.checkMaxBreaks = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckMaxBreaks")).description("Check max break attempts")).defaultValue(true)).build());
        this.checkAir = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckAir")).description("Check if block is air")).defaultValue(true)).build());
        this.checkReplaceable = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckReplaceable")).description("Check if block is replaceable")).defaultValue(true)).build());
        this.checkWorld = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckWorld")).description("Check if world and player exist")).defaultValue(true)).build());
        this.checkTargetNull = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckTargetNull")).description("Check if target is null")).defaultValue(true)).build());
        this.checkStarted = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckStarted")).description("Check if mining has started")).defaultValue(true)).build());
        this.checkProgressComplete = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckProgressComplete")).description("Check if progress is complete")).defaultValue(true)).build());
        this.checkCompleted = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckCompleted")).description("Check if mining is completed")).defaultValue(true)).build());
        this.checkFallFlying = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckFallFlying")).description("Check if player is fall flying")).defaultValue(true)).build());
        this.checkAutoSwitch = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckAutoSwitch")).description("Check if auto switch is enabled")).defaultValue(true)).build());
        this.checkBestSlot = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckBestSlot")).description("Check if best slot is valid")).defaultValue(true)).build());
        this.checkInstantDelay = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckInstantDelay")).description("Check instant mining delay")).defaultValue(true)).build());
        this.checkSwitchDelay = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckSwitchDelay")).description("Check switch delay")).defaultValue(true)).build());
        this.checkTargetChanged = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckTargetChanged")).description("Check if target has changed")).defaultValue(true)).build());
        this.checkHardness = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckHardness")).description("Check block hardness")).defaultValue(true)).build());
        this.checkCanHarvest = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckCanHarvest")).description("Check if can harvest block")).defaultValue(true)).build());
        this.checkEfficiency = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckEfficiency")).description("Check efficiency enchantment")).defaultValue(true)).build());
        this.checkHaste = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckHaste")).description("Check haste effect")).defaultValue(true)).build());
        this.checkMiningFatigue = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckMiningFatigue")).description("Check mining fatigue effect")).defaultValue(true)).build());
        this.checkDamage = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckDamage")).description("Check mining damage")).defaultValue(true)).build());
        this.checkRender = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckRender")).description("Check if should render")).defaultValue(true)).build());
        this.checkAnimation = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckAnimation")).description("Check animation rendering")).defaultValue(true)).build());
        this.checkSwingHand = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckSwingHand")).description("Check if should swing hand")).defaultValue(true)).build());
        this.checkSilentSwitch = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("CheckSilentSwitch")).description("Check silent switch mode")).defaultValue(true)).build());
        this.animationExp = this.sgRender.add(((DoubleSetting.Builder) new DoubleSetting.Builder().name("Animation Exponent")).defaultValue(3.0d).range(0.0d, 10.0d).sliderRange(0.0d, 10.0d).build());
        this.shapeMode = this.sgRender.add(((EnumSetting.Builder) ((EnumSetting.Builder) new EnumSetting.Builder().name("Shape Mode")).defaultValue(ShapeMode.Both)).build());
        this.sideStartColor = this.sgRender.add(((ColorSetting.Builder) new ColorSetting.Builder().name("Side Start")).defaultValue(new SettingColor(255, 255, 255, 0)).build());
        this.sideEndColor = this.sgRender.add(((ColorSetting.Builder) new ColorSetting.Builder().name("Side End")).defaultValue(new SettingColor(255, 255, 255, 50)).build());
        this.lineStartColor = this.sgRender.add(((ColorSetting.Builder) new ColorSetting.Builder().name("Line Start")).defaultValue(new SettingColor(255, 255, 255, 0)).build());
        this.lineEndColor = this.sgRender.add(((ColorSetting.Builder) new ColorSetting.Builder().name("Line End")).defaultValue(new SettingColor(255, 255, 255, 255)).build());
        this.debugClickSide = this.sgDebug.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("Debug ClickSide")).description("Debug click side detection")).defaultValue(false)).build());
        this.debugProgress = this.sgDebug.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("Debug Progress")).description("Debug mining progress")).defaultValue(false)).build());
        this.debugTarget = this.sgDebug.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("Debug Target")).description("Debug target position")).defaultValue(false)).build());
        this.debugSwitch = this.sgDebug.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("Debug Switch")).description("Debug inventory switching")).defaultValue(false)).build());
        this.render = 1.0d;
        this.oldSlot = -1;
        this.timer = new Timer();
        this.mineTimer = new Timer();
        this.instantTimer = new Timer();
        this.hasSwitch = false;
        INSTANCE = this;
    }

    public void onActivate() {
        maxBreaksCount = 0;
        this.hasSwitch = false;
        this.mineTimer.setMs(999999L);
        this.instantTimer.setMs(999999L);
        this.timer.setMs(999999L);
        targetPos = null;
        started = false;
        progress = 0.0f;
        this.lastTime = System.currentTimeMillis();
        this.render = 1.0d;
    }

    public void onDeactivate() {
        if (this.hasSwitch) {
            InventoryUtil.switchToSlot(this.oldSlot);
            this.hasSwitch = false;
        }
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (!((Boolean) this.checkBreakable.get()).booleanValue() || BlockUtils.canBreak(event.blockPos)) {
            event.cancel();
            if (this.mineTimer.passedMs(((Integer) this.mineDelay.get()).intValue())) {
                if (!((Boolean) this.checkTargetChanged.get()).booleanValue() || targetPos == null || !targetPos.equals(event.blockPos)) {
                    this.mineTimer.reset();
                    selfClickPos = event.blockPos;
                    mine(event.blockPos);
                }
            }
        }
    }

    public static void mine(class_2338 pos) {
        maxBreaksCount = 0;
        completed = false;
        targetPos = pos;
        started = false;
        progress = 0.0f;
    }

    public String getInfoString() {
        if (targetPos == null) {
            return null;
        }
        double max = getMineTicks(getTool(targetPos));
        return ((double) progress) >= max * ((Double) this.mineDamage.get()).doubleValue() ? "§f[100%]" : "§f[" + publicProgress + "%]";
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (((Boolean) this.checkWorld.get()).booleanValue() && (this.mc.field_1687 == null || this.mc.field_1724 == null)) {
            return;
        }
        if (((Boolean) this.checkSwitchDelay.get()).booleanValue() && this.timer.passedMs(((Integer) this.switchTime.get()).intValue()) && this.hasSwitch && this.autoSwitch.get() == InventoryUtil.SwitchMode.Delay) {
            info("Switching back to slot " + this.oldSlot, new Object[0]);
            InventoryUtil.switchToSlot(this.oldSlot, (InventoryUtil.SwitchMode) this.autoSwitch.get());
            this.hasSwitch = false;
        } else if (((Boolean) this.checkSwitchDelay.get()).booleanValue() && this.timer.passedMs(((Integer) this.switchTime.get()).intValue()) && this.hasSwitch && this.autoSwitch.get() == InventoryUtil.SwitchMode.Delay) {
            InventoryUtil.switchToSlot(this.oldSlot, (InventoryUtil.SwitchMode) this.autoSwitch.get());
            this.hasSwitch = false;
        }
        if (((Boolean) this.checkTargetNull.get()).booleanValue() && targetPos == null) {
            publicProgress = 0;
            return;
        }
        if (((Boolean) this.debugTarget.get()).booleanValue()) {
            info("Target: " + targetPos.method_10263() + ", " + targetPos.method_10264() + ", " + targetPos.method_10260(), new Object[0]);
        }
        if (((Boolean) this.checkMaxBreaks.get()).booleanValue() && maxBreaksCount >= ((Integer) this.maxBreaks.get()).intValue() * 10) {
            info("Max breaks reached, resetting", new Object[0]);
            maxBreaksCount = 0;
            targetPos = null;
            return;
        }
        if (((Boolean) this.checkDistance.get()).booleanValue() && ((Boolean) this.farCancel.get()).booleanValue() && Math.sqrt(this.mc.field_1724.method_33571().method_1025(targetPos.method_46558())) > ((Integer) this.range.get()).intValue()) {
            info("Target too far, cancelling", new Object[0]);
            targetPos = null;
            return;
        }
        double max = getMineTicks(getTool(targetPos));
        publicProgress = (int) ((((double) progress) / (max * ((Double) this.mineDamage.get()).doubleValue())) * 100.0d);
        if (((Boolean) this.debugProgress.get()).booleanValue()) {
            float f = progress;
            double dDoubleValue = max * ((Double) this.mineDamage.get()).doubleValue();
            int i = publicProgress;
            info("Progress: " + f + "/" + dDoubleValue + " (" + this + "%)", new Object[0]);
        }
        if (((Boolean) this.checkProgressComplete.get()).booleanValue() && progress >= max * ((Double) this.mineDamage.get()).doubleValue() && completed) {
            boolean isAirBlock = ((Boolean) this.checkAir.get()).booleanValue() ? isAir(targetPos) : false;
            boolean isReplaceableBlock = ((Boolean) this.checkReplaceable.get()).booleanValue() ? this.mc.field_1687.method_8320(targetPos).method_45474() : false;
            if (isAirBlock || isReplaceableBlock) {
                maxBreaksCount = 0;
            }
            if (!isAirBlock && !isReplaceableBlock && (!((Boolean) this.usingPause.get()).booleanValue() || !checkPause(((Boolean) this.onlyMain.get()).booleanValue()))) {
                maxBreaksCount++;
            }
        }
        if (((Boolean) this.checkCompleted.get()).booleanValue() && ((Boolean) this.instantMine.get()).booleanValue() && completed) {
            Color side = getColor((Color) this.sideStartColor.get(), (Color) this.sideEndColor.get(), 1.0d);
            Color line = getColor((Color) this.lineStartColor.get(), (Color) this.lineEndColor.get(), 1.0d);
            event.renderer.box(new class_238(targetPos), side, line, (ShapeMode) this.shapeMode.get(), 0);
            boolean z = (((Boolean) this.checkAir.get()).booleanValue() && this.mc.field_1687.method_22347(targetPos)) ? false : true;
            boolean isAirBlock2 = z;
            boolean z2 = (((Boolean) this.checkReplaceable.get()).booleanValue() && this.mc.field_1687.method_8320(targetPos).method_45474()) ? false : true;
            boolean isReplaceableBlock2 = z2;
            if (isAirBlock2 && isReplaceableBlock2) {
                if (!((Boolean) this.checkInstantDelay.get()).booleanValue() || this.instantTimer.passedMs(((Integer) this.instantDelay.get()).intValue())) {
                    info("Instant mining", new Object[0]);
                    sendStop();
                    this.instantTimer.reset();
                    return;
                }
                return;
            }
            return;
        }
        double delta = (System.currentTimeMillis() - this.lastTime) / 1000.0d;
        this.lastTime = System.currentTimeMillis();
        if (((Boolean) this.checkStarted.get()).booleanValue() && !started) {
            class_2350 clickSide = BlockUtil.getClickSide(targetPos);
            if (((Boolean) this.debugClickSide.get()).booleanValue()) {
                info("Click side: " + (clickSide != null ? "null" : clickSide.name()), new Object[0]);
            }
            sendStart();
            return;
        }
        Double damage = (Double) this.mineDamage.get();
        if (!((Boolean) this.checkGround.get()).booleanValue() || this.mc.field_1724.method_24828()) {
            progress = (float) (((double) progress) + (delta * 20.0d));
        } else if (((Boolean) this.checkGround.get()).booleanValue() && !this.mc.field_1724.method_24828()) {
            progress = (float) (((double) progress) + (delta * 4.0d));
        }
        if (((Boolean) this.checkAnimation.get()).booleanValue()) {
            renderAnimation(event, delta, damage.doubleValue());
        }
        if (((Boolean) this.checkProgressComplete.get()).booleanValue() && progress >= max * damage.doubleValue()) {
            info("Mining complete", new Object[0]);
            sendStop();
            selfClickPos = null;
            completed = true;
            if (!((Boolean) this.instantMine.get()).booleanValue()) {
                targetPos = null;
            }
        }
    }

    private void sendStart() {
        class_2350 clickSide = BlockUtil.getClickSide(targetPos);
        if (((Boolean) this.debugClickSide.get()).booleanValue()) {
            info("Sending START_DESTROY_BLOCK with side: " + (clickSide != null ? "null" : clickSide.name()), new Object[0]);
        }
        sendSequencedPacket(id -> {
            return new class_2846(class_2846.class_2847.field_12968, targetPos, clickSide, id);
        });
        if (((Boolean) this.checkSwingHand.get()).booleanValue()) {
            this.mc.field_1724.method_6104(class_1268.field_5808);
        }
        started = true;
        progress = 0.0f;
    }

    private void sendStop() {
        if (((Boolean) this.usingPause.get()).booleanValue() && checkPause(((Boolean) this.onlyMain.get()).booleanValue())) {
            info("Paused due to using item", new Object[0]);
            return;
        }
        int bestSlot = getTool(targetPos);
        if (!this.hasSwitch) {
            this.oldSlot = this.mc.field_1724.method_31548().field_7545;
        }
        if (((Boolean) this.checkAutoSwitch.get()).booleanValue() && this.autoSwitch.get() != InventoryUtil.SwitchMode.None && (!((Boolean) this.checkBestSlot.get()).booleanValue() || bestSlot != -1)) {
            if (((Boolean) this.debugSwitch.get()).booleanValue()) {
                info("Switching to slot " + bestSlot + " (mode: " + ((InventoryUtil.SwitchMode) this.autoSwitch.get()).name() + ")", new Object[0]);
            }
            InventoryUtil.switchToSlot(bestSlot, (InventoryUtil.SwitchMode) this.autoSwitch.get());
            this.timer.reset();
            this.hasSwitch = true;
        }
        if (((Boolean) this.bypassGround.get()).booleanValue() && ((!((Boolean) this.checkFallFlying.get()).booleanValue() || !this.mc.field_1724.method_6128()) && targetPos != null && (!((Boolean) this.checkAir.get()).booleanValue() || !isAir(targetPos)))) {
            info("Bypassing ground check", new Object[0]);
            this.mc.method_1562().method_52787(new class_2828.class_2830(this.mc.field_1724.method_23317(), this.mc.field_1724.method_23318() + 1.0E-9d, this.mc.field_1724.method_23321(), this.mc.field_1724.method_36454(), this.mc.field_1724.method_36455(), true));
            this.mc.field_1724.method_38785();
        }
        class_2350 clickSide = BlockUtil.getClickSide(targetPos);
        if (((Boolean) this.debugClickSide.get()).booleanValue()) {
            info("Sending STOP_DESTROY_BLOCK with side: " + (clickSide != null ? "null" : clickSide.name()), new Object[0]);
        }
        if (((Boolean) this.checkSwingHand.get()).booleanValue()) {
            this.mc.field_1724.method_6104(class_1268.field_5808);
        }
        sendSequencedPacket(id -> {
            return new class_2846(class_2846.class_2847.field_12973, targetPos, clickSide, id);
        });
        if (((Boolean) this.checkSilentSwitch.get()).booleanValue() && this.autoSwitch.get() == InventoryUtil.SwitchMode.Silent && this.hasSwitch) {
            if (((Boolean) this.debugSwitch.get()).booleanValue()) {
                info("Switching back to slot " + this.oldSlot, new Object[0]);
            }
            InventoryUtil.switchToSlot(this.oldSlot, InventoryUtil.SwitchMode.Silent);
            this.hasSwitch = false;
        }
    }

    private boolean isAir(class_2338 breakPos) {
        return this.mc.field_1687.method_22347(breakPos) || (BlockUtil.getBlock(breakPos) == class_2246.field_10036 && BlockUtil.hasCrystal(breakPos));
    }

    private float getMineTicks(int slot) {
        class_1799 class_1799VarMethod_5438;
        float f;
        float f2;
        if (((Boolean) this.checkTargetNull.get()).booleanValue() && targetPos == null) {
            return 20.0f;
        }
        if (((Boolean) this.checkWorld.get()).booleanValue() && (this.mc.field_1687 == null || this.mc.field_1724 == null)) {
            return 20.0f;
        }
        class_2680 state = this.mc.field_1687.method_8320(targetPos);
        float hardness = state.method_26214(this.mc.field_1687, targetPos);
        if (((Boolean) this.checkHardness.get()).booleanValue() && hardness < 0.0f) {
            return Float.MAX_VALUE;
        }
        if (((Boolean) this.checkHardness.get()).booleanValue() && hardness == 0.0f) {
            return 1.0f;
        }
        if (slot == -1) {
            class_1799VarMethod_5438 = class_1799.field_8037;
        } else {
            class_1799VarMethod_5438 = this.mc.field_1724.method_31548().method_5438(slot);
        }
        class_1799 stack = class_1799VarMethod_5438;
        boolean canHarvest = ((Boolean) this.checkCanHarvest.get()).booleanValue() ? stack.method_7951(state) : true;
        float speed = stack.method_7924(state);
        int efficiency = InventoryUtil.getEnchantmentLevel(stack, (class_5321<class_1887>) class_1893.field_9131);
        if (((Boolean) this.checkEfficiency.get()).booleanValue() && efficiency > 0 && speed > 1.0f) {
            speed += (efficiency * efficiency) + 1;
        }
        if (((Boolean) this.checkHaste.get()).booleanValue() && this.mc.field_1724.method_6059(class_1294.field_5917)) {
            int amp = this.mc.field_1724.method_6112(class_1294.field_5917).method_5578();
            speed *= 1.0f + ((amp + 1) * 0.2f);
        }
        if (((Boolean) this.checkMiningFatigue.get()).booleanValue() && this.mc.field_1724.method_6059(class_1294.field_5901)) {
            int amp2 = this.mc.field_1724.method_6112(class_1294.field_5901).method_5578();
            float f3 = speed;
            switch (amp2) {
                case 0:
                    f2 = 0.3f;
                    break;
                case 1:
                    f2 = 0.09f;
                    break;
                case 2:
                    f2 = 0.0027f;
                    break;
                default:
                    f2 = 8.1E-4f;
                    break;
            }
            speed = f3 * f2;
        }
        if (((Boolean) this.checkDamage.get()).booleanValue()) {
            f = (speed / hardness) / (canHarvest ? 30.0f : 100.0f);
        } else {
            f = (speed / hardness) / 30.0f;
        }
        float damage = f;
        if (!((Boolean) this.checkDamage.get()).booleanValue() || damage > 0.0f) {
            return 1.0f / damage;
        }
        return Float.MAX_VALUE;
    }

    private void renderAnimation(Render3DEvent event, double delta, double damage) {
        this.render = class_3532.method_15350(this.render + (delta * 2.0d), -2.0d, 2.0d);
        double max = getMineTicks(getTool(targetPos));
        double p = 1.0d - Math.pow(1.0d - class_3532.method_15350(((double) progress) / (max * damage), 0.0d, 1.0d), ((Double) this.animationExp.get()).doubleValue());
        double size = p / 2.0d;
        class_238 box = new class_238((((double) targetPos.method_10263()) + 0.5d) - size, (((double) targetPos.method_10264()) + 0.5d) - size, (((double) targetPos.method_10260()) + 0.5d) - size, ((double) targetPos.method_10263()) + 0.5d + size, ((double) targetPos.method_10264()) + 0.5d + size, ((double) targetPos.method_10260()) + 0.5d + size);
        Color side = getColor((Color) this.sideStartColor.get(), (Color) this.sideEndColor.get(), p);
        Color line = getColor((Color) this.lineStartColor.get(), (Color) this.lineEndColor.get(), p);
        event.renderer.box(box, side, line, (ShapeMode) this.shapeMode.get(), 0);
    }

    private Color getColor(Color start, Color end, double progress2) {
        return new Color(lerp(start.r, end.r, progress2), lerp(start.g, end.g, progress2), lerp(start.b, end.b, progress2), lerp(start.a, end.a, progress2));
    }

    private int lerp(double start, double end, double d) {
        return (int) Math.round(start + ((end - start) * d));
    }

    private int getTool(class_2338 pos) {
        int index = -1;
        float CurrentFastest = 1.0f;
        for (int i = 0; i < 9; i++) {
            class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
            if (stack != class_1799.field_8037) {
                float digSpeed = InventoryUtil.getEnchantmentLevel(stack, (class_5321<class_1887>) class_1893.field_9131);
                float destroySpeed = stack.method_7924(this.mc.field_1687.method_8320(pos));
                if (digSpeed + destroySpeed > CurrentFastest) {
                    CurrentFastest = digSpeed + destroySpeed;
                    index = i;
                }
            }
        }
        return index;
    }

    public void sendSequencedPacket(class_7204 packetCreator) {
        if (this.mc.method_1562() == null || this.mc.field_1687 == null) {
            return;
        }
        class_7202 pendingUpdateManager = this.mc.field_1687.method_41925().method_41937();
        try {
            int i = pendingUpdateManager.method_41942();
            this.mc.method_1562().method_52787(packetCreator.predict(i));
            if (pendingUpdateManager != null) {
                pendingUpdateManager.close();
            }
        } catch (Throwable th) {
            if (pendingUpdateManager != null) {
                try {
                    pendingUpdateManager.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    public boolean checkPause(boolean onlyMain) {
        return this.mc.field_1690.field_1904.method_1434() && (!onlyMain || this.mc.field_1724.method_6058() == class_1268.field_5808);
    }
}
