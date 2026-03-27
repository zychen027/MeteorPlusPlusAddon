ackage base.client.modules.impl.move;

import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ServerboundPackets1_19;
import base.client.events.api.EventTarget;
import base.client.events.api.types.EventType;
import base.client.events.impl.EventMotion;
import base.client.events.impl.EventPacket;
import base.client.events.impl.EventWorldLoad;
import base.client.modules.Module;
import base.client.utils.PacketUtils;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

import java.util.LinkedList;
import java.util.Queue;

public class GrimFly extends Module {
private boolean FuckGrim = false, GrimLLLLLLLLLLLLLLLLLLL = false;
private int GrimACIsBestAntiCheatLOL = 0;
public final static Queue<Packet<?>> GrimAC_better_than_polar = new LinkedList<>();

@Override
public void onEnable() {
    GrimACIsBestAntiCheatLOL = 0;
    FuckGrim = GrimLLLLLLLLLLLLLLLLLLL = false;
    super.onEnable();
}

@EventTarget
public void onWorldLoad(EventWorldLoad e) {
    toggle();
    GrimACIsBestAntiCheatLOL = 0;
    FuckGrim = GrimLLLLLLLLLLLLLLLLLLL = false;
    GrimAC_better_than_polar.clear();
}

@Override
public void onDisable() {
    GrimACIsBestAntiCheatLOL = 0;
    FuckGrim = GrimLLLLLLLLLLLLLLLLLLL = false;
    while (!GrimAC_better_than_polar.isEmpty()) {
        PacketUtils.sendPacketNoEvent(GrimAC_better_than_polar.poll());
    }
    super.onDisable();
}

@EventTarget
public void onMotion(EventMotion e) {
    if (e.getType() == EventType.PRE) {
        if (FuckGrim) {
            GrimACIsBestAntiCheatLOL++;
            if (GrimACIsBestAntiCheatLOL >= 8) {
                for (int i = 0; i < 1; i++) {
                    // Start_Fall_Flying
                    UserConnection conn = Via.getManager().getConnectionManager().getConnections().iterator().next();
                    PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_19.PLAYER_COMMAND, conn);
                    wrapper.write(Types.VAR_INT, mc.thePlayer.getEntityId());
                    wrapper.write(Types.VAR_INT, 8);
                    wrapper.write(Types.VAR_INT, 0);
                    wrapper.sendToServer(Protocol1_19To1_18_2.class);
                }
            }
            GrimACIsBestAntiCheatLOL = 0;
            FuckGrim = false;
        }
    }
}

@EventTarget
public void onPacket(EventPacket event) {
    Packet<?> packet = event.getPacket();
    if (event.getType() == EventType.SEND) {
        if (packet instanceof C0FPacketConfirmTransaction && GrimLLLLLLLLLLLLLLLLLLL) {
            event.setCancelled(true);
            if (GrimAC_better_than_polar.isEmpty()) {
                FuckGrim = true;
            }
            GrimAC_better_than_polar.add(packet);
        }

        if (packet instanceof C02PacketUseEntity) {
            if (GrimLLLLLLLLLLLLLLLLLLL && !GrimAC_better_than_polar.isEmpty()) {
                while (!GrimAC_better_than_polar.isEmpty()) {
                    PacketUtils.sendPacketNoEvent(GrimAC_better_than_polar.poll());
                }
            }
        }
    }

    if (event.getType() == EventType.RECEIVE) {
        if (packet instanceof S08PacketPlayerPosLook) {
            if (GrimLLLLLLLLLLLLLLLLLLL && !GrimAC_better_than_polar.isEmpty()) {
                while (!GrimAC_better_than_polar.isEmpty()) {
                    PacketUtils.sendPacketNoEvent(GrimAC_better_than_polar.poll());
                }
            }
        }

        if (packet instanceof S12PacketEntityVelocity v) {
            if (v.getEntityID() == mc.thePlayer.getEntityId()) {
                if (GrimLLLLLLLLLLLLLLLLLLL || !GrimAC_better_than_polar.isEmpty()) {
                    return;
                }
                GrimACIsBestAntiCheatLOL = 0;
                GrimLLLLLLLLLLLLLLLLLLL = true;
                event.setCancelled(true);
            }
        }
    }
}
}