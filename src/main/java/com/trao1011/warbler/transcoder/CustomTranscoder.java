package com.trao1011.warbler.transcoder;

import java.io.File;
import java.io.FileNotFoundException;

import javax.sound.sampled.AudioFormat;

import io.vertx.core.Vertx;

interface PCMConsumer {
	public void onAudioFormat(AudioFormat afmt);
	public void onData(byte[] data, int length);
}

public abstract class CustomTranscoder extends Transcoder implements PCMConsumer {
	public CustomTranscoder(Vertx vertx, File input, int outputQuality) throws FileNotFoundException {
		super(vertx, input, outputQuality);
	}

	@Override
	public void onAudioFormat(AudioFormat afmt) {
		createEncoder(afmt);
	}

	@Override
	public void onData(byte[] data, int length) {
		int bytesToTransfer = Math.min(length, getPCMBufferSize());
		int bytesWritten, currentOffset = 0;

		while (0 < (bytesWritten = encodeBuffer(data, currentOffset, bytesToTransfer, encBuffer))) {
			currentOffset += bytesToTransfer;
			bytesToTransfer = Math.min(encBuffer.length, length - currentOffset);
			emitBytes(encBuffer, bytesWritten);
		}
	}
}
