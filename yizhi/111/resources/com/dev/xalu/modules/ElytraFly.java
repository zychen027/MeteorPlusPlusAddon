package com.dev.xalu.modules;

import com.dev.xalu.XALUAddon;
import com.dev.xalu.utils.entity.InventoryUtil;
import com.dev.xalu.utils.math.Timer;
import java.util.Iterator;
import java.util.Objects;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1294;
import net.minecraft.class_1304;
import net.minecraft.class_1671;
import net.minecraft.class_1713;
import net.minecraft.class_1770;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_243;
import net.minecraft.class_2708;
import net.minecraft.class_2848;
import net.minecraft.class_2886;
import net.minecraft.class_3532;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/ElytraFly.class */
public class ElytraFly extends Module {
    public static ElytraFly INSTANCE;
    private final SettingGroup sgGeneral;
    private final SettingGroup sgControl;
    private final SettingGroup sgFirework;
    private final Setting<Mode> mode;
    private final Setting<Boolean> infiniteDura;
    private final Setting<Boolean> packet;
    private final Setting<Integer> packetDelay;
    private final Setting<Boolean> setFlag;
    private final Setting<Double> speed;
    private final Setting<Double> upFactor;
    private final Setting<Double> downFactor;
    private final Setting<Double> upPitch;
    private final Setting<Double> sneakDownSpeed;
    private final Setting<Boolean> speedLimit;
    private final Setting<Double> maxSpeed;
    private final Setting<Boolean> noDrag;
    private final Setting<Boolean> autoStart;
    private final Setting<Boolean> autoStop;
    private final Setting<Boolean> firework;
    private final Setting<Boolean> packetInteract;
    private final Setting<Boolean> inventorySwap;
    private final Setting<Boolean> onlyOne;
    private final Setting<Boolean> usingPause;
    private final Setting<Integer> fireworkDelay;
    private final Setting<Double> boost;
    private final Setting<Double> pitch;
    private final Setting<Boolean> sprint;
    private final Setting<Boolean> autoJump;
    private final Setting<Boolean> releaseSneak;
    private final SettingGroup sgGhostHand;
    private final Setting<Double> ghostHandSpeed;
    private final Setting<Boolean> ghostHandAutoFirework;
    private final Setting<Integer> ghostHandFireworkDelay;
    private final Setting<Double> ghostHandPitch;
    private final Setting<Boolean> ghostHandPacket;
    private final Timer fireworkTimer;
    private final Timer instantFlyTimer;
    private final Timer ghostHandFireworkTimer;
    private boolean hasElytra;
    private boolean flying;
    private int packetDelayInt;
    private float yaw;
    private float rotationPitch;
    private float infinitePitch;
    private float lastInfinitePitch;

    public ElytraFly() {
        super(XALUAddon.CATEGORY, "ElytraFly", "Elytra flight control");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgControl = this.settings.createGroup("Control");
        this.sgFirework = this.settings.createGroup("Firework");
        this.mode = this.sgGeneral.add(((EnumSetting.Builder) ((EnumSetting.Builder) ((EnumSetting.Builder) new EnumSetting.Builder().name("mode")).description("Flight mode")).defaultValue(Mode.Control)).build());
        this.infiniteDura = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("infinite-dura")).description("Infinite durability")).defaultValue(false)).build());
        this.packet = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("packet")).description("Packet mode")).defaultValue(false)).build());
        this.packetDelay = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("packet-delay")).description("Packet delay")).defaultValue(0)).min(0).sliderMax(20).visible(() -> {
            return ((Boolean) this.packet.get()).booleanValue();
        })).build());
        this.setFlag = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("set-flag")).description("Set fall flying flag")).defaultValue(false)).visible(() -> {
            return !((Mode) this.mode.get()).equals(Mode.Bounce);
        })).build());
        this.speed = this.sgControl.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("speed")).description("Flight speed")).defaultValue(1.0d).min(0.1d).sliderMax(10.0d).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.Control);
        })).build());
        this.upFactor = this.sgControl.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("up-factor")).description("Upward speed factor")).defaultValue(1.0d).min(0.0d).sliderMax(10.0d).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.Control);
        })).build());
        this.downFactor = this.sgControl.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("fall-speed")).description("Fall speed")).defaultValue(1.0d).min(0.0d).sliderMax(10.0d).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.Control);
        })).build());
        this.upPitch = this.sgControl.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("up-pitch")).description("Pitch for upward movement")).defaultValue(0.0d).min(0.0d).sliderMax(90.0d).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.Control);
        })).build());
        this.sneakDownSpeed = this.sgControl.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("down-speed")).description("Downward speed when sneaking")).defaultValue(1.0d).min(0.1d).sliderMax(10.0d).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.Control);
        })).build());
        this.speedLimit = this.sgControl.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("speed-limit")).description("Limit maximum speed")).defaultValue(true)).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.Control);
        })).build());
        this.maxSpeed = this.sgControl.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("max-speed")).description("Maximum flight speed")).defaultValue(2.5d).min(0.1d).sliderMax(10.0d).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.Control) && ((Boolean) this.speedLimit.get()).booleanValue();
        })).build());
        this.noDrag = this.sgControl.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("no-drag")).description("Disable drag")).defaultValue(false)).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.Control);
        })).build());
        this.autoStart = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("auto-start")).description("Automatically start flying")).defaultValue(true)).visible(() -> {
            return !((Mode) this.mode.get()).equals(Mode.Bounce);
        })).build());
        this.autoStop = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("auto-stop")).description("Automatically stop on ground")).defaultValue(true)).build());
        this.firework = this.sgFirework.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("firework")).description("Auto use firework")).defaultValue(false)).build());
        SettingGroup settingGroup = this.sgFirework;
        BoolSetting.Builder builder = (BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("packet-interact")).description("Use packet for firework interaction")).defaultValue(true);
        Setting<Boolean> setting = this.firework;
        Objects.requireNonNull(setting);
        this.packetInteract = settingGroup.add(((BoolSetting.Builder) builder.visible(setting::get)).build());
        SettingGroup settingGroup2 = this.sgFirework;
        BoolSetting.Builder builder2 = (BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("inventory-swap")).description("Swap from inventory")).defaultValue(true);
        Setting<Boolean> setting2 = this.firework;
        Objects.requireNonNull(setting2);
        this.inventorySwap = settingGroup2.add(((BoolSetting.Builder) builder2.visible(setting2::get)).build());
        SettingGroup settingGroup3 = this.sgFirework;
        BoolSetting.Builder builder3 = (BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("only-one")).description("Only one firework at a time")).defaultValue(true);
        Setting<Boolean> setting3 = this.firework;
        Objects.requireNonNull(setting3);
        this.onlyOne = settingGroup3.add(((BoolSetting.Builder) builder3.visible(setting3::get)).build());
        SettingGroup settingGroup4 = this.sgFirework;
        BoolSetting.Builder builder4 = (BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("using-pause")).description("Pause while using items")).defaultValue(true);
        Setting<Boolean> setting4 = this.firework;
        Objects.requireNonNull(setting4);
        this.usingPause = settingGroup4.add(((BoolSetting.Builder) builder4.visible(setting4::get)).build());
        SettingGroup settingGroup5 = this.sgFirework;
        IntSetting.Builder builderSliderMax = ((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("firework-delay")).description("Delay between fireworks")).defaultValue(1000)).min(0).sliderMax(20000);
        Setting<Boolean> setting5 = this.firework;
        Objects.requireNonNull(setting5);
        this.fireworkDelay = settingGroup5.add(((IntSetting.Builder) builderSliderMax.visible(setting5::get)).build());
        this.boost = this.sgControl.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("boost")).description("Boost factor")).defaultValue(1.0d).min(0.1d).sliderMax(4.0d).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.Boost);
        })).build());
        this.pitch = this.sgControl.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("pitch")).description("Fixed pitch")).defaultValue(88.0d).min(-90.0d).sliderMax(90.0d).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.Bounce);
        })).build());
        this.sprint = this.sgControl.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("sprint")).description("Auto sprint")).defaultValue(true)).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.Bounce);
        })).build());
        this.autoJump = this.sgControl.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("auto-jump")).description("Auto jump for bounce mode")).defaultValue(true)).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.Bounce);
        })).build());
        this.releaseSneak = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("release-sneak")).description("Release sneak on disable")).defaultValue(false)).build());
        this.sgGhostHand = this.settings.createGroup("GhostHand");
        this.ghostHandSpeed = this.sgGhostHand.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("ghost-speed")).description("GhostHand flight speed")).defaultValue(1.5d).min(0.1d).sliderMax(5.0d).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.GhostHand);
        })).build());
        this.ghostHandAutoFirework = this.sgGhostHand.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("ghost-auto-firework")).description("Auto use firework in GhostHand mode")).defaultValue(true)).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.GhostHand);
        })).build());
        this.ghostHandFireworkDelay = this.sgGhostHand.add(((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("ghost-firework-delay")).description("Delay between fireworks in GhostHand mode")).defaultValue(1500)).min(500).sliderMax(5000).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.GhostHand) && ((Boolean) this.ghostHandAutoFirework.get()).booleanValue();
        })).build());
        this.ghostHandPitch = this.sgGhostHand.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("ghost-pitch")).description("Fixed pitch for GhostHand mode")).defaultValue(45.0d).min(-90.0d).sliderMax(90.0d).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.GhostHand);
        })).build());
        this.ghostHandPacket = this.sgGhostHand.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("ghost-packet")).description("Use packet mode for GhostHand")).defaultValue(true)).visible(() -> {
            return ((Mode) this.mode.get()).equals(Mode.GhostHand);
        })).build());
        this.fireworkTimer = new Timer();
        this.instantFlyTimer = new Timer();
        this.ghostHandFireworkTimer = new Timer();
        this.hasElytra = false;
        this.flying = false;
        this.packetDelayInt = 0;
        this.yaw = 0.0f;
        this.rotationPitch = 0.0f;
        this.infinitePitch = 0.0f;
        this.lastInfinitePitch = 0.0f;
        INSTANCE = this;
    }

    /* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/ElytraFly$Mode.class */
    public enum Mode {
        Control("Control"),
        Boost("Boost"),
        Bounce("Bounce"),
        Pitch("Pitch"),
        Rotation("Rotation"),
        GhostHand("GhostHand");

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        @Override // java.lang.Enum
        public String toString() {
            return this.title;
        }
    }

    public void onActivate() {
        if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
            toggle();
            return;
        }
        this.hasElytra = false;
        this.yaw = this.mc.field_1724.method_36454();
        this.rotationPitch = this.mc.field_1724.method_36455();
        this.fireworkTimer.reset();
        this.instantFlyTimer.reset();
    }

    public void onDeactivate() {
        if (this.mc.field_1724 != null && ((Boolean) this.releaseSneak.get()).booleanValue()) {
            this.mc.method_1562().method_52787(new class_2848(this.mc.field_1724, class_2848.class_2849.field_12984));
        }
    }

    public String getInfoString() {
        return ((Mode) this.mode.get()).toString();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
            return;
        }
        if (((Boolean) this.packet.get()).booleanValue()) {
            this.hasElytra = InventoryUtil.findItemInventorySlot(class_1802.field_8833) != -1;
        } else {
            this.hasElytra = false;
            Iterator it = this.mc.field_1724.method_5661().iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                class_1799 is = (class_1799) it.next();
                if (is.method_7909() instanceof class_1770) {
                    this.hasElytra = true;
                    break;
                }
            }
            if (((Boolean) this.infiniteDura.get()).booleanValue() && !this.mc.field_1724.method_24828() && this.hasElytra) {
                this.flying = true;
                this.mc.field_1761.method_2906(this.mc.field_1724.field_7512.field_7763, 6, 0, class_1713.field_7790, this.mc.field_1724);
                this.mc.field_1761.method_2906(this.mc.field_1724.field_7512.field_7763, 6, 0, class_1713.field_7790, this.mc.field_1724);
                this.mc.method_1562().method_52787(new class_2848(this.mc.field_1724, class_2848.class_2849.field_12982));
                if (((Boolean) this.setFlag.get()).booleanValue()) {
                    this.mc.field_1724.method_23669();
                }
            }
        }
        if (this.mode.get() == Mode.Boost) {
            doBoost();
        } else if (this.mode.get() == Mode.GhostHand) {
            doGhostHand();
        }
        if (((Boolean) this.packet.get()).booleanValue()) {
            handlePacketMode();
        }
        if (((Boolean) this.autoStart.get()).booleanValue() && !this.mc.field_1724.method_24828() && !isFallFlying() && this.hasElytra && this.instantFlyTimer.passedMs(100L)) {
            recastElytra();
            this.instantFlyTimer.reset();
        }
        if (((Boolean) this.autoStop.get()).booleanValue() && this.mc.field_1724.method_24828() && isFallFlying()) {
            this.mc.method_1562().method_52787(new class_2848(this.mc.field_1724, class_2848.class_2849.field_12982));
        }
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (this.mc.field_1724 != null && isFallFlying() && this.mode.get() == Mode.Control) {
            doControl(event);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof class_2708) {
            this.yaw = this.mc.field_1724.method_36454();
            this.rotationPitch = this.mc.field_1724.method_36455();
        }
    }

    private void doBoost() {
        if (this.hasElytra && isFallFlying()) {
            float yaw = (float) Math.toRadians(this.mc.field_1724.method_36454());
            if (this.mc.field_1690.field_1894.method_1434()) {
                this.mc.field_1724.method_5762(((-class_3532.method_15374(yaw)) * ((Double) this.boost.get()).floatValue()) / 10.0f, 0.0d, (class_3532.method_15362(yaw) * ((Double) this.boost.get()).floatValue()) / 10.0f);
            }
        }
    }

    private void doGhostHand() {
        if (this.mc.field_1724 == null) {
            return;
        }
        autoManageElytra();
        if (this.hasElytra) {
            if (!isFallFlying() && !this.mc.field_1724.method_24828() && this.instantFlyTimer.passedMs(100L)) {
                recastElytra();
                this.instantFlyTimer.reset();
            }
            if (isFallFlying()) {
                float targetPitch = ((Double) this.ghostHandPitch.get()).floatValue();
                this.mc.field_1724.method_36457(targetPitch);
                double radPitch = Math.toRadians(targetPitch);
                double velocityY = (-Math.sin(radPitch)) * ((Double) this.ghostHandSpeed.get()).doubleValue();
                double velocityXZ = Math.cos(radPitch) * ((Double) this.ghostHandSpeed.get()).doubleValue();
                float yawRad = (float) Math.toRadians(this.mc.field_1724.method_36454());
                double velocityX = ((double) (-class_3532.method_15374(yawRad))) * velocityXZ;
                double velocityZ = ((double) class_3532.method_15362(yawRad)) * velocityXZ;
                this.mc.field_1724.method_18800(velocityX, velocityY, velocityZ);
                if (((Boolean) this.ghostHandAutoFirework.get()).booleanValue() && this.ghostHandFireworkTimer.passedMs(((Integer) this.ghostHandFireworkDelay.get()).intValue())) {
                    useGhostHandFirework();
                    this.ghostHandFireworkTimer.reset();
                }
            }
        }
    }

    private void autoManageElytra() {
        int elytraSlot;
        if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
            return;
        }
        boolean hasElytraEquipped = false;
        Iterator it = this.mc.field_1724.method_5661().iterator();
        while (true) {
            if (!it.hasNext()) {
                break;
            }
            if (((class_1799) it.next()).method_7909() instanceof class_1770) {
                hasElytraEquipped = true;
                this.hasElytra = true;
                break;
            }
        }
        if (!hasElytraEquipped && (elytraSlot = findElytraInInventory()) != -1) {
            equipElytra(elytraSlot);
        }
        if (((Boolean) this.infiniteDura.get()).booleanValue() && hasElytraEquipped) {
            for (class_1799 is : this.mc.field_1724.method_5661()) {
                if ((is.method_7909() instanceof class_1770) && !class_1770.method_7804(is)) {
                    repairElytra();
                    return;
                }
            }
        }
    }

    private int findElytraInInventory() {
        for (int i = 0; i < 9; i++) {
            class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
            if (stack.method_7909() instanceof class_1770) {
                return i;
            }
        }
        for (int i2 = 9; i2 < this.mc.field_1724.method_31548().method_5439(); i2++) {
            class_1799 stack2 = this.mc.field_1724.method_31548().method_5438(i2);
            if (stack2.method_7909() instanceof class_1770) {
                return i2;
            }
        }
        return -1;
    }

    private void equipElytra(int slot) {
        if (this.mc.field_1724 == null || this.mc.field_1761 == null) {
            return;
        }
        this.mc.field_1761.method_2906(this.mc.field_1724.field_7512.field_7763, slot, 0, class_1713.field_7790, this.mc.field_1724);
        this.mc.field_1761.method_2906(this.mc.field_1724.field_7512.field_7763, 6, 0, class_1713.field_7790, this.mc.field_1724);
        this.mc.field_1761.method_2906(this.mc.field_1724.field_7512.field_7763, slot, 0, class_1713.field_7790, this.mc.field_1724);
        this.hasElytra = true;
    }

    private void repairElytra() {
        if (this.mc.field_1724 == null || this.mc.field_1761 == null) {
            return;
        }
        this.mc.field_1761.method_2906(this.mc.field_1724.field_7512.field_7763, 6, 0, class_1713.field_7790, this.mc.field_1724);
        this.mc.field_1761.method_2906(this.mc.field_1724.field_7512.field_7763, 6, 0, class_1713.field_7790, this.mc.field_1724);
    }

    private void useGhostHandFirework() {
        if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
            return;
        }
        for (class_1671 class_1671Var : this.mc.field_1687.method_18112()) {
            if (class_1671Var instanceof class_1671) {
                class_1671 fireworkRocket = class_1671Var;
                if (fireworkRocket.method_24921() == this.mc.field_1724) {
                    return;
                }
            }
        }
        if (this.mc.field_1724.method_6047().method_7909() == class_1802.field_8639) {
            if (((Boolean) this.ghostHandPacket.get()).booleanValue()) {
                this.mc.method_1562().method_52787(new class_2886(class_1268.field_5808, 0, this.mc.field_1724.method_36454(), this.mc.field_1724.method_36455()));
                return;
            } else {
                this.mc.field_1761.method_2919(this.mc.field_1724, class_1268.field_5808);
                return;
            }
        }
        int fireworkSlot = InventoryUtil.findItem(class_1802.field_8639);
        if (fireworkSlot != -1) {
            int oldSlot = this.mc.field_1724.method_31548().field_7545;
            InventoryUtil.switchToSlot(fireworkSlot);
            if (((Boolean) this.ghostHandPacket.get()).booleanValue()) {
                this.mc.method_1562().method_52787(new class_2886(class_1268.field_5808, 0, this.mc.field_1724.method_36454(), this.mc.field_1724.method_36455()));
            } else {
                this.mc.field_1761.method_2919(this.mc.field_1724, class_1268.field_5808);
            }
            InventoryUtil.switchToSlot(oldSlot);
        }
    }

    private void doControl(PlayerMoveEvent event) {
        double d;
        double d2;
        class_243 velocity = this.mc.field_1724.method_18798();
        Math.sqrt((velocity.field_1352 * velocity.field_1352) + (velocity.field_1350 * velocity.field_1350));
        if (this.mc.field_1690.field_1903.method_1434()) {
            this.mc.field_1724.method_18800(velocity.field_1352, ((Double) this.upFactor.get()).doubleValue(), velocity.field_1350);
        } else if (this.mc.field_1690.field_1832.method_1434()) {
            this.mc.field_1724.method_18800(velocity.field_1352, -((Double) this.downFactor.get()).doubleValue(), velocity.field_1350);
        }
        if (!this.mc.field_1690.field_1894.method_1434() && !this.mc.field_1690.field_1881.method_1434() && !this.mc.field_1690.field_1913.method_1434() && !this.mc.field_1690.field_1849.method_1434()) {
            if (((Boolean) this.noDrag.get()).booleanValue()) {
                this.mc.field_1724.method_18800(velocity.field_1352 * 0.99d, velocity.field_1351, velocity.field_1350 * 0.99d);
                return;
            }
            return;
        }
        float yaw = this.mc.field_1724.method_36454();
        this.mc.field_1724.method_36455();
        if (this.mc.field_1690.field_1894.method_1434()) {
            d = 1.0d;
        } else {
            d = this.mc.field_1690.field_1881.method_1434() ? -1 : 0;
        }
        double forward = d;
        if (this.mc.field_1690.field_1913.method_1434()) {
            d2 = 1.0d;
        } else {
            d2 = this.mc.field_1690.field_1849.method_1434() ? -1 : 0;
        }
        double strafe = d2;
        if (forward != 0.0d && strafe != 0.0d) {
            forward *= Math.sqrt(2.0d) / 2.0d;
            strafe *= Math.sqrt(2.0d) / 2.0d;
        }
        double rad = Math.toRadians(yaw + 90.0f);
        double dx = (forward * Math.cos(rad)) + (strafe * Math.sin(rad));
        double dz = (forward * Math.sin(rad)) - (strafe * Math.cos(rad));
        double newSpeed = ((Double) this.speed.get()).doubleValue();
        if (((Boolean) this.speedLimit.get()).booleanValue() && newSpeed > ((Double) this.maxSpeed.get()).doubleValue()) {
            newSpeed = ((Double) this.maxSpeed.get()).doubleValue();
        }
        this.mc.field_1724.method_18800(dx * newSpeed, this.mc.field_1724.method_18798().field_1351, dz * newSpeed);
    }

    private void handlePacketMode() {
        int elytra;
        if (this.mc.field_1724.method_24828()) {
            return;
        }
        this.packetDelayInt++;
        if (this.packetDelayInt > ((Integer) this.packetDelay.get()).intValue() && (elytra = InventoryUtil.findItem(class_1802.field_8833)) != -1) {
            this.mc.field_1761.method_2906(this.mc.field_1724.field_7512.field_7763, 6, elytra, class_1713.field_7791, this.mc.field_1724);
            this.mc.method_1562().method_52787(new class_2848(this.mc.field_1724, class_2848.class_2849.field_12982));
            this.mc.field_1724.method_23669();
            if (((Boolean) this.firework.get()).booleanValue() && this.fireworkTimer.passedMs(((Integer) this.fireworkDelay.get()).intValue()) && ((!this.mc.field_1724.method_6115() || !((Boolean) this.usingPause.get()).booleanValue()) && isFallFlying())) {
                useFirework();
                this.fireworkTimer.reset();
            }
            this.mc.field_1761.method_2906(this.mc.field_1724.field_7512.field_7763, 6, elytra, class_1713.field_7791, this.mc.field_1724);
            this.packetDelayInt = 0;
        }
    }

    private void useFirework() {
        if (this.mc.field_1724 == null) {
            return;
        }
        if (((Boolean) this.onlyOne.get()).booleanValue()) {
            for (class_1671 class_1671Var : this.mc.field_1687.method_18112()) {
                if (class_1671Var instanceof class_1671) {
                    class_1671 fireworkRocket = class_1671Var;
                    if (fireworkRocket.method_24921() == this.mc.field_1724) {
                        return;
                    }
                }
            }
        }
        this.fireworkTimer.reset();
        if (this.mc.field_1724.method_6047().method_7909() == class_1802.field_8639) {
            if (((Boolean) this.packetInteract.get()).booleanValue()) {
                this.mc.method_1562().method_52787(new class_2886(class_1268.field_5808, 0, this.yaw, this.rotationPitch));
                return;
            } else {
                this.mc.field_1761.method_2919(this.mc.field_1724, class_1268.field_5808);
                return;
            }
        }
        if (((Boolean) this.inventorySwap.get()).booleanValue()) {
            int fireworkSlot = InventoryUtil.findItemInventorySlot(class_1802.field_8639);
            if (fireworkSlot != -1) {
                int i = this.mc.field_1724.method_31548().field_7545;
                InventoryUtil.inventorySwap(fireworkSlot, this.mc.field_1724.method_31548().field_7545);
                if (((Boolean) this.packetInteract.get()).booleanValue()) {
                    this.mc.method_1562().method_52787(new class_2886(class_1268.field_5808, 0, this.yaw, this.rotationPitch));
                } else {
                    this.mc.field_1761.method_2919(this.mc.field_1724, class_1268.field_5808);
                }
                InventoryUtil.inventorySwap(fireworkSlot, this.mc.field_1724.method_31548().field_7545);
                return;
            }
            return;
        }
        int fireworkSlot2 = InventoryUtil.findItem(class_1802.field_8639);
        if (fireworkSlot2 != -1) {
            int oldSlot = this.mc.field_1724.method_31548().field_7545;
            InventoryUtil.switchToSlot(fireworkSlot2);
            if (((Boolean) this.packetInteract.get()).booleanValue()) {
                this.mc.method_1562().method_52787(new class_2886(class_1268.field_5808, 0, this.yaw, this.rotationPitch));
            } else {
                this.mc.field_1761.method_2919(this.mc.field_1724, class_1268.field_5808);
            }
            InventoryUtil.switchToSlot(oldSlot);
        }
    }

    private boolean recastElytra() {
        if (checkConditions() && ignoreGround()) {
            this.mc.method_1562().method_52787(new class_2848(this.mc.field_1724, class_2848.class_2849.field_12982));
            if (((Boolean) this.setFlag.get()).booleanValue()) {
                this.mc.field_1724.method_23669();
                return true;
            }
            return true;
        }
        return false;
    }

    private boolean checkConditions() {
        if (this.mc.field_1724 == null) {
            return false;
        }
        class_1799 itemStack = this.mc.field_1724.method_6118(class_1304.field_6174);
        return (this.mc.field_1724.method_31549().field_7479 || this.mc.field_1724.method_5765() || this.mc.field_1724.method_6101() || !itemStack.method_31574(class_1802.field_8833) || !class_1770.method_7804(itemStack)) ? false : true;
    }

    private boolean ignoreGround() {
        if (this.mc.field_1724 != null && !this.mc.field_1724.method_5799() && !this.mc.field_1724.method_6059(class_1294.field_5902)) {
            class_1799 itemStack = this.mc.field_1724.method_6118(class_1304.field_6174);
            if (itemStack.method_31574(class_1802.field_8833) && class_1770.method_7804(itemStack)) {
                this.mc.field_1724.method_23669();
                return true;
            }
            return false;
        }
        return false;
    }

    private boolean isFallFlying() {
        return this.mc.field_1724 != null && this.mc.field_1724.method_6128();
    }
}
