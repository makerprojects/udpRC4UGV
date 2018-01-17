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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;


public class ActivityWheel extends Activity implements SensorEventListener  {

	private UdpServer udpServer = null;
	private UdpReceiver udpReceiver = null;
	private String udpReceiverHotSpotName;	// hotspot name provided by receiver

	private Button buttonCH7On, buttonAUTO, buttonMANUAL,buttonLEARNING;
	private ImageButton btn_trim_forward, btn_trim_backward, btn_trim_left, btn_trim_right;

	private int xAxis = 0;
	private int yAxis = 0;
	private int motorLeft = 0;
	private int motorRight = 0;
	private boolean show_Debug = false;	// show debug information (from settings)
	private boolean mixing = true;  	// for backward compatibility
	private int xMax = 7;		    	// limit on the X axis from settings  (0-10)
	private int yMax = 5;		    	// limit on the Y axis from settings (0-10)
	private int yThreshold = 50;  		// minimum value of PWM from settings
	private int pwmMax = 126;
										// maximum value of PWM from settings
	private int xR = 5;					// pivot point from settings

	private final String cChannelNeutral = "7F";
	private final String cChannelMax = "FE"; // equals 0xFE
	private final String cChannelMin = "00";
	private String host = "192.168.4.1"; // UDP receiver default address
	private String rx_host = "192.168.4.2"; // UDP sender default address for responses (smart phone IP address) -> not relevant!
	private String rx_port = "12000";		// application default port for responses to smart phone
	private String localPort = "12000";
	private String remotePort = "12001";			// application default port
	private String networkPasskey = "PASSWORD";
	private final String cLeftHeader = "FF00";
	private final String cRightHeader = "FF01";
	private String commandLeft = "FF007F";    // make sure we init the string to avoid problem w/o mixing
	private String commandRight = "FF017F";

	private final int iChannelNeutral = 127;

	private boolean sCommand;
	private int iLastLeft = 255;
	private int iLastRight = 255;

	private boolean bCH7On_sent = false;
	private boolean bCH7Off_sent = false;

	// additional specific defs for processing...
	private SensorManager mSensorManager;
    private Sensor mAccel;
    private Button btn_Rear;
    private VerticalSeekBar VSeekBar;
    private float xgl = 0;
    private boolean isRear = false;															// reverse is off

	private static boolean suppressMessage = false;

	private String FM_AUTO_PWM, FM_LEARNING_PWM, FM_MANUAL_PWM;
	private int	intFMMode;
	private String strFMChannel;

	// trim function defs
	static int iChannel1_neutral = 0;
	static int iChannel2_neutral = 0;

	private static String TAG = ActivityWheel.class.getSimpleName();

	// fail safe related definitions
	Timer timer = null;
	TimerTask timerTask = null;
	int iTimeOut = 0;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		Resources res = getResources();
		mixing = res.getBoolean(R.bool.pref_Mixing);	// enable mixing
		remotePort = res.getString(R.string.default_PORT); // application default port
		localPort = res.getString(R.string.default_rxPORT);
		host = res.getString(R.string.default_IP);   // UDP receiver default address
		networkPasskey = res.getString(R.string.default_networkPasskey);
		udpReceiverHotSpotName = res.getString(R.string.default_udpReceiverHotSpotName);	// hotspot name provided by receiver

        setContentView(R.layout.activity_wheel);
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

		buttonCH7On = (Button) findViewById(R.id.buttonCH7On);
		buttonAUTO = (Button) findViewById(R.id.buttonAUTO);
		buttonLEARNING = (Button) findViewById(R.id.buttonLEARNING);
		buttonMANUAL = (Button) findViewById(R.id.buttonMANUAL);
		btn_trim_forward = (ImageButton) findViewById(R.id.trim_forward);
		btn_trim_backward = (ImageButton) findViewById(R.id.trim_backward);
		btn_trim_left = (ImageButton) findViewById(R.id.trim_left);
		btn_trim_right = (ImageButton) findViewById(R.id.trim_right);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);          
        
        VSeekBar = (VerticalSeekBar) findViewById(R.id.calcVerticalSeekBar);
        VSeekBar.setMaximum(pwmMax);
        
		btn_Rear = (Button) findViewById(R.id.btnRear);

		Globals g = Globals.getInstance();	// load timeout form global variable
		iTimeOut = g.getData();
		Log.d(TAG, "Read timeout " + String.valueOf(iTimeOut));

		udpServer = new UdpServer(this,mHandler);

        btn_Rear.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
		        if(event.getAction() == MotionEvent.ACTION_MOVE) {
		        	isRear = true;
		        } else if (event.getAction() == MotionEvent.ACTION_UP) {
		        	isRear = false;
		        }
				return false;
		    }
		});
        
        VSeekBar.setOnSeekBarChangeListener(new VerticalSeekBar.OnSeekBarChangeListener() {

			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				//Toast.makeText(getBaseContext(), String.valueOf(seekBar.getProgress()), Toast.LENGTH_SHORT).show();
				MotionChanged(xgl,progress);
			}

			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub
				
			}

			public void onStopTrackingTouch(SeekBar seekBar) {
				VSeekBar.setProgressAndThumb(0);
			}
    	});
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

		buttonCH7On.setOnTouchListener(new View.OnTouchListener() {
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

		buttonAUTO.setOnTouchListener(new View.OnTouchListener() {
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

		buttonLEARNING.setOnTouchListener(new View.OnTouchListener() {
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

		buttonMANUAL.setOnTouchListener(new View.OnTouchListener() {
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

		btn_trim_backward.setOnTouchListener(new View.OnTouchListener() {
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

		btn_trim_forward.setOnTouchListener(new View.OnTouchListener() {
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

		btn_trim_right.setOnTouchListener(new View.OnTouchListener() {
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

		btn_trim_left.setOnTouchListener(new View.OnTouchListener() {
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
        private final WeakReference<ActivityWheel> mActivity;
     
        public MyHandler(ActivityWheel activity) {
          mActivity = new WeakReference<ActivityWheel>(activity);
        }
     
        @Override
        public void handleMessage(Message msg) {
        	ActivityWheel activity = mActivity.get();
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
										Log.d(TAG, "Neutral set to: " + String.valueOf(iChannel1_neutral));
									} else {
										iChannel2_neutral = Integer.parseInt(strIncom.toString());
									}
								}
							}
						}
						break;
					case UdpServer.WIFI_NOT_AVAILABLE:
						Log.d(UdpServer.TAG, "Wifi not available (Android system setting). Exit");
						Toast.makeText(activity.getBaseContext(), "Wifi not available (Android system setting). Exit", Toast.LENGTH_SHORT).show();
						activity.finish();
						break;
					case UdpServer.MISSING_PERMISSION_TO_ACCESS_LOCATION:
						Log.d(UdpServer.TAG, "Missing Permission to access position (Android >= 6). Exit");
						Toast.makeText(activity.getBaseContext(), "Missing Android permission - Access to location required. Exit)", Toast.LENGTH_SHORT).show();
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
		public void run() { }
	};
	
	public void onSensorChanged(SensorEvent e) {
	    float xRaw;		// RAW-value from Accelerometer sensor 
	    
		WindowManager windowMgr = (WindowManager)this.getSystemService(WINDOW_SERVICE);
        int rotationIndex = windowMgr.getDefaultDisplay().getRotation();
        if (rotationIndex == 1 || rotationIndex == 3){
        	xRaw = -e.values[1];
        }
        else{
        	xRaw = e.values[0]; 	
        }
		MotionChanged(xRaw,VSeekBar.getProgress());
		xgl = xRaw;
	}
        
	private void MotionChanged(float x, int y) {

    	Log.d(TAG, Math.round(x)+"  "+y);
    	    	
    	xAxis = - Math.round(x*pwmMax/xR);
        //yAxis = -y;
    	if(isRear) yAxis = y;
    	else yAxis = -y;
        
        if(xAxis > pwmMax) xAxis = pwmMax;
        else if(xAxis < -pwmMax) xAxis = -pwmMax;		// negative - tilt right 
        
        if(yAxis > pwmMax) yAxis = pwmMax;
        else if(yAxis < -pwmMax) yAxis = -pwmMax;		// negative - tilt forward 

		if (mixing) {
			if (xAxis > 0) {        // if tilt to left, slow down the left engine
				motorRight = yAxis;
				if (Math.abs(Math.round(x)) > xR) {
					motorLeft = Math.round((x - xR) * pwmMax / (xMax - xR));
					motorLeft = Math.round(-motorLeft * yAxis / pwmMax);
					//if(motorLeft < -pwmMax) motorLeft = -pwmMax;
				} else motorLeft = yAxis - yAxis * xAxis / pwmMax;
			} else if (xAxis < 0) {        // tilt to right
				motorLeft = yAxis;
				if (Math.abs(Math.round(x)) > xR) {
					motorRight = Math.round((Math.abs(x) - xR) * pwmMax / (xMax - xR));
					motorRight = Math.round(-motorRight * yAxis / pwmMax);
					//if(motorRight > -pwmMax) motorRight = -pwmMax;
				} else motorRight = yAxis - yAxis * Math.abs(xAxis) / pwmMax;
			} else if (xAxis == 0) {
				motorLeft = yAxis;
				motorRight = yAxis;
			}

			if (motorLeft > 0) {
				if (motorLeft > pwmMax) motorLeft = pwmMax;
				motorLeft = motorLeft + iChannelNeutral;
			} else {
				if (motorLeft < -pwmMax) motorLeft = -pwmMax;
				motorLeft = motorLeft + iChannelNeutral;
			}

			if (motorRight > 0) {
				if (motorRight > pwmMax) motorRight = pwmMax;
				motorRight = -motorRight + iChannelNeutral;
			} else {
				if (motorRight < -pwmMax) motorRight = -pwmMax;
				motorRight = -motorRight + iChannelNeutral;
			}
		} else {
			motorLeft = iChannelNeutral + xAxis;
			motorRight = iChannelNeutral - yAxis;
		}

		// format command strings for miniSSC processing...
		if (motorRight < 0x10) {
			commandRight = cRightHeader + "0" + String.format("%X",(byte) motorRight);
		} else {
			commandRight = cRightHeader + String.format("%X", (byte) motorRight);
		}
		if (motorLeft < 0x10) {
			commandLeft = cLeftHeader + "0" + String.format("%X",(byte) motorLeft);
		} else {
			commandLeft = cLeftHeader + String.format("%X", (byte) motorLeft);
		}

		// evaluate thresholds...
		sCommand = Math.abs(motorLeft - iLastLeft) > xMax;
		if (Math.abs(motorRight-iLastRight) > yMax) sCommand = true;

		if (sCommand) {
			sentCommand("0x" + commandLeft + commandRight);
			iLastLeft = motorLeft;
			iLastRight = motorRight;
		}

		TextView textX = (TextView) findViewById(R.id.textViewX);
        TextView textY = (TextView) findViewById(R.id.textViewY);
        TextView mLeft = (TextView) findViewById(R.id.mLeft);
        TextView mRight = (TextView) findViewById(R.id.mRight);
        TextView textCmdSend = (TextView) findViewById(R.id.textViewCmdSend);
        
        if(show_Debug){
        	textX.setText(String.valueOf("X:" + String.format("%.1f",x) + "; xPWM:"+xAxis));
	        textY.setText(String.valueOf("Y:" + String.valueOf(y) + "; yPWM:"+yAxis));
        }
        else{
        	textX.setText("");
        	textY.setText("");
        	mLeft.setText("");
        	mRight.setText("");
        	textCmdSend.setText("");
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

	private void loadPref(){
    	SharedPreferences mySharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		host = mySharedPreferences.getString("pref_IP_address", host);
		remotePort = mySharedPreferences.getString("pref_Port_number", remotePort);
		mixing = mySharedPreferences.getBoolean("pref_Mixing_active", true);
		xMax = Integer.parseInt(mySharedPreferences.getString("pref_xMax", String.valueOf(xMax)));
		xR = Integer.parseInt(mySharedPreferences.getString("pref_xR", String.valueOf(xR)));
		yMax = Integer.parseInt(mySharedPreferences.getString("pref_yMax", String.valueOf(yMax)));
		yThreshold = Integer.parseInt(mySharedPreferences.getString("pref_yThreshold", String.valueOf(yThreshold)));
		show_Debug = mySharedPreferences.getBoolean("pref_Debug", false);
		udpReceiverHotSpotName = mySharedPreferences.getString("pref_udpReceiverHotSpotName",udpReceiverHotSpotName);
		networkPasskey = mySharedPreferences.getString("pref_networkPasskey",networkPasskey);
		localPort = mySharedPreferences.getString("pref_rx_Port_number",localPort);
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
    	mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
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
		mSensorManager.unregisterListener(this);
		suppressMessage = true;
		stopTimer();
		udpServer.udpServer_onPause();
		if (udpReceiver != null) {
			udpReceiver.stopUdpReceiver();
			udpReceiver = null;
		}
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	loadPref();
    }
    
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO Auto-generated method stub
    }
}
