package com.trao1011.warbler.transcoder;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;

import io.vertx.core.Vertx;
import javazoom.jl.decoder.*;

public class MP3Transcoder extends CustomTranscoder {

	public MP3Transcoder(Vertx vertx, File input, int outputQuality) throws FileNotFoundException {
		super(vertx, input, outputQuality);
		if (!input.getName().endsWith(".mp3"))
			throw new IllegalArgumentException("Input file must be an MP3 file.");
	}

	@Override
	public void run() {
		FileInputStream fis = null;
		MP3Decoder decoder = null;
		try {
			fis = new FileInputStream(input);
			decoder = new MP3Decoder(fis);
			decoder.setPCMConsumer(this);
			decoder.decode();
		} catch (FileNotFoundException e) {
		} finally {
			if (fis != null)
				try { fis.close(); } catch (IOException e) { }
		}
		finish();
	}
}

class MP3Decoder {
	private Bitstream bitstream;
	private Decoder decoder;
	private PCMConsumer consumer;
	private boolean sentFormat = false;

	public MP3Decoder(FileInputStream fis) {
		bitstream = new Bitstream(fis);
		decoder = new Decoder();
	}

	public void setPCMConsumer(PCMConsumer consumer) {
		this.consumer = consumer;
	}

	public void decode() {
		boolean moreFrames = true;
		while (moreFrames) {
			try {
				moreFrames = decodeFrame();
			} catch (JavaLayerException e) {
				moreFrames = false;
			}
		}

		try { bitstream.close(); } catch (BitstreamException e) { }
	}

	private boolean decodeFrame() throws JavaLayerException {
		Header h = bitstream.readFrame();
		if (h == null)
			return false;

		SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
		synchronized (this) {
			if (consumer != null) {
				if (sentFormat == false) {
					AudioFormat afmt = new AudioFormat(decoder.getOutputFrequency(), 16,
							decoder.getOutputChannels(), true, Transcoder.bigEndian);
					consumer.onAudioFormat(afmt);
					sentFormat = true;
				}

				ByteBuffer buffer = ByteBuffer.allocate(output.getBufferLength() * 2);
				buffer.order(ByteOrder.nativeOrder());
				buffer.asShortBuffer().put(output.getBuffer());
				consumer.onData(buffer.array(), output.getBufferLength() * 2);
			}
		}
		bitstream.closeFrame();
		return true;
	}
}
