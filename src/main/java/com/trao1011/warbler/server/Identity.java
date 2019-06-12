package com.trao1011.warbler.server;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import com.trao1011.warbler.database.User;
import com.trao1011.warbler.database.UserDatabase;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class Identity {
	public static Handler<RoutingContext>

	loginHandler = ctx -> {
		String username = "", password = "";
		String[] pairs = ctx.getBodyAsString().split("\\&");
		for (int i = 0; i < pairs.length; i++) {
			String[] fields = pairs[i].split("=");
			if (fields.length != 2) {
				ctx.response().setStatusCode(400).end();
				return;
			}

			String name = null, value = null;
			try {
				name = URLDecoder.decode(fields[0], "UTF-8");
				value = URLDecoder.decode(fields[1], "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			if (name.equals("u"))
				username = value;
			else if (name.equals("p"))
				password = value;
		}

		UserDatabase.getInstance().login(username, password).thenAccept(user -> {
			if (user != null)
				ctx.session().put("wbuser", user);
			ctx.response().setStatusCode(user == null ? 404 : 200).end();
		});
	},

	logoutHandler = ctx -> {
		ctx.session().destroy();
		ctx.response().setStatusCode(200).end();
	};

	public static boolean isAuthorized(RoutingContext ctx) {
		return isAuthorized(ctx, 0);
	}

	public static boolean isAuthorized(RoutingContext ctx, int accessLevel) {
		User currentUser = ctx.session().get("wbuser");
		if (currentUser == null)
			return false;
		else
			return currentUser.getAccessLevel() >= accessLevel;
	}
}
