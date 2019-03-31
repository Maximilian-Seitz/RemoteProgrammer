package com.rcprogrammer.remoteprogrammer.codeeditor.codeview;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Scroller;

import com.rcprogrammer.remoteprogrammer.R;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;


public class CodeView extends View implements CodeContainer {

    private float codeWidth;
    private float codeHeight;

    private float viewWidth;
    private float viewHeight;


    private Scroller scroller;
    private Paint scrollBarPaint;

    private boolean isDraggingEnabled = false;
    private CodeElement draggedElement = null;
    private JSONArray lastState = new JSONArray();

    private CodeElement storedElement = null;

    private DragAndDropLocation deleteIcon = new DragAndDropLocation(getResources().getDrawable(android.R.drawable.ic_menu_delete), 0.5f, 0, 0, 60, 100, 100);
    private DragAndDropLocation infoIcon = new DragAndDropLocation(getResources().getDrawable(android.R.drawable.ic_menu_help), 1, 0, -60, 170, 100, 100);
    private DragAndDropLocation holdIcon = new DragAndDropLocation(getResources().getDrawable(android.R.drawable.ic_menu_save), 1, 0, -60, 60, 100, 100);
    private DragAndDropLocation heldIcon = new DragAndDropLocation(getResources().getDrawable(R.drawable.box), 1, 0, -60, 60, 100, 100);


    private float touchX = 0;
    private float touchY = 0;
    private float touchSize = 0;

    final GestureDetector gestureDetector;

    private OnChangeListener onChangeListener = null;
    private boolean sendChangeEvent = true;

    private CodeInfoListener codeInfoListener = null;


    int paddingLeft = getPaddingLeft();
    int paddingTop = getPaddingTop();
    int paddingRight = getPaddingRight();
    int paddingBottom = getPaddingBottom();

    private CodeLayout layout;


    private List<CodeElement> code;


    public CodeView(Context context) {
        super(context);

        gestureDetector = new GestureDetector(context, new MyGestureListener());
        init(null, 0);
    }

    public CodeView(Context context, AttributeSet attrs) {
        super(context, attrs);

        gestureDetector = new GestureDetector(context, new MyGestureListener());
        init(attrs, 0);
    }

    public CodeView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        gestureDetector = new GestureDetector(context, new MyGestureListener());
        init(attrs, defStyle);
    }


    private void init(AttributeSet attrs, int defStyle) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        float codeSizeMult = ((float) prefs.getInt("code_size_percent", 100)) / 100;

        layout = new CodeLayout(attrs, defStyle, codeSizeMult);

        code = new ArrayList<>();


        scroller = new Scroller(getContext());
        scroller.setFriction(0.05f);

        scrollBarPaint = new Paint();
        scrollBarPaint.setColor(Color.GRAY);
        scrollBarPaint.setAlpha(180);


        invalidate();
    }



    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);

        paddingLeft = getPaddingLeft();
        paddingTop = getPaddingTop();
        paddingRight = getPaddingRight();
        paddingBottom = getPaddingBottom();

        if (scroller.computeScrollOffset()) {
            postInvalidateOnAnimation();
        }


        CodeElement.drawCodeBlock(canvas, code, paddingTop + layout.elementPaddingVertical - scroller.getCurrY(), paddingLeft + layout.elementPaddingHorizontal - scroller.getCurrX(), layout.elementPaddingVertical);

        if(draggedElement != null){
            draggedElement.draw(canvas, touchY + touchSize, touchX + touchSize);
        }


        drawUIElements(canvas);
    }


    private void drawUIElements(Canvas canvas){
        if(isDraggingEnabled && draggedElement != null){
            drawDragAndDropIcons(canvas);
        } else {
            if(storedElement != null){
                float height = storedElement.getSimpleDrawingHeight() + 2*layout.getElementPaddingVertical();
                float width = storedElement.getSimpleDrawingWidth() + 2*layout.getElementPaddingHorizontal();

                heldIcon.sizeX = width;
                heldIcon.sizeY = height;

                heldIcon.adderX = -(10 + width/2);
                heldIcon.adderY = 10 + height/2;

                heldIcon.draw(canvas);

                storedElement.drawSimple(canvas, 10 + layout.getElementPaddingVertical(), viewWidth - (10+width-layout.getElementPaddingHorizontal()));
            }
        }

        drawScrollBars(canvas);
    }


    private void drawDragAndDropIcons(Canvas canvas){
        deleteIcon.draw(canvas);

        if(storedElement == null){
            holdIcon.draw(canvas);

            if (CodeFormat.get(draggedElement.getCodeFormatId()).hasInfo()) {
                infoIcon.draw(canvas);
            }
        }
    }


    private void drawScrollBars(Canvas canvas){
        float scrollBarPadding = 3;
        float scrollBarWidth = 7;

        if(codeWidth > viewWidth){
            int scrollX = scroller.getCurrX();

            float scrollXStartFraction = scrollX / codeWidth;
            float scrollXEndFraction = (scrollX + viewWidth) / codeWidth;

            canvas.drawRect(
                    paddingLeft + 2*scrollBarPadding + scrollBarWidth + scrollXStartFraction*(viewWidth - paddingLeft - paddingRight - 2*(2*scrollBarPadding + scrollBarWidth)),
                    viewHeight - scrollBarPadding - scrollBarWidth,
                    paddingLeft + 2*scrollBarPadding + scrollBarWidth + scrollXEndFraction*(viewWidth - paddingLeft - paddingRight - 2*(2*scrollBarPadding + scrollBarWidth)),
                    viewHeight - scrollBarPadding,
                    scrollBarPaint
            );
        }

        if(codeHeight > viewHeight){
            int scrollY = scroller.getCurrY();

            float scrollYStartFraction = scrollY / codeHeight;
            float scrollYEndFraction = (scrollY + viewHeight) / codeHeight;

            canvas.drawRect(
                    viewWidth - scrollBarPadding - scrollBarWidth,
                    paddingTop + 2*scrollBarPadding + scrollBarWidth + scrollYStartFraction*(viewHeight - paddingTop - paddingBottom - 2*(2*scrollBarPadding + scrollBarWidth)),
                    viewWidth - scrollBarPadding,
                    paddingTop + 2*scrollBarPadding + scrollBarWidth + scrollYEndFraction*(viewHeight - paddingTop - paddingBottom - 2*(2*scrollBarPadding + scrollBarWidth)),
                    scrollBarPaint
            );
        }
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        this.viewWidth = w;
        this.viewHeight = h;
        super.onSizeChanged(w, h, oldw, oldh);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        invalidate();

        if (event.getAction() == MotionEvent.ACTION_UP && draggedElement != null) {
            isDraggingEnabled = false;

            if(deleteIcon.isWithin(event.getX(), event.getY(), viewWidth, viewHeight)){
                CodeView.this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

                draggedElement = null;
            } else if(infoIcon.isWithin(event.getX(), event.getY(), viewWidth, viewHeight) && storedElement == null){
                CodeView.this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

                if(codeInfoListener != null){
                    codeInfoListener.onRequestInfo(this, CodeFormat.get(draggedElement.getCodeFormatId()));
                }

                storedElement = draggedElement;
                draggedElement = null;
            } else if(holdIcon.isWithin(event.getX(), event.getY(), viewWidth, viewHeight) && storedElement == null){
                CodeView.this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

                storedElement = draggedElement;
                draggedElement = null;
            } else {
                scroller.computeScrollOffset();

                CodeElement.addElementToCodeBlockPosition(draggedElement, code, layout, this, event.getX() + scroller.getCurrX() - paddingLeft - layout.getInnerPaddingHorizontal(), event.getY() + scroller.getCurrY() - paddingTop - layout.getInnerPaddingVertical());

                draggedElement = null;
            }
        }

        touchX = event.getX();
        touchY = event.getY();
        touchSize = event.getSize();

        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }

        return super.onTouchEvent(event);
    }


    @Override
    public boolean recalculateSize() {
        float oldWidth = codeWidth;
        float oldHeight = codeHeight;


        this.codeWidth = CodeElement.calculateCodeBlockWidth(code) + paddingLeft + 2*layout.elementPaddingHorizontal + paddingRight;
        this.codeHeight = CodeElement.calculateCodeBlockHeight(code, layout) + paddingTop + 2*layout.elementPaddingVertical + paddingBottom;


        //This being called means the children have somehow changed, so the CodeView will have to be redrawn.
        invalidate();

        triggerChangeEvent();

        //Check if the codeWidth or codeHeight changed
        if(Float.compare(oldWidth, codeWidth) != 0 || Float.compare(oldHeight, codeHeight) != 0){
            return true;
        } else {
            return false;
        }
    }

    private void triggerChangeEvent(){
        if(onChangeListener != null && sendChangeEvent){
            JSONArray codeState = null;
            try{
                codeState = getCodeState();

                if(lastState.toString().equals(codeState.toString())){
                    return;
                } else {
                    lastState = codeState;
                }
            } catch(Exception e) {
                e.printStackTrace();
            }

            onChangeListener.onChange(this, codeState);
        }
    }


    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            scroller.forceFinished(true);
            CodeView.this.postInvalidateOnAnimation();

            // don't return false here or else none of the other
            // gestures will work
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            showConstantInput(e.getX(), e.getY());
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            scroller.forceFinished(true);
            isDraggingEnabled = true;

            if(heldIcon.isWithin(e.getX(), e.getY(), viewWidth, viewHeight) && storedElement != null){
                draggedElement = storedElement;
                storedElement = null;
            } else {
                scroller.computeScrollOffset();

                draggedElement = CodeElement.removeElementFromCodeBlockPosition(code, layout, CodeView.this, e.getX()+scroller.getCurrX()-paddingLeft-layout.elementPaddingHorizontal, e.getY()+scroller.getCurrY()-paddingTop-layout.elementPaddingVertical);
            }

            if(draggedElement != null){
                CodeView.this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } else {
                isDraggingEnabled = false;
            }
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            showConstantInput(e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if(!isDraggingEnabled){
                scroller.forceFinished(true);

                scroller.setFinalX(Math.min(Math.max(0, (int) (codeWidth - viewWidth)), Math.max(0, scroller.getFinalX() + (int) distanceX)));
                scroller.setFinalY(Math.min(Math.max(0, (int) (codeHeight - viewHeight)), Math.max(0, scroller.getFinalY() + (int) distanceY)));

                CodeView.this.postInvalidateOnAnimation();
            }

            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if(!isDraggingEnabled){
                scroller.fling(scroller.getCurrX(), scroller.getCurrY(), (int) -velocityX, (int) -velocityY, 0, Math.max(0, (int) (codeWidth - viewWidth)), 0,  Math.max(0, (int) (codeHeight - viewHeight)));

                CodeView.this.postInvalidateOnAnimation();
            }

            return true;
        }


        private void showConstantInput(float touchPosX, float touchPosY){
            scroller.computeScrollOffset();

            final float touchLocalX = touchPosX + scroller.getCurrX()-paddingLeft-layout.elementPaddingHorizontal;
            final float touchLocalY = touchPosY + scroller.getCurrY()-paddingTop-layout.elementPaddingVertical;

            CodeFormat.ValueType inputType = CodeElement.getInputTypeAtCodeBlockPosition(code, layout, touchLocalX, touchLocalY);
            String value = CodeElement.getInputValueAtCodeBlockPosition(code, layout, touchLocalX, touchLocalY);

            if(inputType == CodeFormat.ValueType.NONE || inputType == CodeFormat.ValueType.BOOL){
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

            final EditText input = new EditText(getContext());

            if(inputType == CodeFormat.ValueType.NUM){
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            } else if(inputType == CodeFormat.ValueType.TEXT){
                input.setInputType(InputType.TYPE_CLASS_TEXT);
            } else {
                return;
            }

            CodeView.this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

            input.setText(value);
            input.selectAll();
            builder.setView(input);

            // Set up the buttons
            builder.setPositiveButton(R.string.btn_OK, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    CodeElement.setInputValueAtCodeBlockPosition(code, layout, input.getText().toString(), touchLocalX, touchLocalY);
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


    class CodeLayout {

        private int innerPaddingHorizontal =  (int) getResources().getDimension(R.dimen.code_view_inner_padding_horizontal);
        private int innerPaddingVertical =  (int) getResources().getDimension(R.dimen.code_view_inner_padding_vertical);
        private int elementPaddingHorizontal =  (int) getResources().getDimension(R.dimen.code_view_element_padding_vertical);
        private int elementPaddingVertical =  (int) getResources().getDimension(R.dimen.code_view_element_padding_horizontal);
        private int emptyFieldWidth =  (int) getResources().getDimension(R.dimen.code_view_empty_field_width);
        private int emptyFieldHeight =  (int) getResources().getDimension(R.dimen.code_view_empty_field_height);
        private int emptyLineWidth =  (int) getResources().getDimension(R.dimen.code_view_empty_line_width);
        private int endLineWidth =  (int) getResources().getDimension(R.dimen.code_view_end_line_width);

        private float textSize = (int) getResources().getDimension(R.dimen.code_view_text_size);

        private TextPaint mTextPaint;
        private Paint[] mElementPaints;
        private Paint[] mCodeFieldPaints;
        private Paint[] mInputFieldPaints;
        private Paint[] mOutputFieldPaints;


        CodeLayout(AttributeSet attrs, int defStyle, float codeSizeMult) {
            final TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.CodeView, defStyle, 0);

            innerPaddingHorizontal = (int) (codeSizeMult * a.getDimensionPixelSize(R.styleable.CodeView_inner_padding_horizontal, innerPaddingHorizontal));
            innerPaddingVertical = (int) (codeSizeMult * a.getDimensionPixelSize(R.styleable.CodeView_inner_padding_vertical, innerPaddingVertical));
            elementPaddingHorizontal = (int) (codeSizeMult * a.getDimensionPixelSize(R.styleable.CodeView_element_padding_horizontal, elementPaddingHorizontal));
            elementPaddingVertical = (int) (codeSizeMult * a.getDimensionPixelSize(R.styleable.CodeView_element_padding_vertical, elementPaddingVertical));
            emptyFieldWidth = (int) (codeSizeMult * a.getDimensionPixelSize(R.styleable.CodeView_empty_field_width, emptyFieldWidth));
            emptyFieldHeight = (int) (codeSizeMult * a.getDimensionPixelSize(R.styleable.CodeView_empty_field_height, emptyFieldHeight));

            emptyLineWidth = (int) (codeSizeMult * a.getDimensionPixelSize(R.styleable.CodeView_empty_line_width, emptyLineWidth));
            endLineWidth = (int) (codeSizeMult * a.getDimensionPixelSize(R.styleable.CodeView_end_line_width, endLineWidth));

            textSize = (int) (codeSizeMult * a.getDimension(R.styleable.CodeView_text_size, textSize));

            a.recycle();


            mTextPaint = new TextPaint();
            mTextPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
            mTextPaint.setTextAlign(Paint.Align.LEFT);
            mTextPaint.setTextSize(textSize);
            mTextPaint.setColor(Color.WHITE);

            //Get color-schemes for the code-categories
            int[] mainColors = calculateMainColors(0.73f, 0.47f);

            mElementPaints = getPaintsFromColors(mainColors, 0, 0, 0);
            mCodeFieldPaints = getPaintsFromColors(mainColors, 0, 0, -0.17f);
            mInputFieldPaints = getPaintsFromColors(mainColors, 0, 0, 0.36f);
            mOutputFieldPaints = getPaintsFromColors(mainColors, 0, 0, 0.13f);
        }

        private int[] calculateMainColors(float saturation, float value) {
            int numOfCategories = CodeFormat.getNumOfCategories();
            int[] mainColors = new int[numOfCategories];

            for(int i = 0; i < numOfCategories; i++){
                float[] hsv = new float[3];
                hsv[0] = CodeFormat.getCategoryColorHue(i);
                hsv[1] = saturation;
                hsv[2] = value;

                mainColors[i] = Color.HSVToColor(hsv);
            }

            return mainColors;
        }

        private Paint[] getPaintsFromColors(int[] colors, float hueOffset, float saturationOffset, float valueOffset) {
            Paint[] paintArray = new Paint[colors.length];

            for (int i = 0; i < colors.length; i++) {
                paintArray[i] = new Paint();

                float[] hsv = new float[3];
                Color.colorToHSV(colors[i], hsv);
                hsv[0] += hueOffset;
                hsv[1] += saturationOffset;
                hsv[2] += valueOffset;
                int color = Color.HSVToColor(hsv);

                paintArray[i].setColor(color);
            }

            return paintArray;
        }


        int getInnerPaddingHorizontal() {
            return innerPaddingHorizontal;
        }

        int getInnerPaddingVertical() {
            return innerPaddingVertical;
        }

        int getElementPaddingHorizontal() {
            return elementPaddingHorizontal;
        }

        int getElementPaddingVertical() {
            return elementPaddingVertical;
        }

        int getEmptyFieldWidth() {
            return emptyFieldWidth;
        }

        int getEmptyFieldHeight() {
            return emptyFieldHeight;
        }

        int getEmptyLineWidth() {
            return emptyLineWidth;
        }

        int getEndLineWidth() {
            return endLineWidth;
        }


        TextPaint getTextPaint(){
            return mTextPaint;
        }

        Paint getElementPaint(CodeFormat format){
            if(mElementPaints.length > format.getCategory()){
                return mElementPaints[format.getCategory()];
            } else {
                return mElementPaints[0];
            }
        }

        Paint getCodeFieldPaint(CodeFormat format){
            if(mCodeFieldPaints.length > format.getCategory()){
                return mCodeFieldPaints[format.getCategory()];
            } else {
                return mCodeFieldPaints[0];
            }
        }

        Paint getInputFieldPaint(CodeFormat format){
            if(mInputFieldPaints.length > format.getCategory()){
                return mInputFieldPaints[format.getCategory()];
            } else {
                return mInputFieldPaints[0];
            }
        }

        Paint getOutputFieldPaint(CodeFormat format){
            if(mOutputFieldPaints.length > format.getCategory()){
                return mOutputFieldPaints[format.getCategory()];
            } else {
                return mOutputFieldPaints[0];
            }
        }

    }


    private class DragAndDropLocation {
        Drawable icon;

        float fractionX = 0.f;
        float fractionY = 0.f;
        float adderX = 0.f;
        float adderY = 0.f;

        float sizeX = 0;
        float sizeY = 0;

        DragAndDropLocation(Drawable icon, float fractionX, float fractionY, float adderX, float adderY, float sizeX, float sizeY){
            this.icon = icon;

            this.fractionX = fractionX;
            this.fractionY = fractionY;
            this.adderX = adderX;
            this.adderY = adderY;

            this.sizeX = sizeX;
            this.sizeY = sizeY;
        }

        void draw(Canvas canvas){
            icon.setBounds((int) (canvas.getWidth()*fractionX + adderX - sizeX/2), (int) (canvas.getHeight()*fractionY + adderY - sizeY/2), (int) (canvas.getWidth()*fractionX + adderX + sizeX/2), (int) (canvas.getHeight()*fractionY + adderY + sizeY/2));
            icon.draw(canvas);
        }

        boolean isWithin(float x, float y, float maxX, float maxY){
            return (x > maxX*fractionX + adderX - sizeX/2 && x < maxX*fractionX + adderX + sizeX/2 && y > maxY*fractionY + adderY - sizeY/2 && y < maxY*fractionY + adderY + sizeY/2);
        }
    }


    public interface OnChangeListener {
        void onChange(View view, JSONArray codeState);
    }

    public interface CodeInfoListener {
        void onRequestInfo(View view, CodeFormat format);
    }



    public void addCodeElement(CodeFormat format){
        if(format != null){
            CodeElement element = new CodeElement(format, this, layout);
            code.add(element);
            recalculateSize();
        }
    }

    public void addCodeElement(String codeFormatId){
        addCodeElement(CodeFormat.get(codeFormatId));
    }


    public void setCodeState(JSONArray codeState) throws JSONException{
        code = new ArrayList<>();

        sendChangeEvent = false;
        CodeElement.addCodeElementsToCodeBlockFromJSON(codeState, code, this, layout);
        sendChangeEvent = true;
    }

    public JSONArray getCodeState() throws JSONException{
        return CodeElement.getJSONArrayFromCodeBlock(code);
    }


    public void setOnChangeListener(OnChangeListener listener){
        this.onChangeListener = listener;
    }

    public void setCodeInfoListener(CodeInfoListener listener){
        this.codeInfoListener = listener;
    }
}
