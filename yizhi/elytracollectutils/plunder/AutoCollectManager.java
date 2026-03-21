package com.zychen027.meteorplusplus.modules.elytracollectutils.plunder;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import cn.kurt6.elytraautocollect.managers.*;
import cn.kurt6.elytraautocollect.utils.*;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AutoCollectManager {
    private static final AutoCollectManager INSTANCE = new AutoCollectManager();

    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private volatile FlightState flightState = FlightState.CRUISING;
    private volatile Vec3d targetPosition = null;
    private String currentDimension = "";

    final FlightManager flightManager = new FlightManager();
    private final ShipScanner shipScanner = new ShipScanner();
    private final AtomicInteger shipsFound = new AtomicInteger(0);

    private volatile boolean landingMode = false;
    private volatile Vec3d landingTarget = null;
    private volatile boolean elytraFound = false;

    private volatile boolean lowDurabilityDetected = false;
    private volatile boolean lowFireworksDetected = false;
    private volatile Vec3d purpurTargetPosition = null;

    public static AutoCollectManager getInstance() { return INSTANCE; }

    private Vec3d getPlayerPosition(ClientPlayerEntity player) {
        return new Vec3d(player.getX(), player.getY(), player.getZ());
    }

    public void toggle() {
        boolean wasActive = isActive.getAndSet(!isActive.get());
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.translatable(isActive.get() ? "msg.plunder.toggle.on" :
                    "msg.plunder.toggle.off"), true);
            if (isActive.get()) {
                client.player.sendMessage(Text.translatable("msg.plunder.session.start"), false);
                validateConfiguration(client);
            } else {
                client.player.sendMessage(Text.translatable("msg.plunder.session.end", shipsFound.get()), false);
            }
        }
        if (!isActive.get()) {
            resetState();
            flightManager.stopMovement(client);
        }
    }

    private void validateConfiguration(MinecraftClient client) {
        ModConfig config = ModConfig.getInstance();
        client.player.sendMessage(Text.translatable("msg.plunder.config.header"), false);
        int renderDistance = client.options != null ? client.options.getViewDistance().getValue() : 12;
        int scanRadius = config.getEffectiveScanRadius();
        client.player.sendMessage(Text.translatable("msg.plunder.config.render", renderDistance, scanRadius), false);
    }

    private void resetState() {
        flightState = FlightState.CRUISING;
        targetPosition = null;
        landingMode = false;
        landingTarget = null;
        elytraFound = false;
        lowDurabilityDetected = false;
        lowFireworksDetected = false;
    }

    public void tick(MinecraftClient client) {
        if (!isActive.get() || client.player == null || client.world == null) return;

        String nowDim = client.world.getRegistryKey().getValue().toString();
        if (!nowDim.equals(currentDimension)) {
            currentDimension = nowDim;
            flightManager.discardWaypoint();
        }

        if (client.player != null && System.currentTimeMillis() % 1000 < 50) {
            String key = switch (flightState) {
                case TAKING_OFF -> "msg.plunder.state.takeoff";
                case CRUISING -> "msg.plunder.state.cruise";
                case APPROACHING_SHIP -> "msg.plunder.state.approach.ship";
                case APPROACHING_PURPUR -> "msg.plunder.state.approach.purpur";
            };
            client.player.sendMessage(Text.translatable(key, shipsFound.get()), true);
        }
        ClientPlayerEntity player = client.player;
        if (elytraFound && !landingMode) {
            initiateSafeLanding(client, player, Text.translatable("msg.plunder.landing.reason.found"));
            return;
        }
        if (!performSafetyChecks(client, player)) { toggle(); return; }
        if (landingMode) { handleSafeLanding(client, player); return; }
        handleTakeoffLogic(client, player);
        handleCurrentState(client);
        if (System.currentTimeMillis() % 30000 < 100) shipScanner.cleanup();
    }

    private boolean performSafetyChecks(MinecraftClient client, ClientPlayerEntity player) {
        if (!flightManager.tryEquipElytra(player)) {
            client.player.sendMessage(Text.translatable("msg.plunder.safety.noelytra"), false);
            return false;
        }
        if (player.getHealth() < 6.0f) {
            client.player.sendMessage(Text.translatable("msg.plunder.safety.health"), false);
            return false;
        }
        if (isElytraDurabilityLow(player)) {
            if (!lowDurabilityDetected) {
                if (tryReplaceElytra(client, player)) {
                    client.player.sendMessage(Text.translatable("msg.plunder.safety.elytra.swap"), false);
                    lowDurabilityDetected = false;
                } else {
                    int percent = getElytraDurabilityPercent(player);
                    client.player.sendMessage(Text.translatable("msg.plunder.safety.elytra.low", percent), false);
                    lowDurabilityDetected = true;
                    initiateSafeLanding(client, player, Text.translatable("msg.plunder.landing.reason.dura"));
                    return true;
                }
            }
        }
        if (!hasEnoughFireworks(player)) {
            if (!lowFireworksDetected) {
                int count = getFireworkCount(player);
                client.player.sendMessage(Text.translatable("msg.plunder.safety.fireworks.low", count), false);
                lowFireworksDetected = true;
                initiateSafeLanding(client, player, Text.translatable("msg.plunder.landing.reason.fireworks"));
                return true;
            }
        }
        return true;
    }

    private boolean tryReplaceElytra(MinecraftClient client, ClientPlayerEntity player) {
        ItemStack current = player.getEquippedStack(EquipmentSlot.CHEST);
        int currentDura = current.getMaxDamage() - current.getDamage();
        int bestSlot = -1, bestDura = currentDura;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack != null && stack.getItem() == Items.ELYTRA) {
                int d = stack.getMaxDamage() - stack.getDamage();
                if (d > bestDura + 20) { bestDura = d; bestSlot = i; }
            }
        }
        if (bestSlot != -1) {
            ItemStack better = player.getInventory().getStack(bestSlot);
            player.getInventory().setStack(bestSlot, current);
            player.equipStack(EquipmentSlot.CHEST, better);
            return true;
        }
        return false;
    }

    private boolean isElytraDurabilityLow(ClientPlayerEntity player) {
        ItemStack stack = player.getEquippedStack(EquipmentSlot.CHEST);
        if (stack == null || stack.getItem() != Items.ELYTRA) return false;
        int max = stack.getMaxDamage(), dmg = stack.getDamage();
        return (double) dmg / max > 0.85 || (max - dmg) < 50;
    }
    private int getElytraDurabilityPercent(ClientPlayerEntity player) {
        ItemStack stack = player.getEquippedStack(EquipmentSlot.CHEST);
        if (stack == null || stack.getItem() != Items.ELYTRA) return 100;
        return 100 - (int) ((double) stack.getDamage() / stack.getMaxDamage() * 100);
    }
    private boolean hasEnoughFireworks(ClientPlayerEntity player) { return getFireworkCount(player) >= 5; }
    private int getFireworkCount(ClientPlayerEntity player) {
        int c = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s != null && s.getItem() == Items.FIREWORK_ROCKET) c += s.getCount();
        }
        return c;
    }

    private void initiateSafeLanding(MinecraftClient client, ClientPlayerEntity player, Text reason) {
        landingMode = true;
        calculateSafeLandingPosition(client, player);
        client.player.sendMessage(Text.translatable("msg.plunder.landing.head").append(reason), false);
    }

    private void calculateSafeLandingPosition(MinecraftClient client, ClientPlayerEntity player) {
        Vec3d playerPos = getPlayerPosition(player);
        World world = client.world;
        ModConfig config = ModConfig.getInstance();
        Vec3d best = null;
        double bestScore = -1;

        for (int r = 50; r <= 300; r += 50) {
            for (int ang = 0; ang < 360; ang += 20) {
                double rad = Math.toRadians(ang), x = playerPos.x + Math.cos(rad) * r, z = playerPos.z + Math.sin(rad) * r;
                BlockPos g = findSafeGroundLevel(world, new BlockPos((int) x, (int) playerPos.y, (int) z));
                if (g != null) {
                    Vec3d lp = new Vec3d(x, g.getY() + 2, z);
                    double sc = evaluateLandingSpotImproved(world, lp, playerPos, config);
                    if (sc > bestScore) { bestScore = sc; best = lp; }
                }
            }
            if (bestScore > 0.8) break;
        }
        if (best != null) {
            landingTarget = best;
            client.player.sendMessage(Text.translatable("msg.plunder.landing.found"), false);
        } else {
            landingTarget = findEmergencyLandingSpot(world, playerPos);
            client.player.sendMessage(Text.translatable("msg.plunder.landing.emergency"), false);
        }
    }

    private double evaluateLandingSpotImproved(World world, Vec3d lp, Vec3d pp, ModConfig cfg) {
        double sc = 1.0; BlockPos bp = new BlockPos((int) lp.x, (int) lp.y, (int) lp.z);
        try {
            double dist = pp.distanceTo(lp);
            sc *= (dist < 30 ? 0.3 : dist < 100 ? 1.0 : dist < 200 ? 0.8 : 0.4);
            if (lp.y < cfg.minSafeHeight) sc *= 0.2;
            else if (lp.y < cfg.minSafeHeight + 20) sc *= 0.6;
            int pur = countPurpurBlocksNearby(world, bp, 8);
            if (pur > 0) sc *= Math.max(0.1, 1 - pur * 0.2);
            if (pur > 5) sc *= 0.05;
            int danger = 0, air = 0, solid = 0;
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                BlockPos cp = bp.add(x, 0, z); Block b = world.getBlockState(cp).getBlock();
                if (b == Blocks.LAVA || b == Blocks.FIRE) danger += 5;
                else if (b == Blocks.WATER) danger += 1;
                else if (b == Blocks.VOID_AIR) danger += 10;
                else if (isPurpurBlock(world, cp)) danger += 2;
                if (world.getBlockState(cp.up()).isAir() && world.getBlockState(cp.up(2)).isAir()) air++;
                if (!world.getBlockState(cp).isAir() && b != Blocks.LAVA && b != Blocks.WATER && b != Blocks.VOID_AIR) solid++;
            }
            sc *= Math.max(0.1, 1 - danger * 0.05);
            sc *= Math.min(1.0, air / 25.0 + 0.3);
            sc *= Math.min(1.0, solid / 25.0 + 0.5);
            int flat = calculateFlatnessScore(world, bp);
            sc *= (0.5 + flat / 20.0);
            Block cb = world.getBlockState(bp).getBlock();
            if (cb == Blocks.GRASS_BLOCK || cb == Blocks.DIRT) sc *= 1.2;
            else if (cb == Blocks.STONE || cb == Blocks.COBBLESTONE) sc *= 1.1;
            else if (cb == Blocks.SAND || cb == Blocks.GRAVEL) sc *= 0.9;
            return Math.max(0, Math.min(1, sc));
        } catch (Exception e) { return 0; }
    }

    private int countPurpurBlocksNearby(World world, BlockPos c, int r) {
        int cnt = 0;
        for (int x = -r; x <= r; x++) for (int y = -r / 2; y <= r / 2; y++) for (int z = -r; z <= r; z++)
            if (isPurpurBlock(world, c.add(x, y, z))) cnt++;
        return cnt;
    }
    private boolean isPurpurBlock(World world, BlockPos p) {
        try { Block b = world.getBlockState(p).getBlock();
            return b == Blocks.PURPUR_BLOCK || b == Blocks.PURPUR_PILLAR || b == Blocks.PURPUR_STAIRS || b == Blocks.PURPUR_SLAB;
        } catch (Exception e) { return false; }
    }
    private int calculateFlatnessScore(World world, BlockPos c) {
        int flat = 0, cy = c.getY();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos g = findSafeGroundLevel(world, c.add(x, 0, z));
            if (g != null && Math.abs(g.getY() - cy) <= 1) flat++;
        }
        return flat;
    }
    private Vec3d findEmergencyLandingSpot(World world, Vec3d pp) {
        BlockPos pb = new BlockPos((int) pp.x, (int) pp.y, (int) pp.z);
        for (int y = pb.getY(); y >= 0; y--) {
            BlockPos cp = new BlockPos(pb.getX(), y, pb.getZ());
            if (isSafeEmergencyLanding(world, cp)) return new Vec3d(pp.x, y + 2, pp.z);
        }
        return new Vec3d(pp.x, Math.max(80, pp.y), pp.z);
    }
    private boolean isSafeEmergencyLanding(World world, BlockPos p) {
        try { Block b = world.getBlockState(p).getBlock();
            if (b == Blocks.LAVA || b == Blocks.FIRE || b == Blocks.VOID_AIR) return false;
            if (isPurpurBlock(world, p)) return false;
            if (world.getBlockState(p).isAir()) return false;
            return world.getBlockState(p.up()).isAir() && world.getBlockState(p.up(2)).isAir();
        } catch (Exception e) { return false; }
    }

    private void handleSafeLanding(MinecraftClient client, ClientPlayerEntity player) {
        if (landingTarget == null) return;
        if (player.isOnGround()) { completeSafeLanding(client); return; }
        Vec3d playerPos = getPlayerPosition(player);
        double dist = playerPos.distanceTo(landingTarget);
        if (dist > 50) flightManager.flyTowardsWithSpeed(client, landingTarget, 0.8);
        else if (dist > 20) improvedFlyTowardsLanding(client, landingTarget, 0.6);
        else improvedFlyTowardsLanding(client, landingTarget, 0.3);
    }

    private void improvedFlyTowardsLanding(MinecraftClient client, Vec3d target, double speed) {
        ClientPlayerEntity p = client.player;
        Vec3d pp = getPlayerPosition(p);
        Vec3d dir = target.subtract(pp);
        if (dir.lengthSquared() < 0.01) return;
        dir = dir.normalize();
        float yaw = (float) (Math.atan2(dir.z, dir.x) * 180 / Math.PI) - 90;
        float pitch; double hd = pp.y - target.y;
        if (hd > 50) pitch = Math.min(25, (float) (hd / 3));
        else if (hd > 20) pitch = 15;
        else if (hd > 5) pitch = 8;
        else pitch = 2;
        p.setYaw(yaw); p.setPitch(pitch);
        if (client.options != null) {
            double dist = pp.distanceTo(target);
            if (dist > 10) client.options.forwardKey.setPressed(true);
            else client.options.forwardKey.setPressed((System.currentTimeMillis() / 300) % 2 == 0);
        }
    }

    private void completeSafeLanding(MinecraftClient client) {
        flightManager.stopMovement(client);
        if (client.player != null) {
            if (lowDurabilityDetected) client.player.sendMessage(Text.translatable("msg.plunder.landing.done.dura"), false);
            else if (lowFireworksDetected) client.player.sendMessage(Text.translatable("msg.plunder.landing.done.fireworks"), false);
            else client.player.sendMessage(Text.translatable("msg.plunder.landing.done.success"), false);
        }
        if (isActive.get()) toggle();
    }

    private BlockPos findSafeGroundLevel(World world, BlockPos sp) {
        for (int y = sp.getY(); y >= 0; y--) {
            BlockPos cp = new BlockPos(sp.getX(), y, sp.getZ());
            try { Block b = world.getBlockState(cp).getBlock();
                if (!world.getBlockState(cp).isAir() && b != Blocks.VOID_AIR && b != Blocks.LAVA && b != Blocks.WATER && !isPurpurBlock(world, cp))
                    if (world.getBlockState(cp.up()).isAir() && world.getBlockState(cp.up(2)).isAir()) return cp;
            } catch (Exception e) { continue; }
        }
        return null;
    }

    private void handleTakeoffLogic(MinecraftClient client, ClientPlayerEntity player) {
        if (!flightManager.isPlayerElytraFlying(player) && flightState != FlightState.TAKING_OFF)
            flightState = FlightState.TAKING_OFF;
    }

    private void handleCurrentState(MinecraftClient client) {
        switch (flightState) {
            case TAKING_OFF -> handleTakeoff(client);
            case CRUISING -> handleCruising(client);
            case APPROACHING_SHIP -> handleApproachShip(client);
            case APPROACHING_PURPUR -> handleApproachPurpur(client);
        }
    }

    private void handleTakeoff(MinecraftClient client) {
        if (flightManager.takeOff(client)) {
            flightState = FlightState.CRUISING;
            if (client.player != null) client.player.sendMessage(Text.translatable("msg.plunder.takeoff.success"), false);
        }
    }

    private void handleCruising(MinecraftClient client) {
        ClientPlayerEntity p = client.player; ModConfig cfg = ModConfig.getInstance();
        Vec3d playerPos = getPlayerPosition(p);
        if (flightManager.getCurrentWaypoint() == null || playerPos.distanceTo(flightManager.getCurrentWaypoint()) < 50)
            flightManager.generateNextWaypoint(p, cfg);
        flightManager.flyTowardsWithSpeed(client, flightManager.getCurrentWaypoint(), cfg.cruiseSpeed);
        if (System.currentTimeMillis() % 300 < 100) shipScanner.scanForEndShipsAsync(client);
    }

    private void handleApproachShip(MinecraftClient client) {
        if (targetPosition == null) { flightState = FlightState.CRUISING; return; }
        ClientPlayerEntity p = client.player;
        Vec3d playerPos = getPlayerPosition(p);
        if (playerPos.distanceTo(targetPosition) < 30.0) {
            elytraFound = true;
            if (client.player != null) client.player.sendMessage(Text.translatable("msg.plunder.ship.found"), false);
            initiateSafeLanding(client, p, Text.translatable("msg.plunder.landing.reason.found"));
        } else {
            flightManager.flyTowardsWithSpeed(client, targetPosition, ModConfig.getInstance().approachSpeed);
        }
    }

    private volatile long lastPurpurApproachTime = 0;
    private static final long MIN_PURPUR_APPROACH_INTERVAL = 30000;

    private void handleApproachPurpur(MinecraftClient client) {
        if (purpurTargetPosition == null) { flightState = FlightState.CRUISING; return; }
        ClientPlayerEntity p = client.player;
        Vec3d playerPos = getPlayerPosition(p);
        if (playerPos.distanceTo(purpurTargetPosition) < 50.0) {
            flightState = FlightState.CRUISING;
            purpurTargetPosition = null;
            shipScanner.scanForEndShipsAsync(client);
            if (client.player != null) client.player.sendMessage(Text.translatable("msg.plunder.purpur.reached"), false);
        } else {
            flightManager.flyTowardsWithSpeed(client, purpurTargetPosition, ModConfig.getInstance().cruiseSpeed);
            if (System.currentTimeMillis() % 1000 < 100) shipScanner.scanForEndShipsAsync(client);
        }
    }

    public void setPurpurTargetPosition(Vec3d pos) {
        if (flightState == FlightState.APPROACHING_SHIP || flightState == FlightState.APPROACHING_PURPUR) return;
        long now = System.currentTimeMillis();
        if (now - lastPurpurApproachTime < MIN_PURPUR_APPROACH_INTERVAL) return;
        purpurTargetPosition = pos; flightState = FlightState.APPROACHING_PURPUR; lastPurpurApproachTime = now;
        if (MinecraftClient.getInstance().player != null)
            MinecraftClient.getInstance().player.sendMessage(Text.translatable("msg.plunder.purpur.found"), false);
    }

    public boolean isActive() { return isActive.get(); }
    public void setTargetPosition(Vec3d pos) { targetPosition = pos; flightState = FlightState.APPROACHING_SHIP; }
    public void incrementShipsFound() { shipsFound.incrementAndGet(); }
}
