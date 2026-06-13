package com.zychen027.meteorplusplus.asm.mixin;

import com.zychen027.meteorplusplus.modules.PacketKickFix;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.DecoderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(DecoderHandler.class)
@SuppressWarnings("unchecked") // 消除泛型类型擦除带来的编译器警告
public class MixinDecoderHandler {

    // 1. 拦截解码，吞掉异常，返回 null 标记
    @Redirect(
            method = "decode",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/PacketCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private Object onDecodeException(PacketCodec instance, Object o) {
        try {
            return instance.decode(o);
        } catch (DecoderException e) {
            if (PacketKickFix.isFixEnabled && o instanceof ByteBuf buf) {
                // 跳过损坏的剩余字节，防止 Netty 卡死
                buf.skipBytes(buf.readableBytes());
            }
            // 【防踢关键1】：不抛出异常，返回 null。
            return null; 
        }
    }

    // 2. 拦截列表添加，过滤 null，防止 NPE
    @Redirect(
            method = "decode",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false)
    )
    private boolean filterNullPackets(List<Object> list, Object packet) {
        // 【防踢关键2】：阻止 null 加入 out 列表，彻底杜绝 NPE 导致的断连！
        if (packet == null) {
            return false; 
        }
        return list.add(packet);
    }
}
