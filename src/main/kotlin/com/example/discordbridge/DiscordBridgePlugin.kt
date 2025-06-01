package com.example.discordbridge

import com.example.discordbridge.commands.DiscordBridgeCommand
import com.example.discordbridge.commands.CooldownManager
import com.example.discordbridge.config.ConfigManager
import com.example.discordbridge.discord.DiscordBot
import com.example.discordbridge.listeners.ChatListener
import net.kyori.adventure.text.Component
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class DiscordBridgePlugin : JavaPlugin() {
    lateinit var configManager: ConfigManager
        private set

    lateinit var discordBot: DiscordBot
        private set

    lateinit var cooldownManager: CooldownManager
        private set

    private var isEnabled = false

    override fun onEnable() {
        logger.info("Starting DiscordBridgeMC...")

        try {
            configManager = ConfigManager(this)
            configManager.loadConfigs()

            discordBot = DiscordBot(this)

            cooldownManager = CooldownManager(this)
            server.pluginManager.registerEvents(ChatListener(this), this)

            getCommand("discordbridge")?.setExecutor(DiscordBridgeCommand(this))
            

            if (discordBot.start()) {
                isEnabled = true
                logger.info("DiscordBridgeMC successfully enabled!")

                discordBot.sendServerMessage("🟢 **Сервер запущен!**")
            } else {
                logger.severe("Failed to start Discord bot! Check your configuration.")
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to enable DiscordBridgeMC", e)
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        logger.info("Shutting down DiscordBridgeMC...")

        if (isEnabled) {
            discordBot.sendServerMessage("🔴 **Сервер остановлен.**")

            discordBot.shutdown()
        }

        logger.info("DiscordBridgeMC disabled.")
    }

    fun reload(): Boolean =
        try {
            configManager.loadConfigs()
            discordBot.updateSettings()
            true
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to reload configuration", e)
            false
        }

    fun broadcastToMinecraft(message: Component) {
        server.broadcast(message)
    }

    fun isPluginEnabled(): Boolean = isEnabled
}
