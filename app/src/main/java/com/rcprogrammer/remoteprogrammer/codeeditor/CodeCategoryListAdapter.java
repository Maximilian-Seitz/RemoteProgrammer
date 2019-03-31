package com.rcprogrammer.remoteprogrammer.codeeditor;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import com.rcprogrammer.remoteprogrammer.R;

import java.util.List;

public class CodeCategoryListAdapter extends BaseExpandableListAdapter {

    private Context context;

    private SparseArray<String> categories;

    private SparseArray<List<String>> valueNames;
    private SparseArray<List<String>> valueIDs;


    public CodeCategoryListAdapter(Context context, SparseArray<String> categories, SparseArray<List<String>> valueNames, SparseArray<List<String>> valueIDs) {
        this.context = context;

        this.categories = categories;

        this.valueNames = valueNames;
        this.valueIDs = valueIDs;
    }


    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return valueNames.get(categories.keyAt(groupPosition)).get(childPosition);
    }

    public String getChildKey(int groupPosition, int childPosition) {
        return valueIDs.get(categories.keyAt(groupPosition)).get(childPosition);
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return valueIDs.get(categories.keyAt(groupPosition)).size();
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        final String childText = (String) getChild(groupPosition, childPosition);

        if (convertView == null) {
            LayoutInflater infalInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = infalInflater.inflate(R.layout.list_item_code_element, null);
        }

        TextView txtListChild = convertView.findViewById(R.id.txtElementName);

        txtListChild.setText(childText);
        return convertView;
    }


    @Override
    public Object getGroup(int groupPosition) {
        return categories.get(categories.keyAt(groupPosition));
    }

    @Override
    public int getGroupCount() {
        return categories.size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        return categories.keyAt(groupPosition);
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        String headerTitle = (String) getGroup(groupPosition);
        if (convertView == null) {
            LayoutInflater infalInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = infalInflater.inflate(R.layout.list_item_code_category, null);
        }

        TextView lblListHeader = convertView.findViewById(R.id.txtCategoryName);

        lblListHeader.setTypeface(null, Typeface.BOLD);
        lblListHeader.setText(headerTitle);

        return convertView;
    }


    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

}
