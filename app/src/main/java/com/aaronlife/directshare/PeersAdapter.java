package com.aaronlife.directshare;

import android.net.wifi.p2p.WifiP2pDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

public class PeersAdapter extends BaseAdapter
{
    MainActivity mainActivity;
    LayoutInflater inflater;

    WifiP2pDevice[] peers;

    public PeersAdapter(MainActivity mainActivity)
    {
        this.mainActivity = mainActivity;
        this.inflater = LayoutInflater.from(mainActivity);
    }

    @Override
    public int getCount()
    {
        if(peers == null) return 0;

        return peers.length;
    }

    @Override
    public WifiP2pDevice getItem(int position)
    {
        return peers[position];
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        convertView = inflater.inflate(R.layout.listview_peer, parent, false);

        WifiP2pDevice peer = getItem(position);
        TextView txtPeerName = (TextView)convertView.findViewById(R.id.peer_name);
        txtPeerName.setText(peer.deviceName);

        Button btnSend = (Button)convertView.findViewById(R.id.send);
        btnSend.setTag(peers[position]);

        if(mainActivity.isSending == true)
            btnSend.setEnabled(false);
        else
            btnSend.setEnabled(true);

        return convertView;
    }

    public void setPeers(WifiP2pDevice[] peers)
    {
        this.peers = peers;
    }
}