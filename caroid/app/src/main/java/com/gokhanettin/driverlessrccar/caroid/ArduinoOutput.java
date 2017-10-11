package com.gokhanettin.driverlessrccar.caroid;

import java.util.Locale;

public class ArduinoOutput {
    public int speedCommand;
    public int steeringCommand;

    public String toString() {
        return String.format(Locale.US,
                "[%d;%d]", speedCommand, steeringCommand);
    }
}
