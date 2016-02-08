package com.untamedears.PrisonPearl.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

import vg.civcraft.mc.mercury.MercuryAPI;
import vg.civcraft.mc.namelayer.NameAPI;

import com.untamedears.PrisonPearl.FakeLocation;
import com.untamedears.PrisonPearl.PPConfig;
import com.untamedears.PrisonPearl.PrisonPearl;
import com.untamedears.PrisonPearl.PrisonPearlPlugin;
import com.untamedears.PrisonPearl.Summon;

public class PrisonPearlMysqlStorage {

	private Database db;
	private boolean isNameLayer;
	private boolean isMercury;

	public PrisonPearlMysqlStorage(PrisonPearlPlugin plugin) {
		PPConfig config = plugin.getPPConfig();
		isNameLayer = plugin.isNameLayerLoaded();
		isMercury = plugin.isMercuryLoaded();
		String host = config.getMysqlHost();
		String dbname = config.getMysqlDbname();
		String user = config.getMysqlUsername();
		String password = config.getMysqlPassword();
		int port = config.getMysqlPort();
		db = new Database(host, port, dbname, user, password,
				Bukkit.getLogger());
		if (db.connect()) {
			initializeTables();
			initializeStatements();
		}
	}

	public void initializeTables() {
		db.execute("create table if not exists PrisonPearls( "
				+ "uuid char(36) not null,"
				+ "world varchar(255) not null," 
				+ "x int not null,"
				+ "y int not null," 
				+ "z int not null,"
				+ "uq int not null,"
				+ "motd varchar(255) not null," 
				+ "primary key ids_id(uuid));");
		db.execute("create table if not exists PrisonPearlPortaled("
				+ "uuid char(36) not null,"
				+ "primary key uuid_key(uuid));");
		db.execute("create table if not exists PrisonPearlSummon("
				+ "uuid char(36) not null,"
				+ "world varchar(255) not null,"
				+ "x int not null,"
				+ "y int not null,"
				+ "z int not null,"
				+ "dist int,"
				+ "damage int,"
				+ "canSpeak tinyint(1),"
				+ "canDamage tinyint(1),"
				+ "canBreak tinyint(1),"
				+ "primary key uuid_key(uuid));");
		db.execute("create table if not exists FeedDelay("
				+ "lastRestart bigint not null default 0,"
				+ "server varchar(255) not null);");
		db.execute("create table if not exists ppWorldBorder("
				+ "world char(36) not null,"
				+ "x int not null,"
				+ "y int not null,"
				+ "z int not null,"
				+ "primary key world_key(world));");
	}

	private PreparedStatement addPearl, removePearl, getPearl, getAllPearls, updatePearl;
	private PreparedStatement addPortaledPlayer, removePortaledPlayer, getAllPortaledPlayers;
	private PreparedStatement addSummonedPlayer, removeSummonedPlayer, updateSummonedPlayer, getAllSummonedPlayer;
	private PreparedStatement updateLastRestart, getLastRestart, insertFirstRestart;
	private PreparedStatement addWorldBorder, removeWorldBorder, getAllWorldBorder;
	
	public void initializeStatements() {
		addPearl = db.prepareStatement("insert into PrisonPearls(uuid, world, x, y, z, uq, motd)"
						+ "values (?, ?, ?, ?, ?, ?, ?);");
		getPearl = db.prepareStatement("select * from PrisonPearls where uuid = ?;");
		getAllPearls = db.prepareStatement("select * from PrisonPearls;");
		removePearl = db.prepareStatement("delete from PrisonPearls where uuid = ?");
		updatePearl = db.prepareStatement("update PrisonPearls "
				+ "set x = ?, y = ?, z = ?, world = ?, "
				+ "motd = ? where uuid = ?;");
		
		addPortaledPlayer = db.prepareStatement("insert ignore into PrisonPearlPortaled(uuid)"
				+ "values(?);");
		removePortaledPlayer = db.prepareStatement("delete from PrisonPearlPortaled where uuid = ?;");
		getAllPortaledPlayers = db.prepareStatement("select * from PrisonPearlPortaled;");
		
		addSummonedPlayer = db.prepareStatement("insert into PrisonPearlSummon("
				+ "uuid, world, x, y, z, dist, damage, canSpeak, canDamage, canBreak)"
				+ "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
		removeSummonedPlayer = db.prepareStatement("delete from PrisonPearlSummon where uuid = ?;");
		updateSummonedPlayer = db.prepareStatement("update PrisonPearlSummon "
				+ "set world = ?, x = ?, y = ?, z = ?, dist = ?, "
				+ "damage = ?, canSpeak = ?, canDamage = ?, canBreak = ? "
				+ "where uuid = ?;");
		getAllSummonedPlayer = db.prepareStatement("select * from PrisonPearlSummon;");
		
		insertFirstRestart = db.prepareStatement("insert into FeedDelay(lastRestart, server) values(?, ?);");
		updateLastRestart = db.prepareStatement("update FeedDelay "
				+ "set lastRestart = ? where server = ?;");
		getLastRestart = db.prepareStatement("select * from FeedDelay where server = ?");
		
		addWorldBorder = db.prepareStatement("insert into ppWorldBorder(world, x, y, z) values (?,?,?,?);");
		removeWorldBorder = db.prepareStatement("delete from ppWorldBorder where world = ? and x = ? and y = ? and z = ?;");
		getAllWorldBorder = db.prepareStatement("select * from ppWorldBorder;");
	}

	public void reconnectAndReinitialize() {
		if (db.isConnected())
			return;
		db.connect();
		initializeStatements();
	}

	public void addPearl(PrisonPearl pp) {
		try {
			Location ppLocation = pp.getLocation();

			addPearl.setString(1, pp.getImprisonedId().toString());
			addPearl.setString(2, ppLocation.getWorld().getName());
			addPearl.setInt(3, ppLocation.getBlockX());
			addPearl.setInt(4, ppLocation.getBlockY());
			addPearl.setInt(5, ppLocation.getBlockZ());
			addPearl.setInt(6, pp.getUniqueIdentifier());
			addPearl.setString(7, pp.getMotd());
			addPearl.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public PrisonPearl getPearl(UUID uuid) {
		try {
			getPearl.setString(1, uuid.toString());
			ResultSet set = getPearl.executeQuery();
			if (!set.next())
				return null;
			World world = Bukkit.getWorld(set.getString("world"));
			int x = set.getInt("x"), y = set.getInt("y"), z = set.getInt("z");
			String motd = set.getString("motd");
			String name = "";
			int unique = set.getInt("uq");
			if (isNameLayer)
				name = NameAPI.getCurrentName(uuid);
			else
				name = Bukkit.getOfflinePlayer(uuid).getName();
			PrisonPearl pp = null;
			if (world == null)
				pp = new PrisonPearl(name, uuid, new FakeLocation(set.getString("world"), x, y, z), unique);
			else 
				pp = new PrisonPearl(name, uuid, new Location(world, x, y, z), unique);
			pp.setMotd(motd);
			return pp;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public List<PrisonPearl> getAllPearls() {
		List<PrisonPearl> pearls = new ArrayList<PrisonPearl>();
		try {
			ResultSet set = getAllPearls.executeQuery();
			while (set.next()) {
				World world = Bukkit.getWorld(set.getString("world"));
				int x = set.getInt("x"), y = set.getInt("y"), z = set
						.getInt("z");
				String motd = set.getString("motd");
				UUID uuid = UUID.fromString(set.getString("uuid"));
				String name = "";
				int unique = set.getInt("uq");
				if (isNameLayer)
					name = NameAPI.getCurrentName(uuid);
				else
					name = Bukkit.getOfflinePlayer(uuid).getName();
				PrisonPearl pp = null;
				if (world == null)
					pp = new PrisonPearl(name, uuid, new FakeLocation(set.getString("world"), x, y, z), unique);
				else 
					pp = new PrisonPearl(name, uuid, new Location(world, x, y, z), unique);
				pp.setMotd(motd);
				pearls.add(pp);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return pearls;
	}
	
	public void deletePearl(PrisonPearl pearl){
		try {
			removePearl.setString(1, pearl.getImprisonedId().toString());
			removePearl.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void updatePearl(PrisonPearl pp){
		Location loc = pp.getLocation();
		if (loc instanceof FakeLocation)
			return;
		try {
			updatePearl.setInt(1, loc.getBlockX());
			updatePearl.setInt(2, loc.getBlockY());
			updatePearl.setInt(3, loc.getBlockZ());
			updatePearl.setString(4, loc.getWorld().getName());
			updatePearl.setString(5, pp.getMotd());
			updatePearl.setString(6, pp.getImprisonedId().toString());
			updatePearl.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void addPortaledPlayer(UUID uuid){
		try {
			addPortaledPlayer.setString(1, uuid.toString());
			addPortaledPlayer.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void removePortaledPlayer(UUID uuid){
		try {
			removePortaledPlayer.setString(1, uuid.toString());
			removePortaledPlayer.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public List<UUID> getAllPortaledPlayers(){
		List<UUID> uuids = new ArrayList<UUID>();
		try {
			ResultSet set = getAllPortaledPlayers.executeQuery();
			while(set.next()){
				uuids.add(UUID.fromString(set.getString("uuid")));
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return uuids;
	}
	
	public void addSummonedPlayer(Summon summon){
		try {

			Location returnLocation = summon.getReturnLocation();

			addSummonedPlayer.setString(1, summon.getSummonedId().toString());
			addSummonedPlayer.setString(2, returnLocation.getWorld().toString());
			addSummonedPlayer.setInt(3, returnLocation.getBlockX());
			addSummonedPlayer.setInt(4, returnLocation.getBlockY());
			addSummonedPlayer.setInt(5, returnLocation.getBlockZ());
			addSummonedPlayer.setInt(6, summon.getAllowedDistance());
			addSummonedPlayer.setInt(7, summon.getDamageAmount());
			addSummonedPlayer.setBoolean(8, summon.isCanSpeak());
			addSummonedPlayer.setBoolean(9, summon.isCanDealDamage());
			addSummonedPlayer.setBoolean(10, summon.isCanBreakBlocks());
			addSummonedPlayer.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void removeSummonedPlayer(Summon summon){
		try {
			removeSummonedPlayer.setString(1, summon.getSummonedId().toString());
			removeSummonedPlayer.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void updateSummonedPlayer(Summon summon){
		try {
			updateSummonedPlayer.setString(1, summon.getReturnLocation().getWorld().getName());
			updateSummonedPlayer.setInt(2, summon.getReturnLocation().getBlockX());
			updateSummonedPlayer.setInt(3, summon.getReturnLocation().getBlockY());
			updateSummonedPlayer.setInt(4, summon.getReturnLocation().getBlockZ());
			updateSummonedPlayer.setInt(5, summon.getAllowedDistance());
			updateSummonedPlayer.setInt(6, summon.getDamageAmount());
			updateSummonedPlayer.setBoolean(7, summon.isCanSpeak());
			updateSummonedPlayer.setBoolean(8, summon.isCanDealDamage());
			updateSummonedPlayer.setBoolean(9, summon.isCanBreakBlocks());
			updateSummonedPlayer.setString(10, summon.getSummonedId().toString());
			updateSummonedPlayer.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Map<UUID, Summon> getAllSummonedPlayers(){
		Map<UUID, Summon> summons = new HashMap<UUID, Summon>();
		try {
			ResultSet set = getAllSummonedPlayer.executeQuery();
			while(set.next()){
				UUID id = UUID.fromString(set.getString("uuid"));
				String w = set.getString("world");
				World world = Bukkit.getWorld(w);
				if (world == null)
					continue;
				int x = set.getInt("x"), y = set.getInt("y"), z = set.getInt("z");
				int dist = set.getInt("dist");
				int damage = set.getInt("damage");
				boolean canSpeak = set.getBoolean("canSpeak");
				boolean canBreak = set.getBoolean("canBreak");
				boolean canDamage = set.getBoolean("canDamage");
				Location loc = new Location(world, x, y, z);
				Summon summon = new Summon(id, loc, dist, damage, canSpeak, canDamage, canBreak);
				summons.put(id, summon);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return summons;
	}
	
	public void updateLastRestart(long lastRestart) {
		String server = "bukkit";
		if (isMercury)
			server = MercuryAPI.serverName();
		try {
			updateLastRestart.setLong(1, lastRestart);
			updateLastRestart.setString(2, server);
			updateLastRestart.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public long getLastRestart() {
		long lastRestart = 0;
		try {
			String server = "bukkit";
			if (isMercury)
				server = MercuryAPI.serverName();
			getLastRestart.setString(1, server);
			ResultSet set = getLastRestart.executeQuery();
			if (!set.next()) {
				insertFirstRestart.setLong(1, System.currentTimeMillis());
				insertFirstRestart.setString(2, server);
				insertFirstRestart.execute();
				return getLastRestart();
			}
			lastRestart = set.getLong("lastRestart");
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return lastRestart;
	}
	
	public void addWorldBorder(Location loc) {
		try {
			addWorldBorder.setString(1, loc.getWorld().getUID().toString());
			addWorldBorder.setInt(2, loc.getBlockX());
			addWorldBorder.setInt(3, loc.getBlockY());
			addWorldBorder.setInt(4, loc.getBlockZ());
			addWorldBorder.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void removeWorldBorder(Location loc) {
		try {
			removeWorldBorder.setString(1, loc.getWorld().getUID().toString());
			removeWorldBorder.setInt(2, loc.getBlockX());
			removeWorldBorder.setInt(3, loc.getBlockY());
			removeWorldBorder.setInt(4, loc.getBlockZ());
			removeWorldBorder.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public List<Location> getAllWorldBorder() {
		List<Location> locs = new ArrayList<Location>();
		try {
			ResultSet set = getAllWorldBorder.executeQuery();
			while (set.next()) {
				UUID uuid = UUID.fromString(set.getString("world"));
				World world = Bukkit.getWorld(uuid);
				if (world == null)
					continue;
				int x = set.getInt("x");
				int y = set.getInt("y");
				int z = set.getInt("z");
				Location loc = new Location(world, x, y, z);
				locs.add(loc);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return locs;
	}
}
