package com.zychen027.meteorplusplus.modules.elytracollectutils.plunder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ElytraAutoCollectClient implements ClientModInitializer {
    private static KeyBinding toggleKey;
    private long lastTickTime = 0;
    private static final long TICK_INTERVAL = 50;

    private static final KeyBinding.Category CUSTOM_CATEGORY = KeyBinding.Category.create(
            Identifier.of("category.plunder")
    );

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.plunder.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                CUSTOM_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.currentTimeMillis();
            if (now - lastTickTime < TICK_INTERVAL) return;
            lastTickTime = now;
            handleKeyInputs(client);
            try { AutoCollectManager.getInstance().tick(client); }
            catch (Exception e) {
                if (client.player != null)
                    client.player.sendMessage(Text.translatable("msg.plunder.error", e.getMessage()), false);
                if (AutoCollectManager.getInstance().isActive()) AutoCollectManager.getInstance().toggle();
            }
        });
    }

    private void handleKeyInputs(net.minecraft.client.MinecraftClient client) {
        if (toggleKey.wasPressed()) {
            if (client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
                float y = client.gameRenderer.getCamera().getYaw();
                AutoCollectManager.getInstance().flightManager.setCruiseYaw(y);
            }
            AutoCollectManager.getInstance().toggle();
        }
    }
}
