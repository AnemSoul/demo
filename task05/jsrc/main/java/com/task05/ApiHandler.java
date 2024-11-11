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
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.task05.dto.Events;
import com.task05.dto.Request;
import com.task05.dto.Response;
import org.joda.time.DateTime;

import java.util.UUID;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "target_table", value = "$(target_table)"),
        @EnvironmentVariable(key = "region", value = "$(region)")
})
@DependsOn(
        name = "Events",
        resourceType = ResourceType.DYNAMODB_TABLE
)
public class ApiHandler implements RequestHandler<Request, Response> {

    private final AmazonDynamoDB amazonDynamoDB = AmazonDynamoDBClientBuilder
            .standard()
            .withRegion(System.getenv("region"))
            .build();
    private final DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);

    private Item buildItem(Request request) {
        return new Item()
                .withPrimaryKey("id", UUID.randomUUID().toString())
                .withInt("principalID", request.getPrincipalID())
                .withString("createdAt", new DateTime().toString())
                .withMap("body", request.getContent());
    }

    private Events buildEvents(Item item) {
        Events entity = new Events();
        entity.setId(item.getString("id"));
        entity.setPrincipalID(item.getInt("principalID"));
        entity.setCreatedAt(item.getString("createdAt"));
        entity.setBody(item.getMap("body"));
        return entity;
    }

    public Response handleRequest(Request request, Context context) {

        context.getLogger().log("Received request: " + request.toString());

        Table table = dynamoDB.getTable(System.getenv("target_table"));
        Item item = buildItem(request);

        context.getLogger().log("Saving item: " + item);

        table.putItem(item);

        Response response = new Response();
        response.setStatusCode(201);
        response.setEvents(buildEvents(item));

        return response;
    }
}
