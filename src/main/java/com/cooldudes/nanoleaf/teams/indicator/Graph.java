package com.cooldudes.nanoleaf.teams.indicator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.InteractiveRequestParameters;
import com.microsoft.aad.msal4j.PublicClientApplication;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            userId = result.account().homeAccountId().split("\\.")[0];
            p.setProperty("accessToken",accessToken);
            p.setProperty("userId",userId);
            p.store(new FileOutputStream("src/main/resources/oAuth.properties"), null);

        }
        // Print the access token
        System.out.println("Login successful!");

        System.out.println(userId);
        createSubscription(accessToken, session, userId);
    }

    public static String getUser(String token) throws IOException, InterruptedException {
        HttpResponse<String> presenceResponse;
        try (HttpClient client = HttpClient.newHttpClient()) {

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
            presenceResponse = client.send(presenceRequest, HttpResponse.BodyHandlers.ofString());
        }
        System.out.println("Presence Info: " + presenceResponse.body());
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(presenceResponse.body());

        return jsonNode.get("id").asText();
    }

    public static void createSubscription(String accessToken, String session, String userId) throws IOException {
        FileReader reader = new FileReader("src/main/resources/oAuth.properties");
        // create properties object
        Properties p = new Properties();
        p.load(reader);

        /*String certificate = Files.readString(Path.of("src/main/resources/cert.pem"), StandardCharsets.UTF_8)
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .trim();
        String base64Certificate = Base64.getEncoder().encodeToString(certificate.getBytes(StandardCharsets.UTF_8));
        System.out.println(base64Certificate);*/

        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("changeType", "updated");
            requestBody.put("resource", "/communications/presences/" + userId);
            requestBody.put("notificationUrl", p.getProperty("subUrl"));
            requestBody.put("includeResourceData", true);
            requestBody.put("expirationDateTime", ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(10).toString());
            requestBody.put("encryptionCertificate",  CertificateUtil.getBase64EncodedCertificate("src/main/resources/certificate.pem"));
            requestBody.put("encryptionCertificateId", "jake");
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
                System.out.println("Successfully subscribed!");
            } else if (response.statusCode() == 409) {
                updateSubscription(client, p.getProperty("subUrl"), userId, accessToken, requestBodyJson);
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


    private static void updateSubscription(HttpClient client,String url, String id, String token, String body) throws IOException, InterruptedException {

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("notificationUrl",url);
        HttpRequest subscriptionRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.microsoft.com/v1.0/subscriptions/" + id))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(subscriptionRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() > 300) {
            System.err.println(response.body());
        }
    }
}
