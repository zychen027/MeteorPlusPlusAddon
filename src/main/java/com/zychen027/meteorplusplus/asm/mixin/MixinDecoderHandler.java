package com.zychen027.meteorplusplus.asm.mixin;

import com.zychen027.meteorplusplus.modules.PacketKickFix;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.DecoderHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(DecoderHandler.class)
public class MixinDecoderHandler {

    @WrapOperation(method = "decode", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/PacketCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object onDecode(PacketCodec instance, Object object, Operation<Object> original) {
        if (!PacketKickFix.isFixEnabled || !(object instanceof PacketByteBuf buf)) {
            return original.call(instance, object);
        }

        int readerIndex = buf.readerIndex();
        try {
            return original.call(instance, object);
        } catch (DecoderException e) {
            buf.readerIndex(readerIndex);

            try {
                NbtCompound nbt = buf.readNbt();
                if (nbt != null && buf.isReadable(1)) {
                    Text content = buildFallbackComponent(nbt);
                    boolean overlay = buf.readBoolean();
                    return createSystemChatPacket(content, overlay);
                }
            } catch (Exception ignored) {
            }

            buf.readerIndex(readerIndex);
            buf.skipBytes(buf.readableBytes());
            return null;
        }
    }

    private Text buildFallbackComponent(NbtCompound nbt) {
        String translate = nbt.getString("translate", "");
        if (!translate.isEmpty()) {
            if (nbt.contains("with")) {
                NbtList withList = nbt.getList("with").orElse(new NbtList());
                List<Object> args = new ArrayList<>();
                for (int i = 0; i < withList.size(); i++) {
                    args.add(extractComponent(withList.getCompound(i).orElse(new NbtCompound())));
                }
                return Text.translatable(translate, args.toArray());
            }
            return Text.translatable(translate);
        }
        String text = nbt.getString("text", "");
        if (!text.isEmpty()) {
            return Text.literal(text);
        }
        return Text.literal(nbt.toString());
    }

    private Text extractComponent(NbtCompound compound) {
        String translate = compound.getString("translate", "");
        if (!translate.isEmpty()) {
            return Text.translatable(translate);
        }
        String text = compound.getString("text", "");
        if (!text.isEmpty()) {
            return Text.literal(text);
        }
        if (compound.contains("extra")) {
            NbtList extra = compound.getList("extra").orElse(new NbtList());
            if (!extra.isEmpty()) {
                return extractComponent(extra.getCompound(0).orElse(new NbtCompound()));
            }
        }
        return Text.literal(compound.toString());
    }

    private Object createSystemChatPacket(Text content, boolean overlay) throws Exception {
        Class<?> clazz = Class.forName("net.minecraft.network.packet.s2c.play.SystemChatS2CPacket");
        var ctor = clazz.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return ctor.newInstance(content, overlay);
    }
}
