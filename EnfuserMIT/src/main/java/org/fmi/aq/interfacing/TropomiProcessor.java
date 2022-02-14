/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;

import org.fmi.aq.enfuser.options.FusionOptions;


/**
 *
 * @author johanssl
 */
public class TropomiProcessor {

        /**
     * Launch a analysis process for multiple S5P datasets archived by the miner.
     * @param region region name
     * @param area area name
     * @param from_raw if true, then input for the process is read from ORIGINAL
     * S5P netCDFs, and not from 'preprocessed' smaller area-specific files.
     * @param start start time for analysis
     * @param end end time for analysis
     * @param customizer custom string content to further define settings
     * @param vars set of TropomiDef indices, for which variables this process is done.
     */
    public void launchAnalysis(String region, String area, boolean from_raw,
            String start, String end, String customizer, int[] vars) {
        
    }
    
     /**
    * Launch an analysis task after a model run. This will do nothing
    * in case the preprocessing of TROPOMI data is not present for the area,
    * or this method has triggered in less than a week.
    * @param ops options (run dates, area and region names).
    */
    public void launchOperationalAnalysis(FusionOptions ops) {

    }


    public void launchRawPostprocess_recent(String region, String[] areas, boolean skipExisting) {


    }

    /**
    * To be used for DataMiner TROPOMI, to convert the orginal raw
    * data into preprocessed smaller subsets.
    * @param region region name
    * @param areas area name
    * @param skipExisting if true, then preprocessed data will not be created
    * for days for which the files already exist.
    * @param start
    * @param end 
    */
    public void launchRawPostProcess_historical(String region, String[] areas,
            boolean skipExisting, String start, String end) {

    }
    
}
