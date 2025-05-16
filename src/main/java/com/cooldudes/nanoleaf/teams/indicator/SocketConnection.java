package com.cooldudes.nanoleaf.teams.indicator;

import com.nimbusds.oauth2.sdk.util.JSONUtils;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.ConfigurationException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class SocketConnection {

    private static final Logger log = LoggerFactory.getLogger(SocketConnection.class);
    private String PUSHER_KEY;
    private String PUSHER_CLUSTER;
    private final Pusher pusher;

    public SocketConnection(String clientState, StatusChangeHandler changeHandler) throws ConfigurationException {
        try {
            setupProperties();
            pusher = getPusher();
            Channel channel = pusher.subscribe("status-changes-" + clientState);
            channel.bind("status-change", pusherEvent -> {
                try {
                    System.out.println("Received event: " + pusherEvent.toJson());
                    Object eventObject = JSONUtils.parseJSON(pusherEvent.toJson());
//                    Presence presence = JSONUtils.to(eventObject, Presence.class);
//                    changeHandler.handleStatusChange(presence);
                } catch (Exception ex) {
                    log.error("Error subscribing: ", ex);
                    System.err.println("Event: \n" + pusherEvent.toString());
                }
            });

        } catch (IOException e) {
            throw new ConfigurationException("Could not read property values: " + e.getMessage());
        } catch (Exception e) {
            throw new ConfigurationException("Pusher setup failed: " + e.getMessage());
        }

    }

    private Pusher getPusher() {
        final Pusher pusher;
        PusherOptions options = new PusherOptions().setCluster(PUSHER_CLUSTER);
        pusher = new Pusher(PUSHER_KEY, options);
        pusher.connect(new ConnectionEventListener() {
            @Override
            public void onConnectionStateChange(ConnectionStateChange change) {
                System.out.println("State changed to " + change.getCurrentState() +
                        " from " + change.getPreviousState());
            }

            @Override
            public void onError(String message, String code, Exception e) {
                System.err.println("There was a problem connecting!");
                System.err.println(message);
            }
        }, ConnectionState.ALL);
        return pusher;
    }

    private void setupProperties() throws IOException {
        FileReader reader = new FileReader("src/main/resources/pusher.properties");
        // create properties object
        Properties p = new Properties();
        p.load(reader);

        PUSHER_KEY = p.getProperty("KEY");
        PUSHER_CLUSTER = p.getProperty("CLUSTER");

    }

    public void disconnect() {
        pusher.disconnect();
    }

    public void connect() {
        pusher.connect();
    }
}
