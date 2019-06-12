package com.trao1011.warbler.database;

import java.io.InputStream;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.security.spec.*;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.trao1011.warbler.server.WarblerServer;

import io.vertx.core.json.*;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.*;

public class UserDatabase {
	private static final String CRYPT_SALT = "WarblerV5_UserDb";
	private static final String schemaName = "/db_schema.sql";
	private static final User initialAdmin = new User();
	private static JsonObject jdbcConfig = new JsonObject();
	private static UserDatabase udb;
	static {

		initialAdmin.accessLevel = 5;
		initialAdmin.name = "Setup";
		initialAdmin.uid = -1;
		initialAdmin.username = "warbleradmin";

		Path pathToDB = Paths.get(WarblerServer.getAppDataFolder(), "users");
		jdbcConfig.put("driver_class", "org.h2.Driver")
				.put("url", "jdbc:h2:" + pathToDB.normalize().toAbsolutePath().toString())
				.put("max_idle_time", 300);

		try {
			udb = new UserDatabase();
		} catch (SQLException | ClassNotFoundException e) {
			e.printStackTrace();
			System.err.println("Could not initialize database connection.");
			System.exit(1);
		}
	}
	public static UserDatabase getInstance() {
		return udb;
	}

	Map<Long, Playlist> playlists = new HashMap<Long, Playlist>();
	Map<Long, User> users = new HashMap<Long, User>();
	SQLClient client;

	public UserDatabase() throws SQLException, ClassNotFoundException {
		Class.forName(jdbcConfig.getString("driver_class"));
		client = JDBCClient.createShared(WarblerServer.vertx, jdbcConfig);

		// Initialize the database.
		InputStream is = Class.class.getResourceAsStream(schemaName);
		String schemaInput;
		try (Scanner sc = new Scanner(is)) {
			schemaInput = sc.useDelimiter("\\Z").next();
		}
		try (java.sql.Connection conn = DriverManager.getConnection(jdbcConfig.getString("url"))) {
			java.sql.Statement stmt = conn.createStatement();
			stmt.executeUpdate(schemaInput);
			stmt.close();
		}

		// Get all users.
		try (java.sql.Connection conn = DriverManager.getConnection(jdbcConfig.getString("url"))) {
			String sql = "SELECT `ID`, `USERNAME`, `FULLNAME`, `ACCESSLEVEL`, `PREFS` FROM USERS";
			java.sql.Statement stmt = conn.createStatement();
			java.sql.ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				User u = new User();
				u.accessLevel = rs.getInt(4);
				u.name = rs.getString(3);
				u.prefs = new JsonObject(rs.getString(5)).getMap();
				u.uid = rs.getInt(1);
				u.username = rs.getString(2);
				users.put(u.uid, u);
			}
			stmt.close();
		}

		// Get all playlists.
		try (java.sql.Connection conn = DriverManager.getConnection(jdbcConfig.getString("url"))) {
			String sql = "SELECT `ID`, `NAME`, `OWNER`, `SHARED`, `TRACKS` FROM PLAYLISTS";
			java.sql.Statement stmt = conn.createStatement();
			java.sql.ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				Playlist p = new Playlist(rs.getString(2));
				p.author = users.get(rs.getLong(3));
				p.author.playlists.add(p);
				p.id = rs.getLong(1);
				p.shared = rs.getBoolean(4);
				p.tracks = Arrays.stream(rs.getString(5).split(";"))
						.map(trackUUID -> (Track) MediaDatabase.getInstance().get(trackUUID, Track.class))
						.collect(Collectors.toList());
				playlists.put(p.id, p);
			}

			stmt.close();
		}
	}

	private static String crypt(String input) {
		KeySpec spec = new PBEKeySpec(input.toCharArray(),
				CRYPT_SALT.getBytes(),
				1 << 19, 256);
		SecretKeyFactory factory;
		try {
			factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
			return new String(Base64.getEncoder().encode(factory.generateSecret(spec).getEncoded()));
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			System.err.println("[FATAL] Cannot find PBKDF2/HMAC/SHA512 password hashing algorithm.");
			System.exit(1);
			return null;
		}
	}

	public CompletableFuture<User> login(String username, String password) {
		CompletableFuture<User> promise = new CompletableFuture<User>();
		if (users.isEmpty() && username.equals(initialAdmin.username))
			promise.complete(initialAdmin);
		else {
			String sql = "SELECT `ID` FROM USERS WHERE USERNAME = ? AND PASSWORD = ? LIMIT 1";
			JsonArray params = new JsonArray();
			params.add(username);
			params.add(crypt(password));

			client.queryWithParams(sql, params, res -> {
				if (res.succeeded()) {
					ResultSet rs = res.result();
					if (rs.getNumRows() == 1)
						promise.complete(users.get(rs.getResults().get(0).getLong(0)));
					else
						promise.complete(null);
				} else
					promise.completeExceptionally(res.cause());
			});
		}
		return promise;
	}

	CompletableFuture<User> createUser(String username, String password, int accesslevel, String fullname) {
		String sql = "INSERT INTO `USERS` (`USERNAME`, `PASSWORD`, `ACCESSLEVEL`, `FULLNAME`) "
				+ "VALUES (?, ?, ?, ?)";
		JsonArray params = new JsonArray();
		CompletableFuture<User> promise = new CompletableFuture<User>();
		params.add(username);
		params.add(crypt(password));
		params.add(accesslevel);
		params.add(fullname);
		client.updateWithParams(sql, params, res -> {
			if (res.succeeded()) {
				User u = new User();
				u.accessLevel = accesslevel;
				u.name = fullname;
				u.uid = res.result().getKeys().getLong(0);
				u.username = username;
				users.put(u.uid, u);
				promise.complete(u);
			} else
				promise.completeExceptionally(res.cause());
		});
		return promise;
	}

	CompletableFuture<User> setUserPassword(long uid, String currentPassword, String newPassword) {
		String sql = "UPDATE `USERS` SET `PASSWORD` = ? WHERE `ID` = ? AND `PASSWORD` = ?";
		JsonArray params = new JsonArray();
		CompletableFuture<User> promise = new CompletableFuture<User>();
		params.add(crypt(newPassword));
		params.add(uid);
		params.add(crypt(currentPassword));
		client.updateWithParams(sql, params, res -> {
			if (res.succeeded()) {
				if (res.result().getUpdated() > 0)
					promise.complete(users.get(uid));
				else
					promise.complete(null);
			} else
				promise.completeExceptionally(res.cause());
		});
		return promise;
	}

	CompletableFuture<User> setUserAccess(long uid, int accessLevel) {
		String sql = "UPDATE `USERS` SET `ACCESSLEVEL` = ? WHERE `ID` = ?";
		JsonArray params = new JsonArray();
		CompletableFuture<User> promise = new CompletableFuture<User>();
		params.add(accessLevel);
		params.add(uid);
		client.updateWithParams(sql, params, res -> {
			if (res.succeeded()) {
				if (res.result().getUpdated() > 0) {
					User u = users.get(uid);
					u.accessLevel = accessLevel;
					promise.complete(u);
				} else
					promise.complete(null);
			} else
				promise.completeExceptionally(res.cause());
		});
		return promise;
	}

	CompletableFuture<User> setUserName(long uid, String displayName) {
		String sql = "UPDATE `USERS` SET `FULLNAME` = ? WHERE UID = ?";
		JsonArray params = new JsonArray();
		CompletableFuture<User> promise = new CompletableFuture<User>();
		params.add(displayName);
		params.add(uid);
		client.updateWithParams(sql, params, res -> {
			if (res.succeeded()) {
				if (res.result().getUpdated() > 0) {
					User u = users.get(uid);
					u.name = displayName;
					promise.complete(u);
				} else
					promise.complete(null);
			} else
				promise.completeExceptionally(res.cause());
		});
		return promise;
	}

	CompletableFuture<User> setUserPrefs(long uid, String jsonPrefs) {
		String sql = "UPDATE `USERS` SET PREFS = ? WHERE UID = ?";
		JsonObject prefs = new JsonObject(jsonPrefs);
		JsonArray params = new JsonArray();
		CompletableFuture<User> promise = new CompletableFuture<User>();
		params.add(jsonPrefs);
		params.add(uid);
		client.updateWithParams(sql, params, res -> {
			if (res.succeeded()) {
				if (res.result().getUpdated() > 0) {
					User u = users.get(uid);
					u.prefs = prefs.getMap();
					promise.complete(u);
				} else
					promise.complete(null);
			} else
				promise.completeExceptionally(res.cause());
		});
		return promise;
	}

	CompletableFuture<Boolean> removeUser(long uid) {
		String sql = "DELETE FROM `USERS` WHERE `ID` = ?";
		JsonArray params = new JsonArray();
		CompletableFuture<Boolean> promise = new CompletableFuture<Boolean>();
		params.add(uid);
		client.updateWithParams(sql, params, res -> {
			if (res.succeeded()) {
				users.remove(uid);
				promise.complete(res.result().getUpdated() > 0);
			} else
				promise.completeExceptionally(res.cause());
		});
		return promise;
	}

	CompletableFuture<Playlist> createPlaylist(String name, User author) {
		CompletableFuture<Playlist> promise = new CompletableFuture<Playlist>();
		if (author == null) {
			promise.complete(null);
		} else {
			String sql = "INSERT INTO `PLAYLISTS` (`NAME`, `OWNER`) VALUES (?, ?)";
			JsonArray params = new JsonArray();
			params.add(name);
			params.add(author.uid);
			client.updateWithParams(sql, params, res -> {
				if (res.succeeded()) {
					Playlist pl = new Playlist(name);
					pl.author = author;
					pl.id = res.result().getKeys().getLong(0);
					pl.author.playlists.add(pl);
					playlists.put(pl.id, pl);
					promise.complete(pl);
				} else
					promise.completeExceptionally(res.cause());
			});
		}
		return promise;
	}

	CompletableFuture<Playlist> setPlaylistName(long id, String name) {
		CompletableFuture<Playlist> promise = new CompletableFuture<Playlist>();
		String sql = "UPDATE `PLAYLISTS` SET `NAME` = ? WHERE `ID` = ?";
		JsonArray params = new JsonArray();
		params.add(name);
		params.add(id);
		client.updateWithParams(sql, params, res -> {
			if (res.succeeded()) {
				if (res.result().getUpdated() > 0) {
					Playlist pl = playlists.get(id);
					pl.name = name;
					promise.complete(pl);
				} else
					promise.complete(null);
			} else
				promise.completeExceptionally(res.cause());
		});
		return promise;
	}

	CompletableFuture<Playlist> setPlaylistShared(long id, boolean shared) {
		CompletableFuture<Playlist> promise = new CompletableFuture<Playlist>();
		String sql = "UPDATE `PLAYLISTS` SET `SHARED` = ? WHERE `ID` = ?";
		JsonArray params = new JsonArray();
		params.add(shared);
		params.add(id);
		client.updateWithParams(sql, params, res -> {
			if (res.succeeded()) {
				if (res.result().getUpdated() > 0) {
					Playlist pl = playlists.get(id);
					pl.shared = shared;
					promise.complete(pl);
				} else
					promise.complete(null);
			} else
				promise.completeExceptionally(res.cause());
		});
		return promise;
	}

	CompletableFuture<Playlist> setPlaylistTracks(long id, String tracks) {
		CompletableFuture<Playlist> promise = new CompletableFuture<Playlist>();
		String sql = "UPDATE `PLAYLISTS` SET `TRACKS` = ? WHERE `ID` = ?";
		JsonArray params = new JsonArray();
		params.add(tracks);
		params.add(id);
		client.updateWithParams(sql, params, res -> {
			if (res.succeeded()) {
				if (res.result().getUpdated() > 0) {
					Playlist pl = playlists.get(id);
					pl.tracks = Arrays.stream(tracks.split(";"))
							.filter(s -> s.length() > 0)
							.map(s -> (Track) MediaDatabase.getInstance().get(s, Track.class))
							.filter(t -> t != null).collect(Collectors.toList());
					promise.complete(pl);
				} else
					promise.complete(null);
			} else
				promise.completeExceptionally(res.cause());
		});
		return promise;
	}

	CompletableFuture<Boolean> removePlaylist(long id) {
		CompletableFuture<Boolean> promise = new CompletableFuture<Boolean>();
		String sql = "DELETE FROM `PLAYLISTS` WHERE `ID` = ?";
		JsonArray params = new JsonArray();
		params.add(id);
		client.updateWithParams(sql, params, res -> {
			if (res.succeeded()) {
				if (res.result().getUpdated() > 0)
					playlists.remove(id);
				promise.complete(res.result().getUpdated() > 0);
			} else
				promise.completeExceptionally(res.cause());
		});
		return promise;
	}
}
