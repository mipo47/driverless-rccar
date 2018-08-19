package com.gokhanettin.driverlessrccar.caroid;


import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by gokhanettin on 01.04.2017.
 */

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "CameraPreview";
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private Camera.Size mPreviewSize;
    private int mMaxFps;
    private final LinkedList<byte[]> mQueue = new LinkedList<>();
    private static final int MAX_QUEUE_SIZE = 2;
    private static final int BUFFER_COUNT = 4;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;
        mHolder = getHolder();

        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        Camera.Parameters params = mCamera.getParameters();
        List<Camera.Size> sizes = params.getSupportedPreviewSizes();
        Camera.Size size = sizes.get(0);
        for (Camera.Size s : sizes) {
            Log.d(TAG, "Supported preview size " + s.width + ", " + s.height);
            if (s.width >= 720 && (size.width > s.width || size.height > s.height)) {
                size = s;
            }
        }

        List<int[]> fpsRanges = params.getSupportedPreviewFpsRange();
        mMaxFps = fpsRanges.get(0)[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
        for (int[] fpsRange : fpsRanges) {
            int mx = fpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
            int mn = fpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
            Log.d(TAG, "Supported fps range " + mx + ", " + mn);
            if (mMaxFps < mx) {
                mMaxFps = mx;
            }
        }
        params.setPreviewSize(size.width, size.height); // Smaller is better
        params.setPreviewFpsRange(mMaxFps, mMaxFps);
        params.setRecordingHint(true);

//        if (params.isAutoExposureLockSupported() )
//            params.setAutoExposureLock(true);

        mCamera.setParameters(params);

        mPreviewSize = mCamera.getParameters().getPreviewSize();
        Log.d(TAG, "Preview size is set to " + mPreviewSize.width + ", " + mPreviewSize.height);

        int[] fpsRange = new int[2];
        mCamera.getParameters().getPreviewFpsRange(fpsRange);
        Log.d(TAG, "Fps Range is set to " + fpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX] +
                ", " + fpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.e(TAG, "Error setting camera preview: ", e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
            clearQueue();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            for (int i = 0; i < BUFFER_COUNT; i++)
                mCamera.addCallbackBuffer(new byte[getPreviewWidth() * getPreviewHeight() * 3]);

            mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e){
            Log.e(TAG, "Error starting camera preview: ", e);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void setCamera(Camera camera) {
        if (camera == null) {
            mCamera.setPreviewCallback(null);
            mHolder.removeCallback(this);
        } else {
            mHolder.addCallback(this);
            Camera.Parameters params = camera.getParameters();
            params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            params.setPreviewFpsRange(mMaxFps, mMaxFps);
            camera.setParameters(params);
        }
        mCamera = camera;
    }

    public byte[] getPreview() {
        byte[] preview = null;
        synchronized (mQueue) {
            if (mQueue.size() > 0) {
                preview = mQueue.poll();
            }
        }
        return preview;
    }

    public int getPreviewWidth() {
        return mPreviewSize.width;
    }

    public int getPreviewHeight() {
        return mPreviewSize.height;
    }

    public int getPreviewFps() {
        return mMaxFps;
    }

    private void clearQueue() {
        synchronized (mQueue) {
            mQueue.clear();
        }
    }

    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            // TODO Auto-generated method stub
            if (data.length >= getPreviewWidth() * getPreviewHeight()) {
                synchronized (mQueue) {
                    if (mQueue.size() == MAX_QUEUE_SIZE) {
                        mQueue.poll();
                    }
                    mQueue.add(data);
                }
            }
            mCamera.addCallbackBuffer(data);
        }
    };

    public static byte[] previewToJpeg(byte[] preview, int width, int height) {
        byte[] jpeg = null;
        YuvImage image = new YuvImage(preview, ImageFormat.NV21, width, height, null);
        Rect r = new Rect(0, 0, width, height);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean ok = image.compressToJpeg(r, 80, baos);
        if (ok) {
            jpeg = baos.toByteArray();
        }
        return jpeg;
    }
}
