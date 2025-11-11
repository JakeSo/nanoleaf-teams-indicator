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
            // Initialize system tray
            boolean trayInitialized = SystemTrayManager.initialize();
            if (trayInitialized) {
                SystemTrayManager.updateStatus("Initializing...");
                System.out.println("System tray GUI initialized");
            } else {
                System.out.println("Running without system tray GUI");
            }

            SystemTrayManager.updateStatus("Authenticating...");
            String clientState = Graph.initialize();
            if (clientState == null) clientState = RandomGenerators.generateRandomString(7);

            SystemTrayManager.updateStatus("Connecting to Nanoleaf...");
            connectToNanoleaf();

            SystemTrayManager.updateStatus("Connecting to Teams...");
            SocketConnection socket = new SocketConnection(clientState, shapes);

            SystemTrayManager.updateStatus("Ready");
            SystemTrayManager.showNotification("Nanoleaf Teams Indicator",
                "Application started successfully",
                java.awt.TrayIcon.MessageType.INFO);

            CountDownLatch latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // Clean up resources, disconnect Pusher, etc.
                SystemTrayManager.updateStatus("Shutting down...");
                try {
                    shapes.setPower(false);
                } catch (Exception e) {
                    System.out.println("Could not shutdown device: " + e.getMessage());
                }
                socket.disconnect();
                SystemTrayManager.cleanup();
                latch.countDown();
            }));
            latch.await();
        } catch (Exception e) {
            System.out.println("Error: " + e);
            SystemTrayManager.showNotification("Error",
                "Application error: " + e.getMessage(),
                java.awt.TrayIcon.MessageType.ERROR);
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

    /**
     * Public method to reconnect to the Nanoleaf device.
     * Used by the system tray menu.
     *
     * @return true if reconnection was successful, false otherwise
     */
    public static boolean reconnectToNanoleaf() {
        try {
            System.out.println("Attempting to reconnect to Nanoleaf...");
            shapes = NanoleafShapes.findDevice();
            return shapes != null;
        } catch (Exception e) {
            System.err.println("Error reconnecting to Nanoleaf: " + e.getMessage());
            return false;
        }
    }
}
