package com.trao1011.warbler.database;

import java.io.InputStream;
import java.util.Scanner;
import java.util.stream.Collectors;

import graphql.nextgen.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;

public class GraphDB {
	GraphQL gql;
	
	public GraphDB(String schemaName) {
		InputStream is = Class.class.getResourceAsStream(schemaName);
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

	private static RuntimeWiring generateRuntimeWiring() {
		return RuntimeWiring.newRuntimeWiring()
				.type("QueryType", wiring -> wiring
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
						.dataFetcher("entities", env -> MediaDatabase.getInstance().getIndex().keySet())
						.dataFetcher("track", env -> MediaDatabase.getInstance()
								.get(env.getArgument("id"), Track.class))
						.dataFetcher("album", env -> MediaDatabase.getInstance()
								.get(env.getArgument("id"), Album.class))
						.dataFetcher("artist", env -> MediaDatabase.getInstance()
								.get(env.getArgument("id"), Artist.class)))
				.type("Track", wiring -> wiring
						.dataFetcher("id", env -> ((Track) env.getSource()).uuid)
						.dataFetcher("title", env -> ((Track) env.getSource()).title)
						.dataFetcher("track", env -> ((Track) env.getSource()).track)
						.dataFetcher("disc", env -> ((Track) env.getSource()).disc)
						.dataFetcher("duration", env -> ((Track) env.getSource()).duration)
						.dataFetcher("bitrate", env -> ((Track) env.getSource()).bitrate)
						.dataFetcher("album", env -> ((Track) env.getSource()).album)
						.dataFetcher("artists", env -> ((Track) env.getSource()).artists))
				.type("Album", wiring -> wiring
						.dataFetcher("id", env -> ((Album) env.getSource()).uuid)
						.dataFetcher("title", env -> ((Album) env.getSource()).title)
						.dataFetcher("year", env -> ((Album) env.getSource()).date[0])
						.dataFetcher("month", env -> ((Album) env.getSource()).date[1] == 0 ? null : ((Album) env.getSource()).date[1])
						.dataFetcher("day", env -> ((Album) env.getSource()).date[2] == 0 ? null : ((Album) env.getSource()).date[2])
						.dataFetcher("artists", env -> ((Album) env.getSource()).albumArtists)
						.dataFetcher("tracks", env -> ((Album) env.getSource()).tracks))
				.type("Artist", wiring -> wiring
						.dataFetcher("id", env -> ((Artist) env.getSource()).uuid)
						.dataFetcher("name", env -> ((Artist) env.getSource()).name)
						.dataFetcher("tracks", env -> ((Artist) env.getSource()).tracks)
						.dataFetcher("albums", env -> ((Artist) env.getSource()).albums))
				.build();
	}
}
