package com.dev.xalu.modules;

import com.dev.xalu.XALUAddon;
import com.dev.xalu.utils.entity.InventoryUtil;
import com.dev.xalu.utils.math.Timer;
import com.dev.xalu.utils.world.BlockUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
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
import net.minecraft.class_2248;
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

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/SpeedMine.class */
public class SpeedMine extends Module {
    public static SpeedMine INSTANCE;
    private final SettingGroup sgGeneral;
    private final SettingGroup sgRender;
    private final SettingGroup sgAdvanced;
    private final Setting<Mode> mode;
    private final Setting<Boolean> usingPause;
    private final Setting<Boolean> onlyMain;
    private final Setting<InventoryUtil.SwitchMode> autoSwitch;
    private final Setting<Integer> range;
    private final Setting<Integer> maxBreaks;
    private final Setting<Boolean> farCancel;
    private final Setting<Boolean> instantMine;
    private final Setting<Integer> instantDelay;
    private final Setting<Boolean> checkGround;
    private final Setting<Boolean> bypassGround;
    private final Setting<Integer> switchTime;
    private final Setting<Integer> mineDelay;
    private final Setting<Double> mineDamage;
    private final Setting<Selection> selection;
    private final Setting<List<class_2248>> whitelist;
    private final Setting<List<class_2248>> blacklist;
    private final Setting<Boolean> pauseOnFlying;
    private final Setting<Boolean> pauseInFluid;
    private final Setting<Boolean> onlyOnGround;
    private final Setting<Boolean> noAbort;
    private final Setting<Boolean> render;
    private final Setting<Double> animationExp;
    private final Setting<ShapeMode> shapeMode;
    private final Setting<SettingColor> sideStartColor;
    private final Setting<SettingColor> sideEndColor;
    private final Setting<SettingColor> lineStartColor;
    private final Setting<SettingColor> lineEndColor;
    private final Setting<Boolean> showProgress;
    private final Setting<SettingColor> progressBgColor;
    private final Setting<SettingColor> progressFillColor;
    public static int maxBreaksCount;
    public static class_2338 targetPos;
    private static float progress;
    private long lastTime;
    private static boolean started;
    private double renderAnim;
    private int oldSlot;
    private Timer timer;
    private Timer mineTimer;
    private Timer instantTimer;
    private boolean hasSwitch;
    private final Map<class_2338, MiningData> miningQueue;
    public static class_2338 selfClickPos = null;
    public static int publicProgress = 0;
    private static boolean completed = false;

    public SpeedMine() {
        super(XALUAddon.CATEGORY, "SpeedMine", "PacketMine for grim");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgRender = this.settings.createGroup("Render");
        this.sgAdvanced = this.settings.createGroup("Advanced");
        this.mode = this.sgGeneral.add(((EnumSetting.Builder) ((EnumSetting.Builder) ((EnumSetting.Builder) new EnumSetting.Builder().name("mode")).description("Mining mode")).defaultValue(Mode.Packet)).build());
        this.usingPause = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("using-pause")).description("Pause while using items")).defaultValue(true)).build());
        SettingGroup settingGroup = this.sgGeneral;
        BoolSetting.Builder builder = (BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("only-main")).description("Only pause on main hand")).defaultValue(true);
        Setting<Boolean> setting = this.usingPause;
        Objects.requireNonNull(setting);
        this.onlyMain = settingGroup.add(((BoolSetting.Builder) builder.visible(setting::get)).build());
        this.autoSwitch = this.sgGeneral.add(((EnumSetting.Builder) ((EnumSetting.Builder) ((EnumSetting.Builder) new EnumSetting.Builder().name("auto-switch")).description("Auto switch to best tool")).defaultValue(InventoryUtil.SwitchMode.Silent)).build());
        this.range = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("range")).description("Mining range")).defaultValue(6)).min(0).sliderMax(12).build());
        this.maxBreaks = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("try-break-time")).description("Maximum break attempts")).defaultValue(3)).min(0).sliderMax(10).build());
        this.farCancel = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("far-cancel")).description("Cancel when too far")).defaultValue(true)).build());
        this.instantMine = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("instant-mine")).description("Instant mine mode")).defaultValue(true)).build());
        this.instantDelay = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("instant-delay")).description("Instant mine delay")).defaultValue(50)).min(0).sliderMax(1000).build());
        this.checkGround = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("check-ground")).description("Check ground for mining speed")).defaultValue(true)).build());
        this.bypassGround = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("bypass-ground")).description("Bypass ground check")).defaultValue(true)).build());
        this.switchTime = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("switch-time")).description("Switch delay")).defaultValue(100)).min(0).sliderMax(1000).build());
        this.mineDelay = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("mine-delay")).description("Mining delay")).defaultValue(350)).min(0).sliderMax(1000).build());
        this.mineDamage = this.sgGeneral.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("damage")).description("Damage threshold")).defaultValue(1.38d).sliderMax(2.0d).build());
        this.selection = this.sgAdvanced.add(((EnumSetting.Builder) ((EnumSetting.Builder) ((EnumSetting.Builder) new EnumSetting.Builder().name("selection")).description("Block selection mode")).defaultValue(Selection.All)).build());
        this.whitelist = this.sgAdvanced.add(((BlockListSetting.Builder) ((BlockListSetting.Builder) ((BlockListSetting.Builder) new BlockListSetting.Builder().name("whitelist")).description("Whitelisted blocks")).visible(() -> {
            return this.selection.get() == Selection.Whitelist;
        })).build());
        this.blacklist = this.sgAdvanced.add(((BlockListSetting.Builder) ((BlockListSetting.Builder) ((BlockListSetting.Builder) new BlockListSetting.Builder().name("blacklist")).description("Blacklisted blocks")).visible(() -> {
            return this.selection.get() == Selection.Blacklist;
        })).build());
        this.pauseOnFlying = this.sgAdvanced.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("pause-on-flying")).description("Pause while flying")).defaultValue(false)).build());
        this.pauseInFluid = this.sgAdvanced.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("pause-in-fluid")).description("Pause while in fluid")).defaultValue(false)).build());
        this.onlyOnGround = this.sgAdvanced.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("only-on-ground")).description("Only work on ground")).defaultValue(false)).build());
        this.noAbort = this.sgAdvanced.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("no-abort")).description("Never cancel mining packets")).defaultValue(false)).build());
        this.render = this.sgRender.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("render")).description("Render block overlay")).defaultValue(true)).build());
        this.animationExp = this.sgRender.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("animation-exponent")).description("Animation exponent")).defaultValue(3.0d).range(0.0d, 10.0d).sliderRange(0.0d, 10.0d).build());
        this.shapeMode = this.sgRender.add(((EnumSetting.Builder) ((EnumSetting.Builder) ((EnumSetting.Builder) new EnumSetting.Builder().name("shape-mode")).description("Shape rendering mode")).defaultValue(ShapeMode.Both)).build());
        this.sideStartColor = this.sgRender.add(((ColorSetting.Builder) ((ColorSetting.Builder) new ColorSetting.Builder().name("side-start")).description("Starting side color")).defaultValue(new SettingColor(255, 0, 0, 100)).build());
        this.sideEndColor = this.sgRender.add(((ColorSetting.Builder) ((ColorSetting.Builder) new ColorSetting.Builder().name("side-end")).description("Ending side color")).defaultValue(new SettingColor(0, 255, 0, 100)).build());
        this.lineStartColor = this.sgRender.add(((ColorSetting.Builder) ((ColorSetting.Builder) new ColorSetting.Builder().name("line-start")).description("Starting line color")).defaultValue(new SettingColor(255, 0, 0, 255)).build());
        this.lineEndColor = this.sgRender.add(((ColorSetting.Builder) ((ColorSetting.Builder) new ColorSetting.Builder().name("line-end")).description("Ending line color")).defaultValue(new SettingColor(0, 255, 0, 255)).build());
        this.showProgress = this.sgRender.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("show-progress")).description("Show progress bar")).defaultValue(true)).build());
        SettingGroup settingGroup2 = this.sgRender;
        ColorSetting.Builder builderDefaultValue = ((ColorSetting.Builder) ((ColorSetting.Builder) new ColorSetting.Builder().name("progress-bg")).description("Progress bar background color")).defaultValue(new SettingColor(0, 0, 0, 150));
        Setting<Boolean> setting2 = this.showProgress;
        Objects.requireNonNull(setting2);
        this.progressBgColor = settingGroup2.add(((ColorSetting.Builder) builderDefaultValue.visible(setting2::get)).build());
        SettingGroup settingGroup3 = this.sgRender;
        ColorSetting.Builder builderDefaultValue2 = ((ColorSetting.Builder) ((ColorSetting.Builder) new ColorSetting.Builder().name("progress-fill")).description("Progress bar fill color")).defaultValue(new SettingColor(0, 255, 0, 200));
        Setting<Boolean> setting3 = this.showProgress;
        Objects.requireNonNull(setting3);
        this.progressFillColor = settingGroup3.add(((ColorSetting.Builder) builderDefaultValue2.visible(setting3::get)).build());
        this.renderAnim = 1.0d;
        this.oldSlot = -1;
        this.timer = new Timer();
        this.mineTimer = new Timer();
        this.instantTimer = new Timer();
        this.hasSwitch = false;
        this.miningQueue = new HashMap();
        INSTANCE = this;
    }

    /* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/SpeedMine$Mode.class */
    public enum Mode {
        Packet("Packet"),
        Damage("Damage");

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        @Override // java.lang.Enum
        public String toString() {
            return this.title;
        }
    }

    /* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/SpeedMine$Selection.class */
    public enum Selection {
        All("All"),
        Whitelist("Whitelist"),
        Blacklist("Blacklist");

        private final String title;

        Selection(String title) {
            this.title = title;
        }

        @Override // java.lang.Enum
        public String toString() {
            return this.title;
        }
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
        this.renderAnim = 1.0d;
        this.miningQueue.clear();
    }

    public void onDeactivate() {
        if (this.hasSwitch) {
            InventoryUtil.switchToSlot(this.oldSlot);
            this.hasSwitch = false;
        }
        this.miningQueue.clear();
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (BlockUtils.canBreak(event.blockPos)) {
            event.cancel();
            if (isValidBlock(this.mc.field_1687.method_8320(event.blockPos).method_26204()) && this.mineTimer.passedMs(((Integer) this.mineDelay.get()).intValue())) {
                if (targetPos == null || !targetPos.equals(event.blockPos)) {
                    this.mineTimer.reset();
                    selfClickPos = event.blockPos;
                    mine(event.blockPos, event.direction);
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
            return;
        }
        if (!((Boolean) this.pauseOnFlying.get()).booleanValue() || this.mc.field_1724.method_24828()) {
            if (((Boolean) this.pauseInFluid.get()).booleanValue() && (this.mc.field_1724.method_5799() || this.mc.field_1724.method_5771())) {
                return;
            }
            if ((!((Boolean) this.onlyOnGround.get()).booleanValue() || this.mc.field_1724.method_24828()) && this.mode.get() == Mode.Damage && this.mc.field_1761.method_51888() >= ((Double) this.mineDamage.get()).floatValue()) {
                this.mc.field_1761.method_2925();
            }
        }
    }

    private boolean isValidBlock(class_2248 block) {
        if (this.selection.get() == Selection.All) {
            return true;
        }
        return this.selection.get() == Selection.Whitelist ? ((List) this.whitelist.get()).contains(block) : (this.selection.get() == Selection.Blacklist && ((List) this.blacklist.get()).contains(block)) ? false : true;
    }

    public static void mine(class_2338 pos) {
        mine(pos, class_2350.field_11036);
    }

    public static void mine(class_2338 pos, class_2350 direction) {
        maxBreaksCount = 0;
        completed = false;
        targetPos = pos;
        started = false;
        progress = 0.0f;
        INSTANCE.miningQueue.put(pos, new MiningData(pos, direction));
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
        if (this.mc.field_1687 == null || this.mc.field_1724 == null) {
            return;
        }
        if (this.timer.passedMs(((Integer) this.switchTime.get()).intValue()) && this.hasSwitch && this.autoSwitch.get() == InventoryUtil.SwitchMode.Delay) {
            InventoryUtil.switchToSlot(this.oldSlot, (InventoryUtil.SwitchMode) this.autoSwitch.get());
            this.hasSwitch = false;
        }
        if (targetPos == null) {
            publicProgress = 0;
            return;
        }
        if (maxBreaksCount >= ((Integer) this.maxBreaks.get()).intValue() * 10) {
            maxBreaksCount = 0;
            targetPos = null;
            return;
        }
        if (((Boolean) this.farCancel.get()).booleanValue() && Math.sqrt(this.mc.field_1724.method_33571().method_1025(targetPos.method_46558())) > ((Integer) this.range.get()).intValue()) {
            targetPos = null;
            return;
        }
        double max = getMineTicks(getTool(targetPos));
        publicProgress = (int) ((((double) progress) / (max * ((Double) this.mineDamage.get()).doubleValue())) * 100.0d);
        if (progress >= max * ((Double) this.mineDamage.get()).doubleValue() && completed) {
            if (isAir(targetPos) || this.mc.field_1687.method_8320(targetPos).method_45474()) {
                maxBreaksCount = 0;
            }
            if (!isAir(targetPos) && !this.mc.field_1687.method_8320(targetPos).method_45474() && (!((Boolean) this.usingPause.get()).booleanValue() || !checkPause(((Boolean) this.onlyMain.get()).booleanValue()))) {
                maxBreaksCount++;
            }
        }
        if (((Boolean) this.instantMine.get()).booleanValue() && completed) {
            renderBlock(event, targetPos, 1.0d);
            if (!this.mc.field_1687.method_22347(targetPos) && !this.mc.field_1687.method_8320(targetPos).method_45474() && this.instantTimer.passedMs(((Integer) this.instantDelay.get()).intValue())) {
                sendStop();
                this.instantTimer.reset();
                return;
            }
            return;
        }
        double delta = (System.currentTimeMillis() - this.lastTime) / 1000.0d;
        this.lastTime = System.currentTimeMillis();
        if (!started) {
            sendStart();
            return;
        }
        Double damage = (Double) this.mineDamage.get();
        if (!((Boolean) this.checkGround.get()).booleanValue() || this.mc.field_1724.method_24828()) {
            progress = (float) (((double) progress) + (delta * 20.0d));
        } else if (((Boolean) this.checkGround.get()).booleanValue() && !this.mc.field_1724.method_24828()) {
            progress = (float) (((double) progress) + (delta * 4.0d));
        }
        double p = class_3532.method_15350(((double) progress) / (max * damage.doubleValue()), 0.0d, 1.0d);
        renderBlock(event, targetPos, p);
        if (progress >= max * damage.doubleValue()) {
            sendStop();
            selfClickPos = null;
            completed = true;
            if (!((Boolean) this.instantMine.get()).booleanValue()) {
                targetPos = null;
            }
        }
    }

    private void renderBlock(Render3DEvent event, class_2338 pos, double progress2) {
        if (((Boolean) this.render.get()).booleanValue()) {
            class_2680 state = this.mc.field_1687.method_8320(pos);
            if (state.method_26215()) {
                return;
            }
            double p = Math.pow(progress2, ((Double) this.animationExp.get()).doubleValue());
            double size = p / 2.0d;
            class_238 box = new class_238((((double) pos.method_10263()) + 0.5d) - size, (((double) pos.method_10264()) + 0.5d) - size, (((double) pos.method_10260()) + 0.5d) - size, ((double) pos.method_10263()) + 0.5d + size, ((double) pos.method_10264()) + 0.5d + size, ((double) pos.method_10260()) + 0.5d + size);
            Color side = interpolateColor((Color) this.sideStartColor.get(), (Color) this.sideEndColor.get(), progress2);
            Color line = interpolateColor((Color) this.lineStartColor.get(), (Color) this.lineEndColor.get(), progress2);
            event.renderer.box(box, side, line, (ShapeMode) this.shapeMode.get(), 0);
            if (((Boolean) this.showProgress.get()).booleanValue()) {
                renderProgressBar(event, pos, progress2);
            }
        }
    }

    private void renderProgressBar(Render3DEvent event, class_2338 pos, double progress2) {
        double barY = ((double) pos.method_10264()) + 1.2d;
        class_238 bgBox = new class_238(pos.method_10263(), barY, pos.method_10260(), ((double) pos.method_10263()) + 1.0d, barY + 0.1d, ((double) pos.method_10260()) + 1.0d);
        event.renderer.box(bgBox, (Color) this.progressBgColor.get(), (Color) this.progressBgColor.get(), ShapeMode.Sides, 0);
        double fillWidth = 1.0d * progress2;
        class_238 fillBox = new class_238(pos.method_10263(), barY, pos.method_10260(), ((double) pos.method_10263()) + fillWidth, barY + 0.1d, ((double) pos.method_10260()) + 1.0d);
        Color fillColor = interpolateColor(new SettingColor(255, 0, 0, 200), (Color) this.progressFillColor.get(), progress2);
        event.renderer.box(fillBox, fillColor, fillColor, ShapeMode.Sides, 0);
    }

    private Color interpolateColor(Color start, Color end, double progress2) {
        return new Color(lerp(start.r, end.r, progress2), lerp(start.g, end.g, progress2), lerp(start.b, end.b, progress2), lerp(start.a, end.a, progress2));
    }

    private int lerp(double start, double end, double d) {
        return (int) Math.round(start + ((end - start) * d));
    }

    private void sendStart() {
        sendSequencedPacket(id -> {
            return new class_2846(class_2846.class_2847.field_12968, targetPos, BlockUtil.getClickSide(targetPos), id);
        });
        this.mc.field_1724.method_6104(class_1268.field_5808);
        started = true;
        progress = 0.0f;
    }

    private void sendStop() {
        if (((Boolean) this.usingPause.get()).booleanValue() && checkPause(((Boolean) this.onlyMain.get()).booleanValue())) {
            return;
        }
        int bestSlot = getTool(targetPos);
        if (!this.hasSwitch) {
            this.oldSlot = this.mc.field_1724.method_31548().field_7545;
        }
        if (this.autoSwitch.get() != InventoryUtil.SwitchMode.None && bestSlot != -1) {
            InventoryUtil.switchToSlot(bestSlot, (InventoryUtil.SwitchMode) this.autoSwitch.get());
            this.timer.reset();
            this.hasSwitch = true;
        }
        if (((Boolean) this.bypassGround.get()).booleanValue() && !this.mc.field_1724.method_6128() && targetPos != null && !isAir(targetPos)) {
            this.mc.method_1562().method_52787(new class_2828.class_2830(this.mc.field_1724.method_23317(), this.mc.field_1724.method_23318() + 1.0E-9d, this.mc.field_1724.method_23321(), this.mc.field_1724.method_36454(), this.mc.field_1724.method_36455(), true));
            this.mc.field_1724.method_38785();
        }
        this.mc.field_1724.method_6104(class_1268.field_5808);
        sendSequencedPacket(id -> {
            return new class_2846(class_2846.class_2847.field_12973, targetPos, BlockUtil.getClickSide(targetPos), id);
        });
        if (this.autoSwitch.get() == InventoryUtil.SwitchMode.Silent && this.hasSwitch) {
            InventoryUtil.switchToSlot(this.oldSlot, InventoryUtil.SwitchMode.Silent);
            this.hasSwitch = false;
        }
    }

    private boolean isAir(class_2338 breakPos) {
        return this.mc.field_1687.method_22347(breakPos) || (BlockUtil.getBlock(breakPos) == class_2246.field_10036 && BlockUtil.hasCrystal(breakPos));
    }

    private float getMineTicks(int slot) {
        float f;
        if (targetPos == null || this.mc.field_1687 == null || this.mc.field_1724 == null) {
            return 20.0f;
        }
        class_2680 state = this.mc.field_1687.method_8320(targetPos);
        float hardness = state.method_26214(this.mc.field_1687, targetPos);
        if (hardness < 0.0f) {
            return Float.MAX_VALUE;
        }
        if (hardness == 0.0f) {
            return 1.0f;
        }
        class_1799 stack = slot == -1 ? class_1799.field_8037 : this.mc.field_1724.method_31548().method_5438(slot);
        boolean canHarvest = stack.method_7951(state);
        float speed = stack.method_7924(state);
        int efficiency = InventoryUtil.getEnchantmentLevel(stack, (class_5321<class_1887>) class_1893.field_9131);
        if (efficiency > 0 && speed > 1.0f) {
            speed += (efficiency * efficiency) + 1;
        }
        if (this.mc.field_1724.method_6059(class_1294.field_5917)) {
            int amp = this.mc.field_1724.method_6112(class_1294.field_5917).method_5578();
            speed *= 1.0f + ((amp + 1) * 0.2f);
        }
        if (this.mc.field_1724.method_6059(class_1294.field_5901)) {
            int amp2 = this.mc.field_1724.method_6112(class_1294.field_5901).method_5578();
            float f2 = speed;
            switch (amp2) {
                case 0:
                    f = 0.3f;
                    break;
                case 1:
                    f = 0.09f;
                    break;
                case 2:
                    f = 0.0027f;
                    break;
                default:
                    f = 8.1E-4f;
                    break;
            }
            speed = f2 * f;
        }
        float damage = (speed / hardness) / (canHarvest ? 30.0f : 100.0f);
        if (damage <= 0.0f) {
            return Float.MAX_VALUE;
        }
        return 1.0f / damage;
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

    /* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/SpeedMine$MiningData.class */
    public static class MiningData {
        private final class_2338 pos;
        private final class_2350 direction;
        private float blockDamage = 0.0f;
        private boolean started = false;
        private boolean attemptedBreak = false;
        private long breakTime = System.currentTimeMillis();

        public MiningData(class_2338 pos, class_2350 direction) {
            this.pos = pos;
            this.direction = direction;
        }

        public class_2338 getPos() {
            return this.pos;
        }

        public class_2350 getDirection() {
            return this.direction;
        }

        public float getBlockDamage() {
            return this.blockDamage;
        }

        public void damage(float amount) {
            this.blockDamage += amount;
        }

        public boolean isStarted() {
            return this.started;
        }

        public void setStarted(boolean started) {
            this.started = started;
        }

        public boolean hasAttemptedBreak() {
            return this.attemptedBreak;
        }

        public void setAttemptedBreak(boolean attemptedBreak) {
            this.attemptedBreak = attemptedBreak;
            if (attemptedBreak) {
                this.breakTime = System.currentTimeMillis();
            }
        }

        public class_2680 getState() {
            return SpeedMine.INSTANCE.mc.field_1687.method_8320(this.pos);
        }

        public int getSlot() {
            return SpeedMine.INSTANCE.getTool(this.pos);
        }
    }
}
