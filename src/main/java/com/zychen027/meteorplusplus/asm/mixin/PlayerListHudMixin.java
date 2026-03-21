package com.zychen027.meteorplusplus.asm.mixin;

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
        // 获取 BetterTab 模块实例
        meteordevelopment.meteorclient.systems.modules.Modules modules = 
            meteordevelopment.meteorclient.systems.modules.Modules.get();
        
        if (modules == null || playerList == null || playerList.isEmpty()) {
            return playerList;
        }
        
        com.zychen027.meteorplusplus.modules.BetterTab module = 
            modules.get(com.zychen027.meteorplusplus.modules.BetterTab.class);
        
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
        sortedList.sort(Comparator.comparingInt(entry -> {
            String playerName = entry.getProfile().getName();
            if (playerName == null) {
                return Integer.MAX_VALUE;
            }
            
            // 检查是否在置顶列表中
            int pinIndex = pinnedPlayers.indexOf(playerName);
            if (pinIndex != -1) {
                // 置顶玩家按配置顺序排在前面
                return pinIndex;
            }
            
            // 非置顶玩家排在后面
            return Integer.MAX_VALUE;
        }));

        return sortedList;
    }
}
