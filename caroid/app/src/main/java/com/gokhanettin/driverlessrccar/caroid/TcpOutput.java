package com.gokhanettin.driverlessrccar.caroid;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.Locale;

public class TcpOutput {
    private static final String TAG = "TcpOutput";

    public ArduinoInput arduinoInput;
    public AndroidInput androidInput;

    public TcpOutput(ArduinoInput arduinoInput, AndroidInput androidInput) {
        this.arduinoInput = arduinoInput;
        this.androidInput = androidInput;
    }

    public String toString() {
        return "TcpClient.Output = " + arduinoInput.toString() + " | " + androidInput.toString();
    }

    public void writeTo(DataOutputStream stream) throws IOException {
        CameraPreview camera = androidInput.Camera;
        byte[] jpeg = camera.getPreviewJpeg();

        String data = String.format(Locale.US, "[%d;%d;%d;%.1f;%d",
                arduinoInput.isOnline ? 1 : 0,
                arduinoInput.speedCommand, arduinoInput.steeringCommand, arduinoInput.distance,
                jpeg.length
        );
        for (float sensorValue: androidInput.SensorValues) {
            data += String.format(Locale.US, ";%.5f", sensorValue);
        }
        data += "]";

        stream.write(data.getBytes());
        stream.write(jpeg);
    }
}
