package com.dev.xalu.utils.entity;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Set;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1887;
import net.minecraft.class_2868;
import net.minecraft.class_310;
import net.minecraft.class_5321;
import net.minecraft.class_6880;
import net.minecraft.class_9304;
import net.minecraft.class_9334;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/utils/entity/InventoryUtil.class */
public class InventoryUtil {

    /* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/utils/entity/InventoryUtil$SwitchMode.class */
    public enum SwitchMode {
        None,
        Silent,
        Delay
    }

    public static void switchToSlot(int slot) {
        if (slot >= 0 && slot <= 8) {
            class_310.method_1551().field_1724.method_31548().field_7545 = slot;
        }
    }

    public static void silentSwitchToSlot(int slot) {
        if (slot >= 0 && slot <= 8 && class_310.method_1551().field_1724 != null && class_310.method_1551().method_1562() != null) {
            class_310.method_1551().method_1562().method_52787(new class_2868(slot));
        }
    }

    public static void switchToSlot(int slot, SwitchMode mode) {
        switch (mode) {
            case Silent:
                silentSwitchToSlot(slot);
                break;
            case Delay:
                switchToSlot(slot);
                break;
        }
    }

    public static int findItem(class_1792 item) {
        class_310 mc = class_310.method_1551();
        if (mc.field_1724 == null) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.field_1724.method_31548().method_5438(i).method_7909() == item) {
                return i;
            }
        }
        return -1;
    }

    public static int findItemInventorySlot(class_1792 item) {
        class_310 mc = class_310.method_1551();
        if (mc.field_1724 == null) {
            return -1;
        }
        for (int i = 9; i < mc.field_1724.method_31548().method_5439(); i++) {
            if (mc.field_1724.method_31548().method_5438(i).method_7909() == item) {
                return i;
            }
        }
        return -1;
    }

    public static void inventorySwap(int from, int to) {
        class_310 mc = class_310.method_1551();
        if (mc.field_1724 == null || mc.field_1761 == null) {
            return;
        }
        mc.field_1761.method_2906(mc.field_1724.field_7512.field_7763, from, to, class_1713.field_7791, mc.field_1724);
    }

    public static void syncInventory() {
        class_310 mc = class_310.method_1551();
        if (mc.field_1724 == null || mc.field_1761 == null) {
            return;
        }
        mc.field_1761.method_2906(mc.field_1724.field_7512.field_7763, -999, 0, class_1713.field_7790, mc.field_1724);
    }

    public static int getEnchantmentLevel(class_1799 itemStack, class_5321<class_1887> enchantment) {
        if (itemStack.method_7960()) {
            return 0;
        }
        Object2IntArrayMap object2IntArrayMap = new Object2IntArrayMap();
        getEnchantments(itemStack, object2IntArrayMap);
        return getEnchantmentLevel((Object2IntMap<class_6880<class_1887>>) object2IntArrayMap, enchantment);
    }

    public static int getEnchantmentLevel(Object2IntMap<class_6880<class_1887>> itemEnchantments, class_5321<class_1887> enchantment) {
        ObjectIterator it = Object2IntMaps.fastIterable(itemEnchantments).iterator();
        while (it.hasNext()) {
            Object2IntMap.Entry<class_6880<class_1887>> entry = (Object2IntMap.Entry) it.next();
            if (((class_6880) entry.getKey()).method_40225(enchantment)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    public static void getEnchantments(class_1799 itemStack, Object2IntMap<class_6880<class_1887>> enchantments) {
        Set<Object2IntMap.Entry<class_6880<class_1887>>> setMethod_57539;
        enchantments.clear();
        if (!itemStack.method_7960()) {
            if (itemStack.method_7909() == class_1802.field_8598) {
                setMethod_57539 = ((class_9304) itemStack.method_57825(class_9334.field_49643, class_9304.field_49385)).method_57539();
            } else {
                setMethod_57539 = itemStack.method_58657().method_57539();
            }
            Set<Object2IntMap.Entry<class_6880<class_1887>>> itemEnchantments = setMethod_57539;
            for (Object2IntMap.Entry<class_6880<class_1887>> entry : itemEnchantments) {
                enchantments.put((class_6880) entry.getKey(), entry.getIntValue());
            }
        }
    }
}
