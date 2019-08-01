package com.trao1011.warbler.server;

import java.io.*;
import java.net.URLConnection;
import java.nio.file.*;
import java.security.*;
import java.util.Base64;

import com.trao1011.warbler.database.Album;
import com.trao1011.warbler.database.MediaDatabase;
import com.trao1011.warbler.database.Track;
import com.trao1011.warbler.transcoder.Transcoder;
import com.trao1011.warbler.transcoder.TranscoderReader;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.ext.web.RoutingContext;

public class Streaming {
	public static final int DEFAULT_TRANSCODE_QUALITY = 2;

	// Remove files in the cache that already exist.
	static {
		Path transcodeFolder = Paths.get(WarblerServer.getAppDataFolder(), "transcodes");
		transcodeFolder.toFile().mkdirs();
		try {
			Files.walk(transcodeFolder).filter(Files::isRegularFile).map(Path::toFile).forEach(File::delete);
		} catch (IOException e) { }
		// If it couldn't delete a file, it probably wasn't put here by us anyway.
	}

	private static int determineTranscodeQuality(String value) {
		if (value == null)
			return DEFAULT_TRANSCODE_QUALITY;
		else if (value.equals("raw"))
			return -1;
		else {
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				return DEFAULT_TRANSCODE_QUALITY;
			}
		}
	}

	private static File getCachedTranscodeLocation(Track t, int quality) {
		MessageDigest digest = null;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			// This digest algorithm comes with Java.
			System.err.println("Your Java installation is misconfigured.");
			System.exit(1);
		}
		digest.update(t.getLocation().toString().getBytes());
		digest.update(("q" + quality).getBytes());

		String filename = Base64.getUrlEncoder().encodeToString(digest.digest());
		return Paths.get(WarblerServer.getAppDataFolder(), "transcodes", filename + ".mp3").toFile();
	}
	
	public static Handler<RoutingContext> albumArtwork = ctx -> {
		Album album;
		
		if (!Identity.isAuthenticated(ctx)) {
			ctx.response().setStatusCode(403).end("Forbidden");
			return;
		}
		
		if (MediaDatabase.getInstance().get(ctx.request().getParam("id"), Album.class) != null) {
			album = (Album) MediaDatabase.getInstance().get(ctx.request().getParam("id"), Album.class);
		} else {
			ctx.response().setStatusCode(400).end();
			return;
		}
		
		Path coverart = album.getCoverArt();
		if (coverart != null && coverart.toFile().exists()) {
			ctx.response().setStatusCode(200).sendFile(coverart.toAbsolutePath().toString()).end();
		} else {
			ctx.response().setStatusCode(404).end("Not Found");
		}
	},

	streamAudio = ctx -> {
		Track track;
		int transcodeQuality = DEFAULT_TRANSCODE_QUALITY;
		File transcoded;

		if (!Identity.isAuthenticated(ctx)) {
			ctx.response().setStatusCode(403).end("Forbidden");
			return;
		}

		if (MediaDatabase.getInstance().get(ctx.request().getParam("id"), Track.class) != null) {
			track = (Track) MediaDatabase.getInstance().get(ctx.request().getParam("id"), Track.class);
		} else {
			ctx.response().setStatusCode(400).end();
			return;
		}

		if (!ctx.queryParam("q").isEmpty()) {
			transcodeQuality = determineTranscodeQuality(ctx.queryParam("q").iterator().next());
		}

		ctx.response().setStatusCode(206);
		if (transcodeQuality == -1) {
			// Don't transcode.
			String guessedType =  URLConnection.guessContentTypeFromName(track.getLocation().toString());
			ctx.response().putHeader("Content-Type", guessedType).sendFile(track.getLocation().toString());
		} else if ((transcoded = getCachedTranscodeLocation(track, transcodeQuality)).exists()) {
			ctx.response().putHeader("Content-Type", "audio/mpeg").sendFile(transcoded.getAbsolutePath());
		} else {
			transcoded.deleteOnExit();
			
			Transcoder transcoder = null;
			try {
				transcoder = Transcoder.create(ctx.vertx(), track.getLocation().toFile(), transcodeQuality);
			} catch (FileNotFoundException e) {
				ctx.response().setStatusCode(500)
					.putHeader("Content-Type", "text/plain")
					.end("Could not find the input file!");
				return;
			} catch (UnsupportedOperationException e) {
				ctx.response().setStatusCode(500)
					.putHeader("Content-Type", "text/plain")
					.end("Warbler does not support streaming that type of file.");
				return;
			}

			ctx.response().setStatusCode(206)
				.putHeader("Accept-Ranges", "bytes")
				.putHeader("Content-Type", "audio/mpeg")
				.setChunked(true);
			MessageConsumer<Buffer> consumer = ctx.vertx().eventBus().consumer(transcoder.getHandleName());
			consumer.handler(new TranscoderReader(consumer, transcoded, ctx));
			new Thread(transcoder).start();
		}
	};
}
