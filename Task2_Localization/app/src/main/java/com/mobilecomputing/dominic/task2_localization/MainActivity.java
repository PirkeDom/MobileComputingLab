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

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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

    private int CELL_NR = 3; //TODO: IMPORTANT: set right cell number
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

                    // deleote old files
                    File dir = getExternalFilesDir(filepath);
                    for (File file : dir.listFiles()) {
                        file.delete();
                    }

                    doLocalization = false;

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
                            //if(myExternalFile.exists())
                            //    myExternalFile.delete();

                            FileOutputStream fos = new FileOutputStream(myExternalFile, false);
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

                            //TODO: add e.g. gaussion distribution to the values with index 0 to 128 (otherwise the values is...
                            //TODO: ...almost evertime multiplied by 0)
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


                if(currentResults == null)
                    return;

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
                            double sumColumn = 0.0;
                            for (HashMap.Entry<Integer, double[]> tmp : valueAP.entrySet())
                                sumColumn += tmp.getValue()[currentResults.get(keyAP)];

                            double newCellProbability = cellProbabilities.get(keyCell) * valueCell[currentResults.get(keyAP)] / sumColumn; //TODO: find probability p(rss_j)
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
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View view) {

                //load data from files (one per AP)

                accessPointsNormalizedHistogram = new HashMap<>();
                File dir = getExternalFilesDir(filepath);
                int l = dir.listFiles().length;
                for (File file : dir.listFiles()) {
                    HashMap<Integer, double[]> valueAP = new HashMap<Integer, double[]>();
                    FileInputStream is;
                    BufferedReader reader;

                        if (file.exists()) {
                            try {
                                is = new FileInputStream(file);
                                reader = new BufferedReader(new InputStreamReader(is));
                                //header line
                                String line = reader.readLine();
                                line = reader.readLine();
                                while (line != null) {
                                    String[] dataArrayStrings = line.split(";");

                                    double[] dataArray = new double[dataArrayStrings.length - 1];
                                    //start with index 1 -> index 0 is cell number!!
                                    for (int i = 1; i < dataArrayStrings.length; i++)
                                        dataArray[i-1] = Double.parseDouble(dataArrayStrings[i]);
                                    valueAP.put(Integer.parseInt(dataArrayStrings[0]), dataArray);

                                    line = reader.readLine();
                                }
                            }catch (Exception ex){
                                System.out.printf(ex.getMessage());

                            }
                            //read to end of file and then put data into hashmap
                            try {
                                accessPointsNormalizedHistogram.put(file.getName().replace(".txt", ""), valueAP);
                            }
                            catch (Exception ex){
                                System.out.printf(ex.getMessage());
                            }
                        }

                }

                doLocalization = true;

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
                currentResults = new HashMap<>();
                List<ScanResult> results = wifi.getScanResults();
                for (int i = 0; i < results.size(); i++) {
                    currentResults.put(results.get(i).BSSID, (int) (128.0 + results.get(i).level));

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
                    System.out.printf("Wifi", "SSID = " + resultAP.SSID + "; BSSID = " + resultAP.BSSID + "; RSSI =  " + resultAP.level);

                    if (accessPoints.containsKey(resultAP.BSSID) == false)
                        accessPoints.put(resultAP.BSSID, new HashMap<Integer, List<Double>>());

                    if (accessPoints.get(resultAP.BSSID).containsKey(currentCellNr) == false)
                        accessPoints.get(resultAP.BSSID).put(currentCellNr, new ArrayList<Double>());

                    accessPoints.get(resultAP.BSSID).get(currentCellNr).add(new Double(resultAP.level));

                }
            } catch (Exception e) {
            }
        }
    } // End of class WifiReceiver
}






