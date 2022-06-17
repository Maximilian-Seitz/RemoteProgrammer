package com.rcprogrammer.remoteprogrammer.functionlist;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.rcprogrammer.remoteprogrammer.R;
import com.rcprogrammer.remoteprogrammer.codeeditor.EditorActivity;

import java.io.File;

public class FunctionListActivity extends AppCompatActivity {

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;

    private FunctionListFileManager functionData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_function_list);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerViewFunctionList);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));



        String functionDirPath = getApplicationContext().getFilesDir().getAbsolutePath() + File.separator + "functions";
        File functionsDir = new File(functionDirPath);
        if (!functionsDir.exists()){
            functionsDir.mkdirs();
        }

        functionData = new FunctionListFileManager(functionsDir);


        mAdapter = new FunctionListRecyclerViewAdapter(functionData, new ClickListener() {
            @Override
            public void onPositionClicked(int position) {
                Intent myIntent = new Intent(FunctionListActivity.this, EditorActivity.class);

                myIntent.putExtra("functionName", functionData.getNthFunctionName(position));
                FunctionListActivity.this.startActivity(myIntent);
            }
        });

        mRecyclerView.setAdapter(mAdapter);



        FloatingActionButton btnAddFunction = (FloatingActionButton) findViewById(R.id.btnAddFunction);

        btnAddFunction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(FunctionListActivity.this);
                builder.setTitle(R.string.dialog_title_function_name);

                final EditText input = new EditText(FunctionListActivity.this);
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                builder.setView(input);

                // Set up the buttons
                builder.setPositiveButton(R.string.btn_OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        functionData.addFunction(input.getText().toString());
                        mAdapter.notifyDataSetChanged();
                    }
                });
                builder.setNegativeButton(R.string.btn_Cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();
            }
        });

    }


    @Override
    public void onResume(){
        super.onResume();

        mAdapter.notifyDataSetChanged();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    public interface ClickListener {
        void onPositionClicked(int position);
    }
}
