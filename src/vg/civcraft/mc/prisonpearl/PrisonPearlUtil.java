package vg.civcraft.mc.prisonpearl;

import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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
}