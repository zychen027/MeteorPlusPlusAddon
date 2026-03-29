package com.dev.xalu.modules;

import com.dev.xalu.XALUAddon;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_2560;
import net.minecraft.class_2813;
import net.minecraft.class_2828;
import net.minecraft.class_2846;
import net.minecraft.class_2848;
import net.minecraft.class_2868;
import net.minecraft.class_2886;
import net.minecraft.class_304;
import net.minecraft.class_3532;
import net.minecraft.class_3675;
import net.minecraft.class_408;
import net.minecraft.class_418;
import net.minecraft.class_498;
import net.minecraft.class_5134;
import net.minecraft.class_9334;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/NoSlow.class */
public class NoSlow extends Module {
    public static NoSlow INSTANCE;
    private final SettingGroup sgGeneral;
    private final SettingGroup sgBlocks;
    private final Setting<Boolean> strict;
    private final Setting<Boolean> airStrict;
    private final Setting<Boolean> grim;
    private final Setting<Boolean> grimNew;
    private final Setting<Boolean> strafeFix;
    private final Setting<Boolean> inventoryMove;
    private final Setting<Boolean> arrowMove;
    private final Setting<Boolean> items;
    private final Setting<Boolean> sneak;
    private final Setting<Boolean> crawl;
    private final Setting<Boolean> shields;
    private final Setting<Boolean> webs;
    private final Setting<Boolean> berryBush;
    private final Setting<Double> webSpeed;
    private final Setting<Boolean> soulSand;
    private final Setting<Boolean> honeyBlock;
    private final Setting<Boolean> slimeBlock;
    private boolean sneaking;

    public NoSlow() {
        super(XALUAddon.CATEGORY, "NoSlow", "Prevents slowing down when using items");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgBlocks = this.settings.createGroup("Blocks");
        this.strict = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("strict")).description("Strict mode for some anti-cheats")).defaultValue(false)).build());
        this.airStrict = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("air-strict")).description("Air strict mode")).defaultValue(false)).build());
        this.grim = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("grim")).description("Grim anti-cheat bypass")).defaultValue(false)).build());
        this.grimNew = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("grim-v3")).description("Grim v3 bypass")).defaultValue(false)).build());
        this.strafeFix = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("strafe-fix")).description("Fix strafe movement")).defaultValue(false)).build());
        this.inventoryMove = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("inventory-move")).description("Allow moving in inventory screens")).defaultValue(true)).build());
        SettingGroup settingGroup = this.sgGeneral;
        BoolSetting.Builder builder = (BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("arrow-move")).description("Allow looking around with arrow keys")).defaultValue(false);
        Setting<Boolean> setting = this.inventoryMove;
        Objects.requireNonNull(setting);
        this.arrowMove = settingGroup.add(((BoolSetting.Builder) builder.visible(setting::get)).build());
        this.items = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("items")).description("No slow when using items")).defaultValue(true)).build());
        this.sneak = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("sneak")).description("No slow when sneaking")).defaultValue(false)).build());
        this.crawl = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("crawl")).description("No slow when crawling")).defaultValue(false)).build());
        this.shields = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("shields")).description("No slow when using shields")).defaultValue(true)).build());
        this.webs = this.sgBlocks.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("webs")).description("No slow in cobwebs")).defaultValue(false)).build());
        this.berryBush = this.sgBlocks.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("berry-bush")).description("No slow in berry bushes")).defaultValue(false)).build());
        this.webSpeed = this.sgBlocks.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("web-multiplier")).description("Speed multiplier in webs")).defaultValue(1.0d).min(0.0d).sliderMax(1.0d).visible(() -> {
            return ((Boolean) this.webs.get()).booleanValue() || ((Boolean) this.berryBush.get()).booleanValue();
        })).build());
        this.soulSand = this.sgBlocks.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("soul-sand")).description("No slow on soul sand")).defaultValue(false)).build());
        this.honeyBlock = this.sgBlocks.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("honey-block")).description("No slow on honey blocks")).defaultValue(false)).build());
        this.slimeBlock = this.sgBlocks.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("slime-block")).description("No slow on slime blocks")).defaultValue(false)).build());
        this.sneaking = false;
        INSTANCE = this;
    }

    public void onDeactivate() {
        if (((Boolean) this.airStrict.get()).booleanValue() && this.sneaking && this.mc.field_1724 != null) {
            this.mc.method_1562().method_52787(new class_2848(this.mc.field_1724, class_2848.class_2849.field_12984));
        }
        this.sneaking = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
            return;
        }
        if (((Boolean) this.grim.get()).booleanValue() && this.mc.field_1724.method_6115() && !this.mc.field_1724.method_5715() && ((Boolean) this.items.get()).booleanValue()) {
            if (this.mc.field_1724.method_6058() == class_1268.field_5810 && checkStack(this.mc.field_1724.method_6047())) {
                sendInteractPacket(class_1268.field_5808);
            } else if (checkStack(this.mc.field_1724.method_6079())) {
                sendInteractPacket(class_1268.field_5810);
            }
        }
        if (((Boolean) this.airStrict.get()).booleanValue() && !this.mc.field_1724.method_6115()) {
            this.sneaking = false;
            this.mc.method_1562().method_52787(new class_2848(this.mc.field_1724, class_2848.class_2849.field_12984));
        }
        if (((Boolean) this.inventoryMove.get()).booleanValue() && checkScreen()) {
            long handle = this.mc.method_22683().method_4490();
            class_304[] keys = {this.mc.field_1690.field_1903, this.mc.field_1690.field_1894, this.mc.field_1690.field_1881, this.mc.field_1690.field_1849, this.mc.field_1690.field_1913};
            for (class_304 binding : keys) {
                binding.method_23481(class_3675.method_15987(handle, binding.method_1429().method_1444()));
            }
            if (((Boolean) this.arrowMove.get()).booleanValue()) {
                float yaw = this.mc.field_1724.method_36454();
                float pitch = this.mc.field_1724.method_36455();
                if (class_3675.method_15987(handle, 265)) {
                    pitch -= 3.0f;
                } else if (class_3675.method_15987(handle, 264)) {
                    pitch += 3.0f;
                } else if (class_3675.method_15987(handle, 263)) {
                    yaw -= 3.0f;
                } else if (class_3675.method_15987(handle, 262)) {
                    yaw += 3.0f;
                }
                this.mc.field_1724.method_36456(yaw);
                this.mc.field_1724.method_36457(class_3532.method_15363(pitch, -90.0f, 90.0f));
            }
        }
        if ((((Boolean) this.grim.get()).booleanValue() || ((Boolean) this.grimNew.get()).booleanValue()) && ((Boolean) this.webs.get()).booleanValue()) {
            class_238 bb = ((Boolean) this.grim.get()).booleanValue() ? this.mc.field_1724.method_5829().method_1014(1.0d) : this.mc.field_1724.method_5829();
            for (class_2338 pos : getIntersectingWebs(bb)) {
                this.mc.method_1562().method_52787(new class_2846(class_2846.class_2847.field_12973, pos, class_2350.field_11033));
            }
        }
        if (((Boolean) this.airStrict.get()).booleanValue() && !this.sneaking && checkSlowed()) {
            this.sneaking = true;
            this.mc.method_1562().method_52787(new class_2848(this.mc.field_1724, class_2848.class_2849.field_12979));
        }
        handleMovementSlowdown();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (this.mc.field_1724 == null || this.mc.field_1687 == null || this.mc.method_1542()) {
            return;
        }
        class_2828 class_2828Var = event.packet;
        if (class_2828Var instanceof class_2828) {
            class_2828 packet = class_2828Var;
            if (packet.method_36171() && ((Boolean) this.strict.get()).booleanValue() && checkSlowed()) {
                this.mc.method_1562().method_52787(new class_2868(this.mc.field_1724.method_31548().field_7545));
                return;
            }
        }
        if ((event.packet instanceof class_2813) && ((Boolean) this.strict.get()).booleanValue()) {
            if (this.mc.field_1724.method_6115()) {
                this.mc.field_1724.method_6075();
            }
            if (this.sneaking || this.mc.field_1724.method_5715()) {
                this.mc.method_1562().method_52787(new class_2848(this.mc.field_1724, class_2848.class_2849.field_12984));
            }
            if (this.mc.field_1724.method_5624()) {
                this.mc.method_1562().method_52787(new class_2848(this.mc.field_1724, class_2848.class_2849.field_12985));
            }
        }
    }

    public void handleMovementSlowdown() {
        if (this.mc.field_1724 == null) {
            return;
        }
        if ((((Boolean) this.sneak.get()).booleanValue() && this.mc.field_1724.method_5715()) || (((Boolean) this.crawl.get()).booleanValue() && this.mc.field_1724.method_20448())) {
            float f = 1.0f / ((float) this.mc.field_1724.method_45325(class_5134.field_51584));
            this.mc.field_1724.field_3913.field_3905 *= f;
            this.mc.field_1724.field_3913.field_3907 *= f;
        }
        if (checkSlowed()) {
            this.mc.field_1724.field_3913.field_3905 *= 5.0f;
            this.mc.field_1724.field_3913.field_3907 *= 5.0f;
        }
    }

    private boolean checkGrimNew() {
        return !(this.mc.field_1724.method_5715() || this.mc.field_1724.method_20448() || this.mc.field_1724.method_3144() || this.mc.field_1724.method_6014() >= 5) || (this.mc.field_1724.method_6048() > 1 && this.mc.field_1724.method_6048() % 2 != 0);
    }

    public boolean checkSlowed() {
        return (!((Boolean) this.grimNew.get()).booleanValue() || checkGrimNew()) && !this.mc.field_1724.method_3144() && !this.mc.field_1724.method_5715() && ((this.mc.field_1724.method_6115() && ((Boolean) this.items.get()).booleanValue()) || (this.mc.field_1724.method_6039() && ((Boolean) this.shields.get()).booleanValue() && !((Boolean) this.grimNew.get()).booleanValue() && !((Boolean) this.grim.get()).booleanValue()));
    }

    private boolean checkStack(class_1799 stack) {
        return (stack.method_57353().method_57832(class_9334.field_50075) || stack.method_7909() == class_1802.field_8102 || stack.method_7909() == class_1802.field_8399 || stack.method_7909() == class_1802.field_8255) ? false : true;
    }

    private void sendInteractPacket(class_1268 hand) {
        this.mc.method_1562().method_52787(new class_2886(hand, 0, this.mc.field_1724.method_36454(), this.mc.field_1724.method_36455()));
    }

    public boolean checkScreen() {
        return (this.mc.field_1755 == null || (this.mc.field_1755 instanceof class_408) || (this.mc.field_1755 instanceof class_498) || (this.mc.field_1755 instanceof class_418)) ? false : true;
    }

    public List<class_2338> getIntersectingWebs(class_238 boundingBox) {
        List<class_2338> blocks = new ArrayList<>();
        int minX = (int) Math.floor(boundingBox.field_1323);
        int maxX = (int) Math.floor(boundingBox.field_1320);
        int minY = (int) Math.floor(boundingBox.field_1322);
        int maxY = (int) Math.floor(boundingBox.field_1325);
        int minZ = (int) Math.floor(boundingBox.field_1321);
        int maxZ = (int) Math.floor(boundingBox.field_1324);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    class_2338 pos = new class_2338(x, y, z);
                    if (this.mc.field_1687.method_8320(pos).method_26204() instanceof class_2560) {
                        blocks.add(pos);
                    }
                }
            }
        }
        return blocks;
    }

    public boolean noSlow() {
        return isActive() && checkSlowed();
    }

    public boolean getStrafeFix() {
        return isActive() && ((Boolean) this.strafeFix.get()).booleanValue();
    }
}
