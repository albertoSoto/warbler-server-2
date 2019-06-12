package com.trao1011.warbler.transcoder;

import java.io.*;

import javax.sound.sampled.AudioFormat;

import com.jcraft.jogg.*;
import com.jcraft.jorbis.*;

import io.vertx.core.Vertx;

public class VorbisTranscoder extends CustomTranscoder {
	public VorbisTranscoder(Vertx vertx, File input, int outputQuality) throws FileNotFoundException {
		super(vertx, input, outputQuality);
		if (!input.getName().endsWith(".ogg"))
			throw new IllegalArgumentException("Input file must be an Ogg Vorbis file.");
	}

	@Override
	public void run() {
		FileInputStream fis = null;
		VorbisDecoder decoder = null;
		try {
			fis = new FileInputStream(input);
			decoder = new VorbisDecoder(fis);
			decoder.setPCMConsumer(this);
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
}

class VorbisDecoder {
	private int inBufferSize = 4096, convBufferSize = inBufferSize * 2;
	private byte[] convBuffer = new byte[convBufferSize];
	private InputStream input;
	private PCMConsumer consumer;

	public VorbisDecoder(InputStream fis) {
		this.input = fis;
	}

	public void setPCMConsumer(PCMConsumer c) {
		consumer = c;
	}

	public void decode() throws IOException {
		SyncState oy = new SyncState(); // Sync and verify the original incoming bitstream.
		StreamState os = new StreamState(); // Turn Ogg pages into in-order packets
		Page og = new Page(); // A page contains many packets
		Packet op = new Packet(); // Packets can be decoded

		Info vi = new Info(); // Stores bitstream settings.
		Comment vc = new Comment(); // Stores vorbis comments. We'll ignore these.
		DspState vd = new DspState();
		Block vb = new Block(vd);

		byte[] buffer;
		int bytes = 0;

		oy.init();

		while (true) {
			int eos = 0; // end-of-stream

			// Read a 4k block.
			int index = oy.buffer(inBufferSize);
			buffer = oy.data;
			bytes = input.read(buffer, index, inBufferSize);
			oy.wrote(bytes);

			// Get the first page, and set up the decode with its serial number.
			if (oy.pageout(og) != 1) {
				// If we are out of data, we're done, no worries.
				if (bytes < inBufferSize)
					break;

				throw new IOException("The input does not appear to be an Ogg stream.");
			}
			os.init(og.serialno());

			// Extract the initial header.
			vi.init();
			vc.init();
			if (os.pagein(og) < 0)
				throw new IOException("Could not read the first page of the Ogg bitstream.");
			if (os.packetout(op) != 1)
				throw new IOException("Error reading the initial header packet.");
			if (vi.synthesis_headerin(vc, op) < 0)
				throw new IOException("This Ogg bitstream does not contain Vorbis audio data.");

			// This stream is definitely Vorbis. Read the other headers and set up the decoder.
			int i = 0;
			while (i < 2) {
				while (i < 2) {
					int result = oy.pageout(og);
					if (result == 0)
						break; // Get more data.

					if (result == 1) {
						os.pagein(og);
						while (i < 2) {
							result = os.packetout(op);
							if (result == 0)
								break;
							if (result == -1)
								throw new IOException("The secondary header has been corrupted.");
							vi.synthesis_headerin(vc, op);
							i++;
						}
					}
				}

				// read more data
				index = oy.buffer(4096);
				buffer = oy.data;
				bytes = input.read(buffer, index, 4096);
				if (bytes == 0 && i < 2) {
					throw new IOException("The end of file was reached before all Vorbis headers were read.");
				}
				oy.wrote(bytes);
			}
			convBufferSize = inBufferSize / vi.channels;

			// Initialize the decoder.
			vd.synthesis_init(vi);
			vb.init(vd);

			if (consumer != null)
				consumer.onAudioFormat(new AudioFormat(vi.rate, 16, vi.channels, true, Transcoder.bigEndian));

			// This is a straight decode loop.
			float[][][] _pcm = new float[1][][];
			int[] _index = new int[vi.channels];
			while (eos == 0) {
				while (eos == 0) {
					int res = oy.pageout(og);
					if (res == 0)
						break; // Get more data.
					if (res == -1)
						System.err.println("Found corrupt or missing data in the bitstream. Continuing anyway.");
					else {
						os.pagein(og);
						while (true) {
							res = os.packetout(op);
							if (res == 0)
								break; // Get more data.
							if (res == -1) {
								// Already complained about this.
							} else {
								int samples;
								if (vb.synthesis(op) == 0)
									vd.synthesis_blockin(vb);

								while ((samples = vd.synthesis_pcmout(_pcm, _index)) > 0) {
									float[][] pcm = _pcm[0];
									int bout = samples < convBufferSize ? samples : convBufferSize;

									// Convert samples to 16-bit signed ints in host order, and interleave
									for (i = 0; i < vi.channels; i++) {
										int ptr = i * 2, mono = _index[i];
										for (int j = 0; j < bout; j++) {
											int val = (int) (pcm[i][mono + j] * 32768.0);
											val = Math.min(Math.max(val, -32768), 32767); // Clip
											if (val < 0)
												val |= 0x8000;

											convBuffer[ptr] = (byte) (val >>> (Transcoder.bigEndian ? 8 : 0));
											convBuffer[ptr + 1] = (byte) (val >>> (Transcoder.bigEndian ? 0 : 8));
											ptr += 2 * vi.channels;
										}
									}
									if (consumer != null)
										consumer.onData(convBuffer, 2 * vi.channels * bout);
									vd.synthesis_read(bout);
								}
							}
						}
						if (og.eos() != 0)
							eos = 1;
					}
				}
				if (eos == 0) {
					index = oy.buffer(inBufferSize);
					buffer = oy.data;
					bytes = input.read(buffer, index, inBufferSize);
					oy.wrote(bytes);
					if (bytes == 0)
						eos = 1;
				}
			}

			// Clean up the streams.
			os.clear();
			vb.clear();
			vd.clear();
			vi.clear();
		}

		oy.clear();
	}

}

