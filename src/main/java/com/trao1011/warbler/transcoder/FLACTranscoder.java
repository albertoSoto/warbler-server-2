package com.trao1011.warbler.transcoder;

import java.io.*;

import org.jflac.FLACDecoder;
import org.jflac.PCMProcessor;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;

import io.vertx.core.Vertx;

public class FLACTranscoder extends Transcoder implements PCMProcessor {
	
	public FLACTranscoder(Vertx vertx, File input, int outputQuality) throws FileNotFoundException {
		super(vertx, input, outputQuality);
		if (!input.getName().endsWith(".flac"))
			throw new IllegalArgumentException("Input file must be a FLAC file.");
	}

	@Override
	public void run() {
		FileInputStream fis = null;
		FLACDecoder decoder = null;
		try {
			fis = new FileInputStream(input);
			decoder = new FLACDecoder(fis);
			decoder.addPCMProcessor(this);
			decoder.decode();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fis != null)
				try { fis.close(); } catch (IOException e) { }
		}
		finish();
	}

	@Override
	public void processStreamInfo(StreamInfo streamInfo) {
		createEncoder(streamInfo.getAudioFormat());
	}

	@Override
	public void processPCM(ByteData pcm) {
		int bytesToTransfer = Math.min(pcm.getLen(), getPCMBufferSize());
		int bytesWritten, currentOffset = 0;
		
		while (0 < (bytesWritten = encodeBuffer(pcm.getData(), currentOffset, bytesToTransfer, encBuffer))) {
			currentOffset += bytesToTransfer;
			bytesToTransfer = Math.min(encBuffer.length, pcm.getLen() - currentOffset);
			emitBytes(encBuffer, bytesWritten);
		}
	}

}
