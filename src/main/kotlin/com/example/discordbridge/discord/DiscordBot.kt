package com.example.discordbridge.discord

import com.example.discordbridge.DiscordBridgePlugin
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import net.dv8tion.jda.api.entities.emoji.Emoji

class DiscordBot(private val plugin: DiscordBridgePlugin) : ListenerAdapter() {

    private var jda: JDA? = null
    private var guild: Guild? = null
    private var channel: TextChannel? = null
    private var allowedRole: Role? = null

    fun start(): Boolean {
        val config = plugin.configManager

        if (!config.isValidConfiguration()) {
            plugin.logger.severe("Invalid Discord configuration! Please check config.yml")
            return false
        }

        return try {
            jda = JDABuilder.createDefault(config.botToken)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(this)
                .build()

            jda?.awaitReady()
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to start Discord bot: ${e.message}")
            false
        }
    }

    override fun onReady(event: ReadyEvent) {
        plugin.logger.info("Discord bot connected as: ${event.jda.selfUser.name}")

        val config = plugin.configManager

        guild = event.jda.getGuildById(config.guildId)
        if (guild == null) {
            plugin.logger.severe("Could not find guild with ID: ${config.guildId}")
            return
        }

        channel = guild?.getTextChannelById(config.channelId)
        if (channel == null) {
            plugin.logger.severe("Could not find channel with ID: ${config.channelId}")
            return
        }

        allowedRole = guild?.getRoleById(config.allowedRoleId)
        if (allowedRole == null) {
            plugin.logger.severe("Could not find role with ID: ${config.allowedRoleId}")
            return
        }

        plugin.logger.info("Discord bot fully initialized!")
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return

        if (event.channel.id != plugin.configManager.channelId) return

        val member = event.member ?: return
        if (!hasRequiredRole(member)) return

        val config = plugin.configManager
        val message = event.message.contentRaw

        if (message.startsWith(config.commandPrefix)) {
            handleCommand(event, message.substring(config.commandPrefix.length))
            return
        }

        if (config.enableChatSync) {
            handleChatMessage(event)
        }
    }

    private fun hasRequiredRole(member: Member): Boolean {
        return member.roles.any { it.id == plugin.configManager.allowedRoleId }
    }

    private fun handleCommand(event: MessageReceivedEvent, command: String) {
        val args = command.split(" ")
        val cmd = args[0].lowercase()

        when (cmd) {
            "list" -> {
                val onlinePlayers = Bukkit.getOnlinePlayers()
                val playerList = if (onlinePlayers.isEmpty()) {
                    "Нет игроков онлайн"
                } else {
                    "Игроки онлайн (${onlinePlayers.size}): ${onlinePlayers.joinToString(", ") { it.name }}"
                }
                event.channel.sendMessage("📋 **$playerList**").queue()
            }

            "say" -> {
                if (args.size < 2) {
                    event.channel.sendMessage("❌ Использование: `${plugin.configManager.commandPrefix}say <сообщение>`").queue()
                    return
                }

                val messageToSay = args.drop(1).joinToString(" ")
                val component = Component.text("[DISCORD] ")
                    .color(TextColor.color(0x5865F2))
                    .append(Component.text(event.author.name)
                        .color(TextColor.color(0xFFFFFF))
                        .decorate(TextDecoration.BOLD))
                    .append(Component.text(": $messageToSay")
                        .color(TextColor.color(0xFFFFFF)))

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    plugin.broadcastToMinecraft(component)
                })

                event.message.addReaction(Emoji.fromUnicode("✅")).queue()
            }

            "execute", "exec" -> {
                if (args.size < 2) {
                    event.channel.sendMessage("❌ Использование: `${plugin.configManager.commandPrefix}execute <команда>`").queue()
                    return
                }

                val commandToExecute = args.drop(1).joinToString(" ")

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToExecute)
                        event.channel.sendMessage("✅ Команда выполнена: `$commandToExecute`").queue()
                    } catch (e: Exception) {
                        event.channel.sendMessage("❌ Ошибка выполнения команды: ${e.message}").queue()
                    }
                })
            }

            else -> {
                event.channel.sendMessage("❓ Неизвестная команда. Доступные команды: `list`, `say`, `execute`").queue()
            }
        }
    }

    private fun handleChatMessage(event: MessageReceivedEvent) {
        val author = event.author
        val message = event.message.contentRaw
        val userColor = plugin.configManager.getUserColor(author.id)

        val component = Component.text("[DISCORD] ")
            .color(TextColor.color(0x5865F2))
            .append(Component.text(author.name)
                .color(TextColor.fromHexString(userColor))
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Discord пользователь: ${author.name}\nID: ${author.id}")))
                .clickEvent(ClickEvent.suggestCommand("/msg ${author.name} ")))
            .append(Component.text(": $message")
                .color(TextColor.color(0xFFFFFF)))

        Bukkit.getScheduler().runTask(plugin, Runnable {
            plugin.broadcastToMinecraft(component)
        })
    }

    fun sendChatMessage(playerName: String, message: String) {
        if (!plugin.configManager.enableChatSync) return

        channel?.sendMessage("**$playerName**: $message")?.queue()
    }

    fun sendServerMessage(message: String) {
        if (!plugin.configManager.enableServerMessages) return

        channel?.sendMessage(message)?.queue()
    }

    fun sendPlayerJoin(playerName: String) {
        val message = plugin.configManager.getMessage("join").replace("{player}", playerName)
        if (message.isNotEmpty()) {
            sendServerMessage(message)
        }
    }

    fun sendPlayerLeave(playerName: String) {
        val message = plugin.configManager.getMessage("leave").replace("{player}", playerName)
        if (message.isNotEmpty()) {
            sendServerMessage(message)
        }
    }

    fun sendPlayerDeath(playerName: String, deathMessage: String) {
        val message = plugin.configManager.getMessage("death")
            .replace("{player}", playerName)
            .replace("{message}", deathMessage)
        if (message.isNotEmpty()) {
            sendServerMessage(message)
        }
    }

    fun updateSettings() {
        val config = plugin.configManager

        guild = jda?.getGuildById(config.guildId)
        channel = guild?.getTextChannelById(config.channelId)
        allowedRole = guild?.getRoleById(config.allowedRoleId)
    }

    fun shutdown() {
        jda?.shutdown()
    }

    fun isConnected(): Boolean {
        return jda?.status == JDA.Status.CONNECTED
    }
}