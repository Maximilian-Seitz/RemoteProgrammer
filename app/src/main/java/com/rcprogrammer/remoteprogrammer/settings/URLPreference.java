package com.rcprogrammer.remoteprogrammer.settings;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.util.Patterns;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.Toast;


public class URLPreference extends EditTextPreference {

    @TargetApi(21)
    public URLPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public URLPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public URLPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public URLPreference(Context context) {
        super(context);
    }


    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        final AlertDialog dialog = (AlertDialog) getDialog();
        Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = getEditText().getText().toString();
                if (!URLUtil.isValidUrl(text)) {
                    if(Patterns.IP_ADDRESS.matcher(text).matches()){
                        getEditText().setText("http://" + text);
                    } else {
                        Toast.makeText(getContext(), "NOT VALID", Toast.LENGTH_SHORT).show();

                        return;
                    }
                }

                URLPreference.super.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                dialog.dismiss();
            }
        });
    }

}
