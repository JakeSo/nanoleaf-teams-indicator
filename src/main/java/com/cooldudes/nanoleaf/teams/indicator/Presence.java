package com.cooldudes.nanoleaf.teams.indicator;

//TODO Change to match what we are actually receiving
public class Presence {
    public String availability;
    public String activity;

    public Presence(String availability, String activity) {
        this.availability = availability;
        this.activity = activity;
    }
}
