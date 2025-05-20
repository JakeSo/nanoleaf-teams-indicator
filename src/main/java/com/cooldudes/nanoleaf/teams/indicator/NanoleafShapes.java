package com.cooldudes.nanoleaf.teams.indicator;

import io.github.rowak.nanoleafapi.NanoleafException;
import io.github.rowak.nanoleafapi.util.NanoleafDeviceMeta;
import io.github.rowak.nanoleafapi.util.NanoleafSetup;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public class NanoleafShapes implements StatusChangeHandler {
    private static final int API_PORT = 16021;
    private String baseUrl;
    private final HttpClient client;
    public static Map<String, NanoleafEffect.PaletteColor[]> STATUS_PALETTES = Map.ofEntries(
            Map.entry("Available", new NanoleafEffect.PaletteColor[]{
                    new NanoleafEffect.PaletteColor(100, 100, 100, 70),
                    new NanoleafEffect.PaletteColor(100, 75, 100, 30),
            }),
            Map.entry("Busy", new NanoleafEffect.PaletteColor[]{
                    new NanoleafEffect.PaletteColor(0, 100, 100, 50),
                    new NanoleafEffect.PaletteColor(0, 100, 70, 50)
            }),
            Map.entry("Away", new NanoleafEffect.PaletteColor[]{
                    new NanoleafEffect.PaletteColor(45, 80, 100, 10),
                    new NanoleafEffect.PaletteColor(40, 100, 100, 90)
            }),
            Map.entry("OutOfOffice", new NanoleafEffect.PaletteColor[]{
                    new NanoleafEffect.PaletteColor(282, 100, 10, 20),
                    new NanoleafEffect.PaletteColor(0, 0, 0, 80)
            })
    );
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

    /**
     * Pair and generate a new auth token (hold controller power button 5–7 s first)
     */
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

    /**
     * Looks for devices and returns the first one. Gives the user 3 chances to search.
     * @return the device if found, null otherwise
     */
    public static NanoleafShapes findFirst() {
        Properties properties = new Properties();
        Scanner scanner = new Scanner(System.in);
        try {
            NanoleafDeviceMeta ourNano = null;
            int retries = 0;
            while (ourNano == null) {
                System.out.println("Please hold the power button on the Nanoleaf until the lights start flashing, then hit Enter");
                scanner.nextLine();
                List<NanoleafDeviceMeta> devices = NanoleafSetup.findNanoleafDevices(5000);
                if (devices.isEmpty()) {
                    retries++;
                    if (retries > 2) {
                        System.out.println("Exceeded acceptable amount of retries.");
                        return null;
                    }
                    System.out.println("Could not find Nanoleaf. Please try again.");
                } else {
                    ourNano = devices.getFirst();
                }
            }
                String ip = ourNano.getDeviceId();
                properties.setProperty("ip", ip);
                String accessToken = NanoleafSetup.createAccessToken(ip, ourNano.getPort());
                properties.setProperty("accessToken", accessToken);
                properties.store(new FileOutputStream("src/main/resources/nanoleaf.properties"), null);
                return new NanoleafShapes(ip, accessToken);
        } catch (NanoleafException | IOException e) {
            throw new RuntimeException("Error searching for device: " + e.getMessage(), e);
        }
    }

    private HttpRequest.Builder reqBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json");
    }

    /**
     * Power on/off/toggle
     */
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

    /**
     * Brightness: 0–100
     */
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

    /**
     * Set color using RGB; panels expect HSV internally
     */
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

    /**
     * List available effects (scenes)
     */
    public String listEffects() throws IOException, InterruptedException {
        var resp = client.send(reqBuilder("/effects").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return resp.body(); // JSON array of effect names
    }

    /**
     * Activate a named effect
     */
    public void setEffect(String effectName) throws IOException, InterruptedException {
        String json = String.format("{\"select\":{\"value\":\"%s\"}}", effectName);
        client.send(reqBuilder("/effects").PUT(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.discarding());
    }

    public void displayEffect(NanoleafEffect effect) throws IOException, InterruptedException {
        String json = "{\"write\": " + effect.toString() + "}";
        client.send(reqBuilder("/effects").PUT(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.discarding());
    }


    /**
     * Set Nanoleaf effect to match availability color
     *
     * @param userPresence the user's current Teams presence
     */
    @Override
    public void handleStatusChange(Presence userPresence) {

        try {
            if (!(userPresence.availability.equals("Offline") && userPresence.activity.equals("OutOfOffice"))) {
                NanoleafEffect.PaletteColor[] palette = getPaletteColors(userPresence);
                NanoleafEffect effect = new NanoleafEffect(palette);
                displayEffect(effect);
            } else {
                setPower(false);
            }
        } catch (IOException e) {
            NanoleafShapes newNano = findFirst();
            if (newNano == null) {
                throw new RuntimeException("Lost connection to device");
            }
            this.baseUrl = newNano.baseUrl;
            handleStatusChange(userPresence);
        } catch (InterruptedException e) {
            throw new RuntimeException("Error updating effect: " + e.getMessage(), e);
        }
    }


    /**
     * Gets the defined effect palette colors for a given Presence.
     * @param userPresence a Presence to retrieve the color(s) for
     * @return an array containing 1 or more colors
     * @see com.cooldudes.nanoleaf.teams.indicator.NanoleafEffect.PaletteColor
     * @see Presence
     */
    private static NanoleafEffect.PaletteColor[] getPaletteColors(Presence userPresence) {
        NanoleafEffect.PaletteColor[] palette;
        if (userPresence.availability.equals("Busy") || userPresence.availability.equals("DoNotDisturb")) {
            palette = STATUS_PALETTES.get("Busy");
        } else if (userPresence.availability.equals("Away") || userPresence.availability.equals("BeRightBack")) {
            palette = STATUS_PALETTES.get("Away");
        } else if (userPresence.activity.equals("OutOfOffice")) {
            palette = STATUS_PALETTES.get("OutOfOffice");
        } else {
            palette = STATUS_PALETTES.get("Available");
        }
        return palette;
    }
}
