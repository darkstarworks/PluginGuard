package io.github.darkstarworks

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
        registerPaperListeners()
        logger.info("PluginGuard enabled - protecting ${server.pluginManager.plugins.size} plugins")
    }

    private fun registerPaperListeners() {
        // PaperServerListPingEvent only exists on Paper and its forks. Probe via reflection so
        // the plugin still loads on Spigot/Bukkit (server brand spoofing simply won't apply).
        try {
            Class.forName("com.destroystokyo.paper.event.server.PaperServerListPingEvent")
            server.pluginManager.registerEvents(PingListener(this), this)
        } catch (_: ClassNotFoundException) {
            logger.warning("PaperServerListPingEvent unavailable - server-brand spoofing disabled (Paper or a Paper fork required)")
        }
    }

    fun shouldHideServerBrand(): Boolean = hideServerBrand
    fun fakeBrand(): String = fakeServerBrand

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
            "empty" -> player.sendMessage(Component.text("Plugins (0):", NamedTextColor.WHITE))
            "fake-list" -> {
                val plugins = fakePlugins.ifEmpty { listOf("ServerCore", "WorldManager") }
                player.sendMessage(
                    Component.text("Plugins (${plugins.size}): ", NamedTextColor.WHITE)
                        .append(Component.text(plugins.joinToString(", "), NamedTextColor.GREEN))
                )
            }
            "permission-denied" -> player.sendMessage(
                Component.text("I'm sorry, but you do not have permission to perform this command.", NamedTextColor.RED)
            )
        }
    }

    private fun handleVersionCommand(player: Player) {
        when (hideMode) {
            "unknown-command" -> sendUnknownCommand(player)
            "fake-list" -> player.sendMessage(
                Component.text("This server is running ", NamedTextColor.WHITE)
                    .append(Component.text("Paper", NamedTextColor.GREEN))
                    .append(Component.text(" version ", NamedTextColor.WHITE))
                    .append(Component.text("git-Paper-\"$fakeServerBrand\" (MC: ${server.minecraftVersion})", NamedTextColor.GREEN))
            )
            else -> player.sendMessage(Component.text("This command has been disabled.", NamedTextColor.RED))
        }
    }

    private fun handleHelpCommand(player: Player) {
        if (hideMode == "unknown-command") {
            sendUnknownCommand(player)
        } else {
            player.sendMessage(Component.text("--------- Help: Index ---------", NamedTextColor.GOLD))
            player.sendMessage(Component.text("Use /help [n] to get page n of help.", NamedTextColor.GRAY))
        }
    }

    private fun handleICanHasBukkitCommand(player: Player) {
        when (hideMode) {
            "unknown-command" -> sendUnknownCommand(player)
            else -> player.sendMessage(Component.text("This server is not running Bukkit!", NamedTextColor.WHITE))
        }
    }

    private fun sendUnknownCommand(player: Player) {
        player.sendMessage(Component.text("Unknown command. Type \"/help\" for help.", NamedTextColor.RED))
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!command.name.equals("pluginguard", ignoreCase = true)) return false

        if (!sender.hasPermission("pluginguard.reload")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("PluginGuard Commands:", NamedTextColor.GOLD))
            sender.sendMessage(
                Component.text("/pluginguard reload ", NamedTextColor.YELLOW)
                    .append(Component.text("- Reload configuration", NamedTextColor.GRAY))
            )
            sender.sendMessage(
                Component.text("/pluginguard status ", NamedTextColor.YELLOW)
                    .append(Component.text("- Show protection status", NamedTextColor.GRAY))
            )
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                loadConfiguration()
                sender.sendMessage(Component.text("PluginGuard configuration reloaded!", NamedTextColor.GREEN))
            }
            "status" -> {
                sender.sendMessage(Component.text("PluginGuard Status:", NamedTextColor.GOLD))
                fun row(label: String, value: String) = sender.sendMessage(
                    Component.text("$label: ", NamedTextColor.GRAY)
                        .append(Component.text(value, NamedTextColor.WHITE))
                )
                row("Protected Plugins", "${server.pluginManager.plugins.size}")
                row("Hide Mode", hideMode)
                row("Tab Completion", if (hideTabCompletion) "Hidden" else "Visible")
                row("Server Brand", if (hideServerBrand) fakeServerBrand else "Real")
                row("Aggressive Mode", if (aggressiveMode) "Enabled" else "Disabled")
            }
            else -> sender.sendMessage(Component.text("Unknown subcommand. Use /pluginguard for help.", NamedTextColor.RED))
        }
        return true
    }
}
