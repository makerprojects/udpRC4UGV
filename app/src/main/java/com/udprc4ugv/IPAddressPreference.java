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
import android.preference.EditTextPreference;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.Toast;

public class IPAddressPreference extends EditTextPreference {

    public String nAddress;

    public IPAddressPreference(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        nAddress = getText();
        getEditText().setInputType(InputType.TYPE_CLASS_PHONE);
        getEditText().setFilters(new InputFilter[] { new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, android.text.Spanned dest, int dstart, int dend) {
                if (end > start) {
                    String destTxt = dest.toString();
                    String resultingTxt = destTxt.substring(0, dstart) + source.subSequence(start, end) + destTxt.substring(dend);
                    if (!resultingTxt.matches("^\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3})?)?)?)?)?)?")) {
                        return "";
                    } else {
                        String[] splits = resultingTxt.split("\\.");
                        for (String split : splits) {
                            if (Integer.valueOf(split) > 255) {
                                return "";
                            }
                        }
                    }
                }
                return null;
            }
        }
        });

        getEditText().addTextChangedListener(new TextWatcher(){
            boolean deleting = false;
            int lastCount = 0;

            @Override
            public void afterTextChanged(Editable s) {
                if (!deleting) {
                    String working = s.toString();
                    String[] split = working.split("\\.");
                    String string = split[split.length - 1];
                    if (string.length() == 3 || string.equalsIgnoreCase("0")
                            || (string.length() == 2 && Character.getNumericValue(string.charAt(0)) > 1)) {
                        s.append('.');
                        return;
                    }
                }
                nAddress = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                deleting = lastCount >= count;
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
            if (!nAddress.matches("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")) {
                CharSequence text = "Error: Invalid IP Address: " + nAddress;
                Toast toast = Toast.makeText(getContext(), text, Toast.LENGTH_SHORT);
                toast.show();
                positiveResult = false;
            }
        }
        super.onDialogClosed(positiveResult);
    }
}
