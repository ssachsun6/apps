/**
 * 
 * Copyright 2013 Noisy Flowers LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * 
 * com.noisyflowers.android.soilanalysis.util
 * CameraPreview.java
 */

package com.noisyflowers.android.soilanalysis.util;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
	
	private static final String TAG = CameraPreview.class.getName();
	
	//private static final double ASPECT_RATIO = 3.0 / 4.0;
	private static final double ASPECT_RATIO = 4.0 / 3.0;
	
	private SurfaceHolder holder;
    public Camera camera;
    
    private int pWidth, pHeight;
    
    public CameraPreview(Context context, Camera camera, int width, int height) {
        super(context);
        this.camera = camera;
        
        Camera.Parameters cParams = camera.getParameters();
        Camera.Size cSize = cParams.getPreviewSize();
        Log.i(TAG, "cSize.width = " + cSize.width + ", cSize.height = " + cSize.height);
        //pWidth = cSize.width;
        //pHeight = cSize.height;
        pWidth = width;
        pHeight = height;
        Log.i(TAG, "pWidth = " + pWidth + ", pHeight = " + pHeight);

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        holder = getHolder();
        holder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }
    
    /***
	public static Camera getCameraInstance(){
	    Camera c = null;
		try {
	        c = Camera.open(); 
	    }
	    catch (Exception e){
	    	int x = 1;
	        // Camera is not available (in use or does not exist)
	    }
	    return c; 
	}	
	***/

	@Override
    public void onMeasure(int widthSpec, int heightSpec) {
    	/**
		int height = MeasureSpec.getSize(heightSpec);
    	int width = MeasureSpec.getSize(widthSpec);
    	if (width > height * ASPECT_RATIO) {
    		width = (int) (height * ASPECT_RATIO + 0.5);
    	} else {
    		height = (int)(width / ASPECT_RATIO + 0.5);
    	}
    	**/
        setMeasuredDimension(pWidth, pHeight);
	}
    
	
    public void surfaceCreated(SurfaceHolder holder) {
        /**/
    	try {
        	//camera = getCameraInstance();
            //Camera.Parameters cParams = camera.getParameters();
                   	
            //cParams.setPreviewSize(getMeasuredWidth(), getMeasuredHeight());
            //cParams.setPictureSize(getMeasuredWidth(), getMeasuredHeight());
        	//Camera.Size bestSize = getBestPreviewSize(cParams);
    		//Log.i(TAG, "bestSize = " + bestSize.width + "x" + bestSize.height);
            //cParams.setPreviewSize(bestSize.width, bestSize.height);
            //cParams.setPictureSize(bestSize.width, bestSize.height);
            //camera.setParameters(cParams);
        	camera.setPreviewDisplay(holder);
            camera.startPreview();
            
        	//This is already done in NewObservationActivity.onResume.  However, some older devices seem to 
            //need this extra kick after startPreview to get the zoom right.
            Camera.Parameters parameters = camera.getParameters();
        	if (parameters.isZoomSupported()) {
        		camera.setParameters(parameters);
        	}
        	
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
        /**/
    }

    /***
    private Size getBestPreviewSize(Camera.Parameters cParams ) {
        List<Size> sizes = cParams.getSupportedPreviewSizes();
        
        Size bestSize = null;
        
        for(Size s : sizes){
        	if((double)s.width/s.height == ASPECT_RATIO) {
        		if (bestSize == null ||
        			s.width * s.height > bestSize.width * bestSize.height) {
        			bestSize = s;
        		}
        	}
        }
        return bestSize;    	
    }
    ***/
    
    public void surfaceDestroyed(SurfaceHolder holder) {
		//if (camera != null) {
			//camera.stopPreview();
			//camera.release();
        	//camera.setPreviewCallback(null); 
			//camera = null;
		//}
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (holder.getSurface() == null){
          // preview surface does not exist
          return;
        }

        // stop preview before making changes
        try {
            camera.stopPreview();
        } catch (Exception e){
          // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();

        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }
    
    
}
