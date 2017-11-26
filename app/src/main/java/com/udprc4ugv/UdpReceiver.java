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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;

import static com.udprc4ugv.UdpServer.RECEIVE_MESSAGE;

public class UdpReceiver  {

    private AsyncTask<Void, Void, Void> async;
    private static String TAG = UdpReceiver.class.getSimpleName();
    DatagramSocket ds = null;

    @SuppressLint({"NewApi", "StaticFieldLeak"})
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
                        int i;
                        String sBuilder = "";
                        ds = new DatagramSocket(uri.getPort());
                        Log.v(TAG, "Created DatagramSocket at port: " + uri.getPort());
                        while(true) {
                            i = 0;
                            ds.receive(dp);
                            Log.v(TAG, "Server active... received packet" + new String(dp.getData(), dp.getOffset(), dp.getLength()));
                            sBuilder = sBuilder.concat(new String(dp.getData(), dp.getOffset(), dp.getLength()));
                            if (sBuilder.length()>0) {
                                if (sBuilder.charAt(i++) == '\r') {
                                    if (sBuilder.length()>1) {
                                        if (sBuilder.charAt(i++) == '\n') {
                                            while (sBuilder.length() > i) {
                                                if (sBuilder.charAt(i++) == '\n') {
                                                    sBuilder = sBuilder.replace("\r","").replace("\n","");
                                                    handler.obtainMessage(RECEIVE_MESSAGE, sBuilder.length(), 0, sBuilder).sendToTarget();
                                                    sBuilder = "";
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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

