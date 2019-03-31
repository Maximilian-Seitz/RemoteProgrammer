package com.rcprogrammer.remoteprogrammer.codeeditor;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rcprogrammer.remoteprogrammer.R;
import com.rcprogrammer.remoteprogrammer.codeeditor.codeview.CodeFormat;

public class InfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        String formatID = getIntent().getExtras().getString("formatID");

        CodeFormat format = CodeFormat.get(formatID);

        setTitle(format.getName());


        TextView txtTitle = findViewById(R.id.txtCodeBlockName);
        txtTitle.setText(format.getName());


        String description = format.getDescription();

        if(description != null && !description.equals("")){
            TextView txtDescription = findViewById(R.id.txtCodeBlockDescription);
            txtDescription.setText(Html.fromHtml(description));
        } else {
            findViewById(R.id.groupCodeBlockDescription).setVisibility(View.GONE);
        }


        String returnValueDescription = format.getReturnValueDescription();

        if(returnValueDescription != null && !returnValueDescription.equals("")){
            TextView txtReturnValueDescription = findViewById(R.id.txtCodeBlockReturnValue);
            txtReturnValueDescription.setText(Html.fromHtml(returnValueDescription));
        } else {
            findViewById(R.id.groupCodeBlockReturnValue).setVisibility(View.GONE);
        }


        String notes = format.getNotes();

        if(notes != null && !notes.equals("")){
            TextView txtNotes = findViewById(R.id.txtCodeBlockNotes);
            txtNotes.setText(Html.fromHtml(notes));
        } else {
            findViewById(R.id.groupCodeBlockNotes).setVisibility(View.GONE);
        }


        CodeFormat.ParameterDescription[] parameterDescriptions = format.getParameterDescriptions();

        if(parameterDescriptions != null && parameterDescriptions.length > 0){
            LinearLayout parameterDescriptionTable = findViewById(R.id.listCodeBlockParameters);

            findViewById(R.id.tableRowCodeBlockParameterExample).setVisibility(View.GONE);

            LinearLayout.LayoutParams tableRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            TextView txtTypeExample = findViewById(R.id.txtCodeBlockParameterTypeExample);
            ViewGroup.LayoutParams txtTypeParams = txtTypeExample.getLayoutParams();

            TextView txtDescExample = findViewById(R.id.txtCodeBlockParameterDescExample);
            ViewGroup.LayoutParams txtDescParams = txtDescExample.getLayoutParams();

            for(CodeFormat.ParameterDescription paramDesc : parameterDescriptions){
                LinearLayout parameterTableRow = new LinearLayout(this);
                parameterTableRow.setLayoutParams(tableRowParams);
                parameterTableRow.setOrientation(LinearLayout.HORIZONTAL);
                parameterDescriptionTable.addView(parameterTableRow);

                TextView txtType = new TextView(this);
                txtType.setLayoutParams(txtTypeParams);
                txtType.setText(Html.fromHtml(paramDesc.getType()));
                txtType.setBackgroundColor(Color.WHITE);
                txtType.setGravity(Gravity.CENTER);
                txtType.setPadding(4, 4, 4, 4);
                parameterTableRow.addView(txtType);

                TextView txtDesc = new TextView(this);
                txtDesc.setLayoutParams(txtDescParams);
                txtDesc.setText(Html.fromHtml(paramDesc.getDescription()));
                txtDesc.setBackgroundColor(Color.WHITE);
                txtDesc.setPadding(4, 4, 4, 4);
                parameterTableRow.addView(txtDesc);
            }
        } else {
            findViewById(R.id.groupCodeBlockParameters).setVisibility(View.GONE);
        }
    }
}
