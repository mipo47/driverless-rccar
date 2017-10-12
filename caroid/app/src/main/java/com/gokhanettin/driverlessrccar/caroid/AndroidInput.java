package com.gokhanettin.driverlessrccar.caroid;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class AndroidInput {
    private static final String TAG = "AndroidInput";

    public CameraPreview Camera;
    public float[] SensorValues;

    private SensorManager mSensorManager;
    private List<SensorListener> sensors = new ArrayList<>();
    LocationManager locationManager;

    public void setSensors(Context context) {
        if (mSensorManager != null) {
            Log.d(TAG, "Sensors are already set");
            return;
        }
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        SensorValues = new float[11];
        sensors.add(new SensorListener(mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 0));
        sensors.add(new SensorListener(mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), 3));
        sensors.add(new SensorListener(mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), 6));

        Log.d(TAG, "ini LocationManager");
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new MyLocationListener(9);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "You have no persmissions to access GPS location");
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
    }

    class SensorListener implements SensorEventListener {
        Sensor mSensor;
        int mIndex;

        public SensorListener(Sensor sensor, int index) {
            mSensor = sensor;
            mIndex = index;
            mSensorManager.registerListener(this, sensor , SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            System.arraycopy(sensorEvent.values, 0, SensorValues, mIndex, sensorEvent.values.length);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {}
    }

    /*---------- Listener class to get coordinates ------------- */
    private class MyLocationListener implements LocationListener {
        int mIndex;

        public MyLocationListener(int index) {
            mIndex = index;
        }

        @Override
        public void onLocationChanged(Location loc) {
            SensorValues[mIndex + 0] = (float) loc.getLatitude();
            SensorValues[mIndex + 1] = (float) loc.getLongitude();
        }

        @Override
        public void onProviderDisabled(String provider) {
            SensorValues[mIndex + 0] = -1.0f;
            SensorValues[mIndex + 1] = -1.0f;
        }

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    }
}
