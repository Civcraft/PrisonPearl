package vg.civcraft.mc.prisonpearl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerRespawnEvent;

import vg.civcraft.mc.bettershards.BetterShardsAPI;
import vg.civcraft.mc.bettershards.events.PlayerChangeServerReason;
import vg.civcraft.mc.bettershards.misc.PlayerStillDeadException;
import vg.civcraft.mc.mercury.MercuryAPI;
import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;

public class PrisonPearlUtilShards {

	public static void playerRespawnEventSpawn(final Player player, final PlayerRespawnEvent event) {
		if (PrisonPearlPlugin.getSummonManager().isSummoned(player)) {
			PrisonPearlPlugin.getSummonManager().
				returnPlayer(PrisonPearlPlugin.getPrisonPearlManager().getByImprisoned(player), event);
			// Summon method has all the code needed to respawn the player.
			// So we are done here.
			return;
		}
		final PrisonPearlManager pearls = PrisonPearlPlugin.getPrisonPearlManager();
		if (!MercuryAPI.serverName().equals(pearls.getImprisonServer())) {
			// Prison is on another server.
			Bukkit.getScheduler().runTask(PrisonPearlPlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					BetterShardsAPI.randomSpawnPlayer(pearls.getImprisonServer(), player.getUniqueId());
					try {
						PrisonPearlPlugin.doDebug("The player {0} is now teleporting to the server {1} "
								+ "and world {2}.", 
								player.getName(), pearls.getImprisonServer(), pearls.getImprisonWorldName());
						BetterShardsAPI.connectPlayer(player, pearls.getImprisonServer(), PlayerChangeServerReason.PLUGIN);
					} catch (PlayerStillDeadException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				
			});
			
		} else {
			// Same server.
			PrisonPearlPlugin.doDebug("The player {0} was on the right server but"
					+ "wrong world, now teleporting to world {1}", 
					player.getName(), pearls.getImprisonWorldName());
			event.setRespawnLocation(pearls.getImprisonWorld().getSpawnLocation());
		}
	}
	
	public static void playerJoinEventSpawn(final Player player) {
		Bukkit.getScheduler().runTask(PrisonPearlPlugin.getInstance(), new Runnable() {

			@Override
			public void run() {
				PrisonPearlManager pearls = PrisonPearlPlugin.getPrisonPearlManager();
				if (pearls.getImprisonWorld() == null) {
					// The player is on the wrong server, we need to teleport them to the end.
					BetterShardsAPI.randomSpawnPlayer(pearls.getImprisonServer(), player.getUniqueId());
					try {
						PrisonPearlPlugin.doDebug("The player {0} was in the wrong world, now teleporting to {1} "
								+ "and world {2}.", 
								player.getName(), pearls.getImprisonServer(), pearls.getImprisonWorldName());
						BetterShardsAPI.connectPlayer(player, pearls.getImprisonServer(), PlayerChangeServerReason.PLUGIN);
					} catch (PlayerStillDeadException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if (!player.getWorld().equals(pearls.getImprisonWorld())) {
					PrisonPearlPlugin.doDebug("The player {0} was in the wrong world, now teleporting to world {1}", 
							player.getName(), pearls.getImprisonWorldName());
					player.teleport(pearls.getImprisonWorld().getSpawnLocation());
				}
				else {
					PrisonPearlUtil.prisonMotd(player);
				}
			}
			
		});
	}
}
