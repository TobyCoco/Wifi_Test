/*
 * Copyright (C) 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wifirttscan;

import static com.example.android.wifirttscan.AccessPointRangingResultsActivity.SCAN_RESULT_EXTRA;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Application;
import android.companion.WifiDeviceFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.rtt.RangingRequest;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.LayoutManager;

import android.os.PatternMatcher;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.webkit.WebHistoryItem;
import android.widget.TextView;

import com.example.android.wifirttscan.MyAdapter.ScanResultClickListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays list of Access Points enabled with WifiRTT (to check distance). Requests location
 * permissions if they are not approved via secondary splash screen explaining why they are needed.
 */
public class MainActivity extends AppCompatActivity implements ScanResultClickListener {

    private static final String TAG = "MainActivity";

    private static final String StationRoutingName = "";

    private boolean mLocationPermissionApproved = false;

    List<ScanResult> mAccessPointsSupporting80211mc;

    private WifiManager mWifiManager;
    private WifiScanReceiver mWifiScanReceiver;

    private TextView mOutputTextView;
    private RecyclerView mRecyclerView;

    private MyAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOutputTextView = findViewById(R.id.access_point_summary_text_view);
        mRecyclerView = findViewById(R.id.recycler_view);

        // Improve performance if you know that changes in content do not change the layout size
        // of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        LayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);

        mAccessPointsSupporting80211mc = new ArrayList<>();

        mAdapter = new MyAdapter(mAccessPointsSupporting80211mc, this);
        mRecyclerView.setAdapter(mAdapter);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiScanReceiver = new WifiScanReceiver();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();

        mLocationPermissionApproved =
                ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        registerReceiver(
                mWifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
        unregisterReceiver(mWifiScanReceiver);
    }

    private void logToUi(final String message) {
        if (!message.isEmpty()) {
            Log.d(TAG, message);
            mOutputTextView.setText(message);
        }
    }

    @Override
    public void onScanResultItemClick(ScanResult scanResult) {
        Log.d(TAG, "onScanResultItemClick(): ssid: " + scanResult.SSID);

        Intent intent = new Intent(this, AccessPointRangingResultsActivity.class);
        intent.putExtra(SCAN_RESULT_EXTRA, scanResult);
        startActivity(intent);
    }

    public void onClickFindDistancesToAccessPoints(View view) {
        if (mLocationPermissionApproved) {
            logToUi(getString(R.string.retrieving_access_points));
            mWifiManager.startScan();

        } else {
            // On 23+ (M+) devices, fine location permission not granted. Request permission.
            Intent startIntent = new Intent(this, LocationPermissionRequestActivity.class);
            startActivity(startIntent);
        }
    }

    private WifiConfiguration createWifiInfo(String ssid, String password, int Type) {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        config.SSID = "\"" + ssid + "\"";
        config.preSharedKey = "\"" + password + "\"";
        if (Type == 0) {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else if (Type == 1) {
            config.hiddenSSID = false;
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
//            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);
            WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
            enterpriseConfig.setIdentity(ssid);
            enterpriseConfig.setAnonymousIdentity("");
            enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
            //enterpriseConfig.setCaCertificateAlias("");
            enterpriseConfig.setIdentity("");
            //enterpriseConfig.setCaPath("");
            enterpriseConfig.setDomainSuffixMatch("");
            //enterpriseConfig.setSimNum(0);
            enterpriseConfig.setPassword(password);
            config.enterpriseConfig = enterpriseConfig;
        } else {
            return null;
        }
        return config;
    }

    public boolean connectnetwork(String ssid, String macaddr, String password){
        // connect way 3 : pending test
//        WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
//        WifiConfiguration wifiConfiguration = createWifiInfo(ssid, password, 1);
//        int networkid = manager.addNetwork(wifiConfiguration);
//        Log.e(TAG,"add Network Return" + networkid);
//        boolean result = manager.enableNetwork(networkid, true);
//        if (result == true){
//            Log.e("","wifi 连接成功");
//        } else {
//            Log.e("","wifi 连接失败");
//        }
        // connect way 2 : no use
//        final String Wifi_name = ssid;
//        final WifiNetworkSuggestion.Builder builder = new WifiNetworkSuggestion.Builder().setSsid(ssid).setWpa2Passphrase(password);
//        WifiNetworkSuggestion suggestion = builder.build();
//        final List<WifiNetworkSuggestion> suggestionsList = new ArrayList<>();
//        suggestionsList.add(suggestion);
//        WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
//        final int status = manager.addNetworkSuggestions(suggestionsList);
//        if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
//            logToUi( Wifi_name + "连接成功");
//        } else {
//            logToUi( Wifi_name + "连接失败");
//        }

       // connect way 1 : can't use network / 连接两次之后可上网 自动连接
        try {
            final String Wifi_name = ssid;
            WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder();
            builder.setSsid(ssid);
            builder.setWpa2Passphrase(password);
            WifiNetworkSpecifier wifiNetworkSpecifier = builder.build();
            final NetworkRequest request =
                    new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
//                            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                            .setNetworkSpecifier(wifiNetworkSpecifier)
                            .build();
            final ConnectivityManager connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

            final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    connectivityManager.bindProcessToNetwork(network);
                    logToUi(Wifi_name + "连接成功");
                    Log.d(TAG, "连接成功");
                }

                @Override
                public void onUnavailable() {
                    logToUi(Wifi_name + "连接失败");
                    Log.d(TAG, "连接失败");
                }
            };
            //connectivityManager.registerNetworkCallback(request, networkCallback);
            connectivityManager.requestNetwork(request, networkCallback);
        } catch (SecurityException e){
            e.printStackTrace();
        } catch (IllegalArgumentException e){
            e.printStackTrace();
        } catch (RuntimeException e){
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private class WifiScanReceiver extends BroadcastReceiver {

        private List<ScanResult> find80211mcSupportedAccessPoints(
                @NonNull List<ScanResult> originalList) {
            List<ScanResult> newList = new ArrayList<>();

            for (ScanResult scanResult : originalList) {

//                if (scanResult.is80211mcResponder()) {
//                    newList.add(scanResult);
//                }
                newList.add(scanResult);
                if (newList.size() >= RangingRequest.getMaxPeers()) {
                    break;
                }
            }
            return newList;
        }

        // This is checked via mLocationPermissionApproved boolean
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            Boolean wifi_state = true;
            context.startActivity(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY));
            wifi_state = mWifiManager.isWifiEnabled();
            Log.e(TAG,"WIFI State:" + wifi_state);
            mWifiManager.setWifiEnabled(true);
            wifi_state = mWifiManager.isWifiEnabled();
            Log.e(TAG,"WIFI State:" + wifi_state);
            List<ScanResult> scanResults = mWifiManager.getScanResults();
            if (scanResults != null) {

                if (mLocationPermissionApproved) {
                    mAccessPointsSupporting80211mc = find80211mcSupportedAccessPoints(scanResults);

                    mAdapter.swapData(mAccessPointsSupporting80211mc);

                    logToUi(
                            scanResults.size()
                                    + " APs discovered, "
                                    + mAccessPointsSupporting80211mc.size()
                                    + " RTT capable.");
                    //Log.e(TAG,"wifi0 " +scanResults.get(0).SSID +" BSSID:" +scanResults.get(0).BSSID);
                    //Log.e(TAG,"wifi1 " +scanResults.get(1).SSID +" BSSID:" +scanResults.get(1).BSSID);
                    for ( ScanResult scanResult : scanResults) {
                        Log.e(TAG,"WIFI:" + scanResult.SSID);
                        if( scanResult.SSID.equals("MERCURY808") ){
                            Log.e(TAG,"Find MERCURY808 Here");
                            //connectnetwork(scanResult.SSID, scanResult.BSSID, "biedaohao0.");
                        }
                    }
                } else {
                    // TODO (jewalker): Add Snackbar regarding permissions
                    Log.d(TAG, "Permissions not allowed.");
                }
            }
        }
    }
}
