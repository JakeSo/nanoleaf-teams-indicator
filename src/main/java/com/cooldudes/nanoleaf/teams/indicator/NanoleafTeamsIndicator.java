/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.cooldudes.nanoleaf.teams.indicator;

import java.io.*;
import java.util.concurrent.CountDownLatch;

/**
 * Main entry point for the Nanoleaf Teams Indicator application.
 * Handles authentication, device discovery, and event loop.
 * 
 * @author bryson and jake
 */
public class NanoleafTeamsIndicator {

    private static NanoleafShapes shapes;

    /**
     * Main method to start the application.
     * 
     * @param args Command-line arguments (not used)
     */
    public static void main(String[] args) {
        try {

            String clientState = Graph.initialize();
            if (clientState == null) clientState = RandomGenerators.generateRandomString(7);
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
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    private static void connectToNanoleaf() throws InterruptedException {
        shapes = NanoleafShapes.fromPropertiesFile();
        if (shapes != null) {
            try {
                // Test the connection by turning on the device
                shapes.setPower(true);
            } catch (IOException e) {
                // If we cannot connect then look for another one.
                System.out.println("Could not connect to device. Beginning search...");
                shapes = NanoleafShapes.findDevice();
            }
        } else {
            shapes = NanoleafShapes.findDevice();
        }
        if (shapes == null) {
            System.exit(0);
        }
    }
}
