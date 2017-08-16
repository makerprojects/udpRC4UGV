/*
 *  (C) Copyright 2017 Gregor Schlechtriem (http://www.pikoder.com).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Contributors:
 *  	Gregor Schlechtriem
 */

package com.udprc4ugv;


import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.text.DecimalFormat;

import static java.lang.Integer.parseInt;


public class ActivityMCU extends Activity {

	private UdpServer udpServer = null;
	private String udpReceiverHotSpotName;	// hotspot name provided by receiver
	private UdpReceiver udpReceiver = null;
	private String host = "192.168.4.1"; // UDP receiver default address for commands
	private String port = "12001";			// application default port
	private String rx_host = "192.168.4.2"; // UDP sender default address for responses (smart phone IP address) -> not relevant!
	private String rx_port = "12000";		// application default port for responses to smartphone
	private String networkPasskey = "default_networkPasskey";
	private Button btn_flash_Read, btn_flash_Write;
	private static CheckBox cb_AutoOFF;
	private static EditText edit_AutoOFF;
	private static String flash_success;
	private static String error_get_data;
	private static StringBuilder sb = new StringBuilder();  // used to manage multi cycle messages
	private static String TAG = ActivityMCU.class.getSimpleName();
	private static boolean suppressMessage = false;

	int iTimeOut = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		udpReceiverHotSpotName = (String) getResources().getText(R.string.default_udpReceiverHotSpotName);
		loadPref();

		setContentView(R.layout.activity_mcu);
        btn_flash_Read = (Button) findViewById(R.id.flash_Read);
        btn_flash_Write = (Button) findViewById(R.id.flash_Write);
        cb_AutoOFF = (CheckBox) findViewById(R.id.cBox_AutoOFF);
        edit_AutoOFF = (EditText) findViewById(R.id.AutoOFF);
    	flash_success = (String) getResources().getText(R.string.flash_success);
    	error_get_data = (String) getResources().getText(R.string.error_get_data);

		Globals g = Globals.getInstance();	// load timeout form global variable
		iTimeOut = g.getData();
		Log.d(TAG, "Read timeout " + String.valueOf(iTimeOut));

		udpServer = new UdpServer(this,mHandler);


		cb_AutoOFF.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				String str_to_send = "T=";
            	if (isChecked) edit_AutoOFF.setEnabled(true);
            	else {
            		edit_AutoOFF.setEnabled(false) ;
					str_to_send += "000";
					if (Constants.IS_LOGGABLE) {
						Log.v(TAG, "Send Timeout to MCU" + str_to_send);
					}
 				    sentCommand(str_to_send);
            		edit_AutoOFF.setText("0.0");
            	}
            }
        });
        
        btn_flash_Read.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				edit_AutoOFF.setText("XX.X");
				sentCommand(String.valueOf("T?"));
	    	}
	    });
        
        btn_flash_Write.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				float num1 = 0;
				String str_to_send = "T=";
				try {
					num1 = Float.parseFloat(edit_AutoOFF.getText().toString());
				} catch (NumberFormatException e) {
					String err_data_entry = getString(R.string.err_data_entry); 
					Toast.makeText(getBaseContext(), err_data_entry, Toast.LENGTH_SHORT).show();
				}
				
				if(num1 > 0 && num1 < 100){
					DecimalFormat myFormatter = new DecimalFormat("00.0");
					String output = myFormatter.format(num1);
					str_to_send += String.valueOf(output.charAt(0)) + String.valueOf(output.charAt(1)) + String.valueOf(output.charAt(3));
					Globals g = Globals.getInstance();	// store timeout in global variable
					g.setData(((output.charAt(0) - '0') *100 + (output.charAt(1) - '0') * 10 + (output.charAt(3) - '0'))*100); // convert to millis
					if (Constants.IS_LOGGABLE) Log.v(TAG, "Send Timeout to MCU" + str_to_send);
					output = output.replace(',','.');
					if (output.charAt(0) == '0') output = output.substring(1);
					edit_AutoOFF.setText(output);
					sentCommand(str_to_send);
				}
				else{
					String err_range = getString(R.string.mcu_error_range); 
					Toast.makeText(getBaseContext(), err_range, Toast.LENGTH_SHORT).show();
				}
			}
			    });
        

    }
    
    private static class MyHandler extends Handler {
        private final WeakReference<ActivityMCU> mActivity;
     
        public MyHandler(ActivityMCU activity) {
          mActivity = new WeakReference<ActivityMCU>(activity);
        }

	@Override
    public void handleMessage(Message msg) {
        	ActivityMCU activity = mActivity.get();
        	if (activity != null) {
        	  switch (msg.what) {
	            case UdpReceiver.RECEIVE_MESSAGE:								// if message is received
	            	String strIncom = new String((byte[]) msg.obj, msg.arg2, msg.arg1);
					strIncom = strIncom.replace("\r","").replace("\n","");
	            	sb.append(strIncom);								// append string
	            	
	            	float myNum = 9999;

					Log.v(TAG, "Newly received: " + strIncom + "sb: " + sb.toString());

					if (strIncom.equals("!")) { // received '!' as acknoledge of flushing
							Toast.makeText(activity.getBaseContext(), flash_success, Toast.LENGTH_SHORT).show();
							sb.delete(0, sb.length());
					}

					if (sb.length() >= 3) {
						try {
							myNum = Float.parseFloat(sb.toString());
						} catch (NumberFormatException nfe) {
							Toast.makeText(activity.getBaseContext(), "Could not parse " + nfe, Toast.LENGTH_SHORT).show();
							sb.delete(0, sb.length());
						}
					}

	            	if (myNum < 1000) {
	            		Float edit_data_AutoOFF = myNum/10;
	            		edit_AutoOFF.setText(String.valueOf(edit_data_AutoOFF));
						sb.delete(0, sb.length());
	            		if (edit_data_AutoOFF != 0)  cb_AutoOFF.setChecked(true); 
	            		else cb_AutoOFF.setChecked(false);
		            	Toast.makeText(activity.getBaseContext(), "Reading timeout data completed", Toast.LENGTH_SHORT).show();
	                }
	            	break;

				  case UdpServer.WIFI_NOT_AVAILABLE:
					  Log.d(UdpServer.TAG, "Wifi not available (Android system setting). Exit");
					  Toast.makeText(activity.getBaseContext(), "Wifi not available (Android system setting). Exit", Toast.LENGTH_SHORT).show();
					  activity.finish();
					  break;
				  case UdpServer.RECEIVER_NOT_ON_SCAN_LIST:
					  Log.d(UdpServer.TAG, "AP is not on current scan list. Exit");
					  Toast.makeText(activity.getBaseContext(), "Receiver is not offered by Android system scan", Toast.LENGTH_SHORT).show();
					  activity.finish();
					  break;
				  case UdpServer.WIFI_INCORRECT_ADDRESS:
					  Log.d(UdpServer.TAG, "Connection attempt failed");
					  Toast.makeText(activity.getBaseContext(), "Failed to connect to Hotspot", Toast.LENGTH_SHORT).show();
					  break;
				  case UdpServer.WIFI_REQUEST_ENABLE:
					  Log.d(UdpServer.TAG, "Started connecting to ap...");
					  Toast.makeText(activity.getBaseContext(), "Started connecting to ap...", Toast.LENGTH_SHORT).show();
					  break;
				  case UdpServer.WIFI_SOCKET_FAILED:
					  if (!suppressMessage) Toast.makeText(activity.getBaseContext(), "Timeout receiving IP address...", Toast.LENGTH_SHORT).show();
					  activity.finish();
					  break;
				  case UdpServer.USER_STOP_INITIATED:
					  suppressMessage = true;
					  break;
				  case UdpServer.WIFI_HOTSPOT_NOT_FOUND:
					  if (!suppressMessage) Toast.makeText(activity.getBaseContext(), "Hotspot not found", Toast.LENGTH_SHORT).show();
					  activity.finish();
					  break;
				  case UdpServer.WIFI_HOTSPOT_CONNECTING:
					  if (!suppressMessage) Toast.makeText(activity.getBaseContext(), "Connection ok, loading IP address... ", Toast.LENGTH_SHORT).show();
					  break;
				  case UdpServer.WIFI_HOTSPOT_CONNECTED:
					  String localHostIpAddress = Formatter.formatIpAddress(msg.arg1);
					  Log.v(TAG, "Received local IP address: " + localHostIpAddress);
					  if (!suppressMessage) Toast.makeText(activity.getBaseContext(), "Connected to receiver.", Toast.LENGTH_SHORT).show();
					  break;
			  }
			}
    	}
    }

	private final MyHandler mHandler = new MyHandler(this);
         
	private final static Runnable sRunnable = new Runnable() {
		public void run() { }
	};

	private void sentCommand(String str2sent) {
		if (!port.matches("^(6553[0-5]|655[0-2]\\d|65[0-4]\\d\\d|6[0-4]\\d{3}|[1-5]\\d{4}|[1-9]\\d{0,3}|0)$")) {
			CharSequence text = "Error: Invalid Port Number";
			Toast toast = Toast.makeText(getBaseContext(), text, Toast.LENGTH_SHORT);
			toast.show();
			return;
		}
		String uriString = "udp://" + host + ":" + port + "/";
		uriString += Uri.encode(str2sent);
		Uri uri = Uri.parse(uriString);
		UdpSender udpSender = new UdpSender();
		udpSender.SendTo(this.getApplicationContext(), uri);
	}


	private void loadPref(){
     	SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		host = mySharedPreferences.getString("pref_IP_address", host);
		port = mySharedPreferences.getString("pref_Port_number", port);
		rx_port = mySharedPreferences.getString("pref_rx_Port_number", rx_port);
		udpReceiverHotSpotName = mySharedPreferences.getString("pref_udpReceiverHotSpotName",udpReceiverHotSpotName);
		networkPasskey = mySharedPreferences.getString("pref_networkPasskey",networkPasskey);
	}
    
    @Override
    protected void onResume() {
    	super.onResume();
		Log.v(TAG, "Resuming activity...");
    	if(cb_AutoOFF.isChecked()) edit_AutoOFF.setEnabled(true);
    	else edit_AutoOFF.setEnabled(false);
		udpServer.udpConnect(udpReceiverHotSpotName,networkPasskey);
		if (udpReceiver == null) {
			Log.v(TAG, "Restarting receiver...");
			String uriString = "udp://" + rx_host + ":" + rx_port + "/";
			Uri uri = Uri.parse(uriString);
			udpReceiver = new UdpReceiver();
			udpReceiver.runUdpReceiver(uri, this, mHandler);
		}
	}

    @Override
    protected void onPause() {
    	super.onPause();
		Log.v(TAG, "Pausing activity...");
		udpServer.udpServer_onPause();
		if (udpReceiver != null) {
			udpReceiver.stopUdpReceiver();
			udpReceiver = null;
		}
    }

	@Override
	protected void onStop() {
		super.onStop();
 		Log.v(TAG, "Stopping activity...");
 		if (udpReceiver != null) {
 			udpReceiver.stopUdpReceiver();
 			udpReceiver = null;
		}
	}

  	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	loadPref();
    }
}
