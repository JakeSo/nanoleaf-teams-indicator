package com.cooldudes.nanoleaf.teams.indicator;

import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.InteractiveRequestParameters;
import com.microsoft.aad.msal4j.PublicClientApplication;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.ExecutionException;


public class Graph {


    public static void initialize() throws IOException, URISyntaxException, ExecutionException, InterruptedException {
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
        getUser(result.accessToken());
    }

    public static void getUser(String token) throws IOException, InterruptedException {
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
    }
}
