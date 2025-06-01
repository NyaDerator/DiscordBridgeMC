package com.example.discordbridge.listeners

import com.example.discordbridge.DiscordBridgePlugin
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent

class ChatListener(private val plugin: DiscordBridgePlugin) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncChatEvent) {
        if (!plugin.isPluginEnabled()) return

        val player = event.player
        val message = PlainTextComponentSerializer.plainText().serialize(event.message())

        plugin.discordBot.sendChatMessage(player.name, message)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!plugin.isPluginEnabled()) return

        plugin.discordBot.sendPlayerJoin(event.player.name)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!plugin.isPluginEnabled()) return

        plugin.discordBot.sendPlayerLeave(event.player.name)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!plugin.isPluginEnabled()) return

        val player = event.entity
        val deathMessage = event.deathMessage()?.let {
            PlainTextComponentSerializer.plainText().serialize(it)
        } ?: "${player.name} died"

        plugin.discordBot.sendPlayerDeath(player.name, deathMessage)
    }

    @EventHandler
    fun onAdvancementDone(event: PlayerAdvancementDoneEvent) {
        val playerName = event.player.name
        val advancement = event.advancement.key.key

        val message = "🎉 Игрок **$playerName** получил достижение: `$advancement`"

        plugin.discordBot.sendServerMessage(message)
    }
}