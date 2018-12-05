package com.mycamera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Environment;
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
import android.view.View;

import com.mycamera.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

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
    private StreamConfigurationMap mStreamConfig;
    File galleryFolder;
    private int MY_PERMISSIONS_REQUEST=100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.textureView.setSurfaceTextureListener(this);

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
               Log.i("TAG","cameraCaptureSession fail");
            }
        };

        binding.shutterImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FileOutputStream outputPhoto = null;
                try {
                    createImageGallery();
                    outputPhoto = new FileOutputStream(createImageFile(galleryFolder));
                    binding.textureView.getBitmap()
                            .compress(Bitmap.CompressFormat.PNG, 100, outputPhoto);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (outputPhoto != null) {
                            outputPhoto.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
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
             mStreamConfig = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = mStreamConfig.getOutputSizes(SurfaceTexture.class);
            if (sizes.length > 0) {
                mPreviewSize = sizes[0];
                for (Size mSize : sizes) {
                    Log.i(TAG, "height " + mSize.getHeight() + " width " + mSize.getWidth());
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
        }else{
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST);
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
    
    private void mCreatePreviewSession() {
        SurfaceTexture mSurfaceTexture = binding.textureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface mSurface = new Surface(mSurfaceTexture);
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(mSurface);
            mCameraDevice.createCaptureSession(Collections.singletonList(mSurface), mCameraCaptureSessionStateCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        openBackgroundThread();
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

    private void createImageGallery() {
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        galleryFolder=null;
        galleryFolder = new File(storageDirectory, getResources().getString(R.string.app_name));
        if (!galleryFolder.exists()) {
            boolean wasCreated = galleryFolder.mkdirs();
            if (!wasCreated) {
                Log.e("CapturedImages", "Failed to create directory");
            }
        }
    }

    private File createImageFile(File galleryFolder) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "image_" + timeStamp + "_";
        return File.createTempFile(imageFileName, ".jpg", galleryFolder);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    }
}
