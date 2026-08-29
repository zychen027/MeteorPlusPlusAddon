package com.zychen027.meteorplusplus.asm.mixin;

import com.zychen027.meteorplusplus.modules.PacketKickFix;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class MixinClientConnection {

    @Shadow
    private PacketListener packetListener;

    @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
    private void onExceptionCaught(ChannelHandlerContext ctx, Throwable throwable, CallbackInfo ci) {
        if (!PacketKickFix.isFixEnabled) return;
        if (!(throwable instanceof DecoderException || throwable instanceof EncoderException)) return;
        if (!(this.packetListener instanceof ClientPlayNetworkHandler)) return;

        ChatUtils.info("§c[PacketFix] §7跳过了一个损坏的数据包: " + throwable.getMessage());
        ci.cancel();
    }
}
