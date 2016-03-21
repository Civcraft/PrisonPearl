package vg.civcraft.mc.prisonpearl.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;

import vg.civcraft.mc.bettershards.BetterShardsAPI;
import vg.civcraft.mc.bettershards.BetterShardsPlugin;
import vg.civcraft.mc.bettershards.events.PlayerChangeServerReason;
import vg.civcraft.mc.bettershards.misc.BedLocation;
import vg.civcraft.mc.bettershards.misc.PlayerStillDeadException;
import vg.civcraft.mc.bettershards.misc.TeleportInfo;
import vg.civcraft.mc.civmodcore.annotations.CivConfig;
import vg.civcraft.mc.civmodcore.annotations.CivConfigType;
import vg.civcraft.mc.civmodcore.annotations.CivConfigs;
import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlConfig;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.PrisonPearlUtil;
import vg.civcraft.mc.prisonpearl.database.DataBaseHandler;
import vg.civcraft.mc.prisonpearl.database.interfaces.IPrisonPearlStorage;
import vg.civcraft.mc.prisonpearl.events.PrisonPearlEvent;
import vg.civcraft.mc.prisonpearl.misc.FakeLocation;

import static vg.civcraft.mc.prisonpearl.PrisonPearlUtil.*;

public class PrisonPearlManager {

	private boolean isMercury;
	
	private AltsListManager altsManager;
	private BanManager banManager;
	private PrisonPearlPlugin plugin;
	private DataBaseHandler dbHandler;
	private IPrisonPearlStorage storage;
	
	private Map<String, PermissionAttachment> attachments;
	
	public PrisonPearlManager() {
		isMercury = PrisonPearlPlugin.isMercuryEnabled();
		
		attachments = new HashMap<String, PermissionAttachment>();
		for (Player player : Bukkit.getOnlinePlayers())
			updateAttachment(player);
		
		altsManager = PrisonPearlPlugin.getAltsListManager();
		plugin = PrisonPearlPlugin.getInstance();
		dbHandler = PrisonPearlPlugin.getDBHandler();
		storage = dbHandler.getStorageHandler().getPrisonPearlStorage();
		banManager = PrisonPearlPlugin.getBanManager();
		
		feedPearls();
	}
	
	public boolean imprisonPlayer(Player imprisoned, Player imprisoner) {
		return imprisonPlayer(imprisoned.getUniqueId(), imprisoner);
	}

	public boolean imprisonPlayer(UUID imprisonedId, Player imprisoner) {	
		altsManager.cacheAltListFor(imprisonedId);
		
		// set up the imprisoner's inventory
		Inventory inv = imprisoner.getInventory();
		ItemStack stack = null;
		int stacknum = -1;

		// scan for the smallest stack of normal ender pearls
		for (Entry<Integer, ? extends ItemStack> entry :
				inv.all(Material.ENDER_PEARL).entrySet()) {
			ItemStack newstack = entry.getValue();
			int newstacknum = entry.getKey();
			if (!newstack.hasItemMeta()) {
				if (stack != null) {
					// don't keep a stack bigger than the previous one
					if (newstack.getAmount() > stack.getAmount()) {
						continue;
					}
					// don't keep an identical sized stack in a higher slot
					if (newstack.getAmount() == stack.getAmount() &&
							newstacknum > stacknum) {
						continue;
					}
				}

				stack = newstack;
				stacknum = entry.getKey();
			}
		}

		int pearlnum;
		ItemStack dropStack = null;
		if (stacknum == -1) { // no pearl (admin command)
			// give him a new one at the first empty slot
			pearlnum = inv.firstEmpty();
		} else if (stack.getAmount() == 1) { // if he's just got one pearl
			pearlnum = stacknum; // put the prison pearl there
		} else {
			// otherwise, put the prison pearl in the first empty slot
			pearlnum = inv.firstEmpty();
			if (pearlnum > 0) {
				// and reduce his stack of pearls by one
				stack.setAmount(stack.getAmount() - 1);
				inv.setItem(stacknum, stack);
			} else { // no empty slot?
				inv.clear(stacknum); // clear before drop
				dropStack = new ItemStack(Material.ENDER_PEARL, stack.getAmount() - 1);
				pearlnum = stacknum; // then overwrite his stack of pearls
			}
		}

		// drop pearls that otherwise would be deleted
		if (dropStack != null) {
			imprisoner.getWorld().dropItem(imprisoner.getLocation(), dropStack);
			Bukkit.getLogger().info(
				imprisoner.getLocation() + ", " + dropStack.getAmount());
		}

		if (!imprisoner.hasPermission("prisonpearl.normal.pearlplayer")) {
			return false;
		}

		// create the prison pearl
		String name = NameLayerManager.getName(imprisonedId);
		
		// Lets check if the pearls exists and if so remove it.
		PrisonPearl potentialPearl = storage.getByImprisoned(imprisonedId);
		if (potentialPearl != null) {
			storage.removePearl(potentialPearl, "Pearl removed because they were reimprisoned.");
		}
		
		PrisonPearl pp = storage.newPearl(name, imprisonedId, imprisoner);
		// set off an event
		if (!prisonPearlEvent(pp, PrisonPearlEvent.Type.NEW, imprisoner)) {
			storage.removePearl(pp, "Something cancelled the creation of the pearl for player " + pp.getImprisonedName() +
					" uuid: " + pp.getImprisonedId());
			return false;
		}
		if (isMercury)
			MercuryManager.updateLocationToMercury(pp, PrisonPearlEvent.Type.NEW);
		pp.markMove();

		// Create the inventory item
		ItemStack is = new ItemStack(Material.ENDER_PEARL, 1);
		ItemMeta im = is.getItemMeta();
		// Rename pearl to that of imprisoned player
		im.setDisplayName(name);
		List<String> lore = new ArrayList<String>();
		// Gives pearl lore that says more info when hovered over
		lore.add(name + " is held within this pearl");
		lore.add("UUID: "+pp.getImprisonedId());
		lore.add("Unique: " + pp.getUniqueIdentifier());
		// Given enchantment effect (durability used because it doesn't affect pearl behaviour)
		im.addEnchant(Enchantment.DURABILITY, 1, true);
		im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		im.setLore(lore);
		is.setItemMeta(im);
		// Give it to the imprisoner
		inv.setItem(pearlnum, is);
		// Reason for edit: Gives pearl enchantment effect (distinguishable, unstackable) Gives name of prisoner in inventory.


        //check if imprisoned was imprisoned by themselves
		if(imprisoner.getUniqueId() == pp.getImprisonedId()){
			//ok player imprisoned themselves throw the pearl on the floor
			imprisoner.getWorld().dropItem(imprisoner.getLocation(), is);
			Bukkit.getLogger().info("Player attempted to imprison themself. Pearl was dropped");
		}
		
		if (PrisonPearlConfig.isPrisonResetBed()) {
			Player imprisoned = Bukkit.getPlayer(imprisonedId);
			// clear out the players bed
			if (imprisoned != null) {
				if (PrisonPearlPlugin.isBetterShardsEnabled() && PrisonPearlPlugin.isMercuryEnabled()) {
					if (BetterShardsAPI.hasBed(imprisonedId)) {
						String worldName = getImprisonWorldName();
						String server = getImprisonServer();
						TeleportInfo info = new TeleportInfo(worldName, server, 0, 90, 0);
						BedLocation bed = new BedLocation(imprisonedId, info);
						BetterShardsAPI.addBedLocation(imprisonedId, bed);
					}
				}
				else {
					World respawnworld = getImprisonWorld();
					imprisoned.setBedSpawnLocation(respawnworld.getSpawnLocation());
				}
			}
		}
		
		return true;
	}
	
	public World getImprisonWorld() {
		String name = getImprisonWorldName();
		World world = Bukkit.getWorld(name);
		return world;
	}
	
	public String getImprisonWorldName() {
		return PrisonPearlConfig.getImprisonWorldName();
	}
	
	public String getImprisonServer() {
		return PrisonPearlConfig.getImprisonServerName();
	}
	
	public boolean freePlayer(Player player, String reason) {
		PrisonPearl pp = storage.getByImprisoned(player);
		return pp != null && freePearl(pp, reason);
	}

	public boolean freePearl(PrisonPearl pp, String reason) {
		// set off an event
		if (!prisonPearlEvent(pp, PrisonPearlEvent.Type.FREED)) {
			return false;
		}
		storage.removePearl(pp, reason); // delete the pearl first
		// unban alts and players if they are allowed to be
		banManager.checkBan(pp.getImprisonedId());
		banManager.checkBanForAlts(pp.getImprisonedId());
		MercuryManager.updateLocationToMercury(pp, PrisonPearlEvent.Type.FREED);
		
		Player player = pp.getImprisonedPlayer();
		updateAttachment(player);
		if (player != null) {
			Location currentLoc = player.getLocation();
			if (!player.isDead() && currentLoc.getWorld() == getImprisonWorld()) {
				respawnPlayerCorrectly(player, pp);
			}
			
		}
		// Log the free'ing PrisonPearl event with coordinates.
		Player imprisoner = pp.getHolderPlayer();
		String playerLoc = player != null ? serializeLocation(player.getLocation()) : "[???]";
		String message = String.format("Something weird has happened with the pearl " + pp.getImprisonedName());
		
		if (imprisoner != null) {
			String imprisonerLoc = serializeLocation(imprisoner.getLocation());
			message = String.format("%s [%s] was freed by %s [%s]", 
					imprisoner.getName(), playerLoc, imprisoner.getDisplayName(), imprisonerLoc);
		} 
		
		PrisonPearlPlugin.log(message);
		
		if (player != null) {
			player.sendMessage("You've been freed!");
		}
		return true;
	}
	
	private boolean prisonPearlEvent(PrisonPearl pp, PrisonPearlEvent.Type type) {
		return prisonPearlEvent(pp, type, null);
	}

	private boolean prisonPearlEvent(PrisonPearl pp,
			PrisonPearlEvent.Type type, Player imprisoner) {
		PrisonPearlEvent event = new PrisonPearlEvent(pp, type, imprisoner);
		Bukkit.getPluginManager().callEvent(event);
		return !event.isCancelled();
	}
	
	
	public void updateAttachment(Player player) {
		if (player == null) {
			return;
		}
		PermissionAttachment attachment = attachments.get(player.getName());
		if (attachment == null) {
			attachment = player.addAttachment(plugin);
			attachments.put(player.getName(), attachment);
		}
		
		if (storage.isImprisoned(player.getUniqueId())) {
			for (String grant : PrisonPearlConfig.getPrisonGrantPerms())
				attachment.setPermission(grant, true);
			for (String deny : PrisonPearlConfig.getPrisonDenyPerms())
				attachment.setPermission(deny, false);			
		} else {
			for (String grant : PrisonPearlConfig.getPrisonGrantPerms())
				attachment.unsetPermission(grant);
			for (String deny : PrisonPearlConfig.getPrisonDenyPerms())
				attachment.unsetPermission(deny);		
		}
		
		player.recalculatePermissions();
	}
	
	public PermissionAttachment removeAttachments(Player p) {
		if (p == null)
			return null;
		return attachments.get(p);
	}
	
	private Location fuzzLocation(Location loc) {
		if (loc == null)
			return null;

		double rad = Math.random()*Math.PI*2;
		Location newloc = loc.clone();
		if (newloc == null)
			return null;
		newloc.add(1.2*Math.cos(rad), 1.2*Math.sin(rad), 0);
		return newloc;
	}
	
	// hill climbing algorithm which attempts to randomly spawn prisoners while actively avoiding pits
	// the obsidian pillars, or lava.
	public Location getPrisonSpawnLocation() {
		if (PrisonPearlPlugin.isBetterShardsEnabled()) {
			return BetterShardsPlugin.getRandomSpawn().getLocation();
		}
		Random rand = new Random();
		Location loc = getImprisonWorld().getSpawnLocation(); // start at spawn
		for (int i=0; i<30; i++) { // for up to 30 iterations
			if (loc.getY() > 40 && loc.getY() < 70 && i > 5 && !isObstructed(loc)) // if the current candidate looks reasonable and we've iterated at least 5 times
				return loc; // we're done
			
			Location newloc = loc.clone().add(rand.nextGaussian()*(2*i), 0, rand.nextGaussian()*(2*i)); // pick a new location near the current one
			newloc = moveToGround(newloc);
			if (newloc == null)
				continue;
			
			if (newloc.getY() > loc.getY()+(int)(rand.nextGaussian()*3) || loc.getY() > 70) // if its better in a fuzzy sense, or if the current location is too high
				loc = newloc; // it becomes the new current location
		}

		return loc;
	}
	
	private Location moveToGround(Location loc) {
		Location ground = new Location(loc.getWorld(), loc.getX(), 100, loc.getZ());
		while (ground.getBlockY() >= 1) {
			if (!ground.getBlock().isEmpty())
				return ground;
			ground.add(0, -1, 0);
		}
		return null;
	}
	
	private boolean isObstructed(Location loc) {
		Location ground = new Location(loc.getWorld(), loc.getX(), 100, loc.getZ());
		while (ground.getBlockY() >= 1) {
			if (!ground.getBlock().isEmpty())
				break;
				
			ground.add(0, -1, 0);
		}
		
		for (int x=-2; x<=2; x++) {
			for (int y=-2; y<=2; y++) {
				for (int z=-2; z<=2; z++) {
					Location l = ground.clone().add(x, y, z);
					Material type = l.getBlock().getType();
					if (type == Material.LAVA || type == Material.STATIONARY_LAVA || type == Material.ENDER_PORTAL || type == Material.BEDROCK)
						return true;
				}
			}
		}
		
		return false;
	}
	
	public PrisonPearl getPearlByItemStack(ItemStack stack) {
		return storage.getPearlbyItemStack(stack);
	}
	
	public boolean isImprisoned(UUID uuid) {
		return storage.isImprisoned(uuid);
	}
	
	public boolean isImprisoned(Player p) {
		return storage.isImprisoned(p);
	}
	
	public PrisonPearl getByImprisoned(UUID uuid) {
		return storage.getByImprisoned(uuid);
	}
	
	public PrisonPearl getByImprisoned(Player p) {
		return storage.getByImprisoned(p);
	}
	
	public boolean isItemStackPrisonPearl(PrisonPearl pp, ItemStack stack) {
		PrisonPearl p = getPearlByItemStack(stack);
		return p != null ? p.getImprisonedName().equals(pp.getImprisonedName()) : false;
	}
	
	public void addPearl(PrisonPearl pp) {
		storage.addPearl(pp);
	}
	
	public void freePearlFromMercury(PrisonPearl pp, String reason, String server) {
		storage.removePearl(pp, reason);
		if (server != null && pp.getImprisonedPlayer() != null) {
			respawnPlayerCorrectly(pp.getImprisonedPlayer(), pp);
		}
	}
	
	public void feedPearls() {
		PrisonPearlPlugin.log(storage.feedPearls(this));
	}
}
