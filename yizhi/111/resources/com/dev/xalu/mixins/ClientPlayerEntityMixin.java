package com.dev.xalu.mixins;

import com.dev.xalu.modules.NoSlow;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/mixins/ClientPlayerEntityMixin.class */
@Mixin({class_746.class})
public class ClientPlayerEntityMixin {
    @Inject(method = {"shouldSlowDown"}, at = {@At("HEAD")}, cancellable = true)
    private void onShouldSlowDown(CallbackInfoReturnable<Boolean> cir) {
        if (NoSlow.INSTANCE != null && NoSlow.INSTANCE.isActive() && NoSlow.INSTANCE.checkSlowed()) {
            cir.setReturnValue(false);
        }
    }
}
