package com.cooldudes.nanoleaf.teams.indicator;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

/**
 * Controls a Nanoleaf Shapes device and updates its effects based on Teams
 * presence.
 */
public class NanoleafShapes implements StatusChangeHandler {
    private static final String CONFIG_DIR = System.getProperty("user.dir") + File.separator + "resources" + File.separator;
    private static final String PROPS_PATH = CONFIG_DIR + "nanoleaf.properties";
    private static final String SERVICE_TYPE = "_nanoleafapi._tcp.local.";
    private static final int API_PORT = 16021;
    private String baseUrl;
    private final HttpClient client;
    public static Map<String, NanoleafEffect.PaletteColor[]> STATUS_PALETTES = Map.ofEntries(
            Map.entry("Available", new NanoleafEffect.PaletteColor[] {
                    new NanoleafEffect.PaletteColor(100, 100, 100, 70),
                    new NanoleafEffect.PaletteColor(100, 75, 100, 30),
            }),
            Map.entry("Busy", new NanoleafEffect.PaletteColor[] {
                    new NanoleafEffect.PaletteColor(0, 100, 100, 50),
                    new NanoleafEffect.PaletteColor(0, 100, 70, 50)
            }),
            Map.entry("Away", new NanoleafEffect.PaletteColor[] {
                    new NanoleafEffect.PaletteColor(45, 80, 100, 10),
                    new NanoleafEffect.PaletteColor(40, 100, 100, 90)
            }),
            Map.entry("OutOfOffice", new NanoleafEffect.PaletteColor[] {
                    new NanoleafEffect.PaletteColor(282, 100, 10, 20),
                    new NanoleafEffect.PaletteColor(0, 0, 0, 80)
            }));

    /***
     * Creates a new NanoleafShapes
     *
     * @param ip        IP Address of the Nanoleaf Device
     * @param authToken Authorization token received from Nanoleaf
     */
    public NanoleafShapes(String ip, String authToken) {
        if (authToken == null || authToken.isEmpty()) {
            authToken = generateAuthToken(ip);
            writePropsToFile(ip, authToken);
        }
        this.baseUrl = String.format("http://%s:%d/api/v1/%s", ip, API_PORT, authToken);
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public static NanoleafShapes fromPropertiesFile() {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(PROPS_PATH)) {
            props.load(in);
            String ip = props.getProperty("ip");
            String token = props.getProperty("accessToken");
            if (ip != null && token != null) {
                return new NanoleafShapes(ip, token);
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read properties file", e);
        }
    }

    private void writePropsToFile(String ip, String accessToken) {
        Properties props = new Properties();
        props.setProperty("ip", ip);
        props.setProperty("accessToken", accessToken);
        try (FileOutputStream out = new FileOutputStream(PROPS_PATH)) {
            props.store(out, null);
        } catch (IOException e) {
            System.err.println("Could not write Nanoleaf props to file");
        }
    }

    /**
     * Pair and generate a new auth token (hold controller power button 5–7 s first)
     */
    public static String generateAuthToken(String ip) {
        System.out.println("Hold power button until lights flash, then hit Enter.");
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
        scanner.close();
        String url = String.format("http://%s:%d/api/v1/new", ip, API_PORT);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()).build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body().replaceAll(".*\"auth_token\"\s*:\s*\"([^\"]+)\".*", "$1");
        } catch (Exception e) {
            throw new RuntimeException("Auth request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Looks for devices and returns the first one. Gives the user 3 chances to
     * search.
     *
     * @return the device if found, null otherwise
     */
    public static NanoleafShapes findDevice() {
        int retries = 0;
        Scanner scanner = new Scanner(System.in);
        while (retries < 3) {
            System.out.println("Ensure Nanoleaf is on and connected, then hit Enter.");
            scanner.nextLine();
            String ip = findDeviceIPByUserSelection();
            if (ip != null) return new NanoleafShapes(ip, null);
            System.out.println("Device not found. Retry.");
            retries++;
        }
        System.out.println("Max retries exceeded.");
        return null;
    }

    private static String findDeviceIPByUserSelection() {
        try (JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost())) {
            ServiceInfo[] services = jmdns.list(SERVICE_TYPE, 5000);
            if (services.length == 0) {
                System.out.println("No services found.");
                return null;
            }

            System.out.println("Found devices:");
            for (int i = 0; i < services.length; i++) {
                System.out.printf("[%d] %s (%s)%n", i, services[i].getName(), services[i].getHostAddresses()[0]);
            }

            System.out.print("Select a device by number: ");
            Scanner scanner = new Scanner(System.in);
            int choice = scanner.nextInt();
            scanner.close();

            if (choice >= 0 && choice < services.length) {
                return services[choice].getHostAddresses()[0];
            } else {
                System.out.println("Invalid choice.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
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

    /**
     * Sets the power state of the device.
     *
     * @param on true to power on, false to power off
     * @throws IOException          if network error occurs
     * @throws InterruptedException if interrupted
     */
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
                hue, sat, bri);
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
            if (!(userPresence.availability.equals("Offline"))) {
                NanoleafEffect.PaletteColor[] palette = getPaletteColors(userPresence);
                NanoleafEffect effect = new NanoleafEffect(palette);
                displayEffect(effect);
            } else {
                setPower(false);
            }
        } catch (IOException e) {
            NanoleafShapes newNano = findDevice();
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
     *
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
