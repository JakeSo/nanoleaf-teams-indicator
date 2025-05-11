package com.cooldudes.nanoleaf.teams.indicator;

import com.nimbusds.oauth2.sdk.util.JSONUtils;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;

import javax.naming.ConfigurationException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class SocketConnection {

    private String PUSHER_KEY;
    private String PUSHER_CLUSTER;
    private final Pusher pusher;

    public SocketConnection(String userId, StatusChangeHandler changeHandler) throws ConfigurationException {
        try {
            setupProperties();
            pusher = getPusher();
            Channel channel = pusher.subscribe("status-changes");
            channel.bind(userId, pusherEvent -> {
                try {
                    Object eventObject = JSONUtils.parseJSON(pusherEvent.toJson());
                    Presence presence = JSONUtils.to(eventObject, Presence.class);
                    changeHandler.handleStatusChange(presence);
                } catch (Exception ex) {
                    System.err.println(ex);
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
        FileReader reader = new FileReader("pusher.properties");
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
