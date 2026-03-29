package com.dev.xalu.utils.world;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_265;
import net.minecraft.class_3532;
import net.minecraft.class_3959;
import net.minecraft.class_3965;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/utils/world/BlockUtil.class */
public class BlockUtil {
    private static final double MIN_EYE_HEIGHT = 0.4d;
    private static final double MAX_EYE_HEIGHT = 1.62d;
    private static final double MOVEMENT_THRESHOLD = 2.0E-4d;

    public static class_2248 getBlock(class_2338 pos) {
        return MeteorClient.mc.field_1687.method_8320(pos).method_26204();
    }

    public static class_2350 getClickSide(class_2338 pos) {
        class_2350 side = null;
        double range = 100.0d;
        for (class_2350 i : class_2350.values()) {
            if (canSee(pos, i) && class_3532.method_15355((float) MeteorClient.mc.field_1724.method_33571().method_1025(pos.method_10093(i).method_46558())) <= range) {
                side = i;
                range = class_3532.method_15355((float) MeteorClient.mc.field_1724.method_33571().method_1025(pos.method_10093(i).method_46558()));
            }
        }
        if (side != null) {
            return side;
        }
        class_2350 side2 = class_2350.field_11036;
        for (class_2350 i2 : class_2350.values()) {
            if (isGrimDirection(pos, i2) && class_3532.method_15355((float) MeteorClient.mc.field_1724.method_33571().method_1025(pos.method_10093(i2).method_46558())) <= range) {
                side2 = i2;
                range = class_3532.method_15355((float) MeteorClient.mc.field_1724.method_33571().method_1025(pos.method_10093(i2).method_46558()));
            }
        }
        return side2;
    }

    public static class_2350 getClickSideStrict(class_2338 pos) {
        for (class_2350 direction : class_2350.values()) {
            class_2338 offset = pos.method_10093(direction);
            if (MeteorClient.mc.field_1687.method_22347(offset) || getBlock(offset) == class_2246.field_10036) {
                return direction;
            }
        }
        return null;
    }

    public static boolean noEntityBlockCrystal(class_2338 pos, boolean hitbox, boolean render) {
        return true;
    }

    public static boolean hasCrystal(class_2338 pos) {
        return false;
    }

    public static boolean canSee(class_2338 pos, class_2350 side) {
        class_243 testVec = pos.method_46558().method_1031(((double) side.method_10163().method_10263()) * 0.5d, ((double) side.method_10163().method_10264()) * 0.5d, ((double) side.method_10163().method_10260()) * 0.5d);
        class_3965 class_3965VarMethod_17742 = MeteorClient.mc.field_1687.method_17742(new class_3959(getEyesPos(), testVec, class_3959.class_3960.field_17558, class_3959.class_242.field_1348, MeteorClient.mc.field_1724));
        return class_3965VarMethod_17742 == null || class_3965VarMethod_17742.method_17783() == class_239.class_240.field_1333;
    }

    public static class_243 getEyesPos() {
        return MeteorClient.mc.field_1724.method_33571();
    }

    private static class_238 getCombinedBox(class_2338 pos) {
        class_265 shape = MeteorClient.mc.field_1687.method_8320(pos).method_26220(MeteorClient.mc.field_1687, pos).method_1096(pos.method_10263(), pos.method_10264(), pos.method_10260());
        class_238 combined = new class_238(pos);
        for (class_238 box : shape.method_1090()) {
            double minX = Math.max(box.field_1323, combined.field_1323);
            double minY = Math.max(box.field_1322, combined.field_1322);
            double minZ = Math.max(box.field_1321, combined.field_1321);
            double maxX = Math.min(box.field_1320, combined.field_1320);
            double maxY = Math.min(box.field_1325, combined.field_1325);
            double maxZ = Math.min(box.field_1324, combined.field_1324);
            combined = new class_238(minX, minY, minZ, maxX, maxY, maxZ);
        }
        return combined;
    }

    private static boolean isIntersected(class_238 bb, class_238 other) {
        return other.field_1320 - 1.0E-7d > bb.field_1323 && other.field_1323 + 1.0E-7d < bb.field_1320 && other.field_1325 - 1.0E-7d > bb.field_1322 && other.field_1322 + 1.0E-7d < bb.field_1325 && other.field_1324 - 1.0E-7d > bb.field_1321 && other.field_1321 + 1.0E-7d < bb.field_1324;
    }

    /* JADX INFO: Thrown type has an unknown type hierarchy: java.lang.MatchException */
    /* JADX WARN: Removed duplicated region for block: B:28:0x00de A[RETURN, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:29:0x00e2 A[ORIG_RETURN, RETURN] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public static boolean isGrimDirection(net.minecraft.class_2338 r15, net.minecraft.class_2350 r16) throws java.lang.MatchException {
        /*
            Method dump skipped, instruction units count: 228
            To view this dump change 'Code comments level' option to 'DEBUG'
        */
        throw new UnsupportedOperationException("Method not decompiled: com.dev.xalu.utils.world.BlockUtil.isGrimDirection(net.minecraft.class_2338, net.minecraft.class_2350):boolean");
    }

    /* JADX INFO: renamed from: com.dev.xalu.utils.world.BlockUtil$1, reason: invalid class name */
    /* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/utils/world/BlockUtil$1.class */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$net$minecraft$util$math$Direction = new int[class_2350.values().length];

        static {
            try {
                $SwitchMap$net$minecraft$util$math$Direction[class_2350.field_11043.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$net$minecraft$util$math$Direction[class_2350.field_11035.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$net$minecraft$util$math$Direction[class_2350.field_11034.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$net$minecraft$util$math$Direction[class_2350.field_11039.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$net$minecraft$util$math$Direction[class_2350.field_11036.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$net$minecraft$util$math$Direction[class_2350.field_11033.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
        }
    }
}
