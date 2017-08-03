package com.cascade.paginationrecyclerview;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PaginationRecyclerView<T> extends RecyclerView implements Callback<List<T>> {
    //region CONSTRUCTS
    private enum ErrorMessages {
        ADAPTER_CLASS_ERROR("Only " + PaginationAdapter.class.getSimpleName() + " is accepted!"),
        DEFAULT_REQUEST_ERROR("Network request failed."),
        LAYOUT_MANAGER_ERROR("Only linear layout manager is accepted!"),
        SCROLL_NOT_AVAILABLE_ERROR("Added items does not fill the RecycleView! Increase the item height or count. Pagination is not available."),
        SCROLLING_DISABLED("Scrolling disabled for recycler view, enable it to use pagination.");

        final String message;

        ErrorMessages(String message) {
            this.message = message;
        }
    }

    private enum ScrollState {
        MIN,
        IN_BETWEEN,
        MAX
    }

    interface RequestListener {
        void onRequestStarted();

        void onRequestError(String message);

        void onRequestFinished();

        void onFinalPageReached();

        void shouldCallNewRequest(int pageIndex);
    }

    abstract static class PaginationAdapter<T> extends Adapter<RecyclerView.ViewHolder> {
        private final List<T> items;

        PaginationAdapter(List<T> items) {
            if (items == null)
                items = new ArrayList<>();

            this.items = items;
        }

        private void addItems(List<T> newItems) {
            this.items.addAll(newItems);
            notifyDataSetChanged();

            //to enable auto scroll, use:
            /*if (items.size() == 0) {
                this.items.addAll(newItems);
                notifyDataSetChanged();
            } else {
                int oldSize = items.size();
                int newSize = oldSize + newItems.size();
                this.items.addAll(newItems);
                notifyItemRangeInserted(oldSize, newSize);
            }*/
        }

        private void resetAdapter() {
            items.clear();
            notifyDataSetChanged();
        }

        List<T> getItems() {
            return items;
        }
    }
    //endregion

    private static final String LOG_TAG = PaginationRecyclerView.class.getSimpleName();
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;
    private static final int DELAY_FOR_NEW_REQUEST = 150;
    private static final int DELAY_FOR_SCROLL_STATUS_CHECK = 1000;

    private boolean isSwipeActivated = true;
    private boolean isMaxPageCountSet = false;
    private boolean isAlreadyWaiting;
    private boolean sendFirstRequest;
    private long lastRequestTime;
    private int pageIndex;
    private int maxPageCount;
    private int layoutOrientation;

    private PaginationAdapter<T> paginationAdapter;
    private RequestListener requestListener;

    private Call currentCall;

    //region INITIALIZATION METHODS
    public PaginationRecyclerView(Context context) {
        super(context);
        initialize();
    }

    public PaginationRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public PaginationRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
                layoutOrientation = getLayoutManager().getOrientation();

                addSwipeListener();
                addOnScrollListener();
            }
        });
    }

    public void initialize(@NonNull PaginationAdapter<T> paginationAdapter, @NonNull RequestListener requestListener, boolean sendFirstRequest) {
        this.paginationAdapter = paginationAdapter;
        this.requestListener = requestListener;
        this.sendFirstRequest = sendFirstRequest;

        setAdapter(paginationAdapter);

        if (paginationAdapter.getItems() != null && paginationAdapter.getItems().size() > 0)
            setPageIndex(1);

        if (sendFirstRequest)
            requestListener.shouldCallNewRequest(pageIndex);
    }

    private void addOnScrollListener() {
        if (isScrollEnabled())
            addOnScrollListener(new OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);

                    handleScroll(dx, dy);
                }
            });
        else
            Log.w(PaginationRecyclerView.class.getSimpleName(), ErrorMessages.SCROLLING_DISABLED.message);
    }

    private void addSwipeListener() {
        setOnTouchListener(new OnTouchListener() {
            private final GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (e1 != null && e2 != null) {
                        float diff = 0;
                        if (layoutOrientation == LinearLayoutManager.HORIZONTAL)
                            diff = e2.getX() - e1.getX();
                        else if (layoutOrientation == LinearLayoutManager.VERTICAL)
                            diff = e2.getY() - e1.getY();

                        if (Math.abs(diff) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD)
                            if (diff < 0)
                                onSwiped();
                    }
                    return true;
                }
            });

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);

                return PaginationRecyclerView.super.onTouchEvent(event);
            }
        });
    }
    //endregion

    //region SUPER METHODS
    @Override
    public void setLayoutManager(LayoutManager layout) {
        if (!(layout instanceof LinearLayoutManager))
            throw new Error(ErrorMessages.LAYOUT_MANAGER_ERROR.message);
        super.setLayoutManager(layout);
    }

    @Override
    public LinearLayoutManager getLayoutManager() {
        return (LinearLayoutManager) super.getLayoutManager();
    }

    @Override
    public void setAdapter(Adapter adapter) {
        if (!PaginationAdapter.class.isAssignableFrom(adapter.getClass()))
            throw new Error(ErrorMessages.ADAPTER_CLASS_ERROR.message);

        super.setAdapter(adapter);
    }

    @Override
    public PaginationAdapter<T> getAdapter() {
        return paginationAdapter;
    }
    //endregion

    //region UI EVENT METHODS
    private void onScrollStatusChanged(ScrollState scrollState) {
        if (scrollState == ScrollState.MAX) {
            isSwipeActivated = true;
            handleNewActionRequest();
        } else
            isSwipeActivated = false;
    }

    private void onSwiped() {
        if (isSwipeActivated)
            handleNewActionRequest();
    }

    public void handleScroll(int scrollX, int scrollY) {
        int targetParameter = scrollX;

        if (layoutOrientation == LinearLayoutManager.HORIZONTAL)
            targetParameter = scrollX;
        else if (layoutOrientation == LinearLayoutManager.VERTICAL)
            targetParameter = scrollY;

        if (targetParameter == 0)
            onScrollStatusChanged(ScrollState.MIN);
        else if (targetParameter > 0) {
            int visibleItemCount = getLayoutManager().getChildCount();
            int totalItemCount = getLayoutManager().getItemCount();
            int pastVisibleItems = getLayoutManager().findFirstVisibleItemPosition();

            if (visibleItemCount + pastVisibleItems >= totalItemCount)
                onScrollStatusChanged(ScrollState.MAX);
            else
                onScrollStatusChanged(ScrollState.IN_BETWEEN);
        }
    }

    private boolean isScrollable() {
        return (layoutOrientation == LinearLayoutManager.HORIZONTAL && computeHorizontalScrollRange() - getWidth() > 0) ||
                (layoutOrientation == LinearLayoutManager.VERTICAL && computeVerticalScrollRange() - getHeight() > 0);
    }

    private boolean isScrollEnabled() {
        return (layoutOrientation == VERTICAL && getLayoutManager().canScrollVertically()) || (layoutOrientation == HORIZONTAL && getLayoutManager().canScrollHorizontally());
    }
    //endregion

    //region NETWORK METHODS
    @Override
    public void onResponse(@NonNull Call<List<T>> call, @NonNull Response<List<T>> response) {
        requestListener.onRequestFinished();

        isAlreadyWaiting = false;

        if (response.body() != null) {
            List<T> newItems = response.body();
            if (newItems != null && newItems.size() > 0) {
                pageIndex++;
                addNewItems(newItems);
            } else {
                setMaxPageCount(pageIndex);
                requestListener.onFinalPageReached();
            }
        } else {
            String errorBody = null;
            if (response.errorBody() != null) {
                try {
                    errorBody = response.errorBody().string();
                } catch (Exception ex) {
                    errorBody = ErrorMessages.DEFAULT_REQUEST_ERROR.message;
                    ex.printStackTrace();
                }
            }

            requestListener.onRequestError(errorBody);
        }
    }

    @Override
    public void onFailure(@NonNull Call<List<T>> call, @NonNull Throwable t) {
        requestListener.onRequestFinished();

        isAlreadyWaiting = false;
    }
    //endregion

    //region STORY METHODS
    private void handleNewActionRequest() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRequestTime > DELAY_FOR_NEW_REQUEST) {
            if (!isAlreadyWaiting)
                if (pageIndex < maxPageCount || !isMaxPageCountSet)
                    requestListener.shouldCallNewRequest(pageIndex);
                else
                    requestListener.onFinalPageReached();

            lastRequestTime = System.currentTimeMillis();
        }
    }

    private void setMaxPageCount(int maxPageCount) {
        if (maxPageCount < 0)
            maxPageCount = 0;

        this.maxPageCount = maxPageCount;
        isMaxPageCountSet = true;
    }

    public void updateRequest(@NonNull Call<List<T>> call) {
        requestListener.onRequestStarted();

        isAlreadyWaiting = true;
        currentCall = call;

        call.enqueue(this);
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public void addNewItems(List<T> userList) {
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isScrollable())
                    if (sendFirstRequest)
                        requestListener.shouldCallNewRequest(pageIndex);
                    else
                        Log.w(LOG_TAG, ErrorMessages.SCROLL_NOT_AVAILABLE_ERROR.message);
            }
        }, DELAY_FOR_SCROLL_STATUS_CHECK);

        getAdapter().addItems(userList);
        invalidateItemDecorations();
    }

    public void clearItems(int newMaxPageCount) {
        isAlreadyWaiting = false;
        pageIndex = 0;
        setMaxPageCount(newMaxPageCount);

        getAdapter().resetAdapter();

        if (currentCall != null)
            currentCall.cancel();
    }
    //endregion
}