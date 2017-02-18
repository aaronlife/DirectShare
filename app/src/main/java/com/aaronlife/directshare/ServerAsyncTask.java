package com.aaronlife.directshare;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerAsyncTask extends AsyncTask
{
    private MainActivity mainActivity;

    public ServerAsyncTask(MainActivity mainActivity)
    {
        this.mainActivity = mainActivity;
    }

    @Override
    protected String doInBackground(Object[] params)
    {
        ServerSocket serverSocket = null;

        try
        {
            serverSocket = new ServerSocket(8888);
            Socket client = serverSocket.accept();
            serverSocket.setSoTimeout(5000);

            Log.d(Utils.LOGTAG, "Server accept new connection.");

            if(mainActivity.type == Utils.TYPE_SEND)
                Utils.sendPhotos(this, client.getOutputStream(),
                        mainActivity.photosAdp.getCheckedPhotos().values());
            else
            {
                if(ContextCompat.checkSelfPermission(mainActivity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED)
                {
                    Log.d(Utils.LOGTAG, "已取得裝置寫入權限。");
                    Utils.receivePhotos(this, client.getInputStream(), mainActivity);
                }
                else
                {
                    Log.d(Utils.LOGTAG, "未取得裝置寫入權限。");

                    Toast.makeText(mainActivity, "無法儲存照片（權限不足）",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
        catch (IOException e)
        {
            Log.d(Utils.LOGTAG, "Socket error: " + e.getMessage());

            mainActivity.manager.removeGroup(mainActivity.channel, null);
        }
        finally
        {
            try{ serverSocket.close(); } catch(IOException e) {}
        }

        // 由接收端來斷開連接，以免還沒收完就被斷開了
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
    }

    public void updateProgress(int value)
    {
        // 提供讓外部方法觸發onProgressUpdate()方法，更新介面
        publishProgress(value);
    }
}