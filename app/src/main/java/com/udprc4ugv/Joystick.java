/*
 *  (C) Copyright 2018 Gregor Schlechtriem (http://www.pikoder.com).
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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;


public class Joystick extends View {

    // additional specific defs for processing...
    private final static int FINGER_CIRCLE_SIZE = 20;

    Paint fingerPaint, borderPaint, textPaint, alphaPaint;

    int startX;
    int startY;
    int SQUARE_SIDE_LENGTH;

    float x;
    float y;

    float xcirc;
    float ycirc;

    float lastX = 0;
    float lastY = 0;

    int xMoveThreshhold = 1;
    int yMoveThreshold = 1;

    // variables for drag
    boolean drag = false;
    boolean initStickPosition = true;
    float dragX = 0;
    float dragY = 0;

    int	mDirectionChannel;
    int mThrottleChannel;

    private int pwmMax;	// maximum value of PWM from settings
    private int xRperc;	// pivot point for motor
    private final int iChannelNeutral = 127;

    boolean mixing;
    boolean mDirectionNeutralize;
    boolean mThrottleNeutralize;
    boolean mDirectionReverseFlag;
    boolean mThrottleReverseFlag;

    String mUriString;

    private static String TAG = Joystick.class.getSimpleName();

    public Joystick(Context context, int DirectionChannel, int ThrottleChannel, boolean my_mixing, boolean DirectionNeutralize, boolean ThrottleNeutralize, boolean DirectionReverseFlag, boolean ThrottleReverseFlag, String uriString) {
        super(context);

        mDirectionChannel = DirectionChannel;
        mThrottleChannel = ThrottleChannel;
        mDirectionNeutralize = DirectionNeutralize;
        mDirectionReverseFlag = DirectionReverseFlag;
        mThrottleReverseFlag = ThrottleReverseFlag;
        mThrottleNeutralize = ThrottleNeutralize;

        mUriString = uriString;

        mixing = my_mixing;
        fingerPaint = new Paint();
        fingerPaint.setAntiAlias(true);
        fingerPaint.setColor(Color.RED);
        fingerPaint.setStrokeWidth(2);

        borderPaint = new Paint();
        borderPaint.setColor(Color.BLUE);
        borderPaint.setAntiAlias(true);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(14);

        alphaPaint = new Paint();
        alphaPaint.setAlpha(75); // equals 0.3 on a range 0 .. 255

        final Resources res = getResources();
        pwmMax = res.getInteger(R.integer.default_pwmMax);	// maximum value of PWM from settings
        xRperc = res.getInteger(R.integer.default_xRperc); 	// pivot point for motor
    }

    public void setmDirectionReverseFlag (boolean newDirectionReverseFlag) {
        mDirectionReverseFlag = newDirectionReverseFlag;
    }

    public void setmThrottleReverseFlag (boolean newThrottleReverseFlag) {
        mThrottleReverseFlag = newThrottleReverseFlag;
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); // the default drawing

        SQUARE_SIDE_LENGTH = canvas.getWidth() * 7/20;	// use 70% of width
        if (SQUARE_SIDE_LENGTH > canvas.getHeight()/2) {
            SQUARE_SIDE_LENGTH = canvas.getHeight()/2; // or use 100% of height (if this dim is smaller)
        }
        startX= canvas.getWidth()/2;	//	for horizontal position
        startY= canvas.getHeight()/2;	//	for vertical position

        if (initStickPosition) {
            x = startX;
            y = startY;
            initStickPosition = false;
        }
        if(!drag){
            if (mDirectionNeutralize) {
                x = startX;
            }
            if (mThrottleNeutralize) {
                y = startY;
            }
            fingerPaint.setColor(Color.RED);
            // performance optimization 29.01. - redraw only when finger is up since circle is not visible anyway under finger
            canvas.drawCircle(x, y, FINGER_CIRCLE_SIZE, fingerPaint); // 29.01 - perf. optimization
        }

        // performance optimization 29.01. - make sure that empty frame is reprinted once
        canvas.drawRect(startX - SQUARE_SIDE_LENGTH, startY - SQUARE_SIDE_LENGTH, startX + SQUARE_SIDE_LENGTH, startY + SQUARE_SIDE_LENGTH, borderPaint);
        canvas.drawLine(startX, startY - SQUARE_SIDE_LENGTH +9 , startX,startY + SQUARE_SIDE_LENGTH - 9, fingerPaint);
        canvas.drawLine(startX - SQUARE_SIDE_LENGTH + 9, startY, startX + SQUARE_SIDE_LENGTH - 9, startY, fingerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // coordinate of Touch-event
        float evX = event.getX();
        float evY = event.getY();

        // transform to relative coordinates
        xcirc = event.getX() - startX;
        ycirc = event.getY() - startY;
        Log.d(TAG, String.valueOf("X:"+this.getRight()+" Y:"+startY));

        boolean inside = (Math.abs(xcirc) <= SQUARE_SIDE_LENGTH) && (Math.abs(ycirc) <= SQUARE_SIDE_LENGTH);

        int action = MotionEventCompat.getActionMasked(event);
        int index = MotionEventCompat.getActionIndex(event);
        Log.d(TAG,"The action is " + actionToString(action));

        switch (action) {

            case MotionEvent.ACTION_DOWN:
                if(inside){
                    x = evX;
                    y = evY;
                    fingerPaint.setColor(Color.GREEN);
                    CalcMotor(Math.round(xcirc*pwmMax/SQUARE_SIDE_LENGTH),Math.round(ycirc*pwmMax/SQUARE_SIDE_LENGTH), mDirectionChannel, mThrottleChannel, SQUARE_SIDE_LENGTH, mixing);
                    lastX = xcirc;
                    lastY = ycirc;
                    invalidate();
                    drag = true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                // if drag mode is enabled
                if (drag && inside) {
                    x = evX;
                    y = evY;
//        			fingerPaint.setColor(Color.GREEN);
                    if ((Math.abs(lastX - xcirc) > xMoveThreshhold) || (Math.abs(lastY - ycirc) > yMoveThreshold)) {
                        CalcMotor(Math.round(xcirc * pwmMax / SQUARE_SIDE_LENGTH), Math.round(ycirc * pwmMax / SQUARE_SIDE_LENGTH), mDirectionChannel, mThrottleChannel, SQUARE_SIDE_LENGTH, mixing);
                    }
                    lastX = xcirc;
                    lastY = ycirc;
                    // invalidate(); 29.01 - performance optimization
                }
                break;

            // touch completed
            case MotionEvent.ACTION_UP:
                // turn off the drag mode
                drag = false;
                if (mDirectionNeutralize) {
                    xcirc = 0;
                }
                if (mThrottleNeutralize) {
                    ycirc = 0;
                }
                CalcMotor(Math.round(xcirc*pwmMax/SQUARE_SIDE_LENGTH),Math.round(ycirc*pwmMax/SQUARE_SIDE_LENGTH), mDirectionChannel, mThrottleChannel, SQUARE_SIDE_LENGTH, mixing);
                invalidate();
                break;
        }
        return true;
    }

    // Given an action int, returns a string description
    public String actionToString(int action) {
        switch (action) {

            case MotionEvent.ACTION_DOWN: return "Down";
            case MotionEvent.ACTION_MOVE: return "Move";
            case MotionEvent.ACTION_POINTER_DOWN: return "Pointer Down";
            case MotionEvent.ACTION_UP: return "Up";
            case MotionEvent.ACTION_POINTER_UP: return "Pointer Up";
            case MotionEvent.ACTION_OUTSIDE: return "Outside";
            case MotionEvent.ACTION_CANCEL: return "Cancel";
        }
        return "";
    }

    /**
     * This method calculates the actual values (for the miniSSC protocol to drive the motors
     *
     * @param xAxis value from -126 .. 126 for the channel value in horizontal direction (in miniSSC)
     * @param yAxis see xAxis - this one is the vertical channel value
     * @param mDirectionChannel first channel used for mixing
     * @param mThrottleChannel second channel used for mixing
     * @param SQUARE_SIDE_LENGTH equals 0,5 * dimension of the stick's square
     * @param mixing activate mixing
     *
     */

    private void CalcMotor(int xAxis, int yAxis, int mDirectionChannel, int mThrottleChannel, int SQUARE_SIDE_LENGTH, boolean mixing){

        Log.d(TAG, String.valueOf("xAxis:"+xAxis+"  yAxis"+yAxis));

        int motorLeft = 0;
        int motorRight = 0;

        String commandRight;
        String commandLeft;

        if (mixing) {
            int xR = Math.round(SQUARE_SIDE_LENGTH*xRperc/100);		// calculate the value of pivot point
            float calc_x = xAxis * SQUARE_SIDE_LENGTH / pwmMax; //	scaling back in order to maintain mixing algorithm
            if (xAxis > 0) {
                motorRight = yAxis;
                if (Math.abs(Math.round(calc_x)) > xR) {
                    motorLeft = Math.round((calc_x - xR) * pwmMax / (SQUARE_SIDE_LENGTH - xR));
                    motorLeft = Math.round(-motorLeft * yAxis / pwmMax);
                } else motorLeft = yAxis - yAxis * xAxis / pwmMax;
            } else if (xAxis < 0) {
                motorLeft = yAxis;
                if (Math.abs(Math.round(calc_x)) > xR) {
                    motorRight = Math.round((Math.abs(calc_x) - xR) * pwmMax / (SQUARE_SIDE_LENGTH - xR));
                    motorRight = Math.round(-motorRight * yAxis / pwmMax);
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
            if (mDirectionReverseFlag) {
                motorLeft = iChannelNeutral - xAxis;
            } else {
                motorLeft = iChannelNeutral + xAxis;
            }
            if (mThrottleReverseFlag) {
                motorRight = iChannelNeutral - yAxis;
            } else {
                motorRight = iChannelNeutral + yAxis;
            }
        }

        // sent command - threshold evaluation is done by Caller due to different callers (sticks)
        // format command strings for miniSSC processing...
        if (motorRight < 0x10) {
            commandRight = "FF0" + String.valueOf(mThrottleChannel) + "0" + String.format("%X",(byte) motorRight);
        } else {
            commandRight = "FF0" + String.valueOf(mThrottleChannel) + String.format("%X", (byte) motorRight);
        }
        if (motorLeft < 0x10) {
            commandLeft = "FF0" + String.valueOf(mDirectionChannel) + "0" + String.format("%X",(byte) motorLeft);
        } else {
            commandLeft = "FF0" + String.valueOf(mDirectionChannel) + String.format("%X", (byte) motorLeft);
        }
        mSendCommand("0x" + commandLeft + commandRight);
    }

    private void mSendCommand(String commandString) {
        Log.d(TAG, "mSendCommand (URI): " + mUriString);
        Log.d(TAG, "mSendCommand (Command): " + commandString);
        Uri uri = Uri.parse(mUriString + Uri.encode(commandString));
        UdpSender udpSender = new UdpSender();
        udpSender.SendTo(uri);
    }

}

