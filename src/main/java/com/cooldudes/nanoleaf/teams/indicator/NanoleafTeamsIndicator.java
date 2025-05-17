/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.cooldudes.nanoleaf.teams.indicator;

import io.github.rowak.nanoleafapi.util.NanoleafDeviceMeta;
import io.github.rowak.nanoleafapi.util.NanoleafSetup;

import java.util.List;

/**
 *
 * @author bryson and jake
 */
public class NanoleafTeamsIndicator {

    private static NanoleafShapes shapes;
    private static String clientState = RandomGenerators.generateRandomString(10);

    public static void main(String[] args)  {
        try {
           Graph.initialize(clientState);
           //TODO: Improve error handling and maybe store the IP and Access Token if needed
            List<NanoleafDeviceMeta> devices = NanoleafSetup.findNanoleafDevices(500);
            NanoleafDeviceMeta ourNano = devices.getFirst();
            String accessToken = NanoleafSetup.createAccessToken(ourNano.getHostName(), ourNano.getPort());
            NanoleafShapes shapes = new NanoleafShapes(ourNano.getDeviceId(), accessToken);
            new SocketConnection(clientState, shapes);
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }
}
