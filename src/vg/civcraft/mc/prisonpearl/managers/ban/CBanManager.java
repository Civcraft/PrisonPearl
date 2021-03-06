package vg.civcraft.mc.prisonpearl.managers.ban;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import vg.civcraft.mc.cbanman.CBanManagement;
import vg.civcraft.mc.cbanman.ban.Ban;
import vg.civcraft.mc.cbanman.ban.BanLevel;
import vg.civcraft.mc.cbanman.ban.CBanList;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.managers.BanManager;

/**
 * Integrates with CBanManagement's ban methodology.
 * 
 * @author ProgrammerDan <programmerdan@gmail.com>
 *
 */
public final class CBanManager extends BanManager {
	private PrisonPearlPlugin plugin_ = null;
	private CBanManagement cban = null;
	
	private String banMessage_ = FBanManager.BAN_MSG;
	
	public CBanManager(PrisonPearlPlugin plugin) {
		this.plugin_ = plugin;
	}

	@Override
	public void enable() {
		// refresh ban understanding from CBan, if needed.
		if (PrisonPearlPlugin.isCBanManagementEnabled()) {
			cban = CBanManagement.getInstance();
		} else {
			PrisonPearlPlugin.log("Failed to activate CBanManagement!");
		}
	}

	@Override
	public void setBanMessage(String msg) {
		this.banMessage_ = msg;		
	}

	@Override
	public String getBanMessage() {
		return this.banMessage_;
	}
	
	@Override
	public boolean isBanned(UUID uuidName) {
		return cban.isBanned(uuidName);
	}

	@Override
	public Set<UUID> listBannedUUIDS() {
		// TODO: Replace with cban.getBannedPlayersByPlugin() once written.
		TreeSet<UUID> banList = new TreeSet<UUID>();
		Map<UUID, CBanList> allBans = cban.getBannedPlayers();
		for (Map.Entry<UUID, CBanList> ban : allBans.entrySet()) {
			List<Ban> bans = ban.getValue().getList();
			for (Ban singleban : bans) {
				if (singleban.isPluginName(plugin_.getName())) {
					banList.add(ban.getKey());
				}
			}
		}
		return banList;
	}

	@Override
	public void ban(UUID UUIDName) {
		Ban leBan = new Ban(BanLevel.HIGH, plugin_.getName(), banMessage_);
		cban.banPlayer(UUIDName, leBan);
	}

	@Override
	public void pardon(UUID UUIDName) {
		cban.unbanPlayer(UUIDName, plugin_.getName());
	}

	@Override
	public void setBanState(UUID UUIDName, boolean isBanned) {
		if (isBanned(UUIDName) != isBanned) {
			if (isBanned) {
				ban(UUIDName);
			} else {
				pardon(UUIDName);
			}
		}
	}

	@Override
	public boolean hasBannedPlayers() {
		return listBannedUUIDS().size() > 0;
	}
}
