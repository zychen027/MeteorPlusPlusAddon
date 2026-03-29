package com.dev.xalu.modules;

import com.dev.xalu.XALUAddon;
import com.dev.xalu.systems.friends.Friends;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1657;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_2828;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/FollowModule.class */
public class FollowModule extends Module {
    public static FollowModule INSTANCE;
    private class_1657 target;
    private float[] serverRotation;
    private float[] savedRotation;
    private boolean targetNotFoundWarned;
    private final SettingGroup sgGeneral;
    private final SettingGroup sgTargeting;
    private final Setting<TargetMode> targetMode;
    private final Setting<String> targetName;
    private final Setting<Double> targetRange;
    private final Setting<Boolean> ignoreFriends;
    private final Setting<Boolean> switchTarget;
    private final Setting<Double> followDistance;
    private final Setting<Boolean> silentRotation;
    private final Setting<Boolean> stopOnSneak;

    public FollowModule() {
        super(XALUAddon.CATEGORY, "Follow", "Automatically follows a player");
        this.serverRotation = new float[]{0.0f, 0.0f};
        this.savedRotation = new float[]{0.0f, 0.0f};
        this.targetNotFoundWarned = false;
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgTargeting = this.settings.createGroup("Targeting");
        this.targetMode = this.sgTargeting.add(((EnumSetting.Builder) ((EnumSetting.Builder) ((EnumSetting.Builder) new EnumSetting.Builder().name("target-mode")).description("How to select target")).defaultValue(TargetMode.Closest)).build());
        this.targetName = this.sgTargeting.add(((StringSetting.Builder) ((StringSetting.Builder) ((StringSetting.Builder) ((StringSetting.Builder) new StringSetting.Builder().name("target-name")).description("Specific player name to follow (leave empty for auto)")).defaultValue("")).visible(() -> {
            return this.targetMode.get() == TargetMode.Specific;
        })).build());
        this.targetRange = this.sgTargeting.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("target-range")).description("Maximum range to search for targets")).defaultValue(50.0d).min(1.0d).sliderMax(100.0d).build());
        this.ignoreFriends = this.sgTargeting.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("ignore-friends")).description("Ignore friends")).defaultValue(true)).build());
        this.switchTarget = this.sgTargeting.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("switch-target")).description("Switch to closer target if available")).defaultValue(true)).build());
        this.followDistance = this.sgGeneral.add(((DoubleSetting.Builder) ((DoubleSetting.Builder) new DoubleSetting.Builder().name("distance")).description("Follow distance")).defaultValue(3.0d).min(1.0d).sliderMax(10.0d).build());
        this.silentRotation = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("silent-rotation")).description("Silently rotate without moving client view")).defaultValue(true)).build());
        this.stopOnSneak = this.sgGeneral.add(((BoolSetting.Builder) ((BoolSetting.Builder) ((BoolSetting.Builder) new BoolSetting.Builder().name("stop-on-sneak")).description("Stop following when sneaking")).defaultValue(true)).build());
        INSTANCE = this;
    }

    /* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/FollowModule$TargetMode.class */
    public enum TargetMode {
        Closest("Closest"),
        MouseClosest("Mouse Closest"),
        Specific("Specific");

        private final String title;

        TargetMode(String title) {
            this.title = title;
        }

        @Override // java.lang.Enum
        public String toString() {
            return this.title;
        }
    }

    public void onActivate() {
        if (this.mc.field_1687 == null || this.mc.field_1724 == null) {
            toggle();
            return;
        }
        this.savedRotation[0] = this.mc.field_1724.method_36454();
        this.savedRotation[1] = this.mc.field_1724.method_36455();
        this.serverRotation[0] = this.mc.field_1724.method_36454();
        this.serverRotation[1] = this.mc.field_1724.method_36455();
        this.targetNotFoundWarned = false;
        findTarget();
    }

    public void onDeactivate() {
        this.target = null;
        this.targetNotFoundWarned = false;
        if (this.mc.field_1724 != null) {
            this.mc.field_1690.field_1894.method_23481(false);
            this.mc.field_1690.field_1913.method_23481(false);
            this.mc.field_1690.field_1849.method_23481(false);
            this.mc.field_1690.field_1881.method_23481(false);
            this.mc.field_1690.field_1903.method_23481(false);
            this.mc.field_1724.method_36456(this.savedRotation[0]);
            this.mc.field_1724.method_36457(this.savedRotation[1]);
        }
    }

    public String getInfoString() {
        if (this.target == null) {
            return null;
        }
        return this.target.method_5477().getString();
    }

    private void findTarget() {
        if (this.mc.field_1687 == null || this.mc.field_1724 == null) {
            return;
        }
        if (this.targetMode.get() == TargetMode.Specific) {
            findSpecificTarget();
        } else {
            findAutoTarget();
        }
    }

    private void findSpecificTarget() {
        if (this.mc.field_1687 == null || this.mc.field_1724 == null) {
            return;
        }
        String name = (String) this.targetName.get();
        if (name.isEmpty()) {
            if (!this.targetNotFoundWarned) {
                this.mc.field_1724.method_7353(class_2561.method_30163("§c[XALU] 请设置要跟随的玩家名字"), false);
                this.targetNotFoundWarned = true;
                return;
            }
            return;
        }
        try {
            List<class_1657> players = new ArrayList<>(this.mc.field_1687.method_18456());
            for (class_1657 class_1657Var : players) {
                if (class_1657Var != null && class_1657Var != this.mc.field_1724 && (!((Boolean) this.ignoreFriends.get()).booleanValue() || !Friends.isFriend(class_1657Var))) {
                    if (class_1657Var.method_5477() != null && class_1657Var.method_5477().getString().equalsIgnoreCase(name)) {
                        this.target = class_1657Var;
                        this.targetNotFoundWarned = false;
                        this.mc.field_1724.method_7353(class_2561.method_30163("§a[XALU] 正在跟随: " + name), false);
                        return;
                    }
                }
            }
            if (!this.targetNotFoundWarned) {
                this.mc.field_1724.method_7353(class_2561.method_30163("§c[XALU] 找不到玩家: " + name), false);
                this.targetNotFoundWarned = true;
            }
        } catch (Exception e) {
        }
    }

    private void findAutoTarget() {
        if (this.mc.field_1687 == null || this.mc.field_1724 == null) {
            return;
        }
        try {
            List<class_1657> players = new ArrayList<>(this.mc.field_1687.method_18456());
            this.target = players.stream().filter(player -> {
                return (player == null || player == this.mc.field_1724) ? false : true;
            }).filter(player2 -> {
                return player2.method_5805();
            }).filter(player3 -> {
                return ((double) player3.method_5739(this.mc.field_1724)) <= ((Double) this.targetRange.get()).doubleValue();
            }).filter(player4 -> {
                return (((Boolean) this.ignoreFriends.get()).booleanValue() && Friends.isFriend(player4)) ? false : true;
            }).min(getComparator()).orElse(null);
            if (this.target != null) {
                this.targetNotFoundWarned = false;
                this.mc.field_1724.method_7353(class_2561.method_30163("§a[XALU] 正在跟随: " + this.target.method_5477().getString()), false);
            } else if (!this.targetNotFoundWarned) {
                this.mc.field_1724.method_7353(class_2561.method_30163("§c[XALU] 找不到目标"), false);
                this.targetNotFoundWarned = true;
            }
        } catch (Exception e) {
            this.target = null;
        }
    }

    private Comparator<class_1657> getComparator() {
        switch ((TargetMode) this.targetMode.get()) {
            case Closest:
                return Comparator.comparingDouble(p -> {
                    try {
                        return this.mc.field_1724.method_5739(p);
                    } catch (Exception e) {
                        return Double.MAX_VALUE;
                    }
                });
            case MouseClosest:
                return Comparator.comparingDouble(p2 -> {
                    try {
                        return getDistanceToMouse(p2);
                    } catch (Exception e) {
                        return Double.MAX_VALUE;
                    }
                });
            default:
                return Comparator.comparingDouble(p3 -> {
                    try {
                        return this.mc.field_1724.method_5739(p3);
                    } catch (Exception e) {
                        return Double.MAX_VALUE;
                    }
                });
        }
    }

    private double getDistanceToMouse(class_1657 player) {
        class_243 playerPos;
        class_243 eyePos;
        if (this.mc.field_1724 == null || player == null || (playerPos = player.method_19538()) == null || (eyePos = this.mc.field_1724.method_33571()) == null) {
            return Double.MAX_VALUE;
        }
        float yaw = this.mc.field_1724.method_36454();
        float pitch = this.mc.field_1724.method_36455();
        class_243 lookVec = getRotationVector(yaw, pitch);
        class_243 playerToTarget = playerPos.method_1020(eyePos).method_1029();
        return 1.0d - lookVec.method_1026(playerToTarget);
    }

    private class_243 getRotationVector(float yaw, float pitch) {
        float f = pitch * 0.017453292f;
        float g = (-yaw) * 0.017453292f;
        float h = (float) Math.cos(f);
        float i = (float) Math.sin(f);
        float j = (float) Math.cos(g);
        float k = (float) Math.sin(g);
        return new class_243(j * h, -i, k * h);
    }

    private float[] getRotationToTarget(class_243 targetPos) {
        if (this.mc.field_1724 == null || targetPos == null) {
            return new float[]{0.0f, 0.0f};
        }
        class_243 playerPos = this.mc.field_1724.method_33571();
        if (playerPos == null) {
            return new float[]{0.0f, 0.0f};
        }
        double deltaX = targetPos.field_1352 - playerPos.field_1352;
        double deltaY = targetPos.field_1351 - playerPos.field_1351;
        double deltaZ = targetPos.field_1350 - playerPos.field_1350;
        double distance = Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ));
        float yaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        float pitch = (float) (-Math.toDegrees(Math.atan2(deltaY, distance)));
        return new float[]{yaw, pitch};
    }

    private void rotate(float yaw, float pitch) {
        if (this.mc.field_1724 == null) {
            return;
        }
        if (((Boolean) this.silentRotation.get()).booleanValue()) {
            silentRotate(yaw, pitch);
        } else {
            this.mc.field_1724.method_36456(yaw);
            this.mc.field_1724.method_36457(pitch);
        }
    }

    private void silentRotate(float yaw, float pitch) {
        if (this.mc.field_1724 == null) {
            return;
        }
        this.serverRotation[0] = yaw;
        this.serverRotation[1] = pitch;
        if (this.mc.method_1562() != null) {
            this.mc.method_1562().method_52787(new class_2828.class_2831(yaw, pitch, this.mc.field_1724.method_24828()));
        }
    }

    public float[] getServerRotation() {
        return (float[]) this.serverRotation.clone();
    }

    private boolean isTargetValid() {
        if (this.target == null || this.mc.field_1724 == null || this.mc.field_1687 == null) {
            return false;
        }
        try {
            if (this.target.method_5805() && this.target.method_5739(this.mc.field_1724) <= ((Double) this.targetRange.get()).doubleValue()) {
                if (this.mc.field_1687.method_18456().contains(this.target)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.field_1687 == null || this.mc.field_1724 == null) {
            return;
        }
        if (this.target == null || !isTargetValid()) {
            this.target = null;
            findTarget();
            if (this.target == null) {
                this.mc.field_1690.field_1894.method_23481(false);
                this.mc.field_1690.field_1903.method_23481(false);
                return;
            }
        }
        if (((Boolean) this.stopOnSneak.get()).booleanValue() && this.mc.field_1724.method_5715()) {
            this.mc.field_1690.field_1894.method_23481(false);
            this.mc.field_1690.field_1913.method_23481(false);
            this.mc.field_1690.field_1849.method_23481(false);
            this.mc.field_1690.field_1881.method_23481(false);
            this.mc.field_1690.field_1903.method_23481(false);
            return;
        }
        try {
            double distance = this.mc.field_1724.method_5739(this.target);
            double targetDist = ((Double) this.followDistance.get()).doubleValue();
            if (distance > targetDist + 0.5d) {
                class_243 targetPos = this.target.method_19538();
                if (targetPos == null) {
                    this.target = null;
                    return;
                }
                float[] rotations = getRotationToTarget(targetPos);
                rotate(rotations[0], rotations[1]);
                this.mc.field_1690.field_1894.method_23481(true);
                if (targetPos.field_1351 - this.mc.field_1724.method_23318() > 1.0d) {
                    this.mc.field_1690.field_1903.method_23481(true);
                } else {
                    this.mc.field_1690.field_1903.method_23481(false);
                }
            } else if (distance < targetDist - 0.5d) {
                this.mc.field_1690.field_1894.method_23481(false);
                this.mc.field_1690.field_1903.method_23481(false);
            } else {
                this.mc.field_1690.field_1894.method_23481(false);
                this.mc.field_1690.field_1903.method_23481(false);
            }
        } catch (Exception e) {
            this.target = null;
            this.mc.field_1690.field_1894.method_23481(false);
            this.mc.field_1690.field_1903.method_23481(false);
        }
    }

    public void setTarget(String name) {
        this.targetName.set(name);
        this.targetMode.set(TargetMode.Specific);
        this.targetNotFoundWarned = false;
        if (isActive()) {
            findTarget();
        }
    }

    public class_1657 getTarget() {
        return this.target;
    }
}
