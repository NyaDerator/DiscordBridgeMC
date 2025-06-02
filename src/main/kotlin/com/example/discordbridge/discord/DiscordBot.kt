package com.example.discordbridge.discord

import com.example.discordbridge.DiscordBridgePlugin
import com.example.discordbridge.commands.CooldownManager
import com.example.discordbridge.util.CommandLogInterceptor

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.EmbedBuilder
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

import java.awt.Color
import java.time.Instant

class DiscordBot(
    private val plugin: DiscordBridgePlugin,
) : ListenerAdapter() {
    private var jda: JDA? = null
    private var guild: Guild? = null
    private var channel: TextChannel? = null
    private var allowedRole: Role? = null

    companion object {
        val SUCCESS_COLOR = Color(0x00FF00)
        val ERROR_COLOR = Color(0xFF0000)
        val INFO_COLOR = Color(0x5865F2)
        val WARNING_COLOR = Color(0xFFAA00)
        val CHAT_COLOR = Color(0x7289DA)
        val SERVER_COLOR = Color(0x99AAB5)
    }

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
            val embed = EmbedBuilder()
                .setTitle("❌ Доступ запрещён")
                .setDescription("У вас нет прав на использование этой команды.")
                .setColor(ERROR_COLOR)
                .setTimestamp(Instant.now())
                .build()
            
            event.replyEmbeds(embed).setEphemeral(true).queue()
            return
        }

        when (event.name) {
            "list" -> handleListCommand(event)
            "execute" -> handleExecuteCommand(event)
        }
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return

        val config = plugin.configManager

        if (config.channelId.isEmpty()) return
        if (event.channel.id != config.channelId) return

        val member = event.member ?: return
        if (!hasRequiredRole(member)) return

        if (config.enableChatSync) {
            handleChatMessage(event)
        }
    }

    private fun hasRequiredRole(member: Member): Boolean {
        val allowedRoleId = plugin.configManager.allowedRoleId
        if (allowedRoleId.isEmpty()) return true
        return member.roles.any { it.id == allowedRoleId }
    }
    
    private fun handleChatMessage(event: MessageReceivedEvent) {
        val author = event.author
        val message = event.message.contentRaw
        val config = plugin.configManager
        val userColor = config.getUserColor(author.id)

        val component = Component.text("[DISCORD] ")
            .color(TextColor.color(0x5865F2))
            .append(Component.text(author.name)
                .color(TextColor.fromHexString(userColor))
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Discord пользователь: ${author.name}\nID: ${author.id}")))
                .clickEvent(ClickEvent.suggestCommand("/msg ${author.name} ")))
            .append(Component.text(": ${config.filterChatMessage(message)}")
                .color(TextColor.color(0xFFFFFF)))
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            plugin.broadcastToMinecraft(component)
        })
    }

    private fun handleListCommand(event: SlashCommandInteractionEvent) {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        
        val embed = EmbedBuilder()
            .setTitle("📋 Список игроков онлайн")
            .setColor(INFO_COLOR)
            .setTimestamp(Instant.now())
        
        if (onlinePlayers.isEmpty()) {
            embed.setDescription("🚫 Нет игроков онлайн")
        } else {
            val playerList = onlinePlayers.joinToString("\n") { "• `${it.name}`" }
            embed.setDescription("**Всего игроков: ${onlinePlayers.size}**\n\n$playerList")
            embed.setFooter("Обновлено", event.jda.selfUser.avatarUrl)
        }

        event.replyEmbeds(embed.build()).queue()
    }

    private fun handleExecuteCommand(event: SlashCommandInteractionEvent) {
        val config = plugin.configManager
        val command = event.getOption("command")?.asString ?: return
        val playerName = event.getOption("player")?.asString

        if (playerName != null && !config.isPlayerAllowed(playerName) && playerName.isNotEmpty()) {
            val embed = EmbedBuilder()
                .setTitle("❌ У нас нет доступа к данному игроку")
                .setDescription("Команды от имени `$playerName` не разрешены к выполнению")
                .setColor(ERROR_COLOR)
                .setTimestamp(Instant.now())
                .build()
            
            event.replyEmbeds(embed).queue()
            return
        }

        if (!config.isCommandAllowed(command)) {
            val embed = EmbedBuilder()
                .setTitle("❌ Команда запрещена")
                .setDescription("Команда `$command` не разрешена к выполнению")
                .setColor(ERROR_COLOR)
                .setTimestamp(Instant.now())
                .build()
            
            event.replyEmbeds(embed).queue()
            return
        }
        
        val checkCommandmsg = config.checkCommandLimits(command)
        if (checkCommandmsg != "ok") {
            val embed = EmbedBuilder()
                .setTitle("❌ Команда запрещена")
                .setDescription("Ошибка: $checkCommandmsg")
                .setColor(ERROR_COLOR)
                .setTimestamp(Instant.now())
                .build()

            event.replyEmbeds(embed).queue()
            return
        }


        val cooldownManager = plugin.cooldownManager

        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                if (playerName != null) {
                    val player = Bukkit.getPlayerExact(playerName)
                    if (player == null || !player.isOnline) {
                        val embed = EmbedBuilder()
                            .setTitle("❌ Игрок не найден")
                            .setDescription("Игрок `$playerName` не найден или не в сети")
                            .setColor(ERROR_COLOR)
                            .setTimestamp(Instant.now())
                            .build()
                        
                        event.replyEmbeds(embed).queue()
                        return@Runnable
                    }

                    if (cooldownManager.isOnCooldown(player)) {
                        val seconds = cooldownManager.getRemaining(player) / 1000.0
                        val embed = EmbedBuilder()
                            .setTitle("⏳ Кулдаун активен")
                            .setDescription("Игрок `$playerName` на кулдауне")
                            .setColor(WARNING_COLOR)
                            .setTimestamp(Instant.now())
                            .addField("Осталось времени", "%.1f сек".format(seconds), true)
                            .build()
                        
                        event.replyEmbeds(embed).queue()
                        return@Runnable
                    }
                } else {
                    if (cooldownManager.isGlobalCooldown()) {
                        val seconds = cooldownManager.getGlobalRemaining() / 1000.0
                        val embed = EmbedBuilder()
                            .setTitle("⏳ Глобальный кулдаун")
                            .setDescription("Глобальный откат активен")
                            .setColor(WARNING_COLOR)
                            .setTimestamp(Instant.now())
                            .addField("Попробуйте через", "%.1f сек".format(seconds), true)
                            .build()
                        
                        event.replyEmbeds(embed).queue()
                        return@Runnable
                    }
                }

                val finalCommand = if (playerName == null) command else "execute as $playerName at @s run $command"

                val interceptor = CommandLogInterceptor { logOutput ->
                    val errorEmbed = EmbedBuilder()
                        .setTitle("❌ Ошибка выполнения команды")
                        .setDescription("```\n$logOutput\n```")
                        .setColor(ERROR_COLOR)
                        .setTimestamp(Instant.now())
                        .addField("Команда", "`$command`", true)
                        .addField("Игрок", playerName ?: "Консоль", true)
                        .build()
                    
                    event.replyEmbeds(errorEmbed).queue()
                }

                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)

                if (playerName != null) {
                    val player = Bukkit.getPlayerExact(playerName)!!
                    cooldownManager.setCooldown(player, config.executeCommandCooldown)
                } else {
                    cooldownManager.setGlobalCooldown(config.globalExecuteCommandCooldown)
                }

                plugin.logger.info("Executed command: $finalCommand")
                
                val successEmbed = EmbedBuilder()
                    .setTitle("✅ Команда выполнена")
                    .setDescription("Команда успешно выполнена")
                    .setColor(SUCCESS_COLOR)
                    .setTimestamp(Instant.now())
                    .addField("Команда", "`$command`", true)
                    .addField("Исполнитель", event.user.name, true)
                    .addField("Цель", playerName ?: "Консоль", true)
                    .build()
                
                event.replyEmbeds(successEmbed).queue()
                
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    interceptor.stop()
                }, 1L)

            } catch (e: Exception) {
                val embed = EmbedBuilder()
                    .setTitle("❌ Критическая ошибка")
                    .setDescription("Произошла ошибка при выполнении команды")
                    .setColor(ERROR_COLOR)
                    .setTimestamp(Instant.now())
                    .addField("Ошибка", e.message ?: "Неизвестная ошибка", false)
                    .build()
                
                event.replyEmbeds(embed).queue()
            }
        })
    }

    fun sendChatMessage(playerName: String, message: String) {
        if (!plugin.configManager.enableChatSync) return
        
        val embed = EmbedBuilder()
            .setAuthor(playerName, null, "https://mc-heads.net/avatar/$playerName/32")
            .setDescription(message)
            .setColor(CHAT_COLOR)
            .setTimestamp(Instant.now())
            .setFooter("Minecraft Chat")
            .build()
        
        channel?.sendMessageEmbeds(embed)?.queue()
    }

    fun sendServerMessage(message: String) {
        if (!plugin.configManager.enableServerMessages) return
        
        val embed = EmbedBuilder()
            .setDescription(message)
            .setColor(SERVER_COLOR)
            .setTimestamp(Instant.now())
            .setFooter("Server Event")
            .build()
        
        channel?.sendMessageEmbeds(embed)?.queue()
    }

    fun sendPlayerJoin(playerName: String) {
        val messageText = plugin.configManager.getMessage("join").replace("{player}", playerName)
        if (messageText.isEmpty()) return
        
        val embed = EmbedBuilder()
            .setTitle("🟢 Игрок подключился")
            .setDescription(messageText)
            .setColor(SUCCESS_COLOR)
            .setTimestamp(Instant.now())
            .setThumbnail("https://mc-heads.net/avatar/$playerName/64")
            .addField("Игрок", playerName, true)
            .addField("Статус", "Подключился к серверу", true)
            .setFooter("Join Event")
            .build()
        
        channel?.sendMessageEmbeds(embed)?.queue()
    }

    fun sendPlayerLeave(playerName: String) {
        val messageText = plugin.configManager.getMessage("leave").replace("{player}", playerName)
        if (messageText.isEmpty()) return
        
        val embed = EmbedBuilder()
            .setTitle("🔴 Игрок отключился")
            .setDescription(messageText)
            .setColor(ERROR_COLOR)
            .setTimestamp(Instant.now())
            .setThumbnail("https://mc-heads.net/avatar/$playerName/64")
            .addField("Игрок", playerName, true)
            .addField("Статус", "Отключился от сервера", true)
            .setFooter("Leave Event")
            .build()
        
        channel?.sendMessageEmbeds(embed)?.queue()
    }

    fun sendPlayerDeath(playerName: String, deathMessage: String) {
        val messageText = plugin.configManager
            .getMessage("death")
            .replace("{player}", playerName)
            .replace("{message}", deathMessage)
        if (messageText.isEmpty()) return
        
        val embed = EmbedBuilder()
            .setTitle("💀 Смерть игрока")
            .setDescription(messageText)
            .setColor(Color(0x8B0000))
            .setTimestamp(Instant.now())
            .setThumbnail("https://mc-heads.net/avatar/$playerName/64")
            .addField("Игрок", playerName, true)
            .addField("Причина", deathMessage, false)
            .setFooter("Death Event")
            .build()
        
        channel?.sendMessageEmbeds(embed)?.queue()
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