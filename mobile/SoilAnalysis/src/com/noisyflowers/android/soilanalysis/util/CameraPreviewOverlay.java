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
 * CameraPreviewOverlay.java
 */

package com.noisyflowers.android.soilanalysis.util;

import java.util.Calendar;

import com.noisyflowers.android.soilanalysis.SoilColorApplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.MeasureSpec;

public class CameraPreviewOverlay extends SurfaceView {

	private static final String TAG = CameraPreviewOverlay.class.getName();
	private static final double ASPECT_RATIO = 4.0 / 3.0;
    private static final int MAX_CLICK_DURATION = 200;
    private static final int TOUCH_MOVE_THRESHOLD = 3;
    private static final float TEXT_DP = 20;
    private static final float STROKE_DP = 2;
    
	private Context context; 
	private Paint paint = new Paint();
	
    private int pWidth, pHeight;

    public int calibrationRectTop = 0;
	public int calibrationRectBottom = 0;
	public int calibrationRectLeft = 0;
	public int calibrationRectRight = 0;

	public int sampleRectTop = 0;
	public int sampleRectBottom = 0;
	public int sampleRectLeft = 0;
	public int sampleRectRight = 0;
	
	public int canvasHeight = 0;
	public int canvasWidth = 0;
	
	private int previewCircleRadius;
	private int previewCircleCenterX;
	private int previewCircleCenterY;
	
	private int circleResizeIncrement;
	private int circleMinRadius; 
	private int touchMoveThreshold;
	private double previousDistanceFromCenter = 0;
	private int prevX, prevY;
	
	public CameraPreviewOverlay (Context context, int width, int height){
		super(context);
		this.context = context;
		
		pHeight = height;
		pWidth = width;
		setWillNotDraw(false);
	}
	
	boolean touched = false;
	boolean moved = false;
    long startClickTime = -1;
    
    private double distance(int x1, int y1, int x2, int y2) {
    	return Math.sqrt(Math.pow((x2 - x1), 2) + Math.pow((y2 - y1), 2));
    }
    
    /****
    @Override
    protected void onWindowVisibilityChanged (int visibility) {
		SoilColorApplication app = SoilColorApplication.getInstance();
    	if (visibility == View.GONE) {  //if going away, save sample coords
    		app.previewCircleCenterX = previewCircleCenterX;
    		app.previewCircleCenterY = previewCircleCenterY;
    		app.previewCircleRadius = previewCircleRadius;
    	} else if (visibility == View.VISIBLE) {  //if returning, load sample coords
    		previewCircleCenterX = app.previewCircleCenterX;
    		previewCircleCenterY = app.previewCircleCenterY;
    		previewCircleRadius = app.previewCircleRadius;
    	}
    }
    ****/
    
  //TODO: tentative. Would prefer handling in onWindowVisibilityChanged but appears to fail on some devices
    public void saveParameters() {
		SoilColorApplication app = SoilColorApplication.getInstance();
		app.previewCircleCenterX = previewCircleCenterX;
		app.previewCircleCenterY = previewCircleCenterY;
		app.previewCircleRadius = previewCircleRadius;
    }

  //TODO: tentative. Would prefer handling in onWindowVisibilityChanged but appears to fail on some devices
    public void loadParameters() {
		SoilColorApplication app = SoilColorApplication.getInstance();
		previewCircleCenterX = app.previewCircleCenterX;
		previewCircleCenterY = app.previewCircleCenterY;
		previewCircleRadius = app.previewCircleRadius;
    }

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int touchedX = (int)event.getX();
		int touchedY = (int)event.getY();
		
	    switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				previousDistanceFromCenter = distance(previewCircleCenterX, previewCircleCenterY, touchedX, touchedY);
				prevX = touchedX;
				prevY = touchedY;
				startClickTime = Calendar.getInstance().getTimeInMillis();
				break;
			case MotionEvent.ACTION_UP:
				previousDistanceFromCenter = 0;
				long clickDuration = Calendar.getInstance().getTimeInMillis() - startClickTime;
                if(clickDuration < MAX_CLICK_DURATION) {
                	if (touchedX < canvasWidth/2) {
						previewCircleCenterX = touchedX <= previewCircleRadius ? 
																previewCircleRadius :
																touchedX >= canvasWidth/2 - previewCircleRadius ?
																		canvasWidth/2 - previewCircleRadius :
																			touchedX;
						previewCircleCenterY = touchedY <= previewCircleRadius ? 
													previewCircleRadius : 
													touchedY >= canvasHeight - previewCircleRadius ?		
														canvasHeight - previewCircleRadius :
														touchedY;							
						touched = true;
						invalidate();
                	}
				}
				break;
			case MotionEvent.ACTION_MOVE:
				int historySize = event.getHistorySize();
				
				int tentativeRadius = previewCircleRadius;
				double newDistanceFromCenter;
				for (int i = 0; i <= historySize; i++) {
					int newX = i < historySize ? (int)event.getHistoricalX(i) : touchedX;
					int newY = i < historySize ? (int)event.getHistoricalY(i) : touchedY;
					
					if (distance(prevX, prevY, newX, newY) > touchMoveThreshold) { 
						prevX = newX; 
						prevY = newY;
						newDistanceFromCenter = distance(previewCircleCenterX, previewCircleCenterY, newX, newY);

						if (newDistanceFromCenter > previousDistanceFromCenter) {
							tentativeRadius = previewCircleRadius + circleResizeIncrement; 
							tentativeRadius = previewCircleCenterX - tentativeRadius <= 0 ?
									previewCircleRadius :
									previewCircleCenterX + tentativeRadius >= canvasWidth/2 ?
											previewCircleRadius :
											tentativeRadius;
							if (tentativeRadius != previewCircleRadius) {
								tentativeRadius = previewCircleCenterY - tentativeRadius <= 0 ?
										previewCircleRadius :
										previewCircleCenterY + tentativeRadius >= canvasHeight ?
												previewCircleRadius :
												tentativeRadius;
								
							}
						} else if (newDistanceFromCenter < previousDistanceFromCenter){
							tentativeRadius = previewCircleRadius - circleResizeIncrement < circleMinRadius ? 
												circleMinRadius : previewCircleRadius - circleResizeIncrement;	
						}
						previousDistanceFromCenter = newDistanceFromCenter;
						newDistanceFromCenter = 0;
					}
				}
				previewCircleRadius = tentativeRadius;
				touched = true;
				invalidate();
				break;
			default:
		}
		return true;
	}
	
    @Override
    public void onMeasure(int widthSpec, int heightSpec) {
    	/***
    	int height = MeasureSpec.getSize(heightSpec);
    	int width = MeasureSpec.getSize(widthSpec);
    	if (width > height * ASPECT_RATIO) {
    		width = (int) (height * ASPECT_RATIO + 0.5);
    	} else {
    		height = (int)(width / ASPECT_RATIO + 0.5);
    	}
    	***/
        setMeasuredDimension(pWidth, pHeight);
   }


	@Override
	protected void onDraw(Canvas canvas) {
		//DisplayMetrics metrics = context.getResources().getDisplayMetrics();         
		//canvasWidth = metrics.widthPixels;
		//canvasHeight = metrics.heightPixels;
		//canvasWidth = 640;
		//canvasHeight = 480; 
		canvasHeight = getHeight();
		canvasWidth = getWidth();
		Log.i(TAG, "canvasWidth = " + canvasWidth + ", canvasHeight = " + canvasHeight);
		
		//int previewRectHeight = (int)Math.round(canvasHeight * 0.8);
		//int previewRectWidth = (int)Math.round((canvasWidth/2) * 0.625);
		int previewRectHeight = (int)Math.round(canvasHeight * 0.4);
		int previewRectWidth = (int)Math.round((canvasWidth/2) * 0.3);
		previewRectHeight = previewRectWidth;
		circleResizeIncrement = (int)Math.round(canvasHeight * 0.02);
		circleMinRadius = (int)Math.round(canvasHeight * 0.1);
		touchMoveThreshold = (int)Math.round(canvasHeight * 0.01);
		int previewCircleDiameter = previewRectWidth; 
		int previewWidthMargin = ((canvasWidth/2)-previewRectWidth)/4;
		Log.i(TAG, "rectHeight = " + previewRectHeight + ", rectWidth = " + previewRectWidth + ", widthMargin = " + previewWidthMargin);
		
		int previewRectTop = (canvasHeight/2) - (previewRectHeight/2);
		int previewRectBottom = (canvasHeight/2) + (previewRectHeight/2);
		int previewRectLeft = (canvasWidth/2) + previewWidthMargin;
		int previewRectRight = (canvasWidth/2) + previewRectWidth + previewWidthMargin;
		//int previewCircleRadius = previewCircleDiameter/2;
		//int previewCircleCenterX = canvasWidth/2 - previewCircleRadius - previewWidthMargin;
		//int previewCircleCenterY = canvasHeight/2;

		if (!touched) {
			if (SoilColorApplication.getInstance().previewCircleRadius == -1) { //load default
				previewCircleRadius = previewCircleDiameter/2;
				previewCircleCenterX = canvasWidth/2 - previewCircleRadius - previewWidthMargin;
				previewCircleCenterY = canvasHeight/2;
			} else { //load saved
				SoilColorApplication app = SoilColorApplication.getInstance();
				previewCircleRadius = app.previewCircleRadius;
				previewCircleCenterX = app.previewCircleCenterX;
				previewCircleCenterY = app.previewCircleCenterY; 
			}
		} else {
			touched = false;
		}
			
		int textSize = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_DP, getResources().getDisplayMetrics());
		int strokeSize = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, STROKE_DP, getResources().getDisplayMetrics());
		//Log.i(TAG, "textSize = " + textSize + ", textSize2 = " + textSize2);
		
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(strokeSize);
		//paint.setColor(Color.RED);
		paint.setARGB(255, 200, 0, 0);
		paint.setTextSize(textSize);
		paint.setTextAlign(Paint.Align.CENTER);
		//canvas.drawRect(0, 0, canvasWidth, canvasHeight, paint);
		canvas.drawRect(previewRectLeft, previewRectTop, previewRectRight, previewRectBottom, paint);
		canvas.drawText("Card here", previewRectLeft + previewRectWidth/2, canvasHeight/2, paint);
		canvas.drawCircle(previewCircleCenterX, previewCircleCenterY, previewCircleRadius, paint);
		canvas.drawText("Sample here", previewCircleCenterX, previewCircleCenterY, paint);
		
		//calibrationRectTop = previewRectTop + 50;
		//calibrationRectBottom = previewRectBottom - 50;
		calibrationRectTop = previewRectTop + 20;
		calibrationRectBottom = previewRectBottom - 20;
		calibrationRectLeft = previewRectLeft + 20;
		calibrationRectRight = previewRectRight - 20;
		//canvas.drawRect(calibrationRectLeft, calibrationRectTop, calibrationRectRight, calibrationRectBottom, paint);
		
		int sampleRectHalf= (int)Math.round(previewCircleRadius*0.7);
		sampleRectTop = previewCircleCenterY - sampleRectHalf;
		sampleRectBottom = previewCircleCenterY + sampleRectHalf;
		sampleRectLeft = previewCircleCenterX - sampleRectHalf;
		sampleRectRight = previewCircleCenterX + sampleRectHalf;
		//Log.i(TAG, "sampleRectTop = " + sampleRectTop + ", sampleRectBottom = " + sampleRectBottom + ", sampleRectLeft = " + sampleRectLeft + ", sampleRectRight = " + sampleRectRight);
		//canvas.drawRect(sampleRectLeft, sampleRectTop, sampleRectRight, sampleRectBottom, paint);

		
	}
}
