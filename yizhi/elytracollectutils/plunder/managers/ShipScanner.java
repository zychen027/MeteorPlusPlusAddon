package com.zychen027.meteorplusplus.modules.elytracollectutils.plunder.managers;

import cn.kurt6.elytraautocollect.AutoCollectManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import cn.kurt6.elytraautocollect.ModConfig;

import java.util.*;
import java.util.concurrent.*;

public class ShipScanner {
    private final Map<BlockPos, Long> visitedShips = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> visitedPurpurClusters = new ConcurrentHashMap<>();
    private static final long SHIP_COOLDOWN = 300000;
    private static final long PURPUR_COOLDOWN = 300000;

    private Vec3d getPlayerPosition(ClientPlayerEntity player) {
        return new Vec3d(player.getX(), player.getY(), player.getZ());
    }

    private Vec3d getEntityPosition(ItemFrameEntity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    private Vec3d getItemEntityPosition(ItemEntity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    public void scanForEndShipsAsync(MinecraftClient client) {
        try {
            ClientPlayerEntity p = client.player; World w = client.world; if (p == null || w == null) return;
            BlockPos pc = p.getBlockPos();
            int r = ModConfig.getInstance().getEffectiveScanRadius();
            List<BlockPos> purpurs = findPurpurBlockClusters(w, pc, r);
            for (BlockPos pp : purpurs) {
                if (!AutoCollectManager.getInstance().isActive()) return;
                if (isPurpurClusterProcessed(pp)) continue;
                visitedPurpurClusters.put(pp, System.currentTimeMillis());
                client.execute(() -> AutoCollectManager.getInstance().setPurpurTargetPosition(Vec3d.ofCenter(pp)));
                Vec3d ship = searchForShipNearPurpur(w, pp);
                if (ship != null && hasElytraInArea(w, new BlockPos((int) ship.x, (int) ship.y, (int) ship.z))) {
                    handleElytraFound(client, new BlockPos((int) ship.x, (int) ship.y, (int) ship.z)); return;
                }
                break;
            }
            List<BlockPos> ships = findEndShipStructures(w, pc, r);
            for (BlockPos s : ships) {
                if (!AutoCollectManager.getInstance().isActive()) return;
                if (hasElytraInArea(w, s)) { handleElytraFound(client, s); return; }
            }
            scanForDirectElytra(client, pc, r);
        } catch (Exception ignored) {}
    }

    private boolean isPurpurClusterProcessed(BlockPos p) {
        for (Map.Entry<BlockPos, Long> e : visitedPurpurClusters.entrySet())
            if (e.getKey().getSquaredDistance(p) < 2500 && System.currentTimeMillis() - e.getValue() < PURPUR_COOLDOWN) return true;
        return false;
    }

    private List<BlockPos> findPurpurBlockClusters(World w, BlockPos c, int rad) {
        Map<BlockPos, Integer> density = new HashMap<>(); int step = Math.max(8, rad / 32);
        for (int x = -rad; x <= rad; x += step) for (int z = -rad; z <= rad; z += step) for (int y = -rad / 4; y <= rad / 4; y += step) {
            if (!AutoCollectManager.getInstance().isActive()) return List.of();
            BlockPos p = c.add(x, y, z); try {
                if (isPurpurBlock(w, p) && countNearbyPurpurBlocks(w, p, 32) >= 6) density.put(p, countNearbyPurpurBlocks(w, p, 32));
            } catch (Exception e) { continue; }
        }
        return density.entrySet().stream().sorted((a, b) -> {
            int d = Integer.compare(b.getValue(), a.getValue()); if (d != 0) return d;
            return Double.compare(c.getSquaredDistance(a.getKey()), c.getSquaredDistance(b.getKey()));
        }).limit(5).map(Map.Entry::getKey).toList();
    }

    private Vec3d searchForShipNearPurpur(World w, BlockPos pc) {
        int r = 200;
        for (int x = -r; x <= r; x += 4) for (int z = -r; z <= r; z += 4) for (int y = -20; y <= 20; y += 4) {
            BlockPos p = pc.add(x, y, z); if (isShipStructurePattern(w, p)) return Vec3d.ofCenter(p);
        }
        return null;
    }

    private List<BlockPos> findEndShipStructures(World w, BlockPos c, int rad) {
        List<BlockPos> out = new ArrayList<>(); int step = Math.max(8, rad / 32);
        for (int x = -rad; x <= rad; x += step) for (int z = -rad; z <= rad; z += step) for (int y = -rad / 3; y <= rad / 3; y += step) {
            if (!AutoCollectManager.getInstance().isActive()) return out;
            BlockPos p = c.add(x, y, z); try {
                if (isShipStructurePattern(w, p)) { out.add(p); if (out.size() >= 3) return out; }
            } catch (Exception e) { continue; }
        }
        out.sort(Comparator.comparingDouble(c::getSquaredDistance)); return out;
    }

    private boolean isShipStructurePattern(World w, BlockPos p) {
        try { int pur = 0, brew = 0, head = 0;
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                Block b = w.getBlockState(p.add(x, y, z)).getBlock();
                if (isPurpurBlock(w, p.add(x, y, z))) pur++;
                else if (b == Blocks.BREWING_STAND) brew++;
                else if (b == Blocks.DRAGON_HEAD || b == Blocks.DRAGON_WALL_HEAD) head++;
            }
            return pur >= 3 || brew >= 1 || head >= 1;
        } catch (Exception e) { return false; }
    }

    private boolean isPurpurBlock(World w, BlockPos p) {
        try { Block b = w.getBlockState(p).getBlock();
            return b == Blocks.PURPUR_BLOCK || b == Blocks.PURPUR_PILLAR || b == Blocks.PURPUR_STAIRS || b == Blocks.PURPUR_SLAB;
        } catch (Exception e) { return false; }
    }

    private int countNearbyPurpurBlocks(World w, BlockPos c, int rad) {
        int cnt = 0, step = Math.max(2, rad / 8);
        for (int x = -rad; x <= rad; x += step) for (int y = -rad; y <= rad; y += step) for (int z = -rad; z <= rad; z += step) {
            if (cnt > 100) return cnt;
            if (isPurpurBlock(w, c.add(x, y, z))) cnt++;
        }
        return cnt;
    }

    private boolean hasElytraInArea(World w, BlockPos c) {
        Box box = new Box(c.getX() - 300, c.getY() - 150, c.getZ() - 300, c.getX() + 300, c.getY() + 150, c.getZ() + 300);
        List<ItemFrameEntity> frames = w.getEntitiesByClass(ItemFrameEntity.class, box, e -> e != null && !e.getHeldItemStack().isEmpty() && e.getHeldItemStack().getItem() == Items.ELYTRA);
        List<ItemEntity> drops = w.getEntitiesByClass(ItemEntity.class, box, e -> e != null && !e.getStack().isEmpty() && e.getStack().getItem() == Items.ELYTRA && e.isAlive());
        return !frames.isEmpty() || !drops.isEmpty();
    }

    private void handleElytraFound(MinecraftClient client, BlockPos pos) {
        if (isPositionProcessed(pos)) return;
        visitedShips.put(pos, System.currentTimeMillis());
        client.execute(() -> {
            AutoCollectManager mgr = AutoCollectManager.getInstance();
            mgr.setTargetPosition(Vec3d.ofCenter(pos)); mgr.incrementShipsFound();
            if (client.player != null) client.player.sendMessage(Text.translatable("msg.plunder.ship.discovered", pos.getX(), pos.getY(), pos.getZ()), false);
        });
    }

    public void scanForDirectElytra(MinecraftClient client, BlockPos pc, int rad) {
        try { World w = client.world; if (w == null) return;
            Box box = new Box(pc.getX() - rad, pc.getY() - rad / 2, pc.getZ() - rad, pc.getX() + rad, pc.getY() + rad / 2, pc.getZ() + rad);
            List<ItemFrameEntity> frames = w.getEntitiesByClass(ItemFrameEntity.class, box, e -> e != null && !e.getHeldItemStack().isEmpty() && e.getHeldItemStack().getItem() == Items.ELYTRA);
            List<ItemEntity> drops = w.getEntitiesByClass(ItemEntity.class, box, e -> e != null && !e.getStack().isEmpty() && e.getStack().getItem() == Items.ELYTRA && e.isAlive());
            if (!frames.isEmpty() || !drops.isEmpty()) {
                Vec3d center = calculateElytraCenter(frames, drops);
                BlockPos bp = new BlockPos((int) center.x, (int) center.y, (int) center.z);
                if (!isPositionProcessed(bp)) {
                    visitedShips.put(bp, System.currentTimeMillis());
                    client.execute(() -> {
                        AutoCollectManager mgr = AutoCollectManager.getInstance();
                        mgr.setTargetPosition(center); mgr.incrementShipsFound();
                        if (client.player != null) client.player.sendMessage(Text.translatable("msg.plunder.elytra.direct", bp.getX(), bp.getY(), bp.getZ()), false);
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    private Vec3d calculateElytraCenter(List<ItemFrameEntity> f, List<ItemEntity> d) {
        if (f.isEmpty() && d.isEmpty()) return Vec3d.ZERO;
        double x = 0, y = 0, z = 0; int c = 0;
        for (ItemFrameEntity e : f) { Vec3d v = getEntityPosition(e); x += v.x; y += v.y; z += v.z; c++; }
        for (ItemEntity e : d) { Vec3d v = getItemEntityPosition(e); x += v.x; y += v.y; z += v.z; c++; }
        return c > 0 ? new Vec3d(x / c, y / c, z / c) : Vec3d.ZERO;
    }

    private boolean isPositionProcessed(BlockPos p) {
        Long t = visitedShips.get(p); return t != null && System.currentTimeMillis() - t < SHIP_COOLDOWN;
    }

    public void cleanup() {
        long n = System.currentTimeMillis();
        visitedShips.entrySet().removeIf(e -> n - e.getValue() > SHIP_COOLDOWN);
        visitedPurpurClusters.entrySet().removeIf(e -> n - e.getValue() > PURPUR_COOLDOWN);
    }
}
