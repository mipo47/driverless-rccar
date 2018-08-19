package com.gokhanettin.driverlessrccar.caroid;

import android.provider.ContactsContract;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

class FileSaver {
    private static final String TAG = "FileSaver";

    String mDirectory;
    FileWriter mFileWriter;
    int mImageID = 0;

    FileSaver() {
        String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        mDirectory = baseDir + File.separator + "datasets" + File.separator + android.text.format.DateFormat.format("yyyy-MM-dd_HH_mm_ss", new java.util.Date());;
        File f = new File(mDirectory);
        if (!f.isDirectory())
            f.mkdirs();

        String fileName = "header.csv";
        String filePath = mDirectory + File.separator + fileName;

        try {
            mFileWriter = new FileWriter(filePath);
            mFileWriter.write("timestamp,image_id,online,speed,steering,distance,size,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,max_z,lat,lon\n");
            mFileWriter.flush();
        }
        catch (IOException exc) {
            Log.e(TAG, "Unable to create CSV file", exc);
        }
    }

    void save(ArduinoInput arduinoInput, AndroidInput androidInput) {
        mImageID++;

        byte[] jpeg = androidInput.Camera.getPreviewJpeg();

        String data = String.format(Locale.US, "%d,%d,%d,%d,%d,%.1f,%d",
                System.currentTimeMillis(), mImageID,
                arduinoInput.isOnline ? 1 : 0,
                arduinoInput.speedCommand, arduinoInput.steeringCommand, arduinoInput.distance,
                jpeg.length
        );
        for (float sensorValue: androidInput.SensorValues) {
            data += String.format(Locale.US, ",%.5f", sensorValue);
        }
        data += "\n";

        try {
            mFileWriter.write(data);
            mFileWriter.flush();
        }
        catch (IOException exc) {
            Log.e(TAG, "Unable to write into CSV file", exc);
        }

        try {
            FileOutputStream jpegStream = new FileOutputStream(mDirectory + File.separator + mImageID + ".jpg");
            jpegStream.write(jpeg);
            jpegStream.flush();
            jpegStream.close();
        }
        catch (IOException exc) {
            Log.e(TAG, "Unable to create JPEG file " + mImageID + ".jpg" , exc);
        }
    }
}
