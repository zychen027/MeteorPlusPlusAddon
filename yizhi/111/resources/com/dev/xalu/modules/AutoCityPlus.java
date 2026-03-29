package com.dev.xalu.modules;

import com.dev.xalu.XALUAddon;
import com.dev.xalu.systems.friends.Friends;
import com.dev.xalu.utils.combat.CombatUtil;
import com.dev.xalu.utils.world.BlockUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1657;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_746;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/AutoCityPlus.class */
public class AutoCityPlus extends Module {
    public static AutoCityPlus INSTANCE;
    private final SettingGroup sgGeneral;
    private final Setting<Integer> targetRange;
    public final Setting<Integer> range;
    private final Setting<Boolean> antiCrawl;
    private final Setting<Boolean> preferSelfClick;
    private final Setting<Boolean> head;
    private final Setting<Boolean> burrow;
    private final Setting<Boolean> face;
    private final Setting<Boolean> down;
    private final Setting<Boolean> surround;
    private final Setting<Boolean> ignoreFriends;
    public static final List<class_2248> hard = Arrays.asList(class_2246.field_10540, class_2246.field_10443, class_2246.field_22108, class_2246.field_22423, class_2246.field_23152, class_2246.field_22109, class_2246.field_10535);

    public AutoCityPlus() {
        super(XALUAddon.CATEGORY, "AutoCityPlus", "Automatically breaks obsidian");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.targetRange = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("TargetRange")).defaultValue(6)).min(0).sliderMax(8).build());
        this.range = this.sgGeneral.add(((IntSetting.Builder) ((IntSetting.Builder) new IntSetting.Builder().name("Range")).defaultValue(6)).min(0).sliderMax(8).build());
        this.antiCrawl = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("AntiCrawl")).defaultValue(true)).build());
        this.preferSelfClick = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("PreferSelfClick")).defaultValue(true)).build());
        this.head = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("Head")).defaultValue(false)).build());
        this.burrow = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("Burrow")).defaultValue(true)).build());
        this.face = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("Face")).defaultValue(true)).build());
        this.down = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("Down")).defaultValue(false)).build());
        this.surround = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("Surround")).defaultValue(true)).build());
        this.ignoreFriends = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("Ignore Friends")).description("Ignores friends when targeting")).defaultValue(true)).build());
        INSTANCE = this;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        class_1657 player = getClosestEnemy(((Integer) this.targetRange.get()).intValue());
        if (!((Boolean) this.preferSelfClick.get()).booleanValue() || SpeedMine.selfClickPos == null) {
            if (((Boolean) this.antiCrawl.get()).booleanValue() && this.mc.field_1724.method_20448() && canBreak(this.mc.field_1724.method_24515().method_10084()) && !this.mc.field_1724.method_24515().method_10084().equals(SpeedMine.targetPos)) {
                SpeedMine.selfClickPos = this.mc.field_1724.method_24515().method_10084();
                SpeedMine.mine(this.mc.field_1724.method_24515().method_10084());
            } else {
                if (player == null) {
                    return;
                }
                doBreak(player);
            }
        }
    }

    private class_1657 getClosestEnemy(int range) {
        class_746 class_746Var = null;
        double closestDistance = Double.MAX_VALUE;
        for (class_746 class_746Var2 : this.mc.field_1687.method_18456()) {
            if (class_746Var2 != this.mc.field_1724 && CombatUtil.isValid(class_746Var2, range) && (!((Boolean) this.ignoreFriends.get()).booleanValue() || !Friends.isFriend((class_1657) class_746Var2))) {
                double distance = this.mc.field_1724.method_5739(class_746Var2);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    class_746Var = class_746Var2;
                }
            }
        }
        return class_746Var;
    }

    private void doBreak(class_1657 player) {
        class_2338 pos = player.method_24515();
        double[] yOffset = {-0.8d, 0.3d, 2.3d, 1.1d};
        double[] xzOffset = {0.3d, -0.3d};
        for (class_1657 entity : getEnemies(((Integer) this.targetRange.get()).intValue())) {
            for (double y : yOffset) {
                for (double x : xzOffset) {
                    for (double z : xzOffset) {
                        class_2338 offsetPos = new class_2338((int) Math.floor(entity.method_23317() + x), (int) Math.floor(entity.method_23318() + y), (int) Math.floor(entity.method_23321() + z));
                        if (canBreak(offsetPos) && offsetPos.equals(SpeedMine.targetPos)) {
                            return;
                        }
                    }
                }
            }
        }
        List<Float> yList = new ArrayList<>();
        if (((Boolean) this.burrow.get()).booleanValue()) {
            yList.add(Float.valueOf(0.3f));
        }
        if (((Boolean) this.down.get()).booleanValue()) {
            yList.add(Float.valueOf(-0.8f));
        }
        if (((Boolean) this.head.get()).booleanValue()) {
            yList.add(Float.valueOf(2.3f));
        }
        if (((Boolean) this.face.get()).booleanValue()) {
            yList.add(Float.valueOf(1.1f));
        }
        Iterator<Float> it = yList.iterator();
        while (it.hasNext()) {
            double y2 = it.next().floatValue();
            for (double offset : xzOffset) {
                class_2338 offsetPos2 = new class_2338((int) Math.floor(player.method_23317() + offset), (int) Math.floor(player.method_23318() + y2), (int) Math.floor(player.method_23321() + offset));
                if (canBreak(offsetPos2)) {
                    SpeedMine.mine(offsetPos2);
                    return;
                }
            }
        }
        Iterator<Float> it2 = yList.iterator();
        while (it2.hasNext()) {
            double y3 = it2.next().floatValue();
            for (double offset2 : xzOffset) {
                for (double offset22 : xzOffset) {
                    class_2338 offsetPos3 = new class_2338((int) Math.floor(player.method_23317() + offset22), (int) Math.floor(player.method_23318() + y3), (int) Math.floor(player.method_23321() + offset2));
                    if (canBreak(offsetPos3)) {
                        SpeedMine.mine(offsetPos3);
                        return;
                    }
                }
            }
        }
        if (((Boolean) this.surround.get()).booleanValue()) {
            for (class_2350 i : class_2350.values()) {
                if (i != class_2350.field_11036 && i != class_2350.field_11033 && Math.sqrt(this.mc.field_1724.method_33571().method_1025(pos.method_10093(i).method_46558())) <= ((Integer) this.range.get()).intValue() && ((this.mc.field_1687.method_22347(pos.method_10093(i)) || pos.method_10093(i).equals(SpeedMine.targetPos)) && canPlaceCrystal(pos.method_10093(i), false))) {
                    return;
                }
            }
            ArrayList<class_2338> list = new ArrayList<>();
            for (class_2350 i2 : class_2350.values()) {
                if (i2 != class_2350.field_11036 && i2 != class_2350.field_11033 && Math.sqrt(this.mc.field_1724.method_33571().method_1025(pos.method_10093(i2).method_46558())) <= ((Integer) this.range.get()).intValue() && canBreak(pos.method_10093(i2)) && canPlaceCrystal(pos.method_10093(i2), true) && !isSurroundPos(pos.method_10093(i2))) {
                    list.add(pos.method_10093(i2));
                }
            }
            if (!list.isEmpty()) {
                SpeedMine.mine((class_2338) list.stream().min(Comparator.comparingDouble(E -> {
                    return E.method_19770(this.mc.field_1724.method_33571());
                })).get());
                return;
            }
            for (class_2350 i3 : class_2350.values()) {
                if (i3 != class_2350.field_11036 && i3 != class_2350.field_11033 && Math.sqrt(this.mc.field_1724.method_33571().method_1025(pos.method_10093(i3).method_46558())) <= ((Integer) this.range.get()).intValue() && canBreak(pos.method_10093(i3)) && canPlaceCrystal(pos.method_10093(i3), false)) {
                    list.add(pos.method_10093(i3));
                }
            }
            if (!list.isEmpty()) {
                SpeedMine.mine((class_2338) list.stream().min(Comparator.comparingDouble(E2 -> {
                    return E2.method_19770(this.mc.field_1724.method_33571());
                })).get());
            }
        }
    }

    private boolean isSurroundPos(class_2338 pos) {
        for (class_2350 i : class_2350.values()) {
            if (i != class_2350.field_11036 && i != class_2350.field_11033) {
                class_2338 self = getPlayerPos(true);
                if (self.method_10093(i).equals(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    public class_2338 getPlayerPos(boolean fix) {
        return this.mc.field_1724.method_24515();
    }

    public class_2248 getBlock(class_2338 pos) {
        return this.mc.field_1687.method_8320(pos).method_26204();
    }

    public boolean canPlaceCrystal(class_2338 pos, boolean block) {
        class_2338 obsPos = pos.method_10074();
        class_2338 boost = obsPos.method_10084();
        return (getBlock(obsPos) == class_2246.field_9987 || getBlock(obsPos) == class_2246.field_10540 || !block) && BlockUtil.noEntityBlockCrystal(boost, true, true) && BlockUtil.noEntityBlockCrystal(boost.method_10084(), true, true);
    }

    private boolean isObsidian(class_2338 pos) {
        boolean isHardBlock = hard.contains(this.mc.field_1687.method_8320(pos).method_26204());
        boolean inRange = this.mc.field_1724.method_33571().method_1022(pos.method_46558()) <= ((double) ((Integer) this.range.get()).intValue());
        class_2338 playerPos = new class_2338((int) Math.floor(this.mc.field_1724.method_23317()), (int) Math.floor(this.mc.field_1724.method_23318()), (int) Math.floor(this.mc.field_1724.method_23321()));
        pos.equals(playerPos);
        return isHardBlock && inRange;
    }

    private boolean canBreak(class_2338 pos) {
        boolean isObsidianBlock = isObsidian(pos);
        boolean isTargetPos = pos.equals(SpeedMine.targetPos);
        return isObsidianBlock || isTargetPos;
    }

    private List<class_1657> getEnemies(int range) {
        List<class_1657> enemies = new ArrayList<>();
        for (class_746 class_746Var : this.mc.field_1687.method_18456()) {
            if (class_746Var != this.mc.field_1724 && CombatUtil.isValid(class_746Var, range) && (!((Boolean) this.ignoreFriends.get()).booleanValue() || !Friends.isFriend((class_1657) class_746Var))) {
                enemies.add(class_746Var);
            }
        }
        return enemies;
    }
}
