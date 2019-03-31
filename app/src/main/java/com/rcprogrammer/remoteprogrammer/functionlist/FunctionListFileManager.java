package com.rcprogrammer.remoteprogrammer.functionlist;

import java.io.File;
import java.io.IOException;

public class FunctionListFileManager {

    private File fileFolder;

    FunctionListFileManager(File fileFolder){
        this.fileFolder = fileFolder;
    }


    boolean addFunction(String name){
        File file = new File(fileFolder, name);

        if(!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                return false;
            }
        } else {
            return false;
        }

        return true;
    }

    String getNthFunctionName(int functionNum){
        return fileFolder.listFiles()[functionNum].getName();
    }

    boolean removeNthFunction(int functionNum){
        return fileFolder.listFiles()[functionNum].delete();
    }

    int getNumOfFunctions() {
        return fileFolder.listFiles().length;
    }

}
