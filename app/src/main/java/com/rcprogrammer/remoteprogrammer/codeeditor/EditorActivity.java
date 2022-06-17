package com.rcprogrammer.remoteprogrammer.codeeditor;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.rcprogrammer.remoteprogrammer.R;
import com.rcprogrammer.remoteprogrammer.codeeditor.codeview.CodeFormat;
import com.rcprogrammer.remoteprogrammer.codeeditor.codeview.CodeView;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

public class EditorActivity extends AppCompatActivity {
    private String functionName;

    private CodeView codeView;

    private JSONArray codeStateCurrent = null;

    private Stack<JSONArray> codeStateUndoHistory = new Stack<>();
    private Stack<JSONArray> codeStateRedoHistory = new Stack<>();

    private boolean isCurrentlySaved = true;

    private Menu appBarMenu = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        functionName = getIntent().getExtras().getString("functionName");
        setTitle(functionName);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        try{
            CodeFormat.loadFormats(this);
            CodeFormat.loadTranslations(this);
            CodeFormat.loadCategories(this);
        } catch (Exception e){
            e.printStackTrace();
        }


        boolean overrideLanguage = prefs.getBoolean("override_code_language", false);
        String language = Locale.getDefault().getLanguage();

        if(overrideLanguage){
            language = prefs.getString("code_language", language);
        }

        CodeFormat.setLanguage(language);



        codeView = (CodeView) findViewById(R.id.codeEditor);

        codeView.setOnChangeListener(new CodeView.OnChangeListener() {
            @Override
            public void onChange(View view, JSONArray codeState) {
                if(codeStateCurrent != null){
                    codeStateUndoHistory.push(codeStateCurrent);

                    if(appBarMenu != null){
                        setMenuItemEnabled(appBarMenu.findItem(R.id.action_undo), true);
                        setMenuItemEnabled(appBarMenu.findItem(R.id.action_redo), false);
                    }

                    codeStateRedoHistory.empty();
                }

                codeStateCurrent = codeState;

                isCurrentlySaved = false;

                if(appBarMenu != null){
                    setMenuItemEnabled(appBarMenu.findItem(R.id.action_save), true);
                }
            }
        });

        codeView.setCodeInfoListener(new CodeView.CodeInfoListener() {
            @Override
            public void onRequestInfo(View view, CodeFormat format) {
                Intent myIntent = new Intent(EditorActivity.this, InfoActivity.class);

                myIntent.putExtra("formatID", format.getId());

                EditorActivity.this.startActivity(myIntent);
            }
        });


        loadCode(functionName);

        try{
            codeStateCurrent = codeView.getCodeState();
        } catch(Exception e){
            e.printStackTrace();
        }





        SparseArray<String> categories = new SparseArray<>();
        SparseArray<List<String>> valueIDs = new SparseArray<>();
        SparseArray<List<String>> valueNames = new SparseArray<>();


        List<CodeFormat> validFormats = CodeFormat.getAllFormats(true);

        for(CodeFormat format : validFormats){
            int category = format.getCategory();

            List<String> valueIDList;
            List<String> valueNameList;

            if(valueIDs.get(category) == null){
                valueIDList = new ArrayList<>();
                valueNameList = new ArrayList<>();

                valueIDs.put(category, valueIDList);
                valueNames.put(category, valueNameList);
            } else {
                valueIDList = valueIDs.get(category);
                valueNameList = valueNames.get(category);
            }

            valueIDList.add(format.getId());
            valueNameList.add(format.getName());
        }


        int numOfCategories = CodeFormat.getNumOfCategories();

        for(int i = 0; i < numOfCategories; i++){
            if(valueIDs.get(i) != null && valueIDs.get(i).size() > 0){
                categories.put(i, CodeFormat.getCategoryName(i));
            }
        }


        final CodeCategoryListAdapter elementListAdapter = new CodeCategoryListAdapter(this, categories, valueNames, valueIDs);

        final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawerLayout);

        ExpandableListView elementList = (ExpandableListView) findViewById(R.id.elementList);
        elementList.setAdapter(elementListAdapter);
        elementList.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView expandableListView, View view, int categoryNum, int childNum, long l) {
                drawer.closeDrawer(Gravity.END);
                codeView.addCodeElement(elementListAdapter.getChildKey(categoryNum, childNum));
                return false;
            }
        });


        FloatingActionButton btnAddElement = (FloatingActionButton) findViewById(R.id.btnAddElement);
        boolean hideAddCodeBtn = prefs.getBoolean("hide_add_code_button", false);

        if(hideAddCodeBtn){
            btnAddElement.hide();
        } else {
            btnAddElement.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    drawer.openDrawer(Gravity.END);
                }
            });
        }
    }


    @Override
    public void onBackPressed() {
        if(isCurrentlySaved){
            finish();
        } else {
            showDiscardChangeDialog();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.bar_editor_menu,menu);

        setMenuItemEnabled(menu.findItem(R.id.action_redo), false);
        setMenuItemEnabled(menu.findItem(R.id.action_undo), false);
        setMenuItemEnabled(menu.findItem(R.id.action_save), false);

        appBarMenu = menu;

        return true;
    }

    private void setMenuItemEnabled(MenuItem item, boolean enabled){
        if(item != null){
            item.setEnabled(enabled);

            int alpha = 255;

            if(!enabled){
                alpha = 100;
            }

            item.getIcon().setAlpha(alpha);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                if(isCurrentlySaved){
                    finish();
                } else {
                    showDiscardChangeDialog();
                }
                return true;

            case R.id.action_undo:
                undo();
                break;

            case R.id.action_redo:
                redo();
                break;

            case R.id.action_save:
                isCurrentlySaved = true;

                if(appBarMenu != null){
                    setMenuItemEnabled(appBarMenu.findItem(R.id.action_save), false);
                }

                saveCode(functionName);
                break;

            case R.id.action_rename:
                copyFunction(true);
                break;

            case R.id.action_copy:
                copyFunction(false);
                break;
        }

        return super.onOptionsItemSelected(item);
    }


    private void showDiscardChangeDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(EditorActivity.this);
        builder.setTitle(R.string.dialog_title_save_changes_alert);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_Yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                saveCode(functionName);
                finish();
            }
        });

        builder.setNegativeButton(R.string.btn_No, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });

        builder.setNeutralButton(R.string.btn_Cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }


    private void undo(){
        try{
            if(!codeStateUndoHistory.isEmpty()){
                if(codeStateCurrent != null && codeStateCurrent.length() > 0){
                    codeStateRedoHistory.push(codeStateCurrent);

                    if(appBarMenu != null){
                        setMenuItemEnabled(appBarMenu.findItem(R.id.action_redo), true);
                    }
                }

                codeStateCurrent = codeStateUndoHistory.pop();

                if(codeStateUndoHistory.isEmpty()){
                    if(appBarMenu != null){
                        setMenuItemEnabled(appBarMenu.findItem(R.id.action_undo), false);
                    }
                }

                isCurrentlySaved = false;
                if(appBarMenu != null) {
                    setMenuItemEnabled(appBarMenu.findItem(R.id.action_save), true);
                }

                codeView.setCodeState(codeStateCurrent);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void redo(){
        try{
            if(!codeStateRedoHistory.isEmpty()){
                if(codeStateCurrent != null){
                    codeStateUndoHistory.push(codeStateCurrent);

                    if(appBarMenu != null){
                        setMenuItemEnabled(appBarMenu.findItem(R.id.action_undo), true);
                    }
                }

                codeStateCurrent = codeStateRedoHistory.pop();

                if(codeStateRedoHistory.isEmpty() && appBarMenu != null){
                    setMenuItemEnabled(appBarMenu.findItem(R.id.action_redo), false);
                }

                isCurrentlySaved = false;
                if(appBarMenu != null) {
                    setMenuItemEnabled(appBarMenu.findItem(R.id.action_save), true);
                }

                codeView.setCodeState(codeStateCurrent);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void saveCode(String functionName){
        File functionsDir = new File(getApplicationContext().getFilesDir().getAbsolutePath() + File.separator + "functions");
        if (!functionsDir.exists()){
            functionsDir.mkdirs();
        }

        File file = new File(functionsDir, functionName);

        if(file.isDirectory()){
            file.delete();
        }

        try{
            if(!file.exists()){
                file.createNewFile();
            }

            FileWriter writer = new FileWriter(file);

            if(codeStateCurrent != null){
                writer.append(codeStateCurrent.toString());
            } else {
                writer.append("");
            }

            writer.flush();
            writer.close();

            Toast.makeText(this, R.string.msg_save_successful, Toast.LENGTH_SHORT).show();
        } catch(Exception e){
            Toast.makeText(this, R.string.err_failed_to_save, Toast.LENGTH_SHORT).show();

            e.printStackTrace();
        }
    }

    private void loadCode(String functionName){
        //Read file to string
        File functionsDir = new File(getApplicationContext().getFilesDir().getAbsolutePath() + File.separator + "functions");
        if (!functionsDir.exists()){
            return;
        }

        File file = new File(functionsDir, functionName);
        if(!file.exists() || file.isDirectory()){
            return;
        }

        try{
            FileInputStream inputStream = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line = null;

            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            reader.close();
            inputStream.close();

            String fileContent = sb.toString();


            //Store file contents as codeformats
            JSONArray jsonArray = new JSONArray(fileContent);

            codeView.setCodeState(jsonArray);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private void copyFunction(final boolean deleteOriginal){
        AlertDialog.Builder builder = new AlertDialog.Builder(EditorActivity.this);
        builder.setTitle(R.string.dialog_title_rename);

        final EditText input = new EditText(EditorActivity.this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(functionName);
        input.selectAll();

        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_OK, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newName = input.getText().toString();

                saveCode(newName);

                if(deleteOriginal){
                    String originalName = functionName;
                    functionName = newName;
                    setTitle(functionName);

                    File functionsDir = new File(getApplicationContext().getFilesDir().getAbsolutePath() + File.separator + "functions");
                    if (!functionsDir.exists()){
                        return;
                    }

                    File file = new File(functionsDir, originalName);

                    file.delete();
                }
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
}
