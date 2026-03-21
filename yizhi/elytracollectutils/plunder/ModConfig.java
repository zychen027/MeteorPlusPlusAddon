package com.zychen027.meteorplusplus.modules.elytracollectutils.plunder;

import net.minecraft.client.MinecraftClient;

public class ModConfig {
    private static ModConfig INSTANCE = new ModConfig();
    public final int height = 140;
    public int scanRadius = 256;
    public final double fireworkInterval = 5.0;
    public final double cruiseSpeed = 1.0;
    public final double approachSpeed = 0.5;
    public final int minSafeHeight = 40;

    public static ModConfig getInstance() { return INSTANCE; }

    public void updateScanRadius() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            int rd = client.options.getViewDistance().getValue();
            this.scanRadius = Math.max(96, Math.min(1024, rd * 16 + 20));
        }
    }
    public int getEffectiveScanRadius() { updateScanRadius(); return scanRadius; }
    public double getDynamicStepSize() { return getEffectiveScanRadius() * 0.5; }
}
