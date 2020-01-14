package com.rcprogrammer.remoteprogrammer.codeeditor.codeview

import android.content.Context
import android.support.v4.util.ArrayMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.util.*

class CodeFormat private constructor(
        val id: String,
        private var isDeprecated: Boolean,
        private val name: String,
        val category: Int,
        private val returnsType: ValueType,
        private val takesType: ValueType,
        private val lines: Array<Array<Element>>,
        private val description: String?,
        private val returnValueDescription: String?,
        private val notes: String?,
        private val parameterDescriptions: Array<ParameterDescription>?
) {
    fun getName(): String {
        return localizeString(name)
    }

    fun hasInfo(): Boolean {
        return parameterDescriptions != null && parameterDescriptions.isNotEmpty() ||
                description != null && description != "" ||
                returnValueDescription != null && returnValueDescription != "" ||
                notes != null && notes != ""
    }

    fun getDescription(): String? {
        return description?.let { localizeString(it) }
    }

    fun getReturnValueDescription(): String? {
        return returnValueDescription?.let { localizeString(it) }
    }

    fun getNotes(): String? {
        return notes?.let { localizeString(it) }
    }

    fun getParameterDescriptions(): Array<ParameterDescription?>? {
        return if (parameterDescriptions != null) {
            val localizedDescriptions = arrayOfNulls<ParameterDescription>(parameterDescriptions.size)
            for (i in parameterDescriptions.indices) {
                val type = localizeString(parameterDescriptions[i].type)
                val desc = localizeString(parameterDescriptions[i].description)
                localizedDescriptions[i] = ParameterDescription(type, desc)
            }
            localizedDescriptions
        } else {
            null
        }
    }

    val numOfLines: Int
        get() = lines.size

    fun getNumOfColumnsInLine(lineNum: Int): Int {
        return lines[lineNum].size
    }

    fun acceptsParameterAt(format: CodeFormat, lineNum: Int, columnNum: Int): Boolean {
        return lines[lineNum][columnNum].acceptsChild(format)
    }

    fun acceptedTypeAtPosition(lineNum: Int, columnNum: Int): ValueType {
        return lines[lineNum][columnNum].acceptedType
    }

    fun getTextAtPosition(lineNum: Int, columnNum: Int): String {
        return localizeString(lines[lineNum][columnNum].value)
    }

    fun getElementType(lineNum: Int, columnNum: Int): ElementType {
        return lines[lineNum][columnNum].type
    }

    fun isElementInput(lineNum: Int, columnNum: Int): Boolean {
        return lines[lineNum][columnNum].isInput
    }

    fun isElementOutput(lineNum: Int, columnNum: Int): Boolean {
        return lines[lineNum][columnNum].isOutput
    }

    @Throws(JSONException::class)
    private fun toJSONObject(): JSONObject {
        val newObject = JSONObject()
        newObject["id"] = id
        newObject["name"] = name
        newObject["isDeprecated"] = isDeprecated
        newObject["category"] = category
        newObject["takes"] = takesType.toString()
        newObject["returns"] = returnsType.toString()
        newObject["desc"] = description
        newObject["descReturnValue"] = returnValueDescription
        newObject["descNotes"] = notes

        val linesJSONArray = JSONArray()
        for (line in lines) {
            val lineJSONArray = JSONArray()
            for (element in line) {
                val lineElement = JSONObject()
                lineElement["type"] = element.type
                lineElement["value"] = element.value
                lineJSONArray += lineElement
            }
            linesJSONArray += lineJSONArray
        }
        newObject["lines"] = linesJSONArray

        val descParamsJSONArray = JSONArray()
        if (parameterDescriptions != null) {
            for (paramDesc in parameterDescriptions) {
                val paramDescJSONObject = JSONObject()
                paramDescJSONObject["type"] = paramDesc.type
                paramDescJSONObject["desc"] = paramDesc.description
                descParamsJSONArray += paramDescJSONObject
            }
        }
        newObject["descParams"] = descParamsJSONArray

        return newObject
    }

    private interface Element {
        val type: ElementType
        val value: String
        fun acceptsChild(format: CodeFormat): Boolean
        val acceptedType: ValueType
        val isInput: Boolean
        val isOutput: Boolean
    }

    private class TextParameter(override val value: String) : Element {
        override val type = ElementType.TEXT

        override val acceptedType = ValueType.NONE

        override fun acceptsChild(format: CodeFormat) = false

        override val isInput = false

        override val isOutput = false

    }

    private class ChildParameter(private val ioType: IOType) : Element {
        override val value = ioType.toString()

        override val type = ElementType.CODE

        override val acceptedType = when (ioType) {
            IOType.NUM_IN -> ValueType.NUM
            IOType.TEXT_IN -> ValueType.TEXT
            IOType.BOOL_IN -> ValueType.BOOL
            else -> ValueType.NONE
        }

        override fun acceptsChild(format: CodeFormat): Boolean {
            return when (ioType) {
                IOType.BOOL_IN -> format.returnsType == ValueType.BOOL
                IOType.NUM_IN -> format.returnsType == ValueType.NUM
                IOType.TEXT_IN -> format.returnsType == ValueType.TEXT || format.returnsType == ValueType.NUM || format.returnsType == ValueType.BOOL
                IOType.BOOL_OUT -> format.takesType == ValueType.BOOL || format.takesType == ValueType.TEXT
                IOType.NUM_OUT -> format.takesType == ValueType.NUM || format.takesType == ValueType.TEXT
                IOType.TEXT_OUT -> format.takesType == ValueType.TEXT
                else -> true
            }
        }

        override val isInput = when (ioType) {
            IOType.NUM_IN -> true
            IOType.TEXT_IN -> true
            IOType.BOOL_IN -> true
            else -> false
        }

        override val isOutput = when (ioType) {
            IOType.NUM_OUT -> true
            IOType.TEXT_OUT -> true
            IOType.BOOL_OUT -> true
            else -> false
        }

    }

    enum class ElementType {
        CODE, TEXT
    }

    enum class IOType {
        NUM_IN, NUM_OUT, TEXT_IN, TEXT_OUT, BOOL_IN, BOOL_OUT, NONE
    }

    enum class ValueType {
        TEXT, NUM, BOOL, NONE
    }

    class ParameterDescription(val type: String, val description: String)

    companion object {
        private var translations: JSONObject? = null
        private var language: String? = null
        private var categories: MutableList<String>? = null
        private var categoryColorHues: MutableList<Int>? = null
        private var formats: MutableMap<String, CodeFormat>? = null

        @JvmStatic
        operator fun get(id: String?): CodeFormat? {
            return if (formats != null && formats!!.containsKey(id)) {
                formats!![id]
            } else null
        }

        private fun add(id: String, isDeprecated: Boolean, name: String, category: Int, returnsType: ValueType, takesType: ValueType, lines: Array<Array<Element>>, description: String?, returnValueDescription: String?, notes: String?, parameterDescriptions: Array<ParameterDescription>?) {
            if (formats == null) {
                formats = ArrayMap()
            }
            val newFormat = CodeFormat(id, isDeprecated, name, category, returnsType, takesType, lines, description, returnValueDescription, notes, parameterDescriptions)
            formats!![id] = newFormat
        }

        @JvmStatic
        @Throws(IOException::class, JSONException::class)
        fun saveFormats(context: Context) {
            val file = File(context.filesDir.toString() + File.separator + "codeFormats.json")

            if (file.isDirectory) {
                file.delete()
            }

            if (!file.exists()) {
                file.createNewFile()
            }

            if (formats != null) {
                val jsonArray = JSONArray()

                for ((_, value) in formats!!) {
                    jsonArray += value.toJSONObject()
                }

                file.writeText(jsonArray.toString())
            }
        }

        @JvmStatic
        @Throws(IOException::class, JSONException::class)
        fun loadFormats(context: Context) { //Read file to string
            val file = File(context.filesDir.toString() + File.separator + "codeFormats.json")
            if (!file.exists() || file.isDirectory) {
                //Store file contents as CodeFormats
                fromJSONArray(JSONArray(file.readText()))
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun saveTranslations(context: Context) {
            val file = File(context.filesDir.toString() + File.separator + "codeFormatTranslations.json")
            if (file.isDirectory) {
                file.delete()
            }
            if (!file.exists()) {
                file.createNewFile()
            }
            if (translations != null) {
                val writer = FileWriter(file)
                writer.append(translations.toString())
                writer.flush()
                writer.close()
            }
        }

        @JvmStatic
        @Throws(IOException::class, JSONException::class)
        fun loadTranslations(context: Context) {
            val file = File(context.filesDir.toString() + File.separator + "codeFormatTranslations.json")
            if (!file.exists() || file.isDirectory) {
                translations = JSONObject(file.readText())
            }
        }

        @JvmStatic
        @Throws(JSONException::class)
        fun addTranslationsFromJSONObject(jsonObject: JSONObject) {
            val langIterator = jsonObject.keys()
            if (translations != null) {
                while (langIterator.hasNext()) {
                    val language = langIterator.next()
                    val translation = jsonObject.getJSONObject(language)

                    if (translations!!.has(language)) {
                        val oldTranslation = translations!!.getJSONObject(language)

                        for((wordID, word) in translation) {
                            oldTranslation[wordID] = word
                        }
                    } else {
                        translations!![language] = translation
                    }
                }
            } else {
                translations = jsonObject
            }
        }

        @JvmStatic
        val languages: List<String>
            get() {
                val languages: MutableList<String> = ArrayList()
                if (translations != null) {
                    val iter = translations!!.keys()
                    while (iter.hasNext()) {
                        val key = iter.next()
                        if (!key.equals("default", ignoreCase = true)) {
                            languages.add(key)
                        }
                    }
                }
                return languages
            }

        @JvmStatic
        fun setLanguage(newLanguage: String) {
            language = newLanguage
        }

        @JvmStatic
        @Throws(IOException::class, JSONException::class)
        fun saveCategories(context: Context) {
            val file = File(context.filesDir.toString() + File.separator + "codeFormatCategories.json")
            if (file.isDirectory) {
                file.delete()
            }
            if (!file.exists()) {
                file.createNewFile()
            }
            val catArr = JSONArray()
            for (i in categories!!.indices) {
                val catObj = JSONObject()
                catObj["name"] = categories!![i]
                catObj["hue"] = categoryColorHues!![i]
                catArr += catObj
            }
            val writer = FileWriter(file)
            writer.append(catArr.toString())
            writer.flush()
            writer.close()
        }

        @JvmStatic
        @Throws(IOException::class, JSONException::class)
        fun loadCategories(context: Context) {
            val file = File(context.filesDir.toString() + File.separator + "codeFormatCategories.json")

            if (file.exists() && !file.isDirectory) {
                addCategoriesFromJSONArray(JSONArray(file.readText()))
            }
        }

        @JvmStatic
        @Throws(JSONException::class)
        fun addCategoriesFromJSONArray(categoryArray: JSONArray) {
            if (categories == null) {
                categories = ArrayList()
            }
            if (categoryColorHues == null) {
                categoryColorHues = ArrayList()
            }
            for (i in 0 until categoryArray.length()) {
                val category = categoryArray.getJSONObject(i)
                val name = category.getString("name")
                if (categories!!.size <= i) {
                    categories!!.add(name)
                } else {
                    categories!![i] = name
                }
                val hue = category.getInt("hue")
                if (categoryColorHues!!.size <= i) {
                    categoryColorHues!!.add(hue)
                } else {
                    categoryColorHues!![i] = hue
                }
            }
        }

        @JvmStatic
        val numOfCategories: Int
            get() = if (categories != null) {
                categories!!.size
            } else 0

        @JvmStatic
        fun getCategoryName(category: Int): String {
            return if (categories != null) {
                localizeString(categories!![category])
            } else category.toString()
        }

        @JvmStatic
        fun getCategoryColorHue(category: Int): Int {
            return if (categoryColorHues != null) {
                categoryColorHues!![category]
            } else 0
        }

        @Throws(JSONException::class)
        fun fromJSONObject(jsonObject: JSONObject) {
            val id = jsonObject.getString("id")

            val name = if (jsonObject.has("name")) {
                jsonObject.getString("name")
            } else id

            val isDeprecated = if (jsonObject.has("isDeprecated")) {
                jsonObject.getBoolean("isDeprecated")
            } else false

            val category = jsonObject.getInt("category")

            val takesType = if (jsonObject.has("takes")) {
                try {
                    ValueType.valueOf(jsonObject.getString("takes").toUpperCase())
                } catch (e: IllegalArgumentException) {
                    ValueType.NONE
                }
            } else ValueType.NONE

            val returnsType = if (jsonObject.has("returns")) {
                try {
                    ValueType.valueOf(jsonObject.getString("returns").toUpperCase())
                } catch (e: IllegalArgumentException) {
                    ValueType.NONE
                }
            } else ValueType.NONE

            val linesJSONArray = jsonObject.getJSONArray("lines")

            val lines: Array<Array<Element>> = Array(linesJSONArray.length()) { i ->
                val lineJSONArray = linesJSONArray.getJSONArray(i)

                Array(lineJSONArray.length()) { j ->
                    val lineElement = lineJSONArray.getJSONObject(j)
                    val elementType = lineElement.getString("type")

                    when {
                        elementType.equals("text", ignoreCase = true) -> {
                            val text = if (lineElement.has("value")) {
                                lineElement.getString("value")
                            } else ""

                            TextParameter(text)
                        }
                        elementType.equals("code", ignoreCase = true) -> {
                            val type = if (lineElement.has("value")) {
                                val typeStr = lineElement.getString("value")

                                try {
                                    IOType.valueOf(typeStr.toUpperCase())
                                } catch (e: IllegalArgumentException) {
                                    IOType.NONE
                                }
                            } else IOType.NONE

                            ChildParameter(type)
                        }
                        else -> {
                            throw JSONException("Invalid format json.")
                        }
                    }
                }
            }

            val desc = if (jsonObject.has("desc")) {
                jsonObject.getString("desc")
            } else ""

            val descReturnValue = if (jsonObject.has("descReturnValue")) {
                jsonObject.getString("descReturnValue")
            } else ""

            val descNotes = if (jsonObject.has("descNotes")) {
                jsonObject.getString("descNotes")
            } else ""

            val parameterDescriptions = if (jsonObject.has("descParams")) {
                val paramDescJsonArray = jsonObject.getJSONArray("descParams")

                Array(paramDescJsonArray.length()) { i ->
                    val paramDesc = paramDescJsonArray.getJSONObject(i)

                    ParameterDescription(
                            if (paramDesc.has("type")) {
                                paramDesc.getString("type")
                            } else "",
                            if (paramDesc.has("desc")) {
                                paramDesc.getString("desc")
                            } else ""
                    )
                }
            } else null

            add(id, isDeprecated, name, category, returnsType, takesType, lines, desc, descReturnValue, descNotes, parameterDescriptions)
        }

        @JvmStatic
        @Throws(JSONException::class)
        fun fromJSONArray(jsonArray: JSONArray) {
            for (i in 0 until jsonArray.length()) {
                fromJSONObject(jsonArray.getJSONObject(i))
            }
        }

        @JvmStatic
        fun setAllFormatsDeprecated() {
            if (formats != null) {
                for ((_, value) in formats!!) {
                    value.isDeprecated = true
                }
            }
        }

        @JvmStatic
        fun getAllFormats(onlyNonDeprecated: Boolean): List<CodeFormat?> {
            val validFormats: MutableList<CodeFormat?> = ArrayList()
            if (formats != null) {
                for ((_, value) in formats!!) {
                    if (!onlyNonDeprecated || !value.isDeprecated) {
                        validFormats.add(value)
                    }
                }
            }
            return validFormats
        }

        private fun localizeString(str: String): String {
            return if (str.startsWith("@")) {
                val newStr = str.substring(1)
                try {
                    var languageJSON: JSONObject? = if (translations!!.has(language)) {
                        translations!!.getJSONObject(language)
                    } else null

                    if (languageJSON != null && languageJSON.has(newStr)) {
                        languageJSON.getString(newStr)
                    } else {
                        languageJSON = translations!!.getJSONObject("default")
                        if (languageJSON != null && languageJSON.has(newStr)) {
                            languageJSON.getString(newStr)
                        } else {
                            newStr
                        }
                    }
                } catch (e: Exception) {
                    newStr
                }
            } else {
                str
            }
        }
    }

}