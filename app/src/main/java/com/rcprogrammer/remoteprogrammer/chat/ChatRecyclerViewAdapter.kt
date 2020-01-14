package com.rcprogrammer.remoteprogrammer.chat

import android.content.Context
import android.graphics.drawable.Drawable
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

import com.rcprogrammer.remoteprogrammer.R

import java.util.ArrayList

class ChatRecyclerViewAdapter(private val context: Context) : RecyclerView.Adapter<ChatRecyclerViewAdapter.ViewHolder>() {
    private val chatData = ArrayList<ChatEntry>()

    private class ChatEntry internal constructor(internal var text: String, internal var isIncoming: Boolean)

    private fun addMessage(text: String, isIncoming: Boolean) {
        chatData.add(0, ChatEntry(text, isIncoming))
        notifyDataSetChanged()
    }

    fun addOutgoingMessage(text: String) {
        addMessage(text, false)
    }

    fun addIncomingMessage(text: String) {
        addMessage(text, true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        LayoutInflater.from(parent.context)
        val container = LayoutInflater.from(parent.context).inflate(R.layout.list_item_chat_bubble, parent, false)
        return ViewHolder(container)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chatEntry = chatData[position]

        holder.textView.text = chatEntry.text

        if (chatEntry.isIncoming) {
            holder.layout.gravity = Gravity.START

            val chatBubble = context.resources.getDrawable(R.drawable.speech_bubble_out)
            holder.textView.background = chatBubble
        } else {
            holder.layout.gravity = Gravity.END

            val chatBubble = context.resources.getDrawable(R.drawable.speech_bubble_in)
            holder.textView.background = chatBubble
        }
    }

    override fun getItemCount(): Int {
        return chatData.size
    }

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        var layout: LinearLayout = v.findViewById(R.id.layoutChatBubble)
        var textView: TextView = v.findViewById(R.id.txtChatBubble)
    }
}
