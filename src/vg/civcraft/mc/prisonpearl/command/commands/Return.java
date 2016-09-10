package vg.civcraft.mc.prisonpearl.command.commands;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import vg.civcraft.mc.civmodcore.command.PlayerCommand;
import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.Summon;
import vg.civcraft.mc.prisonpearl.managers.PrisonPearlManager;
import vg.civcraft.mc.prisonpearl.managers.SummonManager;

public class Return extends PlayerCommand{

	private PrisonPearlManager pearls;
	private SummonManager summon;
	
	public Return(String name) {
		super(name);
		setIdentifier("ppreturn");
		setDescription("Returns the player that you are holding to the prison world.");
		setUsage("/ppreturn");
		setArguments(0, 0);
		pearls = PrisonPearlPlugin.getPrisonPearlManager();
		summon = PrisonPearlPlugin.getSummonManager();
	}

	@Override
	public boolean execute(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("You must be a player to execute this very fine command.");
			return true;
		}
		
		Player p = (Player) sender;
		ItemStack stack = p.getInventory().getItemInMainHand();
		PrisonPearl pearl = pearls.getPearlByItemStack(stack);
		if (pearl == null) {
			p.sendMessage(ChatColor.RED + "You are not holding a PrisonPearl.");
			return true;
		}
		if (!summon.isSummoned(pearl)) {
			p.sendMessage(ChatColor.RED + "That player is not summoned.");
			return true;
		}
		if (PrisonPearlPlugin.getCombatTagManager().isCombatTagged(pearl.getImprisonedPlayer())) {
			p.sendMessage(ChatColor.RED + "The player is combat tagged and cannot be returned.");
			return true;
		}
		Summon s = summon.getSummon(pearl);
		if (s.getLastSummonedReturned() + 1000 * 5 > System.currentTimeMillis()) {
			p.sendMessage(ChatColor.RED + "Please wait the player was just recently summoned.");
			return true;
		}
		if (!summon.returnPlayer(pearl)) { // There was an issue returning the player.
			p.sendMessage(ChatColor.RED + "Attempting to return the player.");
			// This can be caused by a player failing to be returned. 
			// Looks at bettershardslistener playerEnsuredToTransit and playerFailedToTransit
			// methods as they will provide the next stage of code.
			return true;
		}
		p.sendMessage(ChatColor.GREEN + "Player has been successfully returned.");
		return true;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String[] args) {
		// TODO Auto-generated method stub
		return null;
	}

}
