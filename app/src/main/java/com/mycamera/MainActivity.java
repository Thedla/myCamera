package com.mycamera;

import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import com.mycamera.databinding.ActivityMainBinding;

import java.util.Collections;

public class MainActivity extends AppCompatActivity implements
        TextureView.SurfaceTextureListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    ActivityMainBinding binding;
    CameraManager mCameraManager;
    CameraCharacteristics mCharacteristics;
    CameraDevice.StateCallback mCameraDeviceStateCallback;
    CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback;
    CameraDevice mCameraDevice;
    CameraCaptureSession mCameraCaptureSession;
    CaptureRequest.Builder mCaptureRequestBuilder;
    CaptureRequest mCaptureRequest;
    Size mPreviewSize = null;
    HandlerThread mHandlerThread;
    Handler mHandler;

    String cameraId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.textureView.setSurfaceTextureListener(this);
        mCameraDevicestateCallBack();
    }

    private void setmCameraManager() {
        if (mCameraManager == null) {
            mCameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        }
    }

    private String[] getCameraList() {
        if (mCameraManager == null) {
            setmCameraManager();
        }
        try {
            return mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return new String[]{};
        }
    }

    private boolean checkCameraBackFacing() {
        String[] cam_list = getCameraList();
        if (cam_list.length > 0) {
            for (String facing : cam_list) {
                try {
                    mCharacteristics = mCameraManager.getCameraCharacteristics(facing);
                    Integer cam_facing = mCharacteristics.get(CameraCharacteristics.LENS_FACING);
                    if (cam_facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraId = facing;
                        return true;
                    }
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        setUpCamera();
        mOpenCamera();
    }


    private void setUpCamera() {
        if (checkCameraBackFacing() && mCharacteristics != null) {
            StreamConfigurationMap mStreamConfig = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = mStreamConfig.getOutputSizes(SurfaceTexture.class);
            if (sizes.length > 0) {
                mPreviewSize = sizes[0];
                for (Size mSize : sizes) {
                    Log.i(TAG, "sizes are" + mSize.getHeight() + "width" + mSize.getWidth());
                }

            }
        }

    }

    private void mOpenCamera() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {

            if (mCameraManager != null && !cameraId.isEmpty()) {
                try {
                    mCameraManager.openCamera(cameraId, mCameraDeviceStateCallback, mHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void openBackgroundThread() {
        mHandlerThread = new HandlerThread("camera_background_thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }


    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    private void mCameraDevicestateCallBack() {
        mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                mCameraDevice = cameraDevice;
                mCreatePreviewSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                cameraDevice.close();
                mCameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int i) {
                cameraDevice.close();
                mCameraDevice = null;
            }
        };
    }

    private void mCreatePreviewSession() {
        SurfaceTexture mSurfaceTexture = binding.textureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface mSurface = new Surface(mSurfaceTexture);
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(mSurface);
            mCaptureSessionStateCallbacks();
            mCameraDevice.createCaptureSession(Collections.singletonList(mSurface), mCameraCaptureSessionStateCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    private void mCaptureSessionStateCallbacks() {
        mCameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                if (mCameraDevice == null)
                    return;
                if (mCaptureRequestBuilder != null) {
                    mCaptureRequest = mCaptureRequestBuilder.build();
                    mCameraCaptureSession = cameraCaptureSession;
                    try {
                        mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, mHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding.textureView.isAvailable()) {
            setUpCamera();
            mOpenCamera();
        } else {
            binding.textureView.setSurfaceTextureListener(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onStop() {
        super.onStop();
        mCloseCamera();
        mCloseBackgroundThread();
    }

    private void mCloseCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void mCloseBackgroundThread() {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
            mHandler = null;
        }
    }
}
