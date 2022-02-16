/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

import org.fmi.aq.enfuser.core.assimilation.AdjustmentBank;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.enfuser.datapack.source.DBline;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.enfuser.core.FusionCore;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;
import org.fmi.aq.interfacing.ArchStatProcessor;

/**
 *
 * @author johanssl
 */
public class StatsCruncher {
    double[] corlStats = new double[]{0,0,0,0};
    double[] corlStats_forc = new double[]{0,0,0,0};
    public HashMap<Integer,EvalDat> dats = new HashMap<>();
    HashMap<Integer,EvalDat> false_dats = new HashMap<>();
    public String id;
    public String name;
    public String sec_name = "";
    final int typeInt;

    final VarAssociations VARA;
    final Categories CATS;
    ArrayList<ConditionalAverager> averagers;
    
    public StatsCruncher(String ID, String type,
            AdjustmentBank oc, FusionOptions ops, TZT tzt) {
        this.id = ID;
        name = ID + "_" + type;
        VARA = ops.VARA;
        CATS = GlobOptions.get().CATS;
        this.typeInt = VARA.getTypeInt(type);
        
        averagers = ConditionalTempoAverager.getDefault(ops,tzt);
        averagers.addAll(ConditionalMetAverager.getDefault(ops));
    }

    public final static int PRED = 0;
    public final static int OBS = 1;
    public final static int MODBG = 2;
    public final static int PRED_FORC = 3;
    public final static int PRED_MAE = 4;
    public final static int COUNTERS = 5;
    public final static int RAW_OBS = 6;

    public final static String[] HEADERS_SOURCTYPE = {
        "Predicted concentration",
        "Observed concentration",
        "Regional BG",
        "Predicted forecast",
        "Prediction MAE",
        "Datapoint count",
        "Raw Observation"
    };

    protected static String Y_NAME(int ind) {
        return HEADERS_SOURCTYPE[ind];
    }
    
    /**
     * Create an array of comparisons between measurements and predictions
     * for the specified (temporal) averaging type.Each line provided
     * contains 1) the source id 2) averagingType description 3) avg.prediction
     * 4) avg. observation and 5) averaging data point counter. 
     * 
     * @param averagingType one of HEADERS_TEMPORAL, e.g., MONTH =0;
     * @param metvar
     * @param arr an array where lines are added to. If empty of null, a header
     * will be added as the first line.
     * @param minCounter
     */
    public void addCSVcomparison(int averagingType, boolean metvar,
            ArrayList<String> arr, int minCounter) {
        
        ConditionalAverager ca =this.find(averagingType, metvar);

        float[] preds = ca.getXvector(PRED);
        float[] obs = ca.getXvector(OBS);
        float[] counters = ca.getXvector(COUNTERS);
        String[] xHead = ca.xHeader();
        
        if (arr.isEmpty()) {
            arr.add("ID;"+ca.datasetLabel+";PRED;OBS;N;");
        }
        
        for (int t =0;t<preds.length;t++) {
            
            float count = counters[t];
            if (count<minCounter) continue;
            
            String line =this.id+";"
                    +xHead[t]+";"
                    +csv(preds[t])
                    +csv(obs[t])
                    +(int)count;
            
            arr.add(line);
        }
    }
    
    private ConditionalAverager find(int averagingType, boolean metvar) {
 
        for (ConditionalAverager test:this.averagers) {
            if (metvar && (test instanceof ConditionalMetAverager)) {
                ConditionalMetAverager c = (ConditionalMetAverager)test;
                if (c.metvar==averagingType) {
                    return test;
                }//if metvar match
            }//
            else if (!metvar && test instanceof ConditionalTempoAverager){//temporal
               ConditionalTempoAverager c = (ConditionalTempoAverager)test; 
               if (c.type == averagingType) {
                   return test;
               }
            }
    }
        return null;
    }
    
    public String getOneliner() {
        
        String line = this.name + ";" 
                + this.sec_name + ";"
                + this.shortSecondary() +";"
                + this.latlon()[0]+";"
                + this.latlon()[1] +";";
        //find the averager that does not filter anything.        
        ConditionalAverager ca = find(ConditionalTempoAverager.FILT_ALL,false);
        float[] vect = ca.getYvectorforX(0);//the averager is 'all' so this just lists the averaged products in default order.
        for (float f:vect) {
            line+= csv(f);
        }
        for (int i =0;i<CORRL_NAMES.length;i++) {
            line+=csv((float)this.corlStats[i]);
        }
        line+=";";
        for (int i =0;i<CORRL_NAMES.length;i++) {
            line+=csv((float)this.corlStats_forc[i]);
        }
        
        return line;
    }

    private static String csv(float f) {
        return editPrecision(f,3)+";";
    }

    public static String getOneLinerHeader(Categories CATS) {
        String line = 
                 "Name;"
                +"Secondary Name;"
                +"ShortName;"
                + "Latitude;"
                + "Longitude;";
        
       for (String s:HEADERS_SOURCTYPE) {
           line+=s+";";
       }        
       line +=CATS.headerString();
       for (int i =0;i<CORRL_NAMES.length;i++) {
            line+=CORRL_NAMES[i]+";";
        } 
       line+=";";
       for (int i =0;i<CORRL_NAMES.length;i++) {
            line+="FORECASTED_"+CORRL_NAMES[i]+";";
        } 
       return line;
    }

    public float[] latlon() {
        EvalDat ed = null;
        for (EvalDat e:this.dats.values()) {
           ed = e;
           break;
        }
        if (ed ==null) return new float[]{0,0};
        return new float[]{ed.lat,ed.lon};
    }
 
    private void hourlyCSV(FusionCore ens) {
        if (name.startsWith("PAS_"))return;
        
        ArrayList<String> ss = new ArrayList<>();
        String debugHeader = DebugLine.getFullDebugLine_HEADER(";",ens.ops);
        ss.add(debugHeader);
         for (EvalDat dat : dats.values()) {
            ss.add(dat.debugLine);      
         }
         
        String name2 = this.fileName()  + "_stats_hourly.csv";
        String dir = ens.ops.statsCrunchDir(this.typeInt);
        FileOps.printOutALtoFile2(dir, ss, name2, false); 
    }
 
    public void crunchToFile(FusionOptions ops, FusionCore ens) {
        if (dats.isEmpty()) {
            EnfuserLogger.log(Level.INFO,StatsCruncher.class,"StatCruncher: NO DATA FOR " + this.name);
            return;
        }
        this.corlStats = this.correlationStats(false);
        this.corlStats_forc = this.correlationStats(true);
        ArrayList<String> results = new ArrayList<>();
        //start with basic info
        String aliasID = "";
        DBline dbl = ens.DB.get(id);
        if (dbl != null) {
            aliasID = dbl.alias_ID;
        }
        this.sec_name = aliasID;
        results.add( this.name + "," + aliasID);
        results.add("virtual forecasting point removals: " + this.false_dats.size() 
                + ", true Leave-One-Outs:"+this.getLOOVcounter());
       
        for (int i =0;i<CORRL_NAMES.length;i++) {
            results.add(CORRL_NAMES[i]+";"+this.corlStats[i]);
        }   
        results.add(getLocationSpecsLine(ens));
        results.add("\n");
       
        //the averagers
        //distribute data to averagers
        for (EvalDat dat : dats.values()) {
            for (ConditionalAverager cme:averagers) {
                cme.updateStats(dat);
            }
        }
        //process averaged data into CSV
        for (ConditionalAverager cme:averagers) {
            results.addAll(cme.processToCSVbuffer());
            results.add("\n");
        }
        
        //perhaps some custom instructions exists for more statistics creation?
        ArchStatProcessor.addCustomStatisticsForStatsCruncher(results,ens,this);
        
        //done. To file.
        String dir = ops.statsCrunchDir(this.typeInt);
        String fname = this.fileName() + "_stats.csv";
        FileOps.printOutALtoFile2(dir, results, fname, false);
        //must not forget the hourly csv-file
        hourlyCSV(ens);

    }
    
    public void add(Observation o, StationSource st, FusionCore ens) {
        if (o.incorporatedEnv == null || o.incorporatedEnv.met == null) {
            return;
        }
        if (!ens.sufficientDataForTime(ens, o.dt)) return;
        int key = o.dt.systemHours();
        this.dats.put(key,new EvalDat(ens,ens.ops, o));
    }
    
    public void add(EvalDat ed, TZT tzt) {
      if (ed!=null) {
          if (ed.isForecastDummy()) {
            this.false_dats.put(ed.dt().systemHours(),ed);  
          } else {
            this.dats.put(ed.dt().systemHours(),ed);
          }
      }
    }

    public int getLOOVcounter(){
        int sum =0;
        for (EvalDat ed:dats.values()) {
            if (ed.loovPoint)sum++;
        }
        return sum;
    }


    public ArrayList<String> allEvalPointsArr() {
        //"TIME;MONTH;NAME;OBS;PREDICTED;"
        ArrayList<String> arr = new ArrayList<>();

        for (EvalDat p : this.dats.values()) {
            Dtime dt = p.dt();
            String line = dt.getStringDate() + ";" + dt.month_011 + ";" 
                    + this.id + ";" + p.value() + ";" + p.statsCrunchVector[PRED] + ";";
            
            //then components
            for (float f:p.expComponents) {
                line+=editPrecision(f,3) +";";
            }
            //then meteotology
            line+= p.met.metValsToString(";");
            arr.add(line);
        }
        return arr;
    }

    public static String weekDayString(Dtime local, boolean simple) {
        String s = "N/A";
        boolean monfr = true;

        if (local.dayOfWeek == Calendar.MONDAY) {
            s = "Mon";
        } else if (local.dayOfWeek == Calendar.TUESDAY) {
            s = "Tue";
        } else if (local.dayOfWeek == Calendar.WEDNESDAY) {
            s = "Wed";
        } else if (local.dayOfWeek == Calendar.THURSDAY) {
            s = "Thu";
        } else if (local.dayOfWeek == Calendar.FRIDAY) {
            s = "Fri";
        } else if (local.dayOfWeek == Calendar.SATURDAY) {
            s = "Sat";
            monfr = false;
        } else if (local.dayOfWeek == Calendar.SUNDAY) {
            s = "Sun";
            monfr = false;

        }

        if (simple && monfr) {
            return "Mon-Fri";
        } else {
            return s;
        }

    }

    public boolean hasData() {
        return this.dats.size() > 0;
    }

    public ArrayList<EvalDat> getDataPoints() {
        ArrayList<EvalDat> arr = new ArrayList<>();
        for (EvalDat ed:this.dats.values()) {
            arr.add(ed);
        }
       return arr;
    }

    public int typeInt() {
        return this.typeInt;
    }

    private String getLocationSpecsLine(FusionCore ens) {
        float[] latlon = this.latlon();
        double lat = latlon[0];
        double lon = latlon[1];
        String line = "LocationInfo:;"+ lat +";"+lon +";";
         BlockAnalysis ba =ens.getBlockAnalysis(null,false, (float)lat,(float)lon,this.id);
        //BlockAnalysis ba = ens.getBA(ed.lat,ed.lon,ed.ID);
        if (ba!=null) line+= ba.onelinerSummary();
        return line;
    }

    private String fileName() {
       if (this.sec_name==null ||this.sec_name.length() < 2) return this.name;
       if (this.name.contains("PAS")) return name;
       return this.sec_name;
    }

private final static String[] CORRL_NAMES = {"RMSE","Bias","F2","Pearson correlation","NMSE","FB (fractional bias)","IA"};
private double[] correlationStats(boolean forc) {
    double SSE =0;//sum of squared errors
    double bias = 0;
    double f2 =0;
    double P=0;
    double Ob=0;
    int N = this.dats.values().size();
    int fn =0;
    
    if (N<2) return new double[]{0,0,0,0,0,0};
    
     for (EvalDat ed:this.dats.values()) {
         double modval = ed.modelledValue();
         if (forc)modval = ed.forecastEstimate();
         double diff = modval - ed.value();
         double se = diff*diff;//squared error
         SSE+=se;
         bias+=diff;
         
         P+=modval;
         Ob+=ed.value();
         
         
         if (ed.value()!=0) {
             double ratio = modval/ed.value();
             if (ratio >=0.5 && ratio <=2) f2++;
             fn++;
         }
    }
     P/=N;//average prediction
     Ob/=N;//average observation
     double MSE = SSE/N;
     
     double NMSE = MSE/(P*Ob); //normalized mean squared error
     double RMSE=Math.sqrt(MSE);//root mean squared error
     double FB = (Ob - P)/(Ob + P)*2;
     
     
     bias/=N;
     f2/=fn;
     double[] pcc_ia = this.getPearsonCorrelation_IA(forc);
     return new double[]{RMSE,bias,f2,pcc_ia[0],NMSE,FB,pcc_ia[1]};
}

private double[] getPearsonCorrelation_IA(boolean forc) {
    
    double X = 0;//prediction average
    double Y = 0;//observation average
    int N = this.dats.values().size();
    if (N<2) return new double[]{0,0};
    
    for (EvalDat ed:this.dats.values()) {
        double modval = ed.modelledValue();
        if (forc)modval = ed.forecastEstimate();
        X+=modval;
        Y+=ed.value();
    }
    X/=N;
    Y/=N;
    //averages complete.
    
    double SIGMA_x_Xy_Y = 0;
    double SIGMA_x_X2=0;
    double SIGMA_y_Y2=0;
    
    double ia_upper=0;
    double ia_lower=0;
    
    for (EvalDat ed:this.dats.values()) {
        double x = ed.modelledValue();
        if (forc)x = ed.forecastEstimate();
        
        double y = ed.value();
        //update term sums /divisor)
         SIGMA_x_X2 += (x-X)*(x-X);// builds up SIG (x -X)^2
         SIGMA_y_Y2 += (y-Y)*(y-Y);// builds up SIG (y -Y)^2
        //update term sum (upper)
         SIGMA_x_Xy_Y += (x-X)*(y-Y);
         
         ia_upper+=(x-y)*(x-y);
         double ial = Math.abs(x-Y) + Math.abs(y-Y);
         ial*=ial;
         ia_lower+=ial;
    }
    double divisor = Math.sqrt(SIGMA_x_X2*SIGMA_y_Y2);
    if (divisor==0) return new double[]{0,0};
    double pearson = SIGMA_x_Xy_Y/divisor;
    double ia = 1- ia_upper/ia_lower;
    
    
    return new double[]{pearson,ia};
}

    private String shortSecondary() {
       String s = ""+this.sec_name;
       s = s.toUpperCase();
       if (s.length()<=3) return s;
       return s.substring(0, 3);
    }
}
