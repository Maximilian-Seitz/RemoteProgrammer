package com.rcprogrammer.remoteprogrammer.codeeditor.codeview

import org.json.JSONArray
import org.json.JSONObject

internal operator fun JSONObject.set(name: String, value: Any?) {
    this.put(name, value)
}

internal operator fun JSONArray.set(index: Int, value: Any?) {
    this.put(index, value)
}

internal operator fun JSONArray.plusAssign(value: Any?) {
    this.put(value)
}

internal operator fun JSONObject.iterator() : Iterator<Pair<String, Any?>> {
    return object : Iterator<Pair<String, Any?>> {
        val keyIterator = keys()

        override fun hasNext(): Boolean {
            return keyIterator.hasNext()
        }

        override fun next(): Pair<String, Any?> {
            val key = keyIterator.next()

            return key to this@iterator[key]
        }

    }
}
