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
import android.util.Log;

import java.io.IOException;
import java.net.*;

import static com.udprc4ugv.HexHelper.bytesToHex;
import static com.udprc4ugv.HexHelper.hexStringToBytes;

public class UdpSender {
//    final Handler toastHandler = new Handler();
    private static String TAG = UdpSender.class.getSimpleName();

    public synchronized void SendTo(final Uri uri) {

        if (uri == null) return;
        String msg = Uri.decode(uri.getLastPathSegment());
        if(msg == null) return;
        byte[] msgBytes = msg.getBytes();
        if (msg.startsWith("\\0x")) {
            msg = msg.replace("\\0x", "0x");
            msgBytes = msg.getBytes();
        } else if (msg.startsWith("0x")) {
            msg = msg.replace("0x", "");
            if(!msg.matches("[a-fA-F0-9]+")) {
            	return;
            }
            msgBytes = hexStringToBytes(msg);
        }

        final byte[] buf = msgBytes;

        if(Constants.IS_LOGGABLE) {
            Log.d(TAG, new String(msgBytes));
            Log.d(TAG, "0x" + bytesToHex(msgBytes));
        }

        new Thread(new Runnable() {
            public void run() {
                try {
                    InetAddress serverAddress = InetAddress.getByName(uri
                            .getHost());
                    //Log.v(getString(R.string.app_name), serverAddress.getHostAddress());
                    DatagramSocket socket = new DatagramSocket(); // recast with port number as parameter to ensure static assignment
                    if (!socket.getBroadcast()) socket.setBroadcast(true);
                    DatagramPacket packet = new DatagramPacket(buf, buf.length,
                            serverAddress, uri.getPort());
                    socket.send(packet);
                    socket.close();
                } catch (final UnknownHostException e) {
                    /*toastHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, e.toString(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }); */
                    e.printStackTrace();
                } catch (final SocketException e) {
                    /* toastHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, e.toString(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }); */
                    e.printStackTrace();
                } catch (final IOException e) {
                     /* toastHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, e.toString(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }); */
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
