package vg.civcraft.mc.prisonpearl.managers;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Furnace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlConfig;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.database.DataBaseHandler;
import vg.civcraft.mc.prisonpearl.database.interfaces.IPrisonPearlStorage;
import vg.civcraft.mc.prisonpearl.events.PrisonPearlEvent;
import vg.civcraft.mc.prisonpearl.misc.FakeLocation;
import static vg.civcraft.mc.prisonpearl.PrisonPearlUtil.*;

public class PrisonPearlManager {

	private boolean isMercuryEnabled;
	private boolean isWorldBorderEnabled;
	
	private AltsListManager altsManager;
	private BanManager banManager;
	private PrisonPearlPlugin plugin;
	private DataBaseHandler dbHandler;
	private IPrisonPearlStorage storage;
	
	private Map<String, PermissionAttachment> attachments;
	
	public PrisonPearlManager() {
		isMercuryEnabled = PrisonPearlPlugin.isMercuryEnabled();
		isWorldBorderEnabled = PrisonPearlPlugin.isWorldBorderEnabled();
		
		attachments = new HashMap<String, PermissionAttachment>();
		for (Player player : Bukkit.getOnlinePlayers())
			updateAttachment(player);
		
		altsManager = PrisonPearlPlugin.getAltsListManager();
		plugin = PrisonPearlPlugin.getInstance();
		dbHandler = PrisonPearlPlugin.getDBHandler();
		storage = dbHandler.getStorageHandler().getPrisonPearlStorage();
		banManager = PrisonPearlPlugin.getBanManager();
		
		// PreCache needed alts.
		if (!PrisonPearlConfig.getShouldEnableAltsManager()) {
			for (PrisonPearl pp : storage.getAllPearls()) {
				altsManager.cacheAltListFor(pp.getImprisonedId());
			}
		}
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
		if (isMercuryEnabled)
			MercuryManager.updateLocationToMercury(pp, PrisonPearlEvent.Type.NEW);
		pp.markMove();

		// Create the inventory item
		ItemStack is = generatePearlItem(pp);
		// Give it to the imprisoner
		inv.setItem(pearlnum, is);


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

	public boolean freePearl(final PrisonPearl pp, String reason) {
		// set off an event
		if (!prisonPearlEvent(pp, PrisonPearlEvent.Type.FREED)) {
			return false;
		}
		storage.removePearl(pp, reason); // delete the pearl first

		if (PrisonPearlConfig.getShouldEnableAltsManager()) {
			// unban alts and players if they are allowed to be; bukkit requires kicks be synchronous
			Bukkit.getScheduler().runTask(PrisonPearlPlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					banManager.checkBan(pp.getImprisonedId());
					banManager.checkBanForAlts(pp.getImprisonedId());
				}
				
			});
		}
		MercuryManager.updateLocationToMercury(pp, PrisonPearlEvent.Type.FREED);
		
		PrisonPearlPlugin.getSummonManager().removeSummon(pp);
		
		Player player = pp.getImprisonedPlayer();
		updateAttachment(player);
		if (player != null) {
			Location currentLoc = player.getLocation();
			if (!player.isDead() && currentLoc.getWorld() == getImprisonWorld()) {
				// Here we want to teleport the player to where the pearl is.
				Location loc = pp.getLocation();
				loc.setY(loc.getY() + 1); // So they don't fall through the floor.
				player.teleport(loc);
			}
			
		}
		// Log the free'ing PrisonPearl event with coordinates.
		Player imprisoner = pp.getHolderPlayer();
		String playerLoc = player != null ? serializeLocation(player.getLocation()) : "[???]";
		String message = String.format("Something weird has happened with the pearl " + pp.getImprisonedName() + ", were they maybe freed by pearl feeding?");
		
		if (imprisoner != null && player != null) {
			String imprisonerLoc = serializeLocation(imprisoner.getLocation());
			message = String.format("%s [%s] was freed by %s [%s]", 
					player.getDisplayName(), playerLoc, imprisoner.getDisplayName(), imprisonerLoc);
		} 
		
		if (imprisoner != null) { 
			// immediately update pearl in inventory
			PlayerInventory pi = imprisoner.getInventory();
			Integer location = null;
			for(Entry<Integer, ? extends ItemStack> i : pi.all(Material.ENDER_PEARL).entrySet()) {
				if (this.isItemStackPrisonPearl(pp, i.getValue())) {
					location = i.getKey();
					break;
				}
			}
			if (location != null) {
				pi.clear(location);
				pi.addItem(new ItemStack(Material.ENDER_PEARL, 1));
				imprisoner.updateInventory();
			}
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
			FakeLocation loc = (FakeLocation) pp.getLocation();
			// Raise pearl by 1 block.
			TeleportInfo info = new TeleportInfo(loc.getWorldName(), server, loc.getBlockX(),
					loc.getBlockY()+1, loc.getBlockZ());
			BetterShardsAPI.teleportPlayer(loc.getServerName(), pp.getImprisonedId(), info);
			try {
				BetterShardsAPI.connectPlayer(pp.getImprisonedPlayer(), server, PlayerChangeServerReason.PLUGIN);
			} catch (PlayerStillDeadException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * If pearls havent been fed for at least the configured feed delay on this server, all pearls on this server
	 * will be fed, which means they consume whatever item is used as upkeep cost and if not a sufficient amount of 
	 * that item is available, the pearl will be freed
	 */
	public void feedPearls() {
		String message = "";
		Collection <PrisonPearl> pearls = storage.getAllPearls();

		long inactive_seconds = PrisonPearlConfig.getIgnoreFeedSecond();
		long inactive_hours = PrisonPearlConfig.getIngoreFeedHours();
		long inactive_days = PrisonPearlConfig.getIngoreFeedDays();

		long feedDelay = PrisonPearlConfig.getIgnoreFeedDelay();	//if pearls have been fed in the last x millis it wont feed, defaults to 20 hours
		if(storage.getLastFeed() >= System.currentTimeMillis() - feedDelay) {
			PrisonPearlPlugin.log("Pearls have already been fed, not gonna do it again just yet");
			return;
		} else {
			message+="\nSetting last feed time";
			storage.updateLastFeed(System.currentTimeMillis());
		}
		
		int pearlsfed = 0;
		int coalfed = 0;
		int freedpearls = 0;
		for (PrisonPearl pp : pearls) {
			final UUID prisonerId = pp.getImprisonedId();
			removeLegacyPearl(prisonerId);
			fixMissingPearl(prisonerId);
			//final String prisoner = Bukkit.getPlayer(prisonerId).getName();
			Inventory inv[] = new Inventory[2];
			HolderStateToInventoryResult retval = HolderStateToInventory(pp, inv);
			Location loc = pp.getLocation();
			if (loc instanceof FakeLocation) { // Not on server
				message+="\n" + pp.getImprisonedName() + " was skipped feeding because he is not on the current server.";
				continue; // Other server will handle feeding
			}
			if (retval == HolderStateToInventoryResult.BAD_CONTAINER) {
				String reason = prisonerId + " is being freed. Reason: Freed during coal feed, container was corrupt.";
				freePearl(pp, reason);
				message+="\n freed:"+prisonerId+",reason:"+"badcontainer";
				freedpearls++;
				continue;
			} else if (retval != HolderStateToInventoryResult.SUCCESS) {
				continue;
			}
			else if (isWorldBorderEnabled && PrisonPearlPlugin.getWorldBorderManager().isMaxFeed(loc)){
				String reason = prisonerId + " is being freed. Reason: Freed during coal feed, was outside max distance.";
				freePearl(pp, reason);
				message+="\n freed:"+prisonerId+",reason:"+"maxDistance";
				freedpearls++;
				continue;
			}
			if (inactive_seconds != 0 || inactive_hours != 0 || inactive_days != 0) {
				long inactive_time = pp.getImprisonedOfflinePlayer().getLastPlayed();
				long inactive_millis = inactive_seconds * 1000 + inactive_hours * 3600000 + inactive_days * 86400000;
				inactive_time += inactive_millis;
				if (inactive_time <= System.currentTimeMillis()) {
					// if player has not logged on in the set amount of time than ignore feeding
					message += "\nnot fed inactive: " + prisonerId;
					continue;
				}
			}
			message = message + "Pearl Id: " + prisonerId + " in a " + pp.getHolderBlockState().getType();
			ItemStack requirement = new ItemStack(PrisonPearlConfig.getResourceUpkeepMaterial(), 
					PrisonPearlConfig.getResourceUpkeepAmount());
			int requirementSize = requirement.getAmount();

			if(inv[0].containsAtLeast(requirement,requirementSize)) {
				int pearlnum;
				pearlnum = inv.length;
				message = message + "\n Chest contains enough purestrain coal.";
				inv[0].removeItem(requirement);
				pearlsfed++;
				coalfed += requirementSize;
				message+="\n fed:" + prisonerId + ",location:"+ pp.describeLocation();
			} else if(inv[1] != null && inv[1].containsAtLeast(requirement,requirementSize)){
				message = message + "\n Chest contains enough purestrain coal.";
				inv[1].removeItem(requirement);
				pearlsfed++;
				coalfed += requirementSize;
				message+="\n fed:" + prisonerId + ",location:"+ pp.describeLocation();
			} else {
				message = message + "\n Chest does not contain enough purestrain coal.";
				String reason = prisonerId + " is being freed. Reason: Freed during coal feed, container did not have enough coal.";
				freePearl(pp, reason);
				message+="\n freed:"+prisonerId+",reason:"+"nocoal"+",location:"+pp.describeLocation();
				freedpearls++;
			}
		}
		message = message + "\n Feeding Complete. " + pearlsfed + " were fed " + coalfed + " coal. " + freedpearls + " players were freed.";
		PrisonPearlPlugin.log(message);
	}
	
	/**
	 * Gets the PrisonPearl object of the player imprisoned in the given ItemStack
	 * @param stack ItemStack to get the PrisonPearl for
	 * @return PrisonPearl of the player imprisoned in the given ItemStack or null if no player is imprisoned in that item
	 */
	public PrisonPearl getPearlByItemStack(ItemStack stack) {
		if (stack == null || !stack.hasItemMeta() || stack.getType() != Material.ENDER_PEARL)
    		return null;
    	if (!stack.getItemMeta().hasLore())
    		return null;
    	List<String> lore = stack.getItemMeta().getLore();
    	if (lore.size() != 5 || !stack.getItemMeta().hasEnchants() || !stack.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS))
    		return null;
    	UUID uuid = UUID.fromString(lore.get(1));
    	PrisonPearl pp = getByImprisoned(uuid);
    	if (pp == null){
    		stack.setItemMeta(null);
    		return null;
    	}
    	int id = Integer.parseInt(lore.get(4).split(" ")[1]);
    	boolean isValid = uuid.equals(pp.getImprisonedId()) && id == pp.getUniqueIdentifier();
		if (!isValid) {
			stack.setItemMeta(null);
			pp = null;
		}
    	return pp;
	}
	
	/**
	 * Assumes the given player is imprisoned and searches through the inventory his pearl is held in. If that inventory contains
	 * a pearl, which has the imprisoned players name, but is not a valid pearl after the current criterias, the pearl will be
	 * deleted so a proper one can be regenerated later on instead
	 * @param uuid UUID of the imprisoned player
	 */
	private void removeLegacyPearl(UUID uuid) {
		PrisonPearl pp = getByImprisoned(uuid);
		if (pp == null) {
			return;
		}
		if (pp.getLocation() instanceof FakeLocation) {
			return;
		}
		final String prisonerName = NameLayerManager.getName(uuid);
    	Block b = pp.getLocation().getBlock();
    	if (!(b.getState() instanceof InventoryHolder)) {
    		return;
    	}
    	Inventory inv = ((InventoryHolder) b.getState()).getInventory();
    	for(int i = 0; i < inv.getSize() ; i++) {
    		ItemStack is = inv.getItem(i);
    		if (isLegacyPearl(is, prisonerName)) {
    			inv.setItem(i, null);
    			PrisonPearlPlugin.getInstance().info("Removed legacy pearl holding " + uuid + " at " + b.getLocation());
    			return;
    		}
    	}
    	
    	//check for doublechest
    	if (b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST) {
    		Block adjacentChest = null;
    		if (b.getRelative(BlockFace.NORTH).getType() == b.getType()) {
    			adjacentChest = b.getRelative(BlockFace.NORTH);
    		}
    		if (b.getRelative(BlockFace.SOUTH).getType() == b.getType()) {
    			adjacentChest = b.getRelative(BlockFace.SOUTH);
    		}
    		if (b.getRelative(BlockFace.EAST).getType() == b.getType()) {
    			adjacentChest = b.getRelative(BlockFace.EAST);
    		}
    		if (b.getRelative(BlockFace.WEST).getType() == b.getType()) {
    			adjacentChest = b.getRelative(BlockFace.WEST);
    		}
    		if (adjacentChest != null) {
    			inv = ((InventoryHolder) adjacentChest.getState()).getInventory();
    			for(int i = 0; i < inv.getSize() ; i++) {
    	    		ItemStack is = inv.getItem(i);
    	    		if (isLegacyPearl(is, prisonerName)) {
    	    			inv.setItem(i, null);
    	    			PrisonPearlPlugin.getInstance().info("Removed legacy pearl holding " + uuid + " at " + adjacentChest.getLocation());
    	    			return;
    	    		}
    	    	}
    		}
    	}
	}
	
	private boolean isLegacyPearl(ItemStack is, String prisonerName) {
		if (is == null) {
			return false;
		}
		if (is.getType() == Material.ENDER_PEARL && is.hasItemMeta() && is.getItemMeta().hasDisplayName() && 
				is.getItemMeta().getDisplayName().equals(prisonerName)) {
			//pearl with the name of the imprisoned player
			if (getPearlByItemStack(is) == null) {
				//not a valid pearl the way pearls are currently tracked
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Assumes the given player is imprisoned and regenerates a new pearl item, if the old one is no longer in the inventory block it's supposed
	 * to be and the block is still an inventory with empty slots to generate the pearl in
	 * 
	 * @param uuid UUID of the imprisoned player
	 */
	@CivConfig(name = "fixMissingPearls", def = "false", type = CivConfigType.Bool)
	private void fixMissingPearl(UUID uuid) {
		if (!plugin.GetConfig().get("fixMissingPearls").getBool())
    		return;
    	PrisonPearl pp = getByImprisoned(uuid);
    	Location loc = pp.getLocation();
    	if (loc instanceof FakeLocation) {
    		return;
    	}
    	Block b = pp.getLocation().getBlock();
    	Inventory[] inv = new Inventory[2];
    	BlockState inherentViolence = pp.getHolderBlockState();
    	// Grabs the inventories.
    	switch(b.getType()) {
		case FURNACE:
			inv[0] = ((Furnace)inherentViolence).getInventory();
			break;
		case DISPENSER:
			inv[0] = ((Dispenser)inherentViolence).getInventory();
			break;
		case BREWING_STAND:
			inv[0] = ((BrewingStand)inherentViolence).getInventory();
			break;
		case CHEST:
		case TRAPPED_CHEST:
			Chest c = ((Chest)inherentViolence);
			DoubleChestInventory dblInv = null;
			try {
				dblInv = (DoubleChestInventory)c.getInventory();
				inv[0] = dblInv.getLeftSide();
				inv[1] = dblInv.getRightSide();
			} catch(Exception e){
				inv[0] = c.getInventory();
			}
			break;
		default:
			inv[0] = null;
			inv[1] = null;
		}
		ItemStack stack = null;
		// Scans the inventories looking for the prisonpearl.
    	for (Inventory i: inv) {
    		if (i == null)
    			continue;
    		for (int x = 0; x < i.getSize(); x++) {
    			ItemStack s = i.getItem(x);
    			if (s == null || s.getType() != Material.ENDER_PEARL)
    				continue;
    			PrisonPearl tmp = getPearlByItemStack(s);
    			if (tmp == null)
    				continue;
    			if (tmp.getImprisonedId().equals(uuid)) {
    				stack = s;
    				break;
    			}
    		}
    		if (stack != null)
    			break;
    	}
    	if (stack == null) {
    		for (Inventory i: inv) {
            	if (stack != null) {
        			break;
            	}
            	if (i == null) {
            		continue;
            	}
        		for (int x = 0; x < i.getSize(); x++) {
        			ItemStack current = i.getItem(x);
        			if (current == null) {
        				storage.removePearl(pp, "Regenerating pearl cause it was lost. UUID is: " + pp.getImprisonedId().toString());
        				String name = NameLayerManager.getName(uuid);
        				pp = new PrisonPearl(name, uuid, loc, new Random().nextInt(1000), pp.getKillerUUID(), pp.getImprisonTime());
        				addPearl(pp);
        				ItemStack pearlStack = generatePearlItem(pp);
        				i.setItem(x, pearlStack);
        				stack = pearlStack;
        				break;
        			}
        		}
    		}
    	}
	}
	
	private ItemStack generatePearlItem(PrisonPearl pp) {
		ItemStack is = new ItemStack(Material.ENDER_PEARL);
		ItemMeta im = is.getItemMeta();
		im.setDisplayName(ChatColor.GOLD + pp.getImprisonedName());
		List <String> lore = new LinkedList<String>();
		lore.add(ChatColor.GOLD + pp.getImprisonedName() + ChatColor.RESET + " is held in this pearl");
		lore.add(pp.getImprisonedId().toString());
		lore.add(ChatColor.RESET + "Killed by " + ChatColor.GOLD + pp.getKillerName());
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		if (pp.getImprisonTime() != -1) {
			lore.add(ChatColor.BLUE + "Imprisoned " + sdf.format(new Date(pp.getImprisonTime())));
		}
		else {
			lore.add(ChatColor.BLUE + "Imprisoned a long time ago");
		}
		lore.add(ChatColor.DARK_GREEN + "Unique: " + pp.getUniqueIdentifier());
		im.setLore(lore);
		im.addEnchant(Enchantment.DURABILITY, 1, true);
		im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		is.setItemMeta(im);
		return is;
	}
	
	private HolderStateToInventoryResult HolderStateToInventory(PrisonPearl pp, Inventory[] inv) {
		if (pp == null || inv == null) {
			return HolderStateToInventoryResult.BAD_PARAMETER;
		}
		BlockState inherentViolence = pp.getHolderBlockState();
//		if (Bukkit.getPluginManager().isPluginEnabled("EnderExpansion")){
//			if (pp.getLocation().getBlock().getType() == Material.ENDER_CHEST){
//				inv[0] = Enderplugin.getchestInventory(pp.getLocation());
//				return HolderStateToInventoryResult.SUCCESS;
//			}
//		}
		if (inherentViolence == null) {
			return HolderStateToInventoryResult.NULL_STATE;
		}
		Material mat = inherentViolence.getType();
		
		switch(mat) {
		case FURNACE:
			inv[0] = ((Furnace)inherentViolence).getInventory();
			break;
		case DISPENSER:
			inv[0] = ((Dispenser)inherentViolence).getInventory();
			break;
		case BREWING_STAND:
			inv[0] = ((BrewingStand)inherentViolence).getInventory();
			break;
		case CHEST:
		case TRAPPED_CHEST:
			Chest c = ((Chest)inherentViolence);
			DoubleChestInventory dblInv = null;
			try {
				dblInv = (DoubleChestInventory)c.getInventory();
				inv[0] = dblInv.getLeftSide();
				inv[1] = dblInv.getRightSide();
			} catch(Exception e){
				inv[0] = c.getInventory();
			}
			break;
		default:
			return HolderStateToInventoryResult.BAD_CONTAINER;
		}
		if (inv[0] == null && inv[1] == null) {
			return HolderStateToInventoryResult.NULL_INVENTORY;
		}
		return HolderStateToInventoryResult.SUCCESS;
	}
	
	private enum HolderStateToInventoryResult {
		SUCCESS, BAD_PARAMETER, NULL_STATE, BAD_CONTAINER, NULL_INVENTORY
	}
}
