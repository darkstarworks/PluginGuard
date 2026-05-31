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

    // Immutable snapshot of every config-derived value. Swapped atomically on reload via
    // a @Volatile reference so concurrent event handlers on different Folia region threads
    // always observe a consistent set of values without taking any lock.
    private data class Settings(
        val hideMode: String,
        val fakePlugins: List<String>,
        val bypassPermission: String,
        val protectedCommands: Set<String>,
        val commonPluginCommands: Set<String>,
        val fakeServerBrand: String,
        val blockBukkitCommands: Boolean,
        val redirectBukkitCommands: Boolean,
        val hideTabCompletion: Boolean,
        val blockUnknownCommands: Boolean,
        val hideServerBrand: Boolean,
        val blockCommonPluginCommands: Boolean,
        val aggressiveMode: Boolean,
    )

    @Volatile
    private lateinit var settings: Settings

    override fun onEnable() {
        saveDefaultConfig()
        settings = loadSettings()
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

    fun shouldHideServerBrand(): Boolean = settings.hideServerBrand
    fun fakeBrand(): String = settings.fakeServerBrand

    private fun loadSettings(): Settings {
        reloadConfig()
        return Settings(
            hideMode = config.getString("hide-mode", "unknown-command")!!,
            fakePlugins = config.getStringList("fake-plugins"),
            bypassPermission = config.getString("bypass-permission", "pluginguard.bypass")!!,
            protectedCommands = config.getStringList("protected-commands").mapTo(HashSet()) { it.lowercase() },
            commonPluginCommands = config.getStringList("common-plugin-commands").mapTo(HashSet()) { it.lowercase() },
            fakeServerBrand = config.getString("fake-server-brand", "vanilla")!!,
            blockBukkitCommands = config.getBoolean("block-bukkit-commands", true),
            redirectBukkitCommands = config.getBoolean("redirect-bukkit-commands", false),
            hideTabCompletion = config.getBoolean("hide-tab-completion", true),
            blockUnknownCommands = config.getBoolean("block-unknown-commands", true),
            hideServerBrand = config.getBoolean("hide-server-brand", true),
            blockCommonPluginCommands = config.getBoolean("block-common-plugin-commands", true),
            aggressiveMode = config.getBoolean("aggressive-mode", false),
        )
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        val s = settings
        if (player.hasPermission(s.bypassPermission)) return

        // Extract just the base command without allocating a lowercased copy of the whole message
        // or splitting on spaces. Hot path: runs on every command a player issues.
        val msg = event.message
        val start = if (msg.startsWith('/')) 1 else 0
        val spaceIdx = msg.indexOf(' ', start)
        val end = if (spaceIdx == -1) msg.length else spaceIdx
        if (start >= end) return
        val baseCommand = msg.substring(start, end).lowercase()

        val cleanCommand = when {
            baseCommand.startsWith("bukkit:") -> baseCommand.substring(7)
            baseCommand.startsWith("minecraft:") -> baseCommand.substring(10)
            else -> baseCommand
        }

        when {
            baseCommand in s.protectedCommands || cleanCommand in s.protectedCommands -> {
                event.isCancelled = true
                if (baseCommand.startsWith("bukkit:") && s.redirectBukkitCommands) {
                    handlePluginsCommand(player, s)
                } else {
                    handleProtectedCommand(player, cleanCommand, s)
                }
            }
            baseCommand.startsWith("bukkit:") && s.blockBukkitCommands -> {
                event.isCancelled = true
                sendUnknownCommand(player)
            }
            baseCommand in s.commonPluginCommands && s.blockCommonPluginCommands -> {
                event.isCancelled = true
                sendUnknownCommand(player)
            }
            s.aggressiveMode && !player.hasPermission("$baseCommand.use") -> {
                if (server.getPluginCommand(baseCommand) != null) {
                    event.isCancelled = true
                    sendUnknownCommand(player)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onCommandSend(event: PlayerCommandSendEvent) {
        val s = settings
        if (!s.hideTabCompletion) return
        if (event.player.hasPermission(s.bypassPermission)) return

        event.commands.removeIf { command ->
            val cleanCommand = when {
                command.startsWith("bukkit:") -> command.substring(7).lowercase()
                command.startsWith("minecraft:") -> command.substring(10).lowercase()
                else -> command.lowercase()
            }

            cleanCommand in s.protectedCommands ||
                    (cleanCommand in s.commonPluginCommands && s.blockCommonPluginCommands) ||
                    (s.blockBukkitCommands && command.startsWith("bukkit:"))
        }

        if (s.aggressiveMode) {
            event.commands.removeIf { command ->
                val cmd = server.getPluginCommand(command)
                cmd != null && cmd.plugin != this && !event.player.hasPermission("$command.use")
            }
        }
    }

    private fun handleProtectedCommand(player: Player, command: String, s: Settings) {
        when (command) {
            "plugins", "pl" -> handlePluginsCommand(player, s)
            "version", "ver", "about" -> handleVersionCommand(player, s)
            "help", "?" -> handleHelpCommand(player, s)
            "icanhasbukkit" -> handleICanHasBukkitCommand(player, s)
            else -> sendUnknownCommand(player)
        }
    }

    private fun handlePluginsCommand(player: Player, s: Settings) {
        when (s.hideMode) {
            "unknown-command" -> sendUnknownCommand(player)
            "empty" -> player.sendMessage(Component.text("Plugins (0):", NamedTextColor.WHITE))
            "fake-list" -> {
                val plugins = s.fakePlugins.ifEmpty { listOf("ServerCore", "WorldManager") }
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

    private fun handleVersionCommand(player: Player, s: Settings) {
        when (s.hideMode) {
            "unknown-command" -> sendUnknownCommand(player)
            "fake-list" -> player.sendMessage(
                Component.text("This server is running ", NamedTextColor.WHITE)
                    .append(Component.text("Paper", NamedTextColor.GREEN))
                    .append(Component.text(" version ", NamedTextColor.WHITE))
                    .append(Component.text("git-Paper-\"${s.fakeServerBrand}\" (MC: ${server.minecraftVersion})", NamedTextColor.GREEN))
            )
            else -> player.sendMessage(Component.text("This command has been disabled.", NamedTextColor.RED))
        }
    }

    private fun handleHelpCommand(player: Player, s: Settings) {
        if (s.hideMode == "unknown-command") {
            sendUnknownCommand(player)
        } else {
            player.sendMessage(Component.text("--------- Help: Index ---------", NamedTextColor.GOLD))
            player.sendMessage(Component.text("Use /help [n] to get page n of help.", NamedTextColor.GRAY))
        }
    }

    private fun handleICanHasBukkitCommand(player: Player, s: Settings) {
        when (s.hideMode) {
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
                settings = loadSettings()
                sender.sendMessage(Component.text("PluginGuard configuration reloaded!", NamedTextColor.GREEN))
            }
            "status" -> {
                val s = settings
                sender.sendMessage(Component.text("PluginGuard Status:", NamedTextColor.GOLD))
                fun row(label: String, value: String) = sender.sendMessage(
                    Component.text("$label: ", NamedTextColor.GRAY)
                        .append(Component.text(value, NamedTextColor.WHITE))
                )
                row("Protected Plugins", "${server.pluginManager.plugins.size}")
                row("Hide Mode", s.hideMode)
                row("Tab Completion", if (s.hideTabCompletion) "Hidden" else "Visible")
                row("Server Brand", if (s.hideServerBrand) s.fakeServerBrand else "Real")
                row("Aggressive Mode", if (s.aggressiveMode) "Enabled" else "Disabled")
            }
            else -> sender.sendMessage(Component.text("Unknown subcommand. Use /pluginguard for help.", NamedTextColor.RED))
        }
        return true
    }
}
