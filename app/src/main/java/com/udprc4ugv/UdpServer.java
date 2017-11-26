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

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.util.Log;

import java.util.List;

/**
 * This class does all the work for setting up and managing udp
 * connections with other devices. It will connect to a predefined
 * hotspot and handle the two way traffic to this hotspot when
 * connected.
 */

public class UdpServer {
    // Debugging
    public static String TAG = UdpServer.class.getSimpleName();  // used in other modules to indicate bluetooth error conditions
    private static final boolean D = true;
    final Handler toastHandler = new Handler();

    // Member fields
    private final Handler mHandler;
    private int mState;
    Context mContext;
    private int iTimeOut = 50; // max. number of loops for receiving the ip address

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // existence of AP not verified yet
    public static final int STATE_AVAILABLE = 1;    // AP in Android scan list
    public static final int STATE_CONFIGURED = 2; // ap is already configured in Android world
    public static final int STATE_COULD_NOT_CONNECT = 3; // connection attempt failed
    public static final int STATE_CONNECTING = 4; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 5;  // now connected to the receiver

    // statuses for Handler
    public final static int WIFI_NOT_AVAILABLE = 1;            // wifi is not available
    public final static int RECEIVER_NOT_ON_SCAN_LIST = 10; // did not find receiver on scan list..
    public final static int WIFI_INCORRECT_ADDRESS = 2;        // incorrect IP-address
    public final static int WIFI_REQUEST_ENABLE = 3;        // request enable wifi
    public final static int WIFI_SOCKET_FAILED = 4;            // socket error
    public final static int RECEIVE_MESSAGE = 5;            // receive message
    public final static int USER_STOP_INITIATED = 6;        // user hit back button - shutting down
    public final static int WIFI_HOTSPOT_NOT_FOUND = 7;      // hotspot not found in list of paired devices
    public final static int WIFI_HOTSPOT_CONNECTING = 8;      // hotspot not found in list of paired devices
    public final static int WIFI_HOTSPOT_CONNECTED = 9;      // hotspot not found in list of paired devices

    /**
     * Constructor. Prepares a new hotspot connection to receiver
     *
     * @param handler A Handler to send messages back to the UI Activity
     */
    public UdpServer(Context mContext, Handler handler) {
        this.mContext = mContext;
        mState = STATE_NONE;
        mHandler = handler;
    }


    /*
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    public void udpServer_onPause() {
    	Log.d(TAG, "...On Pause...");
        mState = STATE_NONE; // make sure that we reconnect properly when resuming
     }

    /**
     * Start the ConnectThread to initiate a connection to a remote device. Called by the
     * Activity onResume()
     * @param udpHotSpotName  The AP to connect to
     * @param networkPasskey  The AP's passkey
     */
    public synchronized void udpConnect(String udpHotSpotName, String networkPasskey) {

        WifiInfo wifiInfo = null;
        int apId = 0;
        mState = STATE_NONE;
        if (D) Log.d(TAG, "start udpConnect with state " + mState);
        mHandler.sendEmptyMessage(WIFI_REQUEST_ENABLE);
        WifiManager wifiManager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        List<ScanResult> scanResultList = wifiManager.getScanResults();
        for (ScanResult result : scanResultList) {
            if (D) Log.d(TAG, "scanned: " + result.SSID);
            if (result.SSID.equals(udpHotSpotName)) {
                String securityMode = getScanResultSecurity(result);
                WifiConfiguration wifiConfiguration = createAPConfiguration(udpHotSpotName, networkPasskey, securityMode);
                if (D) Log.d(TAG, "ap is on!");
                mState = STATE_AVAILABLE;
                List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
                if (list == null) {
                    mState = WIFI_NOT_AVAILABLE;
                } else {
                    for (WifiConfiguration i : list) {
                        if (D) Log.d(TAG, "configured: " + i.SSID);
                        if (i.SSID != null && i.SSID.equals("\"" + udpHotSpotName + "\"")) {
                            mState = STATE_CONFIGURED;
                            apId = i.networkId;
                            if (D) Log.d(TAG, i.SSID + " already configured!");
                            break;
                        }
                    }
                    if (mState < STATE_CONFIGURED) {
                        apId = wifiManager.addNetwork(wifiConfiguration);
                        if (D) Log.d(TAG, "added network, id is: " + apId);
                    }
                    boolean b = wifiManager.enableNetwork(apId, true);
                    if (D) Log.d(TAG, "enable network returned: " + b);
                    wifiManager.setWifiEnabled(true);
                    boolean changeHappen = wifiManager.saveConfiguration();
                    if (apId != -1 && changeHappen) {
                        Log.d(TAG, "connecting to: " + result.SSID);
                        mState = STATE_CONNECTING;
                    } else {
                        mState = STATE_COULD_NOT_CONNECT;
                    }
                }
            }
        }
        switch (mState) {
            case WIFI_NOT_AVAILABLE:
                mHandler.sendEmptyMessage(WIFI_NOT_AVAILABLE);
                break;
            case STATE_NONE:
                mHandler.sendEmptyMessage(RECEIVER_NOT_ON_SCAN_LIST);
                break;
            case STATE_COULD_NOT_CONNECT:
                mHandler.sendEmptyMessage(WIFI_INCORRECT_ADDRESS);
                break;
            case STATE_CONNECTING:
                if (D) Log.d(TAG, "start connecting: ");
                mHandler.sendEmptyMessage(WIFI_HOTSPOT_CONNECTING);
                wifiManager.reconnect();
                wifiInfo = wifiManager.getConnectionInfo();
                int iTimeOutCount = 0;
                while ((wifiInfo.getSupplicantState() != SupplicantState.valueOf("ASSOCIATING")) && (iTimeOutCount++ < iTimeOut)) {
                    wifiInfo = wifiManager.getConnectionInfo();
                    SystemClock.sleep(50);
                }
                if (iTimeOutCount == iTimeOut) {
                    if (D)
                        Log.d(TAG, "Time Out level 1 encountered! Breaking...");
                } else {
                    iTimeOutCount = 0;
                    if (D) Log.d(TAG, "read Supplicant state: " + wifiInfo.getSupplicantState());
                    while ((wifiInfo.getSupplicantState() != SupplicantState.valueOf("COMPLETED")) && (iTimeOutCount++ < iTimeOut)) {
                        wifiInfo = wifiManager.getConnectionInfo();
                        SystemClock.sleep(50);
                    }
                    if (iTimeOutCount == iTimeOut) {
                        if (D) Log.d(TAG, "Time Out level 2 encountered! Breaking...");
                    } else {
                        iTimeOutCount = 0;
                        if (D)
                            Log.d(TAG, "read Supplicant state: " + wifiInfo.getSupplicantState());
                        while ((wifiInfo.getIpAddress() == 0) && (iTimeOutCount++ < iTimeOut)) {
                            wifiInfo = wifiManager.getConnectionInfo();
                            SystemClock.sleep(50);
                        }
                    }
                }
                if (iTimeOutCount < iTimeOut) {
                    setState(STATE_CONNECTED);
                } else {
                    Log.d(TAG, "Time Out reading state encountered! Breaking...");
                }
                if (mState == STATE_CONNECTED) {
                    if (D) Log.d(TAG, "udpConnect: connected to " + udpHotSpotName);
                    int ip = wifiInfo.getIpAddress();
                    String ipAddress = Formatter.formatIpAddress(ip);
                    Message message = new Message();
                    message.what = WIFI_HOTSPOT_CONNECTED;
                    message.arg1 = ip;
                    mHandler.sendMessage(message);
                    if (D) Log.d(TAG, "local IP address: " + ipAddress);
                } else {
                    connectionLost();
                }
        }
    }

    private WifiConfiguration createAPConfiguration(String networkSSID, String networkPasskey, String securityMode) {
        WifiConfiguration wifiConfiguration = new WifiConfiguration();

        wifiConfiguration.SSID = "\"" + networkSSID + "\"";

        if (securityMode.equalsIgnoreCase("OPEN")) {

            wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

        } else if (securityMode.equalsIgnoreCase("WEP")) {

            wifiConfiguration.wepKeys[0] = "\"" + networkPasskey + "\"";
            wifiConfiguration.wepTxKeyIndex = 0;
            wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);

        } else if (securityMode.equalsIgnoreCase("PSK")) {

            wifiConfiguration.preSharedKey = "\"" + networkPasskey + "\"";
            wifiConfiguration.hiddenSSID = true;
            wifiConfiguration.status = WifiConfiguration.Status.ENABLED;
            wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            wifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            wifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

        } else {
            Log.i(TAG, "# Unsupported security mode: "+securityMode);

            return null;
        }

        return wifiConfiguration;

    }

    public String getScanResultSecurity(ScanResult scanResult) {

        final String cap = scanResult.capabilities;
        final String[] securityModes = { "WEP", "PSK", "EAP" };

        for (int i = securityModes.length - 1; i >= 0; i--) {
            if (cap.contains(securityModes[i])) {
                return securityModes[i];
            }
        }

        return "OPEN";
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        setState(STATE_NONE);
        // Send a failure message back to the Activity
		mHandler.sendEmptyMessage(WIFI_SOCKET_FAILED);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        setState(STATE_NONE);
        // Send a failure message back to the Activity
		mHandler.sendEmptyMessage(WIFI_SOCKET_FAILED);
    }
}
