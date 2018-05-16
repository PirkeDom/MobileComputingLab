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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private TextView textCellProbabilities;

    private WifiManager wifi;
    private int currentCellNr = -1;
    private boolean exportData = false;

    private boolean doLocalization = false;

    private HashMap<String, HashMap<Integer, List<Double>>> accessPoints = null;
    private HashMap<String, HashMap<Integer, double[]>> accessPointsNormalizedHistogram = null;

    private HashMap<String, Integer> currentResults = null;

    private HashMap<Integer, Double> cellProbabilities;

    private String filepath = "MyFileStorage";

    private int CELL_NR = 0;
    private int RSS_NR = 256;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textCellNr = (TextView) findViewById(R.id.text_cellnr);
        textRecording = (TextView) findViewById(R.id.label_recording);
        textCellProbabilities = (TextView) findViewById(R.id.label_cellProbability);

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

                    accessPointsNormalizedHistogram = new HashMap<>();


                    for (HashMap.Entry<String, HashMap<Integer, List<Double>>> entryAP : accessPoints.entrySet()) {
                        String keyAP = entryAP.getKey();
                        HashMap<Integer, List<Double>> valueAP = entryAP.getValue();

                        File myExternalFile = new File(getExternalFilesDir(filepath), keyAP + ".txt");

                        try {
                            FileOutputStream fos = new FileOutputStream(myExternalFile, true);
                            fos.write(("cellNr;rss 0-255\n").getBytes());

                            HashMap<Integer, double[]> cellnormalizedHistogram = new HashMap<>();
                            CELL_NR = valueAP.size();

                            for (HashMap.Entry<Integer, List<Double>> entryCell : valueAP.entrySet()) {
                                Integer keyCell = entryCell.getKey();
                                List<Double> valueCell = entryCell.getValue();

                                Double[] tmp = valueCell.toArray(new Double[valueCell.size()]);
                                double[] rssValues = new double[128];
                                for (int i = 0; i < tmp.length; i++) {
                                    int rss = (int) (128.0 + tmp[i]);
                                    rssValues[rss]++;
                                }
                                String rssString = "";
                                for (int i = 0; i < rssValues.length; i++) {
                                    rssValues[i] = rssValues[i] / tmp.length;
                                    rssString += rssValues[i] + ";";
                                }

                                fos.write((keyCell.toString() + ";" + rssString + "\n").getBytes());

                                cellnormalizedHistogram.put(keyCell, rssValues);
                            }
                            fos.close();

                            accessPointsNormalizedHistogram.put(keyAP, cellnormalizedHistogram);

                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    System.out.println();

                }
            }
        });

        final Button buttonlocateMe = (Button)findViewById(R.id.button_locateme);
        buttonlocateMe.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                doLocalization = true;

                while(currentResults == null);

                HashMap<String, HashMap<Integer, double[]>> accessPointsPosterior = new HashMap<>();


                for (HashMap.Entry<String, HashMap<Integer, double[]>> entryAP : accessPointsNormalizedHistogram.entrySet()) {
                    String keyAP = entryAP.getKey();
                    if(currentResults.containsKey(keyAP) == false)
                        continue;

                    HashMap<Integer, double[]> valueAP = entryAP.getValue();

                    for (HashMap.Entry<Integer, double[]> entryCell : valueAP.entrySet()) {
                        Integer keyCell = entryCell.getKey();
                        double[] valueCell = entryCell.getValue();
                        for(int i = 0; i < valueCell.length; i++)
                        {
                            double newCellProbability = cellProbabilities.get(keyCell) * valueCell[currentResults.get(keyAP)] / 1.0; //TODO: find probability p(rss_j)
                            cellProbabilities.put(keyCell, newCellProbability);
                        }
                    }
                }
                currentResults = null;

                textCellProbabilities.setText(getResources().getString(R.string.label_cellProbability, cellProbabilities.get(1), cellProbabilities.get(2), cellProbabilities.get(3),
                        cellProbabilities.get(4), cellProbabilities.get(5), cellProbabilities.get(6), cellProbabilities.get(7), cellProbabilities.get(8), cellProbabilities.get(9),
                        cellProbabilities.get(10)));
            }

        });
        final Button buttoninitialBelieve = (Button)findViewById(R.id.button_initielBelieve);
        buttoninitialBelieve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cellProbabilities = new HashMap<Integer, Double>();
                for(int i = 0; i < CELL_NR; i++)
                    cellProbabilities.put(i + 1, 1.0 / CELL_NR);

                textCellProbabilities.setText(getResources().getString(R.string.label_cellProbability, cellProbabilities.get(1), cellProbabilities.get(2), cellProbabilities.get(3),
                        cellProbabilities.get(4), cellProbabilities.get(5), cellProbabilities.get(6), cellProbabilities.get(7), cellProbabilities.get(8), cellProbabilities.get(9),
                        cellProbabilities.get(10)));
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

            if (doLocalization == true) {
                doLocalization = false;
                List<ScanResult> results = wifi.getScanResults();
                for (int i = 0; i < results.size(); i++) {
                    currentResults.put(results.get(i).SSID, (int) (128.0 + results.get(i).level));
                }
                return;
            }

            if (currentCellNr == -1)
                return;

            List<ScanResult> results = wifi.getScanResults();
            //while(results.size() == 0)
                //results = wifi.getScanResults();

            try {
                for (int n = 0; n < results.size(); n++) {
                    ScanResult resultAP = results.get(n);
                    // SSID contains name of AP and level contains RSSI
                    System.out.printf("Wifi", "SSID = " + resultAP.SSID + "; RSSI =  " + resultAP.level);

                    if (accessPoints.containsKey(resultAP.SSID) == false)
                        accessPoints.put(resultAP.SSID, new HashMap<Integer, List<Double>>());

                    if (accessPoints.get(resultAP.SSID).containsKey(currentCellNr) == false)
                        accessPoints.get(resultAP.SSID).put(currentCellNr, new ArrayList<Double>());

                    accessPoints.get(resultAP.SSID).get(currentCellNr).add(new Double(resultAP.level));

                }
            } catch (Exception e) {
            }
        }
    } // End of class WifiReceiver
}






