package com.trao1011.warbler.server;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.trao1011.warbler.database.*;
import com.trao1011.warbler.transcoder.*;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.handler.graphql.*;
import io.vertx.ext.web.sstore.LocalSessionStore;
import me.tongfei.progressbar.*;

public class WarblerServer {
	public static Vertx vertx = Vertx.vertx();

	public static String getAppDataFolder() {
		Path p;
		if (System.getProperty("os.name").toLowerCase().contains("win"))
			p = Paths.get(System.getenv("APPDATA"), "warbler");
		else if (System.getenv("XDG_DATA_HOME") != null)
			p = Paths.get(System.getenv("XDG_DATA_HOME"), "warbler");
		else
			p = Paths.get(System.getProperty("user.home"), ".local", "share", "warbler");

		p.toFile().mkdirs();
		return p.toString();
	}

	private static Configuration getConfig() {
		Configuration cfg = null;
		try {
			cfg = new Configuration(System.getProperty("warbler.server.config_path"));
		} catch (java.io.IOException e) {
			String configPath = System.getProperty("warbler.server.config_path", Configuration.DEFAULT_CFG_PATH.toString());
			System.err.println("[FATAL] Could not open configuration file " + configPath + " for reading.");
			return null;
		} catch (IllegalArgumentException e) {
			String configPath = System.getProperty("warbler.server.config_path", Configuration.DEFAULT_CFG_PATH.toString());
			System.err.println("[FATAL] Configuration file " + configPath + " contained invalid options:\n");
			System.err.println(e.getMessage());
			return null;
		} catch (Exception e) {
			String configPath = System.getProperty("warbler.server.config_path", Configuration.DEFAULT_CFG_PATH.toString());
			System.err.println("[FATAL] Could not parse configuration file " + configPath + ":\n");
			System.err.println(e.getMessage());
			return null;
		}

		return cfg;
	}

	private static Router createRouter() {
		Router router = Router.router(vertx);
		router.route().handler(CookieHandler.create());
		router.route()
			.handler(SessionHandler.create(LocalSessionStore.create(vertx))
				.setCookieHttpOnlyFlag(true)
				.setSessionTimeout(1000L * 60 * 60 * 24 * 7));
		router.route().handler(BodyHandler.create().setBodyLimit(2 << 16));

		router.post("/login").handler(Identity.loginHandler);
		router.get("/loggedIn").handler(Identity.loggedInHandler);
		router.get("/logout").handler(Identity.logoutHandler);

		router.get("/isstream/:id").handler(Streaming.streamAudio);

		router.route("/graphql").handler(GraphQLHandler.create(new GraphDB().ql()));

		router.route().handler(ctx -> ctx.response().setStatusCode(404).end());

		return router;
	}

	public static void main(String[] args) {
		Configuration cfg = getConfig();
		if (cfg == null)
			System.exit(1);

		int numFiles = DataUtilities.countFilesUnder(cfg.getMediaPath().toFile());
		try (ProgressBar pb = new ProgressBar("Reading media...", numFiles, 250,
				System.err, ProgressBarStyle.ASCII, "", 1)) {
			// 250 = milliseconds of update interval time.
			pb.stepTo(0);
			MediaDatabase.getInstance().scan(cfg.getMediaPath(), pb);
		}
		try {
			new Thread(new MediaDatabaseWatcher(cfg.getMediaPath())).start();
		} catch (java.io.IOException e) {
			System.err.println("Could not create a file watcher for the media directory.");
			System.exit(1);
		}
		UserDatabase.getInstance();

		HttpServer server = vertx.createHttpServer();
		Router httpRouter = createRouter();
		server.requestHandler(httpRouter).listen(cfg.getServerPort(), "0.0.0.0", res -> {
			if (res.succeeded()) {
				System.err.println("Now listening on port " + cfg.getServerPort() + "...");
			} else {
				System.err.println("Failed to bind to port " + cfg.getServerPort() + ".");
				System.exit(1);
			}
		});
	}

	public static void transcode(java.io.File f, java.io.File nf) {
		Transcoder t = null;
		try {
			t = new MP3Transcoder(vertx, f, 0);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}

		MessageConsumer<Buffer> reader = vertx.eventBus().consumer(t.getHandleName());
		reader.handler(new TranscoderReader(reader, nf, null));
		Thread tcThread = new Thread(t);
		tcThread.start();
		try {
			tcThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}
}
