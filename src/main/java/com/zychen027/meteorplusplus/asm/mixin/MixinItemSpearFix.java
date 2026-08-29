package com.zychen027.meteorplusplus.asm.mixin;

import com.zychen027.meteorplusplus.modules.SpearModelFix;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class MixinItemSpearFix {

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void fixSpearUseAction(ItemStack stack, CallbackInfoReturnable<UseAction> cir) {
        if (SpearModelFix.isEnabled && SpearModelFix.isSwordThatShouldBeSpear(stack)) {
            cir.setReturnValue(UseAction.SPEAR);
        }
    }

    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void fixSpearMaxUseTime(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (SpearModelFix.isEnabled && SpearModelFix.isSwordThatShouldBeSpear(stack)) {
            cir.setReturnValue(72000);
        }
    }
}
