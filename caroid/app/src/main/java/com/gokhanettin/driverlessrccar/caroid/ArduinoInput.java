package com.gokhanettin.driverlessrccar.caroid;

import java.util.Locale;

public class ArduinoInput {
    public int speedCommand = 1400;
    public int steeringCommand = 1568;
    public float speed;
    public float steering;

    public String toString() {
        Locale locale = Locale.US;
        return String.format(locale,
                "[%d;%d;%.3f;%.3f]", speedCommand, steeringCommand, speed, steering);
    }
}
