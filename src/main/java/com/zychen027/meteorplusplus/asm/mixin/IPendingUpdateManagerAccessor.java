package com.zychen027.meteorplusplus.asm.mixin;

import net.minecraft.client.network.PendingUpdateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PendingUpdateManager.class)
public interface IPendingUpdateManagerAccessor {
    @Accessor("sequence")
    int getSequence();

    @Accessor("sequence")
    void setSequence(int sequence);
}
