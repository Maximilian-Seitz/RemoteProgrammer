package com.rcprogrammer.remoteprogrammer;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.rcprogrammer.remoteprogrammer.chat.ChatRecyclerViewAdapter;
import com.rcprogrammer.remoteprogrammer.codeeditor.codeview.CodeFormat;
import com.rcprogrammer.remoteprogrammer.connection.DeviceConnector;
import com.rcprogrammer.remoteprogrammer.functionlist.FunctionListActivity;
import com.rcprogrammer.remoteprogrammer.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class MainActivity extends AppCompatActivity {

    private DrawerLayout sideMenu;
    private ActionBarDrawerToggle menuButton;

    private final int speechRecognitionRequestId = 1337;

    private boolean connectedToRCDevice = false;

    private ChatRecyclerViewAdapter chatContent;

    private List<String> availableFunctions = new ArrayList<>();

    private ArrayAdapter<String> commandListAdapter;

    private DeviceConnector connector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sideMenu = findViewById(R.id.drawerLayout);
        menuButton = new ActionBarDrawerToggle(this, sideMenu, R.string.open, R.string.close);

        sideMenu.addDrawerListener(menuButton);
        menuButton.syncState();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        findViewById(R.id.progBarConnectToRCDevice).setVisibility(View.GONE);



        ImageButton btnSendCommand = findViewById(R.id.btnSendCommand);

        btnSendCommand.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MultiAutoCompleteTextView commandText = findViewById(R.id.txtCommand);

                if(connectedToRCDevice) {
                    if (!commandText.getText().toString().equals("")) {
                        String command = commandText.getText().toString();
                        connector.sendCommand(getSpeechRecognitionLanguageKey(), command);

                        commandText.setText("");

                        chatContent.addOutgoingMessage(command);
                    }
                } else {
                    connectToRCDevice();
                }
            }
        });




        MultiAutoCompleteTextView commandTxt = findViewById(R.id.txtCommand);

        commandListAdapter = new ArrayAdapter<>(this, android.R.layout.select_dialog_item, availableFunctions);

        commandTxt.setAdapter(commandListAdapter);
        commandTxt.setThreshold(0);

        commandTxt.setTokenizer(new MultiAutoCompleteTextView.Tokenizer() {
            @Override
            public int findTokenStart(CharSequence charSequence, int i) {
                return Math.max(0, charSequence.subSequence(0, i).toString().lastIndexOf(" ") + 1);
            }

            @Override
            public int findTokenEnd(CharSequence charSequence, int i) {
                return Math.max(0, i + charSequence.toString().substring(i).indexOf(" "));
            }

            @Override
            public CharSequence terminateToken(CharSequence charSequence) {
                return charSequence + " ";
            }
        });





        ImageButton btnSpeechInput = findViewById(R.id.btnSpeechInput);

        btnSpeechInput.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(connectedToRCDevice){
                    promptSpeechInput();
                } else {
                    connectToRCDevice();
                }
            }
        });





        View.OnTouchListener buttonColorListener = new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        ImageButton view = (ImageButton ) v;
                        view.getDrawable().setColorFilter(0xFF000000, PorterDuff.Mode.SRC_ATOP);
                        view.getBackground().setColorFilter(0x77000000, PorterDuff.Mode.SRC_ATOP);
                        v.invalidate();
                        break;
                    }
                    case MotionEvent.ACTION_UP:

                    case MotionEvent.ACTION_CANCEL: {
                        ImageButton view = (ImageButton) v;
                        view.getDrawable().clearColorFilter();
                        view.getBackground().clearColorFilter();
                        view.invalidate();
                        break;
                    }
                }
                return false;
            }
        };

        btnSpeechInput.setOnTouchListener(buttonColorListener);
        btnSendCommand.setOnTouchListener(buttonColorListener);




        RecyclerView chatView = findViewById(R.id.recyclerViewChat);

        chatContent = new ChatRecyclerViewAdapter(this);
        chatView.setAdapter(chatContent);




        NavigationView nv = findViewById(R.id.navigationView);

        nv.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case(R.id.nav_functions):
                        Intent functionIntent = new Intent(MainActivity.this, FunctionListActivity.class);
                        MainActivity.this.startActivity(functionIntent);
                        break;
                    case(R.id.nav_help):
                        showHelp();
                        break;
                    case(R.id.nav_about):
                        break;
                    case(R.id.nav_settings):
                        Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                        MainActivity.this.startActivity(settingsIntent);
                        break;
                }

                sideMenu.closeDrawers();
                return true;
            }
        });



        connector = new DeviceConnector(this, "", 3000, new DeviceConnector.ConnectionListener() {
            @Override
            public void onConnectionResult(boolean succeeded, Set<String> functionSet) {
                if(succeeded){
                    availableFunctions.clear();
                    availableFunctions.addAll(functionSet);

                    commandListAdapter.notifyDataSetChanged();
                }

                findViewById(R.id.progBarConnectToRCDevice).setVisibility(View.GONE);
                setConnectedToRCDevice(succeeded);
            }
        });

        connector.setCommandResponseListener(new DeviceConnector.CommandResponseListener() {
            @Override
            public void onResponse(String response) {
                if(chatContent != null){
                    chatContent.addIncomingMessage(response);
                }
            }
        });
    }



    @Override
    public void onResume(){
        super.onResume();

        connectToRCDevice();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.bar_main_menu,menu);

        // If a connection to the RC Device is already established, before the action-bar was created,
        // the connection button could not be hidden before this point, so it must be done here.
        if(connectedToRCDevice){
            if(findViewById(R.id.action_connect) != null){
                findViewById(R.id.action_connect).setVisibility(View.GONE);
            }

            if(findViewById(R.id.action_rc_device_data) != null){
                findViewById(R.id.action_rc_device_data).setVisibility(View.VISIBLE);
            }
        } else {
            if(findViewById(R.id.action_rc_device_data) != null){
                findViewById(R.id.action_rc_device_data).setVisibility(View.GONE);
            }
        }

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case R.id.action_connect:
                connectToRCDevice();
                return true;
            case R.id.action_rc_device_data:
                showRCDeviceFileManager();
                return true;
            case R.id.action_info:
                showHelp();
                return true;
        }

        if(menuButton.onOptionsItemSelected(item)){
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void showHelp(){
        Intent myIntent = new Intent(MainActivity.this, HelpActivity.class);

        MainActivity.this.startActivity(myIntent);
    }


    @Override
    public void onActivityResult(int request_code, int result_code, Intent i) {
        super.onActivityResult(request_code, result_code, i);

        switch (request_code) {
            case speechRecognitionRequestId:
                if (result_code == RESULT_OK && i != null) {
                    String result = i.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);

                    if(result != null){
                        TextView commandText = findViewById(R.id.txtCommand);
                        commandText.setText(result);
                    }
                }
                break;
        }
    }


    private void promptSpeechInput() {
        if(getSpeechRecognitionLanguageKey() != null){
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            }

            i.putExtra(RecognizerIntent.EXTRA_PROMPT, R.string.prompt_say_command);

            try {
                startActivityForResult(i, speechRecognitionRequestId);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.err_speech_recognition_not_supported, Toast.LENGTH_LONG).show();
            }
        }
    }


    private String getSpeechRecognitionLanguageKey(){
        String defaultLang = Locale.getDefault().getLanguage();

        for(String langKey : CodeFormat.getLanguages()){
            String lang = new Locale(langKey).getLanguage();

            if(lang.equals(defaultLang)){
                return langKey;
            }
        }

        return null;
    }


    private void setConnectedToRCDevice(boolean isConnected){
        if(isConnected){
            if(!connectedToRCDevice){
                connectedToRCDevice = true;
                Toast.makeText(this, R.string.msg_successfully_connected_to_rc_device, Toast.LENGTH_SHORT).show();
            }

            if(findViewById(R.id.action_connect) != null){
                findViewById(R.id.action_connect).setVisibility(View.GONE);
            }

            if(findViewById(R.id.action_rc_device_data) != null){
                findViewById(R.id.action_rc_device_data).setVisibility(View.VISIBLE);
            }
        } else {
            connectedToRCDevice = false;
            Toast.makeText(this, R.string.err_not_connected_to_rc_device, Toast.LENGTH_LONG).show();

            if(findViewById(R.id.action_connect) != null){
                findViewById(R.id.action_connect).setVisibility(View.VISIBLE);
            }

            if(findViewById(R.id.action_rc_device_data) != null){
                findViewById(R.id.action_rc_device_data).setVisibility(View.GONE);
            }
        }
    }


    private void showRCDeviceFileManager(){
        AlertDialog.Builder fileManagerDialog = new AlertDialog.Builder(MainActivity.this);
        fileManagerDialog.setTitle("RC Device functions:");

        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, availableFunctions);

        fileManagerDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        fileManagerDialog.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int index) {
                final String functionName = arrayAdapter.getItem(index);

                // Strings to Show In Dialog with Radio Buttons
                final CharSequence[] items = {"Run","Copy","Delete"};

                final AlertDialog optionDialog;

                // Creating and Building the Dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(functionName);

                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int selection = ((AlertDialog) dialog).getListView().getCheckedItemPosition();

                        switch(selection)
                        {
                            case 0:
                                System.out.println("Run " + functionName);
                                break;
                            case 1:
                                System.out.println("Copy " + functionName);
                                break;
                            case 2:
                                System.out.println("Delete " + functionName);
                                break;
                        }

                        dialog.dismiss();
                    }
                });

                builder.setSingleChoiceItems(items, -1, null);

                optionDialog = builder.create();
                optionDialog.show();
            }
        });

        fileManagerDialog.show();
    }


    private void connectToRCDevice(){

        //Make sure the app prefers the WIFI connection
        //TODO Make sure this only happens when it's supposed to, and is undone afterwards
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network[] networks = connectivity.getAllNetworks();

            for (Network network : networks) {
                NetworkInfo networkInfo = connectivity.getNetworkInfo(network);
                if(networkInfo.getType() == ConnectivityManager.TYPE_WIFI){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        connectivity.bindProcessToNetwork(network);
                    } else {
                        connectivity.setProcessDefaultNetwork(network);
                    }
                }
            }
        }


        findViewById(R.id.progBarConnectToRCDevice).setVisibility(View.VISIBLE);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String baseURLString = prefs.getString("rc_device_address", "http://192.168.0.1:5000");
        int connectionTimeout;

        try{
            connectionTimeout = Integer.parseInt(prefs.getString("connection_timeout", "3000"));
        } catch(Exception e){
            connectionTimeout = 3000;
        }

        connector.setBaseURL(baseURLString);
        connector.setConnectionTimeout(connectionTimeout);

        if(!connector.isConnecting()){
            connector.connect();
        }
    }

}
