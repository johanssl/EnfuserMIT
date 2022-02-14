/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feeds;

import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.essentials.date.Dtime;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;
import java.io.File;
import static org.fmi.aq.enfuser.mining.miner.RegionalMiner.FORECASTING_HOURS;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.CREDENTALS_NONE;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.GLOBAL;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.essentials.gispack.Masks.MapPack;

/**
 *
 * @author Lasse Johansson
 */
public class SilamFeed extends Feed {

    private String domainsExcluded = "";
    public final static String EXCLUDE_DOMAINS ="EXCLUDE_DOMAINS<";

    private final static String ARG_TAG = "SILAM_ARGS";
    private final static String[] SPECS = {"SILAM", 180 + "", ARG_TAG     ,"72"   ,".nc"              , CREDENTALS_NONE};
   
    
    public SilamFeed(FusionOptions ops) {
        super(FeedConstructor.create(SPECS,GLOBAL,ops),ops);
        if (!this.readable && !this.stores) {
            return;
        }
        this.ncUnitConversions = true;//MUST BE so, for latest SILAM THREDDS content.
        //the data can be moles, or kilos.
        this.defaultVars = new String[]{"cnc_NO2_gas", "cnc_O3_gas", "cnc_PM10",
            "cnc_PM2_5", "cnc_SO2_gas", "cnc_CO_gas", "cnc_NO_gas",
            "cnc_NH3_gas", "cnc_NMVOC_gas", "cnc_OH_gas",
            "cnc_HNO3_gas", "cnc_H2SO4_gas", "cnc_HCHO_gas", "cnc_EC_m_50"};
        
        this.defaultVars2 = new String[]{"BLH", "MO_len_inv"};
        if (mainProps.argLine.contains(EXCLUDE_DOMAINS)) {
            this.domainsExcluded = getBetween(mainProps.argLine,EXCLUDE_DOMAINS,">");
        }
 
    }
    
    public static void main(String[] args) {
        testRead();
    }
    
    public static void testRead() {
        FusionOptions ops = new FusionOptions("Finland","pks");
        ops.getRunParameters().setDates(new Dtime("2020-04-01T00:00:00"), 0, 72);
        SilamFeed feed = new SilamFeed(ops);
        GlobOptions g = GlobOptions.get();
        g.setLoggingLevel(Level.FINER);
        feed.read(null, ops.getRunParameters(), new DataBase(ops), null);
    }


    @Override
    public boolean readsInitially() {
        return true;
    }
    
    @Override
    public boolean globalInitially() {
        return false;
    }
    
    @Override
    public boolean writesInitially() {
        return true;
    }
    
    @Override
    public String customArgumentInitially(ArrayList<String> areaNames) {
        return SilamFeed.EXCLUDE_DOMAINS + "regional>, " 
              + TAG_VAR_LIST + "defaultVars>, " 
              + TAG_VAR_LIST2 + "BLH,MO_len_inv>";
    }
    

    @Override
    public void read(DataPack dp, RunParameters p, DataBase DB, MapPack mp) {
        
        int loadSpanH = p.loadSpanHours();
        //expand load area if time span is not too long (2 weeks)
        double fractionIncrease =0;
        double kmInc = 50;
        if (ops.SILAM_visualWindow && loadSpanH < 24*14) fractionIncrease =0.4;
        double[] incs = {kmInc,fractionIncrease};
        this.readGridData(NETCDF,dp, DB, p,incs,NO_FILL);
    }
    

    @Override
    public ArrayList<String> store() {
        this.lastStoreCount = 0;
        this.addedFiles.clear();
        storeTick();

        Dtime dt_start = Dtime.getSystemDate_utc(false, Dtime.FULL_HOURS);//getSystemDate(false,NO_HOURS_MINS, IN_UTC);
        dt_start.addSeconds(-3600 * dt_start.hour);
        Dtime dt_end = dt_start.clone();
        dt_end.addSeconds(FORECASTING_HOURS * 3600);

        println("Extracting SILAM data for " + dt_start.getStringDate());
        //String[] defaultVars, PrintStream out, boolean certBypass, String[] creds, Boundaries b, String domainsNotAccepted
        ArrayList<SilamAddress> sas = SilamAddress.getCurrentSources(this.vars(),
                SOUT, this.certificates, this.creds_usr_pass, b, domainsExcluded);
        

        boolean succ1 = false;
        for (SilamAddress sa:sas) {
            if(succ1) continue;
            sa.printout(SOUT);
            succ1 = getSilamFile_new(sa,dt_start, dt_end);
        }
        
        boolean succ2 = false;
        for (SilamAddress sa:sas) {
            if(succ2) continue;
            sa.switchToSecondaryDataset(this.sec_vars(), true);
            sa.printout(SOUT);
            succ2 = getSilamFile_new(sa,dt_start, dt_end);
        }

        if (succ1) {
            this.lastStoreCount++;
        }
        if (succ2) {
            this.lastStoreCount++;
        }
        return this.addedFiles;
    }

       public boolean getSilamFile_new(SilamAddress sa, Dtime start, Dtime end) {
       
        String dir = this.primaryMinerDir;
        String namebody = "SILAM_";
        if (sa.secondary) {
            namebody = "SILAM_secondary_";
        }
        String st = start.getStringDate_YYYY_MM_DDTHH()+"-00-00Z.nc";
        String localFileName = dir + namebody + st;
        File test = new File(localFileName);
        boolean existed = test.exists();
        try {
            String address =sa.buildDownloadUrl(b, start, end);
            boolean ok = downloadFromURL(localFileName, simpleFileDownload, address, false);
            //ok is true if the file exists and no download occurs.
            if (ok && !existed) {
                addedFiles.add(localFileName);
            }
            return ok;

        } catch (Exception e) {
            return false;
        }
    }
}
