package com.trao1011.warbler.transcoder;

import java.io.*;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.ext.web.RoutingContext;

public class TranscoderReader implements Handler<Message<Buffer>> {
	private FileOutputStream fos;
	private MessageConsumer<Buffer> consumer;
	private RoutingContext routingContext;
	private boolean responseOpen;

	public TranscoderReader(MessageConsumer<Buffer> consumer, File transcoded, RoutingContext routingContext) {
		this.routingContext = routingContext;
		this.consumer = consumer;
		try {
			fos = new FileOutputStream(transcoded);
		} catch (FileNotFoundException e) {
			fos = null;
		}
		this.responseOpen = routingContext != null;
	}

	@Override
	public void handle(Message<Buffer> event) {
		if (event.body() == null)
			consumer.unregister();
		
		if (responseOpen)
			handleWriteToStream(event);
		if (fos != null) {
			try { handleWriteToFile(event); } catch (IOException e) { }
		}
	}

	private void handleWriteToStream(Message<Buffer> event) {
		if (event.body() == null) {
			try { routingContext.response().end(); } catch (IllegalStateException e) { }
		} else {
			try {
				routingContext.response().write(event.body());
			} catch (IllegalStateException e) {
				responseOpen = false;
			}
		}
	}

	private void handleWriteToFile(Message<Buffer> event) throws IOException {
		if (event.body() == null) {
			fos.close();
			fos.flush();
		} else {
			fos.write(event.body().getBytes());
		}
	}
}
