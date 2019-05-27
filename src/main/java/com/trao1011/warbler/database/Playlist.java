package com.trao1011.warbler.database;

import java.util.List;
import java.util.ArrayList;

public class Playlist implements Comparable<Playlist> {
	long id;
	String name;
	boolean shared;
	List<Track> tracks;
	User author;

	public Playlist(String name) {
		this.name = name;
		this.shared = false;
		this.tracks = new ArrayList<Track>();
	}

	@Override
	public int compareTo(Playlist o) {
		return name.compareTo(o.name);
	}
}
