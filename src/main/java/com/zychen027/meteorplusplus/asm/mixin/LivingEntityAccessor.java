package com.zychen027.meteorplusplus.asm.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for LivingEntity#ticksSinceLastAttack (1.21.11+)
 * 用于在客户端重置攻击冷却，对齐 LeavesHack 逻辑
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    
    @Accessor("ticksSinceLastAttack")
    int getTicksSinceLastAttack();
    
    @Accessor("ticksSinceLastAttack")
    void setTicksSinceLastAttack(int value);
}