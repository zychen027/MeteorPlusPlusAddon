package com.zychen027.meteorplusplus.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import meteordevelopment.meteorclient.commands.Command
import meteordevelopment.meteorclient.utils.player.ChatUtils
import net.minecraft.command.CommandSource
import com.zychen027.meteorplusplus.modules.BetterTab

/**
 * BetterTab 命令
 * 用于管理置顶玩家列表
 */
class BetterTabCommand : Command("bettertab", "管理 Tab 菜单中的置顶玩家", "管理 Tab 菜单") {

    override fun build(builder: LiteralArgumentBuilder<CommandSource>) {
        builder.then(
            literal("add")
                .then(
                    argument("player", StringArgumentType.word())
                        .executes {
                            val playerName = StringArgumentType.getString(it, "player")
                            val module = BetterTab.INSTANCE
                            if (module != null) {
                                module.addPinnedPlayer(playerName)
                                1
                            } else {
                                0
                            }
                        }
                )
        )

        builder.then(
            literal("remove")
                .then(
                    argument("player", StringArgumentType.word())
                        .executes {
                            val playerName = StringArgumentType.getString(it, "player")
                            val module = BetterTab.INSTANCE
                            if (module != null) {
                                module.removePinnedPlayer(playerName)
                                1
                            } else {
                                0
                            }
                        }
                )
        )

        builder.then(
            literal("clear")
                .executes {
                    val module = BetterTab.INSTANCE
                    if (module != null) {
                        module.clearPinnedPlayers()
                        1
                    } else {
                        0
                    }
                }
        )

        builder.then(
            literal("list")
                .executes {
                    val module = BetterTab.INSTANCE
                    if (module != null) {
                        val players = module.getPinnedPlayers()
                        if (players.isEmpty()) {
                            ChatUtils.info("§c没有置顶玩家")
                        } else {
                            ChatUtils.info("§a置顶玩家列表 (§f${players.size}§a):")
                            players.forEach { player ->
                                ChatUtils.info("  §f- $player")
                            }
                        }
                        1
                    } else {
                        0
                    }
                }
        )

        builder.then(
            literal("help")
                .executes {
                    ChatUtils.info("§6BetterTab 命令帮助:")
                    ChatUtils.info("  §f.bettertab add <玩家> §7- 添加置顶玩家")
                    ChatUtils.info("  §f.bettertab remove <玩家> §7- 移除置顶玩家")
                    ChatUtils.info("  §f.bettertab clear §7- 清空所有置顶玩家")
                    ChatUtils.info("  §f.bettertab list §7- 查看置顶玩家列表")
                    1
                }
        )
    }
}
