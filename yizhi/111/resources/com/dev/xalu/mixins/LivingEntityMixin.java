package com.dev.xalu.mixins;

import com.dev.xalu.modules.NoSlow;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1309;
import net.minecraft.class_1937;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* JADX INFO: loaded from: XALU Beta Version-1.2.1.jar:com/dev/xalu/mixins/LivingEntityMixin.class */
@Mixin({class_1309.class})
public abstract class LivingEntityMixin extends class_1297 {
    public LivingEntityMixin(class_1299<?> type, class_1937 world) {
        super(type, world);
    }

    @Inject(method = {"tickMovement"}, at = {@At("HEAD")})
    private void onTickMovement(CallbackInfo ci) {
        if (NoSlow.INSTANCE != null && NoSlow.INSTANCE.isActive() && (this instanceof class_746)) {
            NoSlow.INSTANCE.handleMovementSlowdown();
        }
    }
}
