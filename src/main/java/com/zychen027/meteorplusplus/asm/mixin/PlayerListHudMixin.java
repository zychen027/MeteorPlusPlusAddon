package com.zychen027.meteorplusplus.asm.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

/**
 * PlayerListHud Mixin - 用于修改 Tab 列表的排序
 * 将置顶玩家显示在列表顶部
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    /**
     * 修改玩家列表变量
     * 在渲染前对玩家列表进行排序
     */
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 0)
    private List<PlayerListEntry> modifyPlayerList(List<PlayerListEntry> playerList) {
        meteordevelopment.meteorclient.systems.modules.Modules modules = meteordevelopment.meteorclient.systems.modules.Modules.get();
        if (modules == null || playerList == null) {
            return playerList;
        }
        com.zychen027.meteorplusplus.modules.BetterTab module = modules.get(com.zychen027.meteorplusplus.modules.BetterTab.class);
        if (module == null || !module.isActive()) {
            return playerList;
        }

        // 获取置顶玩家列表
        List<String> pinnedPlayers = module.getPinnedPlayers();
        if (pinnedPlayers == null || pinnedPlayers.isEmpty()) {
            return playerList;
        }

        // 创建排序后的列表
        List<PlayerListEntry> sortedList = new ArrayList<>(playerList);

        // 修复：将不在当前 playerList 中但在线的置顶玩家添加到列表中
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.networkHandler != null) {
            for (PlayerListEntry entry : client.player.networkHandler.getPlayerList()) {
                String playerName = entry.getProfile().name();
                if (playerName != null && pinnedPlayers.contains(playerName)) {
                    // 检查该置顶玩家是否已在列表中
                    boolean alreadyInList = false;
                    for (PlayerListEntry existing : sortedList) {
                        if (existing.getProfile().name().equals(playerName)) {
                            alreadyInList = true;
                            break;
                        }
                    }
                    // 不在列表中则添加，确保置顶玩家一定会出现
                    if (!alreadyInList) {
                        sortedList.add(entry);
                    }
                }
            }
        }

        // 排序：置顶玩家按配置顺序排在前面，非置顶玩家排在后面
        sortedList.sort(Comparator.comparingInt(entry -> {
            String playerName = entry.getProfile().name();
            if (playerName == null) {
                return Integer.MAX_VALUE;
            }
            int pinIndex = pinnedPlayers.indexOf(playerName);
            if (pinIndex != -1) {
                return pinIndex;
            }
            return Integer.MAX_VALUE;
        }));

        return sortedList;
    }
}
