package com.trao1011.warbler.database;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.*;

import com.trao1011.warbler.database.AudioTag.Attribute;

import me.tongfei.progressbar.ProgressBar;

public class MediaDatabase {
	private Map<String, MediaDatabaseEntry> index = new HashMap<String, MediaDatabaseEntry>();
	private Map<String, String> nameKeyCache = new HashMap<String, String>();

	public MediaDatabase() {
		reset();
	}

	private static MediaDatabase md = new MediaDatabase();
	public static MediaDatabase getInstance() {
		return md;
	}

	public void reset() {
		index.clear();
		nameKeyCache.clear();
	}

	Map<String, MediaDatabaseEntry> getIndex() {
		return index;
	}

	public MediaDatabaseEntry get(String uuid) {
		return index.get(uuid);
	}

	public MediaDatabaseEntry get(String uuid, Class<?> type) {
		MediaDatabaseEntry e = index.get(uuid);
		return type.isInstance(e) ? e : null;
	}

	public void scan(final Path media) {
		scan(media, null);
	}

	public void scan(final Path media, ProgressBar pb) {
		if (media.toFile().getName().startsWith(".")) {
			if (pb != null)
				pb.step();
			return;
		}

		if (media.toFile().isDirectory()) {
			try (Stream<Path> paths = Files.walk(media)) {
				paths.filter(Files::isRegularFile)
					.forEach(f -> scan(f, pb));
			} catch (IOException e) { e.printStackTrace(); }
			return;
		}

		AudioTag atag = AudioTag.read(media.toFile());
		if (atag == null) {
			if (pb != null)
				pb.step();
			return;
		}
		
		String trackUUID, albumUUID;
		List<String> artistUUIDs, albumArtistUUIDs;

		if (atag.containsKey(Attribute.MUSICBRAINZ_TRACK_ID))
			trackUUID = atag.get(Attribute.MUSICBRAINZ_TRACK_ID).toLowerCase().replaceAll("[^0-9a-f]", "");
		else if (atag.containsKey(Attribute.ACOUSTID_ID))
			trackUUID = atag.get(Attribute.ACOUSTID_ID).toLowerCase().replaceAll("[^0-9a-f]", "");
		else
			trackUUID = DataUtilities.SHA256File(media.toFile()).substring(4, 4 + 32);

		if (atag.containsKey(Attribute.MUSICBRAINZ_RELEASE_ID))
			albumUUID = atag.get(Attribute.MUSICBRAINZ_RELEASE_ID).toLowerCase().replaceAll("[^0-9a-f]", "");
		else if (atag.containsKey(Attribute.MUSICBRAINZ_RELEASE_GROUP_ID))
			albumUUID = atag.get(Attribute.MUSICBRAINZ_RELEASE_GROUP_ID).toLowerCase().replaceAll("[^0-9a-f]", "");
		else
			albumUUID = DataUtilities.SHA256(atag.get(Attribute.ALBUM) + atag.get(Attribute.ALBUM_ARTIST))
							.substring(4, 4 + 32);

		if (atag.containsKey(Attribute.MUSICBRAINZ_ARTIST_ID))
			artistUUIDs = Arrays.stream(atag.get(Attribute.MUSICBRAINZ_ARTIST_ID).toLowerCase().split(",|;|/"))
					.map(String::trim)
					.map(s -> s.replaceAll("[^0-9a-f]", ""))
					.collect(Collectors.toList());
		else
			artistUUIDs = Arrays.stream(atag.get(Attribute.ARTISTS, Attribute.ARTIST).split(",|;|/"))
					.map(String::trim)
					.map(DataUtilities::SHA256).map(s -> s.substring(4, 4 + 32))
					.collect(Collectors.toList());

		if (atag.containsKey(Attribute.MUSICBRAINZ_RELEASE_ARTIST_ID))
			albumArtistUUIDs = Arrays.stream(atag.get(Attribute.MUSICBRAINZ_RELEASE_ARTIST_ID).toLowerCase().split(",|;|/"))
					.map(String::trim)
					.map(s -> s.replaceAll("[^0-9a-f]", ""))
					.collect(Collectors.toList());
		else
			albumArtistUUIDs = Arrays.stream(atag.get(Attribute.ALBUM_ARTIST, Attribute.ARTIST).split(",|;|/"))
					.map(String::trim)
					.map(DataUtilities::SHA256).map(s -> s.substring(4, 4 + 32))
					.collect(Collectors.toList());

		Track track = (Track) index.get(trackUUID);
		if (track == null) {
			track = new Track(media);
			index.put(trackUUID, track);
		}
		track.artistRepr = atag.get(Attribute.ARTIST);
		track.bitrate = DataUtilities.tryParseInteger(atag.get(Attribute.BITRATE), -1);
		track.disc = DataUtilities.tryParseInteger(atag.get(Attribute.DISC_NUMBER, Attribute.TOTAL_DISCS), 0);
		track.duration = Integer.parseInt(atag.get(Attribute.DURATION));
		track.format = atag.get(Attribute.FORMAT);
		track.title = atag.get(Attribute.TITLE);
		track.track = DataUtilities.tryParseInteger(atag.get(Attribute.TRACK_NUMBER, Attribute.TOTAL_TRACKS), 0);
		track.uuid = trackUUID;
		track.artists.clear();

		Album album = (Album) index.get(albumUUID);
		if (album == null) {
			album = new Album();
			album.coverart = findBestCoverart(media.getParent());
			album.title = atag.get(Attribute.ALBUM);
			album.artistRepr = atag.get(Attribute.ALBUM_ARTIST);
			album.uuid = albumUUID;
			album.setDate(atag.get(Attribute.DATE));
			index.put(albumUUID, album);
		}
		track.album = album;
		album.tracks.add(track);

		String[] trackArtists = atag.get(Attribute.ARTISTS, Attribute.ARTIST).split(",|;|/"),
				albumArtists = atag.get(Attribute.ALBUM_ARTIST, Attribute.ARTIST).split(",|;|/");

		for (int i = 0; i < trackArtists.length; i++) {
			Artist trackArtist = (Artist) index.get(artistUUIDs.get(i));
			if (trackArtist == null) {
				trackArtist = new Artist(trackArtists[i]);
				trackArtist.uuid = artistUUIDs.get(i);
				trackArtist.tracks.add(track);
				trackArtist.appearances.add(album);
				index.put(artistUUIDs.get(i), trackArtist);
			} else
				trackArtist.name = trackArtists[i];
			track.artists.add(trackArtist);
		}
		
		for (int i = 0; i < albumArtists.length; i++) {
			Artist albumArtist = (Artist) index.get(albumArtistUUIDs.get(i));
			if (albumArtist == null) {
				albumArtist = new Artist(albumArtists[i]);
				albumArtist.uuid = albumArtistUUIDs.get(i);
				index.put(albumArtistUUIDs.get(i), albumArtist);
			} else
				albumArtist.name = albumArtists[i];
			album.albumArtists.add(albumArtist);
			albumArtist.albums.add(album);
		}

		if (pb != null)
			pb.step();
	}

	public void remove(final Path media) {
		Path absMedia = media.normalize().toAbsolutePath();

		// Remove all tracks that are affected by this command.
		index.entrySet().stream()
			.filter(e -> (e.getValue() instanceof Track))
			.filter(e -> ((Track) e.getValue()).location.normalize().toAbsolutePath().startsWith(absMedia))
			.forEach(e -> {
				Track t = (Track) e.getValue();
				t.album.tracks.remove(t);
				t.artists.forEach(a -> a.tracks.remove(t));
				index.remove(e.getKey(), t);
			});

		// Remove all albums with no tracks.
		index.entrySet().stream()
			.filter(e -> (e.getValue() instanceof Album))
			.filter(e -> ((Album) e.getValue()).tracks.size() == 0)
			.forEach(e -> {
				Album a = (Album) e.getValue();
				a.albumArtists.forEach(r -> r.albums.remove(a));
				index.remove(e.getKey(), a);
			});

		// Remove all artists with no tracks.
		index.entrySet().stream()
			.filter(e -> e.getValue() instanceof Artist)
			.filter(e -> ((Artist) e.getValue()).tracks.size() == 0)
			.forEach(e -> index.remove(e.getKey(), e.getValue()));
	}

	public void rescan(final Path media) {
		if (media.toFile().isDirectory()) {
			remove(media);
			scan(media);
		} else if (media.toFile().isFile()) {
			scan(media);
		}
	}

	static Path findBestCoverart(final Path containingDirectory) {
		if (containingDirectory == null)
			return null;

		File dir = containingDirectory.toFile();
		Set<File> images = Arrays.stream(dir.listFiles())
				.filter(f -> f.isFile())
				.filter(f -> f.getName().endsWith(".png") ||
								f.getName().endsWith(".jpg") ||
								f.getName().endsWith(".jpeg"))
				.collect(Collectors.toSet());
		Set<File> covers = images.stream()
				.filter(f -> f.getName().startsWith("cover") || f.getName().startsWith("folder"))
				.collect(Collectors.toSet());

		if (covers.size() > 0)
			return covers.iterator().next().toPath();
		else if (images.size() > 0)
			return images.iterator().next().toPath();
		else
			return null;
	}

}
