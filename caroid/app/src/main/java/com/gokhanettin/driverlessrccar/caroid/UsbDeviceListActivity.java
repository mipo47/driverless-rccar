package com.gokhanettin.driverlessrccar.caroid;

import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.ArrayList;
import java.util.List;

public class UsbDeviceListActivity extends AppCompatActivity {
    public static final String TAG = "DeviceListActivity";
    public static final String EXTRA_BT_ADDRESS = "device_bt_address";
    private static final int REQUEST_ENABLE_BT = 0;
    private UsbManager mUsbManager = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_device_list);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if(mUsbManager == null) {
            Toast.makeText(getApplicationContext(),
                    "Usb is not available", Toast.LENGTH_LONG).show();
            finish();
        } else {
            populatePairedDeviceList();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "BT enabled, populating device list");
                populatePairedDeviceList();
            } else {
                Log.d(TAG, "User rejected to enable BT, finishing");
                finish();
            }
        }
    }

    private void populatePairedDeviceList() {
        Log.d(TAG, "Refreshing device list ...");

        final List<UsbSerialDriver> drivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

        final List<UsbSerialPort> pairedDevices = new ArrayList<UsbSerialPort>();
        for (final UsbSerialDriver driver : drivers) {
            final List<UsbSerialPort> ports = driver.getPorts();
            Log.d(TAG, String.format("+ %s: %s port%s",
                    driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
            pairedDevices.addAll(ports);
        }

        final ListView listViewPairedDevices =
                (ListView) findViewById(R.id.list_view_paired_devices);
        ArrayList<String> list = new ArrayList<>();
        if (pairedDevices.size() > 0) {
            // There are paired devices.
            for (UsbSerialPort port : pairedDevices) {
                list.add("COM" + port.getPortNumber());
            }

            final ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, list);

            listViewPairedDevices.setAdapter(adapter);
            listViewPairedDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    String address = ((TextView) view).getText().toString();
                    Intent data = new Intent();
                    data.putExtra(EXTRA_BT_ADDRESS, address);
                    setResult(RESULT_OK, data);
                    finish();
                }
            });
        } else {
            Toast.makeText(getApplicationContext(),
                    "No Connected USB Device Found.", Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
