package com.mobilecomputing.dominic.task2_localization;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Stream;

public class MainActivity extends Activity {

    private TextView textCellNr;
    private TextView textRecording;

    private WifiManager wifi;
    private int currentCellNr = -1;
    private boolean exportData = false;

    private HashMap<String, HashMap<Integer, List<Double>>> accessPoints = null;

    private int CELL_NR = 10;
    private int RSS_NR = 256;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textCellNr = (TextView) findViewById(R.id.text_cellnr);
        textRecording = (TextView) findViewById(R.id.label_recording);

        // Initialize WifiManager (contains API for managing all aspects of Wi-Fi connectivity)
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        // If Wifi is switched off, enable it
        if (wifi.isWifiEnabled() == false)
            wifi.setWifiEnabled(true);

        // Register a receiver where scan results are made available
        registerReceiver (new WifiReceiver(), new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        // begin scan
        wifi.startScan();

        final Button buttoncreateFile = (Button)findViewById(R.id.button_createFile);
        buttoncreateFile.setOnClickListener(new View.OnClickListener(){
            @TargetApi(Build.VERSION_CODES.N)
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View view) {
                if(buttoncreateFile.getText().toString() == getString(R.string.button_createFile1)) {
                    buttoncreateFile.setText(R.string.button_createFile2);

                    exportData = true;

                    // clear lists with data
                    accessPoints = new HashMap<>();

                }
                else {
                    buttoncreateFile.setText(R.string.button_createFile1);

                    exportData = false;

                    HashMap<String, HashMap<Integer, List<Double>>> accessPointsNormalizedHistogram = new HashMap<>();


                    for (HashMap.Entry<String, HashMap<Integer, List<Double>>> entryAP : accessPoints.entrySet()) {
                        String keyAP = entryAP.getKey();
                        HashMap<Integer, List<Double>> valueAP = entryAP.getValue();

                        HashMap<Integer, List<Double>> cellnormalizedHistogram = new HashMap<>();

                        for (HashMap.Entry<Integer, List<Double>> entryCell : valueAP.entrySet()) {
                            Integer keyCell = entryCell.getKey();
                            List<Double> valueCell = entryCell.getValue();

                            Double[] normalizedData = MathUtils.normalizeToSumUpTo(valueCell.toArray(new Double[valueCell.size()]), 256.0);

                            Histogram hist = new Histogram(MathUtils.toPrimitiveDouble(normalizedData), 256);
                            double[] histogramData = hist.getHistArray();



                            //) valueCell = ...;
                            //TODO: normalize and calculate histrogram ov valueCell list
                            cellnormalizedHistogram.put(keyCell, valueCell);
                        }
                        accessPointsNormalizedHistogram.put(keyAP, cellnormalizedHistogram);
                    }

                    System.out.println();
                    //TODO: normalize data; calculate histogram; export data
                }
            }
        });

        final Button buttonstart = (Button)findViewById(R.id.button_start);
        buttonstart.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if(exportData == false)
                    return;
                try {
                    currentCellNr = Integer.parseInt(textCellNr.getText().toString());
                    textRecording.setText("RECORDING");
                }
                catch (Exception ex){
                    currentCellNr = -1;
                }
            }
        });

        final Button buttonstop = (Button)findViewById(R.id.button_stop);
        buttonstop.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                currentCellNr = -1;
                textRecording.setText("");
            }
        });

    }

    // WifiReceiver class is subclass of WifiActivity
    class WifiReceiver extends BroadcastReceiver {
        // An access point scan has completed and results are sent here
        public void onReceive(Context c, Intent intent) {
            if(currentCellNr == -1)
                return;

            List<ScanResult> results = wifi.getScanResults();
            //while(results.size() == 0)
            //    results = wifi.getScanResults();

            try {
                for (int n = 0; n < results.size (); n++) {
                    ScanResult resultAP = results.get(n);
                    // SSID contains name of AP and level contains RSSI
                    System.out.printf("Wifi", "SSID = " + resultAP.SSID + "; RSSI =  " + resultAP.level);

                    if(accessPoints.containsKey(resultAP.SSID) == false)
                        accessPoints.put(resultAP.SSID, new HashMap<Integer, List<Double>>());

                    if(accessPoints.get(resultAP.SSID).containsKey(currentCellNr) == false)
                        accessPoints.get(resultAP.SSID).put(currentCellNr, new ArrayList<Double>());

                    accessPoints.get(resultAP.SSID).get(currentCellNr).add(new Double(resultAP.level));

                }
            }
            catch (Exception e) {  }
        }
    } // End of class WifiReceiver
}






