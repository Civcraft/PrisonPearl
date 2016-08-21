package vg.civcraft.mc.prisonpearl.managers;

import static vg.civcraft.mc.prisonpearl.PrisonPearlUtil.dropInventory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import vg.civcraft.mc.bettershards.BetterShardsAPI;
import vg.civcraft.mc.bettershards.events.PlayerChangeServerReason;
import vg.civcraft.mc.bettershards.misc.PlayerStillDeadException;
import vg.civcraft.mc.bettershards.misc.TeleportInfo;
import vg.civcraft.mc.mercury.MercuryAPI;
import vg.civcraft.mc.mercury.PlayerDetails;
import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlConfig;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.PrisonPearlUtil;
import vg.civcraft.mc.prisonpearl.Summon;
import vg.civcraft.mc.prisonpearl.database.interfaces.ISummonStorage;
import vg.civcraft.mc.prisonpearl.events.SummonEvent;
import vg.civcraft.mc.prisonpearl.events.SummonEvent.Type;
import vg.civcraft.mc.prisonpearl.misc.FakeLocation;

public class SummonManager {

	private ISummonStorage storage;
	private PrisonPearlManager pearls;

	public SummonManager() {
		storage = PrisonPearlPlugin.getDBHandler().getStorageHandler().getSummonStorage();
		pearls = PrisonPearlPlugin.getPrisonPearlManager();
		Bukkit.getScheduler().scheduleSyncRepeatingTask(PrisonPearlPlugin.getInstance(), new Runnable() {
			public void run() {
				inflictSummonDamage();
			}
		}, 0, PrisonPearlConfig.getSummonDamageTicks());
	}

	public boolean isSummoned(Player p) {
		return isSummoned(p.getUniqueId());
	}

	public boolean isSummoned(UUID uuid) {
		return storage.isSummoned(uuid);
	}

	public boolean isSummoned(PrisonPearl pp) {
		return isSummoned(pp.getImprisonedId());
	}

	/**
	 * This method will handle getting the player from another server and
	 * current server if it needs to.
	 * 
	 * @param pearl
	 */
	public boolean summonPlayer(PrisonPearl pearl) {
		final Player pearled = pearl.getImprisonedPlayer();
		if (pearled == null && PrisonPearlPlugin.isMercuryEnabled()) {
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
			addSummonPlayer(s);
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
		} else if (pearled != null) {
			Summon s = new Summon(pearl.getImprisonedId(), pearled.getLocation(), pearl);
			addSummonPlayer(s);
			// Here we know the player is on the same server going to the same server. Since
			// Mercury is not enabled.
			// Fucking turtles right.
			SummonEvent event = new SummonEvent(pearl, Type.SUMMONED, pearled.getLocation());
			Bukkit.getPluginManager().callEvent(event);
			if (PrisonPearlConfig.shouldPpsummonClearInventory()) {
				PrisonPearlUtil.dropInventory(pearled, pearled.getLocation(), PrisonPearlConfig.shouldPpsummonLeavePearls());
			}
			pearled.teleport(s.getPearlLocation());
			PrisonPearlPlugin.doDebug("Player {0} was just summoned!", pearled.getUniqueId());
			return true;
		}
		return false;
	}

	/**
	 * This method should be used if for example we earlier requested a player
	 * be summoned from another server and only now they came on and we have the
	 * proper details.
	 * 
	 * @param s The summoned Player
	 */
	public void addSummonPlayer(Summon s) {
		storage.addSummon(s);
	}
	
	public boolean returnPlayer(PrisonPearl pearl) {
		return returnPlayer(pearl, null);
	}

	public boolean returnPlayer(PrisonPearl pearl, PlayerRespawnEvent event) {
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
		Summon s = getSummon(pearl);
		s.setTime(System.currentTimeMillis());
		if (s.getReturnLocation() instanceof FakeLocation) {
			// They are.
			
			if (pearled == null) {
				// This happens when the command /ppreturn is used.
				// The player may not be on this server but can now be returned.
				// We want to send the request to all servers that the player should now be freed.
				MercuryManager.returnPPSummon(pearl.getImprisonedId());
				SummonEvent summonEvent = new SummonEvent(pearl, t);
				Bukkit.getPluginManager().callEvent(summonEvent);
				return true;
			}
			// Let's now check if they are even online.
			PlayerDetails details = MercuryAPI.getServerforAccount(pearl.getImprisonedId());
			
			if (details != null) {
				// They are online.
				final FakeLocation loc = (FakeLocation) s.getReturnLocation();
				storage.removeSummon(s);
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
			if (pearled == null) {
				// They are not so lets just remove the summon.
				storage.removeSummon(s);
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

	public boolean removeSummon(PrisonPearl pearl) {
		Summon s = getSummon(pearl);
		if (s == null)
			return false;
		storage.removeSummon(s);
		return true;
	}
	
	public Summon getSummon(UUID uuid) {
		return storage.getSummon(uuid);
	}

	public Summon getSummon(Player p) {
		return getSummon(p.getUniqueId());
	}

	public Summon getSummon(PrisonPearl pearl) {
		return storage.getSummon(pearl.getImprisonedId());
	}

	private void inflictSummonDamage() {
		Map<Player, Double> inflictDmg = new HashMap<Player, Double>();
		Iterator<Entry<UUID, Summon>> i = storage.getAllSummons().entrySet().iterator();
		while (i.hasNext()) {
			Summon summon = i.next().getValue();
			PrisonPearl pp = pearls.getByImprisoned(summon.getUUID());
			if (pp == null) {
				System.err.println("Somehow " + summon.getUUID() + " was summoned but isn't imprisoned");
				i.remove();
				storage.removeSummon(summon.getUUID());
				continue;
			}

			Player player = pp.getImprisonedPlayer();
			if (player == null)
				continue;

			Location pploc = pp.getLocation();
			Location playerloc = player.getLocation();

			if (pploc.getWorld() != playerloc.getWorld() || pploc.distance(playerloc) > summon.getMaxDistance()) {
				inflictDmg.put(player, (double) summon.getAmountDamage());
			}
		}
		for (Map.Entry<Player, Double> entry : inflictDmg.entrySet()) {
			final Player player = entry.getKey();
			final Double damage = entry.getValue();
			player.damage(damage);
		}
	}
}
