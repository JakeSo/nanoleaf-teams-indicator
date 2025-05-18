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
import java.util.*;
import java.util.concurrent.ExecutionException;


public class Graph {

private static String subscriptionId;

    public static void initialize(String session) throws IOException, URISyntaxException, ExecutionException, InterruptedException {
        FileReader reader = new FileReader("src/main/resources/oAuth.properties");
        // create properties object
        Properties p = new Properties();
        p.load(reader);

        final String clientId = p.getProperty("client_id");
        final String tenantId = p.getProperty("tenant");
        final String[] scopes = p.getProperty("graphUserScopes").split(",");
        String accessToken = p.getProperty("accessToken");
        String userId = p.getProperty("userId");
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
            System.out.println(result);
            userId = result.account().homeAccountId().split("\\.")[0];
            p.setProperty("accessToken",accessToken);
            p.setProperty("userId",userId);
            p.store(new FileOutputStream("src/main/resources/oAuth.properties"), null);

        }
        // Print the access token
        System.out.println("Login successful!");
        createSubscription(accessToken, session, userId);
    }

    public static void createSubscription(String accessToken, String session, String userId) throws IOException {
        FileReader reader = new FileReader("src/main/resources/oAuth.properties");
        // create properties object
        Properties p = new Properties();
        p.load(reader);


        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("changeType", "updated");
            requestBody.put("resource", "/communications/presences/" + userId);
            requestBody.put("notificationUrl", p.getProperty("subUrl"));
            requestBody.put("lifecycleNotificationUrl",p.getProperty("lifecycleUrl"));
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
                p.setProperty("subscriptionId", (((JSONObject) JSONUtils.parseJSON(response.body())).getAsString("id")));
                p.store(new FileOutputStream("src/main/resources/oAuth.properties"), null);
                System.out.println("Successfully subscribed!");
            } else if (response.statusCode() == 409) {
                updateSubscription(client, accessToken, session, p);
            } else {
                System.err.println(response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Print response
        System.out.println("Response Code: " + response.statusCode());
        System.out.println("Response Body: " + response.body());
    }


    private static void updateSubscription(HttpClient client, String token, String session, Properties p) throws IOException, InterruptedException {
        String id = p.getProperty("subscriptionId");
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("notificationUrl", p.getProperty("subUrl"));
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
}
