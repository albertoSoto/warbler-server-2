package com.trao1011.warbler.database;

import java.util.*;

public class User {
	int accessLevel;
	long uid;
	String username, name;
	Set<Playlist> playlists;
	Map<String, Object> prefs;

	public User() {
		prefs = new HashMap<String, Object>();
		playlists = new TreeSet<Playlist>();
	}

	public String displayName() {
		return name == null ? username : name;
	}

	public int getAccessLevel() {
		return accessLevel;
	}
}
