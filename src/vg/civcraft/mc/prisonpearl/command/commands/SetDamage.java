package vg.civcraft.mc.prisonpearl.command.commands;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import vg.civcraft.mc.civmodcore.command.PlayerCommand;
import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.Summon;
import vg.civcraft.mc.prisonpearl.managers.MercuryManager;
import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;
import vg.civcraft.mc.prisonpearl.managers.SummonManager;

public class SetDamage extends PlayerCommand{

	private PrisonPearlManager pearls;
	private SummonManager summon;
	
	public SetDamage(String name) {
		super(name);
		setIdentifier("ppsetdamage");
		setDescription("Sets the damage that a summoned player will take when out of range.");
		setUsage("/ppsetdamage <damage amount>");
		setArguments(1, 1);
		pearls = PrisonPearlPlugin.getPrisonPearlManager();
		summon = PrisonPearlPlugin.getSummonManager();
	}

	@Override
	public boolean execute(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("You must be a player to execute this command, my b.");
			return true;
		}
		Player p = (Player) sender;
		int damage = 0;
		try {
			damage = Integer.parseInt(args[0]);
		} catch(NumberFormatException e) {
			p.sendMessage(ChatColor.RED + "That is not a valid number.");
			return true;
		}
		PrisonPearl pearl = pearls.getPearlByItemStack(p.getInventory().getItemInMainHand());
		if (pearl == null) {
			p.sendMessage(ChatColor.RED + "Your are not holding a Prison Pearl.");
			return true;
		}
		if (!summon.isSummoned(pearl)) {
			p.sendMessage(ChatColor.RED + "That player is not summoned.");
			return true;
		}
		Summon s = summon.getSummon(pearl);
		s.setAmountDamage(damage);
		MercuryManager.updateSummonDamage(s);
		p.sendMessage(ChatColor.GREEN + "You have succesfully set the damage to " + damage + ".");
		return true;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String[] args) {
		return new LinkedList<String>();
	}

}
