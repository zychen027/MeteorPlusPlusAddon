package com.zychen027.meteorplusplus.asm.mixin;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * EntityVelocityUpdateS2CPacket Accessor
 * 用于获取实体 ID 和速度值
 */
@Mixin(EntityVelocityUpdateS2CPacket.class)
public interface IEntityVelocityUpdateS2CPacket {
    @Accessor
    int getEntityId();
    
    @Accessor
    int getVelocityX();
    
    @Accessor
    int getVelocityY();
    
    @Accessor
    int getVelocityZ();
}
