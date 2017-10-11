package com.gokhanettin.driverlessrccar.caroid;

import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
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

    private UsbClient mUsbClient;
    private TcpClient mTcpClient;
    private CameraPreview mCameraPreview;
    private CameraManager mCameraManager;
    private AndroidInput androidInput = new AndroidInput();
    private boolean mIsTcpSendOk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_acquisition);

        mCameraManager = new CameraManager();
        // Create our Preview view and set it as the content of our activity.
        mCameraPreview = new CameraPreview(this, mCameraManager.getCamera());
        androidInput.Camera = mCameraPreview;
        final FrameLayout previewLayout = (FrameLayout) findViewById(R.id.acquisition_preview);
        previewLayout.addView(mCameraPreview);
        mUsbClient = new UsbClient(mUsbHandler);
        mTcpClient = new TcpClient(mTcpHandler);
        Intent intent = new Intent(AcquisitionActivity.this, ConnectionActivity.class);
        Log.d(TAG, "Requesting bluetooth and network connections");
        startActivityForResult(intent, REQUEST_CONNECTION);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        mCameraPreview.setCamera(mCameraManager.getCamera());
        int btState = mUsbClient.getState();
        int tcpState = mTcpClient.getState();
        if (btState == UsbClient.STATE_CONNECTED
                || tcpState == TcpClient.STATE_CONNECTED) {
            mIsTcpSendOk = true;
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        mIsTcpSendOk = false;
        mCameraPreview.setCamera(null);
        mCameraManager.releaseCamera();
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
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
                String ip = data.getStringExtra(ConnectionActivity.EXTRA_IP);
                int port = data.getIntExtra(ConnectionActivity.EXTRA_PORT, 5555);
                if (mUsbClient.getState() == UsbClient.STATE_NONE) {
                    Log.d(TAG, "Connecting to usb device at " + address);

                    final UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                    final List<UsbSerialDriver> drivers =
                            UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

                    UsbSerialPort selectedPort = null;
                    for (final UsbSerialDriver driver : drivers) {
                        final List<UsbSerialPort> ports = driver.getPorts();
                        for (final UsbSerialPort usbPort : ports) {
                            String portAddress = "COM" + usbPort.getPortNumber();
                            if (portAddress.equals(address)) {
                                selectedPort = usbPort;
                                break;
                            }
                        }
                    }

                    if (selectedPort != null) {
                        Log.d(TAG, "Connecting to " + address);
                        if (!mUsbClient.connect(mUsbManager, selectedPort, this)) {
                            Log.d(TAG, "Cannot connect to " + address);
                        }
                    } else {
                        Log.d(TAG, "Unable to find " + address);
                    }
                }
                if (mTcpClient.getState() == TcpClient.STATE_NONE) {
                    Log.d(TAG, "Connecting to server at " + ip + ":" + port);
                    mTcpClient.connect(ip, port);
                }
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
            if (mIsTcpSendOk) {
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

        }

        @Override
        protected void onSent(TcpOutput output) {
            Log.d(TAG, "(Tcp) onSent: " + output.toString());
        }

        @Override
        protected void onConnectionEstablished(String serverAddress) {
            mIsTcpSendOk = true;
        }

        @Override
        protected void onConnectionError(String error) {
            Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
            mIsTcpSendOk = false;
        }
    };

    private final Handler mUsbHandler = new Handler(mUsbHandlerCallback);
    private final Handler mTcpHandler = new Handler(mTcpHandlerCallback);
}
