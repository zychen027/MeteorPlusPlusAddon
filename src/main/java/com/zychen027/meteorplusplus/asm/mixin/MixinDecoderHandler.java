package com.zychen027.meteorplusplus.asm.mixin;

import com.zychen027.meteorplusplus.modules.PacketKickFix;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.DecoderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DecoderHandler.class)
public class MixinDecoderHandler {
    @WrapOperation(method = "decode", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/PacketCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"))
    public Object onDecodeException(PacketCodec instance, Object o, Operation<Object> original){
        try {
            return original.call(instance, o);
        } catch (DecoderException e) {
            // 读取 PacketKickFix 模块的静态变量
            if (PacketKickFix.isFixEnabled && o instanceof ByteBuf buf) {
                // 跳过损坏的剩余字节，防止 Netty 认为数据未读取完毕而卡死
                buf.skipBytes(buf.readableBytes());
            }
            // 【关键修改】：不要 throw e; 
            // 返回 null 告诉解码器这是一个空包/丢弃的包，从而吞掉异常，阻止连接断开
            return null; 
        }
    }
}
