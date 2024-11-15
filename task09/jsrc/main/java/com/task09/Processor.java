package com.task09;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.TracingMode;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.task09.weatherDTO.Forecast;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

@LambdaHandler(
        lambdaName = "processor",
        roleName = "processor-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        tracingMode = TracingMode.Active
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
@EnvironmentVariables(
        @EnvironmentVariable(key = "target_table", value = "${target_table}")
)
public class Processor implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayV2HTTPResponse> {

    private final DynamoDB dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient());
    private final String tableName = System.getenv("target_table");

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {

            Forecast forecast = new ObjectMapper().readValue(fetchWeatherData(), Forecast.class);

            storeForecastData(forecast);

            return createResponse(200, "Weather data successfully processed and stored.");
        } catch (Exception e) {

            context.getLogger().log("Error: " + e.getMessage());
            return createResponse(500, "Internal Server Error: " + e.getMessage());
        }
    }

    private String fetchWeatherData() throws Exception {

        URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        try (Scanner scanner = new Scanner(new InputStreamReader(conn.getInputStream()))) {

            StringBuilder response = new StringBuilder();
            while (scanner.hasNext()) response.append(scanner.nextLine());
            return response.toString();
        }
    }

    private void storeForecastData(Forecast forecast) {

        Table table = dynamoDB.getTable(tableName);
        String id = UUID.randomUUID().toString();

        Map<String, Object> forecastMap = new HashMap<>();
        forecastMap.put("elevation", forecast.getElevation());
        forecastMap.put("generationtime_ms", forecast.getGenerationTimeMs());
        forecastMap.put("latitude", forecast.getLatitude());
        forecastMap.put("longitude", forecast.getLongitude());
        forecastMap.put("timezone", forecast.getTimezone());
        forecastMap.put("timezone_abbreviation", forecast.getTimezoneAbbreviation());
        forecastMap.put("utc_offset_seconds", forecast.getUtcOffsetSeconds());

        // Add hourly data
        forecastMap.put("hourly", Map.of(
                "time", forecast.getHourly().getTime(),
                "temperature_2m", forecast.getHourly().getTemperature2mList()
        ));

        // Add hourly units
        forecastMap.put("hourly_units", Map.of(
                "time", forecast.getHourlyUnits().getTime(),
                "temperature_2m", forecast.getHourlyUnits().getTemperature2mString()
        ));

        table
                .putItem(new Item()
                        .withPrimaryKey("id", id)
                        .withMap("forecast", forecastMap));
    }

    private APIGatewayV2HTTPResponse createResponse(int statusCode, String body) {

        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(statusCode)
                .withBody(body)
                .build();
    }
}
