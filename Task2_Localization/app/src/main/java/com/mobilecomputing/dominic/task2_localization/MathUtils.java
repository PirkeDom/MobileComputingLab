package com.mobilecomputing.dominic.task2_localization;

//copied from https://raw.githubusercontent.com/marytts/marytts/master/marytts-signalproc/src/main/java/marytts/util/math/MathUtils.java

public class MathUtils {

    public static double mean(double[] data) {
        return mean(data, 0, data.length - 1);
    }

    /**
     * Compute the mean of all elements in the array. No missing values (NaN) are allowed.
     *
     * @param data
     *            data
     * @param startIndex
     *            start index
     * @param endIndex
     *            end index
     * @throws IllegalArgumentException
     *             if the array contains NaN values.
     * @return mean
     */
    public static double mean(double[] data, int startIndex, int endIndex) {
        double mean = 0;
        int total = 0;
        startIndex = Math.max(startIndex, 0);
        startIndex = Math.min(startIndex, data.length - 1);
        endIndex = Math.max(endIndex, 0);
        endIndex = Math.min(endIndex, data.length - 1);

        if (startIndex > endIndex)
            startIndex = endIndex;

        for (int i = startIndex; i <= endIndex; i++) {
            if (Double.isNaN(data[i]))
                throw new IllegalArgumentException("NaN not allowed in mean calculation");
            mean += data[i];
            total++;
        }
        mean /= total;
        return mean;
    }

    public static double standardDeviation(double[] data) {
        return standardDeviation(data, mean(data));
    }

    public static double standardDeviation(double[] data, double meanVal) {
        return standardDeviation(data, meanVal, 0, data.length - 1);
    }

    public static double standardDeviation(double[] data, double meanVal, int startIndex, int endIndex) {
        return Math.sqrt(variance(data, meanVal, startIndex, endIndex));
    }

    /**
     * Compute the standard deviation of the given data, this function can deal with NaNs
     *
     * @param data
     *            double[]
     * @param opt
     *            0: normalizes with N-1, this provides the square root of best unbiased estimator of the variance, 1: normalizes
     *            with N, this provides the square root of the second moment around the mean
     * @return Math.sqrt(variance(data, opt))
     */
    public static double standardDeviation(double[] data, int opt) {
        if (opt == 0)
            return Math.sqrt(variance(data, opt));
        else
            return Math.sqrt(variance(data, opt));
    }

    /**
     * Compute the variance in the array. This function can deal with NaNs
     *
     * @param data
     *            double[]
     * @param opt
     *            0: normalizes with N-1, this provides the square root of best unbiased estimator of the variance, 1: normalizes
     *            with N, this provides the square root of the second moment around the mean
     * @return S / numData -1 if opt is 0, S / numData otherwise
     */
    public static double variance(double[] data, int opt) {
        // Pseudocode from wikipedia, which cites Knuth:
        // n = 0
        // mean = 0
        // S = 0
        // foreach x in data:
        // n = n + 1
        // delta = x - mean
        // mean = mean + delta/n
        // S = S + delta*(x - mean) // This expression uses the new value of mean
        // end for
        // variance = S/(n - 1)
        double mean = 0;
        double S = 0;
        double numData = 0;
        for (int i = 0; i < data.length; i++) {
            if (!Double.isNaN(data[i])) {
                double delta = data[i] - mean;
                mean += delta / (numData + 1);
                S += delta * (data[i] - mean);
                numData++;
            }
        }
        if (opt == 0)
            return (S / (numData - 1));
        else
            return (S / numData);
    }

    public static double variance(double[] data) {
        return variance(data, mean(data));
    }

    public static double variance(double[] data, double meanVal) {
        return variance(data, meanVal, 0, data.length - 1);
    }

    public static float variance(float[] data, float meanVal) {
        return variance(data, meanVal, 0, data.length - 1);
    }

    public static float variance(float[] data, float meanVal, int startIndex, int endIndex) {
        double[] ddata = new double[data.length];
        for (int i = 0; i < data.length; i++)
            ddata[i] = data[i];

        return (float) variance(ddata, meanVal, startIndex, endIndex);
    }

    public static double variance(double[] data, double meanVal, int startIndex, int endIndex) {
        double var = 0.0;

        if (startIndex < 0)
            startIndex = 0;
        if (startIndex > data.length - 1)
            startIndex = data.length - 1;
        if (endIndex < startIndex)
            endIndex = startIndex;
        if (endIndex > data.length - 1)
            endIndex = data.length - 1;

        for (int i = startIndex; i <= endIndex; i++)
            var += (data[i] - meanVal) * (data[i] - meanVal);

        if (endIndex - startIndex > 1)
            var /= (endIndex - startIndex);

        return var;
    }

    public static double[] variance(double[][] x, double[] meanVector) {
        return variance(x, meanVector, true);
    }

    /**
     * Returns the variance of rows or columns of matrix x
     *
     * @param x
     *            the matrix consisting of row vectors
     * @param meanVector
     *            the vector of mean values -- a column vector if row-wise variances are to be computed, or a row vector if
     *            column-wise variances are to be calculated. param isAlongRows if true, compute the variance of x[0][0], x[1][0]
     *            etc. given mean[0]; if false, compute the variances for the vectors x[0], x[1] etc. separately, given the
     *            respective mean[0], mean[1] etc.
     * @param isAlongRows
     *            isAlongRows
     * @return var
     */
    public static double[] variance(double[][] x, double[] meanVector, boolean isAlongRows) {
        double[] var = null;

        if (x != null && x[0] != null && x[0].length > 0 && meanVector != null) {
            if (isAlongRows) {
                var = new double[x[0].length];
                int j, i;
                for (j = 0; j < x[0].length; j++) {
                    for (i = 0; i < x.length; i++)
                        var[j] += (x[i][j] - meanVector[j]) * (x[i][j] - meanVector[j]);

                    var[j] /= (x.length - 1);
                }
            } else {
                var = new double[x.length];
                for (int i = 0; i < x.length; i++) {
                    var[i] = variance(x[i], meanVector[i]);
                }
            }
        }

        return var;
    }

    public static Double[] normalizeToSumUpTo(Double[] x, double sumUp) {
        return normalizeToSumUpTo(x, x.length, sumUp);
    }

    public static Double[] normalizeToSumUpTo(Double[] x, int len, double sumUp) {
        if (len > x.length)
            len = x.length;

        Double[] y = new Double[len];

        double total = 0.0;
        int i;


        for (i = 0; i < len; i++) {
            x[i] = 100 + x[i];
            total += x[i];
        }

        if (total > 0.0) {
            for (i = 0; i < len; i++)
                y[i] = sumUp * (x[i] / total);
        } else {
            for (i = 0; i < len; i++)
                y[i] = 1.0 / len;
        }

        return y;
    }

    public static double[] toPrimitiveDouble(Double[] data){
        double[] tempArray = new double[data.length];
        for(int i = 0; i < data.length; i++)
            tempArray[i] = data[i];
        return tempArray;

    }
}
