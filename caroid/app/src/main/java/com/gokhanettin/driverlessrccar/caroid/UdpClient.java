package com.gokhanettin.driverlessrccar.caroid;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class UdpClient {
    private static final String TAG = "UdpClient";

    // Message types sent from the TcpClient to activities
    public static final int MESSAGE_CONNECTION_STATE_CHANGE = 0;
    public static final int MESSAGE_RECEIVE = 1;
    public static final int MESSAGE_SEND = 2;
    public static final int MESSAGE_CONNECTION_ESTABLISHED = 3;
    public static final int MESSAGE_CONNECTION_ERROR = 4;

    // Key names to identify some messages
    public static final String SERVER_ADDRESS = "server_address";
    public static final String CONNECTION_ERROR = "udp_conn_error";

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_CONNECTING = 1; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 2;  // now connected to the server

    public float sendProbability = 0.1f;

    private ConnectThread mConnectThread = null;
    private ReaderThread mReaderThread = null;
    private WriterThread mWriterThread = null;

    private Handler mHandler;
    private int mState;
    private int mNewState;

    private InetAddress mAddress;
    private int mPort;

    public UdpClient(Handler handler) {
        mHandler = handler;
        mState = STATE_NONE;
        mNewState = mState;
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

    public synchronized void connect(String ip, int port) {
        Log.d(TAG, "Connecting to: " + ip + ":" + port);
        try {
            mAddress = InetAddress.getByName(ip);
        } catch (UnknownHostException exc) {
            Log.e(TAG, "Cannot connect", exc);
            return;
        }
        mPort = port;

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mReaderThread != null) {
            mReaderThread = null;
        }

        if (mWriterThread != null) {
            mWriterThread.cancel();
            mReaderThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(ip, port);
        mConnectThread.start();
        notifyStateChange();
    }

    public synchronized void disconnect() {
        mState = STATE_NONE;
        notifyStateChange();

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mReaderThread != null) {
            mReaderThread = null;
        }
        // Cancel any thread currently running a connection
        if (mWriterThread != null) {
            mWriterThread.cancel();
            mWriterThread = null;
        }
    }

    public void send(ArduinoInput arduinoInput, AndroidInput androidInput) {
        if (mState != STATE_CONNECTED) return;

        TcpOutput out = new TcpOutput(arduinoInput, androidInput);

        // Process the preview and send unsynchronized
        float currentProbability = 0f;
        if (mWriterThread.sendAsync(out)) {
            currentProbability = 1f;
        } else {
//            Log.d(TAG, "UDP is busy, skip sending");
        }
        // sliding average
        sendProbability = sendProbability * 0.995f + 0.005f * currentProbability;
    }

    private synchronized void connected(DatagramSocket socket) {
        String serverAddress = mAddress.toString();
        Log.d(TAG, "Connected to " + serverAddress);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mReaderThread != null) {
            mReaderThread = null;
        }
        if (mWriterThread != null) {
            mWriterThread.cancel();
            mWriterThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mWriterThread = new WriterThread(socket);
        mWriterThread.start();

        mReaderThread = new ReaderThread(socket);
        mReaderThread.start();

        // Send the name of the connected device back
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECTION_ESTABLISHED);
        Bundle bundle = new Bundle();
        bundle.putString(SERVER_ADDRESS, serverAddress);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        notifyStateChange();
    }

    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECTION_ERROR);
        Bundle bundle = new Bundle();
        bundle.putString(CONNECTION_ERROR, "Unable to connect tcp server");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;
        notifyStateChange();
    }

    private void connectionLost() {
        // Send a conn lost message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_CONNECTION_ERROR);
        Bundle bundle = new Bundle();
        bundle.putString(CONNECTION_ERROR, "Tcp connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = STATE_NONE;
        notifyStateChange();
    }

    private class ConnectThread extends Thread {
        String mmIP;
        int mmPort;
        DatagramSocket mmSocket;

        public ConnectThread(String ip, int port) {
            mmIP = ip;
            mmPort = port;
        }

        @Override
        public void run() {
            super.run();

            try {
                mmSocket = new DatagramSocket();
            } catch (IOException e) {
                Log.e(TAG, "Failed to connect to " + mmIP + ":" + mmPort, e);
                connectionFailed();
                // Close the socket
                mmSocket.close();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (UdpClient.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket);
        }

        void cancel() {
            mmSocket.close();
        }
    }

    private class ReaderThread extends Thread {
        private final DatagramSocket mmSocket;

        private StringBuilder mmStringBuilder;
        private boolean mmValid;

        public ReaderThread(DatagramSocket socket) {
            Log.d(TAG, "create ReaderThread");
            mmSocket = socket;
            mState = STATE_CONNECTED;
            mmStringBuilder = new StringBuilder();
            mmValid = false;
        }

        @Override
        public void run() {
            super.run();
            Log.i(TAG, "BEGIN mReaderThread");
            setName("ReaderThread");
            byte[] receiveData = new byte[1024];
            int c;
            // Keep listening to the InputStream while connected
            while (mState == STATE_CONNECTED) {
                try {
                    // Read from the InputStream
                    // "[<throttle_cmd>;<steering_cmd>]"
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    mmSocket.receive(receivePacket);
                    for (int i = 0; i < receivePacket.getLength(); i++) {
                        c = receiveData[i];
                        if (c == '[') {
                            mmValid = true;
                            mmStringBuilder.setLength(0);
                            continue;
                        }
                        if (mmValid) {
                            if (c == ']') {
                                parse();
                                mmValid = false;
                                break;
                            } else {
                                mmStringBuilder.append((char) c);
                            }
                        }
                    }
                } catch (SocketException e) {
                    Log.e(TAG, "Connection lost to the tcp server", e);
                    connectionLost();
                    break;
                } catch (IOException e) {
                    Log.e(TAG, "Connection lost to the tcp server", e);
                    connectionLost();
                    break;
                }
            }
        }

        private void parse() {
            // "<throttle_cmd>;<steering_cmd>"
            String string = mmStringBuilder.toString();

            Log.d(TAG, "Parse string: " + string);
            String tokens[] = string.split(";");
            TcpInput input = new TcpInput(tokens);

            mHandler.obtainMessage(MESSAGE_RECEIVE, -1, -1, input).sendToTarget();
        }
    }

    private class WriterThread extends Thread {
        private final DatagramSocket mmSocket;
        private final DataOutputStream mmOutStream;
        private final ByteArrayOutputStream mmByteOutput;
        private TcpOutput mOut;

        public WriterThread(DatagramSocket socket) {
            Log.d(TAG, "create WriterThread");
            mmSocket = socket;
            mmByteOutput = new ByteArrayOutputStream(200000);
            mmOutStream = new DataOutputStream(mmByteOutput);
        }

        @Override
        public void run() {
            super.run();
            Log.i(TAG, "BEGIN WriterThread");
            setName("WriterThread");
            int c;
            // Keep listening to the InputStream while connected
            while (mState == STATE_CONNECTED) {
                TcpOutput tcpOutput = null;
                if (mOut != null) {
                    tcpOutput = mOut;
                    mOut = null;
                }
                if (tcpOutput != null) {
                    send(tcpOutput);
                }
            }
        }

        boolean sendAsync(TcpOutput out) {
            if (mOut != null) {
                return false;
            }
            mOut = out;
            return true;
        }

        void send(TcpOutput out) {
            try {
                out.writeTo(mmOutStream);
                mmOutStream.flush();

                byte[] sendData = mmByteOutput.toByteArray();
                mmByteOutput.reset();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, mAddress, mPort);
                mmSocket.send(sendPacket);

                mHandler.obtainMessage(MESSAGE_SEND, -1, -1, out).sendToTarget();
            } catch (IOException e) {
                Log.d(TAG, "Exception on send()", e);
                connectionLost();
            }
        }

        void cancel() {
            try {
                mmOutStream.write("$".getBytes());
                mmOutStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Failed to send communication end indication", e);
            }
            mmSocket.close();
        }
    }
}
