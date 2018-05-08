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
    private GraphView graphX;
    private GraphView graphY;
    private GraphView graphZ;
    private LineGraphSeries<DataPoint> seriesX;
    private LineGraphSeries<DataPoint> seriesY;
    private LineGraphSeries<DataPoint> seriesZ;

    private SensorEvent sensorEvent;

    private final Handler handler = new Handler();

    private String filename = "SampleFile.txt";
    private String filepath = "MyFileStorage";
    File myExternalFile;
    private boolean doRecording = false;
    private String activityRecording = "";

    int delay = 100;
    Runnable runnable;

    int time = 0;

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

        mTextSeconds.setText(getResources().getString(R.string.label_seconds, time++));
        printSensorData();
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
                seriesX.appendData(new DataPoint((double)time, currentValue[0]), true, 1000);
                seriesY.appendData(new DataPoint((double)time, currentValue[1]), true, 1000);
                seriesZ.appendData(new DataPoint((double)time, currentValue[2]), true, 1000);

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


}
