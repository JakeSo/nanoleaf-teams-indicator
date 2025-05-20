package com.cooldudes.nanoleaf.teams.indicator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.InteractiveRequestParameters;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.nimbusds.oauth2.sdk.util.JSONUtils;
import net.minidev.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;


public class Graph {


private static Properties oauthProps;
private static Properties subscriptionProps;

    private static void buildOAuthReader(){
        try {
            FileReader reader = new FileReader("src/main/resources/oAuth.properties");
            // create properties object
            oauthProps = new Properties();
            oauthProps.load(reader);
        }catch (Exception e){

        }
    }
    private static void buildSubscriptionReader(){
        try {
            FileReader reader = new FileReader("src/main/resources/subscription.properties");
            // create properties object
            subscriptionProps = new Properties();
            subscriptionProps.load(reader);
        }catch (Exception e){

        }
    }

    public static void initialize(String session) throws IOException, URISyntaxException, ExecutionException, InterruptedException {

        buildOAuthReader(); buildSubscriptionReader();
        compareTime();
        final String clientId = oauthProps.getProperty("client_id");
        final String tenantId = oauthProps.getProperty("tenant");
        final String[] scopes = oauthProps.getProperty("graphUserScopes").split(",");
        String accessToken = subscriptionProps.getProperty("accessToken");
        String userId = subscriptionProps.getProperty("userId");
        if (accessToken == null) {
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
            accessToken = result.accessToken();
            userId = result.account().homeAccountId().split("\\.")[0];
            subscriptionProps.setProperty("accessToken",accessToken);
            subscriptionProps.setProperty("accessTokenExp",result.expiresOnDate().toString());
            subscriptionProps.setProperty("userId",userId);
            subscriptionProps.store(new FileOutputStream("src/main/resources/subscription.properties"), null);

        }
        System.out.println("Login successful!");
        createSubscription(accessToken, session, userId);
    }

    public static void createSubscription(String accessToken, String session, String userId) throws IOException {

        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("changeType", "updated");
            requestBody.put("resource", "/communications/presences/" + userId);
            requestBody.put("notificationUrl", oauthProps.getProperty("subUrl"));
            requestBody.put("lifecycleNotificationUrl",oauthProps.getProperty("lifecycleUrl"));
            requestBody.put("includeResourceData", true);
            requestBody.put("expirationDateTime", ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(2).toString());
            requestBody.put("encryptionCertificate",  CertificateUtil.getBase64EncodedCertificate("src/main/resources/public-cert.pem"));
            requestBody.put("encryptionCertificateId", "nano");
            requestBody.put("clientState", session);

            ObjectMapper objectMapper = new ObjectMapper();
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest subscriptionRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.microsoft.com/v1.0/subscriptions"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8))
                    .build();
            response = client.send(subscriptionRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 300) {
                //System.out.println(((JSONObject) JSONUtils.parseJSON(response.body())).getAsString("id"));
                subscriptionProps.setProperty("subscriptionId", (((JSONObject) JSONUtils.parseJSON(response.body())).getAsString("id")));
                subscriptionProps.store(new FileOutputStream("src/main/resources/subscription.properties"), null);
                System.out.println("Successfully subscribed!");
            } else if (response.statusCode() == 409) {
                updateSubscription(client, accessToken, session);
            } else {
                System.err.println(response.body());

                throw new SubscriptionException("Failed to subscribe: " + response.body());

            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Print response
        System.out.println("Response Code: " + response.statusCode());
        System.out.println("Response Body: " + response.body());
    }


    private static void updateSubscription(HttpClient client, String token, String session) throws IOException, InterruptedException {
        String id = subscriptionProps.getProperty("subscriptionId");
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("notificationUrl", oauthProps.getProperty("subUrl"));
        updateBody.put("clientState", session);
        String body = new ObjectMapper().writeValueAsString(updateBody);
        HttpRequest subscriptionRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.microsoft.com/v1.0/subscriptions/" + id))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(subscriptionRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() > 300) {
            System.err.println(response.body());
        }
    }
    private static void compareTime() throws IOException {
        if (subscriptionProps.getProperty("accessTokenExp") != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy");

            ZonedDateTime zonedDateTime = ZonedDateTime.parse(ZonedDateTime.now().toString());
            ZonedDateTime storedDate = ZonedDateTime.parse(subscriptionProps.getProperty("accessTokenExp"), formatter);
            //Clear the subscription properties if the access token is overdue
            if (zonedDateTime.isAfter(storedDate)) {
                subscriptionProps.clear();
                subscriptionProps.store(new FileOutputStream("src/main/resources/subscription.properties"), null);
                System.out.println("Properties file cleared successfully.");

            }
        }
    }
}

class SubscriptionException extends Exception {
    public SubscriptionException(String message) {
        super(message);
    }
}
