package com.trao1011.warbler.database;

import java.util.*;

public class Artist extends MediaDatabaseEntry implements Comparable<Artist> {
	String name;
	SortedSet<Track> tracks = new TreeSet<Track>();
	SortedSet<Album> albums = new TreeSet<Album>();

	public Artist(String name) {
		this.name = name;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Artist && ((Artist)obj).name.equals(name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	public String toString() {
		return String.format("Artist[%s]", name);
	}

	@Override
	public int compareTo(Artist o) {
		return name.compareToIgnoreCase(o.name);
	}
}
