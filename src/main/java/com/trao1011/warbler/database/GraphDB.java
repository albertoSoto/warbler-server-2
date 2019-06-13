package com.trao1011.warbler.database;

import java.io.InputStream;
import java.util.Scanner;
import java.util.stream.Collectors;

import graphql.GraphQL;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.impl.RoutingContextImpl;

public class GraphDB {
	GraphQL gql;
	private static final String schemaName = "/gql_schema.sdl";

	public GraphDB() {
		InputStream is = this.getClass().getResourceAsStream(schemaName);
		String schemaInput;
		try (Scanner sc = new Scanner(is)) {
			schemaInput = sc.useDelimiter("\\Z").next();
		}

		TypeDefinitionRegistry reg = new SchemaParser().parse(schemaInput);
		RuntimeWiring gqlWiring = generateRuntimeWiring();
		GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(reg, gqlWiring);
		gql = GraphQL.newGraphQL(schema).build();
	}

	public GraphQL ql() {
		return gql;
	}

	private static User currentUser(DataFetchingEnvironment env) {
		RoutingContextImpl s = env.getContext();
		return s.session().get("wbuser");
	}

	private static boolean currentUserAuthorized(DataFetchingEnvironment env, int accessLevel) {
		// return currentUser(env).accessLevel >= accessLevel;
		return true;
	}

	private static boolean currentUserOwnsPlaylist(DataFetchingEnvironment env) {
		return UserDatabase.getInstance().playlists.get(env.getArgument("id")).author == currentUser(env) ||
				currentUserAuthorized(env, 3); // god mode
	}

	private static final java.util.function.UnaryOperator<TypeRuntimeWiring.Builder>
	queryBuilder = builder -> builder
			.dataFetcher("tracks", env -> MediaDatabase.getInstance().getIndex()
					.values().stream()
					.filter(v -> (v instanceof Track))
					.collect(Collectors.toList()))
			.dataFetcher("albums", env -> MediaDatabase.getInstance().getIndex()
					.values().stream()
					.filter(v -> (v instanceof Album))
					.collect(Collectors.toList()))
			.dataFetcher("artists", env -> MediaDatabase.getInstance().getIndex()
					.values().stream()
					.filter(v -> (v instanceof Artist))
					.collect(Collectors.toList()))
			.dataFetcher("playlists", env -> UserDatabase.getInstance().playlists.values())
			.dataFetcher("entities", env -> MediaDatabase.getInstance().getIndex().keySet())
			.dataFetcher("track", env -> MediaDatabase.getInstance()
					.get(env.getArgument("id"), Track.class))
			.dataFetcher("album", env -> MediaDatabase.getInstance()
					.get(env.getArgument("id"), Album.class))
			.dataFetcher("artist", env -> MediaDatabase.getInstance()
					.get(env.getArgument("id"), Artist.class))
			.dataFetcher("playlist", env -> UserDatabase.getInstance()
					.playlists.get(((Number) env.getArgument("id")).longValue()))
			.dataFetcher("user", env -> UserDatabase.getInstance()
					.users.get(((Number) env.getArgument("id")).longValue()))
			.dataFetcher("currentUser", env -> currentUser(env)),

	mutationBuilder = builder -> builder
			.dataFetcher("createUser", env -> {
				System.out.print("X");
				if (currentUserAuthorized(env, 3)) {
						return UserDatabase.getInstance().createUser(env.getArgument("username"),
								env.getArgument("password"),
								env.getArgument("accesslevel"),
								env.getArgument("displayname"));
				} else return null;
			})
			.dataFetcher("setUserPassword", env -> currentUser(env).uid == (Long) env.getArgument("uid") ?
					UserDatabase.getInstance().setUserPassword(((Number) env.getArgument("uid")).longValue(),
							env.getArgument("currentPassword"),
							env.getArgument("newPassword")) : null)
			.dataFetcher("setUserAccess", env -> {
				int newAccessLevel = env.getArgument("accesslevel");
				long uid = ((Number) env.getArgument("uid")).longValue();
				if (currentUserAuthorized(env, Math.min(3, newAccessLevel)))
					return UserDatabase.getInstance().setUserAccess(uid, newAccessLevel);
				else
					return null;
			})
			.dataFetcher("setUserName", env -> {
				long uid = ((Number) env.getArgument("uid")).longValue();
				if (currentUserAuthorized(env, 3) || currentUser(env).uid == (Long) env.getArgument("uid"))
					return UserDatabase.getInstance().setUserName(uid, env.getArgument("displayname"));
				else
					return null;
			})
			.dataFetcher("setUserPrefs", env -> {
				long uid = ((Number) env.getArgument("uid")).longValue();
				if (currentUserAuthorized(env, 3) || currentUser(env).uid == (Long) env.getArgument("uid"))
					return UserDatabase.getInstance().setUserPrefs(uid, env.getArgument("prefs"));
				else
					return null;
			})
			.dataFetcher("removeUser", env -> currentUserAuthorized(env, 3) ?
					UserDatabase.getInstance().removeUser(((Number) env.getArgument("uid")).longValue()) : false)

			.dataFetcher("createPlaylist", env -> currentUserAuthorized(env, 1) ?
					UserDatabase.getInstance().createPlaylist(env.getArgument("name"), currentUser(env)) : null)
			.dataFetcher("setPlaylistName", env -> currentUserOwnsPlaylist(env) ?
					UserDatabase.getInstance().setPlaylistName(((Number) env.getArgument("id")).longValue(),
							env.getArgument("name")) : null)
			.dataFetcher("setPlaylistShared", env -> currentUserOwnsPlaylist(env) ?
					UserDatabase.getInstance().setPlaylistShared(((Number) env.getArgument("id")).longValue(),
							env.getArgument("shared")) : null)
			.dataFetcher("setPlaylistTracks", env -> currentUserOwnsPlaylist(env) ?
					UserDatabase.getInstance().setPlaylistTracks(((Number) env.getArgument("id")).longValue(),
							env.getArgument("shared")) : null)
			.dataFetcher("removePlaylist", env -> currentUserOwnsPlaylist(env) ?
					UserDatabase.getInstance().removePlaylist(((Number) env.getArgument("id")).longValue()) : false);

	private static RuntimeWiring generateRuntimeWiring() {
		return RuntimeWiring.newRuntimeWiring()
				.type("QueryType", queryBuilder)
				.type("MutationType", mutationBuilder)
				.type("Track", builder -> builder
						.dataFetcher("id", env -> ((Track) env.getSource()).uuid)
						.dataFetcher("title", env -> ((Track) env.getSource()).title)
						.dataFetcher("track", env -> ((Track) env.getSource()).track)
						.dataFetcher("disc", env -> ((Track) env.getSource()).disc)
						.dataFetcher("duration", env -> ((Track) env.getSource()).duration)
						.dataFetcher("bitrate", env -> ((Track) env.getSource()).bitrate)
						.dataFetcher("album", env -> ((Track) env.getSource()).album)
						.dataFetcher("artists", env -> ((Track) env.getSource()).artists))
				.type("Album", builder -> builder
						.dataFetcher("id", env -> ((Album) env.getSource()).uuid)
						.dataFetcher("title", env -> ((Album) env.getSource()).title)
						.dataFetcher("year", env -> ((Album) env.getSource()).date[0])
						.dataFetcher("month", env -> ((Album) env.getSource()).date[1] == 0 ? null : ((Album) env.getSource()).date[1])
						.dataFetcher("day", env -> ((Album) env.getSource()).date[2] == 0 ? null : ((Album) env.getSource()).date[2])
						.dataFetcher("artists", env -> ((Album) env.getSource()).albumArtists)
						.dataFetcher("tracks", env -> ((Album) env.getSource()).tracks))
				.type("Artist", builder -> builder
						.dataFetcher("id", env -> ((Artist) env.getSource()).uuid)
						.dataFetcher("name", env -> ((Artist) env.getSource()).name)
						.dataFetcher("tracks", env -> ((Artist) env.getSource()).tracks)
						.dataFetcher("albums", env -> ((Artist) env.getSource()).albums))
				.type("Playlist", builder -> builder
						.dataFetcher("id", env -> ((Playlist) env.getSource()).id)
						.dataFetcher("name", env -> ((Playlist) env.getSource()).name)
						.dataFetcher("author", env -> ((Playlist) env.getSource()).author)
						.dataFetcher("shared", env -> ((Playlist) env.getSource()).shared)
						.dataFetcher("tracks", env -> ((Playlist) env.getSource()).tracks))
				.type("User", builder -> builder
						.dataFetcher("id", env -> ((User) env.getSource()).uid)
						.dataFetcher("username", env -> ((User) env.getSource()).username)
						.dataFetcher("displayname", env -> ((User) env.getSource()).displayName())
						.dataFetcher("playlists", env -> {
							User current = currentUser(env), target = env.getSource();
							return target.playlists.stream()
									.filter(pl -> target == current || current.accessLevel >= 3 || pl.shared)
									.collect(Collectors.toList());
						})
						.dataFetcher("accesslevel", env -> {
							User target = env.getSource();
							return target == currentUser(env) || currentUserAuthorized(env, 3) ?
									target.accessLevel :
									null;
						})
						.dataFetcher("prefs", env -> {
							User target = env.getSource();
							return target == currentUser(env) || currentUserAuthorized(env, 3) ?
									new JsonObject(target.prefs).encode() :
									null;
						}))
				.build();
	}
}
