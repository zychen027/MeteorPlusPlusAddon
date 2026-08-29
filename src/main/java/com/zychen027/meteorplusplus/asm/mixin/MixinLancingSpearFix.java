package com.zychen027.meteorplusplus.asm.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zychen027.meteorplusplus.modules.SpearModelFix;
import net.minecraft.client.render.entity.state.Lancing;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Lancing.class)
public class MixinLancingSpearFix {

    private static Object getSpearComponent(ItemStack stack, ComponentType<Object> componentType, Operation<Object> original) {
        Object result = original.call(stack, componentType);
        if (result == null && DataComponentTypes.KINETIC_WEAPON.equals(componentType)) {
            Object real = SpearModelFix.getKineticWeaponComponent(stack);
            if (real != null) return real;
        }
        return result;
    }

    @WrapOperation(
        method = "positionArmForSpear",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private static Object fixSpearRender1(ItemStack instance, ComponentType<Object> componentType, Operation<Object> original) {
        return getSpearComponent(instance, componentType, original);
    }

    @WrapOperation(
        method = "method_75392",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private static Object fixSpearRender2(ItemStack instance, ComponentType<Object> componentType, Operation<Object> original) {
        return getSpearComponent(instance, componentType, original);
    }

    @WrapOperation(
        method = "method_75395",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private static Object fixSpearRender3(ItemStack instance, ComponentType<Object> componentType, Operation<Object> original) {
        return getSpearComponent(instance, componentType, original);
    }

    @WrapOperation(
        method = "method_75396",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private static Object fixSpearRender4(ItemStack instance, ComponentType<Object> componentType, Operation<Object> original) {
        return getSpearComponent(instance, componentType, original);
    }
}
