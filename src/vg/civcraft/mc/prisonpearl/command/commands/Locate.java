package vg.civcraft.mc.prisonpearl.command.commands;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import vg.civcraft.mc.civmodcore.command.PlayerCommand;
import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.managers.BroadcastManager;
import vg.civcraft.mc.prisonpearl.managers.MercuryManager;
import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;
import vg.civcraft.mc.prisonpearl.misc.FakeLocation;

public class Locate extends PlayerCommand{

	private PrisonPearlManager manager;
	private static BroadcastManager broad;
	public static List<PrisonPearl> locate = new ArrayList<PrisonPearl>();
	
	public Locate(String name) {
		super(name);
		setIdentifier("pplocate");
		setDescription("Locates your pearl");
		setUsage("/ppl");
		setArguments(0, 0);
		manager = PrisonPearlPlugin.getPrisonPearlManager();
		broad = PrisonPearlPlugin.getBroadcastManager();
	}

	public boolean execute(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED
					+ "This command can only be used as a player");
			return true;
		}
		Player p = (Player) sender;
		if (!manager.isImprisoned(p)) {
			p.sendMessage(ChatColor.RED + "You are not imprisoned");
			return true;
		}
		PrisonPearl pearl = manager.getByImprisoned(p);
		if (!pearl.verifyLocation() && manager.freePearl(pearl, String.format("Prison Pearl %s could not authenticate.", pearl.getImprisonedId().toString()))) {
			return true;
		}
		if (PrisonPearlPlugin.isMercuryEnabled() && (pearl.getLastMoved() + 3000 < System.currentTimeMillis())
				&& (pearl.getLocation() instanceof FakeLocation)) { 
			// if its been more than 3 seconds since last update.
			locate.add(pearl);
			MercuryManager.requestPPLocate(pearl);
			return true;
		}
		broad.broadcast(pearl.getImprisonedId());
		p.sendMessage(ChatColor.YELLOW + "Your pearl is " + pearl.describeLocation());
		return true;
	}

	public List<String> tabComplete(CommandSender sender, String[] args) {
		return new LinkedList<String>(); // empty list
	}
	
	public static void broadCastPearl(PrisonPearl pp) {
		if (!locate.remove(pp))
			return;
		broad.broadcast(pp.getImprisonedId());
		Player p = pp.getImprisonedPlayer();
		if (p != null)
			p.sendMessage(ChatColor.YELLOW + "Your pearl is " + pp.describeLocation());
	}
}
