package com.rcprogrammer.remoteprogrammer.codeeditor.codeview;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class CodeFormat {
    private static JSONObject translations;
    private static String language;

    private static List<String> categories;
    private static List<Integer> categoryColorHues;

    private static Map<String, CodeFormat> formats;

    private final String id;
    private final String name;
    private final int category;
    private final ValueType returnsType;
    private final ValueType takesType;
    private final Element[][] lines;

    private final String description;
    private final String returnValueDescription;
    private final String notes;

    private final ParameterDescription[] parameterDescriptions;

    private boolean isDeprecated = false;


    @Nullable
    public static CodeFormat get(String id) {
        if(formats != null && formats.containsKey(id)){
            return formats.get(id);
        }

        return null;
    }

    private static void add(String id, boolean isDeprecated, String name, int category, ValueType returnsType, ValueType takesType, Element[][] lines, String description, String returnValueDescription, String notes, ParameterDescription[] parameterDescriptions) {
        if(formats == null){
            formats = new ArrayMap<>();
        }

        CodeFormat newFormat = new CodeFormat(id, isDeprecated, name, category, returnsType, takesType, lines, description, returnValueDescription, notes, parameterDescriptions);

        formats.put(id, newFormat);
    }


    public static void saveFormats(Context context) throws IOException, JSONException{
        File file = new File(context.getFilesDir() + File.separator + "codeFormats.json");
        if(file.isDirectory()){
            file.delete();
        }

        if(!file.exists()){
            file.createNewFile();
        }


        if(formats != null){
            JSONArray jsonArray = new JSONArray();

            for(Map.Entry<String, CodeFormat> entry : formats.entrySet()){
                jsonArray.put(entry.getValue().toJSONObject());
            }

            FileWriter writer = new FileWriter(file);
            writer.append(jsonArray.toString());
            writer.flush();
            writer.close();
        }
    }

    public static void loadFormats(Context context) throws IOException, JSONException{
        //Read file to string
        File file = new File(context.getFilesDir() + File.separator + "codeFormats.json");
        if(!file.exists() || file.isDirectory()){
            return;
        }

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

        fromJSONArray(jsonArray);
    }


    public static void saveTranslations(Context context) throws IOException{
        File file = new File(context.getFilesDir() + File.separator + "codeFormatTranslations.json");
        if(file.isDirectory()){
            file.delete();
        }

        if(!file.exists()){
            file.createNewFile();
        }

        if(translations != null){
            FileWriter writer = new FileWriter(file);
            writer.append(translations.toString());
            writer.flush();
            writer.close();
        }
    }

    public static void loadTranslations(Context context) throws IOException, JSONException{
        File file = new File(context.getFilesDir() + File.separator + "codeFormatTranslations.json");
        if(!file.exists() || file.isDirectory()){
            return;
        }

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


        translations = new JSONObject(fileContent);
    }

    public static void addTranslationsFromJSONObject(JSONObject jsonObject) throws JSONException{
        Iterator<String> langIterator = jsonObject.keys();

        if(translations != null){
            while (langIterator.hasNext()) {
                String languageName = langIterator.next();

                if(translations.has(languageName)){
                    JSONObject newTranslation = jsonObject.getJSONObject(languageName);
                    JSONObject oldTranslation = translations.getJSONObject(languageName);

                    Iterator<String> wordIterator = newTranslation.keys();

                    while (wordIterator.hasNext()) {
                        String wordID = wordIterator.next();
                        oldTranslation.put(wordID, newTranslation.get(wordID));
                    }
                } else {
                    translations.put(languageName, jsonObject.getJSONObject(languageName));
                }
            }
        } else {
            translations = jsonObject;
        }
    }

    public static List<String> getLanguages(){
        ArrayList<String> languages = new ArrayList<String>();

        if(translations != null){
            Iterator<String> iter = translations.keys();
            while (iter.hasNext()) {
                String key = iter.next();

                if(!key.equalsIgnoreCase("default")){
                    languages.add(key);
                }
            }
        }

        return languages;
    }

    public static void setLanguage(String newLanguage){
        language = newLanguage;
    }


    public static void saveCategories(Context context) throws IOException, JSONException{
        File file = new File(context.getFilesDir() + File.separator + "codeFormatCategories.json");
        if(file.isDirectory()){
            file.delete();
        }

        if(!file.exists()){
            file.createNewFile();
        }

        JSONArray catArr = new JSONArray();

        for(int i = 0; i < categories.size(); i++){
            JSONObject catObj = new JSONObject();

            catObj.put("name", categories.get(i));
            catObj.put("hue", categoryColorHues.get(i));

            catArr.put(catObj);
        }


        FileWriter writer = new FileWriter(file);
        writer.append(catArr.toString());
        writer.flush();
        writer.close();
    }

    public static void loadCategories(Context context) throws IOException, JSONException{
        File file = new File(context.getFilesDir() + File.separator + "codeFormatCategories.json");
        if(!file.exists() || file.isDirectory()){
            return;
        }

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


        addCategoriesFromJSONArray(new JSONArray(fileContent));
    }

    public static void addCategoriesFromJSONArray(JSONArray categoryArray) throws JSONException{
        if(categories == null){
            categories = new ArrayList<>();
        }

        if(categoryColorHues == null){
            categoryColorHues = new ArrayList<>();
        }

        for(int i = 0; i < categoryArray.length(); i++){
            JSONObject category = categoryArray.getJSONObject(i);

            String name = category.getString("name");

            if(categories.size() <= i){
                categories.add(name);
            } else {
                categories.set(i, name);
            }

            int hue = category.getInt("hue");

            if(categoryColorHues.size() <= i){
                categoryColorHues.add(hue);
            } else {
                categoryColorHues.set(i, hue);
            }
        }
    }

    public static int getNumOfCategories() {
        if(categories == null){
            return 0;
        }

        return categories.size();
    }

    public static String getCategoryName(int category) {
        if(categories != null){
            return localizeString(categories.get(category));
        }

        return String.valueOf(category);
    }

    public static int getCategoryColorHue(int category) {
        if(categoryColorHues != null){
            return categoryColorHues.get(category);
        }

        return 0;
    }


    public static void fromJSONObject(JSONObject jsonObject) throws JSONException {
        String id = jsonObject.getString("id");

        String name = id;
        if (jsonObject.has("name")) {
            name = jsonObject.getString("name");
        }

        boolean isDeprecated = false;
        if (jsonObject.has("isDeprecated")) {
            isDeprecated = jsonObject.getBoolean("isDeprecated");
        }

        int category = jsonObject.getInt("category");

        ValueType takesType = ValueType.NONE;
        ValueType returnsType = ValueType.NONE;

        if(jsonObject.has("takes")){
            try{
                takesType = ValueType.valueOf(jsonObject.getString("takes").toUpperCase());
            } catch (IllegalArgumentException e) {
                takesType = ValueType.NONE;
            }
        }

        if(jsonObject.has("returns")){
            try{
                returnsType = ValueType.valueOf(jsonObject.getString("returns").toUpperCase());
            } catch (IllegalArgumentException e) {
                returnsType = ValueType.NONE;
            }
        }

        JSONArray linesJSONArray = jsonObject.getJSONArray("lines");
        Element[][] lines = new Element[linesJSONArray.length()][];

        for (int i = 0; i < linesJSONArray.length(); i++) {
            JSONArray lineJSONArray = linesJSONArray.getJSONArray(i);

            lines[i] = new Element[lineJSONArray.length()];

            for (int j = 0; j < lineJSONArray.length(); j++) {
                JSONObject lineElement = lineJSONArray.getJSONObject(j);

                String elementType = lineElement.getString("type");

                if (elementType.equalsIgnoreCase("text")){
                    String text = "";

                    if (lineElement.has("value")) {
                        text = lineElement.getString("value");
                    }

                    lines[i][j] = new TextParameter(text);
                } else if (elementType.equalsIgnoreCase("code")){
                    IOType type = IOType.NONE;

                    if(lineElement.has("value")){
                        String typeStr = lineElement.getString("value");

                        try{
                            type = IOType.valueOf(typeStr.toUpperCase());
                        } catch(IllegalArgumentException e){
                            type = IOType.NONE;
                        }
                    }

                    lines[i][j] = new ChildParameter(type);
                } else {
                    throw new JSONException("Invalid format json.");
                }
            }
        }

        String desc = "";
        if (jsonObject.has("desc")) {
            desc = jsonObject.getString("desc");
        }

        String descReturnValue = "";
        if (jsonObject.has("descReturnValue")) {
            descReturnValue = jsonObject.getString("descReturnValue");
        }

        String descNotes = "";
        if (jsonObject.has("descNotes")) {
            descNotes = jsonObject.getString("descNotes");
        }

        ParameterDescription[] parameterDescriptions = null;
        if (jsonObject.has("descParams")) {
            JSONArray paramDescJsonArray = jsonObject.getJSONArray("descParams");

            parameterDescriptions = new ParameterDescription[paramDescJsonArray.length()];

            for(int i = 0; i < paramDescJsonArray.length(); i++){
                JSONObject paramDesc = paramDescJsonArray.getJSONObject(i);

                if(paramDesc.has("type") && paramDesc.has("desc")){
                    parameterDescriptions[i] = new ParameterDescription(paramDesc.getString("type"), paramDesc.getString("desc"));
                } else {
                    parameterDescriptions = null;
                    break;
                }
            }
        }

        CodeFormat.add(id, isDeprecated, name, category, returnsType, takesType, lines, desc, descReturnValue, descNotes, parameterDescriptions);
    }

    public static void fromJSONArray(JSONArray jsonArray) throws JSONException {
        for(int i = 0; i < jsonArray.length(); i++){
            CodeFormat.fromJSONObject(jsonArray.getJSONObject(i));
        }
    }


    public static void setAllFormatsDeprecated(){
        if(formats != null){
            for(Map.Entry<String, CodeFormat> entry : formats.entrySet()){
                entry.getValue().isDeprecated = true;
            }
        }
    }


    public static List<CodeFormat> getAllFormats(boolean onlyNonDeprecated){
        List<CodeFormat> validFormats = new ArrayList<>();

        if(formats != null){
            for(Map.Entry<String, CodeFormat> entry : formats.entrySet()){
                if(!onlyNonDeprecated || !entry.getValue().isDeprecated){
                    validFormats.add(entry.getValue());
                }
            }
        }

        return validFormats;
    }


    private static String localizeString(String str){
        if(str != null && str.startsWith("@")){
            String newStr = str.substring(1);

            try{
                JSONObject languageJSON = null;

                if(translations.has(language)) {
                    languageJSON = translations.getJSONObject(language);
                }

                if(languageJSON != null && languageJSON.has(newStr)){
                    return languageJSON.getString(newStr);
                } else {
                    languageJSON = translations.getJSONObject("default");

                    if(languageJSON != null && languageJSON.has(newStr)){
                        return languageJSON.getString(newStr);
                    } else {
                        return newStr;
                    }
                }

            } catch(Exception e){
                return newStr;
            }
        } else {
            return str;
        }
    }



    private CodeFormat(String id, boolean isDeprecated, String name, int category, ValueType returnsType, ValueType takesType, Element[][] lines, String description, String returnValueDescription, String notes, ParameterDescription[] parameterDescriptions) {
        this.id = id;
        this.name = name;
        this.category = category;

        this.returnsType = returnsType;
        this.takesType = takesType;

        this.lines = lines;

        this.isDeprecated = isDeprecated;

        this.description = description;
        this.returnValueDescription = returnValueDescription;
        this.notes = notes;

        this.parameterDescriptions = parameterDescriptions;
    }


    public String getId(){
        return id;
    }

    public String getName() {
        return localizeString(name);
    }

    public int getCategory() {
        return category;
    }


    public boolean hasInfo(){
        return (parameterDescriptions != null && parameterDescriptions.length > 0) ||
                (description != null && !description.equals("")) ||
                (returnValueDescription != null && !returnValueDescription.equals("")) ||
                (notes != null && !notes.equals(""));
    }

    public String getDescription(){
        return localizeString(description);
    }

    public String getReturnValueDescription(){
        return localizeString(returnValueDescription);
    }

    public String getNotes(){
        return localizeString(notes);
    }

    public ParameterDescription[] getParameterDescriptions(){
        if(parameterDescriptions != null){
            ParameterDescription[] localizedDescriptions = new ParameterDescription[parameterDescriptions.length];

            for(int i = 0; i < parameterDescriptions.length; i++){
                String type = localizeString(parameterDescriptions[i].getType());
                String desc = localizeString(parameterDescriptions[i].getDescription());

                localizedDescriptions[i] = new ParameterDescription(type, desc);
            }

            return localizedDescriptions;
        } else {
            return null;
        }
    }


    public int getNumOfLines(){
        return lines.length;
    }

    public int getNumOfColumnsInLine(int lineNum){
        return lines[lineNum].length;
    }

    public boolean takesParameterAt(CodeFormat format, int lineNum, int columnNum) {
        return lines[lineNum][columnNum].takesChild(format);
    }

    public ValueType takesTypeAtPosition(int lineNum, int columnNum){
        return lines[lineNum][columnNum].takesType();
    }

    public String getTextAtPosition(int lineNum, int columnNum){
        return localizeString(lines[lineNum][columnNum].getValue());
    }

    public ElementType getElementType(int lineNum, int columnNum) {
        return lines[lineNum][columnNum].getType();
    }

    public boolean isElementInput(int lineNum, int columnNum) {
        return lines[lineNum][columnNum].isInput();
    }

    public boolean isElementOutput(int lineNum, int columnNum) {
        return lines[lineNum][columnNum].isOutput();
    }


    private JSONObject toJSONObject() throws JSONException{
        JSONObject newObject = new JSONObject();

        newObject.put("id", id);
        newObject.put("name", name);
        newObject.put("isDeprecated", isDeprecated);
        newObject.put("category", category);
        newObject.put("takes", takesType.toString());
        newObject.put("returns", returnsType.toString());
        newObject.put("desc", description);
        newObject.put("descReturnValue", returnValueDescription);
        newObject.put("descNotes", notes);


        JSONArray linesJSONArray = new JSONArray();

        for (Element[] line : lines) {
            JSONArray lineJSONArray = new JSONArray();

            for (Element element : line) {
                JSONObject lineElement = new JSONObject();

                lineElement.put("type", element.getType());

                if (element.getValue() != null) {
                    lineElement.put("value", element.getValue());
                }

                lineJSONArray.put(lineElement);
            }

            linesJSONArray.put(lineJSONArray);
        }

        newObject.put("lines", linesJSONArray);


        JSONArray descParamsJSONArray = new JSONArray();

        if(parameterDescriptions != null){
            for(ParameterDescription paramDesc : parameterDescriptions){
                JSONObject paramDescJSONObject = new JSONObject();

                paramDescJSONObject.put("type", paramDesc.getType());
                paramDescJSONObject.put("desc", paramDesc.getDescription());

                descParamsJSONArray.put(paramDescJSONObject);
            }
        }

        newObject.put("descParams", descParamsJSONArray);


        return newObject;
    }


    private interface Element {
        public ElementType getType();
        public String getValue();

        public boolean takesChild(CodeFormat format);
        public ValueType takesType();

        public boolean isInput();
        public boolean isOutput();
    }

    private static class TextParameter implements Element {
        private final String text;

        public TextParameter(String text) {
            this.text = text;
        }

        public String getText(){
            return text;
        }

        @Override
        public ElementType getType() {
            return ElementType.TEXT;
        }

        @Override
        public String getValue() {
            return text;
        }

        @Override
        public boolean takesChild(CodeFormat format) {
            return false;
        }

        @Override
        public ValueType takesType() {
            return ValueType.NONE;
        }

        @Override
        public boolean isInput() {
            return false;
        }

        @Override
        public boolean isOutput() {
            return false;
        }
    }

    private static class ChildParameter implements Element {
        private final IOType type;

        public ChildParameter(IOType type) {
            this.type = type;
        }

        @Override
        public ElementType getType() {
            return ElementType.CODE;
        }

        @Override
        public String getValue() {
            return type.toString();
        }

        @Override
        public boolean takesChild(CodeFormat format) {
            switch(type){
                case BOOL_IN:
                    return format.returnsType == ValueType.BOOL;
                case NUM_IN:
                    return format.returnsType == ValueType.NUM;
                case TEXT_IN:
                    return format.returnsType == ValueType.TEXT || format.returnsType == ValueType.NUM || format.returnsType == ValueType.BOOL;
                case BOOL_OUT:
                    return format.takesType == ValueType.BOOL || format.takesType == ValueType.TEXT;
                case NUM_OUT:
                    return format.takesType == ValueType.NUM || format.takesType == ValueType.TEXT;
                case TEXT_OUT:
                    return format.takesType == ValueType.TEXT;
                default:
                    return true;
            }
        }

        @Override
        public ValueType takesType() {
            switch(type){
                case NUM_IN:
                    return ValueType.NUM;
                case TEXT_IN:
                    return ValueType.TEXT;
                case BOOL_IN:
                    return ValueType.BOOL;
                default:
                    return ValueType.NONE;
            }
        }

        @Override
        public boolean isInput() {
            switch(type){
                case NUM_IN:
                    return true;
                case TEXT_IN:
                    return true;
                case BOOL_IN:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean isOutput() {
            switch(type){
                case NUM_OUT:
                    return true;
                case TEXT_OUT:
                    return true;
                case BOOL_OUT:
                    return true;
                default:
                    return false;
            }
        }
    }

    public enum ElementType {
        CODE,
        TEXT
    }


    public enum IOType {
        NUM_IN,
        NUM_OUT,
        TEXT_IN,
        TEXT_OUT,
        BOOL_IN,
        BOOL_OUT,
        NONE
    }

    public enum ValueType {
        TEXT,
        NUM,
        BOOL,
        NONE
    }


    public static class ParameterDescription{
        private final String type;
        private final String description;

        public ParameterDescription(String type, String description){
            this.type = type;
            this.description = description;
        }

        public String getType(){
            return type;
        }

        public String getDescription(){
            return description;
        }
    }
}
