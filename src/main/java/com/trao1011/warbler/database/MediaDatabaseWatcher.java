package com.trao1011.warbler.database;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

public class MediaDatabaseWatcher implements Runnable {
	private WatchService watcher;
	private Map<WatchKey, Path> keys;

	public MediaDatabaseWatcher(Path watchPath) throws IOException {
		this.watcher = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<>();
		this.registerDirectory(watchPath);
	}

	@Override
	public void run() {
		while (true) {
			final WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException e) {
				System.err.print("Interrupted");
				return;
			}
			
			final Path dir = keys.get(key);
			if (dir == null) {
				System.err.println("WatchKey " + key + " unrecognized.");
				continue;
			}
			
			for (WatchEvent<?> evt : key.pollEvents()) {
				final Path target = dir.resolve((Path) evt.context());
				if (evt.kind() == StandardWatchEventKinds.ENTRY_CREATE && target.toFile().isDirectory())
					registerDirectory(target);
				if (evt.kind() == StandardWatchEventKinds.ENTRY_MODIFY && target.toFile().isDirectory())
					continue;
				System.out.println(evt.kind() + " on " + target);
				
				if (evt.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
					if (target.toFile().isFile())
						MediaDatabase.getInstance().scan(target);
				} else if (evt.kind() == StandardWatchEventKinds.ENTRY_DELETE)
					MediaDatabase.getInstance().remove(target);
				else if (evt.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
					if (target.toFile().isFile())
						MediaDatabase.getInstance().rescan(target);
				}
			}
			
			key.reset();
		}
	}
	
	private void registerDirectory(Path dir) {
		File dirFile = dir.toFile();
		if (!dirFile.isDirectory())
			throw new RuntimeException("The file " + dir.toAbsolutePath() + " does not exist or is not a directory.");
		
		try {
			Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
				@Override 
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					WatchKey wkey = dir.register(watcher, 
							StandardWatchEventKinds.ENTRY_CREATE,
							StandardWatchEventKinds.ENTRY_DELETE,
							StandardWatchEventKinds.ENTRY_MODIFY);
					keys.put(wkey, dir);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new RuntimeException("Could not register path " + dir.toAbsolutePath());
		}
	}
}
