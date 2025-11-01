package io.github.darkstarworks

import com.destroystokyo.paper.event.server.PaperServerListPingEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerCommandSendEvent
import org.bukkit.plugin.java.JavaPlugin

class PluginGuard : JavaPlugin(), Listener {

    private lateinit var hideMode: String
    private lateinit var fakePlugins: List<String>
    private lateinit var bypassPermission: String
    private lateinit var protectedCommands: Set<String>
    private lateinit var commonPluginCommands: Set<String>
    private lateinit var fakeServerBrand: String
    private var blockBukkitCommands: Boolean = true
    private var redirectBukkitCommands: Boolean = false
    private var hideTabCompletion: Boolean = true
    private var blockUnknownCommands: Boolean = true
    private var hideServerBrand: Boolean = true
    private var blockCommonPluginCommands: Boolean = true
    private var aggressiveMode: Boolean = false

    override fun onEnable() {
        saveDefaultConfig()
        loadConfiguration()
        server.pluginManager.registerEvents(this, this)
        logger.info("PluginGuard enabled - protecting ${server.pluginManager.plugins.size} plugins")
    }

    private fun loadConfiguration() {
        reloadConfig()
        hideMode = config.getString("hide-mode", "unknown-command")!!
        fakePlugins = config.getStringList("fake-plugins")
        bypassPermission = config.getString("bypass-permission", "pluginguard.bypass")!!
        protectedCommands = config.getStringList("protected-commands").map { it.lowercase() }.toSet()
        commonPluginCommands = config.getStringList("common-plugin-commands").map { it.lowercase() }.toSet()
        fakeServerBrand = config.getString("fake-server-brand", "vanilla")!!
        blockBukkitCommands = config.getBoolean("block-bukkit-commands", true)
        redirectBukkitCommands = config.getBoolean("redirect-bukkit-commands", false)
        hideTabCompletion = config.getBoolean("hide-tab-completion", true)
        blockUnknownCommands = config.getBoolean("block-unknown-commands", true)
        hideServerBrand = config.getBoolean("hide-server-brand", true)
        blockCommonPluginCommands = config.getBoolean("block-common-plugin-commands", true)
        aggressiveMode = config.getBoolean("aggressive-mode", false)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        if (player.hasPermission(bypassPermission)) return

        val message = event.message.lowercase().substringAfter("/")
        val parts = message.split(" ")
        val baseCommand = parts[0]
        val cleanCommand = baseCommand.removePrefix("bukkit:").removePrefix("minecraft:")

        when {
            baseCommand in protectedCommands || cleanCommand in protectedCommands -> {
                event.isCancelled = true
                if (baseCommand.startsWith("bukkit:") && redirectBukkitCommands) {
                    handlePluginsCommand(player)
                } else {
                    handleProtectedCommand(player, cleanCommand)
                }
            }
            baseCommand.startsWith("bukkit:") && blockBukkitCommands -> {
                event.isCancelled = true
                sendUnknownCommand(player)
            }
            baseCommand in commonPluginCommands && blockCommonPluginCommands -> {
                event.isCancelled = true
                sendUnknownCommand(player)
            }
            aggressiveMode && !player.hasPermission("$baseCommand.use") -> {
                if (server.getPluginCommand(baseCommand) != null) {
                    event.isCancelled = true
                    sendUnknownCommand(player)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onCommandSend(event: PlayerCommandSendEvent) {
        if (!hideTabCompletion) return
        if (event.player.hasPermission(bypassPermission)) return

        event.commands.removeIf { command ->
            val cleanCommand = command.lowercase()
                .removePrefix("bukkit:")
                .removePrefix("minecraft:")

            cleanCommand in protectedCommands ||
                    (cleanCommand in commonPluginCommands && blockCommonPluginCommands) ||
                    (blockBukkitCommands && command.startsWith("bukkit:"))
        }

        if (aggressiveMode) {
            event.commands.removeIf { command ->
                val cmd = server.getPluginCommand(command)
                cmd != null && cmd.plugin != this && !event.player.hasPermission("$command.use")
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onServerListPing(event: PaperServerListPingEvent) {
        if (!hideServerBrand) return
        event.protocolVersion = event.client.protocolVersion
        event.version = fakeServerBrand
    }

    private fun handleProtectedCommand(player: Player, command: String) {
        when (command) {
            "plugins", "pl" -> handlePluginsCommand(player)
            "version", "ver", "about" -> handleVersionCommand(player)
            "help", "?" -> handleHelpCommand(player)
            "icanhasbukkit" -> handleICanHasBukkitCommand(player)
            else -> sendUnknownCommand(player)
        }
    }

    private fun handlePluginsCommand(player: Player) {
        when (hideMode) {
            "unknown-command" -> sendUnknownCommand(player)
            "empty" -> player.sendMessage("§fPlugins (0):")
            "fake-list" -> {
                val plugins = fakePlugins.ifEmpty { listOf("ServerCore", "WorldManager") }
                player.sendMessage("§fPlugins (${plugins.size}): §a${plugins.joinToString(", ")}")
            }
            "permission-denied" -> player.sendMessage("§cI'm sorry, but you do not have permission to perform this command.")
        }
    }

    private fun handleVersionCommand(player: Player) {
        when (hideMode) {
            "unknown-command" -> sendUnknownCommand(player)
            "fake-list" -> player.sendMessage("§fThis server is running §aPaper§f version §agit-Paper-\"$fakeServerBrand\" (MC: 1.21.1)")
            else -> player.sendMessage("§cThis command has been disabled.")
        }
    }

    private fun handleHelpCommand(player: Player) {
        if (hideMode == "unknown-command") {
            sendUnknownCommand(player)
        } else {
            player.sendMessage("§6--------- §fHelp: Index §6---------")
            player.sendMessage("§7Use /help [n] to get page n of help.")
        }
    }

    private fun handleICanHasBukkitCommand(player: Player) {
        when (hideMode) {
            "unknown-command" -> sendUnknownCommand(player)
            else -> player.sendMessage("§fThis server is not running Bukkit!")
        }
    }

    private fun sendUnknownCommand(player: Player) {
        player.sendMessage("§cUnknown command. Type \"/help\" for help.")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!command.name.equals("pluginguard", ignoreCase = true)) return false

        if (!sender.hasPermission("pluginguard.reload")) {
            sender.sendMessage("§cYou don't have permission to use this command.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("§6PluginGuard Commands:")
            sender.sendMessage("§e/pluginguard reload §7- Reload configuration")
            sender.sendMessage("§e/pluginguard status §7- Show protection status")
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                loadConfiguration()
                sender.sendMessage("§aPluginGuard configuration reloaded!")
            }
            "status" -> {
                sender.sendMessage("§6PluginGuard Status:")
                sender.sendMessage("§7Protected Plugins: §f${server.pluginManager.plugins.size}")
                sender.sendMessage("§7Hide Mode: §f$hideMode")
                sender.sendMessage("§7Tab Completion: §f${if (hideTabCompletion) "Hidden" else "Visible"}")
                sender.sendMessage("§7Server Brand: §f${if (hideServerBrand) fakeServerBrand else "Real"}")
                sender.sendMessage("§7Aggressive Mode: §f${if (aggressiveMode) "Enabled" else "Disabled"}")
            }
            else -> sender.sendMessage("§cUnknown subcommand. Use /pluginguard for help.")
        }
        return true
    }
}