/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author johanssl
 */
public class Threader {

    public static ExecutorService executor = 
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    public static void reviveExecutor() {
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }
    
    public static void reviveExecutor(int thr) {
        executor = Executors.newFixedThreadPool(thr);
    }
    
}
