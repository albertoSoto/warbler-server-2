package com.trao1011.warbler.server;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.trao1011.warbler.database.GraphDB;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class GraphAPI {
	private static GraphDB gql = new GraphDB("/schema.sdl");

	public static Handler<RoutingContext> 
	
	graphqlHandler = ctx -> {
		ExecutionInput ei = ExecutionInput.newExecutionInput(ctx.getBodyAsString()).build();
		CompletableFuture<ExecutionResult> promise = gql.ql().executeAsync(ei);
		promise.thenAccept(res -> {
			if (res.getErrors().size() > 0) {
				ctx.response().setStatusCode(400).end(String.join("\n", 
						res.getErrors().stream()
							.map(GraphQLError::toString).collect(Collectors.toList())) + "\n");
			} else {
				Map<String, Object> data = res.getData();
				JsonObject jdata = new JsonObject(data);
				ctx.response().setStatusCode(200).end(jdata.encode());
			}
		});
	};

}
