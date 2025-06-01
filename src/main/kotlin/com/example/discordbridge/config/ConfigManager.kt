package com.example.discordbridge.config

import org.bukkit.plugin.Plugin
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.File

class ConfigManager(
    private val plugin: Plugin,
) {
    private lateinit var mainConfig: ConfigurationNode
    private lateinit var cooldownsConfig: ConfigurationNode
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

    var twitchChannel: String = ""
        private set

    var wordBlacklist: List<String> = emptyList()
        private set
    var commandWhitelist: List<String> = emptyList()
        private set
    var commandBlacklist: List<String> = emptyList()
        private set

    var executeCommandCooldown: Long = 10000
        private set
    var globalExecuteCommandCooldown: Long = 10000
        private set
        
    var playersWhitelist: List<String> = emptyList()
        private set
    var playersBlacklist: List<String> = emptyList()
        private set

    private val userColors = mutableMapOf<String, String>()
    private val availableColors = listOf(
        "#FF5555",
        "#55FF55",
        "#5555FF",
        "#FFFF55",
        "#FF55FF",
        "#55FFFF",
        "#FFA500",
        "#800080",
        "#008000",
        "#000080",
        "#800000",
        "#808000",
        "#008080",
        "#C0C0C0",
        "#FF1493",
        "#00CED1",
        "#32CD32",
        "#FFD700",
    )
    private var colorIndex = 0

    fun loadConfigs() {
        loadMainConfig()
        loadCooldownsConfig()
        loadColorsConfig()
    }

    private fun loadMainConfig() {
        val configFile = File(plugin.dataFolder, "config.yml")

        if (!configFile.exists()) {
            plugin.dataFolder.mkdirs()
            createDefaultConfig(configFile)
        }

        val loader = YamlConfigurationLoader.builder().path(configFile.toPath()).build()
        mainConfig = loader.load()

        botToken = mainConfig.node("discord", "bot-token").getString("")!!
        guildId = mainConfig.node("discord", "guild-id").getString("")!!
        channelId = mainConfig.node("discord", "channel-id").getString("")
        allowedRoleId = mainConfig.node("discord", "allowed-role-id").getString("")
        enableChatSync = mainConfig.node("features", "chat-sync").getBoolean(true)
        enableServerMessages = mainConfig.node("features", "server-messages").getBoolean(true)
        twitchChannel = mainConfig.node("features", "twitch-channel").getString("")

        wordBlacklist = mainConfig.node("filters", "word_blacklist").getList(String::class.java, emptyList())
        commandWhitelist = mainConfig.node("filters", "command_whitelist").getList(String::class.java, emptyList())
        commandBlacklist = mainConfig.node("filters", "command_blacklist").getList(String::class.java, emptyList())

        playersWhitelist = mainConfig.node("filters", "players_whitelist").getList(String::class.java, emptyList())
        playersBlacklist = mainConfig.node("filters", "players_blacklist").getList(String::class.java, emptyList())
    }

    private fun loadCooldownsConfig() {
        val cooldownsFile = File(plugin.dataFolder, "cooldowns.yml")
        if (!cooldownsFile.exists()) {
            createDefaultCooldownsConfig(cooldownsFile)
        }
        val loader = YamlConfigurationLoader.builder().path(cooldownsFile.toPath()).build()
        cooldownsConfig = loader.load()

        executeCommandCooldown = cooldownsConfig.node("execute-command-cooldown").getLong(15000)
        globalExecuteCommandCooldown = cooldownsConfig.node("global-execute-command-cooldown").getLong(15000)
    }

    private fun loadColorsConfig() {
        val colorsFile = File(plugin.dataFolder, "colors.yml")

        if (!colorsFile.exists()) {
            createDefaultColorsConfig(colorsFile)
        }

        val loader = YamlConfigurationLoader.builder().path(colorsFile.toPath()).build()
        colorsConfig = loader.load()

        val colorsNode = colorsConfig.node("user-colors")
        if (!colorsNode.virtual()) {
            colorsNode.childrenMap().forEach { (key, value) ->
                userColors[key.toString()] = value.getString("")!!
            }
        }
    }

    private fun createDefaultConfig(file: File) {
        val loader = YamlConfigurationLoader.builder().path(file.toPath()).build()
        val root = loader.createNode()

        root.node("discord", "bot-token").set("YOUR_BOT_TOKEN_HERE")
        root.node("discord", "guild-id").set("YOUR_GUILD_ID_HERE")
        root.node("discord", "channel-id").set("YOUR_CHANNEL_ID_HERE")
        root.node("discord", "allowed-role-id").set("YOUR_ROLE_ID_HERE")

        root.node("features", "chat-sync").set(true)
        root.node("features", "server-messages").set(true)
        root.node("features", "twitch-channel").set("YOUR_TWITCH_CHANNEL_URL_HERE")

        loader.save(root)
    }

    private fun createDefaultCooldownsConfig(file: File) {
        val loader = YamlConfigurationLoader.builder().path(file.toPath()).build()
        val root = loader.createNode()
        root.node("execute-command-cooldown").set(15000)
        root.node("global-execute-command-cooldown").set(15000)
        loader.save(root)
    }

    private fun createDefaultColorsConfig(file: File) {
        val loader = YamlConfigurationLoader.builder().path(file.toPath()).build()
        val root = loader.createNode()
        root.node("user-colors").set(mapOf<String, String>())
        loader.save(root)
    }

    fun getUserColor(userId: String): String =
        userColors.getOrPut(userId) {
            val color = availableColors[colorIndex % availableColors.size]
            colorIndex++
            saveUserColor(userId, color)
            color
        }

    private fun saveUserColor(userId: String, color: String) {
        try {
            val colorsFile = File(plugin.dataFolder, "colors.yml")
            val loader = YamlConfigurationLoader.builder().path(colorsFile.toPath()).build()

            val root = loader.load()
            root.node("user-colors", userId).set(color)
            loader.save(root)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save user color: ${e.message}")
        }
    }

    fun getMessage(key: String): String = mainConfig.node("messages", key).getString("") ?: ""

    fun isCommandAllowed(command: String): Boolean {
        val commandWords = command.split(Regex("\\s+")).map { it.lowercase() }

        val whitelist = commandWhitelist.map { it.lowercase() }
        val blacklist = commandBlacklist.map { it.lowercase() }

        val whitelistEmpty = whitelist.isEmpty()
        val blacklistEmpty = blacklist.isEmpty()

        return when {
            whitelistEmpty && blacklistEmpty -> true

            whitelistEmpty && !blacklistEmpty -> {
                commandWords.none { it in blacklist }
            }

            !whitelistEmpty && blacklistEmpty -> {
                commandWords.any { it in whitelist }
            }

            else -> {
                commandWords.any { it in whitelist } &&
                commandWords.none { it in blacklist }
            }
        }
    }

    fun isPlayerAllowed(command: String): Boolean {
        val playersWords = command.split(Regex("\\s+")).map { it.lowercase() }

        val whitelist = playersWhitelist.map { it.lowercase() }
        val blacklist = playersBlacklist.map { it.lowercase() }

        val whitelistEmpty = whitelist.isEmpty()
        val blacklistEmpty = blacklist.isEmpty()

        return when {
            whitelistEmpty && blacklistEmpty -> true

            whitelistEmpty && !blacklistEmpty -> {
                playersWords.none { it in blacklist }
            }

            !whitelistEmpty && blacklistEmpty -> {
                playersWords.any { it in whitelist }
            }

            else -> {
                playersWords.any { it in whitelist } &&
                playersWords.none { it in blacklist }
            }
        }
    }


    fun filterChatMessage(message: String): String {
        var filtered = message
        wordBlacklist.forEach { word ->
            if (word.isNotEmpty()) {
                val regex = Regex("(?i)${Regex.escape(word)}")
                filtered = regex.replace(filtered) { "*".repeat(it.value.length) }
            }
        }
        return filtered
    }

    fun isValidConfiguration(): Boolean =
        botToken.isNotEmpty() &&
        guildId.isNotEmpty()
}
