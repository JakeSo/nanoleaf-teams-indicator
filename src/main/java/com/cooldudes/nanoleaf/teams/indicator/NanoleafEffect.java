package com.cooldudes.nanoleaf.teams.indicator;

public class NanoleafEffect {
    public String command;
    public String version = "2.0";
    public String animName;
    public String animType;
    public float duration;
    public String colorType = "HSB";
    public PaletteColor[] palette;
    public boolean loop;

    public NanoleafEffect(PaletteColor[] palette) {
        this.command = "display";
        this.animType = "highlight";
        this.duration = 1;
        this.palette = palette;
        this.loop = true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{")
                .append("\"command\":\"").append(command).append("\",")
                .append("\"version\":\"").append(version).append("\",")
                .append("\"animName\":\"").append(animName).append("\",")
                .append("\"animType\":\"").append(animType).append("\",")
                .append("\"duration\":").append(duration).append(",")
                .append("\"colorType\":\"").append(colorType).append("\",")
                .append("\"palette\":[");

        if (palette != null) {
            for (int i = 0; i < palette.length; i++) {
                sb.append(palette[i].toString()); // assumes PaletteColor has its own JSON-like toString()
                if (i < palette.length - 1)
                    sb.append(",");
            }
        }

        sb.append("],")
                .append("\"loop\":").append(loop)
                .append("}");

        return sb.toString();
    }

    public static class PaletteColor {
        public int hue;
        public int saturation;
        public int brightness;
        public int probability;

        public PaletteColor(int hue, int saturation, int brightness, int probability) {
            if (hue > 359 || hue < 0) {
                throw new IllegalArgumentException("Hue should be between 0 and 359");
            }
            if (saturation > 100 || saturation < 0) {
                throw new IllegalArgumentException("Saturation should be between 0 and 100");
            }
            if (brightness > 100 || brightness < 0) {
                throw new IllegalArgumentException("Brightness should be between 0 and 100");
            }
            this.hue = hue;
            this.brightness = brightness;
            this.saturation = saturation;
            this.probability = probability;

        }

        public PaletteColor(int hue) {
            this(hue, 100, 100, 0);
        }

        @Override
        public String toString() {
            return String.format(
                    "{\"hue\":%d,\"saturation\":%d,\"brightness\":%d,\"probability\":%d}",
                    hue, saturation, brightness, probability);
        }

    }
}
