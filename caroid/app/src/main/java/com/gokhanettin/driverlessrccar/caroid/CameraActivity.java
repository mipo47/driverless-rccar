package com.gokhanettin.driverlessrccar.caroid;

import android.content.DialogInterface;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";
    private CameraPreview mCameraPreview;
    private CameraManager mCameraManager;
    private TcpClient mTcpClient;
    private AlertDialog mServerDialog;
    private String mIP = null;
    private int mPort;
    private AndroidInput androidInput = new AndroidInput();
    private ArduinoInput arduinoInput = new ArduinoInput();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mCameraManager = new CameraManager();
        // Create our Preview view and set it as the content of our activity.
        mCameraPreview = new CameraPreview(this, mCameraManager.getCamera());
        androidInput.Camera = mCameraPreview;
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mCameraPreview);
        mTcpClient = new TcpClient(mHandler);
        createServerDialog();
        Log.d(TAG, "Asking for server address");
        mServerDialog.show();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        mCameraPreview.setCamera(mCameraManager.getCamera());
        int state = mTcpClient.getState();
        if (state == TcpClient.STATE_CONNECTED) {
            mTimer.start();
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        mTimer.cancel();
        mCameraPreview.setCamera(null);
        mCameraManager.releaseCamera();
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        if (mTcpClient.getState() != TcpClient.STATE_NONE) {
            mTcpClient.disconnect();
        }
        mServerDialog.dismiss();
    }

    private void createServerDialog() {
        LayoutInflater infilater = this.getLayoutInflater();
        final View textEntryView = infilater.inflate(R.layout.server_address, null);
        mServerDialog  =  new AlertDialog.Builder(this)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle("Server Address")
                .setView(textEntryView)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        EditText ipEdit = (EditText)textEntryView.findViewById(R.id.ip_edit);
                        EditText portEdit = (EditText)textEntryView.findViewById(R.id.port_edit);
                        mIP = ipEdit.getText().toString();
                        mPort = Integer.parseInt(portEdit.getText().toString());
                        mTcpClient.connect(mIP, mPort);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        finish();
                    }
                })
                .create();
    }

    private final Handler.Callback mHandlerCallback = new TcpHandlerCallback() {
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
            Log.d(TAG, "onConnectionStateChanged: " + state);
        }

        @Override
        protected void onReceived(TcpInput input) {

        }

        @Override
        protected void onSent(TcpOutput output) {

        }

        @Override
        protected void onConnectionEstablished(String serverAddress) {
            mTimer.start();
        }

        @Override
        protected void onConnectionError(String error) {
            Log.d(TAG, "OnConnectionError: " + error);
            Toast.makeText(getApplication(), error, Toast.LENGTH_LONG).show();
            mTimer.cancel();
        }
    };

    private final Handler mHandler = new Handler(mHandlerCallback);

    // 30 Hz
    private CountDownTimer mTimer = new CountDownTimer(10001, 33) {
        @Override
        public void onTick(long millisUntilFinished) {
            if (mTcpClient.getState() == TcpClient.STATE_CONNECTED && androidInput.Camera.getPreviewCount() > 0) {
                mTcpClient.send(arduinoInput, androidInput);
            }
        }

        @Override
        public void onFinish() {
            start();
        }
    };
}
