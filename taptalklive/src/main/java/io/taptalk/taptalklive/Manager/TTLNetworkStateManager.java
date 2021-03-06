package io.taptalk.taptalklive.Manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

import io.taptalk.TapTalk.Helper.TapTalk;
import io.taptalk.TapTalk.Interface.TapTalkNetworkInterface;
import io.taptalk.TapTalk.Manager.TAPConnectionManager;
import io.taptalk.TapTalk.Manager.TAPDataManager;
import io.taptalk.TapTalk.ViewModel.TAPRoomListViewModel;

import static io.taptalk.taptalklive.Const.TTLConstant.TapTalkInstanceKey.TAPTALK_INSTANCE_KEY;

public class TTLNetworkStateManager {
    private static TTLNetworkStateManager instance;
    private List<TapTalkNetworkInterface> listeners;

    private TapNetworkCallback networkCallback;
    private NetworkRequest networkRequest;

    public static TTLNetworkStateManager getInstance() {
        return instance == null ? (instance = new TTLNetworkStateManager()) : instance;
    }

    public TTLNetworkStateManager() {
        listeners = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Use ConnectivityManager.NetworkCallback for API 21 and above
            networkCallback = new TapNetworkCallback();
            networkRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
        }
    }

    public void registerCallback(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && null != networkCallback) {
            getConnectivityManager(context).registerNetworkCallback(networkRequest, networkCallback);
        } else  {
            // Broadcast receiver will not receive callback right away, trigger connectivity change manually to update connection status
            triggerConnectivityChange();
        }
    }

    public void unregisterCallback(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && null != networkCallback) {
            getConnectivityManager(context).unregisterNetworkCallback(networkCallback);
        }
    }

    public boolean hasNetworkConnection(Context context) {
        ConnectivityManager connectivityManager = getConnectivityManager(context);
        if (null != connectivityManager &&
                null != connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) &&
                null != connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI) &&
                (NetworkInfo.State.CONNECTED == connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState() ||
                        NetworkInfo.State.CONNECTED == connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState())) {
            return true;
        } else {
            return false;
        }
    }

    private ConnectivityManager getConnectivityManager(Context context) {
        return (null != context) ?
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE) : null;
    }

    public void addNetworkListener(TapTalkNetworkInterface listener) {
        listeners.add(listener);
    }

    public void removeNetworkListener(TapTalkNetworkInterface listener) {
        listeners.remove(listener);
    }

    public void removeNetworkListenerAt(int index) {
        listeners.remove(index);
    }

    public void clearNetworkListener() {
        listeners.clear();
    }

    private void triggerConnectivityChange() {
        if (TTLNetworkStateManager.getInstance().hasNetworkConnection(TapTalk.appContext)) {
            TTLNetworkStateManager.getInstance().onNetworkAvailable();
        } else {
            TTLNetworkStateManager.getInstance().onNetworkLost();
        }
    }

    private void onNetworkAvailable() {
        List<TapTalkNetworkInterface> listenersCopy = new ArrayList<>(listeners);
        if (!listenersCopy.isEmpty()) {
            for (TapTalkNetworkInterface listener : listenersCopy) {
                listener.onNetworkAvailable();
            }
        }
    }

    private void onNetworkLost() {
        TAPRoomListViewModel.setShouldNotLoadFromAPI(TAPTALK_INSTANCE_KEY,false);
        TAPDataManager.getInstance(TAPTALK_INSTANCE_KEY).setNeedToQueryUpdateRoomList(true);
        TAPConnectionManager.getInstance(TAPTALK_INSTANCE_KEY).close();
    }

    public class TapNetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            onNetworkAvailable();
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            onNetworkLost();
        }
    }

    public static class TapNetworkBroadcastReceiver extends BroadcastReceiver {

        public TapNetworkBroadcastReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ||
                    null == intent.getAction() ||
                    !intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE")) {
                return;
            }
            TTLNetworkStateManager.getInstance().triggerConnectivityChange();
        }
    }
}
