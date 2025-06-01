package com.example.discordbridge.commands

import com.example.discordbridge.DiscordBridgePlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class DiscordBridgeCommand(
    private val plugin: DiscordBridgePlugin,
) : CommandExecutor,
    TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (!sender.hasPermission("discordbridge.admin")) {
            sender.sendMessage(
                Component
                    .text("У вас нет прав для использования этой команды!")
                    .color(NamedTextColor.RED),
            )
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "reload" -> {
                sender.sendMessage(
                    Component
                        .text("Перезагрузка конфигурации...")
                        .color(NamedTextColor.YELLOW),
                )

                if (plugin.reload()) {
                    sender.sendMessage(
                        Component
                            .text("✅ Конфигурация успешно перезагружена!")
                            .color(NamedTextColor.GREEN),
                    )
                } else {
                    sender.sendMessage(
                        Component
                            .text("❌ Ошибка при перезагрузке конфигурации!")
                            .color(NamedTextColor.RED),
                    )
                }
            }

            "status" -> {
                val isConnected = plugin.discordBot.isConnected()
                val config = plugin.configManager

                sender.sendMessage(
                    Component
                        .text("=== Discord Bridge Status ===")
                        .color(NamedTextColor.GOLD),
                )
                sender.sendMessage(
                    Component
                        .text("Плагин: ")
                        .color(NamedTextColor.GRAY)
                        .append(
                            Component
                                .text(if (plugin.isPluginEnabled()) "✅ Включен" else "❌ Выключен")
                                .color(if (plugin.isPluginEnabled()) NamedTextColor.GREEN else NamedTextColor.RED),
                        ),
                )
                sender.sendMessage(
                    Component
                        .text("Discord бот: ")
                        .color(NamedTextColor.GRAY)
                        .append(
                            Component
                                .text(if (isConnected) "✅ Подключен" else "❌ Отключен")
                                .color(if (isConnected) NamedTextColor.GREEN else NamedTextColor.RED),
                        ),
                )
                sender.sendMessage(
                    Component
                        .text("Синхронизация чата: ")
                        .color(NamedTextColor.GRAY)
                        .append(
                            Component
                                .text(if (config.enableChatSync) "✅ Включена" else "❌ Выключена")
                                .color(if (config.enableChatSync) NamedTextColor.GREEN else NamedTextColor.RED),
                        ),
                )
                sender.sendMessage(
                    Component
                        .text("Серверные сообщения: ")
                        .color(NamedTextColor.GRAY)
                        .append(
                            Component
                                .text(if (config.enableServerMessages) "✅ Включены" else "❌ Выключены")
                                .color(if (config.enableServerMessages) NamedTextColor.GREEN else NamedTextColor.RED),
                        ),
                )
            }

            else -> {
                sender.sendMessage(
                    Component
                        .text("=== Discord Bridge Commands ===")
                        .color(NamedTextColor.GOLD),
                )
                sender.sendMessage(
                    Component
                        .text("/$label reload")
                        .color(NamedTextColor.YELLOW)
                        .append(
                            Component
                                .text(" - Перезагрузить конфигурацию")
                                .color(NamedTextColor.GRAY),
                        ),
                )
                sender.sendMessage(
                    Component
                        .text("/$label status")
                        .color(NamedTextColor.YELLOW)
                        .append(
                            Component
                                .text(" - Показать статус плагина")
                                .color(NamedTextColor.GRAY),
                        ),
                )
            }
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (!sender.hasPermission("discordbridge.admin")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> listOf("reload", "status").filter { it.startsWith(args[0].lowercase()) }
            else -> emptyList()
        }
    }
}
