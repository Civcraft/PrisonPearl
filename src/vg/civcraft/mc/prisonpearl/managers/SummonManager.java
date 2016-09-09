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
import vg.civcraft.mc.prisonpearl.PrisonPearlUtilShards;
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
		if (PrisonPearlPlugin.isBetterShardsEnabled()) {
			return PrisonPearlUtilShards.handleSummonedPlayerSummon(pearl);
		}
		else {
			return PrisonPearlUtil.handleSummonedPlayerSummon(pearl);
		}
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
		if (PrisonPearlPlugin.isBetterShardsEnabled()) {
			return PrisonPearlUtilShards.handleSummonedPlayerReturn(pearl, event);
		}
		else {
			return PrisonPearlUtil.handleSummonedPlayerReturn(pearl, event);
		}
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
