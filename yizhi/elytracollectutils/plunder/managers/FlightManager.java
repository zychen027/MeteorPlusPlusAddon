package com.zychen027.meteorplusplus.modules.elytracollectutils.plunder.managers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import cn.kurt6.elytraautocollect.ModConfig;
import net.minecraft.world.World;

public class FlightManager {
    private static final double MIN_FLYING_SPEED = 0.1;
    private Vec3d currentWaypoint = null;
    private long lastFireworkTime = 0;
    private volatile boolean hasWarnedOnGround = false;
    private float cruiseYaw = 0.0f;

    private long takeoffStartTime = 0;
    private int jumpAttempts = 0;
    private static final int MAX_JUMP_ATTEMPTS = 10;
    private static final long TAKEOFF_TIMEOUT = 15000;

    private Vec3d getPlayerPosition(ClientPlayerEntity player) {
        return new Vec3d(player.getX(), player.getY(), player.getZ());
    }

    public boolean isPlayerElytraFlying(ClientPlayerEntity player) {
        return hasElytraEquipped(player) && player.isGliding() &&
                player.getVelocity().length() > MIN_FLYING_SPEED;
    }

    public boolean hasElytraEquipped(ClientPlayerEntity player) {
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        return chest != null && chest.getItem() == Items.ELYTRA;
    }

    public boolean tryEquipElytra(ClientPlayerEntity player) {
        if (hasElytraEquipped(player)) return true;
        int slot = findBestElytraInInventory(player);
        if (slot == -1) return false;
        ItemStack elytra = player.getInventory().getStack(slot);
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        player.getInventory().setStack(slot, chest);
        player.equipStack(EquipmentSlot.CHEST, elytra);
        return true;
    }

    private int findBestElytraInInventory(ClientPlayerEntity player) {
        int best = -1, bestDura = -1;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s != null && s.getItem() == Items.ELYTRA) {
                int d = s.getMaxDamage() - s.getDamage();
                if (d > bestDura) { bestDura = d; best = i; }
            }
        }
        return best;
    }

    public boolean autoDeployElytra(MinecraftClient client, ClientPlayerEntity player) {
        if (!hasElytraEquipped(player)) return false;
        if (player.isOnGround()) {
            if (takeoffStartTime == 0) { takeoffStartTime = System.currentTimeMillis(); jumpAttempts = 0; }
            if (System.currentTimeMillis() - takeoffStartTime > TAKEOFF_TIMEOUT) {
                if (client.player != null) client.player.sendMessage(Text.translatable("msg.plunder.takeoff.timeout"), false);
                resetTakeoffState(); return false;
            }
            if (jumpAttempts < MAX_JUMP_ATTEMPTS) {
                if (client.options != null) {
                    client.options.jumpKey.setPressed(true);
                    new Thread(() -> {
                        try {
                            Thread.sleep(100);
                            client.execute(() -> {
                                if (client.options != null) client.options.jumpKey.setPressed(false);
                            });
                        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }).start();
                    jumpAttempts++;
                }
            }
            useFireworkIfNeeded(client, player);
            return false;
        }
        if (!player.isOnGround() && !player.isGliding()) {
            if (player.fallDistance > 1.0f || player.getVelocity().y < -0.3) {
                if (client.options != null) {
                    client.options.jumpKey.setPressed(true);
                    new Thread(() -> {
                        try {
                            Thread.sleep(200);
                            client.execute(() -> {
                                if (client.options != null) client.options.jumpKey.setPressed(false);
                            });
                        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }).start();
                }
                useFireworkIfNeeded(client, player);
            }
            return false;
        }
        if (player.isGliding()) { resetTakeoffState(); return true; }
        return false;
    }

    private void resetTakeoffState() { takeoffStartTime = 0; jumpAttempts = 0; }

    public boolean takeOff(MinecraftClient client) {
        ClientPlayerEntity p = client.player; if (p == null) return false;
        if (!tryEquipElytra(p)) {
            if (!hasWarnedOnGround && client.player != null) {
                client.player.sendMessage(Text.translatable("msg.plunder.safety.noelytra"), false);
                hasWarnedOnGround = true;
            }
            return false;
        }
        if (isPlayerElytraFlying(p)) { resetTakeoffState(); return true; }
        if (!autoDeployElytra(client, p)) {
            if (p.isOnGround() && !hasWarnedOnGround && client.player != null) {
                long elapsed = takeoffStartTime > 0 ? (System.currentTimeMillis() - takeoffStartTime) / 1000 : 0;
                client.player.sendMessage(Text.translatable("msg.plunder.takeoff.progress", elapsed, jumpAttempts, MAX_JUMP_ATTEMPTS), false);
                hasWarnedOnGround = true;
            }
            return false;
        } else hasWarnedOnGround = false;
        if (p.isGliding() && p.getVelocity().length() < MIN_FLYING_SPEED * 2) {
            useFireworkIfNeeded(client, p);
            if (client.options != null) client.options.forwardKey.setPressed(true);
            return false;
        }
        return isPlayerElytraFlying(p);
    }

    private void useFireworkIfNeeded(MinecraftClient client, ClientPlayerEntity player) {
        long now = System.currentTimeMillis();
        boolean isTakingOff = !player.isGliding() && (takeoffStartTime > 0);
        boolean bypassCooldown = isTakingOff && player.isOnGround();

        if ((bypassCooldown || now - lastFireworkTime > (long) (ModConfig.getInstance().fireworkInterval * 1000)) && hasFireworkInInventory(player)) {
            useFireworkSafely(client, player);
            if (!bypassCooldown) {
                lastFireworkTime = now;
            }
        }
    }

    private void useFireworkSafely(MinecraftClient client, ClientPlayerEntity player) {
        int slot = findFireworkSlot(player); if (slot == -1) return;
        final int fSlot = slot;
        if (slot < 9) {
            player.getInventory().setSelectedSlot(slot);
            new Thread(() -> {
                try { Thread.sleep(150); client.execute(() -> {
                    if (client.interactionManager != null && client.player != null)
                        client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                }); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }).start();
        } else {
            int hot = findEmptyHotbarSlot(player); if (hot == -1) hot = player.getInventory().getSelectedSlot();
            final int fHot = hot;
            swapSlots(client, player, fSlot, fHot);
            new Thread(() -> {
                try { Thread.sleep(300); client.execute(() -> {
                    if (client.interactionManager != null && client.player != null) {
                        client.player.getInventory().setSelectedSlot(fHot);
                        client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                    }
                }); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }).start();
        }
    }

    private void swapSlots(MinecraftClient client, ClientPlayerEntity player, int s1, int s2) {
        final int fs1 = s1 < 9 ? s1 + 36 : s1, fs2 = s2 < 9 ? s2 + 36 : s2;
        client.execute(() -> {
            if (client.interactionManager != null) {
                client.interactionManager.clickSlot(player.playerScreenHandler.syncId, fs1, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, player);
                client.interactionManager.clickSlot(player.playerScreenHandler.syncId, fs2, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, player);
                if (!player.getInventory().getStack(s1).isEmpty())
                    client.interactionManager.clickSlot(player.playerScreenHandler.syncId, fs1, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, player);
            }
        });
    }

    private int findEmptyHotbarSlot(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) { ItemStack s = p.getInventory().getStack(i); if (s == null || s.isEmpty()) return i; }
        return -1;
    }
    private boolean hasFireworkInInventory(ClientPlayerEntity p) {
        for (int i = 0; i < p.getInventory().size(); i++) { ItemStack s = p.getInventory().getStack(i); if (s != null && s.getItem() == Items.FIREWORK_ROCKET) return true; }
        return false;
    }
    private int findFireworkSlot(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) { ItemStack s = p.getInventory().getStack(i); if (s != null && s.getItem() == Items.FIREWORK_ROCKET) return i; }
        for (int i = 9; i < p.getInventory().size(); i++) { ItemStack s = p.getInventory().getStack(i); if (s != null && s.getItem() == Items.FIREWORK_ROCKET) return i; }
        return -1;
    }

    public void flyTowardsWithSpeed(MinecraftClient client, Vec3d target, double speedMult) {
        ClientPlayerEntity p = client.player; if (p == null || client.options == null) return;
        Vec3d playerPos = getPlayerPosition(p);
        Vec3d dir = target.subtract(playerPos);
        if (dir.lengthSquared() < 0.01) { client.options.forwardKey.setPressed(false); return; }
        dir = avoidObstacles(client, dir.normalize(), 20.0);
        float yaw = (float) (Math.atan2(dir.z, dir.x) * 180 / Math.PI) - 90;
        float pitch = (float) Math.toDegrees(Math.asin(-dir.y));
        pitch = Math.max(-30f, Math.min(30f, pitch * (float) speedMult));
        p.setYaw(yaw); p.setPitch(pitch);
        client.options.forwardKey.setPressed(true);
        if (speedMult > 0.7 && p.isGliding()) useFireworkIfNeeded(client, p);
    }

    public void generateNextWaypoint(ClientPlayerEntity player, ModConfig cfg) {
        if (player == null) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.gameRenderer == null || c.gameRenderer.getCamera() == null) return;

        Vec3d pos = getPlayerPosition(player);
        float yaw = cruiseYaw;
        double rad = Math.toRadians(yaw);
        double lookX = -Math.sin(rad);
        double lookZ = Math.cos(rad);
        double step = cfg.getDynamicStepSize();

        double targetHeight;
        var world = player.getEntityWorld();
        var worldKey = world.getRegistryKey();
        if (worldKey.getValue().getPath().equals("the_end")) {
            targetHeight = cfg.height;
        } else if (worldKey.getValue().getPath().equals("the_nether")) {
            targetHeight = 200;
        } else {
            targetHeight = 200;
        }

        currentWaypoint = new Vec3d(
                pos.x + lookX * step + (Math.random() - 0.5) * 20,
                targetHeight,
                pos.z + lookZ * step + (Math.random() - 0.5) * 20
        );
    }

    public void setCruiseYaw(float y) { cruiseYaw = y; }

    public Vec3d avoidObstacles(MinecraftClient client, Vec3d cur, double ahead) {
        ClientPlayerEntity p = client.player; if (p == null) return cur;
        World w = client.world;
        Vec3d pos = getPlayerPosition(p);
        Vec3d check = pos.add(p.getRotationVector().multiply(ahead));
        if (isBlocked(w, check)) {
            Vec3d[] alts = { cur.rotateY((float) Math.toRadians(30)), cur.rotateY((float) -Math.toRadians(30)), cur.add(0, 0.5, 0), cur.add(0, -0.5, 0) };
            for (Vec3d a : alts) if (!isBlocked(w, pos.add(a.normalize().multiply(ahead)))) return a.normalize();
            return new Vec3d(0, 1, 0);
        }
        return cur;
    }
    private boolean isBlocked(World w, Vec3d v) { if (w == null) return false; return !w.getBlockState(new BlockPos((int) v.x, (int) v.y, (int) v.z)).isAir(); }

    public void stopMovement(MinecraftClient client) {
        if (client.options != null) {
            client.options.forwardKey.setPressed(false);
            client.options.backKey.setPressed(false);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
            client.options.jumpKey.setPressed(false);
            client.options.sneakKey.setPressed(false);
        }
        resetTakeoffState();
    }
    public Vec3d getCurrentWaypoint() { return currentWaypoint; }
    public void discardWaypoint() {
        currentWaypoint = null;
    }
}
