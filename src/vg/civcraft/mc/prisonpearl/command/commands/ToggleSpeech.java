package vg.civcraft.mc.prisonpearl.command.commands;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import vg.civcraft.mc.civmodcore.command.PlayerCommand;
import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.Summon;
import vg.civcraft.mc.prisonpearl.managers.MercuryManager;
import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;
import vg.civcraft.mc.prisonpearl.managers.SummonManager;

public class ToggleSpeech extends PlayerCommand {
	public ToggleSpeech(String name) {
		super(name);
		setIdentifier("pptogglespeech");
		setDescription("Toggles whether the player imprisoned in the pearl you are holding is allowed to speak");
		setUsage("/ppts [true|false]");
		setArguments(0, 1);
	}

	public boolean execute(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED
					+ "This command can only be used as a player");
			return true;
		}
		Player p = (Player) sender;
		PrisonPearlManager manager = PrisonPearlPlugin.getPrisonPearlManager();
		SummonManager summonManager = PrisonPearlPlugin.getSummonManager();
		ItemStack is = p.getInventory().getItemInMainHand();
		PrisonPearl pearl = null;
		if (is != null) {
			pearl = manager.getPearlByItemStack(is);
		}
		if (pearl == null) {
			p.sendMessage(ChatColor.RED
					+ "You are not holding a valid ender pearl");
			return true;
		}
		Player imprisoned = pearl.getImprisonedPlayer();
		if (imprisoned == null) {
			p.sendMessage(ChatColor.RED
					+ "The player held in this pearl is not online");
			return true;
		}
		Summon summon = summonManager.getSummon(pearl);
		if (summon == null) {
			p.sendMessage(ChatColor.RED + "This player is not summoned");
			return true;
		}
		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("true")
					|| args[0].equalsIgnoreCase("false")) {
				boolean toggle = Boolean.getBoolean(args[0]);
				if (summon.getCanSpeak() == toggle) {
					p.sendMessage(ChatColor.RED
							+ "Speaking is already set to "
							+ String.valueOf(toggle) + " for "
							+ pearl.getImprisonedName());
					MercuryManager.updateSummonToggleSpeech(summon);
					return true;
				}
				summon.setCanSpeak(toggle);
				p.sendMessage(ChatColor.GREEN + "Speaking was set to "
						+ String.valueOf(toggle) + " for "
						+ pearl.getImprisonedName());
				MercuryManager.updateSummonToggleSpeech(summon);
				return true;
			} else {
				p.sendMessage(ChatColor.RED
						+ "You may only specify true or false as arguments for this commands");
				return true;
			}
		}
		boolean currentState = summon.getCanSpeak();
		summon.setCanSpeak(!currentState);
		MercuryManager.updateSummonToggleSpeech(summon);
		p.sendMessage(ChatColor.GREEN + "Speaking was set to "
				+ String.valueOf(!currentState) + " for "
				+ pearl.getImprisonedName());
		return true;
	}

	public List<String> tabComplete(CommandSender sender, String[] args) {
		return new LinkedList<String>(); // empty list
	}
}
