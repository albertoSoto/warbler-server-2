package com.trao1011.warbler.transcoder;

import java.io.*;
import java.nio.ByteOrder;
import java.util.Random;

import javax.sound.sampled.AudioFormat;

import de.sciss.jump3r.lowlevel.LameEncoder;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;

public abstract class Transcoder implements Runnable {
	static final boolean bigEndian = ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);
	protected File input;
	private int outputQuality;
	private String handleName;
	private EventBus eventBus;
	protected LameEncoder encoder;
	protected byte[] encBuffer;

	public Transcoder(io.vertx.core.Vertx vertx, File input, int outputQuality) throws FileNotFoundException {
		this.input = input;
		this.outputQuality = outputQuality;
		if (!input.isFile())
			throw new FileNotFoundException(input.getAbsolutePath());
		
		Random rnd = new Random();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 64; i++) {
			int id = rnd.nextInt(36);
			sb.append(id < 26 ? 'a' + id : '0' + (id - 26));
		}
		handleName = sb.toString();
		eventBus = vertx.eventBus();
		encoder = null;
	}
	
	public String getHandleName() {
		return handleName;
	}
	
	public int getOutputQuality() {
		return outputQuality;
	}
	
	protected LameEncoder createEncoder(AudioFormat intermediateFormat) {
		encoder = new LameEncoder(intermediateFormat, LameEncoder.BITRATE_AUTO,
				LameEncoder.CHANNEL_MODE_JOINT_STEREO, outputQuality, true);
		encBuffer = new byte[encoder.getPCMBufferSize()];
		return encoder;
	}
	
	protected int getPCMBufferSize() {
		return encoder.getPCMBufferSize();
	}
	
	protected int encodeBuffer(byte[] pcm, int offset, int length, byte[] encoded) {
		return encoder.encodeBuffer(pcm, offset, length, encoded);
	}
	
	protected int encodeFinish(byte[] encoded) {
		return encoder.encodeFinish(encoded);
	}
	
	protected void emitBytes(byte[] bytes, int length) {
		eventBus.publish(handleName, Buffer.buffer(bytes).getBuffer(0, length));
	}
	
	/**
	 * Should be called before the thread exits.
	 */
	public void finish() {
		int footerLength = encodeFinish(encBuffer);
		emitBytes(encBuffer, footerLength);
		eventBus.publish(handleName, null);
		encoder.close();
	}
}
