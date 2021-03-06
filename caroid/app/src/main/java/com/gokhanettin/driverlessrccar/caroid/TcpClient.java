package com.gokhanettin.driverlessrccar.caroid;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;

public class TcpClient {
    private static final String TAG = "TcpClient";

    // Message types sent from the TcpClient to activities
    public static final int MESSAGE_CONNECTION_STATE_CHANGE = 0;
    public static final int MESSAGE_RECEIVE = 1;
    public static final int MESSAGE_SEND = 2;
    public static final int MESSAGE_CONNECTION_ESTABLISHED = 3;
    public static final int MESSAGE_CONNECTION_ERROR = 4;

    // Key names to identify some messages
    public static final String SERVER_ADDRESS = "server_address";
    public static final String CONNECTION_ERROR = "tcp_conn_error";

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

    public TcpClient(Handler handler) {
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
            Log.d(TAG, "TCP is busy, skip sending");
        }
        // sliding average
        sendProbability = sendProbability * 0.995f + 0.005f * currentProbability;
    }

    private synchronized void connected(Socket socket) {
        String serverAddress = socket.getInetAddress().toString();
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
        Socket mmSocket;

        public ConnectThread(String ip, int port) {
            mmIP = ip;
            mmPort = port;
            mmSocket = new Socket();
        }

        @Override
        public void run() {
            super.run();

            try {
                mmSocket.connect(new InetSocketAddress(mmIP, mmPort), 10000);
            } catch (IOException e) {
                Log.e(TAG, "Failed to connect to " + mmIP + ":" + mmPort, e);
                connectionFailed();
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "Unable to close() socket on connection failure", e2);
                }
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (TcpClient.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket);
        }

        void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() failed at ConnectThread.cancel()", e);
            }
        }
    }

    private class ReaderThread extends Thread {
        private final Socket mmSocket;
        private final DataInputStream mmInStream;

        private StringBuilder mmStringBuilder;
        private boolean mmValid;

        public ReaderThread(Socket socket) {
            Log.d(TAG, "create ReaderThread");
            mmSocket = socket;
            InputStream tmpIn = null;

            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Tmp in/out streams not created", e);
            }

            mmInStream = new DataInputStream(tmpIn);
            mState = STATE_CONNECTED;
            mmStringBuilder = new StringBuilder();
            mmValid = false;
        }

        @Override
        public void run() {
            super.run();
            Log.i(TAG, "BEGIN mReaderThread");
            setName("ReaderThread");
            int c;
            // Keep listening to the InputStream while connected
            while (mState == STATE_CONNECTED) {
                try {
                    // Read from the InputStream
                    // "[<throttle_cmd>;<steering_cmd>]"
                    while (mmInStream.available() > 0) {
                        c = mmInStream.read();
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
        private final Socket mmSocket;
        private final DataOutputStream mmOutStream;
        private TcpOutput mOut;

        public WriterThread(Socket socket) {
            Log.d(TAG, "create WriterThread");
            mmSocket = socket;
            OutputStream tmpOut = null;

            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Tmp in/out streams not created", e);
            }

            mmOutStream = new DataOutputStream(tmpOut);
        }

        @Override
        public void run() {
            super.run();
            Log.i(TAG, "BEGIN mReaderThread");
            setName("ReaderThread");
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
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed at ReaderThread.cancel()", e);
            }
        }
    }
}
