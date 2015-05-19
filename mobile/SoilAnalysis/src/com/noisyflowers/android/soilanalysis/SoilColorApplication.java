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
 * com.noisyflowers.android.soilanalysis
 * SoilColorApplication.java
 */

package com.noisyflowers.android.soilanalysis;

import java.util.ArrayList;
import java.util.List;

import com.noisyflowers.android.soilanalysis.model.Observation;
import com.noisyflowers.android.soilanalysis.util.ColorConstants;

import android.app.Application;
import android.graphics.Color;

public class SoilColorApplication extends Application {
	
	private List<Observation> observations = new ArrayList<Observation>();
	private String observationSetName = null;
	
	public boolean usePreview = false;
	public boolean useYuvImage = true;
	public boolean useWhiBal = true;
	
	public int previewCircleRadius = -1;
	public int previewCircleCenterX = -1;
	public int previewCircleCenterY = -1;
	public int currentZoom = -1;
	
	private static SoilColorApplication myInstance;
	public static SoilColorApplication getInstance(){
		return myInstance;
	}
	
	public SoilColorApplication () {
		myInstance = this;
	}

	public List<Observation> getObservations() {
		return observations;
	}

	public void clearObservations() {
		observations.clear();
		observationSetName = null;
	}
	
	public String getObservationSetName() {
		return observationSetName;
	}
	
	public void setObservationSetName(String name) {
		observationSetName = name;
	}

	public String calculateMunsell(int color) {
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
	
	/***
	public boolean isUsePreview() {
		return usePreview;
	}
	public void setUsePreview(boolean)
	public boolean isUseYuvImage() {
		return useYuvImage;
	}
	public boolean isUseWhiBal() {
		return useWhiBal;
	}
	***/
}
