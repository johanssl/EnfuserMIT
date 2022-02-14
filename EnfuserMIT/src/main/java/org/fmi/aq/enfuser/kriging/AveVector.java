/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.kriging;

import java.util.ArrayList;

/**
 *
 * @author johanssl
 */
public class AveVector {

    private final ArrayList<double[]> hVals;
    private final int size = 24;
    String id;

    public AveVector(String id) {
        this.hVals = new ArrayList<>();
        this.id = id;
    }

    public void addValuesSet(double[] vals) {

        this.hVals.add(vals);
    }

    public boolean addValuesSetIfEqual(double[] vals, String ID) {
        boolean added = false;
        if (this.id.equals(ID)) {
            this.hVals.add(vals);
            added = true;
        }

        return added;
    }

    public boolean addValuesSetIfSuperset(double[] vals, String ID) {
        boolean added = false;
        if (ID.contains(this.id)) {
            this.hVals.add(vals);
            added = true;
        }

        return added;
    }

    public boolean addValuesSetIfSubset(double[] vals, String ID) {
        boolean added = false;
        if (this.id.contains(ID)) {
            this.hVals.add(vals);
            added = true;
        }

        return added;
    }

    public double[] getAverageSet() {
        double[] ave = new double[size];

        for (int i = 0; i < this.hVals.size(); i++) {
            for (int j = 0; j < size; j++) {
                ave[j] = ave[j] + this.hVals.get(i)[j];
            }

        }
        //get average from sum;
        boolean divByZero = false;
        if (this.hVals.isEmpty()) {
            divByZero = true;
        }
        for (int j = 0; j < size; j++) {
            if (!divByZero) {
                ave[j] = ave[j] / this.hVals.size();
            }
        }

        return ave;
    }

}//class end
