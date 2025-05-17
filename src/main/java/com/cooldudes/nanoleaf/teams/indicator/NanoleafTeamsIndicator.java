/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.cooldudes.nanoleaf.teams.indicator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

/**
 *
 * @author bryson and jake
 */
public class NanoleafTeamsIndicator {

    private static String ACCESS_TOKEN;
    private static NanoleafShapes shapes;
    private static String clientState = RandomGenerators.generateRandomString(10);

    public static void main(String[] args)  {
        try {
            Graph.initialize(clientState);
//            String ip = "192.168.1.207";
//            NanoleafShapes shapes = new NanoleafShapes(ip, ACCESS_TOKEN);
            new SocketConnection(clientState, shapes);
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }
}
