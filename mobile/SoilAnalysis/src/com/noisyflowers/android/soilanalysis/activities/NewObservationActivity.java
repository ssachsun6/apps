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
 * com.noisyflowers.android.soilanalysis.activities
 * NewObservationActivity.java
 */

package com.noisyflowers.android.soilanalysis.activities;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.noisyflowers.android.soilanalysis.R;
import com.noisyflowers.android.soilanalysis.SoilColorApplication;
import com.noisyflowers.android.soilanalysis.util.CameraPreview;
import com.noisyflowers.android.soilanalysis.util.CameraPreviewOverlay;
import com.noisyflowers.android.soilanalysis.util.ColorConstants;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class NewObservationActivity extends Activity {
	
	private static final String TAG = NewObservationActivity.class.getName();

	private static final double ASPECT_RATIO = 4.0 / 3.0;
	private static final int KNOWN_GENERIC_18_VALUE = 0x93; //7f8586
	//private static final int KNOWN_GENERIC_18_VALUE = 0xff; //for testing
	//private static final int KNOWN_WHIBAL_VALUE = 0xb8; //provided by WhiBal
	private static final int KNOWN_WHIBAL_VALUE = 0xc1;  //measured
	private static final int RGB_MASK = 0xffffff;
	private static final String ACTION_GET_SOIL_COLOR = "com.noisyflowers.android.soilanalysis.ACTION_GET_SOIL_COLOR";
	
	private Camera camera = null;
	private CameraPreview cameraPreview;
	private CameraPreviewOverlay overlayView;
	
	private Context context;
	
    FrameLayout preview;
	int displayHeight, displayWidth;
	float previewHeightFactor;
	float previewWidthFactor;
	float pictureHeightFactor;
	float pictureWidthFactor;
	//private boolean usePreview = false;
	//private boolean useYuvImage = true;
	//private boolean useWhiBal = true;
	private boolean externalCall = false;
	private int soilColor;
	
	private SoilColorApplication application;
			
	private void sRGBToLinearRGB (Bitmap bitmap) {
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				int c = bitmap.getPixel(x, y);
				c = sRGBToLinearRGB(c);
				bitmap.setPixel(x, y, c);
			}
		}
	}
	
	private void linearRGBTosRGB (Bitmap bitmap) {
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				int c = bitmap.getPixel(x, y);
				c = linearRGBTosRGB(c);
				bitmap.setPixel(x, y, c);
			}
		}
	}

	private int sRGBToLinearRGB(int c){
		double[] rgb = new double[3];
		rgb[0] = Color.red(c)/255.0;
		rgb[1] = Color.green(c)/255.0;
		rgb[2] = Color.blue(c)/255.0;
		
		for (int i = 0; i < rgb.length; i++) {
			if (rgb[i] <= 0.04045)
				rgb[i] = rgb[i]/12.92;
			else
				rgb[i] = Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
		}
		
		return Color.rgb((int)Math.round(rgb[0] * 255), (int)Math.round(rgb[1] * 255), (int)Math.round(rgb[2] * 255));
	}
	
	private int linearRGBTosRGB(int c) {
		double[] rgb = new double[3];
		rgb[0] = Color.red(c)/255.0;
		rgb[1] = Color.green(c)/255.0;
		rgb[2] = Color.blue(c)/255.0;
		
		for (int i = 0; i < rgb.length; i++) {
			if (rgb[i] <= 0.0031308)
				rgb[i] = rgb[i] * 12.92;
			else
				rgb[i] = 1.055 * Math.pow(rgb[i], 1/2.4) - 0.055;
		}
		
		return Color.rgb((int)Math.round(rgb[0] * 255), (int)Math.round(rgb[1] * 255), (int)Math.round(rgb[2] * 255));
	}

	private int avgColorHSV(Bitmap bitmap, float[] hsvCorrection, boolean filter){
		int retVal = -1;
		
		long pixelCount = 0;
		long redBucket = 0;
		long greenBucket = 0;
		long blueBucket = 0;
		
		if (hsvCorrection == null) {
			hsvCorrection = new float[]{0,0,0};
		}
				
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				float[] hsv = new float[3];
				Color.colorToHSV(bitmap.getPixel(x, y), hsv);
				hsv[0] = hsv[0] + hsvCorrection[0] < 0 ? 360 - (hsv[0] + hsvCorrection[0]) : (hsv[0] + hsvCorrection[0] > 360 ? (hsv[0] + hsvCorrection[0]) - 360 : hsv[0] + hsvCorrection[0]);
				//hsv[1] = hsv[1] + hsvCorrection[1] < 0 ? 0 : (hsv[1] + hsvCorrection[1] > 1 ? 1 : hsv[1] + hsvCorrection[1]);
				hsv[2] = hsv[2] + hsvCorrection[2] < 0 ? 0 : (hsv[2] + hsvCorrection[2] > 1 ? 1 : hsv[2] + hsvCorrection[2]);
				int c = Color.HSVToColor(hsv);
				bitmap.setPixel(x, y, c);
			}
		}

		double threshold = filter && application.useWhiBal ? HSVStdDevBounds(bitmap)[0] : 0;
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				int c = bitmap.getPixel(x, y);
				float[] hsv = new float[3];
				Color.colorToHSV(c, hsv);
				if (hsv[2] > threshold) {
				//if ((c & RGB_MASK) > threshold) {
					int adjustedColor = Color.red(c);
					redBucket += adjustedColor > 255 ? 255 : (adjustedColor < 0 ? 0 : adjustedColor);
					adjustedColor = Color.green(c);
					greenBucket += adjustedColor > 255 ? 255 : (adjustedColor < 0 ? 0 : adjustedColor);
					adjustedColor = Color.blue(c);
					blueBucket += adjustedColor > 255 ? 255 : (adjustedColor < 0 ? 0 : adjustedColor);
					++pixelCount;
				} else {
					int z = 1; //for bkpt
				}
			}
		}
		retVal = Color.rgb((int)(redBucket/pixelCount), (int)(greenBucket/pixelCount), (int)(blueBucket/pixelCount));
		return retVal;
	}

	
	private int avgColor(Bitmap bitmap, boolean filter) {
		int retVal = -1;
		
		long pixelCount = 0;
		long redBucket = 0;
		long greenBucket = 0;
		long blueBucket = 0;

		//TODO:  consider dropping useWhiBal from here.  
		double threshold = filter && application.useWhiBal ? HSVStdDevBounds(bitmap)[0] : 0;
		
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				int c = bitmap.getPixel(x, y);
				float[] hsv = new float[3];
				Color.colorToHSV(c, hsv);
				if (hsv[2] > threshold) {
				//if ((c & RGB_MASK) > threshold) {
					int adjustedColor = Color.red(c);
					redBucket += adjustedColor > 255 ? 255 : (adjustedColor < 0 ? 0 : adjustedColor);
					adjustedColor = Color.green(c);
					greenBucket += adjustedColor > 255 ? 255 : (adjustedColor < 0 ? 0 : adjustedColor);
					adjustedColor = Color.blue(c);
					blueBucket += adjustedColor > 255 ? 255 : (adjustedColor < 0 ? 0 : adjustedColor);
					++pixelCount;
				} else {
					int z = 1; //for bkpt
				}
			}
		}
		if (pixelCount > 0) {
			retVal = Color.rgb((int)(redBucket/pixelCount), (int)(greenBucket/pixelCount), (int)(blueBucket/pixelCount));
		}
		return retVal;		
	}
	
	/***
	private void adjustBitmap(Bitmap bitmap, float[] colorCoefs, Float brightnessCorrection){		
		if (colorCoefs == null) {
			colorCoefs = new float[]{1,1,1};
		}
		
		if (brightnessCorrection == null) {
			brightnessCorrection = (float)0;
		}
		
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				//int c = bitmap.getPixel(x, y);
				int c = sRGBToLinearRGB(bitmap.getPixel(x, y));
				int adjustedRed = ((int)(Color.red(c) * colorCoefs[0]));
				int adjustedGreen = ((int)(Color.green(c) * colorCoefs[1]));
				int adjustedBlue = ((int)(Color.blue(c) * colorCoefs[2]));
	        	//for luma c = Color.rgb((int)Math.round(adjustedRed + (0.21 * brightnessCorrection)), (int)Math.round(adjustedGreen + (0.72 * brightnessCorrection)), (int)Math.round(adjustedBlue + (0.07 * brightnessCorrection)));
				c = Color.rgb(adjustedRed, adjustedGreen, adjustedBlue);
				float[] hsv = new float[3];
				Color.colorToHSV(c, hsv);
				hsv[2] = hsv[2] + brightnessCorrection > 1 ? 1 : (hsv[2] + brightnessCorrection < 0 ? 0 : hsv[2] + brightnessCorrection);
				c = linearRGBTosRGB(Color.HSVToColor(hsv));
				//float[] hsl = sRGBToHSL(c);
				//hsl[2] = hsl[2] + brightnessCorrection > 1 ? 1 : (hsl[2] + brightnessCorrection < 0 ? 0 : hsl[2] + brightnessCorrection);
				//c = HSLTosRGB(hsl);
				bitmap.setPixel(x, y, c);
			}
		}
	}
	***/
	private void adjustBitmap(Bitmap bitmap, float[] colorCoefs, Float brightnessCorrection){		
		if (colorCoefs == null) {
			colorCoefs = new float[]{1,1,1};
		}
		
		if (brightnessCorrection == null) {
			brightnessCorrection = (float)0;
		}
		
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				//get sRGB color from pixel
				int sRGBc = bitmap.getPixel(x, y);
				//convert to linear
				int linearc = sRGBToLinearRGB(sRGBc);
				
				//apply color adjust to linear color
				int adjustedRed = ((int)(Color.red(linearc) * colorCoefs[0]));
				int adjustedGreen = ((int)(Color.green(linearc) * colorCoefs[1]));
				int adjustedBlue = ((int)(Color.blue(linearc) * colorCoefs[2]));
				linearc = Color.rgb(adjustedRed, adjustedGreen, adjustedBlue);
				
				//convert new color to sRGB
				sRGBc = linearRGBTosRGB(linearc);
				//apply brightness adjust to new sRGB and return it
				float[] hsv = new float[3];
				Color.colorToHSV(sRGBc, hsv);
				hsv[2] = hsv[2] + brightnessCorrection > 1 ? 1 : (hsv[2] + brightnessCorrection < 0 ? 0 : hsv[2] + brightnessCorrection);
				sRGBc = Color.HSVToColor(hsv);
				bitmap.setPixel(x, y, sRGBc);
			}
		}
	}
		
	private int[] sRGBStdDevBounds(Bitmap bitmap) {
		long pixelCount = 0;
		long total = 0;
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				int c = bitmap.getPixel(x, y) & RGB_MASK;
				total += c;
				++pixelCount;
			}
		}
		
		int preFilterAvg = (int)(total/pixelCount);
		total = 0;
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				int c = bitmap.getPixel(x, y) & RGB_MASK;
				total += Math.pow(c - preFilterAvg, 2);
			}
		}
		
		int stdDev = (int)Math.sqrt(total/pixelCount) * 3;
	
		int[] bounds = {preFilterAvg - stdDev, preFilterAvg + stdDev};
		return bounds;
	}
	
	private double[] HSVStdDevBounds(Bitmap bitmap) {
		long pixelCount = 0;
		double total = 0;
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				int c = bitmap.getPixel(x, y);
				float[] hsv = new float[3];
				Color.colorToHSV(c, hsv);
				total += hsv[2];
				++pixelCount;
			}
		}
		
		double preFilterAvg = total/pixelCount;
		total = 0;
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				int c = bitmap.getPixel(x, y);
				float[] hsv = new float[3];
				Color.colorToHSV(c, hsv);
				total += Math.pow(hsv[2] - preFilterAvg, 2);
			}
		}
		
		double stdDev = Math.sqrt(total/pixelCount) * 3;
		//double stdDev = Math.sqrt(total/pixelCount) * 1;
	
		double[] bounds = {preFilterAvg - stdDev, preFilterAvg + stdDev};
		return bounds;
	}

	private Bitmap modBitmapRGB(Bitmap bitmap) {
		int threshold = application.useWhiBal ? 0x500000 : 0;
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				int c = bitmap.getPixel(x, y);
				if ((c & RGB_MASK) < threshold) 
					bitmap.setPixel(x, y, 0xffffff);
				else 
					bitmap.setPixel(x, y, 0x000000);
			}
		}
		return bitmap;
	}
	
	private Bitmap modBitmapRGBStdDev(Bitmap bitmap) {
		int lowerBound = sRGBStdDevBounds(bitmap)[0];
		int threshold = application.useWhiBal ? lowerBound : 0;
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				int c = bitmap.getPixel(x, y) & RGB_MASK;
				if (c < threshold) 
					bitmap.setPixel(x, y, 0xffffff);
				else 
					bitmap.setPixel(x, y, 0x000000);
			}
		}
		return bitmap;
	}
	
	private Bitmap modBitmapHSV(Bitmap bitmap) {
		float threshold = (float)0.3;
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				int c = bitmap.getPixel(x, y);
				float[] hsv = new float[3];
				Color.colorToHSV(c, hsv);
				if (hsv[2] < threshold) 
					bitmap.setPixel(x, y, 0xffffff);
				else 
					bitmap.setPixel(x, y, 0x000000);
			}
		}
		return bitmap;
	}
	private Bitmap modBitmapHSVStdDev(Bitmap bitmap) {
		double[] bounds = HSVStdDevBounds(bitmap);
		double lowerBound = bounds[0];
		double threshold = application.useWhiBal ? lowerBound : 0;
		for (int x = 0; x < bitmap.getWidth(); x++) {
			for (int y = 0; y < bitmap.getHeight(); y++) {
				int c = bitmap.getPixel(x, y);
				float[] hsv = new float[3];
				Color.colorToHSV(c, hsv);
				//if (hsv[2] > threshold) 
				if (hsv[2] > bounds[0] && hsv[2] < bounds[1]) 
					bitmap.setPixel(x, y, 0xff000000);
				else 
					bitmap.setPixel(x, y, 0xffffffff);
			}
		}
		return bitmap;
	}

	private AutoFocusCallback autoFocusCallback = new AutoFocusCallback() {
	    @Override
	    public void onAutoFocus(boolean success, Camera camera){
        	if (success) {
	        	if (application.usePreview) {
	        		camera.setOneShotPreviewCallback(oneShotPreviewCallback);
	        	} else {
	        		camera.takePicture(null, null, pictureCallback);
	        	}
	    	} else {
	        	Toast.makeText(context, context.getString(R.string.focus_failure), Toast.LENGTH_SHORT).show();	    		
				ImageButton captureButton = (ImageButton) findViewById(R.id.button_capture);
			    captureButton.setEnabled(true);
	    	}
	    }
	};
 
	private PreviewCallback oneShotPreviewCallback = new PreviewCallback() {
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
		    Camera.Parameters cParams = camera.getParameters(); 
		    Size size = cParams.getPreviewSize(); 
		    
		    Bitmap previewBM;
		    if (application.useYuvImage) {
				YuvImage yuvImage = new YuvImage(data, cParams.getPreviewFormat(), size.width, size.height, null);
			    ByteArrayOutputStream bos = new ByteArrayOutputStream();
			    yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, bos);
			    previewBM = BitmapFactory.decodeByteArray(bos.toByteArray(), 0, bos.toByteArray().length);
		    } else{ 
		    	previewBM = Bitmap.createBitmap(decodeYUV420SP(data, size.width, size.height), size.width, size.height, Bitmap.Config.ARGB_8888);
		    }
		    processImage(previewBM);
		}
	};
	
	/**
	 * Converts an RGB color value to HSL. Conversion formula
	 * adapted from http://en.wikipedia.org/wiki/HSL_color_space.
	 * Assumes r, g, and b are contained in the set [0, 255] and
	 * returns h, s, and l in the set [0, 1].
	 *
	 * @param   int		rgb     The sRGB color 
	 * @return  double[h,s,l]   The HSL representation
	 */
	private float[] sRGBToHSL(int rgb) {
		float r = (float)Color.red(rgb)/255;
		float g = (float)Color.green(rgb)/255;
		float b = (float)Color.blue(rgb)/255;

		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float h, s;
		float l = (max + min) / 2;

	    if(max == min) {
	        h = s = 0; // achromatic
	    } else {
	    	float d = max - min;
	        s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
	        if (max == r) {
	        	h = (g - b) / d + (g < b ? 6 : 0);
	        } else if (max == g) {
	        	h = (b - r) / d + 2;
	        } else {
	            h = (r - g) / d + 4;
	        }
	        h /= 6;
	    }
		    return new float[] {h, s, l};
	}
	
	/**
	 * Converts an HSL color value to RGB. Conversion formula
	 * adapted from http://en.wikipedia.org/wiki/HSL_color_space.
	 * Assumes h, s, and l are contained in the set [0, 1] and
	 * returns r, g, and b in the set [0, 255].
	 *
	 * @param   float[h,s,l]   The HSL representation
	 * @return  int           	The RGB color
	 */	
	private int HSLTosRGB(float[] hsl) {
		float r, g, b;

	    if(hsl[1] == 0){
	        r = g = b = hsl[2]; // achromatic
	    } else {
	    	float q = hsl[2] < 0.5 ? hsl[2] * (hsl[2] + hsl[1]) : hsl[2] + hsl[1] - hsl[2] * hsl[1];
	    	float p = 2 * hsl[2] - q;
	        r = hue2rgb(p, q, hsl[0] + (float)1/3);
	        g = hue2rgb(p, q, hsl[0]);
	        b = hue2rgb(p, q, hsl[0] - (float)1/3);
	    }

	    return Color.rgb((int)(r * 255), (int)(g * 255), (int)(b * 255));
	}

    private float hue2rgb(float p, float q, float t){
        if(t < 0) t += 1;
        if(t > 1) t -= 1;
        if(t < (float)1/6) return p + (q - p) * (float)6 * t;
        if(t < (float)1/2) return q;
        if(t < (float)2/3) return p + (q - p) * (2/3 - t) * (float)6;
        return p;
    }

    /**
     * Looking for any shading in calibration area.
     * 
     * @param bitmap
     * @return
     */
    private boolean imageHasBadLighting(Bitmap bitmap) {
    	int segmentHeight = bitmap.getHeight()/4;
    	int segmentWidth = bitmap.getWidth()/4;
    	float[] vVals = new float[16];  //TODO: using v for now.  Is there something better?
    	int i = 0;
    	double total = 0;
    	for (int x = 0; x < 4; x++) {
    		for (int y = 0; y < 4; y++) {
        		Bitmap segment = Bitmap.createBitmap(bitmap, x*segmentWidth, y*segmentHeight, segmentWidth, segmentHeight);
        		float[] hsv = new float[3];
        		Color.colorToHSV(avgColor(segment, application.useWhiBal), hsv);
        		vVals[i++] = hsv[2]; 
				total += hsv[2];
    		}
    	}
    	
    	double vValAverage = total/16;

		total = 0;
		for (i = 0; i < 16; i++) {
			total += Math.pow(vVals[i] - vValAverage, 2);
		}
		
		double stdDev = Math.sqrt(total/16);
    	
    	//return stdDev/vValAverage > 0.02; //2% determined by trial and error
    	return stdDev/vValAverage > 0.05; //2% determined by trial and error
    }

    private class ProcessImageTask extends AsyncTask<Bitmap, Void, String[]> {
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
        	progressDialog = ProgressDialog.show(context, "", context.getString(R.string.processing_image), true);
        }
        
        private String munsell;
        
        protected String[] doInBackground(Bitmap... bitmaps) {
        	munsell = null;
        	
            int knownColorValue = application.useWhiBal ? KNOWN_WHIBAL_VALUE : KNOWN_GENERIC_18_VALUE;

        	float widthFactor = application.usePreview ? previewWidthFactor : pictureWidthFactor;
        	float heightFactor = application.usePreview ? previewHeightFactor : pictureHeightFactor;
     
        	Bitmap calibrationBitmap = Bitmap.createBitmap(bitmaps[0], (int)(overlayView.calibrationRectLeft/widthFactor), (int)(overlayView.calibrationRectTop/heightFactor), (int)((overlayView.calibrationRectRight-overlayView.calibrationRectLeft)/widthFactor), (int)((overlayView.calibrationRectBottom-overlayView.calibrationRectTop)/heightFactor));   	
        	if (imageHasBadLighting(calibrationBitmap)) {
        		//return context.getString(R.string.bad_lighting_message); 
        		String[] ret = {context.getString(R.string.bad_lighting_message)};
        		return  ret;
        	}
        	int sRGBCalibrationAvg = avgColor(calibrationBitmap, application.useWhiBal); //only filter if using WhiBal card
        	int linearCalibrationAvg = sRGBToLinearRGB(sRGBCalibrationAvg);
        	        	
        	//testing to verify linear/sRGB xforms
        	//String tmppresRGBCalibAvgStr = String.format("#%06X", (0xFFFFFF & sRGBFilteredCalibrationAvg));        	
        	//String tmpLinCalibAvgStr = String.format("#%06X", (0xFFFFFF & linearFilteredCalibrationAvg));        	
        	//int tmpsRGBCalibAvg = linearRGBTosRGB(linearFilteredCalibrationAvg);
        	//String tmpsRGBCalibAvgStr = String.format("#%06X", (0xFFFFFF & tmpsRGBCalibAvg));
        	
        	//calculate gain coefficients - use linear colorspace
        	float[] colorCoefs = new float[3];
        	colorCoefs[0] = (float)Color.green(linearCalibrationAvg)/(float)Color.red(linearCalibrationAvg);
        	colorCoefs[1] = 1;
        	colorCoefs[2] = (float)Color.green(linearCalibrationAvg)/(float)Color.blue(linearCalibrationAvg);
        	
        	/***
        	//for luma
        	int kC = sRGBToLinearRGB(Color.rgb(knownColorValue, knownColorValue, knownColorValue));
        	double knownLuma = 0.21 * Color.red(kC) + 0.72 * Color.green(kC) + 0.07 * Color.blue(kC);
        	double measuredLuma = 0.21 * Color.red(linearFilteredCalibrationAvg) + 0.72 * Color.green(linearFilteredCalibrationAvg) + 0.07 * Color.blue(linearFilteredCalibrationAvg);
        	double deltaLuma = knownLuma - measuredLuma;
        	***/
        	
        	/***
        	// calculate brightness adjust constant using linear colorspace
        	//int knownColor = Color.rgb(knownColorValue, knownColorValue, knownColorValue); //TODO: verify that knownColorValue is in linear RGB
        	int knownColor = sRGBToLinearRGB(Color.rgb(knownColorValue, knownColorValue, knownColorValue)); //TODO: verify that knownColorValue is in sRGB
        	float[] knownHSV = new float[3];
			Color.colorToHSV(knownColor, knownHSV);
			linearFilteredCalibrationAvg = Color.rgb((int)(Color.red(linearFilteredCalibrationAvg) * colorCoefs[0]), 
					(int)(Color.green(linearFilteredCalibrationAvg) * colorCoefs[1]), 
					(int)(Color.blue(linearFilteredCalibrationAvg) * colorCoefs[2]));
			float[] measuredHSV = new float[3];
			Color.colorToHSV(linearFilteredCalibrationAvg, measuredHSV);  //TODO:  apply coefs to avg first?
	       	float brightnessCorrection = knownHSV[2] - measuredHSV[2];
        	***/

        	// calculate brightness adjust constant using nonlinear colorspace
        	int knownColor = Color.rgb(knownColorValue, knownColorValue, knownColorValue); //TODO: verify that knownColorValue is in sRGB
        	float[] knownHSV = new float[3];
			Color.colorToHSV(knownColor, knownHSV);
			int linearAdjustedCalibrationAvg = Color.rgb((int)(Color.red(linearCalibrationAvg) * colorCoefs[0]), 
					(int)(Color.green(linearCalibrationAvg) * colorCoefs[1]), 
					(int)(Color.blue(linearCalibrationAvg) * colorCoefs[2]));
			int sRGBAdjustedCalibrationAvg = linearRGBTosRGB(linearAdjustedCalibrationAvg);
			float[] measuredHSV = new float[3];
			Color.colorToHSV(sRGBAdjustedCalibrationAvg, measuredHSV);  //TODO:  apply coefs to avg first?
	       	float brightnessCorrection = knownHSV[2] - measuredHSV[2];

	       	
        	/***
        	// for HSL
			int knownColor = sRGBToLinearRGB(Color.rgb(knownColorValue, knownColorValue, knownColorValue));
        	float[] knownHSL = sRGBToHSL(knownColor);
			float[] measuredHSL = sRGBToHSL(linearFilteredCalibrationAvg); //TODO:  apply coefs to avg first?
			//float[] measuredHSL = sRGBToHSL(Color.rgb((int)(Color.red(filteredCalibrationAvg) * colorCoefs[0]), 
			//										  (int)(Color.green(filteredCalibrationAvg) * colorCoefs[1]), 
			//										  (int)(Color.blue(filteredCalibrationAvg) * colorCoefs[2])));
	       	float brightnessCorrection = knownHSL[2] - measuredHSL[2];
	       	***/
	       	
	       	//for output during testing
			//float[] hsv = new float[3];
			//Color.colorToHSV(linearFilteredCalibrationAvg, hsv);
			//hsv[2] = hsv[2] + brightnessCorrection > 1 ? 1 : (hsv[2] + brightnessCorrection < 0 ? 0 : hsv[2] + brightnessCorrection);
			//int tmpAdjustedCalibrationAvg = linearRGBTosRGB(Color.HSVToColor(hsv));
        	//String tmpAdjustedCalibrationAvgStr = String.format("#%06X", (0xFFFFFF & tmpAdjustedCalibrationAvg));
		    
		    //for testing output
        	//adjustBitmap(calibrationBitmap, colorCoefs, brightnessCorrection); //assumes linear coefs
        	//int adjustedCalibrationAvg = avgColor(calibrationBitmap, false);
	       	

	       	Bitmap sampleBitMap = Bitmap.createBitmap(bitmaps[0], (int)(overlayView.sampleRectLeft/widthFactor), (int)(overlayView.sampleRectTop/heightFactor), (int)((overlayView.sampleRectRight-overlayView.sampleRectLeft)/widthFactor), (int)((overlayView.sampleRectBottom-overlayView.sampleRectTop)/heightFactor));
        	
	       	//TODO: for output during testing, otherwise unused
	       	int measuredSampleAvg = avgColor(sampleBitMap, false); 
        	//String tmpMeasuredSampleAvgStr = String.format("#%06X", (0xFFFFFF & measuredSampleAvg));
			//int c = sRGBToLinearRGB(measuredSampleAvg);
        	//String tmpLinearMeasuredSampleAvgStr = String.format("#%06X", (0xFFFFFF & c));
			//int adjustedRed = ((int)(Color.red(c) * colorCoefs[0]));
			//int adjustedGreen = ((int)(Color.green(c) * colorCoefs[1]));
			//int adjustedBlue = ((int)(Color.blue(c) * colorCoefs[2]));
			//c = linearRGBTosRGB(Color.rgb(adjustedRed, adjustedGreen, adjustedBlue));
			//float[] hsv = new float[3];
			//Color.colorToHSV(c, hsv);
			//hsv[2] = hsv[2] + brightnessCorrection > 1 ? 1 : (hsv[2] + brightnessCorrection < 0 ? 0 : hsv[2] + brightnessCorrection);
			//c = Color.HSVToColor(hsv);
        	//String tmpAdjustedSampleAvgStr = String.format("#%06X", (0xFFFFFF & c));
        	
        	//sRGBToLinearRGB(sampleBitMap);
            adjustBitmap(sampleBitMap, colorCoefs, brightnessCorrection); //assumes linear coefs
        	//linearRGBTosRGB(sampleBitMap);
        	int adjustedSampleAvg = avgColor(sampleBitMap, false);
        	soilColor = adjustedSampleAvg;
        	int red = (adjustedSampleAvg >> 16) & 0xFF;
        	int green = (adjustedSampleAvg >> 8) & 0xFF;
        	int blue = (adjustedSampleAvg) & 0xFF;

        	//Make formatted string values for readable output
        	String measureCalibrationAvgStr = String.format("#%06X", (0xFFFFFF & sRGBCalibrationAvg));
        	//String adjustedCalibrationAvgStr = String.format("#%06X", (0xFFFFFF & adjustedCalibrationAvg));
        	String targetCalibrationAvgStr = String.format("#%06X", (0xFFFFFF & knownColor));	    	
        	String measuredSampleAvgStr = String.format("#%06X", (0xFFFFFF & measuredSampleAvg));
        	String adjustedSampleAvgStr = String.format("#%06X", (0xFFFFFF & adjustedSampleAvg));	    	

        	munsell = SoilColorApplication.getInstance().calculateMunsell(adjustedSampleAvg);
        	
        	StringBuilder summary = new StringBuilder("Image source: ");
        	if (!application.usePreview) {
        		summary.append(context.getString(R.string.image_source_camera));
        	} else {
        		if (application.useYuvImage) {
        			summary.append(context.getString(R.string.image_source_preview_builtin));
        		} else {
        			summary.append(context.getString(R.string.image_source_preview_manual));
        		}
        	}
        	summary.append("\nCalibration: " + (application.useWhiBal ? context.getString(R.string.calibration_card_whibal) : context.getString(R.string.calibration_card_generic18)));
    		//summary.append("\n\nMeasured calibration color (sRGB):\n    " + measureCalibrationAvgStr);
    		////summary.append("\n\nAdjusted calibration color (sRGB):\n    " + adjustedCalibrationAvgStr);
    		//summary.append("\n\nTarget calibration color (sRGB):\n    " + targetCalibrationAvgStr);
    		//summary.append("\n\nMeasured sample color (sRGB):\n    " + measuredSampleAvgStr);
    		summary.append("\n\nAdjusted sample color (sRGB):\n    " + adjustedSampleAvgStr + "\n    red = " + red + "\n    green = " + green + "\n    blue = " + blue);
    		//summary.append("\n\nMunsell: " + munsell); //TODO: omitting until we get a good lookup table
    		
    		String[] retStrs = new String[5];
    		retStrs[0] = summary.toString();
    		retStrs[1] = measureCalibrationAvgStr;
    		retStrs[2] = targetCalibrationAvgStr;
    		retStrs[3] = measuredSampleAvgStr;
    		retStrs[4] = adjustedSampleAvgStr;
    		return retStrs;
    		//return summary.toString();
        }

        //protected void onPostExecute(String summary) {
        protected void onPostExecute(String[] stuff) {
			   //if (externalCall) {
			//	   NewSampleActivity.this.setResult(soilColor); //cheating a little here by using result code.  Should probably use an Intent.
			//	   NewSampleActivity.this.finish();
			 //  } else {
        	progressDialog.dismiss();
        	
        	String summary = stuff[0];
        	
        	AlertDialog.Builder builder = new AlertDialog.Builder(context);
			if (context.getString(R.string.bad_lighting_message).equals(summary)) {
		      	builder.setMessage(summary)
 			   .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
 				   @Override
 				   public void onClick(DialogInterface dialog, int which) {
 				   }
 			   });
				
			} else {
				/***
				int index = summary.indexOf("#");
				String measuredCalibrationColorString = summary.substring(index, index + 7);
				index = summary.indexOf("#", index + 7);
				String adjustedCalibrationColorString = summary.substring(index, index + 7);
				index = summary.indexOf("#", index + 7);
				String measuredSampleColorString = summary.substring(index, index + 7);
				index = summary.indexOf("#", index + 7);
				String adjustedSampleColorString = summary.substring(index, index + 7);
				***/
				String measuredCalibrationColorString = stuff[1];
				String adjustedCalibrationColorString = stuff[2];
				String measuredSampleColorString = stuff[3];
				String adjustedSampleColorString = stuff[4];
				
				LayoutInflater li = LayoutInflater.from(context);
				View resultsView = li.inflate(R.layout.dialog_result, null);
				TextView summaryText = (TextView) resultsView.findViewById(R.id.dialogResultSummary);
				summaryText.setText(summary);
				
				TextView measuredCalibrationColorSwatch = (TextView) resultsView.findViewById(R.id.dialogMeasuredCalibrationColorSwatch);
				measuredCalibrationColorSwatch.setBackgroundColor(Color.parseColor(measuredCalibrationColorString));
				measuredCalibrationColorSwatch.setText("Measured calibration color:\n" + measuredCalibrationColorString);
				float[] hsv = new float[3];
				Color.colorToHSV(Color.parseColor(measuredCalibrationColorString), hsv);  
				if (hsv[2] > 0.7) measuredCalibrationColorSwatch.setTextColor(Color.BLACK);

				
				TextView adjustedCalibrationColorSwatch = (TextView) resultsView.findViewById(R.id.dialogAdjustedCalibrationColorSwatch);
				adjustedCalibrationColorSwatch.setBackgroundColor(Color.parseColor(adjustedCalibrationColorString));
				adjustedCalibrationColorSwatch.setText("Target calibration color:\n" + adjustedCalibrationColorString);
				Color.colorToHSV(Color.parseColor(adjustedCalibrationColorString), hsv);  
				if (hsv[2] > 0.7) adjustedCalibrationColorSwatch.setTextColor(Color.BLACK);

				TextView measuredSampleColorSwatch = (TextView) resultsView.findViewById(R.id.dialogMeasuredSampleColorSwatch);
				measuredSampleColorSwatch.setBackgroundColor(Color.parseColor(measuredSampleColorString));
				measuredSampleColorSwatch.setText("Measured sample color:\n" + measuredSampleColorString);
				Color.colorToHSV(Color.parseColor(measuredSampleColorString), hsv);  
				if (hsv[2] > 0.7) measuredSampleColorSwatch.setTextColor(Color.BLACK);
				
				TextView adjustedSampleColorSwatch = (TextView) resultsView.findViewById(R.id.dialogAdjustedSampleColorSwatch);
				adjustedSampleColorSwatch.setBackgroundColor(Color.parseColor(adjustedSampleColorString));
				adjustedSampleColorSwatch.setText("Adjusted sample color:\n" + adjustedSampleColorString);
				Color.colorToHSV(Color.parseColor(adjustedSampleColorString), hsv);  
				if (hsv[2] > 0.7) adjustedSampleColorSwatch.setTextColor(Color.BLACK);
				
				builder.setView(resultsView)
	 			   .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					   @Override
					   public void onClick(DialogInterface dialog, int which) {
						   //if (externalCall) {
							   Intent intent = new Intent();
							   intent.putExtra("Munsell", munsell);
							   setResult(soilColor, intent); //cheating a little here by using result code.  Should probably use an Intent.
							   finish();
						   //}
	 				   }
				   })
				   .setNegativeButton("Retry", new DialogInterface.OnClickListener() {
					   @Override
					   public void onClick(DialogInterface dialog, int which) {
	 				   }
				   });

				/***
	        	builder.setMessage(summary)
	        			   .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
	        				   @Override
	        				   public void onClick(DialogInterface dialog, int which) {
	        					   //if (externalCall) {
	        						   Intent intent = new Intent();
	        						   intent.putExtra("Munsell", munsell);
	        						   setResult(soilColor, intent); //cheating a little here by using result code.  Should probably use an Intent.
	        						   finish();
	        					   //}
	         				   }
	        			   })
	        			   .setNegativeButton("Retry", new DialogInterface.OnClickListener() {
	        				   @Override
	        				   public void onClick(DialogInterface dialog, int which) {
	         				   }
	        			   });
	        	***/
			}
        	AlertDialog alert = builder.create();
        	alert.show(); 		
        	
			ImageButton captureButton = (ImageButton) findViewById(R.id.button_capture);
		    captureButton.setEnabled(true);
        }
        //}
    }
    
	public void processImage(Bitmap fullBitmap) {
		
    	/**
		//TODO: this image is for testing, to see what pixels were ignored
		float widthFactor = usePreview ? previewWidthFactor : pictureWidthFactor;
    	float heightFactor = usePreview ? previewHeightFactor : pictureHeightFactor;
    	Bitmap calibrationBitMap = Bitmap.createBitmap(fullBitmap, (int)(overlayView.calibrationRectLeft/widthFactor), (int)(overlayView.calibrationRectTop/heightFactor), (int)((overlayView.calibrationRectRight-overlayView.calibrationRectLeft)/widthFactor), (int)((overlayView.calibrationRectBottom-overlayView.calibrationRectTop)/heightFactor));   	
    	Bitmap filteredCalibrationBitMap = modBitmapHSVStdDev(calibrationBitMap); 
    	ImageView iView = new ImageView(context);
    	iView.setImageBitmap(calibrationBitMap);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
    	preview.addView(iView);
    	**/
		
		/***
    	ImageView iView = new ImageView(context);
    	iView.setImageBitmap(fullBitmap);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
    	preview.addView(iView);
		***/
		
        new ProcessImageTask().execute(fullBitmap);
    }
    
	static public int[] decodeYUV420SP(byte[] yuv420sp, int width, int height) {
    	final int frameSize = width * height;
        int rgb[]=new int[width*height];   

    	for (int j = 0, yp = 0; j < height; j++) {
    		int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
    		for (int i = 0; i < width; i++, yp++) {
    			int y = (0xff & ((int) yuv420sp[yp])) - 16;
    			if (y < 0) y = 0;
    			if ((i & 1) == 0) {
    				v = (0xff & yuv420sp[uvp++]) - 128;
    				u = (0xff & yuv420sp[uvp++]) - 128;
    			}
    			
    			int y1192 = 1192 * y;
    			int r = (y1192 + 1634 * v);
    			int g = (y1192 - 833 * v - 400 * u);
    			int b = (y1192 + 2066 * u);
    			
    			if (r < 0) r = 0; else if (r > 262143) r = 262143;
    			if (g < 0) g = 0; else if (g > 262143) g = 262143;
    			if (b < 0) b = 0; else if (b > 262143) b = 262143;
    			
    			rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
    		}
    	}
    	return rgb;
    }

	private PictureCallback pictureCallback = new PictureCallback() {
	    @Override
	    public void onPictureTaken(byte[] data, Camera camera) {
        	//camera.cancelAutoFocus();
	    	Bitmap fullBitMap = BitmapFactory.decodeByteArray(data, 0, data.length);
	    	Log.i(TAG, "image dim = " + fullBitMap.getWidth() + "x" + fullBitMap.getHeight()); 
	    	
	    	/**
	    	ImageView iView = new ImageView(context);
	    	iView.setImageBitmap(fullBitMap);
	        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
	    	preview.addView(iView);
	    	**/

	    	processImage(fullBitMap);
		    camera.startPreview();
	    }
	};
			
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.image_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	//TODO: move all these to application for persistence across observations
    	
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.image_source_camera:
        	application.usePreview = false;
        	Toast.makeText(context, this.getString(R.string.image_source_camera), Toast.LENGTH_SHORT).show();
            return true;
        case R.id.image_source_preview_builtin:
        	application.usePreview = true;
        	application.useYuvImage = true;
        	Toast.makeText(context, this.getString(R.string.image_source_preview_builtin), Toast.LENGTH_SHORT).show();
            return true;
        case R.id.image_source_preview_manual:
        	application.usePreview = true;
        	application.useYuvImage = false;
        	Toast.makeText(context, this.getString(R.string.image_source_preview_manual), Toast.LENGTH_SHORT).show();
            return true;
        /***
        case R.id.neutral_balance_deltas:
        	useCoefs = false;
        	Toast.makeText(context, this.getString(R.string.neutral_balance_deltas), Toast.LENGTH_SHORT).show();
            return true;
        case R.id.neutral_balance_coefs:
        	//useCoefs = true;
        	Toast.makeText(context, this.getString(R.string.neutral_balance_coefs), Toast.LENGTH_SHORT).show();
            return true;
        ***/
        case R.id.calibration_card_whibal:
        	application.useWhiBal = true;
        	Toast.makeText(context, this.getString(R.string.calibration_card_whibal), Toast.LENGTH_SHORT).show();
            return true;
        case R.id.calibration_card_generic18:
        	application.useWhiBal = false;
        	Toast.makeText(context, this.getString(R.string.calibration_card_generic18), Toast.LENGTH_SHORT).show();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
        
    private ImageButton zoomInButton;
    private ImageButton zoomOutButton;
    
    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		application = SoilColorApplication.getInstance();

	    Intent intent = getIntent();
	    String action = intent.getAction();
	    Log.i(TAG, "intent action = " + action);
	    externalCall = ACTION_GET_SOIL_COLOR.equals(action);
	    
	    //Super cheesy orientation waffle to prevent black preview on older HTC devices.
	    //This problem was only seen on Gingerbread, but including Honeycomb for good measure.
	    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.HONEYCOMB_MR2){
	    	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	    	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	    }
	    
	    requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_new_sample);
		
		context = this;

		ImageButton captureButton = (ImageButton) findViewById(R.id.button_capture);
		captureButton.setOnClickListener(
		    new View.OnClickListener() {
		        @Override
		        public void onClick(View v) {
		        	v.setEnabled(false);
		        	camera.autoFocus(autoFocusCallback);
		        }
		    }
		);

		zoomInButton = (ImageButton) findViewById(R.id.button_zoom_in);
		zoomInButton.setOnClickListener(
		    new View.OnClickListener() {
		        @Override
		        public void onClick(View v) {
		        	Camera.Parameters parameters = camera.getParameters();
		        	if(parameters.isZoomSupported()) {
		        		int maxZoom= parameters.getMaxZoom();
		        		int currentZoom = parameters.getZoom();
		        		if(currentZoom < maxZoom) {
		        			parameters.setZoom(++currentZoom);
		        			camera.setParameters(parameters);
		        		}
		        	}
		        }
		    });

		zoomOutButton = (ImageButton) findViewById(R.id.button_zoom_out);
		zoomOutButton.setOnClickListener(
		    new View.OnClickListener() {
		        @Override
		        public void onClick(View v) {
		        	Camera.Parameters parameters = camera.getParameters();
		        	if(parameters.isZoomSupported()) {
		        		int currentZoom = parameters.getZoom();
		        		if(currentZoom > 0) {
		        			parameters.setZoom(--currentZoom);
		        			camera.setParameters(parameters);
		        		}
		        	}
		        }
		    });
		
        //displayHeight/Width are used used in onResume to calc preview size.  I had originally grabbed them
		//from DisplayMetrics there, but this did not work after the phone was put to sleep from camera and then
		//awakened.  It seems that in that situation, onResume was getting called before the phone had oriented
		//to landscape, so the calc'ed preview size was too small. 
		DisplayMetrics metrics = context.getResources().getDisplayMetrics();         
		Log.i("NewSampleActivity", "screen dim = " + metrics.widthPixels + "x" + metrics.heightPixels);
		displayHeight = metrics.heightPixels;
		displayWidth = metrics.widthPixels;
    	if (displayWidth > displayHeight * ASPECT_RATIO) {
    		displayWidth = (int) (displayHeight * ASPECT_RATIO + 0.5);
    	} else {
    		displayHeight = (int)(displayWidth / ASPECT_RATIO + 0.5);
    	}
        
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
	}

    private Size getBestPreviewSize(Camera.Parameters cParams) {
        List<Size> sizes = cParams.getSupportedPreviewSizes();
        
        Size bestSize = null;
        
        for(Size s : sizes){
        	Log.i(TAG, "supported preview Size = " + s.width + "x" + s.height);
        	if((double)s.width/s.height == ASPECT_RATIO) {
        		if (bestSize == null ||
        			s.width * s.height > bestSize.width * bestSize.height) {
        			bestSize = s;
        		}
        	}
        }
        return bestSize;    	
    }
    
    private Size getBestPictureSize(Camera.Parameters cParams, Camera.Size idealSize) {
        List<Size> sizes = cParams.getSupportedPictureSizes();
        
        Size bestSize = null;
        
        for(Size s : sizes){
        	Log.i(TAG, "supported preview Size = " + s.width + "x" + s.height);   		
        	if((double)s.width/s.height == ASPECT_RATIO) {
            	if (s.width == idealSize.width && s.height == idealSize.height) {
            		bestSize = s;
            		break;
            	}
        		if (bestSize == null ||
        			s.width * s.height < bestSize.width * bestSize.height) {
        			bestSize = s;
        		}
        	}
        }
        return bestSize;    	
    }

    @Override
	protected void onResume() {
		super.onResume();
		camera = getCameraInstance();
        Camera.Parameters cParams = camera.getParameters();
        
        /**
        List<Camera.Size> previewSizes = cParams.getSupportedPreviewSizes();
        Log.i("NewSampleActivity", "Available Preview Sizes");
        for (Camera.Size cS: previewSizes) {
        	Log.i("NewSampleActivity", cS.width + "x" + cS.height);
        }
        List<Camera.Size> pictureSizes = cParams.getSupportedPictureSizes();
        Log.i("NewSampleActivity", "Available Picture Sizes");
        for (Camera.Size cS: pictureSizes) {
        	Log.i("NewSampleActivity", cS.width + "x" + cS.height);
        }
        **/
        
    	Camera.Size bestPreviewSize = getBestPreviewSize(cParams);
    	//bestPreviewSize = camera.new Size(320, 240); //tmp
		Log.i(TAG, "best preview size = " + bestPreviewSize.width + "x" + bestPreviewSize.height);
        cParams.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height);
		previewHeightFactor = (float)displayHeight/bestPreviewSize.height;
		previewWidthFactor = (float)displayWidth/bestPreviewSize.width;
        
		Camera.Size bestPictureSize = getBestPictureSize(cParams, bestPreviewSize);
		//bestPictureSize = camera.new Size(640, 480); //tmp
		Log.i(TAG, "best picture size = " + bestPictureSize.width + "x" + bestPictureSize.height);
        cParams.setPictureSize(bestPictureSize.width, bestPictureSize.height);
		pictureHeightFactor = (float)displayHeight/bestPictureSize.height;
		pictureWidthFactor = (float)displayWidth/bestPictureSize.width;
		//pictureHeightFactor = previewHeightFactor; //tmp
		//pictureWidthFactor = previewWidthFactor;  //tmp
		
        //trying to get the camera back a little
		if (cParams.isZoomSupported()) {
			if (application.currentZoom != -1) {
				cParams.setZoom(application.currentZoom);
			}
        } else {
        	zoomInButton.setVisibility(View.INVISIBLE);
        	zoomOutButton.setVisibility(View.INVISIBLE);
        }

        if (cParams.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
        	cParams.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
		//cParams.setFlashMode(Parameters.FLASH_MODE_ON); //TODO: temporary for testing
        camera.setParameters(cParams);

		// Create our Preview view and set it as the content of our activity.
        cameraPreview = new CameraPreview(this, camera, (int)(bestPreviewSize.width*previewWidthFactor), (int)(bestPreviewSize.height*previewHeightFactor));
        preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(cameraPreview);
        overlayView = new CameraPreviewOverlay(this, (int)(bestPreviewSize.width*previewWidthFactor), (int)(bestPreviewSize.height*previewHeightFactor));
        overlayView.loadParameters();  //TODO: tentative. Would prefer handling in overlayView.onWindowVisibilityChanged but appears to fail on some devices      
        preview.addView(overlayView);          
 	}
        
	@Override
	protected void onPause() {
		super.onPause();
		if (camera != null) {
	    	Camera.Parameters parameters = camera.getParameters();
	    	if(parameters.isZoomSupported()) {
	    		SoilColorApplication.getInstance().currentZoom = parameters.getZoom();
	    	}
			camera.stopPreview();
			camera.release();
			camera = null;
		}
		if(cameraPreview != null) {
			preview.removeView(cameraPreview);
			cameraPreview = null;
		}
		if(overlayView != null) {
	        overlayView.saveParameters();  //TODO: tentative. Would prefer handling in overlayView.onWindowVisibilityChanged but appears to fail on some devices
			preview.removeView(overlayView);
			overlayView = null;
		}		
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		/**
		if (camera != null) {
			camera.release();
			camera = null;
		}
		**/
	}
	
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
	
	private String calculateMunsell(int color) {
		int red = Color.red(color);
		int green = Color.green(color);
		int blue = Color.blue(color);
		
		double[] distances = new double[ColorConstants.NUMBER_OF_SUPPORTED_MUNSELL_VALUES];
		for (int i = 0; i < ColorConstants.NUMBER_OF_SUPPORTED_MUNSELL_VALUES; i++) {
			//((r - r1) * .299)^2 + ((g - g1) * .587)^2 + ((b - b1) * .114)^2
			distances[i] = Math.pow(((red - ColorConstants.MUNSELL_SRGB_RED_VALUES[i]) * 0.299), 2) +
						   Math.pow(((green - ColorConstants.MUNSELL_SRGB_GREEN_VALUES[i]) * 0.587), 2) +
						   Math.pow(((blue - ColorConstants.MUNSELL_SRGB_BLUE_VALUES[i]) * 0.114), 2);
		}
		
		int indexOfMin = 0;
		double min = distances[indexOfMin];
		for (int i = 0; i < ColorConstants.NUMBER_OF_SUPPORTED_MUNSELL_VALUES; i++) {
			if (distances[i] < min) {
				indexOfMin = i;
				min = distances[i];
			}
		}	
		
		return ColorConstants.MUNSELL_SPECS[indexOfMin];
	}
}
