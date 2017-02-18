package com.aaronlife.directshare;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity
                                            implements LoaderCallbacks<Cursor>
{
    public static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSIONS = 0;
    public static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSIONS = 1;

    TextView txtName;
    ListView listPeers;
    GridView gridPhotos;
    TextView txtMessage;
    LinearLayout progressArea;
    ProgressBar progressBar;
    TextView txtProgress;
    ProgressBar scanning;
    TextView txtScanning;

    PeersAdapter peersAdapter;
    PhotosAdapter photosAdp;

    WifiP2pManager manager;
    WifiP2pManager.Channel channel;

    BroadcastReceiver receiver;
    IntentFilter intentFilter;

    int type = Utils.TYPE_RECEIVE;
    boolean isSending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();

        // 阻止進入休眠
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 顯示版本名稱
        TextView txtVersion = (TextView)findViewById(R.id.version);
        PackageInfo pInfo = null;
        try
        {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        }
        catch(PackageManager.NameNotFoundException e)
        {
            e.printStackTrace();
        }
        txtVersion.setText(pInfo.versionName);

        initUI();

        manager = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        unregisterReceiver(receiver);

        manager.cancelConnect(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(Utils.LOGTAG, "Disconnected OK.");
            }

            @Override
            public void onFailure(int reason)
            {
                Log.d(Utils.LOGTAG, "Disconnect failed." + reason);
            }
        });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode
                                           , String permissions[]
                                           , int[] grantResults)
    {
        switch(requestCode)
        {
        case REQUEST_READ_EXTERNAL_STORAGE_PERMISSIONS:
            if (grantResults.length == 1 && grantResults[0] ==
                                        PackageManager.PERMISSION_GRANTED)
            {
                Log.d(Utils.LOGTAG, "已取得讀取相簿權限");

                Display display = getWindowManager().getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);

                photosAdp = new PhotosAdapter(this, size.x / 3);
                gridPhotos.setAdapter(photosAdp);

                // 初始化Loader
                getSupportLoaderManager().initLoader(0, null, this);
            }
            else
            {
                Toast.makeText(this, "沒有讀取相簿權限", Toast.LENGTH_LONG).show();
            }
            break;
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args)
    {
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        return new CursorLoader(this, uri, null, null, null,
                                MediaStore.Images.Media._ID + " DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data)
    {
        photosAdp.setCursor(data);
        photosAdp.notifyDataSetChanged();

        Log.d(Utils.LOGTAG, "onLoadFinished");
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader)
    {

    }

    protected void initUI()
    {
        txtName = (TextView)findViewById(R.id.name);
        listPeers = (ListView)findViewById(R.id.list_peers);
        gridPhotos = (GridView)findViewById(R.id.photos);
        txtMessage = (TextView)findViewById(R.id.message);
        progressArea = (LinearLayout)findViewById(R.id.progress_area);
        progressBar = (ProgressBar)findViewById(R.id.progress_bar);
        txtProgress = (TextView)findViewById(R.id.progress_text);
        scanning = (ProgressBar)findViewById(R.id.scanning);
        txtScanning = (TextView)findViewById(R.id.txt_scanning);

        progressArea.setVisibility(View.GONE);

        peersAdapter = new PeersAdapter(this);
        listPeers.setAdapter(peersAdapter);

        if(ContextCompat.checkSelfPermission(this,
                                Manifest.permission.READ_EXTERNAL_STORAGE)
                                == PackageManager.PERMISSION_GRANTED)
        {
            Log.d(Utils.LOGTAG, "讀取權限已取得。");

            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            photosAdp = new PhotosAdapter(this, size.x / 3);
            gridPhotos.setAdapter(photosAdp);

            // 初始化Loader
            getSupportLoaderManager().initLoader(0, null, this);
        }
        else
        {
            Log.d(Utils.LOGTAG, "無讀取權限，嘗試取得。");

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_EXTERNAL_STORAGE_PERMISSIONS);
        }


        if(ContextCompat.checkSelfPermission(this,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                == PackageManager.PERMISSION_GRANTED)
        {
            Log.d(Utils.LOGTAG, "寫入權限已取得。");
        }
        else
        {
            Log.d(Utils.LOGTAG, "無寫入權限，嘗試取得。");

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSIONS);
        }
    }

    public void onSend(View v)
    {
        progressArea.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        txtProgress.setText("0%");

        type = Utils.TYPE_SEND;
        isSending = true;
        peersAdapter.notifyDataSetChanged();

        HashMap<Integer, String> checkedImagePath =
                                                photosAdp.getCheckedPhotos();

        for(String path : checkedImagePath.values())
        {
            Log.d(Utils.LOGTAG, "即將發送：" + path);
        }

        // 連接遠端對點
        final WifiP2pDevice device = (WifiP2pDevice)v.getTag();
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        manager.connect(channel, config, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(Utils.LOGTAG, "P2P連接成功：" + device.deviceAddress);
            }

            @Override
            public void onFailure(int reason)
            {
                Log.d(Utils.LOGTAG, "P2P連接失敗。");
            }
        });
    }
}