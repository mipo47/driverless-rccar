package com.gokhanettin.driverlessrccar.caroid;

import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;

public class AcquisitionActivity extends AppCompatActivity {
    private static final String TAG = "AcquisitionActivity";
    private static final int REQUEST_CONNECTION = 0;

    // Will be initialized after ConnectionActivity
    public CameraPreview mCameraPreview;
    private CameraManager mCameraManager;

    private AndroidInput androidInput = new AndroidInput();
    private ArduinoInput arduinoInputStub = new ArduinoInput();

    private UsbManager mUsbManager;
    public UsbClient mUsbClient;
    private UdpClient mTcpClient;

    // Can be used to reconnect
    private UsbSerialPort mUsbSerialPort = null;
    private String mIP = null;
    private int mPort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_acquisition);

        mUsbClient = new UsbClient(mUsbHandler);
        mTcpClient = new UdpClient(mTcpHandler);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        androidInput.setSensors(this);

        Intent intent = new Intent(AcquisitionActivity.this, ConnectionActivity.class);
        Log.d(TAG, "Requesting usb and network connections");
        startActivityForResult(intent, REQUEST_CONNECTION);
    }

    // Called once after ConnectionActivity result in this.onActivityResult (order matters!)
    private void startStreaming() {
        if (mCameraPreview != null)
            return;;

        mCameraManager = new CameraManager();
        mCameraPreview = new CameraPreview(this, mCameraManager.getCamera());
        mCameraPreview.tcpClient = mTcpClient;
        androidInput.Camera = mCameraPreview;

        final FrameLayout previewLayout = (FrameLayout) findViewById(R.id.acquisition_preview);
        previewLayout.addView(mCameraPreview);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        if (mCameraPreview != null)
            mCameraPreview.setCamera(mCameraManager.getCamera());

        mTimer.start();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        mTimer.cancel();

        if (mCameraPreview != null) {
            mCameraPreview.setCamera(null);
            mCameraManager.releaseCamera();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (mUsbClient.getState() != UsbClient.STATE_NONE) {
            mUsbClient.disconnect();
        }
        if (mTcpClient.getState() != TcpClient.STATE_NONE) {
            mTcpClient.disconnect();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONNECTION) {
            if (resultCode == RESULT_OK) {
                String address = data.getStringExtra(ConnectionActivity.EXTRA_BT_ADDRESS);
                mIP = data.getStringExtra(ConnectionActivity.EXTRA_IP);
                mPort = data.getIntExtra(ConnectionActivity.EXTRA_PORT, 5000);
                if (mUsbClient.getState() == UsbClient.STATE_NONE && address != null) {
                    Log.d(TAG, "Connecting to usb device at " + address);

                    final List<UsbSerialDriver> drivers =
                            UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

                    mUsbSerialPort = null;
                    for (final UsbSerialDriver driver : drivers) {
                        final List<UsbSerialPort> ports = driver.getPorts();
                        for (final UsbSerialPort usbPort : ports) {
                            String portAddress = "COM" + usbPort.getPortNumber();
                            if (portAddress.equals(address)) {
                                mUsbSerialPort = usbPort;
                                break;
                            }
                        }
                    }

                    if (mUsbSerialPort != null) {
                        Log.d(TAG, "Connecting to " + address);
                        if (!mUsbClient.connect(mUsbManager, mUsbSerialPort, this)) {
                            Log.d(TAG, "Cannot connect to " + address);
                        }
                    } else {
                        Log.d(TAG, "Unable to find " + address);
                    }
                }
                if (mTcpClient.getState() == TcpClient.STATE_NONE) {
                    Log.d(TAG, "Connecting to server at " + mIP + ":" + mPort);
                    mTcpClient.connect(mIP, mPort);
                }
                startStreaming();
            } else {
                finish();
            }
        }
    }

    private final Handler.Callback mUsbHandlerCallback = new  UsbHandlerCallback() {
        @Override
        protected void onConnectionStateChanged(int newState) {
            String state = "";
            switch (newState) {
                case UsbClient.STATE_NONE:
                    state = "STATE_NONE";
                    break;
                case UsbClient.STATE_CONNECTING:
                    state = "STATE_CONNECTING";
                    break;
                case UsbClient.STATE_CONNECTED:
                    state = "STATE_CONNECTED";
                    break;
            }
            Log.d(TAG, "(Serial) onConnectionStateChanged: " + state);

        }

        @Override
        protected void onReceived(ArduinoInput arduinoInput) {
            Log.d(TAG, "(Serial) onReceived: " + arduinoInput.toString());
            if (mTcpClient.getState() == TcpClient.STATE_CONNECTED && mCameraPreview.getPreviewCount() > 0) {
                arduinoInput.isOnline = true;
                mTcpClient.send(arduinoInput, androidInput);
            }
        }

        @Override
        protected void onSent(ArduinoOutput output) {

        }

        @Override
        protected void onCommunicationModeChanged(String newMode) {
            Log.d(TAG, "(Serial) onCommunicationModeChanged: " + newMode);
        }

        @Override
        protected void onConnectionEstablished(String connectedDeviceName) {
            Log.d(TAG, "(Serial) onConnectionEstablished: " + connectedDeviceName);
            mUsbClient.requestCommunicationMode(UsbClient.MODE_MONITOR);
        }

        @Override
        protected void onConnectionError(String error) {
            Log.d(TAG, "(Serial) OnConnectionError: " + error);
            Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
        }
    };

    private final Handler.Callback mTcpHandlerCallback = new TcpHandlerCallback() {
        @Override
        protected void onConnectionStateChanged(int newState) {
            String state = "";
            switch (newState) {
                case TcpClient.STATE_NONE:
                    state = "STATE_NONE";
                    break;
                case TcpClient.STATE_CONNECTING:
                    state = "STATE_CONNECTING";
                    break;
                case TcpClient.STATE_CONNECTED:
                    state = "STATE_CONNECTED";
                    break;
            }
            Log.d(TAG, "(Tcp) onConnectionStateChanged: " + state);
        }

        @Override
        protected void onReceived(TcpInput input) {
            Log.d(TAG, "Running command " + input.command);
            input.command.run(AcquisitionActivity.this);
        }

        @Override
        protected void onSent(TcpOutput output) {
            //Log.d(TAG, "(Tcp) onSent: " + output.toString());
        }

        @Override
        protected void onConnectionEstablished(String serverAddress) {

        }

        @Override
        protected void onConnectionError(String error) {
            Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
        }
    };

    private final Handler mUsbHandler = new Handler(mUsbHandlerCallback);
    private final Handler mTcpHandler = new Handler(mTcpHandlerCallback);

    // 200 Hz, used when USB is disconnected
    private CountDownTimer mTimer = new CountDownTimer(10000001, 5) {
        @Override
        public void onTick(long millisUntilFinished) {
            if (mTcpClient.getState() == TcpClient.STATE_CONNECTED
                && mUsbClient.getState() != UsbClient.STATE_CONNECTED
                && androidInput.Camera.getPreviewCount() > 0)
            {
                mTcpClient.send(arduinoInputStub, androidInput);
            }

            // try to reconnect each 2 seconds
            if (millisUntilFinished % 2000 == 0) {
                if (mIP != null && mTcpClient.getState() == TcpClient.STATE_NONE) {
                    mTcpClient.connect(mIP, mPort);
                }

                if (mUsbSerialPort != null && mUsbClient.getState() == UsbClient.STATE_NONE) {
                    if (!mUsbClient.connect(mUsbManager, mUsbSerialPort, AcquisitionActivity.this)) {
                        Log.d(TAG, "Cannot reconnect to serial");
                    }
                }
            }
        }

        @Override
        public void onFinish() {
            start();
        }
    };
}
