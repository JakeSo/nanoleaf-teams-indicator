/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.cooldudes.nanoleaf.teams.indicator;

import java.io.*;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Main entry point for the Nanoleaf Teams Indicator application.
 * Handles authentication, device discovery, and event loop.
 * 
 * @author bryson and jake
 */
public class NanoleafTeamsIndicator {

    private static NanoleafShapes shapes;
    private static final String clientState = RandomGenerators.generateRandomString(10);

    private static final String PROPS_FILEPATH = "src/main/resources/nanoleaf.properties";

    /**
     * Main method to start the application.
     * 
     * @param args Command-line arguments (not used)
     */
    public static void main(String[] args) {
        try {
            Graph.initialize(clientState);
            connectToNanoleaf();
            SocketConnection socket = new SocketConnection(clientState, shapes);
            CountDownLatch latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // Clean up resources, disconnect Pusher, etc.
                try {
                    shapes.setPower(false);
                } catch (Exception e) {
                    System.out.println("Could not shutdown device: " + e.getMessage());
                }
                socket.disconnect();
                latch.countDown();
            }));
            latch.await();
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }

    /**
     * Connects to the Nanoleaf device, either by using saved properties or
     * discovering a new device.
     * If no access token is found, it will search for the first available Nanoleaf
     * device.
     * 
     * @throws IOException          if there is an error reading properties or
     *                              connecting to the device
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    private static void connectToNanoleaf() throws IOException, InterruptedException {
        Properties properties = new Properties();
        try {
            FileReader fr = new FileReader(PROPS_FILEPATH);
            properties.load(fr);
        } catch (FileNotFoundException e) {
            new File(PROPS_FILEPATH).createNewFile();
        }
        String ACCESS_TOKEN = properties.getProperty("accessToken");
        String ip = properties.getProperty("ip");
        System.out.println("Connecting to Nanoleaf");
        // If we don't have an access token, try to find the device
        if (ACCESS_TOKEN == null) {
            shapes = NanoleafShapes.findFirst();
        } else {
            // Initialize the device with saved properties
            shapes = new NanoleafShapes(ip, ACCESS_TOKEN);
            try {
                // Test the connection by turning on the device
                shapes.setPower(true);
            } catch (IOException e) {
                // If we cannot connect then look for another one.
                System.out.println("Could not connect to device. Beginning search...");
                shapes = NanoleafShapes.findFirst();
            }
        }
        if (shapes == null) {
            System.exit(0);
        }
    }
}
