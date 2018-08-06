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
import android.content.res.Resources;
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
import android.widget.ImageButton;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

import static com.udprc4ugv.R.layout.activity_buttons;

public class ActivityButtons extends Activity {

	private UdpServer udpServer = null;
	private UdpReceiver udpReceiver = null;

	private Button btn_forward, btn_backward, btn_left, btn_right;
	private Button buttonCH7On, buttonAUTO, buttonMANUAL,buttonLEARNING;
	private ImageButton btn_trim_forward, btn_trim_backward, btn_trim_left, btn_trim_right;


	private final String cChannelNeutral = "7F";
	private final String cChannelMax = "FE"; // equals 0xFE
	private final String cChannelMin = "00";

	private String udpReceiverHotSpotName;	// hotspot name provided by receiver
	private String networkPasskey;
	private String host = "192.168.4.1"; // UDP receiver default address
	private String rx_host = "192.168.4.2"; // UDP sender default address for responses (smart phone IP address) -> not relevant!
	private String rx_port = "12000";		// application default port for responses to smart phone
	private String remotePort = "12001";            // application default port
	private String localPort = "12000";
	private String commandLeft;
	private String commandRight;
	private String cLeftHeader;
	private String cRightHeader;
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
	private static boolean suppressMessage = false;
	private String FM_AUTO_PWM, FM_LEARNING_PWM, FM_MANUAL_PWM;

	private static String TAG = ActivityButtons.class.getSimpleName();
	private static final boolean D = true;

	private int	intFMMode;

	// defs for PPM frame position
	private String DirectionRXChannel;
	private String ThrottleRXChannel;

	// trim function defs
	static int iChannel1_neutral = 0;
	static int iChannel2_neutral = 0;

	// fail safe related definitions
	Timer timer = null;
	TimerTask timerTask = null;
	int iTimeOut = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Resources res = getResources();
		mixing = res.getBoolean(R.bool.pref_Mixing);	// enable mixing
		remotePort = res.getString(R.string.default_PORT); // application default port
		localPort = res.getString(R.string.default_rxPORT);
		host = res.getString(R.string.default_IP);   // UDP receiver default address
		networkPasskey = res.getString(R.string.default_networkPasskey);
		udpReceiverHotSpotName = res.getString(R.string.default_udpReceiverHotSpotName);	// hotspot name provided by receiver
		DirectionRXChannel = res.getString(R.string.default_channelLeftRight);
		ThrottleRXChannel = res.getString(R.string.default_channelForwardBackward);

		loadPref();

		commandLeft = "FF0" + String.valueOf(Integer.valueOf(DirectionRXChannel) -1) + "7F";    // make sure we init the string to avoid problem w/o mixing
		commandRight = "FF0" + String.valueOf(Integer.valueOf(ThrottleRXChannel) -1) + "7F";

		cRightHeader = "FF0" + String.valueOf(Integer.valueOf(ThrottleRXChannel) -1);
		cLeftHeader = "FF0" + String.valueOf(Integer.valueOf(DirectionRXChannel) -1);

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
		btn_trim_forward = (ImageButton) findViewById(R.id.trim_forward);
		btn_trim_backward = (ImageButton) findViewById(R.id.trim_backward);
		btn_trim_left = (ImageButton) findViewById(R.id.trim_left);
		btn_trim_right = (ImageButton) findViewById(R.id.trim_right);

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

		btn_trim_backward.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_UP) {
					if (iChannel2_neutral == 0) {
						iChannel2_neutral = 1;
						sentCommand("N2?");
						Toast.makeText(getBaseContext(), "Retrieving value from PiKoder...", Toast.LENGTH_SHORT).show();
					}
					if (iChannel2_neutral >= 1004) {
						iChannel2_neutral = iChannel2_neutral - 4;
						sentCommand("N2=" + Integer.toString(iChannel2_neutral));
						sentCommand("0x" + cRightHeader + cChannelNeutral);
					} else {
						if (iChannel2_neutral > 1) {
							Toast.makeText(getBaseContext(), "Reached minmium trim value...", Toast.LENGTH_SHORT).show();
						}
					}
				}
				return false;
			}
		});

		btn_trim_forward.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_UP) {
					if (iChannel2_neutral == 0) {
						iChannel2_neutral = 1;
						sentCommand("N2?");
						Toast.makeText(getApplicationContext(), "Retrieving value from PiKoder...", Toast.LENGTH_SHORT).show();
					}
					if (iChannel2_neutral < 1997) {
						iChannel2_neutral = iChannel2_neutral + 4;
						sentCommand("N2=" + Integer.toString(iChannel2_neutral));
						sentCommand("0x" + cRightHeader + cChannelNeutral);
					} else {
						if (iChannel2_neutral > 1) {
							Toast.makeText(getBaseContext(), "Reached maximum trim value...", Toast.LENGTH_SHORT).show();
						}
					}
				}
				return false;
			}
		});

		btn_trim_right.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_UP) {
					if (iChannel1_neutral == 0) {
						iChannel1_neutral = 1;
						sentCommand("N1?");
						Toast.makeText(getBaseContext(), "Retrieving value from PiKoder...", Toast.LENGTH_SHORT).show();
					}
					if (iChannel1_neutral >= 1004) {
						iChannel1_neutral = iChannel1_neutral - 4;
						sentCommand("N1=" + Integer.toString(iChannel1_neutral));
						sentCommand("0x" + cLeftHeader + cChannelNeutral);
					} else {
						if (iChannel1_neutral > 1) {
							Toast.makeText(getBaseContext(), "Reached minmium trim value...", Toast.LENGTH_SHORT).show();
						}
					}
				}
				return false;
			}
		});

		btn_trim_left.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_UP) {
					if (iChannel1_neutral == 0) {
						iChannel1_neutral = 1;
						sentCommand("N1?");
						Toast.makeText(getApplicationContext(), "Retrieving value from PiKoder...", Toast.LENGTH_SHORT).show();
					}
					if ((iChannel1_neutral < 1997) && (iChannel1_neutral >= 1000)) {
						iChannel1_neutral = iChannel1_neutral + 4;
						sentCommand("N1=" + Integer.toString(iChannel1_neutral));
						sentCommand("0x" + cLeftHeader + cChannelNeutral);
					} else {
						if (iChannel1_neutral > 1) {
							Toast.makeText(getBaseContext(), "Reached maximum trim value...", Toast.LENGTH_SHORT).show();
						}
					}
				}
				return false;
			}
		});
	}

	private static class MyHandler extends Handler {
		private final WeakReference<ActivityButtons> mActivity;

		public MyHandler(ActivityButtons activity) {
			mActivity = new WeakReference<>(activity);
		}

		@Override
		public void handleMessage(Message msg) {
			ActivityButtons activity = mActivity.get();
			if (activity != null) {
				switch (msg.what) {
					case UdpServer.RECEIVE_MESSAGE:								// if message is received
						String strIncom = (String) msg.obj;
						Log.d(TAG, "Received: " + strIncom );
						if (strIncom.equals("?")) { // received '?' as errormessage
							Toast.makeText(activity.getBaseContext(), "Last command could not be executed!", Toast.LENGTH_SHORT).show();
						} else {
							if (strIncom.equals("!")) { // did receive acknowledge
							} else {
								if (strIncom.length() >= 4) {
									if (iChannel1_neutral ==1) {
										iChannel1_neutral = Integer.parseInt(strIncom.toString());
										Log.d(TAG, "Neutral channel 1 set to: " + String.valueOf(iChannel1_neutral));
									} else {
										iChannel2_neutral = Integer.parseInt(strIncom.toString());
										Log.d(TAG, "Neutral channel 2 set to: " + String.valueOf(iChannel2_neutral));
									}
								}
							}
						}
						break;
					case UdpServer.WIFI_NOT_AVAILABLE:
						Log.d(UdpServer.TAG, "Wifi not available (Android system setting). Exit");
						Toast.makeText(activity.getBaseContext(), "Wifi not available (Android system setting). Exit", Toast.LENGTH_LONG).show();
						activity.finish();
						break;
					case UdpServer.MISSING_PERMISSION_TO_ACCESS_LOCATION:
						Log.d(UdpServer.TAG, "Missing Permission to access position (Android >= 6). Exit");
						Toast.makeText(activity.getBaseContext(), "Missing Android permission - Access to location required. Exit)", Toast.LENGTH_LONG).show();
						activity.finish();
						break;
					case UdpServer.RECEIVER_NOT_ON_SCAN_LIST:
						Log.d(UdpServer.TAG, "AP is not on current scan list. Exit");
						Toast.makeText(activity.getBaseContext(), "Receiver is not offered by Android system scan", Toast.LENGTH_LONG).show();
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
		public void run() {
		}
	};

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
		DirectionRXChannel = mySharedPreferences.getString("pref_channelLeftRight", DirectionRXChannel);
		ThrottleRXChannel = mySharedPreferences.getString("pref_channelForwardBackward", ThrottleRXChannel);
	}

	@Override
	protected void onResume() {
		super.onResume();
		suppressMessage = false;
		udpServer.udpConnect(udpReceiverHotSpotName, networkPasskey);
		if (udpReceiver == null) {
			Log.v(TAG, "Restarting receiver...");
			String uriString = "udp://" + rx_host + ":" + rx_port + "/";
			Uri uri = Uri.parse(uriString);
			udpReceiver = new UdpReceiver();
			udpReceiver.runUdpReceiver(uri, this, mHandler);
		}
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
		if (udpReceiver != null) {
			udpReceiver.stopUdpReceiver();
			udpReceiver = null;
		}
		suppressMessage = true;
	}

	@Override
	protected void onStop() {
		super.onStop();
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
