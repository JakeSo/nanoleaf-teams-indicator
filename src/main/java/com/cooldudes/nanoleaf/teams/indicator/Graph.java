package com.cooldudes.nanoleaf.teams.indicator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.InteractiveRequestParameters;
import com.microsoft.aad.msal4j.PublicClientApplication;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;


public class Graph {


    public static void initialize(String session) throws IOException, URISyntaxException, ExecutionException, InterruptedException {
        FileReader reader = new FileReader("src/main/resources/oAuth.properties");
        // create properties object
        Properties p = new Properties();
        p.load(reader);

        final String clientId = p.getProperty("client_id");
        final String tenantId = p.getProperty("tenant");
        final String[] scopes = p.getProperty("graphUserScopes").split(",");

        System.out.println("Please log in to Microsoft...");

// Build the PublicClientApplication instance (without scopes)
        PublicClientApplication pca = PublicClientApplication.builder(clientId)
                .authority("https://login.microsoftonline.com/" + tenantId)
                .build();

        // Request authentication interactively
        InteractiveRequestParameters parameters = InteractiveRequestParameters.builder(
                        new URI("http://localhost:8080")) // Redirect URI registered in Entra ID
                .scopes(new HashSet<>(Arrays.asList(scopes)))
                .build();

        IAuthenticationResult result = pca.acquireToken(parameters).get();

        // Print the access token
        System.out.println("Login successful!");
        String userId = getUser(result.accessToken());
        System.out.println(userId);
        createSubscription(result.accessToken(), session, userId);
    }

    public static String getUser(String token) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        // Step 1: Get user details (name, email)
        HttpRequest userRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.microsoft.com/v1.0/me?$select=displayName"))

                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json;odata.metadata=none")
                .GET()
                .build();

        HttpResponse<String> userResponse = client.send(userRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("User Info: " + userResponse.body());

        // Step 2: Get presence status
        HttpRequest presenceRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.microsoft.com/v1.0/me/presence"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json;odata.metadata=none")
                .GET()
                .build();
        HttpResponse<String> presenceResponse = client.send(presenceRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("Presence Info: " + presenceResponse.body());
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(presenceResponse.body());

        return jsonNode.get("id").asText();
    }

    public static void createSubscription(String accessToken, String session, String userId) throws IOException, InterruptedException {
        FileReader reader = new FileReader("src/main/resources/oAuth.properties");
        // create properties object
        Properties p = new Properties();
        p.load(reader);


        HttpClient client = HttpClient.newHttpClient();
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("changeType", "updated");
        requestBody.put("resource", "/communications/presences/"+userId);
        requestBody.put("notificationUrl", p.getProperty("subUrl"));
        requestBody.put("expirationDateTime", ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(10).toString());
        /*.put("encryptionCertificate", "test");
        requestBody.put("encryptionCertificateId", "test");*/
        requestBody.put("clientState", session);

        ObjectMapper objectMapper = new ObjectMapper();
        String requestBodyJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest subscriptionRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.microsoft.com/beta/subscriptions"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(subscriptionRequest, HttpResponse.BodyHandlers.ofString());

        // Print response
        System.out.println("Response Code: " + response.statusCode());
        System.out.println("Response Body: " + response.body());
    }
}
