package com.trao1011.warbler.database;

import java.nio.file.Path;
import java.util.*;

public class Track extends MediaDatabaseEntry implements Comparable<Track> {
	Path location;
	String format, title, artistRepr;
	int track, disc, duration, bitrate;
	Album album;
	List<Artist> artists;
	
	public Track(Path location) {
		this.location = location;
		this.artists = new ArrayList<Artist>();
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof Track && ((Track) obj).location.equals(location);
	}

	@Override
	public int hashCode() {
		return location.hashCode();
	}

	@Override
	public String toString() {
		return String.format("Track[%s]", location.toString());
	}

	@Override
	public int compareTo(Track arg0) {
		if (album.compareTo(arg0.album) != 0)
			return album.compareTo(arg0.album);

		if (disc != arg0.disc)
			return disc - arg0.disc;

		if (track != arg0.track)
			return track - arg0.track;

		return location.compareTo(arg0.location);
	}
}
