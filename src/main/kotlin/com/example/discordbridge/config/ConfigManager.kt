package com.example.discordbridge.config

import org.bukkit.plugin.Plugin
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.File
import java.nio.file.Path

class ConfigManager(private val plugin: Plugin) {

    private lateinit var mainConfig: ConfigurationNode
    private lateinit var colorsConfig: ConfigurationNode

    var botToken: String = ""
        private set
    var guildId: String = ""
        private set
    var channelId: String = ""
        private set
    var allowedRoleId: String = ""
        private set
    var commandPrefix: String = "!"
        private set
    var enableChatSync: Boolean = true
        private set
    var enableServerMessages: Boolean = true
        private set

    private val userColors = mutableMapOf<String, String>()
    private val availableColors = listOf(
        "#FF5555", "#55FF55", "#5555FF", "#FFFF55", "#FF55FF", "#55FFFF",
        "#FFA500", "#800080", "#008000", "#000080", "#800000", "#808000",
        "#008080", "#C0C0C0", "#FF1493", "#00CED1", "#32CD32", "#FFD700"
    )
    private var colorIndex = 0

    fun loadConfigs() {
        loadMainConfig()
        loadColorsConfig()
    }

    private fun loadMainConfig() {
        val configFile = File(plugin.dataFolder, "config.yml")

        if (!configFile.exists()) {
            plugin.dataFolder.mkdirs()
            createDefaultConfig(configFile)
        }

        val loader = YamlConfigurationLoader.builder()
            .path(configFile.toPath())
            .build()

        mainConfig = loader.load()

        botToken = mainConfig.node("discord", "bot-token").getString("")!!
        guildId = mainConfig.node("discord", "guild-id").getString("")!!
        channelId = mainConfig.node("discord", "channel-id").getString("")!!
        allowedRoleId = mainConfig.node("discord", "allowed-role-id").getString("")!!
        commandPrefix = mainConfig.node("discord", "command-prefix").getString("!")!!
        enableChatSync = mainConfig.node("features", "chat-sync").getBoolean(true)
        enableServerMessages = mainConfig.node("features", "server-messages").getBoolean(true)
    }

    private fun loadColorsConfig() {
        val colorsFile = File(plugin.dataFolder, "colors.yml")

        if (!colorsFile.exists()) {
            createDefaultColorsConfig(colorsFile)
        }

        val loader = YamlConfigurationLoader.builder()
            .path(colorsFile.toPath())
            .build()

        colorsConfig = loader.load()

        val colorsNode = colorsConfig.node("user-colors")
        if (!colorsNode.virtual()) {
            colorsNode.childrenMap().forEach { (key, value) ->
                userColors[key.toString()] = value.getString("")!!
            }
        }
    }

    private fun createDefaultConfig(file: File) {
        val loader = YamlConfigurationLoader.builder()
            .path(file.toPath())
            .build()

        val root = loader.createNode()

        root.node("discord", "bot-token").set("YOUR_BOT_TOKEN_HERE")
        root.node("discord", "guild-id").set("YOUR_GUILD_ID_HERE")
        root.node("discord", "channel-id").set("YOUR_CHANNEL_ID_HERE")
        root.node("discord", "allowed-role-id").set("YOUR_ROLE_ID_HERE")
        root.node("discord", "command-prefix").set("!")

        root.node("features", "chat-sync").set(true)
        root.node("features", "server-messages").set(true)

        root.node("messages", "join").set("**{player}** присоединился к серверу")
        root.node("messages", "leave").set("**{player}** покинул сервер")
        root.node("messages", "death").set("💀 **{player}** {message}")

        loader.save(root)
    }

    private fun createDefaultColorsConfig(file: File) {
        val loader = YamlConfigurationLoader.builder()
            .path(file.toPath())
            .build()

        val root = loader.createNode()
        root.node("user-colors").set(mapOf<String, String>())

        loader.save(root)
    }

    fun getUserColor(userId: String): String {
        return userColors.getOrPut(userId) {
            val color = availableColors[colorIndex % availableColors.size]
            colorIndex++
            saveUserColor(userId, color)
            color
        }
    }

    private fun saveUserColor(userId: String, color: String) {
        try {
            val colorsFile = File(plugin.dataFolder, "colors.yml")
            val loader = YamlConfigurationLoader.builder()
                .path(colorsFile.toPath())
                .build()

            val root = loader.load()
            root.node("user-colors", userId).set(color)
            loader.save(root)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save user color: ${e.message}")
        }
    }

    fun getMessage(key: String): String {
        return mainConfig.node("messages", key).getString("") ?: ""
    }

    fun isValidConfiguration(): Boolean {
        return botToken.isNotEmpty() &&
                guildId.isNotEmpty() &&
                channelId.isNotEmpty() &&
                allowedRoleId.isNotEmpty()
    }
}