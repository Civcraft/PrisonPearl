package vg.civcraft.mc.prisonpearl.listener;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import vg.civcraft.mc.bettershards.events.PlayerChangeServerEvent;
import vg.civcraft.mc.bettershards.events.PlayerChangeServerReason;
import vg.civcraft.mc.bettershards.events.PlayerEnsuredToTransitEvent;
import vg.civcraft.mc.bettershards.events.PlayerFailedToTransitEvent;
import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.Summon;
import vg.civcraft.mc.prisonpearl.managers.MercuryManager;
import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;
import vg.civcraft.mc.prisonpearl.managers.SummonManager;

/**
 * The point of this class is to catch if BetterShards is trying to random spawn a player when 
 * we are trying to send them to the Prison World.
 * @author rourke750
 *
 */
public class BetterShardsListener implements Listener{

	private PrisonPearlManager pearls;
	private SummonManager summons;
	
	public BetterShardsListener() {
		if (!PrisonPearlPlugin.isBetterShardsEnabled())
			return;
		Bukkit.getPluginManager().registerEvents(this, PrisonPearlPlugin.getInstance());
		pearls = PrisonPearlPlugin.getPrisonPearlManager();
		summons = PrisonPearlPlugin.getSummonManager();
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerTransferServerEvent(PlayerChangeServerEvent event) {
		UUID uuid = event.getPlayerUUID();

		if (event.getServerTravelingTo().equals(pearls.getImprisonServer())) {
			return;
		}

		PlayerChangeServerReason reason = event.getReason();
		if ((reason == PlayerChangeServerReason.RANDOMSPAWN || reason == PlayerChangeServerReason.PORTAL
				|| reason == PlayerChangeServerReason.BED) && pearls.isImprisoned(uuid))
			event.setCancelled(true);
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerFailedToTransit(PlayerFailedToTransitEvent event) {
		UUID uuid = event.getUUID();
		if (!summons.isSummoned(uuid))
			return;
		PrisonPearl pp = pearls.getByImprisoned(uuid);
		Player p = pp.getHolderPlayer();
		p.sendMessage(ChatColor.RED + "There was an issue returning the player. Please "
				+ "contact the admins of the server.");
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerEnsuredToTransit(PlayerEnsuredToTransitEvent event) {
		
	}
}
