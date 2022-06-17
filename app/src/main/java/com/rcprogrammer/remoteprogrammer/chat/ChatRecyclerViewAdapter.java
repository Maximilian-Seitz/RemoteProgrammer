package com.rcprogrammer.remoteprogrammer.chat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.rcprogrammer.remoteprogrammer.R;

import java.util.ArrayList;
import java.util.List;

public class ChatRecyclerViewAdapter extends RecyclerView.Adapter<ChatRecyclerViewAdapter.ViewHolder> {

    private static class ChatEntry{
        String text = "";
        boolean isIncoming = false;

        ChatEntry(String text, boolean isIncoming){
            this.text = text;
            this.isIncoming = isIncoming;
        }
    }

    private Context context;
    private List<ChatEntry> chatData = new ArrayList<>();

    public ChatRecyclerViewAdapter(Context context) {
        this.context = context;
    }

    private void addMessage(String text, boolean isIncoming) {
        chatData.add(0, new ChatEntry(text, isIncoming));
        notifyDataSetChanged();
    }

    public void addOutgoingMessage(String text){
        addMessage(text, false);
    }

    public void addIncomingMessage(String text){
        addMessage(text, true);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater.from(parent.getContext());
        View container = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_chat_bubble, parent, false);
        return new ViewHolder(container);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ChatEntry chatEntry = chatData.get(position);

        holder.textView.setText(chatEntry.text);

        if(chatEntry.isIncoming){
            holder.layout.setGravity(Gravity.START);

            Drawable chatBubble = context.getResources().getDrawable(R.drawable.speech_bubble_out);
            holder.textView.setBackground(chatBubble);
        } else {
            holder.layout.setGravity(Gravity.END);

            Drawable chatBubble = context.getResources().getDrawable(R.drawable.speech_bubble_in);
            holder.textView.setBackground(chatBubble);
        }
    }

    @Override
    public int getItemCount() {
        return chatData.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layout;
        TextView textView;

        ViewHolder(View v) {
            super(v);

            layout = v.findViewById(R.id.layoutChatBubble);
            textView = v.findViewById(R.id.txtChatBubble);
        }
    }
}
