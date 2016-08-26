package vg.civcraft.mc.prisonpearl.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.managers.SummonManager;

public class SummonListener implements Listener{

	private SummonManager summon = PrisonPearlPlugin.getSummonManager();
	
	public SummonListener() {
		Bukkit.getPluginManager().registerEvents(this, PrisonPearlPlugin.getInstance());
	}
	
	@EventHandler(priority=EventPriority.LOWEST)
	public void onPlayerDeathEvent(PlayerRespawnEvent event) {
		Player p = event.getPlayer();
		if (summon.isSummoned(p)) {
			event.getPlayer().sendMessage(ChatColor.GREEN + "You are being returned to your prison.");
			// The code for respawning the player can be found in the PlayerListener.
			// If it had it here it would be too difficult for the two to know which to do.
		}
	}
	
	@EventHandler(priority=EventPriority.NORMAL)
    public void onPlayerChatEvent(AsyncPlayerChatEvent event) {
        if (summon.isSummoned(event.getPlayer()) && !summon.getSummon(event.getPlayer()).getCanSpeak()) {
           event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.NORMAL)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player)event.getDamager();

        if(summon.isSummoned(player) && !summon.getSummon(player).getCanDamage()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.NORMAL)
    public void onBlockBreakEvent(BlockBreakEvent event) {

        Player player = event.getPlayer();

        if(summon.isSummoned(player) && !summon.getSummon(player).getCanBreak()) {
            event.setCancelled(true);
        }
    }
}
