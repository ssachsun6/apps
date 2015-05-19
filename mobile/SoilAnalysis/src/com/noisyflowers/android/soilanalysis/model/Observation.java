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
 * com.noisyflowers.android.soilanalysis.model
 * Observation.java
 */

package com.noisyflowers.android.soilanalysis.model;

public class Observation {

	public int id;
	public String name;
	public int color;
	public String imageSource;
	public String calibrationSource;

	@Override
	public String toString() {
		int mask = 0xFF;
		int red = (color >> 16) & mask;
		int green = (color >> 8) & mask;
		int blue = (color) & mask;
		//return "" + id + " " + name + " " + red + "/" + green + "/" + blue;
		return "" + id + " " + name + " " + red + "/" + green + "/" + blue + " " + imageSource + " " + calibrationSource;
 	}
}
