package com.gokhanettin.driverlessrccar.caroid;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class UsbClient {
    private static final String TAG = "UsbClient";

    // Member fields
    private final Handler mHandler;

    private UsbSerialPort mPort;
    private UsbSerialDriver mDriver;
    private UsbDevice mDevice;

    private ConnectedThread mConnectedThread = null;
    private int mState;
    private int mNewState;

    // Message types sent from the BluetoothClient to activities
    public static final int MESSAGE_CONNECTION_STATE_CHANGE = 0;
    public static final int MESSAGE_RECEIVE = 1;
    public static final int MESSAGE_COMMUNICATION_MODE_CHANGE = 2;
    public static final int MESSAGE_SEND = 3;
    public static final int MESSAGE_CONNECTION_ESTABLISHED = 4;
    public static final int MESSAGE_CONNECTION_ERROR = 5;

    // Key names to identify some messages
    public static final String DEVICE_NAME = "device_name";
    public static final String CONNECTION_ERROR = "conn_error";
    public static final String COMMUNICATION_MODE = "comm_mode";

    // Communication modes
    public static final String MODE_NONE = "N";
    public static final String MODE_MONITOR = "M";
    public static final String MODE_CONTROL = "C";

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_CONNECTING = 1; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 2;  // now connected to a remote device

    public UsbClient(Handler handler) {
        mState = STATE_NONE;
        mNewState = mState;
        mHandler = handler;
    }

    private synchronized void notifyStateChange() {
        mState = getState();
        if (mNewState != mState) {
            Log.d(TAG, "notifyStateChange() " + mNewState + " -> " + mState);
            mNewState = mState;

            // Give the new state to the Handler so the Activity can update
            mHandler.obtainMessage(MESSAGE_CONNECTION_STATE_CHANGE, mNewState, -1).sendToTarget();
        }
    }

    public synchronized int getState() {
        return mState;
    }

    public synchronized boolean connect(UsbManager usbManager, UsbSerialPort port, Context context) {
        try {
            UsbDevice usbDevice = port.getDriver().getDevice();
            if (!usbManager.hasPermission(usbDevice)) {
                PendingIntent pi = PendingIntent.getBroadcast(context, 0, new Intent("com.android.example.USB_PERMISSION"), 0);
                usbManager.requestPermission(usbDevice, pi);
            }

            UsbDeviceConnection connection = usbManager.openDevice(usbDevice);
            if (connection == null) {
                Log.d(TAG, "Opening device failed");
                return false;
            }
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        }
        catch (IOException exception) {
            return false;
        }

        mPort = port;
        mDriver = mPort.getDriver();
        mDevice = mDriver.getDevice();

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(mPort);
        mConnectedThread.start();

        // Send the name of the connected device back
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECTION_ESTABLISHED);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, mDevice.getDeviceName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        notifyStateChange();
        return true;
    }

    public synchronized void disconnect() {
        mState = STATE_NONE;
        notifyStateChange();

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    public void requestCommunicationMode(String mode) {
        requestCommunicationMode(mode, 0);
    }

    public void requestCommunicationMode(String mode, int delay) {
        // Create temporary object
        ConnectedThread t;
        // Synchronize a copy of the ReaderThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            t = mConnectedThread;
        }
        // Request unsynchronized
        t.requestCommunicationMode(mode, delay);
    }

    public void send(int speedCmd, int steeringCmd) {
        send(speedCmd, steeringCmd, 0);
    }

    public void send(int speedCmd, int steeringCmd, int delay) {
        ArduinoOutput out = new ArduinoOutput();
        out.speedCommand = speedCmd;
        out.steeringCommand = steeringCmd;

        ConnectedThread t;

        // Synchronize a copy of the ReaderThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            t = mConnectedThread;
        }
        // Send unsynchronized
        t.send(out, delay);
    }

    public void send(String command, int delay) {
        ConnectedThread t;

        // Synchronize a copy of the ReaderThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            t = mConnectedThread;
        }

        // Send unsynchronized
        t.send(command, delay);
    }

    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECTION_ERROR);
        Bundle bundle = new Bundle();
        bundle.putString(CONNECTION_ERROR, "Bluetooth device connection lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;
        notifyStateChange();
    }

    private class ConnectedThread extends Thread implements SerialInputOutputManager.Listener {
        private SerialInputOutputManager mManager;

        private StringBuilder mmStringBuilder;
        private boolean mmValid;

        private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

        ConnectedThread(UsbSerialPort port) {
            Log.d(TAG, "create ReaderThread");

            mManager = new SerialInputOutputManager(port, this);
            mExecutor.submit(mManager);

            mState = STATE_CONNECTED;
            mmStringBuilder = new StringBuilder();
            mmValid = false;
        }

        void send(String command, int delay) {
            Log.d(TAG, "Sending to serial: " + command);

            byte[] buffer = command.getBytes();
            try {
                mManager.writeAsync(buffer);
            } catch (Exception e) {
                Log.e(TAG, "Exception on send()", e);
                connectionLost();
            }

            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        void send(ArduinoOutput out, int delay) {
            // "<throttle_cmd> <steering_cmd>\n"
            String string = String.format(Locale.US, "%d %d\n", out.speedCommand, out.steeringCommand);
            send(string, delay);
            mHandler.obtainMessage(MESSAGE_SEND, -1, -1, out).sendToTarget();
        }

        void requestCommunicationMode(String mode, int delay) {
            // "<mode>\n"
            Log.d(TAG, "Changing mode to " + mode);
            byte[] buffer = (mode + "\n").getBytes();
            try {
                mManager.writeAsync(buffer);
                Message msg = mHandler.obtainMessage(MESSAGE_COMMUNICATION_MODE_CHANGE);
                Bundle bundle = new Bundle();
                bundle.putString(COMMUNICATION_MODE, mode);
                msg.setData(bundle);
                mHandler.sendMessage(msg);
            } catch (Exception e) {
                Log.e(TAG, "Exception on requestCommunicationMode()", e);
            }

            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        void cancel() {
            try {
                mManager.stop();
                mManager = null;
            } catch (Exception e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }

        private void parse() {
            // "<throttle_cmd>;<steering_cmd>;<velocity>;<steering>"
            String string = mmStringBuilder.toString();
            try {
                Log.d(TAG, "Parse string: " + string);
                String tokens[] = string.split(" ");
                ArduinoInput in = new ArduinoInput();
                in.speedCommand = Integer.parseInt(tokens[0]);
                in.steeringCommand = Integer.parseInt(tokens[1]);
                in.distance = Float.parseFloat(tokens[2]);
                mHandler.obtainMessage(MESSAGE_RECEIVE, -1, -1, in).sendToTarget();
            } catch (NumberFormatException exc) {
                Log.e(TAG, "Unable to parse data from serial: " + string, exc);
            }
        }

        @Override
        public void onNewData(byte[] data) {
            for (byte c : data) {
                if (mmValid) {
                    if (c == '\n') {
                        parse();
                        mmStringBuilder.setLength(0);
                        break;
                    } else {
                        mmStringBuilder.append((char) c);
                    }
                } else if (c == '\n') {
                    mmValid = true;
                }
            }
        }

        @Override
        public void onRunError(Exception e) {
            Log.e(TAG, "UsbClient run error", e);
            connectionLost();
        }
    }
}
