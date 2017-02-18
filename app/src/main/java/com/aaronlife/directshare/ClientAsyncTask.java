package com.aaronlife.directshare;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ClientAsyncTask extends AsyncTask
{
    MainActivity mainActivity;
    String address;

    public ClientAsyncTask(MainActivity mainActivity, String address)
    {
        this.mainActivity = mainActivity;
        this.address = address;
    }

    @Override
    protected Object doInBackground(Object[] params)
    {
        // 延遲一秒，不能讓ClientSocket比ServerSocket建立，否則會連不到Server
        try
        {
            Thread.sleep(1000);
        }
        catch(InterruptedException e) {}

        Socket socket = new Socket();

        try
        {
            socket.bind(null);
            socket.connect(new InetSocketAddress(address, 8888), 5000);

            if(mainActivity.type == Utils.TYPE_SEND)
                Utils.sendPhotos(this, socket.getOutputStream(),
                                 mainActivity.photosAdp.getCheckedPhotos().values());
            else
            {
                if(ContextCompat.checkSelfPermission(mainActivity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED)
                {
                    Log.d(Utils.LOGTAG, "已取得裝置寫入權限。");
                    Utils.receivePhotos(this, socket.getInputStream(), mainActivity);
                }
                else
                {
                    Log.d(Utils.LOGTAG, "未取得裝置寫入權限。");

                    Toast.makeText(mainActivity, "無法儲存照片（權限不足）",
                                                 Toast.LENGTH_LONG).show();
                }
            }

            Log.d(Utils.LOGTAG, "Client連線成功。");
        }
        catch (IOException e)
        {
            Log.d(Utils.LOGTAG, "Client連線失敗：" + e.getMessage());

            Utils.p2pDisconnect(mainActivity.manager, mainActivity.channel);
        }
        finally
        {
            try { socket.close(); } catch(IOException e) {}
        }

        // 發送方不能先關閉，否則可能接收方還沒收完就timeout了
        if(mainActivity.type == Utils.TYPE_RECEIVE)
        {
            Utils.p2pDisconnect(mainActivity.manager, mainActivity.channel);
        }

        return null;
    }

    @Override
    protected void onProgressUpdate(Object[] values)
    {
        super.onProgressUpdate(values);

        int value = (Integer)values[0];
        mainActivity.progressBar.setProgress(value);
        mainActivity.txtProgress.setText("" + value + "%");

        if(value == 100)
        {
            mainActivity.photosAdp.images.clear();
        }
    }

    public void updateProgress(int value)
    {
        // 觸發onProgressUpdate()方法，更新介面
        publishProgress(value);
    }
}