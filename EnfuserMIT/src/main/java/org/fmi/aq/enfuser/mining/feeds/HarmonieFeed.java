/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feeds;

import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;
import java.io.File;
import org.fmi.aq.enfuser.mining.miner.RegionalMiner;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.BALTIC;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.CREDENTALS_NONE;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.essentials.gispack.Masks.MapPack;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;

/**
 *
 * @author johanssl
 */
public class HarmonieFeed extends Feed {
    
    private final static String ARG_TAG = "HARMONIE_ARGS";
    private final static String[] SPECS =  {"HARMONIE", "120", ARG_TAG,"12"   ,".nc" , CREDENTALS_NONE};
    private final float sizeIncreaser = 1;
    private final float coordAdd = 0.1f;
    private int levels =0;
    public final static String LEVELS = "LEVELS<";
    
        public static boolean isMe(String argTag) {
        if(argTag==null) return false;
        return ARG_TAG.equals(argTag);
    }
        
    public HarmonieFeed(FusionOptions ops) {
        super(FeedConstructor.create(SPECS,BALTIC,ops),ops);
        
        //HARMONIE_2019-11-29T00-00-00Z_59.78_60.69_23.9_25.88_.nc
        this.fileReader.setDateSplitIndex_file(1);//date is the second element.
        this.fileReader.setBoundsSplitIndex_file(2);//bounds is to be parsed from 2,3,4,5 indices, so starting from 2.
        this.defaultVars = new String[]{
            "Pressure",
            "DewPoint",
            "Humidity",
            "Temperature",
            "WindUMS",
            "WindVMS",
            "PrecipitationAmount",
            "TotalCloudCover",
            "RadiationGlobal",
            "WindGust",
            "RadiationNetSurfaceSWAccumulation"};
        this.refreshModulo = 3;//every 2nd extraction all existing data will be replaced. 
        //Extraction ticker is 180min and new Harmonie data is available every 6 hour. Should be perfect.
        
        if (mainProps.argLine.contains(LEVELS)) {
            String levs = getBetween(mainProps.argLine,LEVELS,">");
            levels = Integer.valueOf(levs);
        }
    }
    
    public static void main(String[] args) {
        FusionOptions ops = new FusionOptions("Finland");
        HarmonieFeed feed = new HarmonieFeed(ops);
        feed.levels =3;
        feed.store();
    }
    
    
    @Override
    public boolean writesInitially() {
        return true;
    }
    @Override
    public boolean globalInitially() {
        return false;
    }
    @Override
    public String customArgumentInitially(ArrayList<String> areaNames) {
        String s = TAG_CUSTOM_AREAS;
        int k =0;
        for (String ar:areaNames) {
            k++;
            s+=ar;
            if (k<areaNames.size())s+=",";
        }
        return s+">";
    }
    @Override
    public boolean readsInitially() {
        return true;
    }
    
    @Override
    public void read(DataPack dp, RunParameters p, DataBase DB, MapPack mp) {
        this.readGridData(NETCDF,dp, DB, p, DEFAULT_EXPANSION,NO_FILL);
    }

    @Override
    public ArrayList<String> store() {
        storeTick();
        ArrayList<String> arr = new ArrayList<>();
        boolean refresh = storeTicks % this.refreshModulo == 0;
        
       int HSTEP =6; 
       ArrayList<Integer> HOURS = RegionalMiner.forecastingOffsets(HSTEP);

        for (int h : HOURS) {
            if (this.customAreaBounds == null) {
                println("Extracting HARMONIE-data...");
                ArrayList<String> ar = getHarmonieFile(this.b, h, refresh, HSTEP);//true for netCDF
                arr.addAll(ar);
                this.lastStoreCount = ar.size();

            } else {//one or more custom smaller areas. Harmonie data size is large so specific smaller target areas can be nice to define.
                this.lastStoreCount = 0;

                for (Boundaries btemp : this.customAreaBounds) {
                    //add some marging
                    Boundaries bb = btemp.clone();
                    bb.expand(this.sizeIncreaser);
                    bb.latmax += this.coordAdd;
                    bb.latmax = editPrecision(bb.latmax, 2);

                    bb.lonmax += this.coordAdd;
                    bb.lonmax = editPrecision(bb.lonmax, 2);

                    bb.latmin -= this.coordAdd;
                    bb.latmin = editPrecision(bb.latmin, 2);

                    bb.lonmin -= this.coordAdd;
                    bb.lonmin = editPrecision(bb.lonmin, 2);

                    println("Extracting HARMONIE-data (custom Bounds = " + bb.toText() + ")...");
                    ArrayList<String> ar = getHarmonieFile(bb, h, refresh, HSTEP);//true for netCDF
                    arr.addAll(ar);
                    this.lastStoreCount += ar.size();
                }
            }
        }//for hourAdd
        return arr;
    }

    public boolean netCDF = true;

    public ArrayList<String> getHarmonieFile(Boundaries b, int hoursAdd, boolean refresh, int hoursMax) {
        ArrayList<String> arr = new ArrayList<>();
        final String Z = "Z";

        String latestDate = null;
        String[] vars = this.vars();//default or custom, depending on argument line for HARMONIE.
        Dtime start = null;
        String startTime = null;
        Dtime end = null;
        String endTime = null;
        try {
            String[] temp = getLatestHarmonieDate(this);
            if (temp != null) {
                latestDate = temp[0];
                start = new Dtime(latestDate);
                start.addSeconds(hoursAdd * 3600);
                startTime = start.getStringDate(Dtime.STAMP_NOZONE);

                end = new Dtime(latestDate);
                end.addSeconds((hoursAdd + hoursMax) * 3600);

                endTime = end.getStringDate(Dtime.STAMP_NOZONE);
                this.println("hirlam end date = " + end.getStringDate(Dtime.STAMP_NOZONE) + "Z");
            }

        } catch (Exception ex) {
            this.printStackTrace(ex);
        }

        String format_grib = "&format=grib2";
        String format_netCDF = "&format=netcdf";

        String format;
        if (netCDF) {
            format = format_netCDF;
        } else {
            format = format_grib;
        }

        String address = "https://opendata.fmi.fi/download?producer=harmonie_scandinavia_surface&param=";

        int j = 0;
        for (String var : vars) {
            address += var;
            if (j < vars.length - 1) {
                address += ",";
            }
            j++;
        }

        address += "&bbox=" + b.lonmin + "," + b.latmin + "," + b.lonmax + "," + b.latmax
                + "&origintime=" + latestDate + Z + "&starttime=" + startTime + Z + "&endtime=" + endTime + Z
                + format + "&projection=epsg:4326&levels="+levels+"&timestep=60";

        String refr = "";
        if (refresh) {
            refr = " (refresh mode is on)";
        }
        this.println("Extracting HARMONIE" + refr + " with\n" + address);

        String localFileName = this.primaryMinerDir + "HARMONIE_" + startTime.replace(":", "-") + "Z_" + b.toText_fileFriendly(3) + ".grb2";
        if (address.contains(format_netCDF)) {
            localFileName = localFileName.replace(".grb2", ".nc");
        }

        try {
            boolean succ = this.downloadFromURL(localFileName, simpleFileDownload, address, refresh);
            if (succ) {
                arr.add(localFileName);
            }
        } catch (Exception e) {

        }
        return arr;
    }


    private final static String BODY1 = "https://opendata.fmi.fi/wfs?request=GetFeature&storedquery_id=fmi::forecast::harmonie::surface::grid";

    private String[] getLatestHarmonieDate(Feed f) throws Exception {
        String date = null;
        String address = BODY1 + "";//+apiKey +BODY2;

        String currContent = f.mineFromURL(address,null);
        f.SOUT.println(currContent);
        date = getBeginDate(currContent);
        f.SOUT.println("Harmonie startDate = " + date);

        return new String[]{date, currContent};
    }

    public String getBeginDate(String content) {

        String start = "<gml:beginPosition>";
        String end = "</gml:beginPosition>";

        String[] temp = content.split(start);
        String[] temp2 = temp[1].split(end);
        return temp2[0].replace("Z", "");

    }

    @Override
    public void releaseStorageSpace(String storageDir) {

        String dir = this.primaryMinerDir;
        File f = new File(dir);
        File[] ff = f.listFiles();

        for (File file : ff) {

            String name = file.getName();
            if (!name.contains(".nc")) {
                continue;
            }
            String[] spl = name.split("_");
            String date = spl[1].replace(".nc", "");
            if (!date.contains("T00-")) {
                continue;//check only these
            }
            date = date.replace("-00-00Z", ":00:00");

            try {

                Dtime dt = new Dtime(date);
                Dtime dt_next = dt.clone();
                dt_next.addSeconds(3600 * 24);
                //check if there is a file for this data
                String testName = "HIRLAM_" + dt_next.getStringDate_YYYY_MM_DD() + "T00-00-00Z.nc";
                File test = new File(dir + testName);
                if (test.exists()) {//delete most files between these two timestamps. 06 and 18 are unnecessary!
                    File delFile = new File(dir + testName.replace("T00-00-00Z", "T06-00-00Z"));
                    if (delFile.exists()) {
                        delFile.delete();
                        EnfuserLogger.log(Level.FINER,this.getClass(),"Deleting " + delFile.getAbsolutePath());
                    }

                    delFile = new File(dir + testName.replace("T00-00-00Z", "T18-00-00Z"));
                    if (delFile.exists()) {
                        delFile.delete();
                        EnfuserLogger.log(Level.FINER,this.getClass(),"Deleting " + delFile.getAbsolutePath());
                    }
                }

            } catch (Exception e) {

            }

        }

    }

    public String fileNameToDate(String file) {
        String timestamp = file.replace("HARMONIE_", "");

        timestamp = timestamp.replace("-00-00Z.nc", ":00:00");
        timestamp = timestamp.replace("_00_00Z.nc", ":00:00");
        return timestamp;
    }

}
