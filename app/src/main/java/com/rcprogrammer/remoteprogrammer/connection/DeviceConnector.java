package com.rcprogrammer.remoteprogrammer.connection;

import android.content.Context;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.rcprogrammer.remoteprogrammer.codeeditor.codeview.CodeFormat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class DeviceConnector {

    private Context context;

    private RequestQueue queue;

    private ConnectionListener connectionListener;
    private CommandResponseListener commandResponseListener;

    private RetryPolicy retryPolicy;

    private boolean isConnecting = false;

    private String baseURL;
    private String currBaseURL;

    private JSONArray formatJSONArray = null;
    private JSONObject langJSONObject = null;
    private JSONArray categoryJSONArray = null;

    private boolean formatUpToDate = false;
    private boolean langUpToDate = false;
    private boolean categoryUpToDate = false;

    private Map<String, Long> functionSyncDates;


    public DeviceConnector(Context context, String baseURL, int connectionTimeout, ConnectionListener connectionListener){
        this.context = context;
        this.connectionListener = connectionListener;
        this.baseURL = baseURL;
        this.retryPolicy = new DefaultRetryPolicy(
                connectionTimeout,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        );

        queue = Volley.newRequestQueue(context);
    }


    public void connect(){
        loadData();

        formatJSONArray = null;
        langJSONObject = null;
        categoryJSONArray = null;

        formatUpToDate = false;
        langUpToDate = false;
        categoryUpToDate = false;

        functionSyncDates = null;

        currBaseURL = baseURL;

        isConnecting = true;

        initConnection();
    }

    private void loadData(){
        try {
            CodeFormat.loadFormats(context);
            CodeFormat.loadTranslations(context);
            CodeFormat.loadCategories(context);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void saveData(){
        try {
            CodeFormat.saveFormats(context);
            CodeFormat.saveTranslations(context);
            CodeFormat.saveCategories(context);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void exchangeData(){
        if(!formatUpToDate && formatJSONArray == null){
            requestCodeFormats();
            return;
        }

        if(!langUpToDate && langJSONObject == null){
            requestCodeLanguages();
            return;
        }

        if(!categoryUpToDate && categoryJSONArray == null){
            requestCodeCategories();
            return;
        }

        sendFunctions();

        finish();
    }
    
    private void initConnection(){
        String subURL = currBaseURL + "/info";

        // Request a JSON response from the provided URL.
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest (Request.Method.GET, subURL, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            long formatChangeTime = response.getLong("format");
                            long categoryChangeTime = response.getLong("categories");
                            long langChangeTime = response.getLong("lang");

                            if(response.has("functions")) {
                                functionSyncDates = new HashMap<>();
                                JSONObject functionSyncDatesJSON = response.getJSONObject("functions");

                                Iterator<String> keyList = functionSyncDatesJSON.keys();
                                while (keyList.hasNext()) {
                                    String key = keyList.next();

                                    functionSyncDates.put(key, functionSyncDatesJSON.getLong(key));
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }


                        exchangeData();
                    }
                },

                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        finish(false);
                    }
                }
        );

        jsonObjectRequest.setRetryPolicy(retryPolicy);

        // Add the request to the RequestQueue.
        queue.add(jsonObjectRequest);
    }

    private void requestCodeFormats(){
        String subURL = currBaseURL + "/code_syntax/format";

        // Request a JSON response from the provided URL.
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest (Request.Method.GET, subURL, null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        formatJSONArray = response;
                        exchangeData();
                    }
                },

                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        finish(false);
                    }
                }
        );

        jsonArrayRequest.setRetryPolicy(retryPolicy);

        // Add the request to the RequestQueue.
        queue.add(jsonArrayRequest);
    }

    private void requestCodeLanguages(){
        String subURL = currBaseURL + "/code_syntax/lang";

        // Request a JSON response from the provided URL.
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest (Request.Method.GET, subURL, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        langJSONObject = response;
                        exchangeData();
                    }
                },

                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        finish(false);
                    }
                }
        );

        jsonObjectRequest.setRetryPolicy(retryPolicy);

        // Add the request to the RequestQueue.
        queue.add(jsonObjectRequest);
    }

    private void requestCodeCategories(){
        String subURL = currBaseURL + "/code_syntax/categories";

        // Request a JSON response from the provided URL.
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest (Request.Method.GET, subURL, null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        categoryJSONArray = response;
                        exchangeData();
                    }
                },

                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        finish(false);
                    }
                }
        );

        jsonArrayRequest.setRetryPolicy(retryPolicy);

        // Add the request to the RequestQueue.
        queue.add(jsonArrayRequest);
    }

    private JSONArray loadFunctionFromFile(File file){
        if(!file.exists() || file.isDirectory()){
            return null;
        }

        try{
            FileInputStream inputStream = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            reader.close();
            inputStream.close();

            String fileContent = sb.toString();


            return new JSONArray(fileContent);
        } catch(Exception e){
            e.printStackTrace();
        }

        return null;
    }

    private void sendFunctions(){
        String subURL = currBaseURL + "/function";

        String functionDirPath = context.getFilesDir().getAbsolutePath() + File.separator + "functions";
        File functionsDir = new File(functionDirPath);
        if (!functionsDir.exists()){
            return;
        }

        for(File functionFile : functionsDir.listFiles()){
            boolean sendFunction = true;

            String functionName = functionFile.getName();
            long updateTime = System.currentTimeMillis()/1000;

            long functionLastModDate = functionFile.lastModified() / 1000;

            if(functionSyncDates != null && functionSyncDates.containsKey(functionName)){
                long functionSyncDate = functionSyncDates.get(functionName);

                if(functionSyncDate > functionLastModDate + 10){ //10 seconds margin (in case anything messes up)
                    sendFunction = false;
                }
            }

            if(sendFunction){
                JSONArray function = loadFunctionFromFile(functionFile);

                if(function != null){
                    JsonArrayRequest jsonArrayPostRequest = new JsonArrayRequest(Request.Method.POST, subURL + "/" + functionName + "/" + updateTime, function, null, null);
                    queue.add(jsonArrayPostRequest);
                }
            }
        }
    }

    /* language must be a short id of the language, like "en" or "de".*/
    public void sendCommand(String language, String command){
        String subURL = currBaseURL + "/command";

        JSONObject commandJSON = new JSONObject();

        try {
            commandJSON.put("lang", language);
            commandJSON.put("text", command);
            commandJSON.put("id", System.currentTimeMillis());
        } catch (JSONException e){
            e.printStackTrace();
        }

        JsonObjectRequest jsonObjectPostRequest = new JsonObjectRequest(Request.Method.POST, subURL, commandJSON,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        if(commandResponseListener != null){
                            if(response.has("text")){
                                try {
                                    commandResponseListener.onResponse(response.getString("text"));
                                } catch(JSONException e){
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                },
                null);
        queue.add(jsonObjectPostRequest);
    }

    private void finish(boolean succeeded){
        if(succeeded){
            saveData();
        } else {
            loadData();
        }


        Set<String> functionSet = new HashSet<>();

        if(functionSyncDates != null){
            functionSet = functionSyncDates.keySet();
        }

        connectionListener.onConnectionResult(succeeded, functionSet);


        isConnecting = false;
    }

    private void finish(){
        boolean succeeded = false;

        try {
            if(!formatUpToDate && formatJSONArray.length() > 0){
                CodeFormat.setAllFormatsDeprecated();

                CodeFormat.fromJSONArray(formatJSONArray);
            }

            if(!langUpToDate) {
                CodeFormat.addTranslationsFromJSONObject(langJSONObject);
            }

            if(!categoryUpToDate){
                CodeFormat.addCategoriesFromJSONArray(categoryJSONArray);
            }

            succeeded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        finish(succeeded);
    }


    public void setBaseURL(String baseURL){
        this.baseURL = baseURL;
    }

    public void setConnectionTimeout(int connectionTimeout){
        retryPolicy = new DefaultRetryPolicy(
                connectionTimeout,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        );
    }


    public void setConnectionListener(ConnectionListener connectionListener){
        this.connectionListener = connectionListener;
    }

    public void setCommandResponseListener(CommandResponseListener commandResponseListener){
        this.commandResponseListener = commandResponseListener;
    }


    public boolean isConnecting(){
        return isConnecting;
    }


    public interface ConnectionListener{
        void onConnectionResult(boolean succeeded, Set<String> availableFunctions);
    }

    public interface CommandResponseListener{
        void onResponse(String response);
    }
}
