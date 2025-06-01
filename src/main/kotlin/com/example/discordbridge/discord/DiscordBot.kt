package com.example.discordbridge.discord

import com.example.discordbridge.DiscordBridgePlugin
import com.example.discordbridge.commands.CooldownManager
import com.example.discordbridge.util.CommandLogInterceptor

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

import org.bukkit.entity.Player
import org.bukkit.Bukkit

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.layout.PatternLayout

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

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return

        if (event.channel.id != plugin.configManager.channelId) return

        val member = event.member ?: return
        if (!hasRequiredRole(member)) return

        val config = plugin.configManager
        val message = event.message.contentRaw

        if (config.enableChatSync) {
            handleChatMessage(event)
        }
    }

    private fun hasRequiredRole(member: Member): Boolean = member.roles.any { it.id == plugin.configManager.allowedRoleId }
    
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
        val config = plugin.configManager

        val command = event.getOption("command")?.asString ?: return
        val playerName = event.getOption("player")?.asString

        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                val cooldownManager = plugin.cooldownManager
                val finalCommand = if (playerName == null) command else "execute as $playerName at @s run $command"

                val interceptor = CommandLogInterceptor { logOutput ->
                    event.reply("❌ Ошибка при выполнении команды:\n```\n$logOutput\n```").queue()
                }

                // Выполняем команду
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)

                // Отключаем перехват через 1 тик
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    interceptor.stop()

                    // Если уже отправлен ответ (т.е. была ошибка), не продолжаем
                    if (event.isAcknowledged) return@Runnable

                    // Назначаем кулдаун
                    if (playerName != null) {
                        val player = Bukkit.getPlayerExact(playerName)
                        if (player == null || !player.isOnline) {
                            event.reply("❌ Игрок `$playerName` не найден или не в сети.").queue()
                            return@Runnable
                        }

                        if (cooldownManager.isOnCooldown(player)) {
                            val seconds = cooldownManager.getRemaining(player) / 1000.0
                            event.reply("⏳ Игрок `$playerName` на кулдауне (%.1f сек)".format(seconds)).queue()
                            return@Runnable
                        }

                        cooldownManager.setCooldown(player, config.executeCommandCoolDown)
                    } else {
                        if (cooldownManager.isGlobalCooldown()) {
                            val seconds = cooldownManager.getGlobalRemaining() / 1000.0
                            event.reply("⏳ Глобальный откат. Попробуйте через %.1f сек".format(seconds)).queue()
                            return@Runnable
                        }

                        cooldownManager.setGlobalCooldown(config.executeCommandCoolDown)
                    }

                    plugin.logger.info(finalCommand)
                    event.reply("✅ Команда выполнена: `$command`").queue()
                }, 1L)
            } catch (e: Exception) {
                event.reply("❌ Ошибка выполнения команды: ${e.message}").queue()
            }
        })
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
