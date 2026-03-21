package com.zychen027.meteorplusplus.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import meteordevelopment.meteorclient.commands.Command
import net.minecraft.command.CommandSource
import net.minecraft.text.Text
import com.zychen027.meteorplusplus.modules.elytraautopilot.ElytraAutoPilot

/**
 * ElytraAutoPilot 命令
 * 使用 . 前缀（Meteor Client 默认）
 */
class ElytraAutoPilotCommand : Command("elytraautopilot", "鞘翅自动驾驶控制", "eap") {

    override fun build(builder: LiteralArgumentBuilder<CommandSource>) {
        // .eap flyto <x> <z>
        builder.then(
            literal("flyto")
                .then(
                    argument("x", IntegerArgumentType.integer(-2000000000, 2000000000))
                        .then(
                            argument("z", IntegerArgumentType.integer(-2000000000, 2000000000))
                                .executes {
                                    val x = IntegerArgumentType.getInteger(it, "x")
                                    val z = IntegerArgumentType.getInteger(it, "z")
                                    executeFlyTo(x, z)
                                }
                        )
                )
                .then(
                    argument("name", StringArgumentType.greedyString())
                        .executes {
                            val name = StringArgumentType.getString(it, "name")
                            executeFlyTo(name)
                        }
                )
        )

        // .eap takeoff [名称]
        builder.then(
            literal("takeoff")
                .executes {
                    executeTakeoff()
                }
                .then(
                    argument("name", StringArgumentType.greedyString())
                        .executes {
                            val name = StringArgumentType.getString(it, "name")
                            executeTakeoff(name)
                        }
                )
        )

        // .eap land
        builder.then(
            literal("land")
                .executes {
                    executeLand()
                }
        )

        // .eap flylocation set <名称> <x> <z>
        builder.then(
            literal("flylocation")
                .then(
                    literal("set")
                        .then(
                            argument("name", StringArgumentType.word())
                                .then(
                                    argument("x", IntegerArgumentType.integer(-2000000000, 2000000000))
                                        .then(
                                            argument("z", IntegerArgumentType.integer(-2000000000, 2000000000))
                                                .executes {
                                                    val name = StringArgumentType.getString(it, "name")
                                                    val x = IntegerArgumentType.getInteger(it, "x")
                                                    val z = IntegerArgumentType.getInteger(it, "z")
                                                    executeFlyLocationSet(name, x, z)
                                                }
                                        )
                                )
                        )
                )
                // .eap flylocation remove <名称>
                .then(
                    literal("remove")
                        .then(
                            argument("name", StringArgumentType.greedyString())
                                .executes {
                                    val name = StringArgumentType.getString(it, "name")
                                    executeFlyLocationRemove(name)
                                }
                        )
                )
                // .eap flylocation list
                .then(
                    literal("list")
                        .executes {
                            executeFlyLocationList()
                        }
                )
        )

        // .eap help
        builder.then(
            literal("help")
                .executes {
                    info("鞘翅自动驾驶命令帮助:")
                    info("  .eap flyto <x> <z> - 飞往坐标")
                    info("  .eap flyto <名称> - 飞往命名位置")
                    info("  .eap takeoff - 起飞")
                    info("  .eap land - 降落")
                    info("  .eap flylocation set <名称> <x> <z> - 保存位置")
                    info("  .eap flylocation remove <名称> - 删除位置")
                    info("  .eap flylocation list - 列出位置")
                    1
                }
        )
    }

    private fun executeFlyTo(x: Int, z: Int): Int {
        val module = ElytraAutoPilot.INSTANCE ?: run {
            error("鞘翅自动驾驶模块未启用")
            return 0
        }
        module.flyTo(x, z)
        return 1
    }

    private fun executeFlyTo(name: String): Int {
        val module = ElytraAutoPilot.INSTANCE ?: run {
            error("鞘翅自动驾驶模块未启用")
            return 0
        }
        if (module.flyTo(name)) {
            return 1
        }
        error("未找到位置：$name")
        return 0
    }

    private fun executeTakeoff(): Int {
        val module = ElytraAutoPilot.INSTANCE ?: run {
            error("鞘翅自动驾驶模块未启用")
            return 0
        }
        module.takeoff()
        return 1
    }

    private fun executeTakeoff(name: String): Int {
        val module = ElytraAutoPilot.INSTANCE ?: run {
            error("鞘翅自动驾驶模块未启用")
            return 0
        }
        if (module.takeoff(name)) {
            return 1
        }
        error("未找到位置：$name")
        return 0
    }

    private fun executeLand(): Int {
        val module = ElytraAutoPilot.INSTANCE ?: run {
            error("鞘翅自动驾驶模块未启用")
            return 0
        }
        module.land()
        return 1
    }

    private fun executeFlyLocationSet(name: String, x: Int, z: Int): Int {
        val module = ElytraAutoPilot.INSTANCE ?: run {
            error("鞘翅自动驾驶模块未启用")
            return 0
        }
        if (module.addFlyLocation(name, x, z)) {
            info("已保存位置：$name (x=$x, z=$z)")
            return 1
        }
        error("位置名称已存在：$name")
        return 0
    }

    private fun executeFlyLocationRemove(name: String): Int {
        val module = ElytraAutoPilot.INSTANCE ?: run {
            error("鞘翅自动驾驶模块未启用")
            return 0
        }
        if (module.removeFlyLocation(name)) {
            info("已删除位置：$name")
            return 1
        }
        error("未找到位置：$name")
        return 0
    }

    private fun executeFlyLocationList(): Int {
        val module = ElytraAutoPilot.INSTANCE ?: run {
            error("鞘翅自动驾驶模块未启用")
            return 0
        }
        val locations = module.getFlyLocations()
        if (locations.isEmpty()) {
            error("没有保存的飞行位置")
            return 0
        }
        info("飞行位置列表:")
        for (loc in locations) {
            info("  - $loc")
        }
        return 1
    }
}
