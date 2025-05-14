/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.cooldudes.nanoleaf.teams.indicator;

import java.io.IOException;
import java.util.*;

/**
 *
 * @author bryson and jake
 */
public class NanoleafTeamsIndicator {

    private static String ACCESS_TOKEN;
    private static NanoleafShapes shapes;

    //TODO DoNotDisturb dark red palette
    static Map<String, NanoleafEffect.PaletteColor[]> STATUS_PALETTES = Map.ofEntries(
            Map.entry("Available", new NanoleafEffect.PaletteColor[]{
                    new NanoleafEffect.PaletteColor(100, 100, 100, 70),
                    new NanoleafEffect.PaletteColor(100, 100, 70, 30),
            }),
            Map.entry("Busy", new NanoleafEffect.PaletteColor[]{
                    new NanoleafEffect.PaletteColor(0, 100, 100, 50),
                    new NanoleafEffect.PaletteColor(0, 100, 70, 50)
            }),
            Map.entry("Away", new NanoleafEffect.PaletteColor[]{
                    new NanoleafEffect.PaletteColor(45, 80, 100, 10 ),
                    new NanoleafEffect.PaletteColor(40, 100, 100, 90)
            }),
            Map.entry("OutOfOffice", new NanoleafEffect.PaletteColor[]{
                    new NanoleafEffect.PaletteColor(282, 100, 10, 20),
                    new NanoleafEffect.PaletteColor(0,0,0,80)
            })
    );

    public static void main(String[] args) {
        try {
//            List<NanoleafDeviceMeta> devices = NanoleafSetup.findNanoleafDevices(5);
//            NanoleafDeviceMeta selectedNano = null;
//            if (devices.size() == 1) {
//                selectedNano = devices.getFirst();
//            } else {
//                System.out.println(devices);
//            }
//            if (selectedNano == null)
//            System.out.printf("Found device with IP %s and Port %d%n", selectedNano.getHostName(),selectedNano.getPort());
            String ip = "192.168.1.207";
            shapes = new NanoleafShapes(ip, ACCESS_TOKEN);
            shapes.setPower(true);
            setStatusEffect("Available", "Available");
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }

    public static void setStatusEffect(String availability, String activity) {
        NanoleafEffect.PaletteColor[] palette;

        if (availability.equals("Busy") || availability.equals("DoNotDisturb")) {
            palette = STATUS_PALETTES.get("Busy");
        } else if (availability.equals("Away") || availability.equals("BeRightBack")) {
            palette = STATUS_PALETTES.get("Away");
        } else if (activity.equals("OutOfOffice")) {
            palette = STATUS_PALETTES.get("OutOfOffice");
        } else {
            palette = STATUS_PALETTES.get("Available");
        }
        NanoleafEffect effect = new NanoleafEffect(palette);
        try {
            shapes.displayEffect(effect);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            System.out.println(e);
        }

    }
}
