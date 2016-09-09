package vg.civcraft.mc.prisonpearl.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitTask;

import net.minelink.ctplus.compat.api.NpcIdentity;
import vg.civcraft.mc.bettershards.BetterShardsAPI;
import vg.civcraft.mc.bettershards.BetterShardsPlugin;
import vg.civcraft.mc.bettershards.events.PlayerChangeServerReason;
import vg.civcraft.mc.bettershards.misc.PlayerStillDeadException;
import vg.civcraft.mc.mercury.MercuryAPI;
import vg.civcraft.mc.namelayer.NameAPI;
import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlConfig;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.PrisonPearlUtil;
import vg.civcraft.mc.prisonpearl.PrisonPearlUtilShards;
import vg.civcraft.mc.prisonpearl.Summon;
import vg.civcraft.mc.prisonpearl.events.PrisonPearlEvent;
import vg.civcraft.mc.prisonpearl.managers.BanManager;
import vg.civcraft.mc.prisonpearl.managers.CombatTagManager;
import vg.civcraft.mc.prisonpearl.managers.DamageLogManager;
import vg.civcraft.mc.prisonpearl.managers.MercuryManager;
import vg.civcraft.mc.prisonpearl.managers.NameLayerManager;
import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;
import vg.civcraft.mc.prisonpearl.managers.SummonManager;
import vg.civcraft.mc.prisonpearl.managers.WorldBorderManager;
import vg.civcraft.mc.prisonpearl.misc.FakeLocation;

import static vg.civcraft.mc.prisonpearl.PrisonPearlUtil.*;

public class PlayerListener implements Listener {

	private static final Location RESPAWN_PLAYER = new Location(null, 0, 0, 0);
	private static PrisonPearlManager pearls;
	private static CombatTagManager combatManager;
	private static BanManager ban;
	private static SummonManager summon;
	private static DamageLogManager dlManager;
	private static WorldBorderManager wb;

	public PlayerListener() {
		Bukkit.getPluginManager().registerEvents(this, PrisonPearlPlugin.getInstance());
		pearls = PrisonPearlPlugin.getPrisonPearlManager();
		ban = PrisonPearlPlugin.getBanManager();
		summon = PrisonPearlPlugin.getSummonManager();
		combatManager = PrisonPearlPlugin.getCombatTagManager();
		dlManager = PrisonPearlPlugin.getDamageLogManager();
		wb = PrisonPearlPlugin.getWorldBorderManager();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		final Player player = event.getPlayer();
		pearls.updateAttachment(player);

		if (PrisonPearlPlugin.isBetterShardsEnabled()) {
			// In case a player comes from another server and has a pearl.
			if (BetterShardsPlugin.getTransitManager().isPlayerInArrivalTransit(player.getUniqueId())) {
				for (ItemStack stack : player.getInventory().getContents()) {
					if (stack == null)
						continue;
					final PrisonPearl pp = pearls.getPearlByItemStack(stack);
					if (pp == null)
						continue;
					pp.setHolder(player);
					pp.markMove();
				}
			}
		}
		
		if (player.isDead())
			return;

		PrisonPearl pp = pearls.getByImprisoned(player);
		if (pp != null) {
			if (summon.isSummoned(pp)) {
				return; // They are summoned let them be.  They will either die or not.
			} else if (PrisonPearlPlugin.isBetterShardsEnabled()) {
				PrisonPearlUtilShards.playerJoinEventSpawn(player);
			} else {
				PrisonPearlUtil.playerJoinEventSpawn(player);
			}
		}
	}

	// don't let people escape through the end portal
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerPortalEvent(PlayerPortalEvent event) {
		Player player = event.getPlayer();

		if (pearls.isImprisoned(player) && !summon.isSummoned(player)) { // if in prison but not imprisoned
			event.setCancelled(true);
		}
	}

	// remove permission attachments and record the time players log out
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		PermissionAttachment attachment = pearls.removeAttachments(event.getPlayer());
		if (attachment != null)
			attachment.remove();
	}

	// adjust spawnpoint if necessary
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerRespawn(final PlayerRespawnEvent event) {
		if (pearls.isImprisoned(event.getPlayer())) {
			PrisonPearlPlugin.doDebug("Player {0} is respawning while pearled.", event.getPlayer().getName());
			prisonMotd(event.getPlayer());
			// Time to teleport player if they are on wrong server.
			if (PrisonPearlPlugin.isBetterShardsEnabled()) { // Lets spawn them using shard code.
				PrisonPearlUtilShards.playerRespawnEventSpawn(event.getPlayer(), event);
			} else { // Spawn them with normal code.
				PrisonPearlUtil.playerRespawnEventSpawn(event.getPlayer(), event);
			}
		}
	}

	// called when a player joins or spawns
	private void prisonMotd(Player player) {
		if (pearls.isImprisoned(player) && !summon.isSummoned(player)) {
			for (String line : PrisonPearlConfig.getPrisonMotd())
				player.sendMessage(line);
			player.sendMessage(pearls.getByImprisoned(player).getMotd());
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onEntityDeath(EntityDeathEvent event) {
		if (!(event.getEntity() instanceof Player))
			return;
		
		Player player = (Player)event.getEntity();
		UUID uuid = player.getUniqueId();
		String playerName = player.getName();
		
		if (combatManager.isCombatTagPlusNPC(player)){
			NpcIdentity iden = combatManager.getCombatTagPlusNPCIdentity(player);
			uuid = iden.getId();
			playerName = iden.getName();
			PrisonPearlPlugin.log("NPC Player: " + playerName + ", ID: " + uuid);
		} else if (combatManager.isEnabled() && !combatManager.isCombatTagged(player)) {
			PrisonPearlPlugin.log("Player: " + playerName + " is out of combatTag, immune from pearling.");
			return;
		}
		
		PrisonPearl pp = pearls.getByImprisoned(uuid); // find out if the player is imprisoned
		if (pp != null) { // if imprisoned
			if (!PrisonPearlConfig.getAllowPrisonStealing() || player.getLocation().getWorld() == pearls.getImprisonWorld()) {
				// bail if prisoner stealing isn't allowed, or if the player is in prison (can't steal prisoners from prison ever)
				PrisonPearlPlugin.log("Player: " + playerName + " is already pearled and cannot be stolen here.");
				
				// reveal location of pearl to damaging players if pearl stealing is disabled
				for (Player damager : dlManager.getDamagers(player)) {
					damager.sendMessage(ChatColor.GREEN+"[PrisonPearl] "+playerName+" cannot be pearled here because they are already "+pp.describeLocation());
				}
				return;
			}
		}
		
		for (Player damager : dlManager.getDamagers(player)) { // check to see if anyone can imprison him
			if (pp != null && pp.getHolderPlayer() == damager) { // if this damager has already imprisoned this person
				PrisonPearlPlugin.log("Player: " + playerName + " is already held by " + damager.getName() + ", cannot re-pearl.");
				break; // don't be confusing and re-imprison him, just let him die
			}
			
			int firstpearl = Integer.MAX_VALUE; // find the first regular enderpearl in their inventory
			for (Entry<Integer, ? extends ItemStack> entry : damager.getInventory().all(Material.ENDER_PEARL).entrySet()) {
				ItemStack stack = entry.getValue();
				if (!stack.hasItemMeta())
					firstpearl = Math.min(entry.getKey(), firstpearl);
			}
			
			if (firstpearl == Integer.MAX_VALUE) // no pearl
				continue; // no imprisonment
			
			if (PrisonPearlConfig.getMustPrisonPearlHotBar() && firstpearl > 9) // bail if it must be in the hotbar
				continue; 
				
			if (pearls.imprisonPlayer(uuid, damager)) {// otherwise, try to imprison
				PrisonPearlPlugin.log("Player " + playerName + " has been imprisoned by " + damager.getName() + ".");
				damager.sendMessage(ChatColor.GREEN + "You have imprisoned " + player.getDisplayName());
				player.sendMessage(ChatColor.GREEN + "You have been imprisoned by " + damager.getDisplayName());
				break;
			}
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerQuitforBroadcast(PlayerQuitEvent event) {
		
	}
	
	// Drops a pearl when a player leaves.
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerQuitPearlCheck(PlayerQuitEvent event) {
		Player imprisoner = event.getPlayer();
		Location loc = imprisoner.getLocation();
		World world = imprisoner.getWorld();
		Inventory inv = imprisoner.getInventory();
		if (PrisonPearlPlugin.isBetterShardsEnabled() && BetterShardsPlugin.getTransitManager()
						.hasTransferredRecently(imprisoner.getUniqueId(), 1500)) {
			PrisonPearlPlugin.doDebug("The player {0} has quit and is transfering servers.", imprisoner.getName());
			// If player is transfered to another server.
			for (Entry<Integer, ? extends ItemStack> entry :
				inv.all(Material.ENDER_PEARL).entrySet()) {
				ItemStack item = entry.getValue();
				PrisonPearl pp = pearls.getPearlByItemStack(item);
				if (pp == null) {
					continue;
				}

				PrisonPearlPlugin.doDebug("Found the pearl of {0} located on {1}.", pp.getImprisonedName(), 
						imprisoner.getName());
				pp.setHolder(new FakeLocation(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
					MercuryAPI.serverName(), imprisoner.getName()));
			}
			return;
		} else if (combatManager.isCombatTagged(imprisoner)) {
			// If the player is combat tagged lets wait for the entity to despawn to drop the pearl.
			PrisonPearlPlugin.doDebug("Player {0} quit while combat tagged.", imprisoner.getName());
			return;
		}
		PrisonPearlPlugin.doDebug("Player {0} quit and is now dropping pearl.", imprisoner.getName());
		for (Entry<Integer, ? extends ItemStack> entry :
				inv.all(Material.ENDER_PEARL).entrySet()) {
			ItemStack item = entry.getValue();
			PrisonPearl pp = pearls.getPearlByItemStack(item);
			if (pp == null) {
				continue;
			}
			pp.markMove();
			int slot = entry.getKey();
			inv.clear(slot);
			world.dropItemNaturally(loc, item);
		}
	}
	
	private Map<UUID, BukkitTask> unloadedPearls = new HashMap<UUID, BukkitTask>();
	// Free the pearl if its on a chunk that unloads
	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkUnload(ChunkUnloadEvent event) {		
		for (Entity e : event.getChunk().getEntities()) {
			if (!(e instanceof Item))
				continue;

			final PrisonPearl pp = pearls.getPearlByItemStack(((Item) e).getItemStack());
			
			if (pp == null)
				continue;
			
			final Entity entity = e;
			final World world = entity.getWorld();
			final int chunkX = event.getChunk().getX();
			final int chunkZ = event.getChunk().getZ();
			// doing this in onChunkUnload causes weird things to happen

			event.setCancelled(true);
			final UUID uuid = pp.getImprisonedId();
			if (unloadedPearls.containsKey(uuid))
				return;
			BukkitTask count = Bukkit.getScheduler().runTaskLater(PrisonPearlPlugin.getInstance(), new Runnable() {
				public void run(){
					// Check that chunk is still unloaded before freeing
					if (!world.isChunkLoaded(chunkX, chunkZ)
						&& pearls.freePearl(pp, pp.getImprisonedName() + " ("+
								pp.getImprisonedId() + ") is being freed. Reason: Chunk with PrisonPearl unloaded."))
					{
						entity.remove();
					}

					unloadedPearls.remove(uuid);
				}
			}, PrisonPearlConfig.getPrisonUnloadTimerTicks());
			unloadedPearls.put(uuid, count);
		}
	}
	
	// Free the pearl if it combusts in lava/fire
	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityCombustEvent(EntityCombustEvent event) {
		if (!(event.getEntity() instanceof Item))
			return;
		
		PrisonPearl pp = pearls.getPearlByItemStack(
			((Item) event.getEntity()).getItemStack());
		if (pp == null)
			return;
		
		String reason = pp.getImprisonedName() + " ("+pp.getImprisonedId() + ") is being freed. Reason: PrisonPearl combusted(lava/fire).";
		pearls.freePearl(pp, reason);
	}
	
	// Handle inventory dragging properly.
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryDrag(InventoryDragEvent event) {
		if(event.isCancelled())
			return;
		
		Map<Integer, ItemStack> items = event.getNewItems();

		for(Integer slot : items.keySet()) {
			ItemStack item = items.get(slot);
			
			PrisonPearl pearl = pearls.getPearlByItemStack(item);
			if(pearl != null) {
				boolean clickedTop = event.getView().convertSlot(slot) == slot;
				
				InventoryHolder holder = clickedTop ? event.getView().getTopInventory().getHolder() : event.getView().getBottomInventory().getHolder();
				
				pearl.markMove();
				updatePearlHolder(pearl, holder, event);
				
				if(event.isCancelled()) {
					return;
				}
			}
		}
	}
	
	// Prevent imprisoned players from placing PrisonPearls in their inventory.
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onPrisonPearlClick(InventoryClickEvent event) {
		Player clicker = (Player) event.getWhoClicked();
		
		if (pearls.getPearlByItemStack(event.getCurrentItem()) != null 
			&& pearls.isImprisoned(clicker)) {
			clicker.sendMessage(ChatColor.RED + "Imprisoned players cannot pick up prison pearls!");
			event.setCancelled(true);	// Prevent imprisoned player from grabbing PrisonPearls.
		}
	}
	
	// Track the location of a pearl
	// Forbid pearls from being put in storage minecarts
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClick(InventoryClickEvent event) {
		if(event.isCancelled())
			return;
		
		// announce an prisonpearl if it is clicked
		ItemStack newitem = announcePearl((Player) event.getWhoClicked(), event.getCurrentItem());
		if (newitem != null)
			event.setCurrentItem(newitem);
		
		if(event.getAction() == InventoryAction.COLLECT_TO_CURSOR
				|| event.getAction() == InventoryAction.PICKUP_ALL
				|| event.getAction() == InventoryAction.PICKUP_HALF
				|| event.getAction() == InventoryAction.PICKUP_ONE) {
			PrisonPearl pearl = pearls.getPearlByItemStack(event.getCurrentItem());
			
			if(pearl != null) {
				pearl.markMove();
				updatePearl(pearl, (Player) event.getWhoClicked(), true);
			}
		}
		else if(event.getAction() == InventoryAction.PLACE_ALL
				|| event.getAction() == InventoryAction.PLACE_SOME
				|| event.getAction() == InventoryAction.PLACE_ONE) {	
			PrisonPearl pearl = pearls.getPearlByItemStack(event.getCursor());
			
			if(pearl != null) {
				boolean clickedTop = event.getView().convertSlot(event.getRawSlot()) == event.getRawSlot();
				
				InventoryHolder holder = clickedTop ? event.getView().getTopInventory().getHolder() : event.getView().getBottomInventory().getHolder();
				if (holder==null){
					pearl.getHolderPlayer();
					pearl.markMove();
					// Ender Expansion isn't currently being used anyways.  Not sure if this method will work.
					//ee.updateEnderStoragePrison(pearl, event, player.getTargetBlock(new HashSet<Material>(), 5).getLocation());
				}
				else{
					pearl.markMove();
					updatePearlHolder(pearl, holder, event);
				}
				if (!(holder instanceof Player) && wb.isMaxFeed(pearl.getLocation())){
					HumanEntity human = event.getWhoClicked();
					if (human instanceof Player){
						Player p = (Player) human;
						p.sendMessage(ChatColor.GREEN + "The Pearl of " + pearl.getImprisonedName() + " was placed outside of the feed "
								+ "range, if you leave it here the pearl will be freed on restart.");
						PrisonPearlPlugin.doDebug("The Pearl of {0} was placed outside of the feed "
								+ "range by {1}, if left here the pearl will be freed on restart.",pearl.getImprisonedName(),
								p.getDisplayName());
					}
				}
			}
		} else if(event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {			
			PrisonPearl pearl = pearls.getPearlByItemStack(event.getCurrentItem());
			
			if(pearl != null) {
				boolean clickedTop = event.getView().convertSlot(event.getRawSlot()) == event.getRawSlot();
				
				InventoryHolder holder = !clickedTop ? event.getView().getTopInventory().getHolder() : event.getView().getBottomInventory().getHolder();
				if (holder==null){
					// All legacy code from ender expansion.
					//pearl.getHolderPlayer();
					//pearl.markMove();
					//ee.updateEnderStoragePrison(pearl, event, player.getTargetBlock(new HashSet<Material>(), 5).getLocation());
				}
				else if(holder.getInventory().firstEmpty() >= 0) {
					pearl.markMove();
					updatePearlHolder(pearl, holder, event);
				}
				if (!(holder instanceof Player) && wb.isMaxFeed(pearl.getLocation())){
					HumanEntity human = event.getWhoClicked();
					if (human instanceof Player){
						Player p = (Player) human;
						p.sendMessage(ChatColor.GREEN + "The Pearl of " + pearl.getImprisonedName() + " was placed outside of the feed "
								+ "range, if you leave it here the pearl will be freed on restart.");
						PrisonPearlPlugin.doDebug("The Pearl of {0} was placed outside of the feed "
								+ "range by {1}, if left here the pearl will be freed on restart.",pearl.getImprisonedName(),
								p.getDisplayName());
					}
				}
			}
		}
		else if(event.getAction() == InventoryAction.HOTBAR_SWAP) {
			PlayerInventory playerInventory = event.getWhoClicked().getInventory();
			PrisonPearl pearl = pearls.getPearlByItemStack(playerInventory.getItem(event.getHotbarButton()));
			
			if(pearl != null) {
				boolean clickedTop = event.getView().convertSlot(event.getRawSlot()) == event.getRawSlot();
				
				InventoryHolder holder = clickedTop ? event.getView().getTopInventory().getHolder() : event.getView().getBottomInventory().getHolder();
				
				pearl.markMove();
				updatePearlHolder(pearl, holder, event);
			}
			
			if(event.isCancelled())
				return;
			
			pearl = pearls.getPearlByItemStack(event.getCurrentItem());
			
			if(pearl != null) {
				pearl.markMove();
				updatePearl(pearl, (Player) event.getWhoClicked());
			}
		}
		else if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
			PrisonPearl pearl = pearls.getPearlByItemStack(event.getCursor());
			
			if(pearl != null) {
				boolean clickedTop = event.getView().convertSlot(event.getRawSlot()) == event.getRawSlot();
				
				InventoryHolder holder = clickedTop ? event.getView().getTopInventory().getHolder() : event.getView().getBottomInventory().getHolder();
				
				pearl.markMove();
				updatePearlHolder(pearl, holder, event);
			}
			
			if(event.isCancelled())
				return;
			
			pearl = pearls.getPearlByItemStack(event.getCurrentItem());
			
			if(pearl != null) {
				pearl.markMove();
				updatePearl(pearl, (Player) event.getWhoClicked(), true);
			}
		}
		else if(event.getAction() == InventoryAction.DROP_ALL_CURSOR
				|| event.getAction() == InventoryAction.DROP_ALL_SLOT
				|| event.getAction() == InventoryAction.DROP_ONE_CURSOR
				|| event.getAction() == InventoryAction.DROP_ONE_SLOT) {
			// Handled by onItemSpawn
		}
		else {
			if(pearls.getPearlByItemStack(event.getCurrentItem()) != null || pearls.getPearlByItemStack(event.getCursor()) != null) {
				((Player) event.getWhoClicked()).sendMessage(ChatColor.RED + "Error: PrisonPearl doesn't support this inventory functionality quite yet!");
				
				event.setCancelled(true);
			}
		}
	}
	
	private void updatePearlHolder(PrisonPearl pearl, InventoryHolder holder, Cancellable event) {
		
		if (holder instanceof Chest) {
			updatePearl(pearl, (Chest) holder);
		} else if (holder instanceof DoubleChest) {
			updatePearl(pearl, (Chest) ((DoubleChest) holder).getLeftSide());
		} else if (holder instanceof Furnace) {
			updatePearl(pearl, (Furnace) holder);
		} else if (holder instanceof Dispenser) {
			updatePearl(pearl, (Dispenser) holder);
		} else if (holder instanceof BrewingStand) {
			updatePearl(pearl, (BrewingStand) holder);
		} else if (holder instanceof Player) {
			updatePearl(pearl, (Player) holder);
		}else {
			PrisonPearlPlugin.doDebug("Update Pearl Holder for {0}, it has been placed in an unknown container: {1}",
					pearl, holder.getClass().getClass()
			);

			event.setCancelled(true);
		}
	}
	
	// Track the location of a pearl if it spawns as an item for any reason
	@EventHandler(priority = EventPriority.MONITOR)
	public void onItemSpawn(ItemSpawnEvent event) {
		Item item = event.getEntity();
		PrisonPearl pp = pearls.getPearlByItemStack(item.getItemStack());
		if (pp == null)
			return;
		pp.markMove();
		updatePearl(pp, item);
	}
	
	// Track the location of a pearl if a player picks it up
	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		PrisonPearl pp = pearls.getPearlByItemStack(event.getItem().getItemStack());
		if (pp == null)
			return;
		
		pp.markMove();
		updatePearl(pp, event.getPlayer());
		
		// If pearl was in unloaded chunk
		if (unloadedPearls.isEmpty())
			return;
		UUID want = pp.getImprisonedId();
		for (UUID uuid: unloadedPearls.keySet()){
			if (want.equals(uuid)){
				unloadedPearls.get(uuid).cancel();
				unloadedPearls.remove(uuid);
			}
		}
	}

	// Prevent imprisoned players from picking up PrisonPearls.
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerPickupPearl(PlayerPickupItemEvent event) {
		if (pearls.getPearlByItemStack(event.getItem().getItemStack()) != null 
			&& pearls.isImprisoned(event.getPlayer())) {
			event.setCancelled(true);
		}
	}
	
	// Deny pearls traveling to other worlds.
	@EventHandler(priority = EventPriority.HIGHEST)
	public void worldChangeEvent(PlayerTeleportEvent event){
		TeleportCause cause = event.getCause();
		if (TeleportCause.CHORUS_FRUIT.equals(cause) ||
			TeleportCause.ENDER_PEARL.equals(cause) ||
			TeleportCause.UNKNOWN.equals(cause)) {
			// Normal movement-type events are ignored.
			// Portalling or plugin world changes are not ignored.
			return;
		}
		List<String> denyWorlds = PrisonPearlConfig.getPearlDenyTransferWorlds();
		Location previous = event.getFrom();
		Location to = event.getTo();
		World newWorld = to.getWorld();
		if (!denyWorlds.contains(newWorld.getName()))
			return;
      
		World oldWorld = previous.getWorld();
		//not changing worlds, no reason to free pearls
		if(newWorld.getName().equals(oldWorld.getName()))
			return;
		
		Inventory inv = event.getPlayer().getInventory();
		boolean message = false;
		for (Entry<Integer, ? extends ItemStack> entry :
			inv.all(Material.ENDER_PEARL).entrySet()) {
			ItemStack item = entry.getValue();
			PrisonPearl pp = pearls.getPearlByItemStack(item);
			if (pp == null) {
				continue;
			}
			pp.markMove();
			int slot = entry.getKey();
			inv.clear(slot);
			previous.getWorld().dropItemNaturally(previous, item);
			message = true;
		}
		if (message)
			event.getPlayer().sendMessage(ChatColor.RED + "This world is not allowed " +
				"to have Prison Pearls in it. Your prisoner was dropped where " +
				"you were in the previous world.");
	}

	private void updatePearl(PrisonPearl pp, Item item) {
		pp.setHolder(item);
		Bukkit.getPluginManager().callEvent(
				new PrisonPearlEvent(pp, PrisonPearlEvent.Type.DROPPED));
		MercuryManager.updateLocationToMercury(pp, PrisonPearlEvent.Type.DROPPED);
	}

	private void updatePearl(PrisonPearl pp, Player player) {
	    updatePearl(pp, player, false);
	}

	private void updatePearl(PrisonPearl pp, Player player, boolean isOnCursor) {
		if (isOnCursor) {
			pp.setCursorHolder(player);
		} else {
			pp.setHolder(player);
		}
		Bukkit.getPluginManager().callEvent(
				new PrisonPearlEvent(pp, PrisonPearlEvent.Type.HELD));
		MercuryManager.updateLocationToMercury(pp, PrisonPearlEvent.Type.HELD);
	}

	private <ItemBlock extends InventoryHolder & BlockState> void updatePearl(
			PrisonPearl pp, ItemBlock block) {
		pp.setHolder(block);
		Bukkit.getPluginManager().callEvent(
				new PrisonPearlEvent(pp, PrisonPearlEvent.Type.HELD));
		MercuryManager.updateLocationToMercury(pp, PrisonPearlEvent.Type.HELD);
	}
	
	private ItemStack announcePearl(Player player, ItemStack item) {
		if (item == null)
			return null;

		if (item.getType() == Material.ENDER_PEARL && item.getDurability() != 0) {
			PrisonPearl pp = pearls.getPearlByItemStack(item);

			if (pp == null) {
				return new ItemStack(Material.ENDER_PEARL, 1);
			}
			pp.markMove();
		}
		return null;
	}
	
	// Announce the person in a pearl when a player holds it
	@EventHandler(priority = EventPriority.MONITOR)
	public void onItemHeldChange(PlayerItemHeldEvent event) {

		Inventory inv = event.getPlayer().getInventory();
		ItemStack item = inv.getItem(event.getNewSlot());
		ItemStack newitem = announcePearl(event.getPlayer(), item);
		if (newitem != null)
			inv.setItem(event.getNewSlot(), newitem);
	}

	// Free pearls when right clicked
	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerInteract(PlayerInteractEvent event) {

		PrisonPearl pp = pearls.getPearlByItemStack(event.getItem());
		if (pp == null)
			return;
		pp.markMove();

		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			Material m = event.getClickedBlock().getType();
			if (m == Material.CHEST || m == Material.WORKBENCH
					|| m == Material.FURNACE || m == Material.DISPENSER
					|| m == Material.BREWING_STAND)
				return;
		} else if (event.getAction() != Action.RIGHT_CLICK_AIR) {
			return;
		}

		Player player = event.getPlayer();
		player.getInventory().setItemInMainHand(null);
		event.setCancelled(true);

		freePearl(pp, pp.getImprisonedName() + " ("+pp.getImprisonedId() + ") is being freed. Reason: " 
				+ player.getDisplayName() + " threw the pearl.");
		player.sendMessage("You've freed " + pp.getImprisonedName());
	}

	// Free the pearl if it despawns
	@EventHandler(priority = EventPriority.MONITOR)
	public void onItemDespawn(ItemDespawnEvent event) {
		PrisonPearl pp = pearls.getPearlByItemStack(event.getEntity().getItemStack());
		if (pp == null)
			return;

		freePearl(pp, pp.getImprisonedName() + " ("+pp.getImprisonedId() + ") is being freed. Reason: PrisonPearl item despawned.");
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onItemDamage(EntityDamageEvent event) {
		Entity e = event.getEntity();
		if (e.getType() != EntityType.DROPPED_ITEM || !event.getEntity().isDead()) 
			return;
		ItemStack s = ((Item) e).getItemStack();
		PrisonPearl pp = pearls.getPearlByItemStack(s);
		if (pp == null)
			return;
		freePearl(pp, pp.getImprisonedName() + " ("+pp.getImprisonedId() + ") is being freed. Reason: PrisonPearl item was destroyed.");
	}
	
	public boolean freePlayer(Player player, String reason) {
		PrisonPearl pp = pearls.getByImprisoned(player);
		return pp != null && freePearl(pp, reason);
	}

	public boolean freePearl(PrisonPearl pp, String reason) {
		// set off an event
		pearls.freePearl(pp, reason);
		return true;
	}

}
