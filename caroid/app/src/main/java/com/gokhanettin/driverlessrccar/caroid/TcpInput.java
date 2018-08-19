package com.gokhanettin.driverlessrccar.caroid;

import android.content.Context;
import android.util.Log;

import java.util.Date;

public class TcpInput {
    private static final String TAG = "TcpOutput";

    abstract interface IServerCommand {
        void run(AcquisitionActivity activity);
    }

    class QualityCommand implements IServerCommand {
        int quality;

        QualityCommand(int quality) {
            this.quality = quality;
        }

        @Override
        public void run(AcquisitionActivity activity) {
            activity.mCameraPreview.jpegQuality = quality;
        }
    }

    class FlashCommand implements IServerCommand {
        @Override
        public void run(AcquisitionActivity activity) {
            activity.mCameraPreview.flash();
        }
    }

    class ArduinoCommand implements IServerCommand {
        String command;

        public ArduinoCommand(String command) {
            this.command = command;
        }

        @Override
        public void run(AcquisitionActivity activity) {
            activity.mTcpClient.pingDate = new Date();
            if (activity.mUsbClient.getState() == UsbClient.STATE_CONNECTED) {
                Log.d(TAG, "Send to USB: " + command);
                activity.mUsbClient.send(command + "\n", 0);
            }
        }
    }

    class PingCommand implements IServerCommand {
        @Override
        public void run(AcquisitionActivity activity) {
            Log.d(TAG, "Got ping signal from server");
            activity.mTcpClient.pingDate = new Date();
        }
    }

    public IServerCommand command;

    public TcpInput(String tokens[]) {
        if (tokens.length == 0) {
            Log.d(TAG, "Empty command from server");
            return;
        }

        switch (tokens[0]) {
            case "Q":
                command = new QualityCommand(Integer.parseInt(tokens[1]));
                break;
            case "F":
                command = new FlashCommand();
                break;
            case "A":
                command = new ArduinoCommand(tokens[1]);
                break;
            case "P":
                command = new PingCommand();
                break;
            default:
                Log.d(TAG, "Unknown command: " + tokens[0]);
        }
    }
}
