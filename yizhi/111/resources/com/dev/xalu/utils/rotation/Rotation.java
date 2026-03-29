package com.dev.xalu.utils.rotation;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.class_243;
import net.minecraft.class_2828;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/utils/rotation/Rotation.class */
public class Rotation {
    public static float[] getRotation(class_243 target) {
        if (MeteorClient.mc.field_1724 == null) {
            return new float[]{0.0f, 0.0f};
        }
        class_243 playerPos = MeteorClient.mc.field_1724.method_33571();
        double deltaX = target.field_1352 - playerPos.field_1352;
        double deltaY = target.field_1351 - playerPos.field_1351;
        double deltaZ = target.field_1350 - playerPos.field_1350;
        double distance = Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ));
        float yaw = ((float) Math.toDegrees(Math.atan2(deltaZ, deltaX))) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(deltaY, distance)));
        return new float[]{yaw, pitch};
    }

    public static void snapAt(float yaw, float pitch) {
        if (MeteorClient.mc.field_1724 == null) {
            return;
        }
        MeteorClient.mc.field_1724.method_36456(yaw);
        MeteorClient.mc.field_1724.method_36457(pitch);
        if (MeteorClient.mc.field_1724.method_36455() > 90.0f) {
            MeteorClient.mc.field_1724.method_36457(90.0f);
        }
        if (MeteorClient.mc.field_1724.method_36455() < -90.0f) {
            MeteorClient.mc.field_1724.method_36457(-90.0f);
        }
    }

    public static void silentSnapAt(float yaw, float pitch) {
        if (MeteorClient.mc.field_1724 == null || MeteorClient.mc.method_1562() == null) {
            return;
        }
        MeteorClient.mc.method_1562().method_52787(new class_2828.class_2830(MeteorClient.mc.field_1724.method_23317(), MeteorClient.mc.field_1724.method_23318(), MeteorClient.mc.field_1724.method_23321(), yaw, pitch, MeteorClient.mc.field_1724.method_24828()));
    }
}
