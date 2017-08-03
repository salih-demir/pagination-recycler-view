package com.cascade.paginationrecyclerview;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Created by Salih Demir on 2.08.2017.
 */

interface Service {
    @GET("/comments")
    Call<List<Comment>> getComments(@Query("postId") int pageIndex);
}