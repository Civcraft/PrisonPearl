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
import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;

public class Kill extends PlayerCommand {
	public Kill(String name) {
		super(name);
		setIdentifier("ppkill");
		setDescription("Kills the player who is imprisoned in the pearl you are holding");
		setUsage("/ppkill");
		setArguments(0, 0);
	}

	public boolean execute(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED
					+ "This command can only be used as a player");
			return true;
		}
		Player p = (Player) sender;
		PrisonPearlManager manager = PrisonPearlPlugin.getPrisonPearlManager();
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
		imprisoned.setHealth(0.0); // Let's kill the player.
		imprisoned.sendMessage(ChatColor.YELLOW
				+ "You were struck down by your imprisoner");
		p.sendMessage(ChatColor.GREEN + "You killed "
				+ pearl.getImprisonedName());
		return true;
	}

	public List<String> tabComplete(CommandSender sender, String[] args) {
		return new LinkedList<String>(); // empty list
	}
}
