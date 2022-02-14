/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;

import java.util.HashMap;
import org.fmi.aq.essentials.geoGrid.Boundaries;

/**
 *
 * @author johanssl
 */
public class Guard {

    public static void create(HashMap<String, String> argHash) {
       
    }
    
    public Guard(String root) {
    }
    
     /**
     * Display all keys held by crypts.dat on system out.
     * @param compare if true then the keys matched against
     * known set of keys.
     * @param secret
     */
    public void printKeys(boolean compare, String secret) {
      
    }

    public void addKeyValueToCrypts(String key, String value) {
       
    }

    /**
     * Check the Boundaries with respect a possible region lock.
     * @param mb the bounds
     */
    public void checkRegionLocking(Boundaries mb) {

    }

    public boolean hasRegionLock() {
        return false;
    }

    public String getEncryptedProperty(String key) {
       return null;
    }

    public boolean regionAllowed(Boundaries mb) {
        return true;
    }
    
    
    /**
     *Launch a sequence of tests and send email alerts if notable
     *issues are found. The list of recipients is maintained at globOps.txt
     * For the same topic, only one alert message can be sent per day. 
     */
    public static void operativeEmailAlertCheck() {
    }    
    
    
    
}
