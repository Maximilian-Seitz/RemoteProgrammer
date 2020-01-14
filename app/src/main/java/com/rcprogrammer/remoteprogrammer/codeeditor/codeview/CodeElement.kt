package com.rcprogrammer.remoteprogrammer.codeeditor.codeview

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.SparseArray
import com.rcprogrammer.remoteprogrammer.codeeditor.codeview.CodeFormat.ValueType
import com.rcprogrammer.remoteprogrammer.codeeditor.codeview.CodeView.CodeLayout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt

class CodeElement internal constructor(private val format: CodeFormat, parent: CodeContainer, private val layout: CodeLayout) : CodeContainer {
    private var parent: CodeContainer? = parent

    private val childElements: SparseArray<SparseArray<CodeElement>> = SparseArray(format.numOfLines)
    private val childValues: SparseArray<SparseArray<String>> = SparseArray(format.numOfLines)
    private val childBlocks: SparseArray<MutableList<CodeElement>> = SparseArray(calculateNumOfChildBlocks())
    private val codeLineHeights = FloatArray(format.numOfLines)
    private val codeLineWidths = FloatArray(format.numOfLines)

    var width = 0f
        private set
    var height = 0f
        private set

    init {
        //Initiate all the sparse arrays with empty lists/sparse arrays.
        for (i in 0..format.numOfLines) {
            val columnsInLine = format.getNumOfColumnsInLine(i)
            if (columnsInLine > 0) {
                childElements.put(i, SparseArray(columnsInLine))
                childValues.put(i, SparseArray(columnsInLine))
            } else {
                childBlocks.put(i, ArrayList())
            }
        }

        recalculateSize()
    }

    internal constructor(jsonObject: JSONObject, parent: CodeContainer, layout: CodeLayout) : this(CodeFormat[jsonObject.getString("id")]!!, parent, layout) {
        val params: JSONArray = if (jsonObject.has("params")) {
            jsonObject.getJSONArray("params")
        } else JSONArray()

        val blocks: JSONArray = if (jsonObject.has("blocks")) {
            jsonObject.getJSONArray("blocks")
        } else JSONArray()

        var paramNum = 0
        var blockNum = 0

        for (lineNum in 0..format.numOfLines) {
            val numOfColumnsInLine = format.getNumOfColumnsInLine(lineNum)
            if (numOfColumnsInLine > 0) {
                for (columnNum in 0..numOfColumnsInLine) {
                    if (format.getElementType(lineNum, columnNum) === CodeFormat.ElementType.CODE) {
                        when (val element = params[paramNum++]) {
                            is JSONObject -> childElements[lineNum].put(columnNum, CodeElement(element, this, layout))
                            is String -> childValues[lineNum].put(columnNum, element)
                            else -> throw JSONException("Error parsing JSON")
                        }
                    }
                }
            } else {
                val codeBlock = blocks.getJSONArray(blockNum++)

                addCodeElementsToCodeBlockFromJSON(codeBlock, childBlocks[lineNum], this, layout)
            }
        }

        recalculateSize()
    }

    @Throws(JSONException::class)
    private fun toJSONObject(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject["id"] = format.id

        val params = JSONArray()
        val blocks = JSONArray()

        var paramNum = 0
        var blockNum = 0
        for (lineNum in 0..format.numOfLines) {
            if (format.getNumOfColumnsInLine(lineNum) > 0) {
                for (columnNum in 0..format.getNumOfColumnsInLine(lineNum)) {
                    if (format.getElementType(lineNum, columnNum) === CodeFormat.ElementType.CODE) {
                        val childElement = childElements[lineNum][columnNum]
                        if (childElement != null) {
                            params[paramNum] = childElement.toJSONObject()
                        } else {
                            if (format.acceptedTypeAtPosition(lineNum, columnNum) !== ValueType.NONE) {
                                params[paramNum] = childValues[lineNum][columnNum] ?: ""
                            } else {
                                params[paramNum] = ""
                            }
                        }
                        paramNum++
                    }
                }
            } else {
                blocks[blockNum++] = getJSONArrayFromCodeBlock(childBlocks[lineNum])
            }
        }

        if (paramNum > 0) {
            jsonObject["params"] = params
        }

        if (blockNum > 0) {
            jsonObject["blocks"] = blocks
        }

        return jsonObject
    }

    private fun removeElementFromPosition(x: Float, y: Float): CodeElement? {
        var currentY = 0f

        for (i in 0..format.numOfLines) {
            currentY += codeLineHeights[i]
            if (currentY >= y) {
                return if (codeLineWidths[i] >= x) {
                    removeElementFromLineAtPosition(i, x, y - (currentY - codeLineHeights[i]))
                } else {
                    null
                }
            }
        }

        return if (x <= layout.endLineWidth) {
            this
        } else {
            null
        }
    }

    private fun removeElementFromLineAtPosition(lineNum: Int, x: Float, y: Float): CodeElement? {
        return if (format.getNumOfColumnsInLine(lineNum) == 0) {
            //This line is a CodeBlock
            if (x >= layout.elementPaddingHorizontal + layout.emptyLineWidth) {
                removeElementFromCodeBlockPosition(childBlocks[lineNum], layout, this, x - layout.elementPaddingHorizontal - layout.emptyLineWidth, y - layout.elementPaddingVertical)
            } else {
                this
            }
        } else {
            var currentX = layout.innerPaddingHorizontal.toFloat()
            if (x >= layout.innerPaddingHorizontal) {
                for (i in 0 until format.getNumOfColumnsInLine(lineNum)) {
                    currentX += getCodeLineElementWidth(lineNum, i)
                    if (x <= currentX) {
                        val childElement = childElements[lineNum][i]
                                ?: return this
                        val elementStartX = currentX - getCodeLineElementWidth(lineNum, i) + layout.elementPaddingHorizontal
                        val elementStartY = codeLineHeights[lineNum] / 2 - getCodeLineElementHeight(lineNum, i) / 2 - layout.elementPaddingVertical
                        val removedElement = childElement.removeElementFromPosition(x - elementStartX, y - elementStartY)
                                ?: return this
                        if (removedElement === childElement) {
                            childElements[lineNum].remove(i)
                            recalculateSize()
                            return removedElement
                        }
                        return removedElement
                    }
                    currentX += layout.innerPaddingHorizontal.toFloat()
                    if (x <= currentX) {
                        return this
                    }
                }
            }
            this
        }
    }

    private fun addElementAtPosition(element: CodeElement, x: Float, y: Float): ElementEntryResult {
        if (y <= 0) {
            return ElementEntryResult.IS_ABOVE
        }
        if (y >= height) {
            return ElementEntryResult.IS_BELOW
        }
        if (x < 0) {
            return if (y < height / 2) {
                ElementEntryResult.IS_ABOVE
            } else {
                ElementEntryResult.IS_BELOW
            }
        }

        //Check if there's a specific field/codeblock this element is dropped into
        var lineY = 0f
        for (i in 0 until format.numOfLines) {
            if (lineY + codeLineHeights[i] >= y) {
                val wasAdded = addElementToLineAtPosition(element, i, x, y - lineY)
                return if (wasAdded) {
                    ElementEntryResult.WAS_ADDED
                } else { //Element was dropped on a line where it couldn't be added to a specific element. It will be added to the closest codeblock later in this function.
                    break
                }
            }
            lineY += codeLineHeights[i]
        }

        //Put element in closest codeblock (before or after this element, or at the start or end of the closest contained codeblock)
        var previousCodeBlockEndPos = 0f
        var previousCodeBlockLineNum = -1
        lineY = 0f
        for (i in 0 until format.numOfLines) {
            if (format.getNumOfColumnsInLine(i) == 0) {
                //Current checked line is a codeblock
                //Now we have to check if the element was dropped somewhere above this block; if it wasn't, we store this block, as being the "previous" block
                if (lineY + codeLineHeights[i] >= y) {
                    //The element was dropped somewhere in between the start of the previous codeblock, and the start of this one
                    //We now check if it was dropped closer to the start of this block (lineY), or the end of the last (previousCodeBlockEndPos)
                    //This is done by getting the average of both positions, giving the position in the middle, and checking if the dropped element was dropped before, or after this point
                    val middlePosition = (lineY + previousCodeBlockEndPos) / 2
                    return if (y <= middlePosition) {
                        //The element was dropped closer to the last codeblock. Put it as the last element in there.
                        if (previousCodeBlockLineNum < 0) {
                            ElementEntryResult.IS_ABOVE
                        } else {
                            childBlocks[previousCodeBlockLineNum]!!.add(element)
                            element.parent = this
                            recalculateSize()
                            ElementEntryResult.WAS_ADDED
                        }
                    } else {
                        //Add element to current codeblock
                        childBlocks[i]!!.add(0, element)
                        element.parent = this
                        recalculateSize()
                        ElementEntryResult.WAS_ADDED
                    }
                } else {
                    previousCodeBlockLineNum = i
                    previousCodeBlockEndPos = lineY + codeLineHeights[i]
                }
            }
            lineY += codeLineHeights[i]
        }
        //Check when it was dropped after the last codeBlock, if it should be dropped into that one, or after this code element
        val middlePosition = (height + previousCodeBlockEndPos) / 2
        return if (y <= middlePosition) {
            //The element was dropped closer to the last codeblock. Put it as the last element in there.
            if (previousCodeBlockLineNum < 0) {
                ElementEntryResult.IS_ABOVE
            } else {
                childBlocks[previousCodeBlockLineNum]!!.add(element)
                element.parent = this
                recalculateSize()
                ElementEntryResult.WAS_ADDED
            }
        } else {
            ElementEntryResult.IS_BELOW
        }
    }

    private fun addElementToLineAtPosition(element: CodeElement, lineNum: Int, x: Float, y: Float): Boolean {
        if (format.getNumOfColumnsInLine(lineNum) == 0) {
            //This line contains a codeblock, to which the element should be added
            return addElementToCodeBlockPosition(element, childBlocks[lineNum], layout, this, x - layout.innerPaddingHorizontal - layout.emptyLineWidth, y - layout.innerPaddingVertical)
        } else {
            var currentX = layout.innerPaddingHorizontal.toFloat()
            if (x >= layout.innerPaddingHorizontal) {
                for (i in 0 until format.getNumOfColumnsInLine(lineNum)) {
                    val currentElementWidth = getCodeLineElementWidth(lineNum, i)
                    if (x <= currentX + currentElementWidth) {
                        val childValue = childValues[lineNum][i]
                        return if (childValue == null || childValue == "") {
                            val childElement = childElements[lineNum]!![i]
                            if (childElement == null) {
                                if (format.acceptsParameterAt(element.format, lineNum, i)) {
                                    childElements[lineNum]!!.put(i, element)
                                    element.parent = this
                                    recalculateSize()
                                    true
                                } else {
                                    false
                                }
                            } else {
                                val elementStartX = currentX + layout.elementPaddingHorizontal
                                val elementStartY = codeLineHeights[lineNum] / 2 - getCodeLineElementHeight(lineNum, i) / 2 + layout.elementPaddingVertical
                                val result = childElement.addElementAtPosition(element, x - elementStartX, y - elementStartY)
                                result == ElementEntryResult.WAS_ADDED
                            }
                        } else {
                            false
                        }
                    }
                    currentX += currentElementWidth + layout.innerPaddingHorizontal
                    if (x <= currentX) {
                        return false
                    }
                }
            }
        }
        return false
    }

    private fun getInputTypeAtPosition(x: Float, y: Float): ValueType {
        if (y <= 0 || y >= height || x <= 0 || x >= width) {
            return ValueType.NONE
        }
        var lineY = 0f
        for (i in 0 until format.numOfLines) {
            if (lineY + codeLineHeights[i] >= y) {
                return getInputTypeAtPositionInLine(i, x, y - lineY)
            }
            lineY += codeLineHeights[i]
        }
        return ValueType.NONE
    }

    private fun getInputTypeAtPositionInLine(lineNum: Int, x: Float, y: Float): ValueType {
        return if (format.getNumOfColumnsInLine(lineNum) == 0) {
            getInputTypeAtCodeBlockPosition(childBlocks[lineNum], layout, x - layout.emptyLineWidth - layout.elementPaddingHorizontal, y - layout.elementPaddingVertical)
        } else {
            var currentX = layout.innerPaddingHorizontal.toFloat()
            if (x >= layout.innerPaddingHorizontal) {
                for (i in 0 until format.getNumOfColumnsInLine(lineNum)) {
                    val currentElementWidth = getCodeLineElementWidth(lineNum, i)
                    if (x <= currentX + currentElementWidth) {
                        val childElement = childElements[lineNum]!![i]
                        return if (childElement == null) {
                            val elementStartY = codeLineHeights[lineNum] / 2 - getCodeLineElementHeight(lineNum, i) / 2
                            val elementEndY = codeLineHeights[lineNum] / 2 + getCodeLineElementHeight(lineNum, i) / 2
                            if (y in elementStartY..elementEndY) {
                                format.acceptedTypeAtPosition(lineNum, i)
                            } else {
                                ValueType.NONE
                            }
                        } else {
                            val elementStartX = currentX + layout.elementPaddingHorizontal
                            val elementStartY = codeLineHeights[lineNum] / 2 - getCodeLineElementHeight(lineNum, i) / 2 + layout.elementPaddingVertical
                            childElement.getInputTypeAtPosition(x - elementStartX, y - elementStartY)
                        }
                    }
                    currentX += currentElementWidth + layout.innerPaddingHorizontal
                    if (x <= currentX) {
                        return ValueType.NONE
                    }
                }
            }
            ValueType.NONE
        }
    }

    private fun getInputValueAtPosition(x: Float, y: Float): String {
        if (y <= 0 || y >= height || x <= 0 || x >= width) {
            return ""
        }
        var lineY = 0f
        for (i in 0 until format.numOfLines) {
            if (lineY + codeLineHeights[i] >= y) {
                return getInputValueAtPositionInLine(i, x, y - lineY)
            }
            lineY += codeLineHeights[i]
        }
        return ""
    }

    private fun getInputValueAtPositionInLine(lineNum: Int, x: Float, y: Float): String {
        return if (format.getNumOfColumnsInLine(lineNum) == 0) {
            getInputValueAtCodeBlockPosition(childBlocks[lineNum], layout, x - layout.emptyLineWidth - layout.elementPaddingHorizontal, y - layout.elementPaddingVertical)
        } else {
            var currentX = layout.innerPaddingHorizontal.toFloat()
            if (x >= layout.innerPaddingHorizontal) {
                for (i in 0 until format.getNumOfColumnsInLine(lineNum)) {
                    val currentElementWidth = getCodeLineElementWidth(lineNum, i)
                    if (x <= currentX + currentElementWidth) {
                        val childElement = childElements[lineNum]!![i]
                        return if (childElement == null) {
                            val elementStartY = codeLineHeights[lineNum] / 2 - getCodeLineElementHeight(lineNum, i) / 2
                            val elementEndY = codeLineHeights[lineNum] / 2 + getCodeLineElementHeight(lineNum, i) / 2
                            if (y in elementStartY..elementEndY) {
                                childValues[lineNum][i]
                            } else {
                                ""
                            }
                        } else {
                            val elementStartX = currentX + layout.elementPaddingHorizontal
                            val elementStartY = codeLineHeights[lineNum] / 2 - getCodeLineElementHeight(lineNum, i) / 2 + layout.elementPaddingVertical
                            childElement.getInputValueAtPosition(x - elementStartX, y - elementStartY)
                        }
                    }
                    currentX += currentElementWidth + layout.innerPaddingHorizontal
                    if (x <= currentX) {
                        return ""
                    }
                }
            }
            ""
        }
    }

    private fun setInputValueAtPosition(value: String, x: Float, y: Float) {
        if (y !in 0f..height || x !in 0f..width) {
            return
        }

        var lineY = 0f
        for (i in 0..format.numOfLines) {
            if (lineY + codeLineHeights[i] >= y) {
                setInputValueAtPositionInLine(i, value, x, y - lineY)
                return
            }
            lineY += codeLineHeights[i]
        }
    }

    private fun setInputValueAtPositionInLine(lineNum: Int, value: String, x: Float, y: Float) {
        if (format.getNumOfColumnsInLine(lineNum) == 0) {
            setInputValueAtCodeBlockPosition(childBlocks[lineNum], layout, value, x - layout.emptyLineWidth - layout.elementPaddingHorizontal, y - layout.elementPaddingVertical)
        } else {
            var currentX = layout.innerPaddingHorizontal.toFloat()
            if (x >= layout.innerPaddingHorizontal) {
                for (i in 0..format.getNumOfColumnsInLine(lineNum)) {
                    val currentElementWidth = getCodeLineElementWidth(lineNum, i)
                    if (x <= currentX + currentElementWidth) {
                        val childElement = childElements[lineNum]!![i]
                        if (childElement == null) {
                            val elementStartY = codeLineHeights[lineNum] / 2 - getCodeLineElementHeight(lineNum, i) / 2
                            val elementEndY = codeLineHeights[lineNum] / 2 + getCodeLineElementHeight(lineNum, i) / 2
                            if (y in elementStartY..elementEndY) {
                                childValues[lineNum].put(i, value)
                                recalculateSize()
                                return
                            } else {
                                return
                            }
                        } else {
                            val elementStartX = currentX + layout.elementPaddingHorizontal
                            val elementStartY = codeLineHeights[lineNum] / 2 - getCodeLineElementHeight(lineNum, i) / 2 + layout.elementPaddingVertical
                            childElement.setInputValueAtPosition(value, x - elementStartX, y - elementStartY)
                            return
                        }
                    }
                    currentX += currentElementWidth + layout.innerPaddingHorizontal
                    if (x <= currentX) {
                        return
                    }
                }
            }
        }
    }

    override fun recalculateSize(): Boolean {
        val oldWidth = width
        val oldHeight = height
        width = 0f
        height = 0f
        for (i in 0..format.numOfLines) {
            if (format.getNumOfColumnsInLine(i) == 0) {
                codeLineWidths[i] = calculateCodeBlockWidth(childBlocks[i]) + layout.elementPaddingHorizontal + layout.emptyLineWidth
                codeLineHeights[i] = calculateCodeBlockHeight(childBlocks[i], layout) + 2 * layout.elementPaddingVertical
                width = max(width, codeLineWidths[i])
                height += codeLineHeights[i]
            } else {
                var lineHeight = 0f
                var lineWidth = layout.innerPaddingHorizontal.toFloat()
                for (j in 0..format.getNumOfColumnsInLine(i)) {
                    lineHeight = max(lineHeight, getCodeLineElementHeight(i, j) + 2 * layout.innerPaddingVertical)
                    lineWidth += getCodeLineElementWidth(i, j) + layout.innerPaddingHorizontal
                }
                codeLineWidths[i] = lineWidth
                codeLineHeights[i] = lineHeight
                height += codeLineHeights[i]
                width = max(width, lineWidth)
            }
        }

        //Check if the last "line" contains a code-block; if so, add an additional, empty, line, closing the code-block
        if (format.getNumOfColumnsInLine(format.numOfLines - 1) == 0) {
            height += textHeight + 2 * layout.innerPaddingVertical.toFloat()
        }

        //Tell the parent to recalculate
        if (parent != null) {
            parent!!.recalculateSize()
        }

        //Check if the width or height changed
        return oldWidth != width || oldHeight != height
    }

    val simpleDrawingWidth: Float
        get() {
            val text = simpleDrawingText
            return layout.textPaint.measureText(text) + 2 * layout.innerPaddingHorizontal
        }

    val simpleDrawingHeight: Float
        get() = (textHeight + 2 * layout.innerPaddingVertical).toFloat()

    fun drawSimple(canvas: Canvas, top: Float, left: Float) {
        val text = simpleDrawingText
        val width = simpleDrawingWidth
        val height = simpleDrawingHeight
        drawRect(canvas, left, top, left + width, top + height, layout.getElementPaint(format))
        canvas.drawText(text, left + layout.innerPaddingHorizontal, top + height / 2 + layout.textPaint.fontMetrics.bottom, layout.textPaint)
    }

    private val simpleDrawingText: String
        get() {
            var text = format.getName()
            var hasContent = false
            for (i in 0..format.numOfLines) {
                if (childBlocks[i] != null && childBlocks[i]!!.size > 0) {
                    hasContent = true
                    break
                }
                if (childElements[i] != null && childElements[i]!!.size() > 0) {
                    hasContent = true
                    break
                }
                val values = childValues[i]
                if (values != null) {
                    for (j in 0..values.size()) {
                        val childVal = values[values.keyAt(j)]
                        if (childVal != null && childVal != "") {
                            hasContent = true
                            break
                        }
                    }
                }
            }
            if (hasContent) {
                text += " ..."
            }
            return text
        }

    //Draws the CodeElement recursively (calling the draw functions on all it's children)
    fun draw(canvas: Canvas, top: Float, left: Float) { //Don't draw element, if it won't be within the screen
        if (top + height >= 0 && left + width >= 0 && top <= canvas.height && left <= canvas.width) { //Draw a line along the left side of the code block, connecting all the lines, so there are no gaps.
            drawRect(canvas, left, top, left + layout.emptyLineWidth, top + height, layout.getElementPaint(format))
            //Draw main body of the code-element, and draw all it's children
            var lineStartHeight = top
            for (i in 0 until format.numOfLines) {
                drawCodeLine(canvas, i, lineStartHeight, left)
                lineStartHeight += codeLineHeights[i]
            }
            //Check if the last "line" contains a code-block; if so, add an additional, empty, line, closing the code-block
            if (format.getNumOfColumnsInLine(format.numOfLines - 1) == 0) {
                drawRect(canvas, left, top + height - (textHeight + 2 * layout.innerPaddingVertical), left + layout.endLineWidth, top + height, layout.getElementPaint(format))
            }
        }
    }

    private fun drawCodeLine(canvas: Canvas, lineIndex: Int, top: Float, left: Float) {
        val numOfColumnsInLine = format.getNumOfColumnsInLine(lineIndex)
        //Checks if this "line" contains a code-block, or is a normal line
        if (numOfColumnsInLine == 0) {
            drawCodeBlock(canvas, childBlocks[lineIndex], top + layout.elementPaddingVertical, left + layout.emptyLineWidth + layout.elementPaddingHorizontal, layout.elementPaddingVertical.toFloat())
        } else { //Draw background
            drawRect(canvas, left, top, left + codeLineWidths[lineIndex], top + codeLineHeights[lineIndex], layout.getElementPaint(format))
            var elementStartWidth = left + layout.innerPaddingHorizontal
            for (i in 0 until numOfColumnsInLine) {
                val elementWidth = getCodeLineElementWidth(lineIndex, i)
                drawCodeElement(canvas, lineIndex, i, top, elementStartWidth)
                elementStartWidth += elementWidth + layout.innerPaddingHorizontal
            }
        }
    }

    private fun drawCodeElement(canvas: Canvas, lineIndex: Int, elementIndex: Int, top: Float, left: Float) {
        val elementWidth = getCodeLineElementWidth(lineIndex, elementIndex)
        val elementHeight = getCodeLineElementHeight(lineIndex, elementIndex)
        val element: CodeElement?
        when (format.getElementType(lineIndex, elementIndex)) {
            CodeFormat.ElementType.TEXT -> canvas.drawText(format.getTextAtPosition(lineIndex, elementIndex), left, top + codeLineHeights[lineIndex] / 2 + layout.textPaint.fontMetrics.bottom, layout.textPaint)
            CodeFormat.ElementType.CODE -> {
                val paint: Paint = when {
                    format.isElementInput(lineIndex, elementIndex) -> {
                        layout.getInputFieldPaint(format)
                    }
                    format.isElementOutput(lineIndex, elementIndex) -> {
                        layout.getOutputFieldPaint(format)
                    }
                    else -> {
                        layout.getCodeFieldPaint(format)
                    }
                }

                drawRect(canvas, left, top + codeLineHeights[lineIndex] / 2 - elementHeight / 2, left + elementWidth, top + codeLineHeights[lineIndex] / 2 + elementHeight / 2, paint)

                element = childElements[lineIndex]!![elementIndex]
                if (element != null) {
                    element.draw(canvas, top + codeLineHeights[lineIndex] / 2 - elementHeight / 2 + layout.elementPaddingVertical, left + layout.elementPaddingHorizontal)
                } else {
                    val value = childValues[lineIndex][elementIndex]
                    if (value != null && value != "") {
                        canvas.drawText(value, left + layout.innerPaddingHorizontal, top + codeLineHeights[lineIndex] / 2 + layout.textPaint.fontMetrics.bottom, layout.textPaint)
                    }
                }
            }
        }
    }

    private fun drawRect(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float, paint: Paint) {
        if (Build.VERSION.SDK_INT >= 21) {
            canvas.drawRoundRect(startX, startY, endX, endY, 10f, 10f, paint)
        } else {
            canvas.drawRect(startX, startY, endX, endY, paint)
        }
    }

    private fun getCodeLineElementHeight(lineIndex: Int, elementIndex: Int): Float {
        return if (format.getElementType(lineIndex, elementIndex) === CodeFormat.ElementType.TEXT) {
            textHeight.toFloat()
        } else {
            val element = childElements[lineIndex]!![elementIndex]
            if (element != null) {
                element.height + 2 * layout.elementPaddingVertical
            } else {
                val value = childValues[lineIndex][elementIndex]
                if (value == null || value == "") {
                    layout.emptyFieldHeight.toFloat()
                } else {
                    (textHeight + 2 * layout.innerPaddingVertical).toFloat()
                }
            }
        }
    }

    private fun getCodeLineElementWidth(lineIndex: Int, elementIndex: Int): Float {
        return if (format.getElementType(lineIndex, elementIndex) === CodeFormat.ElementType.TEXT) {
            layout.textPaint.measureText(format.getTextAtPosition(lineIndex, elementIndex))
        } else {
            val element = childElements[lineIndex]!![elementIndex]
            if (element != null) {
                element.width + 2 * layout.elementPaddingHorizontal
            } else {
                val value = childValues[lineIndex][elementIndex]
                if (value == null || value == "") {
                    layout.emptyFieldWidth.toFloat()
                } else {
                    layout.textPaint.measureText(value) + 2 * layout.innerPaddingHorizontal
                }
            }
        }
    }

    private val textHeight: Int
        get() {
            val fm = layout.textPaint.fontMetrics
            return (fm.bottom - fm.top + fm.leading).roundToInt()
        }

    private fun calculateNumOfChildBlocks(): Int {
        var childBlocks = 0
        for (i in 0 until format.numOfLines) {
            if (format.getNumOfColumnsInLine(i) == 0) {
                childBlocks++
            }
        }
        return childBlocks
    }

    private enum class ElementEntryResult {
        WAS_ADDED, IS_ABOVE, IS_BELOW
    }

    val codeFormatId: String
        get() = format.id

    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        internal fun addCodeElementsToCodeBlockFromJSON(jsonArray: JSONArray?, codeBlock: MutableList<CodeElement>?, parent: CodeContainer, layout: CodeLayout) {
            if (jsonArray != null && codeBlock != null) {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    codeBlock.add(CodeElement(obj, parent, layout))
                }
            }

            parent.recalculateSize()
        }

        @JvmStatic
        @Throws(JSONException::class)
        internal fun getJSONArrayFromCodeBlock(codeBlock: List<CodeElement>?): JSONArray {
            val jsonArray = JSONArray()
            if (codeBlock != null) {
                for (element in codeBlock) {
                    jsonArray.put(element.toJSONObject())
                }
            }
            return jsonArray
        }

        @JvmStatic
        internal fun drawCodeBlock(canvas: Canvas, codeBlock: List<CodeElement>?, top: Float, left: Float, elementPaddingVertical: Float) {
            if (codeBlock == null || left > canvas.width) {
                return
            }
            var currentTop = top
            for (element in codeBlock) {
                if (currentTop > canvas.height) {
                    break
                }
                if (currentTop + element.height >= 0 && left + element.width >= 0) {
                    element.draw(canvas, currentTop, left)
                }
                currentTop += element.height + elementPaddingVertical
            }
        }

        @JvmStatic
        internal fun removeElementFromCodeBlockPosition(codeBlock: MutableList<CodeElement>?, codeBlockLayout: CodeLayout, parent: CodeContainer, x: Float, y: Float): CodeElement? {
            if (codeBlock == null || codeBlock.isEmpty() || x < 0 || y < 0) {
                return null
            }
            var height = 0f
            for (i in codeBlock.indices) {
                val element = codeBlock[i]
                height += element.height
                if (height >= y) {
                    return if (element.width >= x) {
                        val removedElement = element.removeElementFromPosition(x, y - (height - element.height))
                        if (removedElement === element) {
                            codeBlock.removeAt(i)
                            parent.recalculateSize()
                        }
                        if (removedElement != null) {
                            removedElement.parent = null
                        }
                        removedElement
                    } else {
                        null
                    }
                }
                height += codeBlockLayout.elementPaddingVertical.toFloat()
                if (height >= y) {
                    return null
                }
            }
            return null
        }

        @JvmStatic
        internal fun addElementToCodeBlockPosition(addedElement: CodeElement?, codeBlock: MutableList<CodeElement>?, codeBlockLayout: CodeLayout, parent: CodeContainer, x: Float, y: Float): Boolean {
            if (codeBlock == null || addedElement == null) {
                return false
            }
            var height = 0f
            for (i in codeBlock.indices) {
                val element = codeBlock[i]
                val result = element.addElementAtPosition(addedElement, x, y - height)
                when (result) {
                    ElementEntryResult.IS_ABOVE -> {
                        addedElement.parent = parent
                        codeBlock.add(i, addedElement)
                        parent.recalculateSize()
                        return true
                    }
                    ElementEntryResult.WAS_ADDED -> return true
                    ElementEntryResult.IS_BELOW -> {
                    }
                    else -> return false
                }
                height += element.height + codeBlockLayout.elementPaddingVertical
            }
            addedElement.parent = parent
            codeBlock.add(addedElement)
            parent.recalculateSize()
            return true
        }

        @JvmStatic
        internal fun getInputTypeAtCodeBlockPosition(codeBlock: List<CodeElement>?, codeBlockLayout: CodeLayout, x: Float, y: Float): ValueType {
            if (codeBlock == null || codeBlock.isEmpty() || x < 0 || y < 0) {
                return ValueType.NONE
            }
            var height = 0f
            for (i in codeBlock.indices) {
                val element = codeBlock[i]
                height += element.height
                if (height >= y) {
                    return if (element.width >= x) {
                        element.getInputTypeAtPosition(x, y - (height - element.height))
                    } else {
                        ValueType.NONE
                    }
                }
                height += codeBlockLayout.elementPaddingVertical.toFloat()
                if (height >= y) {
                    return ValueType.NONE
                }
            }
            return ValueType.NONE
        }

        @JvmStatic
        internal fun getInputValueAtCodeBlockPosition(codeBlock: List<CodeElement>?, codeBlockLayout: CodeLayout, x: Float, y: Float): String {
            if (codeBlock == null || codeBlock.isEmpty() || x < 0 || y < 0) {
                return ""
            }
            var height = 0f
            for (i in codeBlock.indices) {
                val element = codeBlock[i]
                height += element.height
                if (height >= y) {
                    return if (element.width >= x) {
                        element.getInputValueAtPosition(x, y - (height - element.height))
                    } else {
                        ""
                    }
                }
                height += codeBlockLayout.elementPaddingVertical.toFloat()
                if (height >= y) {
                    return ""
                }
            }
            return ""
        }

        @JvmStatic
        internal fun setInputValueAtCodeBlockPosition(codeBlock: List<CodeElement>?, codeBlockLayout: CodeLayout, value: String, x: Float, y: Float) {
            if (codeBlock == null || codeBlock.isEmpty() || x < 0 || y < 0) {
                return
            }
            var height = 0f
            for (i in codeBlock.indices) {
                val element = codeBlock[i]
                height += element.height
                if (height >= y) {
                    if (element.width >= x) {
                        element.setInputValueAtPosition(value, x, y - (height - element.height))
                    }
                    return
                }
                height += codeBlockLayout.elementPaddingVertical.toFloat()
                if (height >= y) {
                    return
                }
            }
        }

        @JvmStatic
        internal fun calculateCodeBlockWidth(codeBlock: List<CodeElement>?): Float {
            if (codeBlock == null) {
                return 0f
            }
            var width = 0f
            for (element in codeBlock) {
                width = max(width, element.width)
            }
            return width
        }

        @JvmStatic
        internal fun calculateCodeBlockHeight(codeBlock: List<CodeElement>?, codeBlockLayout: CodeLayout): Float {
            if (codeBlock == null || codeBlock.isEmpty()) {
                return 0f
            }
            var height = 0f
            for (element in codeBlock) {
                height += element.height
            }
            height += codeBlockLayout.elementPaddingVertical * (codeBlock.size - 1).toFloat()
            return height
        }
    }
}