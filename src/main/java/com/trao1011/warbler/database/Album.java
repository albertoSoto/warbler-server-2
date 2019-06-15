package com.trao1011.warbler.database;

import java.nio.file.Path;
import java.util.*;

public class Album extends MediaDatabaseEntry implements Comparable<Album> {
	String title, artistRepr;
	Path coverart;
	int[] date = new int[3];
	Set<Artist> albumArtists = new LinkedHashSet<Artist>();
	SortedSet<Track> tracks = new TreeSet<Track>();
	
	public Path getCoverArt() {
		return coverart;
	}

	void setDate(String date) {
		if (date == null)
			return;

		String[] components = date.split("-");
		try {
			for (int i = 0; i < 3; i++)
				this.date[i] = Integer.parseInt(components[i]);
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			for (int i = 0; i < 3; i++)
				this.date[i] = 0;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Album) {
			Album other = (Album) o;
			return title.equals(other.title) && artistRepr.equals(other.artistRepr);
		} else
			return false;
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}

	@Override
	public String toString() {
		return String.format("Album[%s by %s]", title, artistRepr);
	}

	@Override
	public int compareTo(Album o) {
		if (artistRepr.compareTo(o.artistRepr) != 0)
			return artistRepr.compareTo(o.artistRepr);

		for (int i = 0; i < 3; i++) {
			if (date[i] != o.date[i])
				return date[i] - o.date[i];
		}

		return title.compareToIgnoreCase(o.title);
	}
}
