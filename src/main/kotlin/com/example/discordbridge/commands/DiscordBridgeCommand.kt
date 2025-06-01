package com.example.discordbridge.commands

import com.example.discordbridge.DiscordBridgePlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.Bukkit

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
                sender.sendMessage(
                    Component
                        .text("Интеграция с Twitch: ")
                        .color(NamedTextColor.GRAY)
                        .append(
                            Component
                                .text(if (config.twitchChannel.isNotBlank()) "✅ Включена" else "❌ Выключена")
                                .color(if (config.twitchChannel.isNotBlank()) NamedTextColor.GREEN else NamedTextColor.RED),
                        ),
                )

                sender.sendMessage(
                    Component
                        .text("=== Фильтры ===")
                        .color(NamedTextColor.GOLD),
                )

                sender.sendMessage(
                    Component
                        .text("Список запрещённых слов: ")
                        .color(NamedTextColor.GRAY)
                        .append(
                            Component
                                .text(if (config.wordBlacklist.isNotEmpty()) config.wordBlacklist.joinToString(", ") else "—")
                                .color(NamedTextColor.WHITE),
                        ),
                )

                sender.sendMessage(
                    Component
                        .text("Белый список команд: ")
                        .color(NamedTextColor.GRAY)
                        .append(
                            Component
                                .text(if (config.commandWhitelist.isNotEmpty()) config.commandWhitelist.joinToString(", ") else "—")
                                .color(NamedTextColor.WHITE),
                        ),
                )

                sender.sendMessage(
                    Component
                        .text("Чёрный список команд: ")
                        .color(NamedTextColor.GRAY)
                        .append(
                            Component
                                .text(if (config.commandBlacklist.isNotEmpty()) config.commandBlacklist.joinToString(", ") else "—")
                                .color(NamedTextColor.WHITE),
                        ),
                )
            }
            "resetcd" -> {
                val targetName = args.getOrNull(1)
                val cooldownManager = plugin.cooldownManager

                if (targetName == null) {
                    // Сброс своего кд
                    if (sender is Player) {
                        cooldownManager.resetCooldown(sender)
                        sender.sendMessage(Component.text("✅ Ваш кулдаун сброшен!").color(NamedTextColor.GREEN))
                    } else {
                        sender.sendMessage(Component.text("Укажите игрока!").color(NamedTextColor.RED))
                    }
                } else if (targetName.equals("global", ignoreCase = true)) {
                    cooldownManager.resetGlobalCooldown()
                    sender.sendMessage(Component.text("✅ Глобальный кулдаун сброшен!").color(NamedTextColor.GREEN))
                } else {
                    val target = plugin.server.getPlayer(targetName)
                    if (target != null) {
                        cooldownManager.resetCooldown(target)
                        sender.sendMessage(
                            Component.text("✅ Кулдаун игрока ${target.name} сброшен!").color(NamedTextColor.GREEN)
                        )
                    } else {
                        sender.sendMessage(Component.text("Игрок не найден!").color(NamedTextColor.RED))
                    }
                }
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

                sender.sendMessage(
                    Component
                        .text("/$label resetcd [ник|global]")
                        .color(NamedTextColor.YELLOW)
                        .append(
                            Component
                                .text(" - Сбросить кулдаун игрока или глобальный")
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
            1 -> listOf("reload", "status", "resetcd").filter { it.startsWith(args[0].lowercase()) }

            2 -> if (args[0].equals("resetcd", true)) {
                val names = Bukkit.getOnlinePlayers().map { it.name } + "global"
                names.filter { it.startsWith(args[1], true) }
            } else emptyList()

            else -> emptyList()
        }
    }
}
