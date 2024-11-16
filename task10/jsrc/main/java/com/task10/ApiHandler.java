package com.task10;

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

	@Override
	public APIGatewayV2HTTPResponse handleRequest(APIRequest requestEvent, Context context) {
		System.out.println("API request:" + requestEvent);
		return switch (requestEvent.path()) {
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
				if (requestEvent.method().equals("POST")) {
					var tableObject = buildTableObject(requestEvent);
					yield persistTable(tableObject);
				} else {
					yield scanTable();
				}
			}
			case "/reservations" -> {
				if (requestEvent.method().equals("POST")) {
					var reservationObject = buildReservationObject(requestEvent);
					yield persistReservation(reservationObject);
				} else {
					yield scanReservations();
				}
			}
			default -> {
				System.out.println("Processing" + requestEvent.authorization_header());
				yield findTable(requestEvent.authorization_header());
			}
		};
	}

	private Table buildTableObject(APIRequest apiRequest) {
		System.out.println("Calling buildTableObject ...");
		return new Table(Integer.valueOf(apiRequest.body_json().get("id")), Integer.valueOf(apiRequest.body_json().get("number")),
				Integer.valueOf(apiRequest.body_json().get("places")), Boolean.valueOf(apiRequest.body_json().get("isVip")),
				Objects.nonNull(apiRequest.body_json().get("minOrder")) ? Integer.parseInt(apiRequest.body_json().get("minOrder")) : null);
	}

	private Reservation buildReservationObject(APIRequest apiRequest) {
		System.out.println("Calling buildReservationObject ...");
		return new Reservation(Integer.valueOf(apiRequest.body_json().get("tableNumber")), apiRequest.body_json().get("clientName"),
				apiRequest.body_json().get("phoneNumber"), apiRequest.body_json().get("date"),
				apiRequest.body_json().get("slotTimeStart"), apiRequest.body_json().get("slotTimeEnd"));
	}

	private Table buildTableResponse(Map<String, AttributeValue> result) {
		return new Table(Integer.valueOf(result.get("id").getN()), Integer.valueOf(result.get("number").getN()),
				Integer.valueOf(result.get("places").getN()), result.get("isVip").getBOOL(),
				Objects.nonNull(result.get("minOrder")) ? (Integer.valueOf(result.get("minOrder").getN())) : null);
	}

	private Reservation buildReservationResponse(Map<String, AttributeValue> result) {
		return new Reservation(Integer.valueOf(result.get("tableNumber").getN()), result.get("clientName").getS(), result.get("phoneNumber").getS(),
				result.get("date").getS(), result.get("slotTimeStart").getS(), result.get("slotTimeEnd").getS());
	}

	private String createAppClient(String userPoolId) {
		System.out.println("Calling createAppClient ...");
		var result = identityProviderClient.createUserPoolClient(
				CreateUserPoolClientRequest.builder().userPoolId(userPoolId)
						.explicitAuthFlows(ExplicitAuthFlowsType.ALLOW_ADMIN_USER_PASSWORD_AUTH, ExplicitAuthFlowsType.ALLOW_REFRESH_TOKEN_AUTH).clientName("api_client").build());
		System.out.println("createAppClient " + result.userPoolClient().clientId());
		return result.userPoolClient().clientId();
	}

	private APIGatewayV2HTTPResponse signUpUser(APIRequest apiRequest, String userPoolId) {
		System.out.println("Calling signUpUser ...");
		try {
			var userAttributeList = new ArrayList<AttributeType>();
			userAttributeList.add(AttributeType.builder().name("email").value(apiRequest.body_json().get("email")).build());
			var adminCreateUserRequest = AdminCreateUserRequest.builder()
					.temporaryPassword(apiRequest.body_json().get("password"))
					.userPoolId(userPoolId)
					.username(apiRequest.body_json().get("email"))
					.messageAction(MessageActionType.SUPPRESS)
					.userAttributes(userAttributeList).build();
			identityProviderClient.adminCreateUser(adminCreateUserRequest);
			System.out.println("User has been created ");
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).build();
		} catch (CognitoIdentityProviderException e) {
			System.err.println("Error while signing up user " + e.awsErrorDetails().errorMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse signInUser(APIRequest apiRequest, String userPoolId, String clientId) {
		System.out.println("Calling signInUser ...");
		// Set up the authentication request
		var authRequest = AdminInitiateAuthRequest.builder()
				.authFlow("ADMIN_USER_PASSWORD_AUTH")
				.authParameters(Map.of(
						"USERNAME", apiRequest.body_json().get("email"),
						"PASSWORD", apiRequest.body_json().get("password")
				))
				.userPoolId(userPoolId)
				.clientId(clientId)
				.build();

		try {
			var authResponse = identityProviderClient.adminInitiateAuth(authRequest);
			System.out.println("Auth response: " + authResponse + "session " + authResponse.session());
			var authResult = authResponse.authenticationResult();
			if (Objects.nonNull(authResponse.challengeName()) && authResponse.challengeName().equals(ChallengeNameType.NEW_PASSWORD_REQUIRED)) {
				var adminRespondToAuthChallengeResponse = identityProviderClient.adminRespondToAuthChallenge(AdminRespondToAuthChallengeRequest.builder()
						.userPoolId(userPoolId)
						.clientId(clientId)
						.session(authResponse.session())
						.challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
						.challengeResponses(
								Map.of("NEW_PASSWORD", apiRequest.body_json().get("password"),
										"USERNAME", apiRequest.body_json().get("email"))).build());
				System.out.println("Challenge passed: " + adminRespondToAuthChallengeResponse.authenticationResult().idToken());
				authResult = adminRespondToAuthChallengeResponse.authenticationResult();
			}
			// At this point, the user is successfully authenticated, and you can access JWT tokens:
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withBody(authResult.idToken()).build();
		} catch (Exception e) {
			System.err.println("Error while signing in user " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private String getUserPoolId() {
		System.out.println("Calling getUserPoolId ...");
		var userPoolDescriptionType = UserPoolDescriptionType.builder().id("test-id").build();
		try {
			var request = ListUserPoolsRequest.builder().maxResults(50).build();
			var response = identityProviderClient.listUserPools(request);
			userPoolDescriptionType = response.userPools().stream().filter(value -> value.name().equals(System.getenv("booking_userpool")))
					.findFirst().orElse(userPoolDescriptionType);
			System.out.println("User pool id: " + userPoolDescriptionType.id());
			return userPoolDescriptionType.id();

		} catch (CognitoIdentityProviderException e) {
			System.err.println("Error while listing the user pools: " + e.awsErrorDetails().errorMessage());
		}
		return userPoolDescriptionType.id();
	}

	private APIGatewayV2HTTPResponse persistTable(Table table) {
		System.out.println("Calling persistTable ...");
		try {
			var attributesMap = new HashMap<String, AttributeValue>();
			attributesMap.put("id", new AttributeValue().withN(String.valueOf(table.id())));
			attributesMap.put("number", new AttributeValue().withN(String.valueOf(table.number())));
			attributesMap.put("places", new AttributeValue().withN(String.valueOf(table.places())));
			attributesMap.put("isVip", new AttributeValue().withBOOL(table.isVip()));
			if (Objects.nonNull(table.minOrder())) {
				attributesMap.put("minOrder", new AttributeValue().withN(String.valueOf(table.minOrder())));
			}
			amazonDynamoDB.putItem(System.getenv("tables_table"), attributesMap);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withBody(String.valueOf(table.id())).build();
		} catch (Exception e) {
			System.err.println("Error while persisting table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse scanTable() {
		try {
			var tableList = amazonDynamoDB.scan(new ScanRequest(System.getenv("tables_table")))
					.getItems().stream().map(this::buildTableResponse).toList();
			System.out.println("Table scan: " + tableList);
			var apiResponse = new TableResponse(tableList);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withBody(objectMapper.writeValueAsString(apiResponse)).build();
		} catch (Exception e) {
			System.err.println("Error while scanning table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse findTable(String tableId) {
		try {
			var attributesMap = new HashMap<String, AttributeValue>();
			attributesMap.put("id", new AttributeValue().withN(String.valueOf(tableId)));
			var result = amazonDynamoDB.getItem(System.getenv("tables_table"), attributesMap).getItem();
			var tableResult = buildTableResponse(result);
			System.out.println("Table find result: " + result);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withBody(objectMapper.writeValueAsString(tableResult)).build();
		} catch (Exception e) {
			System.err.println("Error while finding table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse scanReservations() {
		try {
			var reservationList = amazonDynamoDB.scan(new ScanRequest(System.getenv("reservations_table")))
					.getItems().stream().map(this::buildReservationResponse).toList();
			var apiResponse = new ReservationResponse(reservationList);
			System.out.println("Reservation scan: " + reservationList);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withBody(objectMapper.writeValueAsString(apiResponse)).build();
		} catch (Exception e) {
			System.err.println("Error while scanning table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse persistReservation(Reservation reservation) {
		System.out.println("Calling persistReservation ...");
		try {
			if (validateTable(reservation) && validateReservation(reservation)) {
				var attributesMap = new HashMap<String, AttributeValue>();
				attributesMap.put("id", new AttributeValue(UUID.randomUUID().toString()));
				attributesMap.put("tableNumber", new AttributeValue().withN(String.valueOf(reservation.tableNumber())));
				attributesMap.put("clientName", new AttributeValue(String.valueOf(reservation.clientName())));
				attributesMap.put("phoneNumber", new AttributeValue(String.valueOf(reservation.phoneNumber())));
				attributesMap.put("date", new AttributeValue(reservation.date()));
				attributesMap.put("slotTimeStart", new AttributeValue(String.valueOf(reservation.slotTimeStart())));
				attributesMap.put("slotTimeEnd", new AttributeValue(String.valueOf(reservation.slotTimeEnd())));
				amazonDynamoDB.putItem(System.getenv("reservations_table"), attributesMap);
				return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withBody(UUID.randomUUID().toString()).build();
			} else {
				return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR, there is already a reservation or the table does not exist").build();
			}
		} catch (Exception e) {
			System.err.println("Error while persisting reservation " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
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


	record APIRequest(String method, String path, String authorization_header, Map<String, String> body_json) {

	}

	record Table(Number id, Number number, Number places, Boolean isVip, Number minOrder) {

	}

	record Reservation(Number tableNumber, String clientName, String phoneNumber, String date, String slotTimeStart,
					   String slotTimeEnd) {

	}

	record ReservationResponse(List<Reservation> reservations) {

	}

	record TableResponse(List<Table> tables) {

	}

}
