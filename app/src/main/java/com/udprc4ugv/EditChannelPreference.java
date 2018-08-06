package com.udprc4ugv;

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import static com.udprc4ugv.UdpServer.TAG;

public class EditChannelPreference extends EditTextPreference {

    public int num1 = 0;
    private boolean _ignore = true;

    public EditChannelPreference(final Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        getEditText().addTextChangedListener(new TextWatcher(){

            @Override
            public void afterTextChanged(Editable s) {
                String working = s.toString();
                if (working.length() == 1) {
                    try {
                        num1 = Integer.parseInt(working);
                        Log.v(TAG, "Parsed num1 afterTextChanged: " + String.valueOf(num1));
                    } catch (NumberFormatException e) {
                        if (working.length() > 0) {
                            String err_data_entry = context.getString(R.string.err_data_entry);
                            Toast.makeText(context, err_data_entry, Toast.LENGTH_SHORT).show();
                        } else num1 = 0;
                    }
                }
                else num1 = 0;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Nothing happens here
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Nothing happens here
            }

        });
    }

    @Override
    public void onDialogClosed (boolean positiveResult) {
        if (positiveResult) {
            Log.v(TAG, "onDialogClosed; num1= " + String.valueOf(num1));
            if (num1 < 1 || num1 > 8) {
                CharSequence text = "Error: Incorrect Channel Value or Format!";
                Toast toast = Toast.makeText(getContext(), text, Toast.LENGTH_SHORT);
                toast.show();
                positiveResult = false;
            } else {
                String output = getText();
                Log.v(TAG, "onDialogClosed retrieved " + output);
            }
        }
        super.onDialogClosed(positiveResult);
    }
}
