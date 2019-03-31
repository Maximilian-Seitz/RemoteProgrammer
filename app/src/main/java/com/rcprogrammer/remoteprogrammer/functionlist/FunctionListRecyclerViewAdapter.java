package com.rcprogrammer.remoteprogrammer.functionlist;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.rcprogrammer.remoteprogrammer.R;

import java.lang.ref.WeakReference;

public class FunctionListRecyclerViewAdapter extends RecyclerView.Adapter<FunctionListRecyclerViewAdapter.ViewHolder> {
    private FunctionListFileManager mDataset;
    private View movedView;

    private final FunctionListActivity.ClickListener functionItemClickListener;

    class ViewHolder extends RecyclerView.ViewHolder {
        private WeakReference<FunctionListActivity.ClickListener> functionItemClickListenerReference;

        TextView mTextView;
        ImageButton btnDelete;

        ViewHolder(View v, FunctionListActivity.ClickListener functionItemClickListener) {
            super(v);

            functionItemClickListenerReference = new WeakReference<FunctionListActivity.ClickListener>(functionItemClickListener);

            View contentView = v.findViewById(R.id.contentView);
            mTextView = (TextView) contentView.findViewById(R.id.txtItemDesc);

            btnDelete = (ImageButton) v.findViewById(R.id.btnDelete);
            btnDelete.setX(-1000.f);


            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    functionItemClickListenerReference.get().onPositionClicked(getAdapterPosition());
                }
            });

            v.setOnTouchListener(new View.OnTouchListener() {
                private float xPosStartTouch = 0;
                private float xPosStartDrag = 0;
                private float maxOffset;
                private float minDiff = 50;

                public boolean onTouch(View v, MotionEvent event) {
                    View contentView = v.findViewById(R.id.contentView);
                    int action = event.getActionMasked();

                    ImageButton btnDelete = (ImageButton) v.findViewById(R.id.btnDelete);
                    maxOffset = btnDelete.getWidth();

                    if(action == MotionEvent.ACTION_DOWN) {
                        xPosStartTouch = event.getRawX();
                        xPosStartDrag = -1;
                    } else if(action == MotionEvent.ACTION_MOVE){
                        if ((event.getRawX() - xPosStartTouch) < -minDiff || (event.getRawX() - xPosStartTouch) > minDiff) {
                            if (xPosStartDrag < 0) {
                                xPosStartDrag = event.getRawX();
                            }

                            if (v != movedView) {
                                if (movedView != null) {
                                    movedView.findViewById(R.id.contentView).setX(0);
                                    movedView.findViewById(R.id.btnDelete).setX(-1000.f);
                                }
                                movedView = v;
                            }

                            float newX = contentView.getX() + (event.getRawX() - xPosStartDrag);

                            if (newX < -maxOffset)
                                newX = -maxOffset;

                            if (newX > 0)
                                newX = 0;

                            contentView.setX(newX);
                            btnDelete.setX(newX + contentView.getWidth());
                        }
                    } else if(action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_OUTSIDE){
                        if ((event.getRawX() - xPosStartDrag) < 0) {
                            contentView.setX(-maxOffset);
                            btnDelete.setX(contentView.getWidth() - maxOffset);
                        } else {
                            contentView.setX(0);
                            btnDelete.setX(contentView.getWidth());
                        }
                    }

                    return false;
                }
            });
        }
    }

    FunctionListRecyclerViewAdapter(FunctionListFileManager myDataset, FunctionListActivity.ClickListener functionItemClickListener) {
        mDataset = myDataset;
        this.functionItemClickListener = functionItemClickListener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater.from(parent.getContext());
        View container = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_function, parent, false);
        ViewHolder vh = new ViewHolder(container, functionItemClickListener);
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.mTextView.setText(mDataset.getNthFunctionName(position));

        holder.btnDelete.setOnClickListener(new DeleteButtonOnClickListener(this, mDataset, position));
    }

    @Override
    public int getItemCount() {
        return mDataset.getNumOfFunctions();
    }


    private class DeleteButtonOnClickListener implements View.OnClickListener {
        FunctionListRecyclerViewAdapter parent;
        FunctionListFileManager data;
        private int pos;

        DeleteButtonOnClickListener(FunctionListRecyclerViewAdapter parent, FunctionListFileManager data, int pos){
            this.parent = parent;
            this.data = data;
            this.pos = pos;
        }

        @Override
        public void onClick(View v) {
            if (movedView != null) {
                movedView.findViewById(R.id.contentView).setX(0);
                movedView.findViewById(R.id.btnDelete).setX(-1000.f);
            }
            movedView = null;

            data.removeNthFunction(pos);
            parent.notifyDataSetChanged();
        }
    }

}
