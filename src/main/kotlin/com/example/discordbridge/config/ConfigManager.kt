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

    // Система фильтрации команд
    private val commandLimitRules = mutableListOf<CommandLimitRule>()

    data class CommandLimitRule(
        val pattern: String,
        val limits: Map<Int, IntRange>
    )

    fun loadConfigs() {
        loadMainConfig()
        loadCooldownsConfig()
        loadColorsConfig()
        parseCommandLimits()
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

    private fun parseCommandLimits() {
        commandLimitRules.clear()
        
        val commandLimitsNode = mainConfig.node("filters", "command_limits")
        if (commandLimitsNode.virtual() || commandLimitsNode.childrenList().isEmpty()) {
            return
        }
        
        try {
            for (limitNode in commandLimitsNode.childrenList()) {
                val pattern = limitNode.node("pattern").getString("")
                if (pattern.isNullOrEmpty()) {
                    plugin.logger.warning("Пропущено правило с пустым паттерном")
                    continue
                }
                
                val limitsNode = limitNode.node("limits")
                if (limitsNode.virtual()) {
                    plugin.logger.warning("Пропущено правило '$pattern' без лимитов")
                    continue
                }
                
                val limits = mutableMapOf<Int, IntRange>()
                
                limitsNode.childrenMap().forEach { (indexKey, valueNode) ->
                    try {
                        val index = indexKey.toString().toInt()
                        val rangeString = valueNode.getString("")
                        
                        if (!rangeString.isNullOrEmpty()) {
                            val range = parseRangeString(rangeString)
                            if (range != null) {
                                limits[index] = range
                            } else {
                                plugin.logger.warning("Неверный формат диапазона '$rangeString' для позиции $index в паттерне '$pattern'")
                            }
                        }
                    } catch (e: NumberFormatException) {
                        plugin.logger.warning("Неверный индекс '$indexKey' в паттерне '$pattern'")
                    }
                }
                
                if (limits.isNotEmpty()) {
                    commandLimitRules.add(CommandLimitRule(pattern, limits))
                    plugin.logger.info("Загружено правило: '$pattern' с лимитами: $limits")
                } else {
                    plugin.logger.warning("Правило '$pattern' не содержит валидных лимитов")
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Ошибка при разборе правил лимитов команд: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun parseRangeString(rangeString: String): IntRange? {
        return try {
            when {
                rangeString.contains("..") -> {
                    val parts = rangeString.split("..")
                    if (parts.size == 2) {
                        val start = parts[0].trim().toInt()
                        val end = parts[1].trim().toInt()
                        start..end
                    } else {
                        null
                    }
                }
                rangeString.matches("-?\\d+".toRegex()) -> {
                    val value = rangeString.toInt()
                    value..value // Одиночное значение как диапазон
                }
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
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

        // Создаем пример command_limits в новом формате
        val commandLimitsNode = root.node("filters", "command_limits")
        
        // Правило для tp команды
        val tpRule = commandLimitsNode.appendListNode()
        tpRule.node("pattern").set("/tp * * * *")
        val tpLimits = tpRule.node("limits")
        tpLimits.node("2").set("-500..500")  // X координата
        tpLimits.node("3").set("0..320")     // Y координата
        tpLimits.node("4").set("-500..500")  // Z координата
        
        // Правило для effect команды
        val effectRule = commandLimitsNode.appendListNode()
        effectRule.node("pattern").set("/effect * * * * *")
        val effectLimits = effectRule.node("limits")
        effectLimits.node("4").set("0..120")  // Время действия
        effectLimits.node("5").set("0..50")   // Уровень эффекта
        
        // Правило для give команды
        val giveRule = commandLimitsNode.appendListNode()
        giveRule.node("pattern").set("/give * * *")
        val giveLimits = giveRule.node("limits")
        giveLimits.node("3").set("1..64")     // Количество предметов

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

    /**
     * Проверяет команду на соответствие лимитам
     * @param command команда для проверки
     * @return "ok" если команда разрешена, иначе сообщение об ошибке
     */
    fun checkCommandLimits(command: String): String {
        if (commandLimitRules.isEmpty()) {
            return "ok"
        }
        
        val commandParts = command.trim().split("\\s+".toRegex())
        
        for (rule in commandLimitRules) {
            val patternParts = rule.pattern.split("\\s+".toRegex())
            
            if (matchesPattern(commandParts, patternParts)) {
                val violation = checkLimits(commandParts, rule.limits)
                if (violation != null) {
                    return violation
                }
            }
        }
        
        return "ok"
    }

    private fun matchesPattern(command: List<String>, pattern: List<String>): Boolean {
        if (command.size != pattern.size) return false
        
        for (i in command.indices) {
            if (pattern[i] != "*" && pattern[i] != command[i]) {
                return false
            }
        }
        
        return true
    }

    private fun checkLimits(command: List<String>, limits: Map<Int, IntRange>): String? {
        for ((index, range) in limits) {
            if (index >= command.size) continue
            
            val arg = command[index]
            val value = parseNumericArgument(arg)
            
            if (value != null && value !in range) {
                return "Аргумент '$arg' на позиции ${index + 1} превышает допустимый лимит ${range.first}..${range.last}"
            }
        }
        
        return null
    }

    private fun parseNumericArgument(arg: String): Int? {
        return try {
            when {
                arg.equals("infinite", ignoreCase = true) -> Int.MAX_VALUE
                arg.startsWith("~") -> {
                    val relativeValue = arg.substring(1)
                    if (relativeValue.isEmpty()) 0 else relativeValue.toInt()
                }
                else -> arg.toInt()
            }
        } catch (e: NumberFormatException) {
            null
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