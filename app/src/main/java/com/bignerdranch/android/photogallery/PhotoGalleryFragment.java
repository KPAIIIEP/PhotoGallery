package com.bignerdranch.android.photogallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;

import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.SearchView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends Fragment {
    private static final String TAG = "PhotoGalleryFragment";

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private ProgressBar mProgressBar;
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;
    private ThumbnailDownloader<GalleryItem> mGalleryItemThumbnailDownloader;
    private int mPage = 1;
    private int mPreviousTotal = 0;
    private boolean mLoading = true;
    private int mLastVisibleItemPosition = 0;
    private boolean mNewPageLoaded = true;
    private ThumbnailCache mThumbnailCache;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        mThumbnailCache = new ThumbnailCache(ThumbnailCache.getMaxSize(getContext()));
        mProgressBar = (ProgressBar) getActivity().findViewById(R.id.progressBar);
        mProgressBar.setVisibility(ProgressBar.VISIBLE);
        updateItems();

        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler, mThumbnailCache);
        mThumbnailDownloader.setThumbnailDownloadListener(
                new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>() {
                    @Override
                    public void onThumbnailDownloaded(PhotoHolder photoHolder, Bitmap bitmap) {
                        Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                        photoHolder.bindDrawable(drawable);
                    }
                }
        );
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        mGalleryItemThumbnailDownloader = new ThumbnailDownloader<>(responseHandler, mThumbnailCache);
        mGalleryItemThumbnailDownloader.start();
        mGalleryItemThumbnailDownloader.getLooper();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mPhotoRecyclerView = (RecyclerView) v.findViewById(R.id.photo_recycler_view);
        mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        GridLayoutManager layoutManager = (GridLayoutManager) mPhotoRecyclerView.getLayoutManager();
        mPhotoRecyclerView.getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (v.getWidth() > 1600) {
                    ((GridLayoutManager)mPhotoRecyclerView.getLayoutManager()).setSpanCount(4);
                }
                mLastVisibleItemPosition = ((GridLayoutManager)mPhotoRecyclerView
                        .getLayoutManager()).findLastVisibleItemPosition();
                Log.i(TAG, "Последний видимый элемент: " + String.valueOf(mLastVisibleItemPosition));
                mPhotoRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                //super.onScrolled(recyclerView, dx, dy);
                List<GalleryItem> preloadBitmapList;
                if (dy > 0) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    if (mLoading) {
                        if (totalItemCount > mPreviousTotal) {
                            mLoading = false;
                            mPreviousTotal = totalItemCount;
                        }
                    }
                    if (!mLoading && (visibleItemCount + firstVisibleItem) >= totalItemCount) {
                        mProgressBar.setVisibility(ProgressBar.VISIBLE);
                        mNewPageLoaded = false;
                        updateItems();
                        mLoading = true;
                    }
                    if (mNewPageLoaded &
                            (layoutManager.findLastVisibleItemPosition() > mLastVisibleItemPosition)) {
                        Log.i(TAG, String.valueOf(mLastVisibleItemPosition));
                        if (mLastVisibleItemPosition < mItems.size() - 10) {
                            preloadBitmapList = mItems
                                    .subList(mLastVisibleItemPosition, mLastVisibleItemPosition + 10);
                        } else {
                            preloadBitmapList = mItems
                                    .subList(mLastVisibleItemPosition, mItems.size() - 1);
                        }
                        for (GalleryItem item : preloadBitmapList) {
                            mGalleryItemThumbnailDownloader.queueThumbnail(item, item.getUrl());
                            if (item.getUrl() != null) {
                                Log.i(TAG, item.getUrl());
                            }
                        }
                        mLastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();
                    }
                }
            }
        });
        setupAdapter();
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
        mGalleryItemThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        mGalleryItemThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.fragment_photo_gallery, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                Log.d(TAG, "QueryTextSubmit: " + s);
                QueryPreferences.setStoredQuery(getActivity(), s);
                mPage = 1;
                mPreviousTotal = 0;
                mItems.clear();
                mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
                updateItems();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                Log.d(TAG, "QueryTextChange: " + s);
                return false;
            }
        });
        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = QueryPreferences.getStoredQuery(getActivity());
                searchView.setQuery(query, false);
            }
        });
        MenuItem toggleItem = menu.findItem(R.id.menu_item_toggle_polling);
//        if (PollService.isServiceAlarmOn(getActivity())) {
        if (PollJobService.isPollJobServiceOn(getContext())) {
            toggleItem.setTitle(R.string.stop_polling);
        } else {
            toggleItem.setTitle(R.string.start_polling);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(), null);
                mPage = 1;
                mPreviousTotal = 0;
                mItems.clear();
                mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
                updateItems();
                return true;
            case R.id.menu_item_toggle_polling:
                //boolean shouldStartAlarm = !PollService.isServiceAlarmOn(getActivity());
                //PollService.setServiceAlarm(getActivity(), shouldStartAlarm);
                boolean isPollJobServiceOn = !PollJobService.isPollJobServiceOn(getContext());
                PollJobService.startPollJobService(getContext(), isPollJobServiceOn);
                getActivity().invalidateOptionsMenu();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateItems() {
        String query = QueryPreferences.getStoredQuery(getActivity());
        new FetchItemsTask(query).execute(String.valueOf(mPage++));
    }

    private void setupAdapter() {
        if (isAdded()) {
            if (mPhotoRecyclerView.getAdapter() == null ) {
                mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                    }
                });
                try {
                    thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                thread.start();
            } else {
                mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
            }
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);
            mItemImageView = (ImageView) itemView.findViewById(R.id.item_image_view);
        }

        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {
        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, viewGroup, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder photoHolder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            Drawable placeholder = getResources().getDrawable(R.drawable.bill_up_close);
            photoHolder.bindDrawable(placeholder);
            mThumbnailDownloader.queueThumbnail(photoHolder, galleryItem.getUrl());
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    private class FetchItemsTask extends AsyncTask<String, Void, List<GalleryItem>> {
        private String mQuery;

        public FetchItemsTask(String query) {
            mQuery = query;
        }

        @Override
        protected List<GalleryItem> doInBackground(String... params) {
            FlickrFetchr ff = new FlickrFetchr();
            ff.setPage(params[0]);
            if (mQuery == null) {
                return ff.fetchRecentPhotos();
            } else {
                return ff.searchPhotos(mQuery);
            }
        }

        @Override
        protected void onPostExecute(List<GalleryItem> items) {
            mProgressBar.setVisibility(ProgressBar.GONE);
            mItems.addAll(items);
            setupAdapter();
            mNewPageLoaded = true;
        }
    }
}
