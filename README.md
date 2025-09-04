# Nanoleaf Teams Indicator

A Java application that syncs your Microsoft Teams presence with a Nanoleaf Shapes device, visually displaying your Teams status using colored light effects.

## Features

- Authenticates with Microsoft Graph API to read Teams presence.
- Subscribes to presence updates and receives real-time notifications.
- Controls a Nanoleaf Shapes device to reflect your Teams status (Available, Busy, Away, Out of Office, Offline).
- Automatic device discovery and pairing.
- Secure handling of credentials and certificates.

## Requirements

- Java 17 or later
- Maven
- A Nanoleaf Shapes device on your local network
- Microsoft Entra ID (Azure AD) app registration with appropriate Graph API permissions
- Network access to Microsoft Graph and Pusher

## Setup

1. **Clone the repository:**
   ```sh
   git clone https://github.com/yourusername/nanoleaf-teams-indicator.git
   cd nanoleaf-teams-indicator
   ```

2. **Configure Microsoft Graph:**
   - Register an app in Entra ID (Azure AD).
   - Set redirect URI to `http://localhost:8080`.
   - Grant `Presence.Read` and `Presence.Read.All` permissions.
   - Copy your `client_id` and `tenant` to `resources/oAuth.properties`.

3. **Configure Pusher:**
   - Create a Pusher app and note the key and cluster.
   - Add them to `resources/pusher.properties`.

4. **Certificates and Keys:**
   - Place your public certificate as `resources/public-cert.pem`.
   - Place your Java keystore as `resources/keystore.jks` and set the password in `resources/keystore.properties`.

5. **Build the project:**
   ```sh
   mvn package
   ```

6. **Run the application:**
   ```sh
   java -jar target/nanoleaf-teams-indicator-1.0-SNAPSHOT.jar
   ```

## Usage

- On first run, the app will prompt you to log in to Microsoft and pair with your Nanoleaf device.
- Your Teams presence will be reflected on the Nanoleaf device using different color effects.
- To stop the app, press `Ctrl+C`.

## Status Color Mapping

| Teams Status    | Nanoleaf Effect Color |
|-----------------|----------------------|
| Available       | Green                |
| Busy/DND        | Red                  |
| Away/BRB        | Yellow/Orange        |
| Out of Office   | Purple/Dim           |
| Offline         | Device Off           |

## Troubleshooting

- If the device is not found, ensure it's on the same network and powered on.
- If authentication fails, check your `oAuth.properties` and app registration.
- For certificate errors, verify your keystore and certificate files.

## License

MIT License

---

**Authors:**  
Bryson & Jake

