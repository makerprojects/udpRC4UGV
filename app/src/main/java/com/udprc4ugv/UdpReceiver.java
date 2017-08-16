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
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;

public class UdpReceiver  {

    private AsyncTask<Void, Void, Void> async;
    private static String TAG = UdpReceiver.class.getSimpleName();
    public final static int RECEIVE_MESSAGE = 5;			// receive message
    DatagramSocket ds = null;

    @SuppressLint("NewApi")
    public void runUdpReceiver(final Uri uri, final Context context, final Handler handler) {
            async = new AsyncTask<Void, Void, Void>()
            {
                @Override
                protected Void doInBackground(Void... params)
                {
                    Log.v(TAG, "Started background task...");
                    byte[] lMsg = new byte[4096];
                    DatagramPacket dp = new DatagramPacket(lMsg, lMsg.length);
                    try {
                        ds = new DatagramSocket(uri.getPort());
                        Log.v(TAG, "Created DatagramSocket at port: " + uri.getPort());
                        while(true) {
                            ds.receive(dp);
                            Log.v(TAG, "Server active... received packet" + new String(dp.getData(), dp.getOffset(), dp.getLength()));
                            handler.obtainMessage(RECEIVE_MESSAGE, dp.getLength(), dp.getOffset(), dp.getData()).sendToTarget();
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        String message = e.getMessage();
                        Log.v(TAG, "Server exception: " + message);
                    }
                    finally {
                        if (ds != null) {
                            ds.close();
                            Log.v(TAG, "Closed socket...");
                        }
                    }
                    return null;
                }
            };

            async.execute();
        }

    public void stopUdpReceiver() {
        Log.v(TAG, "Stop receiver...");
        ds.close(); // ds would be blocked -> we exit with caught exception
    }
}

