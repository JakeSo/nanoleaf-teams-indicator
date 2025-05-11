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

    public static void main(String[] args) throws IOException, URISyntaxException, ExecutionException, InterruptedException {
        try {
            Graph.initialize(RandomGenerators.generateRandomString(10));
            String ip = "192.168.1.207";
            NanoleafShapes shapes = new NanoleafShapes(ip, ACCESS_TOKEN);
            new SocketConnection("jake.sorrentino@remasonco.com", shapes);
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }
}
