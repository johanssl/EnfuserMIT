/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feeds;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import org.fmi.aq.enfuser.mining.miner.RegionalMiner;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;

/**
 *The purpose of this class is to handle SILAM url-addresses (thredds)
 * for different available versions and domains. This info also includes
 * the recent update date, variable lists and geographic bounds.
 * The idea is that the most suitable SILAM domain and a source
 * of download can be determined automatically for Enfuser.
 * 
 * @author johanssl
 */
public class SilamAddress {
  
    public String versionString;
    public double versionDouble=1;
    
    public final String baseLink;
    public String threddsCatalogLink;
    public String threddsDownloadLink;
    
    public final String domainName;
    public Boundaries domain;
    public double area_mkm2;
    public final Dtime latestRun;
    
    public ArrayList<String> allVars = new ArrayList<>();
    public ArrayList<String> availableVars=new ArrayList<>();
    public String catalogContent;
    public String[] defVars;
    public final static String SILAM_FI="http://silam.fmi.fi";
    public final static String THREDDS_ROOT=SILAM_FI+"/thredds/catalog.html";
    
    public boolean secondary =false;
    /**
     * Initialize the instance
     * @param latest the date for latest data for this SILAM address.
     * @param baseLink the base link for thredds, that
     * should include the thredds server address, but also the domain and
     * version specifics.
     * @param defVars A list of variables that the download will be attempted.
     */
   public SilamAddress(Dtime latest, String baseLink, String[] defVars) {
        this.baseLink =baseLink;
        // /thredds/catalog/silam_glob_v5_5_1/catalog.html
        this.latestRun = latest;
        this.domainName = getBetween(baseLink, "silam_" ,"_v");
        this.versionString = getBetween(baseLink, "_v" ,"/catalog");
        this.defVars = defVars;
        
   }
   
   /**
    * Switch to an alternative variable list for downloads.
    * @param defVars new list of variables
    * @param sec flag as "secondary" if true. This removes the "horizontal stride"
    * for SILAM download URL, since the secondary download has been designed
    * for 2D variables such as BLH. 
    */
   public void switchToSecondaryDataset(String[] defVars, boolean sec) {
       this.availableVars.clear();
       for (String s:defVars) {
           if (this.allVars.contains(s)) this.availableVars.add(s);
       }
       this.secondary = sec;
   }
   
    private final static String TZ = ":00Z";
    
   /**
    * Form the URL address for the download. All variables that have been listed
    * for download that were actually present (availableVars), will
    * be included in the URL. The format will be netCDF.
    * @param b the bounds for download
    * @param startTime start time for download
    * @param endTime the end time
    * @return String url download link.
    */ 
   public String buildDownloadUrl(Boundaries b,
   Dtime startTime, Dtime endTime) {
       
        String adminEmail = GlobOptions.get().adminEmails()[0];
        String horizStride = "&horizStride=1";
        if (secondary) {
            horizStride = "";
        }
        
        String address = this.threddsDownloadLink+"?";
        

        for (String var:this.availableVars) {
            address += "var=" +var + "&";
        }
        address
                += "north=" + b.latmax
                + "&west=" + b.lonmin
                + "&east=" + b.lonmax
                + "&south=" + b.latmin
                + horizStride
                + "&time_start=" + startTime.getStringDate_noTS() + TZ
                + "&time_end=" + endTime.getStringDate_noTS() + TZ
                + "&timeStride=1&vertCoord=0&addLatLon=true&accept=netcdf&email=" + adminEmail;
       
      return address; 
   }
   
      /**
    * Form the URL address for the download. All variables that have been listed
    * for download that were actually present (availableVars), will
    * be included in the URL. The format will be netCDF.
    * The domain most recent update time will be used as the download
    * start-time.
    * @param b the bounds for download
    * @param endTime the end time
    * @return String url download link.
    */ 
      public String buildDownloadUrl(Boundaries b,
      Dtime endTime) {
          
        Dtime startTime = this.latestRun.clone();
      return buildDownloadUrl( b,startTime, endTime);
   }
  
  /**
   * Printout details for this instance, using the given print stream.
   * @param out the print-stream used for println().
   */    
   public void printout(PrintStream out) {
        out.println(latestRun.getStringDate_noTS()+", "+ domainName +", "
        + this.versionString +", "+this.versionDouble+  ", vars= "
                + this.allVars.size() +",matchingVars="+this.availableVars.size() 
                +", "+ domain.toText(4) + ", A[M km2]="+(int)this.area_mkm2
        //+"\n\t"+this.threddsDownloadLink 
        );
   }
   
   /**
    * Dig deeper in the THREDDS catalogs and assess the available variables,
    * links and geographic bounds.
    * Note: in order to use this SilamAdress instance, this method must be
    * invoked.
    * @param out for feedback println().
    * @throws java.lang.Exception
    */ 
   public void  init(PrintStream out) throws Exception {
      String domainVer = this.domainName+"_v"+this.versionString;
      
      String[] verNums = this.versionString.split("_");
      String s="";
      int j =0;
      for (String n:verNums) {
          j++;
          s+=n;
          if (j==1) s+=".";
      }
      this.versionDouble = Double.valueOf(s);
      
       //define the links    
       this.threddsCatalogLink = SILAM_FI + 
       "/thredds/catalog/silam_"+domainVer+ "/"
               +"runs/catalog.html?dataset=silam_"+domainVer +"/"
               + "runs/silam_" + domainVer +"_RUN_"+this.latestRun.getStringDate_noTS()+"Z";
       
       this.threddsDownloadLink =  SILAM_FI +
       "/thredds/ncss/grid/silam_"+domainVer+ "/"
               + "runs/silam_" + domainVer +"_RUN_"+this.latestRun.getStringDate_noTS()+"Z";
           
           
            this.catalogContent = RegionalMiner.mineFromURL_static(false,
                    this.threddsCatalogLink, out, null,null,null);  
          
            //set variable listing
          setVarsFromCatalog(defVars, out);
          
          //then domain
          String domString = getBetween(this.catalogContent, "GeospatialCoverage",
                  "TimeCoverage").toLowerCase();
          domString = domString.replace(" ", "");
          domString = domString.replace("</em>", "");
          
          String longs = getBetween(domString,"longitude:","degrees");
           out.println("longs = "+ longs);
           String[] lonSplit = longs.split("to");
           double lonmin = Double.valueOf(lonSplit[0]);
           double lonmax = Double.valueOf(lonSplit[1]);
          String lats = getBetween(domString,"latitude:","degrees");
           out.println("lats = "+lats);
           String[] latSplit = lats.split("to");
            double latmin = Double.valueOf(latSplit[0]);
            double latmax = Double.valueOf(latSplit[1]);
            
            this.domain = new Boundaries(latmin,latmax,lonmin,lonmax);
            this.area_mkm2 = this.domain.getFullArea_km2()/1000000;
   }
   
  
    public final static String THREDDS_CATALOG_BODY1 = SILAM_FI+"/thredds/catalog/silam_";
    public final static String THREDDS_CATALOG_BODY2 = "/runs/catalog.html?dataset=silam_";
    public static String[] SILAM_VARLIST_CRUDESPLIT = {"Variables", "GeospatialCoverage"};//get the variable part from html-content with these.
    public static String[] SILAM_VAR_SPLITTERS = {"<li><strong>", " "};//read individual Silam variables in between these.

     
   
   private void setVarsFromCatalog(
           String[] silamVars, PrintStream out) {
       
       String vars_raw = getBetween(catalogContent, SILAM_VARLIST_CRUDESPLIT[0],
               SILAM_VARLIST_CRUDESPLIT[1]);
            //EnfuserLogger.log(Level.FINER,this.getClass(),"\n\nRAW\n" + vars_raw +"\n\n");
            String[] split = vars_raw.split(SILAM_VAR_SPLITTERS[0]);//cnc_ALD2_gas (ug/m3) 
            //</strong> =  <i>Concentration in air ALD2_gas</i> = 
            for (String temp : split) {
                //EnfuserLogger.log(Level.FINER,this.getClass(),"\t TEMP = "+ temp);
                temp = temp.replace("</strong>", " ");
                try {
                    String cand = temp.split(SILAM_VAR_SPLITTERS[1])[0];//china_v5_5_1
                    //out.println("\t\t Silam variable available: " + cand);
                    //Cross-check
                    this.allVars.add(cand);
                    if (silamVars!=null) {
                    for (String test : silamVars) {
                        if (test.equals(cand)) {
                            //out.println("\t\t\t has been listed for extraction.");
                            this.availableVars.add(test);
                        }
                    }
                    } else {
                        this.availableVars.add(cand);
                    }
                } catch (Exception e) {

                }
            }//for all listed variables

       
   }
   
   protected int getRank() {
       double d = 1000*this.versionDouble/this.area_mkm2*this.availableVars.size();
       return (int)d;
   }
   
    
 public static ArrayList<String> getRefs(boolean certBypass,
         PrintStream out, String[] creds) {
     ArrayList<String> arr = new ArrayList<>();
     
             try {
            String result = RegionalMiner.mineFromURL_static(certBypass,
                    THREDDS_ROOT, out, creds,null,null);

            result = result.replace("'", "");
            result = result.replace(" ", "");
            
            String[] split = result.split("href=");
            for (String temp : split) {
                try {
                    String cand = temp.split("><")[0];//  /thredds/catalog/silam_glob_v5_5_1/catalog.html
                    if (cand.contains("silam") && cand.contains("thredds") 
                            && cand.contains("_v")) {
                        if (!cand.contains("daily") 
                                && !cand.contains("allergy"))arr.add(cand);
                    }

                } catch (Exception e) {

                }
            }//for all listed versions

        } catch (Exception ex) {
            ex.printStackTrace();
        }
             
      return arr;
 }
 
 public static ArrayList<Dtime> getRunDates(String silamRef, boolean certBypass,
         PrintStream out, String[] creds) {
     
     String url = SILAM_FI
             +silamRef.replace("/catalog.html", "/runs/catalog.html");
      ArrayList<Dtime> arr = new ArrayList<>();
     
             try {
            String result = RegionalMiner.mineFromURL_static(certBypass,
                    url, out, creds,null,null);

            result = result.replace("'", "");
            result = result.replace(" ", "");
            
            
            String[] split = result.split("href=");
            for (String temp : split) {
                try {
                    String cand = getBetween(temp,"RUN_","Z");
                    Dtime dt = new Dtime(cand);
                    arr.add(dt);

                } catch (Exception e) {

                }
            }//for all listed versions

        } catch (Exception ex) {
            ex.printStackTrace();
        }
             
      return arr;
     
 }
 
 public static ArrayList<SilamAddress> getCurrentSources(String[] defaultVars, PrintStream out,
         boolean certBypass, String[] creds, Boundaries b, String domainsNotAccepted) {
      ArrayList<SilamAddress> sas = new ArrayList<>();
      
       ArrayList<String> arr= getRefs(certBypass, out, creds);
       out.println("Links:");
     for (String s:arr) {
         out.println("\t"+s);
         ArrayList<Dtime> dts = getRunDates(s, false,
         out, null);
         
         for(Dtime dt:dts) {
             out.println("\t\t "+ dt.getStringDate_noTS());
         }
         
         if (dts.size()>0) {
             try {
             SilamAddress sa = new SilamAddress(dts.get(0),s, defaultVars);
             sa.init(out);
             sas.add(sa);
             } catch (Exception e) {
               out.println("=====================\nFAILED for: "+s+"\n=====================");
             }
             
         }
         
     }//for base links
     
     //check coverage 
     ArrayList<SilamAddress> acceptable = new ArrayList<>();
     for (SilamAddress sa:sas) {
         if (domainsNotAccepted!=null && domainsNotAccepted.contains(sa.domainName))continue;
         if (sa.domain.fullyContains(b)) {
             acceptable.add(sa);
         }
     }
     
     //then sort
     if (acceptable.size()>1) Collections.sort(acceptable,new SilamAddressComparator());
     return acceptable;
 }
 
 public static void main(String[] args) {
     String root = GlobOptions.get().getRootDir();//init
    
    String[] defaultVars = new String[]{"cnc_NO2_gas", "cnc_O3_gas", "cnc_PM10",
    "cnc_PM2_5", "cnc_SO2_gas", "cnc_CO_gas", "cnc_NO_gas",
    "cnc_NH3_gas", "cnc_NMVOC_gas", "cnc_OH_gas"};
    
     String[] secVars = new String[]{"BLH", "MO_len_inv"};
    
     boolean certBypass=false;
     String[] creds =null;
     Dtime end = Dtime.getSystemDate_FH();
     end.addSeconds(-3600*end.hour);
     end.addSeconds(3600*24*2);
     Boundaries b = new Boundaries(60.0,62.0,24,26);
     String dontAcceptDom = "regional";
      ArrayList<SilamAddress> sas =getCurrentSources(defaultVars,System.out,certBypass,creds,b,dontAcceptDom);
      //EnfuserLogger.log(Level.FINER,this.getClass(),"Listing outcome (in priority order):");
      for (SilamAddress sa:sas) {
          sa.printout(System.out);
          String addr = sa.buildDownloadUrl(b,end);
          //EnfuserLogger.log(Level.FINER,this.getClass(),"\t "+ addr);
          sa.switchToSecondaryDataset(secVars,true);
          addr = sa.buildDownloadUrl(b,end);
          //EnfuserLogger.log(Level.FINER,this.getClass(),"\t "+ addr);
      }
        
 }
   
   
     /**
     * Based on HTML-content on silam.fmi.fi/thredds/, the latest SILAM version
     * number (in text, e.g, '5_6_1') is parsed. This information is can be used
     * for data extraction, in case a specified tag
     * (SilamFeed.ADDRESS_BUILDER_KEY) is present. (Note: the Silam region needs
     * to be encapsulated right after the tag)
     *
     * @param notThisVersion a version String that is not allowed (e.g., an
     * early test version)
     * @param regID The Silam modelling region, e.g., europe, glob, china.
     * @param out Printstream for console input (use Feed's SOUT)
     * @param certBypass bypass SSH-certificate check (should not be needed
     * here)
     * @param creds HTTPS credentials (should not be needed, null can be given)
     * @return a String that describes the latest version, e.g., "5_6_1"
     */
    public static String getLatestVersion(String notThisVersion, String regID,
            PrintStream out, boolean certBypass, String[] creds) {

        // get html response from url:
        double latestVersion = Double.MAX_VALUE * -1;
        String latestVersionString = null;

        try {
            String result = RegionalMiner.mineFromURL_static(certBypass,
                    THREDDS_ROOT, out, creds,null,null);
            //println(result);
            ////&nbsp;<a href='/thredds/catalog/silam_china_v5_5_1/catalog.html'><tt>silam_china_v5_5_1/</tt></a></td>
            String[] split = result.split("/thredds/catalog/silam_");
            for (String temp : split) {
                try {
                    String cand = temp.split("/catalog.html")[0];//china_v5_5_1

                    if (!cand.contains(regID)) {
                        continue;//not the region we are interrested
                    }
                    String version = cand.split("_v")[1];
                    String[] decims = version.split("_");
                    double scaler = 1.0;
                    double value = 0;
                    for (String d : decims) {
                        value += Double.valueOf(d) * scaler;
                        scaler /= 10.0;
                    }
                    if (value > latestVersion) {
                        out.println("Newer version: " + value + " => " + version + " for " + regID);
                        if (notThisVersion != null && notThisVersion.equals(version)) {
                            out.println("\t this version is not allowed: " + version);
                        } else {
                            latestVersionString = version;
                            latestVersion = value;
                        }
                    }

                } catch (Exception e) {

                }
            }//for all listed versions

        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

        out.println("Silam latestVersion Outcome: " + latestVersionString);
        return latestVersionString;

    }   
    
}
