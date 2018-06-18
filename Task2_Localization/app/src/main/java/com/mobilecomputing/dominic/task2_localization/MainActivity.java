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
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.Button;
import android.widget.FrameLayout;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

public class MainActivity extends Activity {

    private int[] markerPosX = new int[]{30,150,260,370,460,530,370,630,740,840};
    private int[] markerPosY = new int[]{920,920,820,920,820,820,820,820,820,820};

    private TextView textCellNr;
    private TextView textRecording;
    private TextView textCellProbabilities;
    private TextView textMarker;

    private int nr_of_scans = 0;

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

    private void refreshWifiData() {
        try {
            wifi.startScan();

            List<ScanResult> results = wifi.getScanResults();

            if (currentCellNr == -1)
                return;

            for (int n = 0; n < results.size(); n++) {
                ScanResult resultAP = results.get(n);
                // SSID contains name of AP and level contains RSSI
                System.out.printf("Wifi", "SSID = " + resultAP.SSID + "; BSSID = " + resultAP.BSSID.replace(':', '_') + "; RSSI =  " + resultAP.level);

                if (accessPoints.containsKey(resultAP.BSSID.replace(':', '_')) == false)
                    accessPoints.put(resultAP.BSSID.replace(':', '_'), new HashMap<Integer, List<Double>>());

                if (accessPoints.get(resultAP.BSSID.replace(':', '_')).containsKey(currentCellNr) == false)
                    accessPoints.get(resultAP.BSSID.replace(':', '_')).put(currentCellNr, new ArrayList<Double>());

                accessPoints.get(resultAP.BSSID.replace(':', '_')).get(currentCellNr).add(new Double(resultAP.level));

            }

        } catch (Exception ex) {

        }
    }

    private static HashMap sortByValues(HashMap map) {
        List list = new LinkedList(map.entrySet());
        // Defined Custom Comparator here
        Collections.sort(list, new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((Comparable) ((Map.Entry) (o2)).getValue())
                        .compareTo(((Map.Entry) (o1)).getValue());
            }
        });

        // Here I am copying the sorted list in HashMap
        // using LinkedHashMap to preserve the insertion order
        HashMap sortedHashMap = new LinkedHashMap();
        for (Iterator it = list.iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            sortedHashMap.put(entry.getKey(), entry.getValue());
        }
        return sortedHashMap;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textCellNr = (TextView) findViewById(R.id.text_cellnr);
        textRecording = (TextView) findViewById(R.id.label_recording);
        textCellProbabilities = (TextView) findViewById(R.id.label_cellProbability);

        textMarker = (TextView) findViewById(R.id.label_marker);

        textMarker.setX(markerPosX[0]);
        textMarker.setY(markerPosY[0]);

       // textMarker.layout(10,500,0,0);


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

                if (doLocalization == false) {
                    return;
                }
                currentResults = new HashMap<>();
                wifi.startScan();
                List<ScanResult> results = wifi.getScanResults();
                for (int i = 0; i < results.size(); i++) {
                    currentResults.put(results.get(i).BSSID.replace(':', '_'), (int) (128.0 + results.get(i).level));

                }

                if(currentResults == null)
                    return;

                //TODO: maybe find better way to sort and trim hashmap
                //only use 4 strongest values
                HashMap<String, Integer> map = sortByValues(currentResults);
                HashMap trimmedHashMap = new LinkedHashMap();
                Iterator it = map.entrySet().iterator();
                int count = 0;
                while (it.hasNext() && count < 4) {
                    Map.Entry pair = (Map.Entry)it.next();
                    System.out.println(pair.getKey() + " = " + pair.getValue());
                    trimmedHashMap.put(pair.getKey(), pair.getValue());
                    it.remove(); // avoids a ConcurrentModificationException
                    count++;
                }
                currentResults = trimmedHashMap;


                //loop over access points
                for (HashMap.Entry<String, Integer> entry : currentResults.entrySet()) {
                    for (int cell = 1; cell <= CELL_NR; cell++) {
                        double newCellProbability = cellProbabilities.get(cell) * accessPointsNormalizedHistogram.get(entry.getKey()).get(cell)[entry.getValue()];

                        // Any cell may contain the agent with a non-zero probability
                        double chance = 1/(cellProbabilities.size() * 1000.0);
                        if (newCellProbability < chance)
                            newCellProbability = chance;
                        cellProbabilities.put(cell, newCellProbability);


                    }
                    // normalize
                    double sumColumn = 0.0;
                    for (HashMap.Entry<Integer, Double> entryCell : cellProbabilities.entrySet())
                        sumColumn += entryCell.getValue();
                    for (HashMap.Entry<Integer, Double> entryCell : cellProbabilities.entrySet())
                        cellProbabilities.put(entryCell.getKey(), entryCell.getValue() / sumColumn);
                }



                currentResults = null;

                //TODO: mark cell with highest probability
                HashMap<String, Integer> tmp = sortByValues(cellProbabilities);
                int cell_nr_to_mark = (int)tmp.keySet().toArray()[0];


                textMarker.setX(markerPosX[cell_nr_to_mark - 1]);
                textMarker.setY(markerPosY[cell_nr_to_mark - 1]);

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

                    wifi.startScan();

                    /*for(int a = 0; a < 1000; a++)
                        refreshWifiData();*/

                    textRecording.setText("RECORDING CELL " + currentCellNr);



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
                textRecording.setText("RECORDING CELL " + currentCellNr + " DONE" + "\nScans: " + nr_of_scans);
                currentCellNr = -1;
                nr_of_scans = 0;

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
            if(results.size() == 0)
                return;



            try {
                for (int n = 0; n < results.size(); n++) {
                    ScanResult resultAP = results.get(n);
                    // SSID contains name of AP and level contains RSSI
                    System.out.printf("Wifi", "SSID = " + resultAP.SSID + "; BSSID = " + resultAP.BSSID.replace(':', '_') + "; RSSI =  " + resultAP.level);

                    if (accessPoints.containsKey(resultAP.BSSID.replace(':', '_')) == false)
                        accessPoints.put(resultAP.BSSID.replace(':', '_'), new HashMap<Integer, List<Double>>());

                    if (accessPoints.get(resultAP.BSSID.replace(':', '_')).containsKey(currentCellNr) == false)
                        accessPoints.get(resultAP.BSSID.replace(':', '_')).put(currentCellNr, new ArrayList<Double>());

                    accessPoints.get(resultAP.BSSID.replace(':', '_')).get(currentCellNr).add(new Double(resultAP.level));


                }
                textRecording.setText("RECORDING CELL " + currentCellNr + "\nScans: " + ++nr_of_scans);
                wifi.startScan();
            } catch (Exception e) {
            }
        }
    } // End of class WifiReceiver
}






