// compared with Record, add another attribute - predictLabel
// which is used to store the predicted label for the current testRecord.
package com.mobilecomputing.dominic.task1_activitymonitoring;
public class TestRecord extends Record{
	int predictedLabel;
	
	TestRecord(double[] attributes, int classLabel) {
		super(attributes, classLabel);
	}
}
