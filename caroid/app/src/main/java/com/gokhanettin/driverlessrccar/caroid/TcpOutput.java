package com.gokhanettin.driverlessrccar.caroid;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

public class TcpOutput {
    public ArduinoInput ArduinoInput;
    public AndroidInput AndroidInput;

    public TcpOutput(ArduinoInput arduinoInput, AndroidInput androidInput) {
        ArduinoInput = arduinoInput;
        AndroidInput = androidInput;
    }

    public String toString() {
        return "TcpClient.Output = " + ArduinoInput.toString() + " | " + AndroidInput.toString();
    }

    public void writeTo(DataOutputStream stream) throws IOException {
        CameraPreview camera = AndroidInput.Camera;
        int width = camera.getPreviewWidth();
        int height = camera.getPreviewHeight();
        byte[] preview = camera.getPreview();
        byte[] jpeg = CameraPreview.previewToJpeg(preview, width, height);

        byte[] header = String.format(Locale.US, "[%d;%d;%.3f;%.3f;%d]",
                ArduinoInput.speedCommand, ArduinoInput.steeringCommand, ArduinoInput.speed,
                ArduinoInput.steering, jpeg.length).getBytes();

        stream.write(header);
        stream.write(jpeg);
    }
}
