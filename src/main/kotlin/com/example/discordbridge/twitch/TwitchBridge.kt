package com.example.discordbridge.twitch

import com.example.discordbridge.DiscordBridgePlugin
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit

class TwitchBridge(
    private val plugin: DiscordBridgePlugin,
    private val channel: String,
    private val onMessageReceived: (user: String, message: String) -> Unit
) {
    private val twitchClient = TwitchClientBuilder.builder()
        .withEnableChat(true)
        .build()

    fun start() {
        val config = plugin.configManager
        if (config.twitchChannel.isEmpty()) return

        twitchClient.chat.joinChannel(channel)

        twitchClient.eventManager.onEvent(ChannelMessageEvent::class.java) { event ->
            if (event.channel.name.equals(channel, ignoreCase = true)) {
                val user = event.user.name
                val message = event.message
                val userColor = config.getUserColor(user)

                val component = Component.text("[TWITCH] ")
                    .color(TextColor.color(0x7B68EE))
                    .append(
                        Component.text(user)
                            .color(TextColor.fromHexString(userColor))
                            .decorate(TextDecoration.BOLD)
                            .hoverEvent(HoverEvent.showText(Component.text("Twitch пользователь: $user")))
                            .clickEvent(ClickEvent.suggestCommand("/msg $user "))
                    )
                    .append(Component.text(": ${config.filterChatMessage(message)}").color(TextColor.color(0xFFFFFF)))

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    plugin.broadcastToMinecraft(component)
                })

                onMessageReceived(user, message)
            }
        }
    }

    fun stop() {
        twitchClient.chat.leaveChannel(channel)
        twitchClient.close()
    }
}
