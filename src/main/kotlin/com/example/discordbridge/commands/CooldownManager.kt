package com.example.discordbridge.commands

import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

class CooldownManager(private val plugin: Plugin) {

    private val cooldowns = mutableMapOf<UUID, Long>()
    private val bossBars = mutableMapOf<UUID, BossBar>()

    private var globalCooldownEnd: Long = 0L

    fun isOnCooldown(player: Player): Boolean {
        val now = System.currentTimeMillis()
        return cooldowns[player.uniqueId]?.let { it > now } ?: false
    }

    fun getRemaining(player: Player): Long {
        val now = System.currentTimeMillis()
        return (cooldowns[player.uniqueId] ?: 0L) - now
    }

    fun setCooldown(player: Player, durationMillis: Long) {
        val end = System.currentTimeMillis() + durationMillis
        cooldowns[player.uniqueId] = end
        showCooldownBar(player, durationMillis)
    }

    private fun showCooldownBar(player: Player, duration: Long) {
        val uuid = player.uniqueId

        // Удаляем старую полосу
        bossBars[uuid]?.removeAll()

        val bar = Bukkit.createBossBar("§cОткат команды...", BarColor.RED, BarStyle.SEGMENTED_10)
        bar.progress = 1.0
        bar.addPlayer(player)
        bossBars[uuid] = bar

        val start = System.currentTimeMillis()
        val end = start + duration

        object : BukkitRunnable() {
            override fun run() {
                val now = System.currentTimeMillis()
                val progress = ((end - now).toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
                bar.progress = progress

                if (now >= end) {
                    bar.removeAll()
                    bossBars.remove(uuid)
                    showAvailableBar(player, 60L) // 60 тиков = 3 секунды
                    cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 2L)
    }

    private fun showAvailableBar(player: Player, durationTicks: Long) {
        val uuid = player.uniqueId

        val bar = Bukkit.createBossBar("§aКоманда доступна!", BarColor.GREEN, BarStyle.SOLID)
        bar.progress = 1.0
        bar.addPlayer(player)
        bossBars[uuid] = bar

        object : BukkitRunnable() {
            override fun run() {
                bar.removeAll()
                bossBars.remove(uuid)
            }
        }.runTaskLater(plugin, durationTicks)
    }

    // Глобальный кулдаун 

    fun isGlobalCooldown(): Boolean {
        return System.currentTimeMillis() < globalCooldownEnd
    }

    fun getGlobalRemaining(): Long {
        return globalCooldownEnd - System.currentTimeMillis()
    }

    fun setGlobalCooldown(durationMillis: Long) {
        globalCooldownEnd = System.currentTimeMillis() + durationMillis
    }



    fun resetCooldown(player: Player) {
        cooldowns.remove(player.uniqueId)
        bossBars[player.uniqueId]?.removeAll()
        bossBars.remove(player.uniqueId)
    }

    fun resetGlobalCooldown() {
        globalCooldownEnd = 0L
    }
}