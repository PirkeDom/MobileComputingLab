package com.mobilecomputing.dominic.task1_activitymonitoring;

import android.app.ActivityManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import android.os.Handler;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.Buffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager mSensorManager;
    // Individual light and proximity sensors.
    private Sensor mSensorProximity;
    private Sensor mSensorLight;
    private Sensor mSensorAccelerometer;

    // TextViews to display current sensor values
    private TextView mTextSeconds;
    private TextView mTextSensorAccelerometer;
    private TextView mTextActivity;
    private TextView mTextPredictedActivity;
    private GraphView graphX;
    private GraphView graphY;
    private GraphView graphZ;
    private LineGraphSeries<DataPoint> seriesX;
    private LineGraphSeries<DataPoint> seriesY;
    private LineGraphSeries<DataPoint> seriesZ;

    private SensorEvent sensorEvent;

    private final Handler handler = new Handler();

    private TrainRecord[] trainingSet;
    private TestRecord[] testingSet;

    private String filename = "SampleFile.txt";
    private String filepath = "MyFileStorage";
    File myExternalFile;
    private boolean doRecording = false;
    private String activityRecording = "";

    private float[][] buffer;
    private int buffercounter;

    int delay = 1000;
    Runnable runnable;

    int counter = 0;

    @Override
    protected void onResume() {
        handler.postDelayed(new Runnable() {
            public void run() {
                //printSensorData();
                //mTextSeconds.setText(getResources().getString(R.string.label_seconds, time++));
                runnable=this;

                handler.postDelayed(runnable, delay);
            }
        }, delay);

        super.onResume();
    }

    @Override
    protected void onPause() {
        //handler.removeCallbacks(runnable); //stop handler when activity not visible
        super.onPause();
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        mTextSeconds = (TextView) findViewById(R.id.label_seconds);
        mTextSensorAccelerometer = (TextView) findViewById(R.id.label_accelerometer);
        mTextActivity = (TextView) findViewById(R.id.text_activity);
        mTextPredictedActivity = (TextView) findViewById(R.id.label_predicted_activity);

        mSensorProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mSensorLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        String sensor_error = getResources().getString(R.string.error_no_sensor);
        if (mSensorAccelerometer == null) {
            mTextSensorAccelerometer.setText(sensor_error);
        }

        graphX = (GraphView) findViewById(R.id.accelerometer_x);
        graphX.getViewport().setXAxisBoundsManual(true);
        graphX.getViewport().setMinX(0);
        graphX.getViewport().setMaxX(200);

        graphY = (GraphView) findViewById(R.id.accelerometer_y);
        graphY.getViewport().setXAxisBoundsManual(true);
        graphY.getViewport().setMinX(0);
        graphY.getViewport().setMaxX(200);

        graphZ = (GraphView) findViewById(R.id.accelerometer_z);
        graphZ.getViewport().setXAxisBoundsManual(true);
        graphZ.getViewport().setMinX(0);
        graphZ.getViewport().setMaxX(200);

        seriesX = new LineGraphSeries<>(new DataPoint[] {new DataPoint(0, 0)});
        seriesY = new LineGraphSeries<>(new DataPoint[] {new DataPoint(0, 0)});
        seriesZ = new LineGraphSeries<>(new DataPoint[] {new DataPoint(0, 0)});
        graphX.addSeries(seriesX);
        graphY.addSeries(seriesY);
        graphZ.addSeries(seriesZ);

        buffer = new float[20][3];
        buffercounter = 0;

        knn(getExternalFilesDir("classification") + "/activity_train.txt", getExternalFilesDir("classification") + "/activity_test.txt",1,2);


        Button buttonstart = (Button)findViewById(R.id.buttonstart);
        buttonstart.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                activityRecording = mTextActivity.getText().toString();
                myExternalFile = new File(getExternalFilesDir(filepath), activityRecording + ".txt");
                doRecording = true;
            }
        });

        Button buttonstop = (Button)findViewById(R.id.buttonstop);
        buttonstop.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                doRecording = false;
            }
        });

        Button buttontest = (Button)findViewById(R.id.buttontest);
        buttontest.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {


                TestRecord recordToClassify = new TestRecord(new double[]{1.854070, 4.217626, 8.494149, 0.000000}, 0);

                TrainRecord[] neighbors = findKNearestNeighbors(trainingSet, recordToClassify, 3, new EuclideanDistance());
                int classLabel = classify(neighbors);
                String predictedActivity = "";
                switch(classLabel)
                {
                    case 0: predictedActivity = "Running";
                        break;
                    case 1: predictedActivity = "Walking";
                        break;
                    case 2: predictedActivity = "Sitting";
                        break;
                }
                mTextPredictedActivity.setText(getResources().getString(R.string.label_predicted_activity, predictedActivity));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(mSensorAccelerometer != null){
            mSensorManager.registerListener(this,mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSensorManager.unregisterListener(this);
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            sensorEvent = event;

        mTextSeconds.setText(getResources().getString(R.string.label_seconds, counter++));
        printSensorData();

        if(counter % 20 == 0 )
        {
            //calculate mean of all 20 values and put it into knn
            System.out.println();

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
                mTextSensorAccelerometer.setText(getResources().getString(R.string.label_accelerometer, currentValue[0], currentValue[1], currentValue[2]));

                //Store data in memory, file, or in other data structure
                //addDataToProcess (x, y, z);
                seriesX.appendData(new DataPoint((double)counter, currentValue[0]), true, 1000);
                seriesY.appendData(new DataPoint((double)counter, currentValue[1]), true, 1000);
                seriesZ.appendData(new DataPoint((double)counter, currentValue[2]), true, 1000);


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

    static String extractGroupName(String filePath){
        StringBuilder groupName = new StringBuilder();
        for(int i = 15; i < filePath.length(); i ++){
            if(filePath.charAt(i) != '_')
                groupName.append(filePath.charAt(i));
            else
                break;
        }

        return groupName.toString();
    }
}
