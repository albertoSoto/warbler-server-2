package com.trao1011.warbler.database;

import java.util.List;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class Playlist extends MediaDatabaseEntry implements Comparable<Playlist> {
	long id;
	String name;
	boolean shared;
	List<Track> tracks;
	User author;

	public Playlist(String name, User author) {
		this(name, author, "p" + DataUtilities.SHA256(name + author.username + new SimpleDateFormat().format(new Date())).substring(4, 4 + 32));
	}
	
	public Playlist(String name, User author, String uuid) {
		this.name = name;
		this.author = author;
		this.shared = false;
		this.tracks = new ArrayList<Track>();
		this.uuid = uuid;
	}

	@Override
	public int compareTo(Playlist o) {
		return name.compareTo(o.name);
	}

	@Override
	public String getSearchValue() {
		return name;
	}
}
