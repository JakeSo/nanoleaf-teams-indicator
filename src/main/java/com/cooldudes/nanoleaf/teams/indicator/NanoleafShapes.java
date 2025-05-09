package com.cooldudes.nanoleaf.teams.indicator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class NanoleafShapes {
    private static final int API_PORT = 16021;
    private final String baseUrl;
    private final HttpClient client;

    /***
     * Creates a new NanoleafShapes
     * @param ip IP Address of the Nanoleaf Device
     * @param authToken Authorization token received from Nanoleaf
     */
    public NanoleafShapes(String ip, String authToken) {
        this.baseUrl = String.format("http://%s:%d/api/v1/%s", ip, API_PORT, authToken);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** Pair and generate a new auth token (hold controller power button 5–7 s first) */
    public static String generateAuthToken(String ip) throws IOException, InterruptedException {
        String url = String.format("http://%s:%d/api/v1/new", ip, API_PORT);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        // Response JSON: {"auth_token":"..."}
        return resp.body()
                .replaceAll(".*\"auth_token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private HttpRequest.Builder reqBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json");
    }

    /** Power on/off/toggle */
    public boolean getPower() throws IOException, InterruptedException {
        var resp = client.send(reqBuilder("/state/on").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return resp.body().contains("\"value\":true");
    }

    public void setPower(boolean on) throws IOException, InterruptedException {
        String json = String.format("{\"on\":{\"value\":%b}}", on);
        client.send(reqBuilder("/state/on")
                        .PUT(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.discarding());
    }

    /** Brightness: 0–100 */
    public int getBrightness() throws IOException, InterruptedException {
        var resp = client.send(reqBuilder("/state/brightness").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return Integer.parseInt(resp.body().replaceAll(".*\"value\":(\\d+).*", "$1"));
    }

    public void setBrightness(int level) throws IOException, InterruptedException {
        String json = String.format("{\"brightness\":{\"value\":%d}}", level);
        client.send(reqBuilder("/state/brightness")
                        .PUT(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.discarding());
    }

    /** Set color using RGB; panels expect HSV internally */
    public void setColor(int r, int g, int b) throws IOException, InterruptedException {
        // Simple RGB→HSV conversion
        float[] hsv = java.awt.Color.RGBtoHSB(r, g, b, null);
        int hue = Math.round(hsv[0] * 360);
        int sat = Math.round(hsv[1] * 100);
        int bri = Math.round(hsv[2] * 100);
        String json = String.format(
                "{\"hue\":{\"value\":%d},\"sat\":{\"value\":%d},\"brightness\":{\"value\":%d}}",
                hue, sat, bri
        );
        client.send(reqBuilder("/state/color")
                        .PUT(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.discarding());
    }

    /** List available effects (scenes) */
    public String listEffects() throws IOException, InterruptedException {
        var resp = client.send(reqBuilder("/effects").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return resp.body(); // JSON array of effect names
    }

    /** Activate a named effect */
    public void setEffect(String effectName) throws IOException, InterruptedException {
        String json = String.format("{\"select\":{\"value\":\"%s\"}}", effectName);
        client.send(reqBuilder("/effects").PUT(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.discarding());
    }

    public void displayEffect(NanoleafEffect effect) throws IOException, InterruptedException{
        String json ="{\"write\": " + effect.toString() + "}" ;
        client.send(reqBuilder("/effects").PUT(HttpRequest.BodyPublishers.ofString(json)).build(),
            HttpResponse.BodyHandlers.discarding());
    }


}
