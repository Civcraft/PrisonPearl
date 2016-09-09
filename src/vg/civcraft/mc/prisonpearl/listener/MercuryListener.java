package vg.civcraft.mc.prisonpearl.listener;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import vg.civcraft.mc.mercury.MercuryAPI;
import vg.civcraft.mc.mercury.events.AsyncPluginBroadcastMessageEvent;
import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.Summon;
import vg.civcraft.mc.prisonpearl.command.commands.Locate;
import vg.civcraft.mc.prisonpearl.events.PrisonPearlEvent;
import vg.civcraft.mc.prisonpearl.managers.BroadcastManager;
import vg.civcraft.mc.prisonpearl.managers.MercuryManager;
import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;
import vg.civcraft.mc.prisonpearl.managers.SummonManager;
import vg.civcraft.mc.prisonpearl.misc.FakeLocation;

public class MercuryListener implements Listener{
	
	private final String channel = "PrisonPearl";
	private PrisonPearlManager pearls;
	private SummonManager sm;
	private BroadcastManager bm;
	
	public MercuryListener() {
		if (!PrisonPearlPlugin.isMercuryEnabled())
			return;
		Bukkit.getPluginManager().registerEvents(this, PrisonPearlPlugin.getInstance());
		pearls = PrisonPearlPlugin.getPrisonPearlManager();
		sm = PrisonPearlPlugin.getSummonManager();
		bm = PrisonPearlPlugin.getBroadcastManager();
		MercuryAPI.registerPluginMessageChannel(channel);
	}
	
	@EventHandler()
	public void mercuryListener(AsyncPluginBroadcastMessageEvent event) {
		String channel = event.getChannel();
		String message = event.getMessage();
		String[] parts = message.split("\\|");
		if (!this.channel.equals(channel))
			return;
		if("update".equalsIgnoreCase(parts[0]))
			pearlUpdate(parts, event.getOriginServer());
		else if("transfer".equalsIgnoreCase(parts[0]))
			pearlTransfer(parts);
		else if("move".equalsIgnoreCase(parts[0]))
			prisonPearlMove(parts, event.getOriginServer());
		else if("summon".equalsIgnoreCase(parts[0]))
			prisonPearlSummon(parts, event.getOriginServer());
		else if("broadcast".equals(parts[0]))
			prisonPearlBroadcast(parts, event.getOriginServer());
		else if("locate".equals(parts[0]))
			prisonPearlLocate(parts, event.getOriginServer());
	}
	
	private void pearlTransfer(String[] message){
		
		UUID holder = UUID.fromString(message[1]);
		UUID pearl = UUID.fromString(message[2]);
		PrisonPearl pp = pearls.getByImprisoned(pearl);
		Player p = Bukkit.getPlayer(holder);
		if (p == null) // Player is not on the server.
			return; // Some other server will pick it up.
		pp.setHolder(p);
	}
	
	private void pearlUpdate(String[] message, String server) {
		PrisonPearlEvent.Type type = PrisonPearlEvent.Type.valueOf(message[1]);
		UUID id = UUID.fromString(message[2]);
		String name = message[8];
		FakeLocation loc;
		loc = new FakeLocation(message[3], Double.parseDouble(message[4]), Double.parseDouble(message[5]),
				Double.parseDouble(message[6]), server, name);
		int unique = Integer.parseInt(message[7]);
		String motd;
		if (message [9].equals("null")) {
			motd = null;
		}
		else {
			motd = message [9];
		}
		UUID killerUUID;
		if (message [10].equals("null")) {
			killerUUID = null;
		}
		else {
			killerUUID = UUID.fromString(message [10]);
		}
		long imprisonTime = Long.parseLong(message [11]);
		if (type.equals(PrisonPearlEvent.Type.NEW)) {
			PrisonPearl pp = new PrisonPearl(name, id, loc, unique, killerUUID, imprisonTime);
			pp.setMotd(motd);
			pp.setHolder(loc);
			pp.markMove();
			pearls.addPearl(pp);
			// We are also going to check if the player is here, if they are kill them.
			Player p;
			if ((p = Bukkit.getPlayer(id)) != null) {
				p.setHealth(0.0);
				p.sendMessage(ChatColor.RED + "You have been imprisoned by " + loc.getPlayer());
			}
			return;
		}
		else if (type.equals(PrisonPearlEvent.Type.DROPPED) ||
				type.equals(PrisonPearlEvent.Type.HELD)) {
			PrisonPearl pp = pearls.getByImprisoned(id);
			pp.setHolder(loc);
			pp.markMove();
		}
		else if (type.equals(PrisonPearlEvent.Type.FREED)) {
			PrisonPearl pp = pearls.getByImprisoned(id);
			if (pp == null)
				return;
			pp.setHolder(loc); // So the player is correctly teleported to the right place.
			pearls.freePearlFromMercury(pp, "This pearl was freed on another server. Removing instance.", server);
			BroadcastManager broad = PrisonPearlPlugin.getBroadcastManager();
			List<UUID> uuids = broad.getAllBroadcasters(id);
			if (uuids == null)
				return;
			for (UUID receiever: uuids) {
				Player p;
				if ((p = Bukkit.getPlayer(receiever)) == null)
					continue;
				p.sendMessage(ChatColor.GREEN + pp.getImprisonedName() + " was freed!");
			}
			broad.removeAllBroadcasts(pp.getImprisonedId());
		}
	}
	
	private void prisonPearlMove(String[] message, String server) {
		UUID uuid = UUID.fromString(message[1]);
		String world = message[2];
		String player = message[6];
		if (player.equals("null"))
			player = null;
		FakeLocation loc = new FakeLocation(world, Double.parseDouble(message[3]), Double.parseDouble(message[4]),
				Double.parseDouble(message[5]), server, player);
		PrisonPearl pp = pearls.getByImprisoned(uuid);
		if (pp != null) {
			pp.setHolder(loc);
			pp.markMove();
		}
		else {
			PrisonPearlPlugin.getInstance().warning("Tried to update location of nonexistent pearl " + uuid + " " + world + " " + player);
		}
	}
	
	private void prisonPearlSummon(String[] message, String server) {
		/*
		 * Formats:
		 * <return> <UUID of pearled>. Alerts the server that this player should no longer be summoned.
		 * <request> <UUID of pearled> <server to send them>. Alerts the server that this player was requested to be summoned.
		 * <deny> <UUID of pearled> <reason (this is the message to return to player who summoned)>.
		 * Denies server request of returning summoned pearl.
		 */
		String reason = message[1];
		UUID uuid = UUID.fromString(message[2]);
		Player p = Bukkit.getPlayer(uuid);
		
		if (reason.equals("return")) {
			PrisonPearl pp = pearls.getByImprisoned(uuid);
			if (sm.isSummoned(pp) && p != null){
				sm.returnPlayer(pp);
			} 
			else
				sm.removeSummon(pp);
		} 
		else if (reason.equals("request")) {
			String toServer = message[3];
			if (p == null)
				return;
			if (PrisonPearlPlugin.getCombatTagManager().isCombatTagged(p) || p.isDead()) {
				String returnMessage = String.format("%s cannot be summoned.", p.getName());
				MercuryManager.denyPPSummon(uuid, returnMessage);
				return;
			}
			
			PrisonPearl pp = pearls.getByImprisoned(uuid);
			// Job of the shard holding the player in the prison world to add to mysql.
			if (sm.summonPlayer(pearls.getByImprisoned(p))) {
				// SummonPlayer will trigger a respawnPlayerCorrectly that will teleport the player to the pearl cords.
				MercuryManager.acceptPPSummon(uuid, p.getLocation());
			}
		}
		else if (reason.equals("deny")) {
			PrisonPearl pp = pearls.getByImprisoned(uuid);
			p = pp.getHolderPlayer();
			if (p != null)
				p.sendMessage(ChatColor.RED + message[3]);
		}
		else if (reason.equals("accept")) {
			PrisonPearl pp = pearls.getByImprisoned(uuid);
			String world = message[3];
			int x = Integer.parseInt(message[4]);
			int y = Integer.parseInt(message[5]);
			int z = Integer.parseInt(message[6]);
			Location returnLoc = new FakeLocation(world, x, y, z, server);
			Summon s = new Summon(uuid, returnLoc, pp);
			sm.addSummonPlayer(s);
			Player holder = pp.getHolderPlayer();
			if (holder != null)
				holder.sendMessage(ChatColor.GREEN + "The player has been summoned.");
		}
		else if (reason.equals("update")) {
			String type = message[3];
			if (type.equals("damage")) {
				int damage = Integer.parseInt(message[4]);
				Summon s = sm.getSummon(uuid);
				if (s == null) { // Summon player does not exist.
					return;
				}
				s.setAmountDamage(damage);
			}
			else if (type.equals("distance")) {
				int distance = Integer.parseInt(message[4]);
				Summon s = sm.getSummon(uuid);
				if (s == null) { // Summon player does not exist.
					return;
				}
				s.setMaxDistance(distance);
			}
			else if (type.equals("toggle")) {
				String sub = message[4];
				Summon s = sm.getSummon(uuid);
				if (s == null) { // Summon player does not exist.
					return;
				}
				
				boolean value = Boolean.getBoolean(message[5]);
				switch(sub) {
				case "blocks":
					s.setCanBreak(value);
					break;
				case "damage":
					s.setCanDamage(value);
					break;
				case "speech":
					s.setCanSpeak(value);
					break;
				}
			}
		}
	}
	
	private void prisonPearlBroadcast(String[] parts, String originServer) {
		String type = parts[1];
		if (type.equals("add")) {
			UUID pearled = UUID.fromString(parts[2]);
			UUID receiver = UUID.fromString(parts[3]);
			bm.addBroadcast(pearled, receiver);
		}
		else if (type.equals("remove")) {
			UUID pearled = UUID.fromString(parts[2]);
			UUID receiver = UUID.fromString(parts[3]);
			bm.removeBroadcasts(pearled, receiver);
		}
		else if (type.equals("request")) {
			UUID pearled = UUID.fromString(parts[2]);
			UUID receiver = UUID.fromString(parts[3]);
			bm.requestBroadcastFromMercury(pearled, receiver);
		}
		else if (type.equals("send")) {
			PrisonPearl pp = pearls.getByImprisoned(UUID.fromString(parts[2]));
			for (int x = 3; x < parts.length; x++) {
				Player p = Bukkit.getPlayer(UUID.fromString(parts[x]));
				if (p == null)
					continue;
				bm.broadcastMessage(p, pp);
			}
		}
	}
	
	private void prisonPearlLocate(String[] parts, String originServer) {
		String type = parts[1];
		UUID uuid = UUID.fromString(parts[2]);
		if (type.equals("request")) {
			PrisonPearl pp = pearls.getByImprisoned(uuid);
			if (pp == null)
				return;
			if (pp.getLocation() instanceof FakeLocation)
				return;
			MercuryManager.sendPPLocate(pp);
		}
		else if (type.equals("send")) {
			FakeLocation loc = new FakeLocation(parts[3], Double.parseDouble(parts[4]), Double.parseDouble(parts[5]),
					Double.parseDouble(parts[6]), originServer, parts[7]);
			PrisonPearl pp = pearls.getByImprisoned(uuid);
			pp.setHolder(loc);
			pp.markMove();
			Locate.broadCastPearl(pp); // Sends the pp message to players who may be waiting for it.
		}
	}

}
