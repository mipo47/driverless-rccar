package com.gokhanettin.driverlessrccar.caroid;

import android.os.Handler;
import android.os.Message;

abstract class UsbHandlerCallback implements Handler.Callback {
    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case UsbClient.MESSAGE_CONNECTION_STATE_CHANGE:
                onConnectionStateChanged(msg.arg1);
                break;
            case UsbClient.MESSAGE_RECEIVE:
                ArduinoInput in = (ArduinoInput) msg.obj;
                onReceived(in);
                break;
            case UsbClient.MESSAGE_SEND:
                ArduinoOutput out = (ArduinoOutput) msg.obj;
                onSent(out);
                break;
            case UsbClient.MESSAGE_COMMUNICATION_MODE_CHANGE:
                String mode = msg.getData().getString(UsbClient.COMMUNICATION_MODE);
                onCommunicationModeChanged(mode);
                break;
            case UsbClient.MESSAGE_CONNECTION_ESTABLISHED:
                String deviceName = msg.getData().getString(UsbClient.DEVICE_NAME);
                onConnectionEstablished(deviceName);
                break;
            case UsbClient.MESSAGE_CONNECTION_ERROR:
                String error = msg.getData().getString(UsbClient.CONNECTION_ERROR);
                onConnectionError(error);
                break;
        }
        return true;
    }
    protected abstract void onConnectionStateChanged(int newState);
    protected abstract void onReceived(ArduinoInput in);
    protected abstract void onSent(ArduinoOutput out);
    protected abstract void onCommunicationModeChanged(String newMode);
    protected abstract void onConnectionEstablished(String connectedDeviceName);
    protected abstract void onConnectionError(String error);
}
