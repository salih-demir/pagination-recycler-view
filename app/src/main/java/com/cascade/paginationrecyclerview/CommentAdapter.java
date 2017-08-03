package com.cascade.paginationrecyclerview;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Salih Demir on 2.08.2017.
 */

class CommentAdapter extends PaginationRecyclerView.PaginationAdapter<Comment> {
    private Context context;

    CommentAdapter(Context context, List<Comment> items) {
        super(items);
        this.context = context;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);
        return new RecyclerView.ViewHolder(itemView) {
        };
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Comment comment = getItems().get(position);

        TextView textViewCommentName = holder.itemView.findViewById(R.id.tv_comment_name);
        textViewCommentName.setText(comment.getName());
    }

    @Override
    public int getItemCount() {
        return getItems().size();
    }
}