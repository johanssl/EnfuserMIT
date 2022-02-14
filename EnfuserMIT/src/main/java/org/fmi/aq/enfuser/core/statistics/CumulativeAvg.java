/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

/**
 *
 * @author johanssl
 */
public class CumulativeAvg {

    private double avg;
    private int n;

    public CumulativeAvg() {

        avg = 0;
        n = 0;
    }

    public void updateAverage(double val) {
        double nextAvg;
        nextAvg = (val + n * avg) / (n + 1);
        avg = nextAvg;
        n++;
    }

    public double getAvg() {
        return this.avg;
    }

}
