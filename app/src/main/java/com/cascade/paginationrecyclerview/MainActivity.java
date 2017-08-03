package com.cascade.paginationrecyclerview;

import android.app.Activity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.widget.Toast;

import com.google.gson.GsonBuilder;

import java.util.List;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends Activity implements PaginationRecyclerView.RequestListener {
    private SwipeRefreshLayout swipeRefreshLayout;
    private PaginationRecyclerView<Comment> paginationRecyclerView;

    private Service service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeNetwork();
        initializeViews();
    }

    @Override
    public void onRequestStarted() {
        swipeRefreshLayout.setRefreshing(true);
    }

    @Override
    public void onRequestError(String message) {
        swipeRefreshLayout.setRefreshing(false);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestFinished() {
        swipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void onFinalPageReached() {
        swipeRefreshLayout.setEnabled(false);
        Toast.makeText(this, "Final Page Reached", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void shouldCallNewRequest(int pageIndex) {
        Call<List<Comment>> call = service.getComments(pageIndex);
        paginationRecyclerView.updateRequest(call);
    }

    private void initializeNetwork() {
        Converter.Factory factory = GsonConverterFactory.create(new GsonBuilder().create());

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://jsonplaceholder.typicode.com")
                .client(new OkHttpClient())
                .addConverterFactory(factory)
                .build();

        service = retrofit.create(Service.class);
    }

    private void initializeViews() {
        swipeRefreshLayout = findViewById(R.id.srl_main);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        paginationRecyclerView = findViewById(R.id.prv_main);
        paginationRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        paginationRecyclerView.setPageIndex(1);
        paginationRecyclerView.initialize(new CommentAdapter(this, null), this, true);
    }
}