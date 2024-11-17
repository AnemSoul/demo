package com.task11;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.*;

@LambdaHandler(lambdaName = "api_handler",
		roleName = "api_handler-role",
		aliasName = "${lambdas_alias_name}",
		runtime = DeploymentRuntime.JAVA17,
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "tables_table", value = "${tables_table}"),
		@EnvironmentVariable(key = "reservations_table", value = "${reservations_table}"),
		@EnvironmentVariable(key = "booking_userpool", value = "${booking_userpool}")})

public class ApiHandler implements RequestHandler<ApiHandler.APIRequest, APIGatewayV2HTTPResponse> {

	private final ObjectMapper objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

	private final AmazonDynamoDB amazonDynamoDB = AmazonDynamoDBClientBuilder.standard().withRegion(System.getenv("region")).build();

	private final CognitoIdentityProviderClient identityProviderClient = CognitoIdentityProviderClient.builder().region(Region.of(System.getenv("region"))).build();

	public APIGatewayV2HTTPResponse handleRequest(APIRequest requestEvent, Context context) {
		System.out.println("API request:" + requestEvent);
		return switch(requestEvent.path()) {
			case "/signup" -> {
				var userPoolId = getUserPoolId();
				yield signUpUser(requestEvent, userPoolId);
			}
			case "/signin" -> {
				var userPoolId = getUserPoolId();
				var clientId = createAppClient(userPoolId);
				yield signInUser(requestEvent, userPoolId, clientId);
			}
			case "/tables" -> {
				if(requestEvent.method().equals("POST")) {
					var tableObject = buildTableObject(requestEvent);
					yield persistTable(tableObject);
				} else {
					yield scanTable();
				}
			}
			case "/reservations" -> {
				if(requestEvent.method().equals("POST")) {
					var reservationObject = buildReservationObject(requestEvent);
					yield persistReservation(reservationObject);
				} else {
					yield scanReservations();
				}
			}
			default -> {
				System.out.println("Processing" +  requestEvent.authorization_header());
				yield findTable(requestEvent.authorization_header());
			}
		};
	}

	private Table buildTableObject(APIRequest apiRequest) {
		// Логгирование
		System.out.println("Calling buildTableObject ...");

		// Извлечение данных из JSON
		String idString = apiRequest.body_json().get("id");
		String numberString = apiRequest.body_json().get("number");
		String placesString = apiRequest.body_json().get("places");
		String isVipString = apiRequest.body_json().get("isVip");
		String minOrderString = apiRequest.body_json().get("minOrder");

		// Преобразование данных
		Integer id = Integer.valueOf(idString);
		Integer number = Integer.valueOf(numberString);
		Integer places = Integer.valueOf(placesString);
		Boolean isVip = Boolean.valueOf(isVipString);
		Integer minOrder = (minOrderString != null) ? Integer.valueOf(minOrderString) : null;

		// Создание объекта Table
		return new Table(id, number, places, isVip, minOrder);
	}

	private Reservation buildReservationObject(APIRequest apiRequest) {
		// Логгирование
		System.out.println("Calling buildReservationObject ...");

		// Извлечение данных из JSON
		String tableNumberString = apiRequest.body_json().get("tableNumber");
		String clientName = apiRequest.body_json().get("clientName");
		String phoneNumber = apiRequest.body_json().get("phoneNumber");
		String date = apiRequest.body_json().get("date");
		String slotTimeStart = apiRequest.body_json().get("slotTimeStart");
		String slotTimeEnd = apiRequest.body_json().get("slotTimeEnd");

		// Преобразование данных
		Integer tableNumber = Integer.valueOf(tableNumberString);

		// Создание объекта Reservation
		return new Reservation(tableNumber, clientName, phoneNumber, date, slotTimeStart, slotTimeEnd);
	}

	private Table buildTableResponse(Map<String, AttributeValue> result) {
		// Извлечение данных из Map
		AttributeValue idValue = result.get("id");
		AttributeValue numberValue = result.get("number");
		AttributeValue placesValue = result.get("places");
		AttributeValue isVipValue = result.get("isVip");
		AttributeValue minOrderValue = result.get("minOrder");

		// Преобразование данных
		Integer id = Integer.valueOf(idValue.getN());
		Integer number = Integer.valueOf(numberValue.getN());
		Integer places = Integer.valueOf(placesValue.getN());
		Boolean isVip = isVipValue.getBOOL();
		Integer minOrder = (minOrderValue != null) ? Integer.valueOf(minOrderValue.getN()) : null;

		// Создание объекта Table
		return new Table(id, number, places, isVip, minOrder);
	}


	private Reservation buildReservationResponse(Map<String, AttributeValue> result) {
		// Извлечение данных из Map
		AttributeValue tableNumberValue = result.get("tableNumber");
		AttributeValue clientNameValue = result.get("clientName");
		AttributeValue phoneNumberValue = result.get("phoneNumber");
		AttributeValue dateValue = result.get("date");
		AttributeValue slotTimeStartValue = result.get("slotTimeStart");
		AttributeValue slotTimeEndValue = result.get("slotTimeEnd");

		// Преобразование данных
		Integer tableNumber = Integer.valueOf(tableNumberValue.getN());
		String clientName = clientNameValue.getS();
		String phoneNumber = phoneNumberValue.getS();
		String date = dateValue.getS();
		String slotTimeStart = slotTimeStartValue.getS();
		String slotTimeEnd = slotTimeEndValue.getS();

		// Создание объекта Reservation
		return new Reservation(tableNumber, clientName, phoneNumber, date, slotTimeStart, slotTimeEnd);
	}


	private String createAppClient(String userPoolId) {
		// Логгирование начала метода
		System.out.println("Calling createAppClient ...");

		// Создание запроса на создание клиента пула пользователей
		CreateUserPoolClientRequest request = CreateUserPoolClientRequest.builder()
				.userPoolId(userPoolId) // Устанавливаем ID пула пользователей
				.explicitAuthFlows(ExplicitAuthFlowsType.ALLOW_ADMIN_USER_PASSWORD_AUTH,
						ExplicitAuthFlowsType.ALLOW_REFRESH_TOKEN_AUTH) // Разрешаем определенные потоки авторизации
				.clientName("api_client") // Устанавливаем имя клиента
				.build();

		// Отправка запроса на создание клиента
		CreateUserPoolClientResponse result = identityProviderClient.createUserPoolClient(request);

		// Логгирование результата
		System.out.println("createAppClient " + result.userPoolClient().clientId());

		// Возвращаем clientId
		return result.userPoolClient().clientId();
	}


	private APIGatewayV2HTTPResponse signUpUser(APIRequest apiRequest, String userPoolId) {
		// Логгирование начала метода
		System.out.println("Calling signUpUser ...");

		try {
			// Создаем список атрибутов пользователя
			ArrayList<AttributeType> userAttributeList = new ArrayList<>();
			String email = apiRequest.body_json().get("email"); // Получаем email из запроса
			userAttributeList.add(AttributeType.builder().name("email").value(email).build()); // Добавляем email в атрибуты

			// Создаем запрос на создание пользователя
			AdminCreateUserRequest adminCreateUserRequest = AdminCreateUserRequest.builder()
					.temporaryPassword(apiRequest.body_json().get("password")) // Получаем временный пароль
					.userPoolId(userPoolId) // Устанавливаем ID пула пользователей
					.username(email) // Устанавливаем имя пользователя (email)
					.messageAction(MessageActionType.SUPPRESS) // Отключаем отправку сообщений
					.userAttributes(userAttributeList) // Устанавливаем атрибуты пользователя
					.build();

			// Отправляем запрос на создание пользователя
			identityProviderClient.adminCreateUser(adminCreateUserRequest);

			// Логгирование успешного создания пользователя
			System.out.println("User has been created");

			// Возвращаем успешный ответ
			return APIGatewayV2HTTPResponse.builder()
					.withStatusCode(200)
					.withHeaders(buildHeaders())
					.build();
		} catch (CognitoIdentityProviderException e) {
			// Логгирование ошибки
			System.err.println("Error while signing up user " + e.awsErrorDetails().errorMessage());

			// Возвращаем ошибку с описанием
			return APIGatewayV2HTTPResponse.builder()
					.withStatusCode(400)
					.withHeaders(buildHeaders())
					.withBody("ERROR " + e.getMessage())
					.build();
		}
	}


	private Map<String, String> buildHeaders() {
		// Создаем новую карту для хранения заголовков
		HashMap<String, String> map = new HashMap<>();

		// Добавляем заголовки в карту
		map.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token");
		map.put("Access-Control-Allow-Origin", "*"); // Разрешаем доступ с любого источника
		map.put("Access-Control-Allow-Methods", "*"); // Разрешаем любые методы
		map.put("Accept-Version", "*"); // Указываем, что все версии приемлемы

		// Возвращаем карту с заголовками
		return map;
	}


	private APIGatewayV2HTTPResponse signInUser(APIRequest apiRequest, String userPoolId, String clientId) {
		// Логгирование начала процесса входа
		System.out.println("Calling signInUser ...");

		// Подготовка запроса на аутентификацию
		Map<String, String> authParameters = new HashMap<>();
		authParameters.put("USERNAME", apiRequest.body_json().get("email")); // Получаем email
		authParameters.put("PASSWORD", apiRequest.body_json().get("password")); // Получаем пароль

		var authRequest = AdminInitiateAuthRequest.builder()
				.authFlow("ADMIN_USER_PASSWORD_AUTH") // Тип аутентификации
				.authParameters(authParameters) // Параметры аутентификации
				.userPoolId(userPoolId) // ID пула пользователей
				.clientId(clientId) // ID клиента
				.build();

		try {
			// Выполняем аутентификацию
			var authResponse = identityProviderClient.adminInitiateAuth(authRequest);
			System.out.println("Auth response: " + authResponse + " session: " + authResponse.session());

			var authResult = authResponse.authenticationResult(); // Получаем результат аутентификации

			// Если требуется новый пароль, обрабатываем вызов для изменения пароля
			if (authResponse.challengeName() != null && authResponse.challengeName().equals(ChallengeNameType.NEW_PASSWORD_REQUIRED)) {
				// Отправляем новый пароль
				var challengeResponse = identityProviderClient.adminRespondToAuthChallenge(AdminRespondToAuthChallengeRequest.builder()
						.userPoolId(userPoolId)
						.clientId(clientId)
						.session(authResponse.session()) // Используем сессию из предыдущего ответа
						.challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED) // Указываем, что пароль нужно обновить
						.challengeResponses(Map.of(
								"NEW_PASSWORD", apiRequest.body_json().get("password"), // Новый пароль
								"USERNAME", apiRequest.body_json().get("email") // Email пользователя
						))
						.build());

				// Обновленный результат аутентификации
				System.out.println("Challenge passed: " + challengeResponse.authenticationResult().idToken());
				authResult = challengeResponse.authenticationResult(); // Получаем результат после обновления пароля
			}

			// Возвращаем успешный ответ с полученным токеном
			return APIGatewayV2HTTPResponse.builder()
					.withStatusCode(200)
					.withHeaders(buildHeaders()) // Добавляем заголовки
					.withBody(authResult.idToken()) // Возвращаем токен авторизации
					.build();
		} catch (Exception e) {
			// Логгируем ошибку и возвращаем ответ с ошибкой
			System.err.println("Error while signing in user: " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder()
					.withStatusCode(400)
					.withHeaders(buildHeaders())
					.withBody("ERROR " + e.getMessage())
					.build();
		}
	}


	private String getUserPoolId() {
		// Логгируем начало метода
		System.out.println("Calling getUserPoolId ...");

		// По умолчанию устанавливаем значение для ID пула пользователя
		String userPoolId = "test-id"; // Это значение будет использоваться, если пул не найден

		try {
			// Создаем запрос для получения списка пулов пользователей
			var request = ListUserPoolsRequest.builder().maxResults(50).build();

			// Отправляем запрос в клиент идентификации
			var response = identityProviderClient.listUserPools(request);

			// Ищем пул по имени, которое указано в переменной окружения
			for (var userPool : response.userPools()) {
				if (userPool.name().equals(System.getenv("booking_userpool"))) {
					// Если нашли пул с нужным именем, присваиваем его ID
					userPoolId = userPool.id();
					break; // Выход из цикла, так как нужный пул найден
				}
			}

			// Логгируем найденный ID пула
			System.out.println("User pool id: " + userPoolId);

		} catch (CognitoIdentityProviderException e) {
			// Логгируем ошибку, если запрос не удался
			System.err.println("Error while listing the user pools: " + e.awsErrorDetails().errorMessage());
		}

		// Возвращаем ID пула пользователя
		return userPoolId;
	}


	private APIGatewayV2HTTPResponse persistTable(Table table) {
		// Логгируем начало метода
		System.out.println("Calling persistTable ...");

		try {
			// Создаем карту атрибутов для записи в DynamoDB
			Map<String, AttributeValue> attributesMap = new HashMap<>();

			// Добавляем атрибуты для таблицы
			attributesMap.put("id", new AttributeValue().withN(String.valueOf(table.id())));
			attributesMap.put("number", new AttributeValue().withN(String.valueOf(table.number())));
			attributesMap.put("places", new AttributeValue().withN(String.valueOf(table.places())));
			attributesMap.put("isVip", new AttributeValue().withBOOL(table.isVip()));

			// Если minOrder существует, добавляем его в карту
			if (table.minOrder() != null) {
				attributesMap.put("minOrder", new AttributeValue().withN(String.valueOf(table.minOrder())));
			}

			// Отправляем данные в DynamoDB
			amazonDynamoDB.putItem(System.getenv("tables_table"), attributesMap);

			// Возвращаем успешный ответ с ID таблицы
			return APIGatewayV2HTTPResponse.builder()
					.withStatusCode(200)
					.withHeaders(buildHeaders())
					.withBody(String.valueOf(table.id()))
					.build();

		} catch (Exception e) {
			// Логгируем ошибку и возвращаем ошибку в ответе
			System.err.println("Error while persisting table: " + e.getMessage());

			// Возвращаем ошибку в ответе
			return APIGatewayV2HTTPResponse.builder()
					.withStatusCode(400)
					.withHeaders(buildHeaders())
					.withBody("ERROR: " + e.getMessage())
					.build();
		}
	}


	private APIGatewayV2HTTPResponse scanTable() {
		try {
			var tableList = amazonDynamoDB.scan(new ScanRequest(System.getenv("tables_table")))
					.getItems().stream().map(this::buildTableResponse).toList();
			System.out.println("Table scan: " + tableList);
			var apiResponse = new TableResponse(tableList);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withHeaders(buildHeaders()).withBody(objectMapper.writeValueAsString(apiResponse)).build();
		} catch (Exception e) {
			System.err.println("Error while scanning table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withHeaders(buildHeaders()).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse findTable(String tableId) {
		try {
			var attributesMap = new HashMap<String, AttributeValue>();
			attributesMap.put("id", new AttributeValue().withN(String.valueOf(tableId)));
			var result = amazonDynamoDB.getItem(System.getenv("tables_table"), attributesMap).getItem();
			var tableResult = buildTableResponse(result);
			System.out.println("Table find result: " + result);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withHeaders(buildHeaders()).withBody(objectMapper.writeValueAsString(tableResult)).build();
		} catch (Exception e) {
			System.err.println("Error while finding table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withHeaders(buildHeaders()).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse scanReservations() {
		try {
			var reservationList = amazonDynamoDB.scan(new ScanRequest(System.getenv("reservations_table")))
					.getItems().stream().map(this::buildReservationResponse).toList();
			var apiResponse = new ReservationResponse(reservationList);
			System.out.println("Reservation scan: " + reservationList);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withHeaders(buildHeaders()).withBody(objectMapper.writeValueAsString(apiResponse)).build();
		} catch (Exception e) {
			System.err.println("Error while scanning table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withHeaders(buildHeaders()).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse persistReservation(Reservation reservation) {
		System.out.println("Calling persistReservation ..." );
		try {
			if(validateTable(reservation) && validateReservation(reservation)) {
				var attributesMap = new HashMap<String, AttributeValue>();
				attributesMap.put("id", new AttributeValue(UUID.randomUUID().toString()));
				attributesMap.put("tableNumber", new AttributeValue().withN(String.valueOf(reservation.tableNumber())));
				attributesMap.put("clientName", new AttributeValue(String.valueOf(reservation.clientName())));
				attributesMap.put("phoneNumber", new AttributeValue(String.valueOf(reservation.phoneNumber())));
				attributesMap.put("date", new AttributeValue(reservation.date()));
				attributesMap.put("slotTimeStart", new AttributeValue(String.valueOf(reservation.slotTimeStart())));
				attributesMap.put("slotTimeEnd", new AttributeValue(String.valueOf(reservation.slotTimeEnd())));
				amazonDynamoDB.putItem(System.getenv("reservations_table"), attributesMap);
				return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withHeaders(buildHeaders()).withBody(UUID.randomUUID().toString()).build();
			} else {
				return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR, there is already a reservation or the table does not exist").build();
			}
		} catch(Exception e) {
			System.err.println("Error while persisting reservation " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withHeaders(buildHeaders()).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private boolean validateTable(Reservation reservation) {
		var tableList = amazonDynamoDB.scan(new ScanRequest(System.getenv("tables_table")))
				.getItems().stream().map(this::buildTableResponse).filter(value -> reservation.tableNumber().equals(value.number())).count();
		System.out.println("Validate table:" + tableList);
		return tableList == 1;
	}

	private boolean validateReservation(Reservation reservation) {
		var reservationList = amazonDynamoDB.scan(new ScanRequest(System.getenv("reservations_table")))
				.getItems().stream().map(this::buildReservationResponse)
				.filter(value ->
						value.tableNumber().equals(reservation.tableNumber()) && value.slotTimeStart().equals(reservation.slotTimeStart())
								&& value.slotTimeEnd().equals(reservation.slotTimeEnd())).count();
		System.out.println("Validate reservation:" + reservationList);
		return reservationList == 0;
	}



	public record APIRequest(String method, String path, String authorization_header, Map<String, String> body_json) {

	}

	public record Table(Number id, Number number, Number places, Boolean isVip, Number minOrder){

	}

	public record Reservation(Number tableNumber, String clientName, String phoneNumber, String date, String slotTimeStart, String slotTimeEnd) {

	}

	public record ReservationResponse(List<Reservation> reservations) {

	}

	public record TableResponse(List<Table> tables) {

	}

}