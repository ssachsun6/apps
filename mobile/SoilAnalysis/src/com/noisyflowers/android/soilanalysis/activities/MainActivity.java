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
 * MainActivity.java
 */

package com.noisyflowers.android.soilanalysis.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import com.noisyflowers.android.soilanalysis.R;
import com.noisyflowers.android.soilanalysis.SoilColorApplication;
import com.noisyflowers.android.soilanalysis.model.Observation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	private static final String TAG = MainActivity.class.getName();

	private List<Observation> observations;
	
	ListView listView;
	ListAdapter aD;
	TextView emailAddressText, observationSetNameText;
	
	private Context context;
	
	private boolean sendingEmail = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		context = this;
		
		observations = SoilColorApplication.getInstance().getObservations();

		listView = (ListView)findViewById(R.id.observationsListView);
		emailAddressText = (TextView)findViewById(R.id.emailAddress);
		observationSetNameText = (TextView)findViewById(R.id.setName);
		
		SharedPreferences settings = getPreferences(0);
		String email = settings.getString("email", null);
		emailAddressText.setText(email);
		
	}
	
    @Override
    protected void onPause(){
       super.onPause();
       SoilColorApplication.getInstance().setObservationSetName(observationSetNameText.getText().toString());   
    }

    @Override
    protected void onStop(){
       super.onStop();

      SharedPreferences settings = getPreferences(0);
      SharedPreferences.Editor editor = settings.edit();
      editor.putString("email", emailAddressText.getText().toString());

      // Commit the edits!
      editor.commit();
    }

    @Override
	public void onResume() {
		super.onResume();
		
		aD = new ArrayAdapter<Observation>(this, android.R.layout.simple_list_item_1, observations);
		listView.setAdapter(aD);
		observationSetNameText.setText(SoilColorApplication.getInstance().getObservationSetName());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public void showNewObservationActivity(View view) {
		CharSequence name = observationSetNameText.getText();
		if (name != null && !"".equals(name.toString())) {
	    	Intent intent = new Intent(this, NewObservationActivity.class);
	    	startActivityForResult(intent, 0);
		} else {
			Toast.makeText(this, "Please enter an observation set name", Toast.LENGTH_SHORT).show();
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		/***
		if (requestCode == 1) {
			deleteFile(observationSetFileName);
			return;
		}
		***/
		
		int mask = 0xFF;
		int red = (resultCode >> 16) & mask;
		int green = (resultCode >> 8) & mask;
		int blue = (resultCode) & mask;

		//String munsell = data.getStringExtra("Munsell");
		Log.i(TAG, "red/green/blue = " + red + "/" + green + "/" + blue);
		
		Observation ob = new Observation();
		ob.color = resultCode;
		ob.name = observationSetNameText.getText().toString();
		//TODO: image metadata?
		ob.calibrationSource = SoilColorApplication.getInstance().useWhiBal ? this.getString(R.string.calibration_card_whibal) : 
																		this.getString(R.string.calibration_card_generic18);
		ob.imageSource = SoilColorApplication.getInstance().usePreview ?
									SoilColorApplication.getInstance().useYuvImage ? this.getString(R.string.image_source_preview_builtin) :
																					 this.getString(R.string.image_source_preview_manual) :
									this.getString(R.string.image_source_camera);
		
		if (observations.add(ob)) {
			ob.id = observations.indexOf(ob) + 1;
		}
	}
	
	private boolean dumpToTmpFile(String fileName, String content) {
		boolean retVal = false;
		FileOutputStream outputStream;
	    try {
	    	  outputStream = openFileOutput(fileName, Context.MODE_WORLD_READABLE);
	    	  outputStream.write(content.getBytes());
	    	  outputStream.close();
	    	  retVal = true;
	    } catch (IOException e) {
	        // Error while creating file
	    }
	    
	    return retVal;
	}
	
	private String observationSetFileName;
	
	public void sendEmail(View view) {
		CharSequence s = emailAddressText.getText();
		if (s == null || "".equals(s.toString())) {
			Toast.makeText(this, "Please enter an amail address", Toast.LENGTH_SHORT).show();
			return;
		}
		s = observationSetNameText.getText();
		if (s == null || "".equals(s.toString())) {
			Toast.makeText(this, "Please enter an observation set name", Toast.LENGTH_SHORT).show();
			return;
		}
		if (observations.size() == 0) {
			Toast.makeText(this, "There are no observations to send", Toast.LENGTH_SHORT).show();
			return;
		}

		StringBuilder obsStringB = new StringBuilder();
		//obsStringB.append("ID,observation set name,red,green,blue,image source,calibration source\n");
		obsStringB.append("ID,observation set name,red,green,blue,hex,munsell,image source,calibration source\n");
    	
		int mask = 0xFF;
		for (Observation obs : observations) {
			int red = (obs.color >> 16) & mask;
			int green = (obs.color >> 8) & mask;
			int blue = (obs.color) & mask;
			//obsStringB.append(obs.id + "," + obs.name + "," + red + "," + green + "," + blue + "," + obs.imageSource + "," + obs.calibrationSource + "\n");
	    	String colorHexStr = String.format("#%06X", (0xFFFFFF & obs.color));
        	String munsell = SoilColorApplication.getInstance().calculateMunsell(obs.color);
			obsStringB.append(obs.id + "," + obs.name + "," + red + "," + green + "," + blue + "," + colorHexStr + "," + munsell + "," + obs.imageSource + "," + obs.calibrationSource + "\n");
		}
		
		Uri uri = null;
		/***
		try {
			observationSetFileName = URLEncoder.encode(observationSetNameText.getText().toString() + ".csv", "UTF-8");
			if (dumpToTmpFile(observationSetFileName, obsStringB.toString())) {
				File file = new File(observationSetFileName);
				uri = Uri.fromFile(file);
				
			}
		} catch (Exception e) {}
		***/
		
		Intent i = new Intent(Intent.ACTION_SEND);
		i.setType("text/plain");
		i.putExtra(Intent.EXTRA_EMAIL, new String[] {emailAddressText.getText().toString()});
		i.putExtra(Intent.EXTRA_SUBJECT, observationSetNameText.getText().toString());
		i.putExtra(Intent.EXTRA_TEXT,obsStringB.toString());
		if (uri != null) i.putExtra(Intent.EXTRA_STREAM, uri);
		sendingEmail = true;
		this.startActivity(Intent.createChooser(i, "Select application"));
		//this.startActivityForResult(Intent.createChooser(i, "Select application"), 1);       
	}
	
	public void clearObservationSet(View view) {
		/***
		SoilColorApplication.getInstance().clearObservations();
		aD = new ArrayAdapter<Observation>(this, android.R.layout.simple_list_item_1, observations);
		listView.setAdapter(aD);
		observationSetNameText.setText(SoilColorApplication.getInstance().getObservationSetName());
		***/
    	AlertDialog.Builder builder = new AlertDialog.Builder(context);
    	builder.setMessage("Are you sure you want to clear this data?  This cannot be undone.")
    			   .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
    				   @Override
    				   public void onClick(DialogInterface dialog, int which) {
    						SoilColorApplication.getInstance().clearObservations();
    						aD = new ArrayAdapter<Observation>(context, android.R.layout.simple_list_item_1, observations);
    						listView.setAdapter(aD);
    						observationSetNameText.setText(SoilColorApplication.getInstance().getObservationSetName());
     				   }
    			   })
    			   .setNegativeButton("No", new DialogInterface.OnClickListener() {
    				   @Override
    				   public void onClick(DialogInterface dialog, int which) {
     				   }
    			   });
    	AlertDialog alert = builder.create();
    	alert.show(); 		
	}

}
