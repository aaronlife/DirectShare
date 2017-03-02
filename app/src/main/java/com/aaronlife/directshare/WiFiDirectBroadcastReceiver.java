package com.aaronlife.directshare;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import android.view.View;

import java.net.InetAddress;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver
{
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private MainActivity activity;

    public WiFiDirectBroadcastReceiver(WifiP2pManager manager,
                                       WifiP2pManager.Channel channel,
                                       MainActivity activity)
    {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    @Override
    public void onReceive(final Context context, Intent intent)
    {
        String action = intent.getAction();

        if(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action))
        {
            Log.d(Utils.LOGTAG, "WIFI_P2P_STATE_CHANGED_ACTION");

            // 檢查WiFi是否已經開啟
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);

            if(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
            {
                Log.d(Utils.LOGTAG, "WIFI_P2P_STATE_ENABLED");

                // WiFi已經打開，開始掃瞄裝置
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener()
                {
                    @Override
                    public void onSuccess()
                    {
                        Log.d(Utils.LOGTAG, "WiFi對點裝置掃描成功。");
                    }

                    @Override
                    public void onFailure(int reasonCode)
                    {
                        Log.d(Utils.LOGTAG,
                              "WiFi對點裝置掃描失敗，錯誤碼: " + reasonCode);
                    }
                });
            }
            else
            {
                Log.d(Utils.LOGTAG, "WIFI_P2P_STATE_DISABLED");

                // WiFi沒有打開，打開WiFi
                WifiManager wifiManager =
                    (WifiManager)activity.getSystemService(Context.WIFI_SERVICE);
                wifiManager.setWifiEnabled(true);
            }
        }
        else if(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action))
        {
            Log.d(Utils.LOGTAG, "WIFI_P2P_PEERS_CHANGED_ACTION");

            if(manager != null)
            {
                // 呼叫requestPeers來取得掃描到的WiFi Direct裝置清單
                manager.requestPeers(channel,
                                        new WifiP2pManager.PeerListListener()
                {
                    @Override
                    public void onPeersAvailable(WifiP2pDeviceList peers)
                    {
                        if(peers.getDeviceList().size() > 0)
                        {
                            activity.scanning.setVisibility(View.GONE);
                            activity.txtScanning.setVisibility(View.GONE);
                        }
                        else
                        {
                            activity.scanning.setVisibility(View.VISIBLE);
                            activity.txtScanning.setVisibility(View.VISIBLE);
                            manager.discoverPeers(channel, null);
                        }

                        WifiP2pDevice[] tmp =
                                new WifiP2pDevice[peers.getDeviceList().size()];
                        peers.getDeviceList().toArray(tmp);
                        activity.peersAdapter.setPeers(tmp);
                        activity.peersAdapter.notifyDataSetChanged();
                    }
                });
            }
        }
        else if(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action))
        {
            Log.d(Utils.LOGTAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION");

            if (manager == null) return;

            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if(networkInfo.isConnected())
            {
                Log.d(Utils.LOGTAG, "We are connected.");

                activity.isSending = true;
                activity.peersAdapter.notifyDataSetChanged();
                activity.progressArea.setVisibility(View.VISIBLE);
                activity.progressBar.setProgress(0);
                activity.txtProgress.setText("0%");

                manager.requestConnectionInfo(channel,
                                    new WifiP2pManager.ConnectionInfoListener()
                {
                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo info)
                    {
                        // 取得遠端裝置的IP位址
                        InetAddress address = info.groupOwnerAddress;

                        if(info.groupOwnerAddress != null)
                        {
                            Log.d(Utils.LOGTAG, address.getHostAddress());

                            if(info.isGroupOwner)
                            {
                                Log.d(Utils.LOGTAG, "I'm group owner");
                                new ServerAsyncTask(activity).execute();
                            }
                            else
                            {
                                Log.d(Utils.LOGTAG, "I'm not group owner");
                                new ClientAsyncTask(activity,
                                            address.getHostAddress()).execute();
                            }
                        }
                        else
                            Log.d(Utils.LOGTAG, "沒有發現Group owner，無法連線");
                    }
                });
            }
            else
            {
                // It's a disconnect
                Log.d(Utils.LOGTAG, "We are not connected.");

                // 設回預設值
                activity.type = Utils.TYPE_RECEIVE;
                activity.isSending = false;
                activity.peersAdapter.notifyDataSetChanged();
                activity.progressArea.setVisibility(View.GONE);

                // 斷線後，再重新掃描
                manager.discoverPeers(channel,
                                            new WifiP2pManager.ActionListener()
                {
                    @Override
                    public void onSuccess()
                    {
                        Log.d(Utils.LOGTAG, "WiFi對點裝置掃描成功。");
                    }

                    @Override
                    public void onFailure(int reasonCode)
                    {
                        Log.d(Utils.LOGTAG, "掃描失敗，錯誤碼: " + reasonCode);
                    }
                });
            }
        }
        else if(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action))
        {
            Log.d(Utils.LOGTAG, "WIFI_P2P_THIS_DEVICE_CHANGED_ACTION");

            WifiP2pDevice device = 
                intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            activity.txtName.setText("裝置名稱：" + device.deviceName);
        }
    }
}
