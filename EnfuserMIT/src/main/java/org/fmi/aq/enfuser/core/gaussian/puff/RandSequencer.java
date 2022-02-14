/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import org.fmi.aq.enfuser.options.GlobOptions;

/**
 *
 * @author johanssl
 */
public class RandSequencer {

    private int currRand = 0;
    private final float[] randArr_posneg1;
    
    //for deterministic model runs we need seemingly random numbers
    private final static double[] RAND50 = {
    -0.24326,
    0.91531,
    -0.24046,
    -0.31373,
    -0.74650,
    -0.22161,
    0.86189,
    -0.63959,
    -0.17028,
    -0.22470,
    0.74278,
    0.88539,
    0.42519,
    -0.40560,
    0.50100,
    -0.33975,
    0.13992,
    0.33411,
    0.12468,
    -0.75516,
    0.82493,
    -0.27501,
    -0.85859,
    -0.76299,
    0.04839,
    -0.30994,
    0.37324,
    0.28732,
    0.16244,
    -0.58238,
    0.21854,
    -0.32535,
    0.89275,
    0.81841,
    -0.81809,
    -0.36733,
    -0.82127,
    0.42411,
    -0.63912,
    0.08566,
    0.71812,
    -0.05821,
    -0.01407,
    0.82324,
    0.41125,
    0.19374,
    -0.00991,
    -0.79270,
    0.59490,
    -0.21936
    };

    public RandSequencer(int N) {
        this(N,GlobOptions.get().deterministic() );
    }
        
    
    public RandSequencer(int N, boolean determ) {
        this.randArr_posneg1 = new float[N];
        
        if (determ) {
            
            int st = N%7;
            for (int i =0;i<N;i++) {
                int ind = i+st;
                int modInd = ind%RAND50.length;
                 this.randArr_posneg1[i] = (float)RAND50[modInd];
            }
            
            
        } else {
             for (int i = 0; i < N; i++) {
                this.randArr_posneg1[i] = (float) (Math.random() * 2 - 1);
            }
        }
       
    }

    public int N() {
        return randArr_posneg1.length;
    } 
    public float nextRand() {
        this.currRand++;
        if (this.currRand > randArr_posneg1.length - 1) {
            this.currRand = 0;
        }
        return this.randArr_posneg1[this.currRand];
    }
    
    public float nextRand01() {
       float rand = nextRand();
       rand*=0.5f;//scale: -0.5 to 0.5
       return rand+0.5f;//shift: 0 to 1
    }
    
    public static void test() {
        GlobOptions.get().setDeterministic(true);
        RandSequencer r = new RandSequencer(60);
        RandSequencer r2 = new RandSequencer(10);
        RandSequencer r3 = new RandSequencer(13);
        for (int i =0;i<70;i++) {
            System.out.println(r.nextRand() +";"+r2.nextRand()+";"+r3.nextRand());
        }
    }

    public float randAt(int randIndex) {
        return this.randArr_posneg1[randIndex];
    }
    
}
