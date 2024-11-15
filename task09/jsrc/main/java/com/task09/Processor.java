package com.task09;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
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
import com.syndicate.deployment.model.RetentionSetting;
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
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED)
@EnvironmentVariables(
		@EnvironmentVariable(key = "target_table", value = "${target_table}")
)
public class Processor implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayV2HTTPResponse> {

	private final AmazonDynamoDB amazonDynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
	private final DynamoDB dynamoDB = new DynamoDB(amazonDynamoDBClient);
	private final String tableName = System.getenv("target_table");

	@Override

	public APIGatewayV2HTTPResponse handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		APIGatewayV2HTTPResponse response;
		try {
			// Fetch weather data
			String weatherData = fetchWeatherData();
			ObjectMapper objectMapper = new ObjectMapper();

			// Deserialize JSON to WeatherForecast object
			Forecast forecast = objectMapper.readValue(weatherData, Forecast.class);

			// Create a Map to store the forecast data
			Map<String, Object> forecastMap = new HashMap<>();
			forecastMap.put("elevation", forecast.getElevation());
			forecastMap.put("generationtime_ms", forecast.getGenerationtime_ms());
			forecastMap.put("latitude", forecast.getLatitude());
			forecastMap.put("longitude", forecast.getLongitude());
			forecastMap.put("timezone", forecast.getTimezone());
			forecastMap.put("timezone_abbreviation", forecast.getTimezone_abbreviation());
			forecastMap.put("utc_offset_seconds", forecast.getUtc_offset_seconds());

			// Create a Map for hourly data
			Map<String,Object> hourlyMap = new HashMap<>();
			hourlyMap.put("time", (forecast.getHourly().getTime()));
			hourlyMap.put("temperature_2m", forecast.getHourly().getTemperature_2m());
			forecastMap.put("hourly", hourlyMap);

			// Create a Map for hourly units
			Map<String, String> hourlyUnitsMap = new HashMap<>();
			hourlyUnitsMap.put("time", forecast.getHourly_units().getTime());
			hourlyUnitsMap.put("temperature_2m", forecast.getHourly_units().getTemperature_2m());
			forecastMap.put("hourly_units", hourlyUnitsMap);

			// Generate a unique ID
			String id = UUID.randomUUID().toString();

			// Store the forecast data in DynamoDB
			Table table = dynamoDB.getTable(tableName);
			Item item = new Item()
					.withPrimaryKey("id", id)
					.withMap("forecast", forecastMap);
			table.putItem(item);

			// Build the successful response
			response = APIGatewayV2HTTPResponse.builder()
					.withStatusCode(200)
					.withBody("Weather data successfully processed and stored.")
					.build();

		} catch (Exception ex) {
			context.getLogger().log("Error: " + ex.getMessage());
			response = APIGatewayV2HTTPResponse.builder()
					.withStatusCode(500)
					.withBody("{\"statusCode\": 500, \"message\": \"Internal Server Error\"} " + ex.getMessage())
					.build();
		}

		return response;
	}

	private String fetchWeatherData() throws Exception {

		URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=50.4375&longitude=30.5&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m");


		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		// Set the request method to GET
		conn.setRequestMethod("GET");

		// Read the response using a Scanner
		Scanner scanner = new Scanner(new InputStreamReader(conn.getInputStream()));
		StringBuilder response = new StringBuilder();

		while (scanner.hasNext()) {
			response.append(scanner.nextLine());
		}

		scanner.close();
		return response.toString();
	}
}
