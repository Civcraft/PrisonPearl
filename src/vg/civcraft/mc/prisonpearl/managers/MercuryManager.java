package vg.civcraft.mc.prisonpearl.managers;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import vg.civcraft.mc.mercury.MercuryAPI;
import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlConfig;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.Summon;
import vg.civcraft.mc.prisonpearl.database.interfaces.IPrisonPearlStorage;
import vg.civcraft.mc.prisonpearl.events.PrisonPearlEvent;
import vg.civcraft.mc.prisonpearl.misc.FakeLocation;

public class MercuryManager {

	private final static String channel = "PrisonPearl";
	private static boolean isMercuryEnabled;
	private static IPrisonPearlStorage storage;
	
	public MercuryManager() {
		isMercuryEnabled = PrisonPearlPlugin.isMercuryEnabled();
		if (!isMercuryEnabled)
			return;
		storage = PrisonPearlPlugin.getDBHandler().getStorageHandler().getPrisonPearlStorage();
		Bukkit.getScheduler().scheduleSyncRepeatingTask(PrisonPearlPlugin.getInstance(), new Runnable() {

			@Override
			public void run() {
				updateAllPearlLocations();
			}
			
		}, 20, PrisonPearlConfig.getMercuryUpdateMessageTicks());
	}
	
	private static void updateAllPearlLocations(){
		if (!isMercuryEnabled)
			return;
    	Collection<PrisonPearl> pearls = storage.getAllPearls();
    	for (PrisonPearl pp: pearls){
    		Location loc = pp.getLocation();
    		
    		if (loc instanceof FakeLocation)
    			continue; // If it isn't your pearl don't worry about it.  The server that has it will send the messages.
    		
    		
    		String playerName = null;
    		
    		playerName = pp.getHolderName();
    		
    		String message = "move|" + pp.getImprisonedId().toString() + "|" + loc.getWorld().getName() + "|" + loc.getBlockX() + "|" + 
    			loc.getBlockY() + "|" + loc.getBlockZ() + "|" + playerName;
    		MercuryAPI.sendGlobalMessage(message, channel);
    	}
    }
	
	public static void updateTransferToMercury(UUID imprisoner, UUID pearled) {
		if (!isMercuryEnabled)
			return;
		String message = "";
		message += "transfer|" + imprisoner.toString() + "|" + pearled.toString();
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void updateLocationToMercury(PrisonPearl pp, PrisonPearlEvent.Type type){
		if (!isMercuryEnabled)
			return;
		Location loc = pp.getLocation();
		String playerName = null;
		if (pp.getHolderPlayer() != null)
			playerName = pp.getHolderPlayer().getDisplayName();
		UUID uuid = pp.getImprisonedId();
		String worldName = "";
		if (loc instanceof FakeLocation)
			worldName = ((FakeLocation) loc).getWorldName();
		else
			worldName = loc.getWorld().getName();
		String message = "update|" + type.name() + "|" + uuid.toString() + "|" + worldName + "|" + loc.getBlockX() + "|" + loc.getBlockY() + "|" + 
		loc.getBlockZ() + "|" + pp.getUniqueIdentifier() + "|" + playerName + "|" + pp.getMotd() + "|" + pp.getKillerUUID() + "|" + pp.getImprisonTime();
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void requestPPSummon(UUID uuid) {
		if (!isMercuryEnabled)
			return;
		String message = "summon|request|" + uuid.toString() + "|" + MercuryAPI.serverName();
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void denyPPSummon(UUID uuid, String reason) {
		if (!isMercuryEnabled)
			return;
		String message = "summon|deny|" + uuid.toString() + "|" + reason;
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void acceptPPSummon(UUID uuid, Location loc) {
		if (!isMercuryEnabled)
			return;
		String message = String.format("summon|accept|%s|%s|%d|%d|%d", uuid.toString(), loc.getWorld().getName(), loc.getBlockX(), 
				loc.getBlockY(), loc.getBlockZ());
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void returnPPSummon(UUID uuid) {
		if (!isMercuryEnabled)
			return;
		String message = "summon|return|" + uuid.toString();
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void addBroadcast(UUID pearled, UUID receiver) {
		if (!isMercuryEnabled)
			return;
		String message = "broadcast|add|" + pearled.toString() + "|" + receiver.toString();
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void requestBroadcast(UUID pearled, UUID receiver) {
		if (!isMercuryEnabled)
			return;
		String message = "broadcast|request|" + pearled.toString() + "|" + receiver.toString();
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void removeBroadcast(UUID pearled, UUID receiver) {
		if (!isMercuryEnabled)
			return;
		String message = "broadcast|remove|" + pearled.toString() + "|" + receiver.toString();
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void sendBroadcast(UUID pearled, List<UUID> receivers) {
		if (!isMercuryEnabled)
			return;
		StringBuilder builder = new StringBuilder();
		builder.append("broadcast|send|" + pearled.toString() + "|");
		for (int x = 0; x < receivers.size(); x++) {
			builder.append(receivers.get(x).toString());
			if (x + 1 < receivers.size())
				builder.append("|");
		}
		MercuryAPI.sendGlobalMessage(builder.toString(), channel);
	}
	
	public static boolean isPlayerOnline(UUID uuid) {
		if (!isMercuryEnabled)
			for (Player p: Bukkit.getOnlinePlayers()) {
				if (p.getUniqueId().equals(uuid))
					return true;
			}
		else
			for (UUID x: MercuryAPI.getAllAccounts()) {
				if (x.equals(uuid))
					return true;
			}
		return false;
	}
	
	public static boolean isPlayerOnline(String name) {
		if (!isMercuryEnabled)
			for (Player p: Bukkit.getOnlinePlayers()) {
				if (p.getName().equalsIgnoreCase(name))
					return true;
			}
		else
			for (String x: MercuryAPI.getAllPlayers()) {
				if (x.equalsIgnoreCase(name))
					return true;
			}
		return false;
	}
	
	public static void requestPPLocate(PrisonPearl pp) {
		if (!isMercuryEnabled)
			return;
		String message = "locate|request|" + pp.getImprisonedId().toString();
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void sendPPLocate(PrisonPearl pp) {
		if (!isMercuryEnabled)
			return;
		Location loc = pp.getLocation();
		String message = String.format("locate|send|%s|%s|%d|%d|%d|%s", pp.getImprisonedId().toString(), loc.getWorld().getName(),
				loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), pp.getHolderName());
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void updateSummonDamage(Summon s) {
		if (!isMercuryEnabled)
			return;
		String message = String.format("summon|update|%s|damage|%d", s.getUUID().toString(), s.getAmountDamage());
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void updateSummonDistance(Summon s) {
		if (!isMercuryEnabled)
			return;
		String message = String.format("summon|update|%s|distance|%d", s.getUUID().toString(), s.getMaxDistance());
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void updateSummonToggleBlocks(Summon s) {
		if (!isMercuryEnabled)
			return;
		String message = String.format("summon|update|%s|toggle|blocks|%b", s.getUUID().toString(), s.getCanBreak());
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void updateSummonToggleDamage(Summon s) {
		if (!isMercuryEnabled)
			return;
		String message = String.format("summon|update|%s|toggle|damage|%b", s.getUUID().toString(), s.getCanDamage());
		MercuryAPI.sendGlobalMessage(message, channel);
	}
	
	public static void updateSummonToggleSpeech(Summon s) {
		if (!isMercuryEnabled)
			return;
		String message = String.format("summon|update|%s|toggle|speech|%b", s.getUUID().toString(), s.getCanSpeak());
		MercuryAPI.sendGlobalMessage(message, channel);
	}
}
