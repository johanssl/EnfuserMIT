/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.statistics.StatsCruncher;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.date.Dtime;

/**
 *
 * @author johanssl
 */
public class ArchStatProcessor {

    
    public ArchStatProcessor() {
        
    }
    
  /**
  * Launch a StatsCruncher assessment based on archived statistics.
  * @param dbFilter if true, then all unknown ID's will be skipped from the process
  * (db.csv)
  * @param types array of variable type names
  * @param start start time stamp
  * @param end end time stamp
  * @param region region name
  * @param areaName area name
  * @param loov if true, then read true Leave-One-Out statistics instead (must exist)
  */  
    public void crunchStatisticsFromArchives(boolean dbFilter, String[] types,
            String start, String end, String region, String areaName, boolean loov) {
        
    }


    
    
    
  /**
  * Unzip selected netCDF content to another directory from allPollutant compressed
  * archives.
  * @param destDir destination directory
  * @param regionalArchDir the regional archive directory, without area and year
  * child directories.
  * @param area area name
  * @param start start time stamp
  * @param end end time stamp
  * @param allPollutants if true, then allPollutants_x.nc files are unzipped
  * @param components if true, then components_x.nc files are unzipped
  */  
    public void unzipArchivedContent(String destDir, String regionalArchDir,
            String area, String start, String end,
            boolean allPollutants, boolean components) {

    }


    public String combineUnzippedNetCDF(String inDir, String outDir, String start, String end,
            boolean components, double[] aqiTh) {
 
      return null;
    }
    
  /**
  * Produced a combined, averaged netCDF dataset using multiple pollutant
  * netCDF-files that are unpacked and read on the go.
  * @param tempDir for temporary unzips
  * @param outDir destination directory
  * @param ops options that hold the area name and time span in particular.
  * @param components if true, then components_x.nc files are processed
  * @param aqiTh
  * @param skipHash a hash of sysH, true for each hour that read of hourly data
  * is to be skipped.
  * @return full file name of the created netCDF.
  */
    public String combineNetCDF(String tempDir, String outDir, FusionOptions ops,
            boolean components, double[] aqiTh, HashMap<Integer,Boolean> skipHash) {
     return null;
    }

     /**
  * Launch a statistical estimation process, StatsCrunch, in EnfuserGUI.
  * @param type the variable type for stats
  * @param sources a list of source names that are included in the process.
  * @param neuralOut if true, then an additional text-dataset is generated
  * which may be suitable for more advanced neural methods.
  * @param mode ALL, NIGHT or DAY
  * @param start Dtime start object
  * @param end Dtime end object
  * @param ens the core that holds the data for the assessment.
  * Note: the DataFusion should be performed for the same assessment
  * period before this method is used.
  */
    public void launchGUICruncher(String type, ArrayList<String> sources, boolean neuralOut,
            Object start, Object end, Object ens) {

    }

    public static void launchWIthRunMode(String[] args) {
       
    }

     /**
     * For the given region and area -pair, launch a statsCruncher task in case
     * enough time has passed since this method was invoked for the area the last
     * time.
     * @param region region name
     * @param area modelling area name
     * @param updateDays an amount in days, that manages how often the statsCrunching
     * process should be updated.
     */
    public void launchRollingStatisticsCreation(String region, String area, int updateDays) {

    }

     /**
     * Launch a process for average gridded concentrations creation.The format will
     * be netCDF (possibly including PNG visualizations. Timespan will be
     * the previous full month. Input for the process is held by the Enfuser_master_out.
     * Data will be processed according to FusionOptions' griddedAveragesDir().
     * @param regionName region name
     * @param areaID area name
     * @param hourSparser hourly sparser that with values larger than 1 can
     * speed up the process by processing every n-th allPollutants.zip.
     * @param visualizations
     * @param p run parameters that carry the end time of the model run.
     */
    public void launchGridAverager(String regionName, String areaID,
            int hourSparser, boolean visualizations, RunParameters p) {

    }
    
    
    public static void addCustomStatisticsForStatsCruncher(ArrayList<String> results,
            FusionCore ens, StatsCruncher sc) {

    }


}
