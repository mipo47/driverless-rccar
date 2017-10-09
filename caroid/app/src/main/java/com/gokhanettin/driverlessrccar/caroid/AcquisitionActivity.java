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
import java.util.Locale;

public class AcquisitionActivity extends AppCompatActivity {
    private static final String TAG = "AcquisitionActivity";
    private static final int REQUEST_CONNECTION = 0;

    private UsbClient mBluetoothClient;
    private TcpClient mTcpClient;
    private CameraPreview mCameraPreview;
    private CameraManager mCameraManager;
    private boolean mIsTcpSendOk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_acquisition);

        mCameraManager = new CameraManager();
        // Create our Preview view and set it as the content of our activity.
        mCameraPreview = new CameraPreview(this, mCameraManager.getCamera());
        final FrameLayout previewLayout = (FrameLayout) findViewById(R.id.acquisition_preview);
        previewLayout.addView(mCameraPreview);
        mBluetoothClient = new UsbClient(mBluetoothHandler);
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
        int btState = mBluetoothClient.getState();
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
        if (mBluetoothClient.getState() != UsbClient.STATE_NONE) {
            mBluetoothClient.disconnect();
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
                if (mBluetoothClient.getState() == UsbClient.STATE_NONE) {
                    Log.d(TAG, "Connecting to bluetooth  device at " + address);

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
                        if (!mBluetoothClient.connect(mUsbManager, selectedPort, this)) {
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

    private final Handler.Callback mBluetoothHandlerCallback = new  BluetoothHandlerCallback() {
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
            Log.d(TAG, "(Bluetooth) onConnectionStateChanged: " + state);

        }

        @Override
        protected void onReceived(int speedCmd, int steeringCmd,
                                  float speed, float steering) {
            Locale locale = Locale.US;
            Log.d(TAG, "(Bluetooth) onReceived: " + String.format(locale,
                    "[%d;%d;%.3f;%.3f]", speedCmd, steeringCmd, speed, steering));
            if (mIsTcpSendOk) {
                mTcpClient.send(speedCmd, steeringCmd, speed, steering, mCameraPreview);
            }
        }

        @Override
        protected void onSent(int speedCmd, int steeringCmd) {

        }

        @Override
        protected void onCommunicationModeChanged(String newMode) {
            Log.d(TAG, "(Bluetooth) onCommunicationModeChanged: " + newMode);
        }

        @Override
        protected void onConnectionEstablished(String connectedDeviceName) {
            Log.d(TAG, "(Bluetooth) onConnectionEstablished: " + connectedDeviceName);
            mBluetoothClient.requestCommunicationMode(UsbClient.MODE_MONITOR);
        }

        @Override
        protected void onConnectionError(String error) {
            Log.d(TAG, "(Bluetooth) OnConnectionError: " + error);
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
        protected void onReceived(int speedCmd, int steeringCmd) {

        }

        @Override
        protected void onSent(int speedCmd, int steeringCmd,
                              float speed, float steering, byte[] jpeg) {
            Log.d(TAG, "(Tcp) onSent: " + String.format(Locale.US, "header=[%d;%d;%.3f;%.3f;%d]",
                    speedCmd, steeringCmd, speed, steering, jpeg.length));
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

    private final Handler mBluetoothHandler = new Handler(mBluetoothHandlerCallback);
    private final Handler mTcpHandler = new Handler(mTcpHandlerCallback);
}
