package com.dev.xalu.utils.combat;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_742;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/utils/combat/CombatUtil.class */
public class CombatUtil {
    public static List<class_1657> getEnemies(double range) {
        List<class_1657> list = new ArrayList<>();
        for (class_742 player : Lists.newArrayList(MeteorClient.mc.field_1687.method_18456())) {
            if (isValid(player, range)) {
                list.add(player);
            }
        }
        return list;
    }

    public static boolean isValid(class_1297 entity, double range) {
        boolean invalid = entity == null || !entity.method_5805() || entity.equals(MeteorClient.mc.field_1724) || MeteorClient.mc.field_1724.method_19538().method_1022(entity.method_19538()) > range;
        return !invalid;
    }

    public static boolean isValid(class_1297 entity) {
        boolean invalid = entity == null || !entity.method_5805() || entity.equals(MeteorClient.mc.field_1724);
        return !invalid;
    }

    public static class_1657 getClosestEnemy(double distance) {
        class_1297 class_1297Var = null;
        Iterator<class_1657> it = getEnemies(distance).iterator();
        while (it.hasNext()) {
            class_1297 class_1297Var2 = (class_1657) it.next();
            if (class_1297Var == null) {
                class_1297Var = class_1297Var2;
            } else if (MeteorClient.mc.field_1724.method_5707(class_1297Var2.method_19538()) < MeteorClient.mc.field_1724.method_5858(class_1297Var)) {
                class_1297Var = class_1297Var2;
            }
        }
        return class_1297Var;
    }
}
