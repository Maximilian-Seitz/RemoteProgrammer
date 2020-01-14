package com.rcprogrammer.remoteprogrammer.codeeditor.codeview;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class CodeElement implements CodeContainer{
    private CodeFormat format;

    private CodeContainer parent;

    private CodeView.CodeLayout layout;


    private float width = 0.f;
    private float height = 0.f;


    private SparseArray<SparseArray<CodeElement>> childElements;
    private SparseArray<SparseArray<String>> childValues;

    private SparseArray<List<CodeElement>> childBlocks;

    private float[] codeLineHeights;
    private float[] codeLineWidths;


    CodeElement(CodeFormat format, CodeContainer parent, CodeView.CodeLayout layout){
        init(format, parent, layout);
    }


    CodeElement(JSONObject jsonObject, CodeContainer parent, CodeView.CodeLayout layout) throws JSONException{
        init(CodeFormat.get(jsonObject.getString("id")), parent, layout);

        JSONArray params = null;
        JSONArray blocks = null;

        if(jsonObject.has("params")){
            params = jsonObject.getJSONArray("params");
        }

        if(jsonObject.has("blocks")){
            blocks = jsonObject.getJSONArray("blocks");
        }


        int paramNum = 0;
        int blockNum = 0;
        for(int lineNum = 0; lineNum < format.getNumOfLines(); lineNum++){
            if(format.getNumOfColumnsInLine(lineNum) > 0) {
                for (int columnNum = 0; columnNum < format.getNumOfColumnsInLine(lineNum); columnNum++) {
                    if (format.getElementType(lineNum, columnNum) == CodeFormat.ElementType.CODE) {
                        try {
                            JSONObject childJSON = params.getJSONObject(paramNum);

                            childElements.get(lineNum).put(columnNum, new CodeElement(childJSON, this, layout));
                        } catch (JSONException e) {
                            try {
                                String childValue = params.getString(paramNum);

                                childValues.get(lineNum).put(columnNum, childValue);
                            } catch (JSONException f) {}
                        }

                        paramNum++;
                    }
                }
            } else {
                if(blocks == null){
                    throw new JSONException("Error parsing JSON");
                }

                addCodeElementsToCodeBlockFromJSON(blocks.getJSONArray(blockNum), childBlocks.get(lineNum), this, layout);
                blockNum++;
            }
        }

        recalculateSize();
    }


    private void init(CodeFormat format, CodeContainer parent, CodeView.CodeLayout layout){
        this.format = format;
        this.parent = parent;
        this.layout = layout;

        if(format == null || layout == null){
            throw new IllegalArgumentException();
        }

        codeLineHeights = new float[format.getNumOfLines()];
        codeLineWidths = new float[format.getNumOfLines()];

        childElements = new SparseArray<>(format.getNumOfLines());
        childValues = new SparseArray<>(format.getNumOfLines());

        int numOfChildBlocks = calculateNumOfChildBlocks();
        childBlocks = new SparseArray<>(numOfChildBlocks);

        //Initiate all the sparse arrays with empty lists/sparse arrays.
        for(int i = 0; i < format.getNumOfLines(); i++){
            if(format.getNumOfColumnsInLine(i) > 0){
                childElements.put(i, new SparseArray<CodeElement>(format.getNumOfColumnsInLine(i)));
                childValues.put(i, new SparseArray<String>(format.getNumOfColumnsInLine(i)));
            } else {
                childBlocks.put(i, new ArrayList<CodeElement>());
            }
        }

        recalculateSize();
    }


    static void addCodeElementsToCodeBlockFromJSON(JSONArray jsonArray, List<CodeElement> codeBlock, CodeContainer parent, CodeView.CodeLayout layout) throws JSONException{
        if(jsonArray != null && codeBlock != null){
            for(int i = 0; i < jsonArray.length(); i++){
                JSONObject obj = jsonArray.getJSONObject(i);

                codeBlock.add(new CodeElement(obj, parent, layout));
            }
        }

        parent.recalculateSize();
    }

    static JSONArray getJSONArrayFromCodeBlock(List<CodeElement> codeBlock) throws JSONException{
        JSONArray jsonArray = new JSONArray();

        if(codeBlock != null){
            for(CodeElement element : codeBlock){
                jsonArray.put(element.toJSONObject());
            }
        }

        return jsonArray;
    }


    static void drawCodeBlock(Canvas canvas, List<CodeElement> codeBlock, float top, float left, float elementPaddingVertical){
        if(codeBlock == null || left > canvas.getWidth()){
            return;
        }

        float currentTop = top;

        for(CodeElement element : codeBlock){
            if(currentTop > canvas.getHeight()){
                break;
            }

            if(currentTop + element.getHeight() >= 0 && left + element.getWidth() >= 0){
                element.draw(canvas, currentTop, left);
            }

            currentTop += element.getHeight() + elementPaddingVertical;
        }
    }


    static CodeElement removeElementFromCodeBlockPosition(List<CodeElement> codeBlock, CodeView.CodeLayout codeBlockLayout, CodeContainer parent, float x, float y){
        if(codeBlock == null || codeBlock.size() == 0 || x < 0 || y < 0){
            return null;
        }

        float height = 0;

        for (int i = 0; i < codeBlock.size(); i++) {
            CodeElement element = codeBlock.get(i);
            height += element.getHeight();

            if(height >= y){
                if(element.getWidth() >= x){
                    CodeElement removedElement = element.removeElementFromPosition(x, y - (height-element.getHeight()));

                    if(removedElement == element){
                        codeBlock.remove(i);
                        parent.recalculateSize();
                    }

                    if(removedElement != null){
                        removedElement.parent = null;
                    }

                    return removedElement;
                } else {
                    return null;
                }
            }

            height += codeBlockLayout.getElementPaddingVertical();

            if(height >= y){
                return null;
            }
        }

        return null;
    }

    static boolean addElementToCodeBlockPosition(CodeElement addedElement, List<CodeElement> codeBlock, CodeView.CodeLayout codeBlockLayout, CodeContainer parent, float x, float y){
        if(codeBlock == null || addedElement == null){
            return false;
        }

        float height = 0;

        for (int i = 0; i < codeBlock.size(); i++) {
            CodeElement element = codeBlock.get(i);

            ElementEntryResult result = element.addElementAtPosition(addedElement, x, y - height);

            switch(result){
                case IS_ABOVE:
                    addedElement.parent = parent;
                    codeBlock.add(i, addedElement);
                    parent.recalculateSize();
                    return true;
                case WAS_ADDED:
                    return true;
                case IS_BELOW:
                    break;
                default:
                    return false;
            }


            height += element.getHeight() + codeBlockLayout.getElementPaddingVertical();
        }


        addedElement.parent = parent;
        codeBlock.add(addedElement);
        parent.recalculateSize();
        return true;
    }


    static CodeFormat.ValueType getInputTypeAtCodeBlockPosition(List<CodeElement> codeBlock, CodeView.CodeLayout codeBlockLayout, float x, float y) {
        if(codeBlock == null || codeBlock.size() == 0 || x < 0 || y < 0){
            return CodeFormat.ValueType.NONE;
        }

        float height = 0;

        for (int i = 0; i < codeBlock.size(); i++) {
            CodeElement element = codeBlock.get(i);
            height += element.getHeight();

            if(height >= y){
                if(element.getWidth() >= x){
                    return element.getInputTypeAtPosition(x, y - (height - element.getHeight()));
                } else {
                    return CodeFormat.ValueType.NONE;
                }
            }

            height += codeBlockLayout.getElementPaddingVertical();

            if(height >= y){
                return CodeFormat.ValueType.NONE;
            }
        }

        return CodeFormat.ValueType.NONE;
    }

    static String getInputValueAtCodeBlockPosition(List<CodeElement> codeBlock, CodeView.CodeLayout codeBlockLayout, float x, float y) {
        if(codeBlock == null || codeBlock.size() == 0 || x < 0 || y < 0){
            return "";
        }

        float height = 0;

        for (int i = 0; i < codeBlock.size(); i++) {
            CodeElement element = codeBlock.get(i);
            height += element.getHeight();

            if(height >= y){
                if(element.getWidth() >= x){
                    return element.getInputValueAtPosition(x, y - (height - element.getHeight()));
                } else {
                    return "";
                }
            }

            height += codeBlockLayout.getElementPaddingVertical();

            if(height >= y){
                return "";
            }
        }

        return "";
    }

    static void setInputValueAtCodeBlockPosition(List<CodeElement> codeBlock, CodeView.CodeLayout codeBlockLayout, String value, float x, float y) {
        if(codeBlock == null || codeBlock.size() == 0 || x < 0 || y < 0){
            return;
        }

        float height = 0;

        for (int i = 0; i < codeBlock.size(); i++) {
            CodeElement element = codeBlock.get(i);
            height += element.getHeight();

            if(height >= y){
                if(element.getWidth() >= x){
                    element.setInputValueAtPosition(value, x, y - (height - element.getHeight()));
                }

                return;
            }

            height += codeBlockLayout.getElementPaddingVertical();

            if(height >= y){
                return;
            }
        }
    }


    static float calculateCodeBlockWidth(List<CodeElement> codeBlock){
        if(codeBlock == null){
            return 0;
        }

        float width = 0;

        for (CodeElement element : codeBlock) {
            width = Math.max(width, element.getWidth());
        }

        return width;
    }

    static float calculateCodeBlockHeight(List<CodeElement> codeBlock, CodeView.CodeLayout codeBlockLayout){
        if(codeBlock == null || codeBlock.size() == 0){
            return 0;
        }

        float height = 0;

        for (CodeElement element : codeBlock) {
            height += element.getHeight();
        }

        height += codeBlockLayout.getElementPaddingVertical()*(codeBlock.size()-1);

        return height;
    }


    private JSONObject toJSONObject() throws JSONException{
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("id", format.getId());


        JSONArray params = new JSONArray();
        JSONArray blocks = new JSONArray();

        int paramNum = 0;
        int blockNum = 0;
        for(int lineNum = 0; lineNum < format.getNumOfLines(); lineNum++){
            if(format.getNumOfColumnsInLine(lineNum) > 0) {
                for (int columnNum = 0; columnNum < format.getNumOfColumnsInLine(lineNum); columnNum++) {
                    if (format.getElementType(lineNum, columnNum) == CodeFormat.ElementType.CODE) {
                        CodeElement childElement = childElements.get(lineNum).get(columnNum);

                        if(childElement != null){
                            params.put(paramNum, childElement.toJSONObject());
                        } else {
                            if(format.acceptedTypeAtPosition(lineNum, columnNum) != CodeFormat.ValueType.NONE){
                                String childValue = childValues.get(lineNum).get(columnNum);
                                if(childValue != null && !childValue.equals("")){
                                    params.put(paramNum, childValue);
                                } else {
                                    params.put(paramNum, "");
                                }
                            } else {
                                params.put(paramNum, "");
                            }
                        }

                        paramNum++;
                    }
                }
            } else {
                blocks.put(blockNum, getJSONArrayFromCodeBlock(childBlocks.get(lineNum)));
                blockNum++;
            }
        }

        if(paramNum > 0){
            jsonObject.put("params", params);
        }

        if(blockNum > 0){
            jsonObject.put("blocks", blocks);
        }

        return jsonObject;
    }


    private CodeElement removeElementFromPosition(float x, float y){
        float currentY = 0;

        for(int i = 0; i < format.getNumOfLines(); i++){
            currentY += codeLineHeights[i];

            if(currentY >= y){
                if(codeLineWidths[i] >= x){
                    return removeElementFromLineAtPosition(i, x , y - (currentY - codeLineHeights[i]));
                } else {
                    return null;
                }
            }
        }

        if(x <= layout.getEndLineWidth()){
            return this;
        } else {
            return null;
        }
    }

    private CodeElement removeElementFromLineAtPosition(int lineNum, float x, float y){
        if(format.getNumOfColumnsInLine(lineNum) == 0){
            //This line is a codeblock
            if(x >= layout.getElementPaddingHorizontal() + layout.getEmptyLineWidth()) {
                return removeElementFromCodeBlockPosition(childBlocks.get(lineNum), layout, this, x - layout.getElementPaddingHorizontal() - layout.getEmptyLineWidth(), y - layout.getElementPaddingVertical());
            } else {
                return this;
            }
        } else {
            float currentX = layout.getInnerPaddingHorizontal();

            if(x >= layout.getInnerPaddingHorizontal()){
                for(int i = 0; i < format.getNumOfColumnsInLine(lineNum); i++){
                    currentX += getCodeLineElementWidth(lineNum, i);

                    if(x <= currentX){
                        CodeElement childElement = childElements.get(lineNum).get(i);

                        if(childElement == null){
                            return this;
                        }


                        float elementStartX = currentX - getCodeLineElementWidth(lineNum, i) + layout.getElementPaddingHorizontal();
                        float elementStartY = codeLineHeights[lineNum]/2 - getCodeLineElementHeight(lineNum, i)/2 - layout.getElementPaddingVertical();

                        CodeElement removedElement = childElement.removeElementFromPosition( x - elementStartX, y - elementStartY);

                        if(removedElement == null){
                            return this;
                        }

                        if(removedElement == childElement){
                            childElements.get(lineNum).remove(i);
                            recalculateSize();

                            return removedElement;
                        }

                        return removedElement;
                    }

                    currentX += layout.getInnerPaddingHorizontal();

                    if(x <= currentX){
                        return this;
                    }
                }
            }

            return this;
        }
    }


    private ElementEntryResult addElementAtPosition(CodeElement element, float x, float y){
        if(y <= 0){
            return ElementEntryResult.IS_ABOVE;
        }

        if(y >= height){
            return ElementEntryResult.IS_BELOW;
        }

        if(x < 0){
            if(y < height/2){
                return ElementEntryResult.IS_ABOVE;
            } else {
                return ElementEntryResult.IS_BELOW;
            }
        }


        float lineY;

        //Check if there's a specific field/codeblock this element is dropped into
        lineY = 0;

        for(int i = 0; i < format.getNumOfLines(); i++){
            if(lineY+codeLineHeights[i] >= y){
                boolean wasAdded = addElementToLineAtPosition(element, i, x, y - lineY);

                if(wasAdded){
                    return ElementEntryResult.WAS_ADDED;
                } else {
                    //Element was dropped on a line where it couldn't be added to a specific element. It will be added to the closest codeblock later in this function.
                    break;
                }
            }

            lineY += codeLineHeights[i];
        }


        //Put element in closest codeblock (before or after this element, or at the start or end of the closest contained codeblock)
        float previousCodeBlockEndPos = 0;
        int previousCodeBlockLineNum = -1;

        lineY = 0;

        for(int i = 0; i < format.getNumOfLines(); i++){
            if(format.getNumOfColumnsInLine(i) == 0){
                //Current checked line is a codeblock
                //Now we have to check if the element was dropped somewhere above this block; if it wasn't, we store this block, as being the "previous" block
                if(lineY+codeLineHeights[i] >= y){
                    //The element was dropped somewhere in between the start of the previous codeblock, and the start of this one
                    //We now check if it was dropped closer to the start of this block (lineY), or the end of the last (previousCodeBlockEndPos)
                    //This is done by getting the average of both positions, giving the position in the middle, and checking if the dropped element was dropped before, or after this point
                    float middlePosition = (lineY + previousCodeBlockEndPos)/2;

                    if(y <= middlePosition){
                        //The element was dropped closer to the last codeblock. Put it as the last element in there.

                        if(previousCodeBlockLineNum < 0){
                            return ElementEntryResult.IS_ABOVE;
                        } else {
                            childBlocks.get(previousCodeBlockLineNum).add(element);
                            element.parent = this;
                            recalculateSize();

                            return ElementEntryResult.WAS_ADDED;
                        }

                    } else {
                        //Add element to current codeblock
                        childBlocks.get(i).add(0, element);
                        element.parent = this;
                        recalculateSize();

                        return ElementEntryResult.WAS_ADDED;
                    }

                } else {
                    previousCodeBlockLineNum = i;
                    previousCodeBlockEndPos = lineY + codeLineHeights[i];
                }
            }

            lineY += codeLineHeights[i];
        }


        //Check when it was dropped after the last codeBlock, if it should be dropped into that one, or after this code element
        float middlePosition = (height + previousCodeBlockEndPos)/2;

        if(y <= middlePosition){
            //The element was dropped closer to the last codeblock. Put it as the last element in there.

            if(previousCodeBlockLineNum < 0){
                return ElementEntryResult.IS_ABOVE;
            } else {
                childBlocks.get(previousCodeBlockLineNum).add(element);
                element.parent = this;
                recalculateSize();

                return ElementEntryResult.WAS_ADDED;
            }

        } else {
            return ElementEntryResult.IS_BELOW;
        }

    }

    private boolean addElementToLineAtPosition(CodeElement element, int lineNum, float x, float y){
        if(format.getNumOfColumnsInLine(lineNum) == 0){
            //This line contains a codeblock, to which the element should be added
            return addElementToCodeBlockPosition(element, childBlocks.get(lineNum), layout, this, x - layout.getInnerPaddingHorizontal() - layout.getEmptyLineWidth(), y - layout.getInnerPaddingVertical());
        } else {
            float currentX = layout.getInnerPaddingHorizontal();

            if(x >= layout.getInnerPaddingHorizontal()){

                for(int i = 0; i < format.getNumOfColumnsInLine(lineNum); i++){
                    float currentElementWidth = getCodeLineElementWidth(lineNum, i);

                    if(x <= currentX + currentElementWidth){
                        String childValue = childValues.get(lineNum).get(i);

                        if(childValue == null || childValue.equals("")){
                            CodeElement childElement = childElements.get(lineNum).get(i);

                            if(childElement == null){
                                if(format.acceptsParameterAt(element.format, lineNum, i)){
                                    childElements.get(lineNum).put(i, element);
                                    element.parent = this;
                                    recalculateSize();

                                    return true;
                                } else {
                                    return false;
                                }
                            } else {
                                float elementStartX = currentX + layout.getElementPaddingHorizontal();
                                float elementStartY = codeLineHeights[lineNum]/2 - getCodeLineElementHeight(lineNum, i)/2 + layout.getElementPaddingVertical();

                                ElementEntryResult result = childElement.addElementAtPosition(element, x - elementStartX, y - elementStartY);

                                if(result == ElementEntryResult.WAS_ADDED){
                                    return true;
                                } else {
                                    return false;
                                }
                            }
                        } else {
                            return false;
                        }
                    }

                    currentX += currentElementWidth + layout.getInnerPaddingHorizontal();

                    if(x <= currentX){
                        return false;
                    }
                }

            }
        }

        return false;
    }


    private CodeFormat.ValueType getInputTypeAtPosition(float x, float y) {
        if(y <= 0 || y >= height || x <= 0 || x >= width){
            return CodeFormat.ValueType.NONE;
        }

        float lineY = 0;

        for(int i = 0; i < format.getNumOfLines(); i++){
            if(lineY+codeLineHeights[i] >= y){
                return getInputTypeAtPositionInLine(i, x, y - lineY);
            }

            lineY += codeLineHeights[i];
        }

        return CodeFormat.ValueType.NONE;
    }

    private CodeFormat.ValueType getInputTypeAtPositionInLine(int lineNum, float x, float y) {
        if(format.getNumOfColumnsInLine(lineNum) == 0){
            return getInputTypeAtCodeBlockPosition(childBlocks.get(lineNum), layout, x - layout.getEmptyLineWidth() - layout.getElementPaddingHorizontal(), y - layout.getElementPaddingVertical());
        } else {
            float currentX = layout.getInnerPaddingHorizontal();

            if(x >= layout.getInnerPaddingHorizontal()){
                for(int i = 0; i < format.getNumOfColumnsInLine(lineNum); i++){
                    float currentElementWidth = getCodeLineElementWidth(lineNum, i);

                    if(x <= currentX + currentElementWidth){
                        CodeElement childElement = childElements.get(lineNum).get(i);

                        if(childElement == null){
                            float elementStartY = codeLineHeights[lineNum]/2 - getCodeLineElementHeight(lineNum, i)/2;
                            float elementEndY = codeLineHeights[lineNum]/2 + getCodeLineElementHeight(lineNum, i)/2;

                            if(y >= elementStartY && y <= elementEndY){
                                return format.acceptedTypeAtPosition(lineNum, i);
                            } else {
                                return CodeFormat.ValueType.NONE;
                            }
                        } else {
                            float elementStartX = currentX + layout.getElementPaddingHorizontal();
                            float elementStartY = codeLineHeights[lineNum]/2 - getCodeLineElementHeight(lineNum, i)/2 + layout.getElementPaddingVertical();

                            return childElement.getInputTypeAtPosition(x - elementStartX, y - elementStartY);
                        }
                    }

                    currentX += currentElementWidth + layout.getInnerPaddingHorizontal();

                    if(x <= currentX){
                        return CodeFormat.ValueType.NONE;
                    }
                }
            }

            return CodeFormat.ValueType.NONE;
        }
    }


    private String getInputValueAtPosition(float x, float y) {
        if(y <= 0 || y >= height || x <= 0 || x >= width){
            return "";
        }

        float lineY = 0;

        for(int i = 0; i < format.getNumOfLines(); i++){
            if(lineY+codeLineHeights[i] >= y){
                return getInputValueAtPositionInLine(i, x, y - lineY);
            }

            lineY += codeLineHeights[i];
        }

        return "";
    }

    private String getInputValueAtPositionInLine(int lineNum, float x, float y) {
        if(format.getNumOfColumnsInLine(lineNum) == 0){
            return getInputValueAtCodeBlockPosition(childBlocks.get(lineNum), layout, x - layout.getEmptyLineWidth() - layout.getElementPaddingHorizontal(), y - layout.getElementPaddingVertical());
        } else {
            float currentX = layout.getInnerPaddingHorizontal();

            if(x >= layout.getInnerPaddingHorizontal()){
                for(int i = 0; i < format.getNumOfColumnsInLine(lineNum); i++){
                    float currentElementWidth = getCodeLineElementWidth(lineNum, i);

                    if(x <= currentX + currentElementWidth){
                        CodeElement childElement = childElements.get(lineNum).get(i);

                        if(childElement == null){
                            float elementStartY = codeLineHeights[lineNum]/2 - getCodeLineElementHeight(lineNum, i)/2;
                            float elementEndY = codeLineHeights[lineNum]/2 + getCodeLineElementHeight(lineNum, i)/2;

                            if(y >= elementStartY && y <= elementEndY){
                                return childValues.get(lineNum).get(i);
                            } else {
                                return "";
                            }
                        } else {
                            float elementStartX = currentX + layout.getElementPaddingHorizontal();
                            float elementStartY = codeLineHeights[lineNum]/2 - getCodeLineElementHeight(lineNum, i)/2 + layout.getElementPaddingVertical();

                            return childElement.getInputValueAtPosition(x - elementStartX, y - elementStartY);
                        }
                    }

                    currentX += currentElementWidth + layout.getInnerPaddingHorizontal();

                    if(x <= currentX){
                        return "";
                    }
                }
            }

            return "";
        }
    }


    private void setInputValueAtPosition(String value, float x, float y) {
        if(y <= 0 || y >= height || x <= 0 || x >= width){
            return;
        }

        float lineY = 0;

        for(int i = 0; i < format.getNumOfLines(); i++){
            if(lineY+codeLineHeights[i] >= y){
                setInputValueAtPositionInLine(i, value, x, y - lineY);
                return;
            }

            lineY += codeLineHeights[i];
        }
    }

    private void setInputValueAtPositionInLine(int lineNum, String value, float x, float y) {
        if(format.getNumOfColumnsInLine(lineNum) == 0){
            setInputValueAtCodeBlockPosition(childBlocks.get(lineNum), layout, value, x - layout.getEmptyLineWidth() - layout.getElementPaddingHorizontal(), y - layout.getElementPaddingVertical());
        } else {
            float currentX = layout.getInnerPaddingHorizontal();

            if(x >= layout.getInnerPaddingHorizontal()){
                for(int i = 0; i < format.getNumOfColumnsInLine(lineNum); i++){
                    float currentElementWidth = getCodeLineElementWidth(lineNum, i);

                    if(x <= currentX + currentElementWidth){
                        CodeElement childElement = childElements.get(lineNum).get(i);

                        if(childElement == null){
                            float elementStartY = codeLineHeights[lineNum]/2 - getCodeLineElementHeight(lineNum, i)/2;
                            float elementEndY = codeLineHeights[lineNum]/2 + getCodeLineElementHeight(lineNum, i)/2;

                            if(y >= elementStartY && y <= elementEndY){
                                childValues.get(lineNum).put(i, value);
                                recalculateSize();
                                return;
                            } else {
                                return;
                            }
                        } else {
                            float elementStartX = currentX + layout.getElementPaddingHorizontal();
                            float elementStartY = codeLineHeights[lineNum]/2 - getCodeLineElementHeight(lineNum, i)/2 + layout.getElementPaddingVertical();

                            childElement.setInputValueAtPosition(value, x - elementStartX, y - elementStartY);
                            return;
                        }
                    }

                    currentX += currentElementWidth + layout.getInnerPaddingHorizontal();

                    if(x <= currentX){
                        return;
                    }
                }
            }
        }
    }


    public boolean recalculateSize(){
        float oldWidth = width;
        float oldHeight = height;

        width = 0.f;
        height = 0.f;

        for(int i = 0; i < format.getNumOfLines(); i++){
            if(format.getNumOfColumnsInLine(i) == 0){
                codeLineWidths[i] = calculateCodeBlockWidth(childBlocks.get(i)) + layout.getElementPaddingHorizontal() + layout.getEmptyLineWidth();
                codeLineHeights[i] = calculateCodeBlockHeight(childBlocks.get(i), layout) + 2* layout.getElementPaddingVertical();

                width = Math.max(width, codeLineWidths[i]);
                height += codeLineHeights[i];
            } else {
                float lineHeight = 0;
                float lineWidth = layout.getInnerPaddingHorizontal();

                for(int j = 0; j < format.getNumOfColumnsInLine(i); j++){
                    lineHeight = Math.max(lineHeight, getCodeLineElementHeight(i, j) + 2* layout.getInnerPaddingVertical());
                    lineWidth += getCodeLineElementWidth(i, j) + layout.getInnerPaddingHorizontal();
                }

                codeLineWidths[i] = lineWidth;
                codeLineHeights[i] = lineHeight;

                height += codeLineHeights[i];
                width = Math.max(width, lineWidth);
            }
        }

        //Check if the last "line" contains a code-block; if so, add an additional, empty, line, closing the code-block
        if(format.getNumOfColumnsInLine(format.getNumOfLines()-1) == 0){
            height += getTextHeight()+2* layout.getInnerPaddingVertical();
        }

        //Tell the parent to recalculate
        if(parent != null){
            parent.recalculateSize();
        }

        //Check if the width or height changed
        if(Float.compare(oldWidth, width) != 0 || Float.compare(oldHeight, height) != 0){
            return true;
        } else {
            return false;
        }
    }



    float getSimpleDrawingWidth(){
        String text = getSimpleDrawingText();

        return layout.getTextPaint().measureText(text) + 2* layout.getInnerPaddingHorizontal();
    }

    float getSimpleDrawingHeight(){
        return getTextHeight() + 2*layout.getInnerPaddingVertical();
    }

    void drawSimple(Canvas canvas, float top, float left){
        String text = getSimpleDrawingText();

        float width = getSimpleDrawingWidth();
        float height = getSimpleDrawingHeight();

        drawRect(canvas, left, top, left + width, top + height, layout.getElementPaint(format));
        canvas.drawText(text, left + layout.getInnerPaddingHorizontal(), top + height/2 + layout.getTextPaint().getFontMetrics().bottom, layout.getTextPaint());
    }

    private String getSimpleDrawingText(){
        String text = format.getName();

        boolean hasContent = false;

        for(int i = 0; i < format.getNumOfLines(); i++){
            if(childBlocks.get(i) != null && childBlocks.get(i).size() > 0){
                hasContent = true;
                break;
            }

            if(childElements.get(i) != null && childElements.get(i).size() > 0){
                hasContent = true;
                break;
            }

            SparseArray<String> values = childValues.get(i);
            if(values != null){
                for(int j = 0; j < values.size(); j++){
                    String childVal = values.get(values.keyAt(j));
                    if(childVal != null && !childVal.equals("")){
                        hasContent = true;
                        break;
                    }
                }
            }
        }

        if(hasContent){
            text += " ...";
        }

        return text;
    }



    //Draws the CodeElement recursively (calling the draw functions on all it's children)
    void draw(Canvas canvas, float top, float left){
        //Don't draw element, if it won't be within the screen
        if(top + height >= 0 && left + width >= 0 && top <= canvas.getHeight() && left <= canvas.getWidth()){
            //Draw a line along the left side of the code block, connecting all the lines, so there are no gaps.
            drawRect(canvas, left, top, left+ layout.getEmptyLineWidth(), top+height, layout.getElementPaint(format));


            //Draw main body of the code-element, and draw all it's children
            float lineStartHeight = top;

            for(int i = 0; i < format.getNumOfLines(); i++){
                drawCodeLine(canvas, i, lineStartHeight, left);

                lineStartHeight += codeLineHeights[i];
            }


            //Check if the last "line" contains a code-block; if so, add an additional, empty, line, closing the code-block
            if(format.getNumOfColumnsInLine(format.getNumOfLines()-1) == 0){
                drawRect(canvas, left, top + height - (getTextHeight()+2* layout.getInnerPaddingVertical()), left+ layout.getEndLineWidth(), top+height, layout.getElementPaint(format));
            }
        }
    }


    private void drawCodeLine(Canvas canvas, int lineIndex, float top, float left){
        int numOfColumnsInLine = format.getNumOfColumnsInLine(lineIndex);

        //Checks if this "line" contains a code-block, or is a normal line
        if(numOfColumnsInLine == 0){
            drawCodeBlock(canvas, childBlocks.get(lineIndex), top + layout.getElementPaddingVertical(), left + layout.getEmptyLineWidth() + layout.getElementPaddingHorizontal(), layout.getElementPaddingVertical());
        } else {
            //Draw background
            drawRect(canvas, left, top, left + codeLineWidths[lineIndex], top + codeLineHeights[lineIndex], layout.getElementPaint(format));


            float elementStartWidth = left + layout.getInnerPaddingHorizontal();

            for(int i = 0; i < numOfColumnsInLine; i++){
                float elementWidth = getCodeLineElementWidth(lineIndex, i);

                drawCodeElement(canvas, lineIndex, i, top, elementStartWidth);

                elementStartWidth += elementWidth + layout.getInnerPaddingHorizontal();
            }
        }
    }

    private void drawCodeElement(Canvas canvas, int lineIndex, int elementIndex, float top, float left){
        float elementWidth = getCodeLineElementWidth(lineIndex, elementIndex);
        float elementHeight = getCodeLineElementHeight(lineIndex, elementIndex);

        CodeElement element;

        switch(format.getElementType(lineIndex, elementIndex)){
            case TEXT:
                canvas.drawText(format.getTextAtPosition(lineIndex, elementIndex), left, top + codeLineHeights[lineIndex]/2 + layout.getTextPaint().getFontMetrics().bottom, layout.getTextPaint());
                break;
            case CODE:
                Paint paint;

                if(format.isElementInput(lineIndex, elementIndex)){
                    paint = layout.getInputFieldPaint(format);
                } else if (format.isElementOutput(lineIndex, elementIndex)){
                    paint = layout.getOutputFieldPaint(format);
                } else {
                    paint = layout.getCodeFieldPaint(format);
                }

                drawRect(canvas, left, top + codeLineHeights[lineIndex]/2 - elementHeight/2, left + elementWidth, top + codeLineHeights[lineIndex]/2 + elementHeight/2, paint);
                element = childElements.get(lineIndex).get(elementIndex);

                if(element != null){
                    element.draw(canvas, top + codeLineHeights[lineIndex]/2 - elementHeight/2 + layout.getElementPaddingVertical(), left + layout.getElementPaddingHorizontal());
                } else {
                    String value = childValues.get(lineIndex).get(elementIndex);

                    if(value != null && !value.equals("")){
                        canvas.drawText(value, left + layout.getInnerPaddingHorizontal(), top + codeLineHeights[lineIndex]/2 + layout.getTextPaint().getFontMetrics().bottom, layout.getTextPaint());
                    }
                }

                break;
        }
    }

    private void drawRect(Canvas canvas, float startX, float startY, float endX, float endY, Paint paint){
        if (Build.VERSION.SDK_INT >= 21) {
            canvas.drawRoundRect(startX, startY, endX, endY, 10, 10, paint);
        } else {
            canvas.drawRect(startX, startY, endX, endY, paint);
        }
    }


    private float getCodeLineElementHeight(int lineIndex, int elementIndex){
        if(format.getElementType(lineIndex, elementIndex) == CodeFormat.ElementType.TEXT){
            return getTextHeight();
        } else {
            CodeElement element = childElements.get(lineIndex).get(elementIndex);

            if(element != null){
                return element.getHeight() + 2* layout.getElementPaddingVertical();
            } else {
                String value = childValues.get(lineIndex).get(elementIndex);

                if(value == null || value.equals("")){
                    return layout.getEmptyFieldHeight();
                } else {
                    return getTextHeight() + 2* layout.getInnerPaddingVertical();
                }
            }
        }
    }

    private float getCodeLineElementWidth(int lineIndex, int elementIndex){
        if(format.getElementType(lineIndex, elementIndex) == CodeFormat.ElementType.TEXT){
            return layout.getTextPaint().measureText(format.getTextAtPosition(lineIndex, elementIndex));
        } else {
            CodeElement element = childElements.get(lineIndex).get(elementIndex);

            if(element != null){
                return element.getWidth() + 2* layout.getElementPaddingHorizontal();
            } else {
                String value = childValues.get(lineIndex).get(elementIndex);

                if(value == null || value.equals("")){
                    return layout.getEmptyFieldWidth();
                } else {
                    return layout.getTextPaint().measureText(value) + 2* layout.getInnerPaddingHorizontal();
                }
            }
        }
    }


    private int getTextHeight(){
        Paint.FontMetrics fm = layout.getTextPaint().getFontMetrics();
        return Math.round(fm.bottom - fm.top + fm.leading);
    }


    private int calculateNumOfChildBlocks(){
        int childBlocks = 0;

        for(int i = 0; i < format.getNumOfLines(); i++){
            if(format.getNumOfColumnsInLine(i) == 0){
                childBlocks++;
            }
        }

        return childBlocks;
    }


    private enum ElementEntryResult{
        WAS_ADDED,
        IS_ABOVE,
        IS_BELOW
    }



    public float getWidth(){
        return width;
    }

    public float getHeight(){
        return height;
    }



    public String getCodeFormatId() {
        return format.getId();
    }
}
