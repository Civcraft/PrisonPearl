package vg.civcraft.mc.prisonpearl.database.mysql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import com.google.common.collect.Maps;

import vg.civcraft.mc.prisonpearl.PrisonPearlConfig;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.Summon;
import vg.civcraft.mc.prisonpearl.database.interfaces.ISummonStorage;
import vg.civcraft.mc.prisonpearl.misc.FakeLocation;

public class SummonMysqlStorage implements ISummonStorage{

	private Database db;
	private MysqlDatabaseHandler handle;
	
	public SummonMysqlStorage(Database db, MysqlDatabaseHandler handle) {
		this.db = db;
		this.handle = handle;
		createTables();
		initializeStatements();
	}
	
	private void createTables() {
		db.execute("create table if not exists PrisonPearlSummon("
				+ "uuid varchar(36) not null,"
				+ "world varchar(36) not null,"
				+ "x int not null,"
				+ "y int not null,"
				+ "z int not null,"
				+ "dist int,"
				+ "damage int,"
				+ "canSpeak tinyint(1),"
				+ "canDamage tinyint(1),"
				+ "canBreak tinyint(1),"
				+ "primary key uuid_key(uuid));");
	}
	
	private String addSummonedPlayer, removeSummonedPlayer, updateSummonedPlayer, getAllSummonedPlayer,
		getSummon;
	
	private void initializeStatements() {
		addSummonedPlayer = "insert into PrisonPearlSummon("
				+ "uuid, world, x, y, z, dist, damage, canSpeak, canDamage, canBreak)"
				+ "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
		removeSummonedPlayer = "delete from PrisonPearlSummon where uuid = ?;";
		updateSummonedPlayer = "update PrisonPearlSummon "
				+ "set world = ?, x = ?, y = ?, z = ?, dist = ?, "
				+ "damage = ?, canSpeak = ?, canDamage = ?, canBreak = ? "
				+ "where uuid = ?;";
		getAllSummonedPlayer = "select * from PrisonPearlSummon;";
		getSummon = "select * from PrisonPearlSummon where uuid = ?;";
	}
	
	private Map<UUID, Summon> summons = Maps.newHashMap();

	@Override
	public void addSummon(Summon summon) {
		handle.refreshAndReconnect();
		summons.put(summon.getUUID(), summon);
		if (summon.getReturnLocation() instanceof FakeLocation)
			return;
		PreparedStatement addSummonedPlayer = db.prepareStatement(this.addSummonedPlayer);
		Location loc = summon.getReturnLocation();
		try {
			addSummonedPlayer.setString(1, summon.getUUID().toString());
			addSummonedPlayer.setString(2, loc.getWorld().getUID().toString());
			addSummonedPlayer.setInt(3, loc.getBlockX());
			addSummonedPlayer.setInt(4, loc.getBlockY());
			addSummonedPlayer.setInt(5, loc.getBlockZ());
			addSummonedPlayer.setInt(6, summon.getMaxDistance());
			addSummonedPlayer.setInt(7, summon.getAmountDamage());
			addSummonedPlayer.setBoolean(8, summon.getCanSpeak());
			addSummonedPlayer.setBoolean(9, summon.getCanDamage());
			addSummonedPlayer.setBoolean(10, summon.getCanBreak());
			addSummonedPlayer.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				addSummonedPlayer.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void removeSummon(Summon summon) {
		removeSummon(summon.getUUID());
	}
	
	@Override
	public void removeSummon(UUID uuid) {
		handle.refreshAndReconnect();
		summons.remove(uuid);
		PreparedStatement removeSummonedPlayer = db.prepareStatement(this.removeSummonedPlayer);
		try {
			removeSummonedPlayer.setString(1, uuid.toString());
			removeSummonedPlayer.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				removeSummonedPlayer.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public Summon getSummon(UUID uuid) {
		return summons.get(uuid);
	}

	@Override
	public boolean isSummoned(UUID uuid) {
		return getSummon(uuid) != null;
	}

	@Override
	public void updateSummon(Summon summon) {
		handle.refreshAndReconnect();
		PreparedStatement updateSummonedPlayer = db.prepareStatement(this.updateSummonedPlayer);
		String world = "";
		Location loc = summon.getReturnLocation();
		if (loc instanceof FakeLocation) 
			world = ((FakeLocation) loc).getWorldName();
		else
			world = loc.getWorld().getUID().toString();
		try {
			updateSummonedPlayer.setString(1, world);
			updateSummonedPlayer.setInt(2, loc.getBlockX());
			updateSummonedPlayer.setInt(3, loc.getBlockY());
			updateSummonedPlayer.setInt(4, loc.getBlockZ());
			updateSummonedPlayer.setInt(5, summon.getMaxDistance());
			updateSummonedPlayer.setInt(6, summon.getAmountDamage());
			updateSummonedPlayer.setBoolean(7, summon.getCanSpeak());
			updateSummonedPlayer.setBoolean(8, summon.getCanDamage());
			updateSummonedPlayer.setBoolean(9, summon.getCanBreak());
			updateSummonedPlayer.setString(10, summon.getUUID().toString());
			updateSummonedPlayer.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				updateSummonedPlayer.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void save() {
		for (Summon s: summons.values())
			updateSummon(s);
	}

	@Override
	public void load() {
		handle.refreshAndReconnect();
		PreparedStatement getAllSummonedPlayer = db.prepareStatement(this.getAllSummonedPlayer);
		try {
			ResultSet set = getAllSummonedPlayer.executeQuery();
			while(set.next()) {
				Summon summon;
				UUID worldUUID = UUID.fromString(set.getString("world"));
				World w = Bukkit.getWorld(worldUUID);
				Location loc = null;
				UUID uuid = UUID.fromString(set.getString("uuid"));
				int x = set.getInt("x");
				int y = set.getInt("y");
				int z = set.getInt("z");
				if (w == null)
					loc = new FakeLocation(worldUUID.toString(), x, y, z, PrisonPearlConfig.getImprisonServerName());
				else 
					loc = new Location(w, x, y, z);
				int dist = set.getInt("dist");
				int damage = set.getInt("damage");
				boolean canSpeak = set.getBoolean("canSpeak");
				boolean canDamage = set.getBoolean("canDamage");
				boolean canBreak = set.getBoolean("canBreak");
				summon = new Summon(uuid, loc, null);
				summon.setMaxDistance(dist);
				summon.setAmountDamage(damage);
				summon.setCanSpeak(canSpeak);
				summon.setCanDamage(canDamage);
				summon.setCanBreak(canBreak);
				summons.put(uuid, summon);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				getAllSummonedPlayer.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public Map<UUID, Summon> getAllSummons() {
		return summons;
	}
}
