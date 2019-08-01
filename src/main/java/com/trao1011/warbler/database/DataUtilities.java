package com.trao1011.warbler.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DataUtilities {
	private static MessageDigest sha256 = null;
	
	public static int tryParseInteger(String s, int defaultValue) {
		if (s == null)
			return defaultValue;

		if (s.contains("/"))
			s = s.split("/")[0];

		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static MessageDigest getSHA256Digest() {
		if (sha256 == null) {
			try {
				sha256 = MessageDigest.getInstance("SHA-256");
			} catch (NoSuchAlgorithmException e) {
				// We can do this because SHA-256 is supposed to be packaged with the Java runtime.
				e.printStackTrace();
				System.exit(1);
			}
		}
		
		sha256.reset();
		return sha256;
	}

	private static String bytesToHex(byte[] bytes) {
		StringBuffer hexStr = new StringBuffer();
		for (int i = 0; i < bytes.length; i++) {
			if ((bytes[i] & 0xFF) < 16)
				hexStr.append('0');
			hexStr.append(Integer.toHexString(bytes[i] & 0xFF));
		}
		return hexStr.toString();
	}

	public static String SHA256(final String s) {
		return bytesToHex(getSHA256Digest().digest(s.getBytes(StandardCharsets.UTF_8)));
	}

	public static String SHA256File(final File f) {
		MessageDigest digest = getSHA256Digest();
		byte[] chunk = new byte[1 << 22];
		int chunkLen;

		try (FileInputStream fis = new FileInputStream(f)) {
			while ((chunkLen = fis.read(chunk)) != -1)
				digest.update(chunk, 0, chunkLen);
		} catch (IOException e) {
			return null;
		}

		return bytesToHex(digest.digest());
	}

	public static int countFilesUnder(final File prefix) {
		int count = 0;
		for (File f : prefix.listFiles()) {
			if (f.isFile())
				count++;
			else if (f.isDirectory())
				count += countFilesUnder(f);
		}
		return count;
	}
}
