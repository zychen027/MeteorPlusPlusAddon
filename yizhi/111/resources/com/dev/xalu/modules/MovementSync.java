package com.dev.xalu.modules;

import com.dev.xalu.XALUAddon;
import meteordevelopment.meteorclient.systems.modules.Module;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/modules/MovementSync.class */
public class MovementSync extends Module {
    public static MovementSync INSTANCE;

    public MovementSync() {
        super(XALUAddon.CATEGORY, "MovementSync", "Synchronize movement with server");
        INSTANCE = this;
    }
}
