package com.task05;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.task05.dto.Event;
import com.task05.dto.Request;
import com.task05.dto.Response;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "target_table", value = "${target_table}")
})
public class ApiHandler implements RequestHandler<Request, Response> {

	AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
	private DynamoDB dynamoDb = new DynamoDB(client);
	private String DYNAMODB_TABLE_NAME = System.getenv("target_table");

	@Override
	public Response handleRequest(Request request, Context context) {

		int principalId = request.getPrincipalID();
		Map<String, String> body = request.getContent();

		String newId = UUID.randomUUID().toString();
		String createdAt = DateTimeFormatter.ISO_INSTANT
				.format(Instant.now().atOffset(ZoneOffset.UTC));

		Table table = dynamoDb.getTable(DYNAMODB_TABLE_NAME);

		Item item = new Item()
				.withPrimaryKey("id", newId)
				.withInt("principalId", principalId)
				.withString("createdAt", createdAt)
				.withMap("body", body);

		table.putItem(item);

		Event event = Event.builder()
				.id(newId)
				.principalID(principalId)
				.createdAt(createdAt)
				.body(body)
				.build();

        return Response.builder()
				.statusCode(201)
				.event(event)
				.build();
	}
}
