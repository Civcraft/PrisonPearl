package vg.civcraft.mc.prisonpearl.listener;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.events.PrisonPearlEvent;
import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;
import vg.civcraft.mc.prisonpearl.managers.PrisonPortaledPlayerManager;

public class PrisonPortaledPlayerListener implements Listener{

	private PrisonPearlManager pearls;
	private PrisonPortaledPlayerManager manager;
	
	public PrisonPortaledPlayerListener() {
		Bukkit.getPluginManager().registerEvents(this, PrisonPearlPlugin.getInstance());
		pearls = PrisonPearlPlugin.getPrisonPearlManager();
		manager = PrisonPearlPlugin.getPrisonPortaledPlayerManager();
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		Player player = event.getPlayer();
		if (pearls.isImprisoned(player))
			return;
		else if (!pearls.isImprisoned(player) && manager.isPlayerPortaledToPrison(player)) {
			manager.removePlayerPortaled(player.getUniqueId());
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerPortalEvent(PlayerPortalEvent event) {
		Player player = event.getPlayer();
		if (!pearls.isImprisoned(player.getUniqueId()))
			return;
		Location toLoc = event.getTo();
		if (toLoc == null) {
			return;
		}
		if (toLoc.getWorld() == pearls.getImprisonWorld())
			manager.addPlayerPortaled(player.getUniqueId());
		else{
			manager.removePlayerPortaled(player.getUniqueId());
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void playerJoinEvent(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		Location toLoc = p.getLocation();
		if (toLoc == null || toLoc.getWorld() != pearls.getImprisonWorld()) {
			return;
		}
		
		if (!pearls.isImprisoned(p) && manager.isPlayerPortaledToPrison(p)) {
			p.sendMessage("While away, you were freed!");
			p.setHealth(0.0); // Need to kill the player;
			manager.removePlayerPortaled(p.getUniqueId());
			return;
		}
		else if (pearls.isImprisoned(p)) {
			manager.addPlayerPortaled(p.getUniqueId());
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void playerQuitEvent(PlayerQuitEvent event) {
		Player p = event.getPlayer();
		Location toLoc = p.getLocation();
		if (toLoc == null || toLoc.getWorld() != pearls.getImprisonWorld()) {
			return;
		}
		
		if (!pearls.isImprisoned(p) && manager.isPlayerPortaledToPrison(p)) {
			// Don't need to kill the player here because if he/she was freed
			// then they were already handled elsewhere.
			manager.removePlayerPortaled(p.getUniqueId());
		}
		else if (pearls.isImprisoned(p)) {
			manager.addPlayerPortaled(p.getUniqueId());
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onPrisonPearlEvent(PrisonPearlEvent event) {
		if (event.getType() == PrisonPearlEvent.Type.NEW) {
			UUID uuid = event.getPrisonPearl().getImprisonedId();
			manager.addPlayerPortaled(uuid);
		}
	}
}
