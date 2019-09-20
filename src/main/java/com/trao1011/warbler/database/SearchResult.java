package com.trao1011.warbler.database;

public class SearchResult {
	MediaDatabaseEntry e;
	int score;
	
	SearchResult(MediaDatabaseEntry e, int score) {
		this.e = e;
		this.score = score;
	}
	
	public String uuid() {
		return e.uuid;
	}
	
	public String toString() {
		return "[\"" + e.uuid + "\", " + score + "]";
	}
}
