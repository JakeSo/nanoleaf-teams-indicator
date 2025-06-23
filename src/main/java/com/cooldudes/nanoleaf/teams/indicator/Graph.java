package com.cooldudes.nanoleaf.teams.indicator;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    private static final String CONFIG_DIR = System.getProperty("user.dir") + File.separator + "resources" + File.separator;
    private static final String GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0";
    private static final String OAUTH_PROPS_PATH = CONFIG_DIR + "oAuth.properties";
    private static final String SUBSCRIPTION_PROPS_PATH = CONFIG_DIR + "subscription.properties";
    private static Properties oauthProps;
    private static Properties subscriptionProps;
    private static IAccount account;
    private static PublicClientApplication app;
    private static String accessToken;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy");

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
     * @throws SubscriptionException 
     */
    public static void initialize(String session) throws IOException, URISyntaxException, SubscriptionException {

        buildOAuthReader();
        buildSubscriptionReader();
        final String clientId = oauthProps.getProperty("client_id");
        final String tenantId = oauthProps.getProperty("tenant");
        final String[] scopes = oauthProps.getProperty("graphUserScopes").split(",");
        System.out.println("Please log in to Microsoft...");
        // Build the PublicClientApplication instance (without scopes)
        app = PublicClientApplication.builder(clientId)
                .authority("https://login.microsoftonline.com/" + tenantId)
                .build();

        IAuthenticationResult result = requestUserLogin(scopes);
        accessToken = result.accessToken();
        account = result.account();
        String userId = account.homeAccountId().split("\\.")[0];
        subscriptionProps.setProperty("userId", userId);
        subscriptionProps.store(new FileOutputStream(SUBSCRIPTION_PROPS_PATH), null);
        System.out.println("Login successful!");
        createSubscription(session, userId);
    }

    private static IAuthenticationResult requestUserLogin(final String[] scopes) throws URISyntaxException {
        // Request authentication interactively
        InteractiveRequestParameters parameters = InteractiveRequestParameters.builder(
                new URI("http://localhost:8080")) // Redirect URI registered in Entra ID
                .scopes(new HashSet<>(Arrays.asList(scopes)))
                .build();

        IAuthenticationResult result = app.acquireToken(parameters).join();
        return result;
    }

    /**
     * Creates a new Microsoft Graph subscription for presence updates.
     * @param session     Unique session/client state string
     * @param userId      Microsoft user ID
     * @throws SubscriptionException if there is an error during subscription creation
     */
    public static void createSubscription(String session, String userId) throws SubscriptionException {

        HttpResponse<String> response;
        HttpClient client = HttpClient.newHttpClient();
        try {
            String requestBodyJson = getCreateSubRequestBodyJson(session, userId);
            HttpRequest subscriptionRequest = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPH_BASE_URL + "/subscriptions"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8))
                    .build();
            response = client.send(subscriptionRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 300) {
                String id = ((JSONObject) JSONUtils.parseJSON(response.body())).getAsString("id");
                String expiration = ((JSONObject) JSONUtils.parseJSON(response.body()))
                        .getAsString("expirationDateTime");
                subscriptionProps.setProperty("subscriptionId", id);
                subscriptionProps.setProperty("expirationDateTime", expiration);
                subscriptionProps.store(new FileOutputStream(SUBSCRIPTION_PROPS_PATH), null);
                System.out.println("Successfully subscribed!");
            } else if (response.statusCode() == 409) {
                deleteSubscription();
                createSubscription(session, userId);
            } else if (response.statusCode() == 401) {
                getNewToken();
                createSubscription(session, userId);   
            } else {
                System.err.println(response.body());
                throw new RuntimeException(response.body());

            }
        } catch (Exception e) {
            throw new SubscriptionException("Error creating subscription: " + e.getMessage(), e);
        }

        // Print response
        System.out.println("Response Code: " + response.statusCode());
        System.out.println("Response Body: " + response.body());
    }

    private static String getCreateSubRequestBodyJson(String session, String userId) throws Exception, JsonProcessingException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("changeType", "updated");
        requestBody.put("resource", "/communications/presences/" + userId);
        requestBody.put("notificationUrl", oauthProps.getProperty("subUrl"));
        requestBody.put("lifecycleNotificationUrl", oauthProps.getProperty("lifecycleUrl"));
        requestBody.put("includeResourceData", true);
        requestBody.put("expirationDateTime", ZonedDateTime.now(ZoneOffset.UTC).plusHours(1).toString());
        requestBody.put("encryptionCertificate",
                CertificateUtil.getBase64EncodedCertificate("src/main/resources/public-cert.pem"));
        requestBody.put("encryptionCertificateId", "nano");
        requestBody.put("clientState", session);

        ObjectMapper objectMapper = new ObjectMapper();
        String requestBodyJson = objectMapper.writeValueAsString(requestBody);
        return requestBodyJson;
    }

    private static void deleteSubscription() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        String id = subscriptionProps.getProperty("subscriptionId");
        if (id != null && !id.isBlank()) {
            HttpRequest subscriptionRequest = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPH_BASE_URL + "/subscriptions/" + id))
                    .header("Authorization", "Bearer " + accessToken)
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
     * @throws SubscriptionException if there is an error during the update process
     */
    static void updateSubscription() throws SubscriptionException {
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
                String expiration = ((JSONObject) JSONUtils.parseJSON(response.body()))
                        .getAsString("expirationDateTime");
                subscriptionProps.setProperty("expirationDateTime", expiration);
                subscriptionProps.store(new FileOutputStream(SUBSCRIPTION_PROPS_PATH), null);
            } else if (response.statusCode() == 401) {
                System.out.println("Token expired");
                getNewToken();
                updateSubscription();
            } else {
                System.out.println(response.body());
                throw new RuntimeException("Reauthorization failed with status" + response.statusCode());
            }
        } catch (Exception e) {
            throw new SubscriptionException("Exception while updating subscription: " + e.getMessage(), e);
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
            accessToken = result.accessToken();
        } catch (Exception e) {
            System.out.println("Silent token acquisition failed: " + e.getMessage());
            try {
                // Fallback to interactive login if silent acquisition fails
                IAuthenticationResult result = requestUserLogin(scopes);
                accessToken = result.accessToken();
                account = result.account();
            } catch (Exception ex) {
                throw new RuntimeException("Failed to acquire new token interactively: " + ex.getMessage(), ex);
            }
        }

    }

    /**
     * Ensures there is an active subscription, creating a new one if necessary.
     * @throws SubscriptionException if there is an error during subscription management
     */
    public static synchronized void ensureActiveSubscription() throws SubscriptionException {
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
            createSubscription(session, userId);
        } else {
            updateSubscription();
        }
    }
}

class SubscriptionException extends Exception {
    public SubscriptionException(String message, Throwable cause) {
        super(message,cause);
    }
}
