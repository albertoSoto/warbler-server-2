package com.trao1011.warbler.server;

import java.io.IOException;

import com.trao1011.warbler.database.MediaDatabase;
import com.trao1011.warbler.database.MediaDatabaseWatcher;

public class WarblerServer {
	public static void main(String[] args) {
		Configuration cfg = null;
		try {
			cfg = new Configuration(System.getProperty("warbler.server.config_path"));
		} catch (java.io.IOException e) {
			String configPath = System.getProperty("warbler.server.config_path", Configuration.DEFAULT_CFG_PATH.toString());
			System.err.println("[FATAL] Could not open configuration file " + configPath + " for reading.");
			System.exit(1);
		} catch (IllegalArgumentException e) {
			String configPath = System.getProperty("warbler.server.config_path", Configuration.DEFAULT_CFG_PATH.toString());
			System.err.println("[FATAL] Configuration file " + configPath + " contained invalid options:\n");
			System.err.println(e.getMessage());
			System.exit(1);
		} catch (Exception e) {
			String configPath = System.getProperty("warbler.server.config_path", Configuration.DEFAULT_CFG_PATH.toString());
			System.err.println("[FATAL] Could not parse configuration file " + configPath + ":\n");
			System.err.println(e.getMessage());
			System.exit(1);
		}
		
		MediaDatabase.getInstance().scan(cfg.getMediaPath());
		try {
			new Thread(new MediaDatabaseWatcher(cfg.getMediaPath())).start();
		} catch (IOException e) {
			System.err.println("Could not create a file watcher for the media directory.");
			System.exit(1);
		}
	}
}
