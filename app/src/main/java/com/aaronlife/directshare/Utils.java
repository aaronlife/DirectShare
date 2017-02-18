package com.aaronlife.directshare;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

public class Utils
{
    public final static String LOGTAG = "aarontest";
    public final static int TYPE_SEND = 0;
    public final static int TYPE_RECEIVE = 1;

    public static int byteArrayToInt(byte[] b)
    {
        int value = 0;
        for (int i = 0; i < 4; i++)
        {
            int shift = (4 - 1 - i) * 8;
            value += (b[i] & 0x000000FF) << shift;
        }
        return value;
    }

    public static byte[] intToByteArray(int a)
    {
        byte[] ret = new byte[4];
        ret[3] = (byte) (a & 0xFF);
        ret[2] = (byte) ((a >> 8) & 0xFF);
        ret[1] = (byte) ((a >> 16) & 0xFF);
        ret[0] = (byte) ((a >> 24) & 0xFF);

        return ret;
    }

    protected static void waitForData(InputStream inputStream, int bytes)
                                                            throws IOException
    {
        while(inputStream.available() < bytes)
        {
            try
            {
                Thread.sleep(150);
            }
            catch(InterruptedException e)
            {
            }
        }
    }

    public static void updateProgress(AsyncTask asyncTask, int value)
    {
        // 觸發onProgressUpdate()方法，更新介面
        if(asyncTask instanceof ServerAsyncTask)
            ((ServerAsyncTask)asyncTask).updateProgress(value);
        else
            ((ClientAsyncTask)asyncTask).updateProgress(value);
    }

    // 發送照片
    public static void sendPhotos(AsyncTask asyncTask, OutputStream outStream,
                              Collection<String> photoPaths) throws IOException
    {
        updateProgress(asyncTask, 0);

        // Byte 0~1 = 0x00, 0xff 識別碼
        outStream.write(0x00);
        outStream.write(0xff);
        outStream.flush();

        // Byte 2~5 = 總長度（bytes）
        int totalLength = 0;

        for(String path : photoPaths)
        {
            File file = new File(path);
            totalLength += file.length();

            Log.d(Utils.LOGTAG, "Send: " + path + "(" + file.length() + ")");
        }

        outStream.write(Utils.intToByteArray(totalLength));
        outStream.flush();

        int sentLength = 0;
        byte[] buf = new byte[1024];

        for(String path : photoPaths)
        {
            // Byte 6   = 第１個檔名長度
            outStream.write(path.substring(path.lastIndexOf("/") + 1).length());
            outStream.flush();

            // Byte 6+n = 檔名
            outStream.write(path.substring(path.lastIndexOf("/") + 1).getBytes());
            outStream.flush();

            // Byte 6+n+1~6+n+4 = 檔案大小（bytes）
            File file = new File(path);
            outStream.write(Utils.intToByteArray((int)file.length()));
            outStream.flush();

            // Byte 6+n+5~... = 檔案內容
            FileInputStream fileInputStream = new FileInputStream(path);

            int len = 0;
            while((len = fileInputStream.read(buf)) != -1)
            {
                outStream.write(buf, 0, len);
                outStream.flush();

                sentLength += len;
                updateProgress(asyncTask, (int)((double)sentLength / totalLength * 100));
            }

            fileInputStream.close();
        }

        updateProgress(asyncTask, 100);

        // Byte n   = 0x00 傳輸結束
        outStream.write(0x00);
        outStream.flush();

        outStream.close();
    }

    // 接收照片
    public static void receivePhotos(AsyncTask asyncTask, InputStream inputStream,
                                     Context context) throws IOException
    {
        updateProgress(asyncTask, 0);

        byte[] intArray = new byte[4];
        byte[] buf = new byte[1024];

        // Byte 0~1 = 0x00, 0xff 識別碼
        waitForData(inputStream, 2);
        inputStream.read(buf, 0, 2);

        if(buf[0] != 0x00 && buf[1] != 0xff)
        {
            Log.d(Utils.LOGTAG, "表頭錯誤");
            return;
        }

        // Byte 2~5 = 總長度（bytes）
        waitForData(inputStream, 4);
        inputStream.read(intArray, 0, 4);

        int totalLen = Utils.byteArrayToInt(intArray);
        int receivedLen = 0;

        Log.d(Utils.LOGTAG, "總大小：" + totalLen);

        while(true)
        {
            // Byte 6   = 第１個檔名長度
            waitForData(inputStream, 1);
            int pathLength = inputStream.read();

            // Byte n   = 0x00 傳輸結束
            if(pathLength == 0x00) break;

            // Byte 6+n = 檔名
            waitForData(inputStream, pathLength);
            byte[] pathArray = new byte[pathLength];
            inputStream.read(pathArray, 0, pathLength);
            String path = new String(pathArray);

            // Byte 6+n+1~6+n+4 = 檔案大小（bytes）
            waitForData(inputStream, 4);
            inputStream.read(intArray, 0, 4);
            int fileLength = Utils.byteArrayToInt(intArray);

            Log.d(Utils.LOGTAG, "即將接收檔案：" + path + "(" + fileLength + ")");

            // Byte 6+n+5~... = 檔案內容
            int total = 0;
            File pathFile = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM) + "/DirectShare/" + path);

            File dirs = new File(pathFile.getParent());
            if (!dirs.exists()) dirs.mkdirs();
            pathFile.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(pathFile);

            while(true)
            {
                // 寫入檔案
                int len = 0;
                if(fileLength - total > 1024)
                    len = inputStream.read(buf, 0, 1024);
                else
                    len = inputStream.read(buf, 0, fileLength - total);

                fileOutputStream.write(buf, 0, len);
                fileOutputStream.flush();

                total += len;
                receivedLen += len;
                updateProgress(asyncTask, (int)((double)receivedLen / totalLen * 100));

                if(total >= fileLength) break;
            }

            updateProgress(asyncTask, 100);
            fileOutputStream.close();

            // 更新媒體庫
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(pathFile));
            context.sendBroadcast(mediaScanIntent);
        }
        inputStream.close();
    }

    public static void p2pDisconnect(WifiP2pManager manager,
                                     WifiP2pManager.Channel channel)
    {
        manager.removeGroup(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(Utils.LOGTAG, "移除Group成功。");
            }

            @Override
            public void onFailure(int reason)
            {
                Log.d(Utils.LOGTAG, "移除Group失敗。");
            }
        });

        manager.cancelConnect(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess()
            {
                Log.d(Utils.LOGTAG, "取消連線成功。");
            }

            @Override
            public void onFailure(int reason)
            {
                Log.d(Utils.LOGTAG, "取消連線失敗。");
            }
        });
    }
}