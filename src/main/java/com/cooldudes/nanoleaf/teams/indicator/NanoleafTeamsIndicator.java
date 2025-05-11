/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.cooldudes.nanoleaf.teams.indicator;

/**
 *
 * @author bryson and jake
 */
public class NanoleafTeamsIndicator {

    private static final String ACCESS_TOKEN = "dq3kywKI2eYET1S1SdVanhVCP5149Mhw";

    public static void main(String[] args) {
        try {
            String ip = "192.168.1.207";
            NanoleafShapes shapes = new NanoleafShapes(ip, ACCESS_TOKEN);
            new SocketConnection("jake.sorrentino@remasonco.com", shapes);
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }
}
