package com.example.discordbridge.util

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.layout.PatternLayout

class CommandLogInterceptor(private val onError: (String) -> Unit) {
    private val interceptedMessages = mutableListOf<String>()
    private val appender: Appender


    fun ansiToDiscordMarkdown(input: String): String {
        var text = input

        text = text.replace("\u001B\\[[;\\d]*m".toRegex(), "")

        text = text.replace("\u001B\\[91m".toRegex(), "**")
        text = text.replace("\u001B\\[31m".toRegex(), "**")

        text = text.replace("\u001B\\[3m".toRegex(), "*")
        text = text.replace("\u001B\\[0m".toRegex(), "")

        text = text.replace("\u001B\\[[;\\d]*m".toRegex(), "")

        return text
    }

    init {
        val layout = PatternLayout.createDefaultLayout()
        appender = object : AbstractAppender("CommandLogInterceptor", null, layout, false) {
            override fun append(event: LogEvent) {
                val msg = event.message.formattedMessage
                if (
                    msg.contains("Unknown command", true) ||
                    msg.contains("Incorrect argument", true) ||
                    msg.contains("Usage:", true) ||
                    msg.contains("[HERE]", true) ||
                    msg.contains("error", true) ||
                    msg.contains("Exception", true)
                ) {
                    synchronized(interceptedMessages) {
                        interceptedMessages.add(msg)
                    }
                }
            }
        }
        appender.start()
        val logger = LogManager.getRootLogger() as org.apache.logging.log4j.core.Logger
        logger.addAppender(appender)
    }

    fun stop() {
        val logger = LogManager.getRootLogger() as org.apache.logging.log4j.core.Logger
        logger.removeAppender(appender)
        appender.stop()

        val output = synchronized(interceptedMessages) {
            interceptedMessages.joinToString("\n")
        }

        if (output.isNotBlank()) {
            onError(ansiToDiscordMarkdown(output))
        }
    }
}
