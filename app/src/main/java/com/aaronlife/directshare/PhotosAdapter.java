package com.aaronlife.directshare;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;


public class PhotosAdapter extends BaseAdapter
{
    Context context;
    LayoutInflater inflater;
    int width = 0;

    SparseBooleanArray checkStatus;
    HashMap<Integer, String> checkedPhotoPath = new HashMap<>();
    ConcurrentHashMap<Integer, Bitmap> images = new ConcurrentHashMap<>();

    Deque<LoadImageAsyncTask> queue = new ArrayDeque<>();

    Cursor data;

    public PhotosAdapter(Context context, int width)
    {
        this.context = context;
        inflater = LayoutInflater.from(context);
        this.width = width;

        new Thread(new LoadImageThread()).start();
    }

    @Override
    public int getCount()
    {
        if(data == null) return 0;

        return data.getCount();
    }

    @Override
    public String getItem(int position)
    {
        if(!data.moveToPosition(position)) return null;

        int columnIndex = data.getColumnIndex(MediaStore.Images.Media.DATA);

        return data.getString(columnIndex);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        Log.d(Utils.LOGTAG, "p:"+ position);
        convertView = inflater.inflate(R.layout.gridview_photo, parent, false);

        // 設定GridView每張照片的寬
        ViewGroup.LayoutParams viewParam = convertView.getLayoutParams();
        viewParam.height = width;
        convertView.setLayoutParams(viewParam);

        String path = getItem(position);
        ImageView imageView = (ImageView)convertView.findViewById(R.id.photo);

        if(images.get(position) != null)
            imageView.setImageBitmap(images.get(position));
        else
        {
            queue.offerFirst(new LoadImageAsyncTask(position, data, imageView));

            while(queue.size() >= 100) queue.pollLast();
        }

        CheckBox checkBox = (CheckBox)convertView.findViewById(R.id.checkbox);
        checkBox.setOnCheckedChangeListener(onCheckedChangeListener);
        checkBox.setChecked(checkStatus.get(position));
        checkBox.setButtonDrawable(R.drawable.checkbox_selector);

        // 將CheckBox編號用Tag記下來
        checkBox.setTag(position);

        // 照片路徑記在Tag中
        checkBox.setTag(R.string.app_name, path);

        return convertView;
    }

    CompoundButton.OnCheckedChangeListener onCheckedChangeListener =
        new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                buttonView.setChecked(isChecked);

                if(buttonView.getTag() != null)
                    checkStatus.put((int)buttonView.getTag(), isChecked);

                if(isChecked && buttonView.getTag(R.string.app_name) != null)
                {
                    Log.d(Utils.LOGTAG, "選取: " + buttonView.getTag(R.string.app_name));
                    checkedPhotoPath.put((int)buttonView.getTag(),
                            (String)buttonView.getTag(R.string.app_name));
                }
                else if(buttonView.getTag() != null)
                    checkedPhotoPath.remove((int)buttonView.getTag());

                ((MainActivity)context).txtMessage
                        .setText("" + checkedPhotoPath.size() + "張照片被選取");
            }
        };

    class LoadImageThread implements Runnable
    {
        @Override
        public void run()
        {
            while(true)
            {
                if(queue.size() > 0)
                {
                    try
                    {
                        queue.poll().execute().get(); // 阻塞式
                    }
                    catch(InterruptedException | ExecutionException e) {}
                }
                else
                {
                    try {Thread.sleep(500);} catch(InterruptedException e) {}
                }
            }
        }
    }

    class LoadImageAsyncTask extends AsyncTask<Void, Void, Bitmap>
    {
        int position;
        Cursor cursor;
        ImageView imageView;

        public LoadImageAsyncTask(int position, Cursor cursor,
                                  ImageView imageView)
        {
            this.position = position;
            this.cursor = cursor;
            this.imageView = imageView;
        }

        @Override
        protected Bitmap doInBackground(Void... params)
        {
            // 因為使用者可能來回卷動，所以造成重複的載入動作
            if(images.get(position) != null) return images.get(position);

            if(data.moveToPosition(position))
            {
                int width =
                    data.getInt(data.getColumnIndex(MediaStore.Images.Media.WIDTH));
                String path =
                    data.getString(data.getColumnIndex(MediaStore.Images.Media.DATA));

                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = width / 200; // 寬200像素
                Bitmap bitmap = BitmapFactory.decodeFile(path, o);

                Log.d(Utils.LOGTAG, "(" + position + ")原始寬：" + width +
                        ", 縮放：" + o.inSampleSize);

                if(bitmap == null) return null;

                synchronized(images)
                {
                    images.put(position, bitmap);

                    // position 0時不處理快取
                    if(position == 0) return bitmap;

                    final int cacheSize = 200;

                    if(position > cacheSize)
                    {
                        for(int i = 1 ; i < position - cacheSize ; i++)
                        {
                            if(images.get(i) != null) images.remove(i).recycle();
                        }
                    }

                    if(position < data.getCount() - cacheSize)
                    {
                        for(int i = 0; i < data.getCount() - cacheSize - position; i++)
                        {
                            if(images.get(position + cacheSize + i) != null)
                                images.remove(position + cacheSize + i).recycle();
                        }
                    }
                }

                return bitmap;
            }

            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap)
        {
            super.onPostExecute(bitmap);

            if(bitmap != null)
            {
                imageView.setImageBitmap(bitmap);
            }

            Log.d(Utils.LOGTAG, "目前快取的數量：" + images.size());
        }
    }

    public void setCursor(Cursor data)
    {
        Log.d(Utils.LOGTAG, "照片數量：" + data.getCount());

        this.data = data;
        checkStatus = new SparseBooleanArray(data.getCount());
    }

    public HashMap<Integer, String> getCheckedPhotos()
    {
        return checkedPhotoPath;
    }
}