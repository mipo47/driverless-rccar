package com.gokhanettin.driverlessrccar.caroid;

import java.util.Locale;

public class ArduinoInput {
    public boolean isOnline = false;
    public int speedCommand = 1400;
    public int steeringCommand = 1568;
    public float distance = 0f;

    public String toString() {
        Locale locale = Locale.US;
        return String.format(locale,
                "[%d;%d;%.1f]", speedCommand, steeringCommand, distance);
    }
}
