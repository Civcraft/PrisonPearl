package vg.civcraft.mc.prisonpearl;

import static vg.civcraft.mc.prisonpearl.PrisonPearlUtil.dropInventory;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerRespawnEvent;

import vg.civcraft.mc.bettershards.BetterShardsAPI;
import vg.civcraft.mc.bettershards.events.PlayerChangeServerReason;
import vg.civcraft.mc.bettershards.misc.PlayerStillDeadException;
import vg.civcraft.mc.bettershards.misc.TeleportInfo;
import vg.civcraft.mc.mercury.MercuryAPI;
import vg.civcraft.mc.mercury.PlayerDetails;
import vg.civcraft.mc.prisonpearl.events.SummonEvent;
import vg.civcraft.mc.prisonpearl.events.SummonEvent.Type;
import vg.civcraft.mc.prisonpearl.managers.MercuryManager;
import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;
import vg.civcraft.mc.prisonpearl.misc.FakeLocation;

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
	
	public static boolean handleSummonedPlayerReturn(PrisonPearl pearl, PlayerRespawnEvent event) {
		final Player pearled = pearl.getImprisonedPlayer();
		
		Type t = null;
		if (event == null) {
			t = Type.RETURNED;
		} else {
			// Since there is a PlayerRespawnEvent we know that the player died and is being 
			// returned that way.
			t = Type.DIED;
		}
		
		// Let's first deal with if the player is being returned to a different server.
		Summon s = PrisonPearlPlugin.getSummonManager().getSummon(pearl);
		s.setTime(System.currentTimeMillis());
		if (s.getReturnLocation() instanceof FakeLocation) {
			// They are.
			// Here we check if the pearl originated on this server.
			if (pearled != null) {
				// Player is on the same server 
				PrisonPearlPlugin.getSummonManager().removeSummon(pearl);
				MercuryManager.returnPPSummon(pearl.getImprisonedId());
			}
			// Here would be for all other servers.
			// Let's now check if they are even online.
			PlayerDetails details = MercuryAPI.getServerforAccount(pearl.getImprisonedId());
			
			if (details != null) {
				// They are online.
				final FakeLocation loc = (FakeLocation) s.getReturnLocation();
				PrisonPearlPlugin.getSummonManager().removeSummon(pearl);
				SummonEvent summonEvent = new SummonEvent(pearl, t, pearled.getLocation());
				Bukkit.getPluginManager().callEvent(summonEvent);
				if (PrisonPearlConfig.getShouldPPReturnKill()) {
					pearled.setHealth(0);
				} else {
					if (event != null) {
						// This is being called from the player respawning.
						Bukkit.getScheduler().runTask(PrisonPearlPlugin.getInstance(), new Runnable() {

							@Override
							public void run() {
								try {
									// Since the player is dead we actually want to random spawn them in that world.
									BetterShardsAPI.randomSpawnPlayer(loc.getServerName(), pearled.getUniqueId());
									BetterShardsAPI.connectPlayer(pearled, loc.getServerName(), PlayerChangeServerReason.PLUGIN);
								} catch (PlayerStillDeadException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							
						});
						return true;
					} else {
						try {
							TeleportInfo info = new TeleportInfo(loc.getWorldName(), loc.getServerName(), 
									loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
							BetterShardsAPI.teleportPlayer(info.getServer(), pearled.getUniqueId(), info);
							return BetterShardsAPI.connectPlayer(pearled, loc.getServerName(), PlayerChangeServerReason.PLUGIN);
						} catch (PlayerStillDeadException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
		} else {
			SummonEvent summonEvent = new SummonEvent(pearl, t);
			Bukkit.getPluginManager().callEvent(summonEvent);
			// Player is being returned same server.
			// Let's check if the player is online.
			MercuryManager.returnPPSummon(pearl.getImprisonedId());
			if (pearled == null) {
				// They are not so lets just remove the summon.
				PrisonPearlPlugin.getSummonManager().removeSummon(pearl);
			} else {
				// They are.
				if (event != null) {
					event.setRespawnLocation(s.getReturnLocation());
				} else {
					pearled.teleport(s.getReturnLocation());
				}
			}
		}
		return false;
	}
	
	public static boolean handleSummonedPlayerSummon(PrisonPearl pearl) {
		final Player pearled = pearl.getImprisonedPlayer();
		if (pearled == null) {
			// Here we are going to deal with all the fun that could be sharding.
			// So we know that the player is not on this server so we want to request them here if they are online.
			PlayerDetails details = MercuryAPI.getServerforAccount(pearl.getImprisonedId());
			if (details != null) {
				PrisonPearlPlugin.doDebug("Player {0} is being requested to be summoned from {1}.", 
						pearl.getImprisonedId(), MercuryAPI.serverName());
				MercuryManager.requestPPSummon(pearl.getImprisonedId());
				return true;
			}
			// If they are not online we return false and say they are not online and cannot
			// be summoned.
		} else if (pearled != null && PrisonPearlPlugin.isMercuryEnabled()) {
			// This statement is triggered usually from Mercury Listener.
			// There can be the chance where someone summoned a player on the same shard so we 
			// need to account for that as well.
			
			// Create the Summon Object
			Summon s = new Summon(pearl.getImprisonedId(), pearled.getLocation(), pearl);
			PrisonPearlPlugin.getSummonManager().addSummonPlayer(s);
			// Lets drop the inventory if need be.
			if (PrisonPearlConfig.shouldPpsummonClearInventory()) {
				dropInventory(pearled, pearled.getLocation(), PrisonPearlConfig.shouldPpsummonLeavePearls());
			}
			Location loc = pearl.getLocation();
			if (loc instanceof FakeLocation) {
				// Here we know that the player holding the pearl is on another server
				// and we now need to deal with that.
				FakeLocation fakeLoc = (FakeLocation) loc;
				TeleportInfo info = new TeleportInfo(fakeLoc.getWorldName(), fakeLoc.getServerName(), 
						fakeLoc.getBlockX(), fakeLoc.getBlockY() + 1, fakeLoc.getBlockZ());
				BetterShardsAPI.teleportPlayer(info.getServer(), pearled.getUniqueId(), info);
				try {
					return BetterShardsAPI.connectPlayer(pearled, fakeLoc.getServerName(), PlayerChangeServerReason.PLUGIN);
				} catch (PlayerStillDeadException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				// So this is that weird case where the summoner is the same shard as the
				// player that is imprisoned.
				SummonEvent event = new SummonEvent(pearl, Type.SUMMONED, pearled.getLocation());
				Bukkit.getPluginManager().callEvent(event);
				pearled.teleport(loc);
				return true;
			}
		}
		return false;
	}
}
