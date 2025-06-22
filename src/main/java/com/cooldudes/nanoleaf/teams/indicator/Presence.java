package com.cooldudes.nanoleaf.teams.indicator;

/**
 * Represents a user's Teams presence, including availability and activity.
 */
public class Presence {
    public String availability;
    public String activity;

    /**
     * Constructs a Presence object.
     * 
     * @param availability The user's availability (e.g., Available, Busy)
     * @param activity     The user's activity (e.g., InACall, OutOfOffice)
     */
    public Presence(String availability, String activity) {
        this.availability = availability;
        this.activity = activity;
    }

    @Override
    public String toString() {
        return "Presence{" + "availability='" + availability + '\'' +
                ", activity='" + activity + '\'' +
                '}';
    }
}
