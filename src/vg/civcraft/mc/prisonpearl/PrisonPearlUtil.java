package vg.civcraft.mc.prisonpearl;

import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import vg.civcraft.mc.prisonpearl.events.SummonEvent;
import vg.civcraft.mc.prisonpearl.events.SummonEvent.Type;
import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;
import vg.civcraft.mc.prisonpearl.managers.SummonManager;

public class PrisonPearlUtil {

	private static PrisonPearlManager manager;
	private static SummonManager summon;
	
	private static Thread mainThread;
	
	public PrisonPearlUtil() {
		manager = PrisonPearlPlugin.getPrisonPearlManager();
		summon = PrisonPearlPlugin.getSummonManager();
		mainThread = Thread.currentThread();
	}
	
	public static void playerRespawnEventSpawn(Player player, PlayerRespawnEvent event) {
		PrisonPearlManager pearls = PrisonPearlPlugin.getPrisonPearlManager();
		PrisonPearlPlugin.doDebug("The player {0} is now teleporting to world {1}", 
				player.getName(), pearls.getImprisonWorldName());
		event.setRespawnLocation(pearls.getImprisonWorld().getSpawnLocation());
	}
	
	public static void playerJoinEventSpawn(Player player) {
		PrisonPearlManager pearls = PrisonPearlPlugin.getPrisonPearlManager();
		if (!player.getLocation().getWorld().equals(pearls.getImprisonWorld())) {
			PrisonPearlPlugin.doDebug("The player {0} was in the wrong world, now teleporting to world {1}", 
					player.getName(), pearls.getImprisonWorldName());
			player.teleport(pearls.getImprisonWorld().getSpawnLocation());
		}
		else {
			prisonMotd(player);
		}
	}
	
	 /**
     * @param location - the location to serialize into user-friendly text.
     * @return the serialized user-friendly string representing location.
     */
    public static String serializeLocation(Location location) {
    	return String.format("%s, (%d, %d, %d)", location.getWorld().getName(),
    							location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    
    public static void delayedTp(final Player player, final Location loc, final boolean dropInventory) {
		final boolean respawn = loc != null;
		final Location oldLoc = player.getLocation();
		if (respawn) {
			player.setHealth(0.0);
		}
		Bukkit.getScheduler().callSyncMethod(PrisonPearlPlugin.getInstance(), new Callable<Void>() {
			public Void call() {
				if (!respawn) {
					player.teleport(loc);
				}
				if (dropInventory) {
					dropInventory(player, oldLoc, PrisonPearlConfig.shouldPpsummonLeavePearls());
				}
				return null;
			}
		});
	}
    
    public static void dropInventory(Player player, Location loc, boolean leavePearls) {
		if (loc == null) {
			loc = player.getLocation();
		}
		final World world = loc.getWorld();
		Inventory inv = player.getInventory();
		int end = inv.getSize();
		for (int i = 0; i < end; ++i) {
			final ItemStack item = inv.getItem(i);
			if (item == null) {
				continue;
			}
			if (leavePearls && item.getType().equals(Material.ENDER_PEARL)
					&& item.getDurability() == 0) {
				continue;
			}
			inv.clear(i);
			if (!isMainThread(Thread.currentThread())) {
				final Location l = loc;
				Bukkit.getScheduler().runTask(PrisonPearlPlugin.getInstance(), new Runnable() {

					@Override
					public void run() {
						world.dropItemNaturally(l, item);
					}
					
				});
			}
			else
				world.dropItemNaturally(loc, item);
		}
	}
    
    public static boolean isMainThread(Thread t) {
    	return t.equals(mainThread);
    }
    
    // called when a player joins or spawns
 	public static void prisonMotd(Player player) {
 		PrisonPearlManager pearls = PrisonPearlPlugin.getPrisonPearlManager();
 		if (pearls.isImprisoned(player) && !summon.isSummoned(player)) {
 			for (String line : PrisonPearlConfig.getPrisonMotd())
 				player.sendMessage(line);
 			player.sendMessage(pearls.getByImprisoned(player).getMotd());
 		}
 	}
 	
 	public static boolean handleSummonedPlayerReturn(PrisonPearl pearl, PlayerRespawnEvent event) {
 		final Player pearled = pearl.getImprisonedPlayer();
		Summon s = PrisonPearlPlugin.getSummonManager().getSummon(pearled);
		Type t = null;
		if (event == null) {
			t = Type.RETURNED;
		} else {
			// Since there is a PlayerRespawnEvent we know that the player died and is being 
			// returned that way.
			t = Type.DIED;
		}
 		SummonEvent summonEvent = new SummonEvent(pearl, t);
		Bukkit.getPluginManager().callEvent(summonEvent);
		// Player is being returned same server.
		// Let's check if the player is online.
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
		return true;
 	}
 	
 	public static boolean handleSummonedPlayerSummon(PrisonPearl pearl) {
 		final Player pearled = pearl.getImprisonedPlayer();
 		if (pearled != null) {
			Summon s = new Summon(pearl.getImprisonedId(), pearled.getLocation(), pearl);
			PrisonPearlPlugin.getSummonManager().addSummonPlayer(s);
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
}