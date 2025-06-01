package com.example.discordbridge.discord

import com.example.discordbridge.DiscordBridgePlugin
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import org.bukkit.Bukkit

class DiscordBot(
    private val plugin: DiscordBridgePlugin,
) : ListenerAdapter() {
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
            jda =
                JDABuilder
                    .createDefault(config.botToken)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
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
        channel = guild?.getTextChannelById(config.channelId)
        allowedRole = guild?.getRoleById(config.allowedRoleId)

        if (guild == null || channel == null || allowedRole == null) {
            plugin.logger.severe("Failed to fetch guild/channel/role. Check IDs in config.yml")
            return
        }

        guild!!
            .updateCommands()
            .addCommands(
                Commands.slash("list", "Показать список онлайн-игроков"),
                Commands
                    .slash("execute", "Выполнить команду от имени консоли")
                    .addOption(OptionType.STRING, "command", "Команда", true)
                    .addOption(OptionType.STRING, "player", "Имя игрока (опционально)", false),
            ).queue {
                plugin.logger.info("Slash-команды успешно зарегистрированы.")
            }

        plugin.logger.info("Discord bot fully initialized!")
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val member = event.member ?: return
        if (!hasRequiredRole(member)) {
            event.reply("❌ У вас нет прав на использование этой команды.").setEphemeral(true).queue()
            return
        }

        when (event.name) {
            "list" -> handleListCommand(event)
            "execute" -> handleExecuteCommand(event)
        }
    }

    private fun hasRequiredRole(member: Member): Boolean = member.roles.any { it.id == plugin.configManager.allowedRoleId }

    private fun handleListCommand(event: SlashCommandInteractionEvent) {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        val playerList =
            if (onlinePlayers.isEmpty()) {
                "Нет игроков онлайн"
            } else {
                "Игроки онлайн (${onlinePlayers.size}): ${onlinePlayers.joinToString(", ") { it.name }}"
            }

        event.reply("📋 `$playerList`").queue()
    }

    private fun handleExecuteCommand(event: SlashCommandInteractionEvent) {
        val command = event.getOption("command")?.asString ?: return
        val playerName = event.getOption("player")?.asString

        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                try {
                    if (playerName == null) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                            plugin.logger.info(command)
                    } else {
                        val player = Bukkit.getPlayerExact(playerName)
                        if (player != null && player.isOnline) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute as $playerName at @s run $command")
                            plugin.logger.info("execute as $playerName at @s run $command")
                        } else {
                            event.reply("❌ Игрок `$playerName` не найден или не в сети.").queue()
                            return@Runnable
                        }
                    }

                    event.reply("✅ Команда выполнена: `$command`").queue()
                } catch (e: Exception) {
                    event.reply("❌ Ошибка выполнения команды: ${e.message}").queue()
                }
            },
        )
    }

    fun sendChatMessage(
        playerName: String,
        message: String,
    ) {
        if (!plugin.configManager.enableChatSync) return
        channel?.sendMessage("**$playerName**: $message")?.queue()
    }

    fun sendServerMessage(message: String) {
        if (!plugin.configManager.enableServerMessages) return
        channel?.sendMessage(message)?.queue()
    }

    fun sendPlayerJoin(playerName: String) {
        val message = plugin.configManager.getMessage("join").replace("{player}", playerName)
        if (message.isNotEmpty()) sendServerMessage(message)
    }

    fun sendPlayerLeave(playerName: String) {
        val message = plugin.configManager.getMessage("leave").replace("{player}", playerName)
        if (message.isNotEmpty()) sendServerMessage(message)
    }

    fun sendPlayerDeath(
        playerName: String,
        deathMessage: String,
    ) {
        val message =
            plugin.configManager
                .getMessage("death")
                .replace("{player}", playerName)
                .replace("{message}", deathMessage)
        if (message.isNotEmpty()) sendServerMessage(message)
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

    fun isConnected(): Boolean = jda?.status == JDA.Status.CONNECTED
}
