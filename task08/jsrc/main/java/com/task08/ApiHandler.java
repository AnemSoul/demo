package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import org.example.OpenMeteoWeather;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        layers = "sdk-layer",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaLayer(
        layerName = "sdk-layer",
        libraries = {"layer/lib/open-weather-1.0-SNAPSHOT.jar"},
        runtime = DeploymentRuntime.JAVA11,
        artifactExtension = ArtifactExtension.ZIP
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<Object, String> {

    @Override
    public String handleRequest(Object input, Context context) {
        String weatherData = getWeatherData(context);
        context.getLogger().log("Weather Data: " + weatherData);
        return weatherData;
    }

    private String getWeatherData(Context context) {
        OpenMeteoWeather weatherClient = new OpenMeteoWeather();
        try {
            return weatherClient.callApi();
        } catch (Exception e) {
            context.getLogger().log("Failed to retrieve weather data: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
