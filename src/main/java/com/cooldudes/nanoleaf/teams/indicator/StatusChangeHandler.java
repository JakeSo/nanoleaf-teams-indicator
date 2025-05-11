package com.cooldudes.nanoleaf.teams.indicator;

public interface StatusChangeHandler {

    void handleStatusChange(Presence userPresence) throws Exception;

}
