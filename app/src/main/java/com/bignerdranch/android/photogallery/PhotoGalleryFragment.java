package com.bignerdranch.android.photogallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends Fragment {
    private static final String TAG = "PhotoGalleryFragment";

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    ProgressBar mProgressBar;
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;
    private ProgressBar mProgressBar;
    private int mPage = 1;
    private int mPreviousTotal = 0;
    private boolean mLoading = true;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mProgressBar = (ProgressBar) getActivity().findViewById(R.id.progressBar);
        mProgressBar.setVisibility(ProgressBar.VISIBLE);
        new FetchItemsTask().execute();

        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
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
        Log.i(TAG, "Background thread started");
        new FetchItemsTask().execute(String.valueOf(mPage++));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mPhotoRecyclerView = (RecyclerView) v.findViewById(R.id.photo_recycler_view);
        mPhotoRecyclerView.getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (v.getWidth() > 1600) {
                    ((GridLayoutManager)mPhotoRecyclerView.getLayoutManager()).setSpanCount(4);
                }
            }
        });
        mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        LinearLayoutManager layoutManager = (LinearLayoutManager) mPhotoRecyclerView.getLayoutManager();
        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                //super.onScrolled(recyclerView, dx, dy);
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
                        new FetchItemsTask().execute(String.valueOf(mPage++));
                        mLoading = true;
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    private void setupAdapter() {
        if (isAdded()) {
            if (mPhotoRecyclerView.getAdapter() == null ) {
                mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
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
        @Override
        protected List<GalleryItem> doInBackground(String... params) {
            return new FlickrFetchr().fetchItems(params[0]);
        }

        @Override
        protected void onPostExecute(List<GalleryItem> items) {
            mProgressBar.setVisibility(ProgressBar.GONE);
            mItems.addAll(items);
            setupAdapter();
        }
    }
}
