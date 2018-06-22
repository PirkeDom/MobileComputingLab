package com.mobilecomputing.dominic.task2_localization;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity implements SensorEventListener {

    private int[] markerPosX = new int[]{30,150,260,370,460,530,370,630,740,840};
    private int[] markerPosY = new int[]{920,920,820,920,820,820,820,820,820,820};

    private TextView textCellNr;
    private TextView textRecording;
    private TextView textCellProbabilities;
    private TextView textMarker;


    //activity variables
    private Sensor mSensorAccelerometer;
    private Sensor mSensorRotation;
    private SensorEvent sensorEvent;
    int sensorDataCounter = 0;
    private float[][] buffer;
    private int buffercounter;
    private TrainRecord[] trainingSet;
    private TestRecord[] testingSet;
    private boolean doRecording = false;
    File myExternalFile;
    private TextView textPredictedActivity;
    private TextView textPredictedDirection;
    private SensorManager mSensorManager;
    private Rotation calibrationDirection = null;
    private Rotation latestDirection;
    private boolean initiateDirectoin = false;

    //end of activity variables

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

        textCellNr = findViewById(R.id.text_cellnr);
        textRecording = findViewById(R.id.label_recording);
        textCellProbabilities = findViewById(R.id.label_cellProbability);

        textPredictedActivity = (TextView) findViewById(R.id.label_predicted_activity);
        textPredictedDirection = (TextView) findViewById(R.id.label_predicted_direction);

        textCellProbabilities.setText(getResources().getString(R.string.label_cellProbability, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));

        textMarker = findViewById(R.id.label_marker);

        textMarker.setX(markerPosX[0]);
        textMarker.setY(markerPosY[0]);

        // textMarker.layout(10,500,0,0);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        buffer = new float[20][3];
        buffercounter = 0;
        knn(getExternalFilesDir("classification") + "/activity_train.txt", getExternalFilesDir("classification") + "/activity_test.txt",1,2);



        // Initialize WifiManager (contains API for managing all aspects of Wi-Fi connectivity)
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        // If Wifi is switched off, enable it
        if (wifi.isWifiEnabled() == false)
            wifi.setWifiEnabled(true);

        // Register a receiver where scan results are made available
        registerReceiver (new WifiReceiver(), new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        // begin scan
        wifi.startScan();


        final Button buttoncreateFile = findViewById(R.id.button_createFile);
        buttoncreateFile.setOnClickListener(new View.OnClickListener(){
            @TargetApi(Build.VERSION_CODES.N)
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View view) {
                if(buttoncreateFile.getText().toString().equals(getString(R.string.button_createFile1))) {
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

                            FileOutputStream fos = new FileOutputStream(myExternalFile, false);
                            fos.write(("cellNr;rss 0-255\n").getBytes());

                            HashMap<Integer, double[]> cellnormalizedHistogram = new HashMap<>();

                            for (HashMap.Entry<Integer, List<Double>> entryCell : valueAP.entrySet()) {
                                Integer keyCell = entryCell.getKey();
                                List<Double> valueCell = entryCell.getValue();

                                Double[] tmp = valueCell.toArray(new Double[valueCell.size()]);
                                double[] rssValues = new double[128];
                                for (int i = 0; i < tmp.length; i++) {
                                    int rss = (int) (128.0 + tmp[i]);
                                    rssValues[rss]++;
                                }
                                //remove gaps with zeros (fill up with average of previous and next index)
                                for (int i = 1; i < rssValues.length - 1; i++) {
                                    if(rssValues[i] == 0.0 && rssValues[i-1] > 0 && rssValues[i+1] > 0)
                                        rssValues[i] = (rssValues[i-1] + rssValues[i+1]) / 2;
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

                            //TODO: maybe add e.g. gaussion distribution to the values with index 0 to 128
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
                try {

                    if (doLocalization == false) {
                        return;
                    }
                    wifi.startScan();

                    /*currentResults = new HashMap<>();
                    List<ScanResult> results = wifi.getScanResults();
                    for (int i = 0; i < results.size(); i++) {
                        currentResults.put(results.get(i).BSSID.replace(':', '_'), (int) (128.0 + results.get(i).level));
                    }*/

                    if (currentResults == null)
                        return;

                    //not best way to sort and trim hashmap (but works...)
                    //only use 4 strongest values
                    HashMap<String, Integer> map = sortByValues(currentResults);
                    HashMap trimmedHashMap = new LinkedHashMap();
                    Iterator it = map.entrySet().iterator();
                    int count = 0;
                    while (it.hasNext() && count < 4) {
                        Map.Entry pair = (Map.Entry) it.next();
                        System.out.println(pair.getKey() + " = " + pair.getValue());
                        trimmedHashMap.put(pair.getKey(), pair.getValue());
                        it.remove(); // avoids a ConcurrentModificationException
                        count++;
                    }
                    currentResults = trimmedHashMap;

                    //loop oover current results (4 strongest access points)
                    for (HashMap.Entry<String, Integer> entry : currentResults.entrySet()) {
                        //loop over cells
                        for (int cell = 1; cell <= CELL_NR; cell++) {
                            double newCellProbability = cellProbabilities.get(cell) * accessPointsNormalizedHistogram.get(entry.getKey()).get(cell)[entry.getValue()];

                            // Any cell may contain the agent with a non-zero probability
                            double chance = 1 / (cellProbabilities.size() * 1000.0);
                            if (newCellProbability < chance)
                                newCellProbability = chance;
                            cellProbabilities.put(cell, newCellProbability);

                        }
                        // normalize
                        double sumColumn = 0.0;
                        for (HashMap.Entry<Integer, Double> entryCell : cellProbabilities.entrySet())
                            sumColumn += entryCell.getValue();
                        for (HashMap.Entry<Integer, Double> entryCell : cellProbabilities.entrySet()) {
                            if (sumColumn > 0)
                                cellProbabilities.put(entryCell.getKey(), entryCell.getValue() / sumColumn);
                        }
                    }

                    currentResults = null;

                    HashMap<String, Integer> tmp = sortByValues(cellProbabilities);
                    int cell_nr_to_mark = (int) tmp.keySet().toArray()[0];

                    textMarker.setX(markerPosX[cell_nr_to_mark - 1]);
                    textMarker.setY(markerPosY[cell_nr_to_mark - 1]);

                    textCellProbabilities.setText(getResources().getString(R.string.label_cellProbability, cellProbabilities.get(1), cellProbabilities.get(2), cellProbabilities.get(3),
                            cellProbabilities.get(4), cellProbabilities.get(5), cellProbabilities.get(6), cellProbabilities.get(7), cellProbabilities.get(8), cellProbabilities.get(9),
                            cellProbabilities.get(10)));

                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }
            }

        });
        final Button buttoninitialBelieve = (Button)findViewById(R.id.button_initielBelieve);
        buttoninitialBelieve.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View view) {

                initiateDirectoin = true;

                //load data from files (one per AP)
                accessPointsNormalizedHistogram = new HashMap<>();
                File dir = getExternalFilesDir(filepath);
                int l = dir.listFiles().length;
                for (File file : dir.listFiles()) {
                    HashMap<Integer, double[]> valueAP = new HashMap<Integer, double[]>();
                    FileInputStream is;
                    BufferedReader reader;
                    try {
                        if (file.exists()) {

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
                                    dataArray[i - 1] = Double.parseDouble(dataArrayStrings[i]);
                                valueAP.put(Integer.parseInt(dataArrayStrings[0]), dataArray);

                                line = reader.readLine();
                            }
                            accessPointsNormalizedHistogram.put(file.getName().replace(".txt", ""), valueAP);
                        }
                    }
                    catch (Exception ex){
                        System.out.printf(ex.getMessage());
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
                System.out.println(e.getMessage());
            }
        }
    } // End of class WifiReceiver

    @Override
    protected void onStart() {
        super.onStart();

        if(mSensorAccelerometer != null){
            mSensorManager.registerListener(this,mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this,mSensorRotation, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSensorManager.unregisterListener(this);
    }

    /** https://github.com/umranium/Snippets/blob/master/SubjectDirection.java
     * Updates the direction using the latest sensor values obtained
     * from Sensor.TYPE_ROTATION_VECTOR
     *
     * @param sensorValues values obtained from the sensor
     */
    public void onSensorValues(float[] sensorValues) {
        //only write z quaternion into rotation (phone has to be held always with screen on top)
        Rotation rotation = new Rotation(
                (double) sensorValues[3], // quaternion scalar
                0, //(double) sensorValues[0], // quaternion x
                0, //(double) sensorValues[1], // quaternion y
                (double) sensorValues[2], // quaternion z
                false); // no need to normalise

        if (calibrationDirection == null && initiateDirectoin == true) {
            // Save the first sensor value obtained as the calibration value
            calibrationDirection = rotation;
            initiateDirectoin = false;
        } else if(calibrationDirection != null){
            // Apply the reverse of the calibration direction to the newly
            //  obtained direction to obtain the direction the user is facing
            //  relative to his/her original direction
            latestDirection = calibrationDirection.applyInverseTo(rotation);
            double angle = latestDirection.getAngle();

            double angleDegree = angle * 180.0 / Math.PI;
            textPredictedDirection.setText(getResources().getString(R.string.label_predicted_direction, angleDegree));
            double t = latestDirection.getQ0();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {


        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            sensorEvent = event;

            sensorDataCounter++;
            printSensorData();

            if (sensorDataCounter % 10 == 0) {
                //TODO: calculate mean of all 20 values and put it into knn
                float sumX = 0, sumY = 0, sumZ = 0;
                for (int i = 0; i < 20; i++) {
                    sumX += buffer[i][0];
                    sumY += buffer[i][1];
                    sumZ += buffer[i][2];
                }

                TestRecord recordToClassify = new TestRecord(new double[]{sumX / 20, sumY / 20, sumZ / 20, 0.000000}, 0);

                TrainRecord[] neighbors = findKNearestNeighbors(trainingSet, recordToClassify, 3, new EuclideanDistance());
                int classLabel = classify(neighbors);
                String predictedActivity = "";
                switch (classLabel) {
                    case 0:
                        predictedActivity = "Running";
                        break;
                    case 1:
                        predictedActivity = "Walking";
                        break;
                    case 2:
                        predictedActivity = "Sitting";
                        break;
                }
                textPredictedActivity.setText(getResources().getString(R.string.label_predicted_activity, predictedActivity));

            }
        }
        else if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            onSensorValues(event.values);
        }


    }

    private void printSensorData(){
        if(sensorEvent == null)
            return;
        float[] currentValue = sensorEvent.values;
        int sensorType = sensorEvent.sensor.getType();

        switch (sensorType) {
            // Event came from the light sensor.
            case Sensor.TYPE_ACCELEROMETER:
                //Store data in memory, file, or in other data structure


                buffer[buffercounter++] = currentValue;
                buffercounter = buffercounter % 20;

                if(doRecording) {
                    try {
                        FileOutputStream fos = new FileOutputStream(myExternalFile, true);
                        fos.write((Double.toString(currentValue[0]) + ";").getBytes());
                        fos.write((Double.toString(currentValue[1]) + ";").getBytes());
                        fos.write(Double.toString(currentValue[2]).getBytes());
                        fos.write('\n');
                        fos.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                break;
            case Sensor.TYPE_PROXIMITY:
                //mTextSensorProximity.setText(getResources().getString(R.string.label_proximity, currentValue));
                break;

            default:
                // do nothing
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    ///k-NN
    public void knn(String trainingFile, String testFile, int K, int metricType){
        //get the current time
        final long startTime = System.currentTimeMillis();

        // make sure the input arguments are legal
        if(K <= 0){
            System.out.println("K should be larger than 0!");
            return;
        }

        // metricType should be within [0,2];
        if(metricType > 2 || metricType <0){
            System.out.println("metricType is not within the range [0,2]. Please try again later");
            return;
        }

        //TrainingFile and testFile should be the same group
        String trainGroup = extractGroupName(trainingFile);
        String testGroup = extractGroupName(testFile);

        if(!trainGroup.equals(testGroup)){
            System.out.println("trainingFile and testFile are illegal!");
            return;
        }


        try {
            //read trainingSet and testingSet
            trainingSet =  FileManager.readTrainFile(trainingFile);
            testingSet =  FileManager.readTestFile(testFile);

            //determine the type of metric according to metricType
            Metric metric;
            if(metricType == 0)
                metric = new CosineSimilarity();
            else if(metricType == 1)
                metric = new L1Distance();
            else if (metricType == 2)
                metric = new EuclideanDistance();
            else{
                System.out.println("The entered metric_type is wrong!");
                return;
            }

            //test those TestRecords one by one
            int numOfTestingRecord = testingSet.length;
            for(int i = 0; i < numOfTestingRecord; i ++){
                TrainRecord[] neighbors = findKNearestNeighbors(trainingSet, testingSet[i], K, metric);
                int classLabel = classify(neighbors);
                testingSet[i].predictedLabel = classLabel; //assign the predicted label to TestRecord
            }

            //calculate the accuracy
            int correctPrediction = 0;
            for(int j = 0; j < numOfTestingRecord; j ++){
                if(testingSet[j].predictedLabel == testingSet[j].classLabel)
                    correctPrediction ++;
            }

            //Output a file containing predicted labels for TestRecords
            String predictPath = FileManager.outputFile(testingSet, trainingFile);
            System.out.println("The prediction file is stored in "+predictPath);
            System.out.println("The accuracy is "+((double)correctPrediction / numOfTestingRecord)*100+"%");

            //print the total execution time
            final long endTime = System.currentTimeMillis();
            System.out.println("Total excution time: "+(endTime - startTime) / (double)1000 +" seconds.");


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // Find K nearest neighbors of testRecord within trainingSet
    static TrainRecord[] findKNearestNeighbors(TrainRecord[] trainingSet, TestRecord testRecord,int K, Metric metric){
        int NumOfTrainingSet = trainingSet.length;
        assert K <= NumOfTrainingSet : "K is lager than the length of trainingSet!";

        //Update KNN: take the case when testRecord has multiple neighbors with the same distance into consideration
        //Solution: Update the size of container holding the neighbors
        TrainRecord[] neighbors = new TrainRecord[K];

        //initialization, put the first K trainRecords into the above arrayList
        int index;
        for(index = 0; index < K; index++){
            trainingSet[index].distance = metric.getDistance(trainingSet[index], testRecord);
            neighbors[index] = trainingSet[index];
        }

        //go through the remaining records in the trainingSet to find K nearest neighbors
        for(index = K; index < NumOfTrainingSet; index ++){
            trainingSet[index].distance = metric.getDistance(trainingSet[index], testRecord);

            //get the index of the neighbor with the largest distance to testRecord
            int maxIndex = 0;
            for(int i = 1; i < K; i ++){
                if(neighbors[i].distance > neighbors[maxIndex].distance)
                    maxIndex = i;
            }

            //add the current trainingSet[index] into neighbors if applicable
            if(neighbors[maxIndex].distance > trainingSet[index].distance)
                neighbors[maxIndex] = trainingSet[index];
        }

        return neighbors;
    }

    // Get the class label by using neighbors
    static int classify(TrainRecord[] neighbors){
        //construct a HashMap to store <classLabel, weight>
        HashMap<Integer, Double> map = new HashMap<Integer, Double>();
        int num = neighbors.length;

        for(int index = 0;index < num; index ++){
            TrainRecord temp = neighbors[index];
            int key = temp.classLabel;

            //if this classLabel does not exist in the HashMap, put <key, 1/(temp.distance)> into the HashMap
            if(!map.containsKey(key))
                map.put(key, 1 / temp.distance);

                //else, update the HashMap by adding the weight associating with that key
            else{
                double value = map.get(key);
                value += 1 / temp.distance;
                map.put(key, value);
            }
        }

        //Find the most likely label
        double maxSimilarity = 0;
        int returnLabel = -1;
        Set<Integer> labelSet = map.keySet();
        Iterator<Integer> it = labelSet.iterator();

        //go through the HashMap by using keys
        //and find the key with the highest weights
        while(it.hasNext()){
            int label = it.next();
            double value = map.get(label);
            if(value > maxSimilarity){
                maxSimilarity = value;
                returnLabel = label;
            }
        }

        return returnLabel;
    }

    static String extractGroupName(String tmpFilePath){
        StringBuilder groupName = new StringBuilder();
        for(int i = 15; i < tmpFilePath.length(); i ++){
            if(tmpFilePath.charAt(i) != '_')
                groupName.append(tmpFilePath.charAt(i));
            else
                break;
        }

        return groupName.toString();
    }
}
