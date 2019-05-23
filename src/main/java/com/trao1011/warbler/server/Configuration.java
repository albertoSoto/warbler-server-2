package com.trao1011.warbler.server;

import java.io.File;
import java.nio.file.*;
import java.util.stream.Collectors;

import org.tomlj.*;

public class Configuration {
	public static final Path DEFAULT_CFG_PATH = Paths.get(".", "config.toml");
	
	private TomlParseResult toml;
	private int serverPort;
	private Path mediaPath;
	
	public Configuration(String configPath) throws Exception {
		Path source = configPath == null ? DEFAULT_CFG_PATH : Paths.get(configPath);
		toml = Toml.parse(source);
		if (toml.hasErrors()) {
			String errorMessages = String.join("\n\n", 
					toml.errors().stream().map(err -> err.toString()).collect(Collectors.toList()));
			throw new Exception(errorMessages);
		}
		
		// Check for required configuration options
		// There aren't any.
		
		// Load all known configuration options
		mediaPath = Paths.get(toml.getString("media.path", () -> "./media"));
		serverPort = (int) toml.getLong("server.port", () -> 24594L);
		
		// Sanity-check all configuration options
		File mediaDirFile = mediaPath.toFile();
		if (!mediaDirFile.isDirectory()) {
			String err = "The provided media path " + mediaPath.toAbsolutePath() + " does not exist.";
			throw new IllegalArgumentException(err);
		}
		if (serverPort <= 0 || serverPort >= 65536) {
			throw new IllegalArgumentException("The provided port " + serverPort + " is not valid.");
		}
	}
	
	public Path getMediaPath() {
		return mediaPath;
	}
	
	public int getServerPort() {
		return serverPort;
	}
	
	public String getOption(String key) {
		return toml.getString(key);
	}
}
