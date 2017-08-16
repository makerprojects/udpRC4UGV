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
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

import static com.udprc4ugv.R.layout.activity_buttons;

public class ActivityButtons extends Activity {

	private UdpServer udpServer = null;

	private Button btn_forward, btn_backward, btn_left, btn_right, buttonCH7On, buttonAUTO, buttonMANUAL,buttonLEARNING;

	private final String cChannelNeutral = "7F";
	private final String cChannelMax = "FE"; // equals 0xFE
	private final String cChannelMin = "00";

	private String udpReceiverHotSpotName;	// hotspot name provided by receiver
	private String host = "192.168.4.1"; // UDP receiver default address
	private String remotePort = "12001";            // application default port
	private String localPort = "12000";
	private String networkPasskey = "PASSWORD";
	private final String cLeftHeader = "FF00";
	private final String cRightHeader = "FF01";
	private String commandLeft = "FF007F";    // make sure we init the string to avoid problem w/o mixing
	private String commandRight = "FF017F";
	private String strFMChannel;
	private boolean mixing = true; // for backward compatibility
	private boolean forward_down_sent = false;
	private boolean forward_up_sent = false;
	private boolean backward_down_sent = false;
	private boolean backward_up_sent = false;
	private boolean right_down_sent = false;
	private boolean right_up_sent = false;
	private boolean left_down_sent = false;
	private boolean left_up_sent = false;
    private boolean bCH7On_sent = false;
    private boolean bCH7Off_sent = false;
	private static StringBuilder sb = new StringBuilder();  // used to manage multi cycle messages
	private static boolean suppressMessage = false;
	private String FM_AUTO_PWM, FM_LEARNING_PWM, FM_MANUAL_PWM;
	private static String TAG = ActivityButtons.class.getSimpleName();
	private int	intFMMode;

	// fail safe related definitions
	Timer timer = null;
	TimerTask timerTask = null;
	int iTimeOut = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		udpReceiverHotSpotName = (String) getResources().getText(R.string.default_udpReceiverHotSpotName);
		loadPref();

		if (FM_AUTO_PWM.length() < 4) {
			FM_AUTO_PWM = "0" + FM_AUTO_PWM;
		}
		if (FM_LEARNING_PWM.length() < 4) {
			FM_LEARNING_PWM = "0" + FM_LEARNING_PWM;
		}
		if (FM_MANUAL_PWM.length() < 4) {
			FM_MANUAL_PWM = "0" + FM_MANUAL_PWM;
		}

		setContentView(activity_buttons);
		btn_forward = (Button) findViewById(R.id.forward);
		btn_backward = (Button) findViewById(R.id.backward);
		btn_left = (Button) findViewById(R.id.left);
		btn_right = (Button) findViewById(R.id.right);
        buttonCH7On = (Button) findViewById(R.id.buttonCH7On);
		buttonAUTO = (Button) findViewById(R.id.buttonAUTO);
		buttonLEARNING = (Button) findViewById(R.id.buttonLEARNING);
		buttonMANUAL = (Button) findViewById(R.id.buttonMANUAL);

		Globals g = Globals.getInstance();	// load timeout form global variable
		iTimeOut = g.getData();
		Log.d(TAG, "Read timeout " + String.valueOf(iTimeOut));

		udpServer = new UdpServer(this,mHandler);

		if (intFMMode == 1) {
			buttonAUTO.setBackgroundColor(Color.GREEN);
			sentCommand(strFMChannel + "=" + FM_AUTO_PWM);
		} else if (intFMMode == 2) {
			buttonLEARNING.setBackgroundColor(Color.GREEN);
			sentCommand(strFMChannel + "=" + FM_LEARNING_PWM);
		} else {
			buttonMANUAL.setBackgroundColor(Color.GREEN);
			sentCommand(strFMChannel + "=" + FM_MANUAL_PWM);
		}

		btn_forward.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					commandRight = cRightHeader + cChannelMax; // miniSSC positions
					if (mixing) commandLeft = cLeftHeader + cChannelMin; // commands for miniSSC
					if (!forward_down_sent) {
						sentCommand("0x" + commandLeft + commandRight);
						forward_down_sent = true;
						forward_up_sent = false;
					}
				} else if (event.getAction() == MotionEvent.ACTION_UP) {
					if (mixing)
						commandLeft = cLeftHeader + cChannelNeutral; // if not mixing then maintain steering value
					commandRight = cRightHeader + cChannelNeutral; // commands for miniSSC
					if (!forward_up_sent) {
						sentCommand("0x" + commandLeft + commandRight);
						forward_up_sent = true;
						forward_down_sent = false;
					}
				}
				return false;
			}
		});

		btn_backward.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					commandRight = cRightHeader + cChannelMin;    // command SSC format
					if (mixing) commandLeft = cLeftHeader + cChannelMax;
					if (!backward_down_sent) {
						sentCommand("0x" + commandLeft + commandRight);
						backward_down_sent = true;
						backward_up_sent = false;
					}
				} else if (event.getAction() == MotionEvent.ACTION_UP) {
					if (mixing) commandLeft = cLeftHeader + cChannelNeutral; // commands for miniSSC
					commandRight = cRightHeader + cChannelNeutral; // commands for miniSSC
					if (!backward_up_sent) {
						sentCommand("0x" + commandLeft + commandRight);
						backward_up_sent = true;
						backward_down_sent = false;
					}
				}
				return false;
			}
		});

		btn_right.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					commandLeft = cLeftHeader + cChannelMax;
					if (mixing) commandRight = cRightHeader + cChannelMax; // commands for miniSSC
					if (!right_down_sent) {
						sentCommand("0x" + commandLeft + commandRight);
						right_down_sent = true;
						right_up_sent = false;
					}
				} else if (event.getAction() == MotionEvent.ACTION_UP) {
					commandLeft = cLeftHeader + cChannelNeutral; // commands for miniSSC
					if (mixing) commandRight = cRightHeader + cChannelNeutral;
					if (!right_up_sent) {
						sentCommand("0x" + commandLeft + commandRight);
						right_up_sent = true;
						right_down_sent = false;
					}
				}
				return false;
			}
		});

		btn_left.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					commandLeft = cLeftHeader + cChannelMin;
					if (mixing) commandRight = cRightHeader + cChannelMin; // commands for miniSSC
					if (!left_down_sent) {
						sentCommand("0x" + commandLeft + commandRight);
						left_down_sent = true;
						left_up_sent = false;
					}
				} else if (event.getAction() == MotionEvent.ACTION_UP) {
					commandLeft = cLeftHeader + cChannelNeutral; // commands for miniSSC
					if (mixing)
						commandRight = cRightHeader + cChannelNeutral; // maintain motion if not mixing
					if (!left_up_sent) {
						sentCommand("0x" + commandLeft + commandRight);
						left_up_sent = true;
						left_down_sent = false;
					}
				}
				return false;
			}
		});

        buttonCH7On.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!bCH7On_sent) {
                        sentCommand("7=1850");
                        bCH7On_sent = true;
                        bCH7Off_sent = false;
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (!bCH7Off_sent) {
                        sentCommand("7=1500");
                        bCH7Off_sent = true;
                        bCH7On_sent = false;
                    }
                }
                return false;
            }
        });

		buttonAUTO.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					if (intFMMode != 1) {
						sentCommand(strFMChannel + "=" + FM_AUTO_PWM);
						buttonAUTO.setBackgroundColor(Color.GREEN);
						buttonLEARNING.setBackgroundColor(Color.LTGRAY);
						buttonMANUAL.setBackgroundColor(Color.LTGRAY);
					}
				}
				return false;
			}
		});

		buttonLEARNING.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					if (intFMMode != 2) {
						sentCommand(strFMChannel + "=" + FM_LEARNING_PWM);
						buttonAUTO.setBackgroundColor(Color.LTGRAY);
						buttonLEARNING.setBackgroundColor(Color.GREEN);
						buttonMANUAL.setBackgroundColor(Color.LTGRAY);
					}
				}
				return false;
			}
		});

		buttonMANUAL.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					if (intFMMode != 3) {
						sentCommand(strFMChannel + "=" + FM_MANUAL_PWM);
						buttonAUTO.setBackgroundColor(Color.LTGRAY);
						buttonLEARNING.setBackgroundColor(Color.LTGRAY);
						buttonMANUAL.setBackgroundColor(Color.GREEN);
					}
				}
				return false;
			}
		});

	}


	private static class MyHandler extends Handler {
		private final WeakReference<ActivityButtons> mActivity;

		public MyHandler(ActivityButtons activity) {
			mActivity = new WeakReference<ActivityButtons>(activity);
			}
			@Override
			public void handleMessage(Message msg) {
				ActivityButtons activity = mActivity.get();
				if (activity != null) {
					switch (msg.what) {
						case UdpReceiver.RECEIVE_MESSAGE:								// if message is received
							String strIncom = new String((byte[]) msg.obj, msg.arg2, msg.arg1);
							strIncom = strIncom.replace("\r","").replace("\n","");
							sb.append(strIncom);								// append string
							Log.v(TAG, "Newly received: " + strIncom + "sb: " + sb.toString());
							if (strIncom.equals("?")) { // received '?' as errormessage
								Toast.makeText(activity.getBaseContext(), "Last command could not be executed!", Toast.LENGTH_SHORT).show();
							} else {
								if (!strIncom.equals("!")) { // did not received acknoledge
									Toast.makeText(activity.getBaseContext(), "Newly received: " + strIncom + "sb: " + sb.toString(), Toast.LENGTH_SHORT).show();
								}
							}
							sb.delete(0, sb.length());
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

	// get the heartbeat going
	public void startTimer() {
		Log.v(TAG, "starting Timer");
		timer = new Timer();
		timerTask = new TimerTask() {
			@Override
			public void run() {
				sentCommand("0x" + commandLeft + commandRight);
			}
		};
		timer.scheduleAtFixedRate(timerTask,0,iTimeOut/2); 	// play it safe...
	}

	public void stopTimer() {
		//stop the timer, if it's not already null
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
	}

	private final MyHandler mHandler = new MyHandler(this);

	private final static Runnable sRunnable = new Runnable() {
		public void run() {
		}
	};

	private void sentCommand(String commandString) {
		if (!remotePort.matches("^(6553[0-5]|655[0-2]\\d|65[0-4]\\d\\d|6[0-4]\\d{3}|[1-5]\\d{4}|[1-9]\\d{0,3}|0)$")) {
			CharSequence text = "Error: Invalid Port Number";
			Toast toast = Toast.makeText(getBaseContext(), text, Toast.LENGTH_SHORT);
			toast.show();
			return;
		}
		String uriString = "udp://" + host + ":" + remotePort + "/";
		uriString += Uri.encode(commandString);
		Uri uri = Uri.parse(uriString);
		UdpSender udpSender = new UdpSender();
		udpSender.SendTo(this.getApplicationContext(), uri);
	}

	private void loadPref() {
		SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		host = mySharedPreferences.getString("pref_IP_address", host);
		remotePort = mySharedPreferences.getString("pref_Port_number", remotePort);
		mixing = mySharedPreferences.getBoolean("pref_Mixing_active", true);
		udpReceiverHotSpotName = mySharedPreferences.getString("pref_udpReceiverHotSpotName",udpReceiverHotSpotName);
		localPort = mySharedPreferences.getString("pref_rx_Port_number",localPort);
		networkPasskey = mySharedPreferences.getString("pref_networkPasskey",networkPasskey);
		FM_AUTO_PWM = mySharedPreferences.getString("defaultFlightMode_AUTO_PWMValue", getString(R.string.defaultFM_AUTO_PWM));
		FM_LEARNING_PWM = mySharedPreferences.getString("defaultFlightMode_LEARNING_PWMValue", getString(R.string.defaultFM_LEARNING_PWM));
		FM_MANUAL_PWM = mySharedPreferences.getString("defaultFlightMode_MANUAL_PWMValue", getString(R.string.defaultFM_MANUAL_PWM));
		intFMMode = Integer.valueOf(mySharedPreferences.getString("defaultFlightMode", getString(R.string.default_FlightMode)));
		strFMChannel = mySharedPreferences.getString("defaultFlightModeChannel", getString(R.string.default_FlightModeOutput));
	}

	@Override
	protected void onResume() {
		super.onResume();
		suppressMessage = false;
		udpServer.udpConnect(udpReceiverHotSpotName, networkPasskey);
		// start timer onResume if set
		if (iTimeOut > 0) {
			startTimer();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		stopTimer();
		udpServer.udpServer_onPause();
		suppressMessage = true;
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		loadPref();
	}
}
