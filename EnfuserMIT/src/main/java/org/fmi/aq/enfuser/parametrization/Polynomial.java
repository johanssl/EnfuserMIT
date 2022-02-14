/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.parametrization;

/**
 * A simple second order polynomial function class for the computation of y =
 * ax2 + bx +c. The main use-case for this class is that the polynomial can be
 * defined with three points: min, mid and max. This way, all kinds of
 * polynomials can be easily constructed.
 *
 * @author johanssl
 */
public class Polynomial {

    final float a;
    final float b;
    final float c;
    final float x_scaler;

    public Polynomial(float a, float b, float c, float x_scaler) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.x_scaler = x_scaler;
    }

    /**
     * compute the function value
     *
     * @param x x-value
     * @return function value
     */
    public float getValue(float x) {
        x *= x_scaler;
        return a * x * x + b * x + c;
    }

    /**
     * Construct Polynomial using min, mid and max points.
     *
     * @param min value at 0
     * @param mid value at 0.5
     * @param max value at 1
     * @param maxX x value at maximum
     *
     * @return Polynomial instance
     */
    public static Polynomial getFromEndPoints(float min, float mid, float max, float maxX) {

        float c;
        float b;
        float a;
        //y = ax2 + bx +c
        c = min;// when x is zero

        //max = a+ c +b /when x is 1)
        // => a = max - min - b
        float range = max - min;
        //a = range -b;

        //mid = 0.25a + 0.5b + c
        //mid = 0.25a +0.5b + min
        //mid = 0.25(range-b) + 0.5b +min
        //mid = 0.25range + 0.25b + min
        // => 0.25b = mid - 0.25range - min
        b = 4 * mid - range - 4 * min;
        a = range - b;

        float x_scaler = 1f / maxX;
        Polynomial p = new Polynomial(a, b, c, x_scaler);
        return p;

    }

}
