package com.cooldudes.nanoleaf.teams.indicator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Manages the system tray icon and menu for the Nanoleaf Teams Indicator application.
 */
public class SystemTrayManager {
    private static SystemTray systemTray;
    private static TrayIcon trayIcon;
    private static String currentStatus = "Starting...";
    private static PopupMenu popupMenu;

    /**
     * Initializes the system tray icon and menu.
     * @return true if initialization was successful, false otherwise
     */
    public static boolean initialize() {
        if (!SystemTray.isSupported()) {
            System.err.println("System tray is not supported on this platform");
            return false;
        }

        try {
            systemTray = SystemTray.getSystemTray();

            // Create tray icon with default image
            Image image = createDefaultIcon();

            // Create popup menu
            popupMenu = new PopupMenu();

            // Status menu item (non-clickable, shows current status)
            MenuItem statusItem = new MenuItem("Status: " + currentStatus);
            statusItem.setEnabled(false);
            popupMenu.add(statusItem);

            popupMenu.addSeparator();

            // Reconnect menu item
            MenuItem reconnectItem = new MenuItem("Reconnect to Nanoleaf");
            reconnectItem.addActionListener(e -> {
                showNotification("Reconnecting...", "Attempting to reconnect to Nanoleaf device", TrayIcon.MessageType.INFO);
                // This will be implemented in the main class
                if (NanoleafTeamsIndicator.reconnectToNanoleaf()) {
                    showNotification("Reconnected", "Successfully reconnected to Nanoleaf device", TrayIcon.MessageType.INFO);
                } else {
                    showNotification("Failed", "Could not reconnect to Nanoleaf device", TrayIcon.MessageType.ERROR);
                }
            });
            popupMenu.add(reconnectItem);

            // About menu item
            MenuItem aboutItem = new MenuItem("About");
            aboutItem.addActionListener(e -> {
                showNotification("Nanoleaf Teams Indicator",
                    "Syncs your Microsoft Teams status with your Nanoleaf device.\n\nCurrent Status: " + currentStatus,
                    TrayIcon.MessageType.INFO);
            });
            popupMenu.add(aboutItem);

            popupMenu.addSeparator();

            // Exit menu item
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> {
                System.out.println("Exiting application from system tray...");
                cleanup();
                System.exit(0);
            });
            popupMenu.add(exitItem);

            // Create tray icon
            trayIcon = new TrayIcon(image, "Nanoleaf Teams Indicator - " + currentStatus, popupMenu);
            trayIcon.setImageAutoSize(true);

            // Add double-click listener to show status
            trayIcon.addActionListener(e -> {
                showNotification("Current Status", currentStatus, TrayIcon.MessageType.INFO);
            });

            // Add tray icon to system tray
            systemTray.add(trayIcon);

            System.out.println("System tray initialized successfully");
            return true;

        } catch (AWTException | IOException e) {
            System.err.println("Error initializing system tray: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Updates the status displayed in the system tray.
     * @param status The new status to display
     */
    public static void updateStatus(String status) {
        currentStatus = status;
        if (trayIcon != null) {
            trayIcon.setToolTip("Nanoleaf Teams Indicator - " + status);

            // Update the status menu item
            if (popupMenu != null && popupMenu.getItemCount() > 0) {
                MenuItem statusItem = popupMenu.getItem(0);
                statusItem.setLabel("Status: " + status);
            }

            // Update icon based on status
            try {
                Image icon = getIconForStatus(status);
                trayIcon.setImage(icon);
            } catch (IOException e) {
                System.err.println("Error updating tray icon: " + e.getMessage());
            }
        }
    }

    /**
     * Shows a notification from the system tray.
     * @param title The notification title
     * @param message The notification message
     * @param messageType The type of message (INFO, WARNING, ERROR)
     */
    public static void showNotification(String title, String message, TrayIcon.MessageType messageType) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, messageType);
        }
    }

    /**
     * Gets the appropriate icon for the given status.
     * @param status The status string
     * @return The icon image
     */
    private static Image getIconForStatus(String status) throws IOException {
        String lowerStatus = status.toLowerCase();

        // Available/Online - Green
        if (lowerStatus.contains("available") || lowerStatus.contains("online")) {
            return createColoredIcon(Color.GREEN);
        }
        // Busy/Do Not Disturb - Red
        else if (lowerStatus.contains("busy") || lowerStatus.contains("do not disturb") || lowerStatus.contains("donotdisturb")) {
            return createColoredIcon(Color.RED);
        }
        // Away/Be Right Back - Yellow/Orange
        else if (lowerStatus.contains("away") || lowerStatus.contains("be right back") || lowerStatus.contains("berightback")) {
            return createColoredIcon(new Color(255, 165, 0)); // Orange
        }
        // Offline - Gray
        else if (lowerStatus.contains("offline")) {
            return createColoredIcon(Color.GRAY);
        }
        // Out of Office - Purple
        else if (lowerStatus.contains("presenceunknown") || lowerStatus.contains("out of office")) {
            return createColoredIcon(new Color(128, 0, 128)); // Purple
        }
        // Default - Blue
        else {
            return createColoredIcon(Color.BLUE);
        }
    }

    /**
     * Creates a default icon for the system tray.
     * @return The default icon image
     */
    private static Image createDefaultIcon() throws IOException {
        return createColoredIcon(Color.BLUE);
    }

    /**
     * Creates a colored circular icon.
     * @param color The color of the icon
     * @return The icon image
     */
    private static Image createColoredIcon(Color color) {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // Enable anti-aliasing for smoother circle
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw filled circle
        g.setColor(color);
        g.fillOval(1, 1, size - 2, size - 2);

        // Draw border
        g.setColor(Color.WHITE);
        g.drawOval(1, 1, size - 2, size - 2);

        g.dispose();
        return image;
    }

    /**
     * Cleans up the system tray icon.
     */
    public static void cleanup() {
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
            System.out.println("System tray icon removed");
        }
    }
}
