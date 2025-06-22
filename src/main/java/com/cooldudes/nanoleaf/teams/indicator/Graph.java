package com.cooldudes.nanoleaf.teams.indicator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.msal4j.*;
import com.nimbusds.oauth2.sdk.util.JSONUtils;
import net.minidev.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.*;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Handles Microsoft Graph API authentication, subscription management, and
 * token refresh.
 */
public class Graph {

    private static final String GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0";
    private static final String OAUTH_PROPS_PATH = "src/main/resources/oAuth.properties";
    private static final String SUBSCRIPTION_PROPS_PATH = "src/main/resources/subscription.properties";
    private static Properties oauthProps;
    private static Properties subscriptionProps;
    private static IAccount account;
    private static PublicClientApplication app;

    private static void buildOAuthReader() {
        try (FileReader reader = new FileReader(OAUTH_PROPS_PATH)) {
            // create properties object
            oauthProps = new Properties();
            oauthProps.load(reader);
        } catch (Exception e) {
            System.err.println("Error reading oAuth properties file: " + e.getMessage());
            System.err.println("Please ensure the file exists and is correctly formatted.");
            throw new RuntimeException("Failed to load OAuth properties", e);
        }
    }

    private static void buildSubscriptionReader() {
        try (FileReader reader = new FileReader(SUBSCRIPTION_PROPS_PATH)) {
            // create properties object
            subscriptionProps = new Properties();
            subscriptionProps.load(reader);
        } catch (Exception e) {
            System.out.println("No subscription properties file found.");

        }
    }

    /**
     * Initializes authentication and creates a Graph subscription.
     * 
     * @param session Unique session/client state string
     * @throws IOException        if properties cannot be read or written
     * @throws URISyntaxException if the redirect URI is invalid
     */
    public static void initialize(String session) throws IOException, URISyntaxException {

        buildOAuthReader();
        buildSubscriptionReader();
        checkAccessTokenExpiry();
        final String clientId = oauthProps.getProperty("client_id");
        final String tenantId = oauthProps.getProperty("tenant");
        final String[] scopes = oauthProps.getProperty("graphUserScopes").split(",");
        System.out.println("Please log in to Microsoft...");
        // Build the PublicClientApplication instance (without scopes)
        app = PublicClientApplication.builder(clientId)
                .authority("https://login.microsoftonline.com/" + tenantId)
                .build();

        // Request authentication interactively
        InteractiveRequestParameters parameters = InteractiveRequestParameters.builder(
                new URI("http://localhost:8080")) // Redirect URI registered in Entra ID
                .scopes(new HashSet<>(Arrays.asList(scopes)))
                .build();

        IAuthenticationResult result = app.acquireToken(parameters).join();
        String accessToken = result.accessToken();
        account = result.account();
        String userId = account.homeAccountId().split("\\.")[0];
        subscriptionProps.setProperty("accessToken", accessToken);
        subscriptionProps.setProperty("accessTokenExp", result.expiresOnDate().toString());
        subscriptionProps.setProperty("userId", userId);
        subscriptionProps.store(new FileOutputStream(SUBSCRIPTION_PROPS_PATH), null);
        System.out.println("Login successful!");
        createSubscription(accessToken, session, userId);
    }

    /**
     * Creates a new Microsoft Graph subscription for presence updates.
     * 
     * @param accessToken OAuth access token
     * @param session     Unique session/client state string
     * @param userId      Microsoft user ID
     */
    public static void createSubscription(String accessToken, String session, String userId) {

        HttpResponse<String> response;
        HttpClient client = HttpClient.newHttpClient();
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("changeType", "updated");
            requestBody.put("resource", "/communications/presences/" + userId);
            requestBody.put("notificationUrl", oauthProps.getProperty("subUrl"));
            requestBody.put("lifecycleNotificationUrl", oauthProps.getProperty("lifecycleUrl"));
            requestBody.put("includeResourceData", true);
            requestBody.put("expirationDateTime", ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(45).toString());
            requestBody.put("encryptionCertificate",
                    CertificateUtil.getBase64EncodedCertificate("src/main/resources/public-cert.pem"));
            requestBody.put("encryptionCertificateId", "nano");
            requestBody.put("clientState", session);

            ObjectMapper objectMapper = new ObjectMapper();
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest subscriptionRequest = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPH_BASE_URL + "/subscriptions"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8))
                    .build();
            response = client.send(subscriptionRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 300) {
                // System.out.println(((JSONObject)
                // JSONUtils.parseJSON(response.body())).getAsString("id"));
                subscriptionProps.setProperty("subscriptionId",
                        (((JSONObject) JSONUtils.parseJSON(response.body())).getAsString("id")));
                subscriptionProps.store(new FileOutputStream(SUBSCRIPTION_PROPS_PATH), null);
                System.out.println("Successfully subscribed!");
            } else if (response.statusCode() == 409) {
                deleteSubscription(client, accessToken);
                createSubscription(accessToken, session, userId);
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

    private static void deleteSubscription(HttpClient client, String token) throws IOException, InterruptedException {
        String id = subscriptionProps.getProperty("subscriptionId");
        if (id != null && !id.isBlank()) {
            HttpRequest subscriptionRequest = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPH_BASE_URL + "/subscriptions/" + id))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .DELETE()
                    .build();
            HttpResponse<String> response = client.send(subscriptionRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() > 300) {
                System.err.println(response.body());
            }
        }
    }

    /**
     * Updates the current subscription's expiration time.
     */
    static void updateSubscription() {
        String id = subscriptionProps.getProperty("subscriptionId");
        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpRequest subscriptionRequest = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPH_BASE_URL + "/subscriptions/" + id))
                    .header("Authorization", "Bearer " + subscriptionProps.getProperty("accessToken"))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .method("PATCH",
                            HttpRequest.BodyPublishers.ofString(String.format("{ \"expirationDateTime\": \"%s\" }",
                                    ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(45))))
                    .build();
            HttpResponse<String> response = client.send(subscriptionRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 300) {
                System.out.println("Reauthorization succeeded!");
            } else if (response.statusCode() == 401) {
                System.out.println("Token expired");
                getNewToken();
                updateSubscription();
            } else {
                System.out.println(response.body());
                throw new RuntimeException("Reauthorization failed with status" + response.statusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception while updating subscription: " + e.getMessage(), e);
        }
    }

    /**
     * Attempts to silently acquire a new access token.
     */
    static void getNewToken() {
        final String[] scopes = oauthProps.getProperty("graphUserScopes").split(",");
        try {
            SilentParameters parameters = SilentParameters.builder(new HashSet<>(Arrays.asList(scopes)))
                    .account(account)
                    .build();

            IAuthenticationResult result = app.acquireTokenSilently(parameters).join();
            subscriptionProps.setProperty("accessToken", result.accessToken());
            subscriptionProps.setProperty("accessTokenExp", result.expiresOnDate().toString());
            subscriptionProps.store(new FileOutputStream(SUBSCRIPTION_PROPS_PATH), null);

        } catch (Exception e) {
            throw new RuntimeException("Exception when getting new token: " + e.getMessage(), e);
        }

    }

    private static void checkAccessTokenExpiry() throws IOException {
        if (subscriptionProps.getProperty("accessTokenExp") != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy");

            ZonedDateTime zonedDateTime = ZonedDateTime.parse(ZonedDateTime.now().toString());
            ZonedDateTime storedDate = ZonedDateTime.parse(subscriptionProps.getProperty("accessTokenExp"), formatter);
            // Clear the subscription properties if the access token is overdue
            if (zonedDateTime.isAfter(storedDate)) {
                subscriptionProps.clear();
                subscriptionProps.store(new FileOutputStream(SUBSCRIPTION_PROPS_PATH), null);
                System.out.println("Properties file cleared successfully.");

            }
        }
    }

    /**
     * Ensures there is an active subscription, creating a new one if necessary.
     */
    public static synchronized void ensureActiveSubscription() {
        buildSubscriptionReader();
        String expStr = subscriptionProps.getProperty("expirationDateTime");
        String accessToken = subscriptionProps.getProperty("accessToken");
        String userId = subscriptionProps.getProperty("userId");
        String session = subscriptionProps.getProperty("clientState");
        boolean needsNew = false;

        if (expStr == null || accessToken == null || userId == null || session == null) {
            needsNew = true;
        } else {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy");
                ZonedDateTime exp = ZonedDateTime.parse(expStr, formatter);
                if (ZonedDateTime.now(ZoneOffset.UTC).isAfter(exp.minusMinutes(5))) {
                    needsNew = true;
                }
            } catch (Exception e) {
                needsNew = true;
            }
        }

        if (needsNew) {
            System.out.println("Subscription missing or expired. Creating new subscription...");
            createSubscription(accessToken, session, userId);
        } else {
            updateSubscription();
        }
    }
}

class SubscriptionException extends Exception {
    public SubscriptionException(String message) {
        super(message);
    }
}
