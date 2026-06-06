package com.zychen027.meteorplusplus.asm.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerMoveC2SPacket.class)
public interface IPlayerMoveC2SPacketAccessor {
    @Accessor("yaw")
    float getYaw();

    @Accessor("pitch")
    float getPitch();
}
