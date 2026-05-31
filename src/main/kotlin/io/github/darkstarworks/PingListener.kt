package io.github.darkstarworks

import com.destroystokyo.paper.event.server.PaperServerListPingEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Isolated so the Paper-only event class is only resolved when this listener is loaded.
 * PluginGuard checks for class presence before registering.
 */
class PingListener(private val plugin: PluginGuard) : Listener {

    @EventHandler(priority = EventPriority.LOW)
    fun onServerListPing(event: PaperServerListPingEvent) {
        if (!plugin.shouldHideServerBrand()) return
        event.protocolVersion = event.client.protocolVersion
        event.version = plugin.fakeBrand()
    }
}
