/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.reader;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.logging.Level;
import org.fmi.aq.enfuser.datapack.source.DBline;
import org.fmi.aq.enfuser.datapack.source.DBsearch;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.datapack.main.Observation;
import static org.fmi.aq.enfuser.ftools.FuserTools.evaluateSpan;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.enfuser.mining.feeds.FeedConstructor;
import org.fmi.aq.enfuser.options.VarConversion;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.enfuser.logging.Parser;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.gispack.Masks.MapPack;

/**
 *A class intended for Feeds to parse file properties, especially the date and
 * possible bounding box information. This class however, is also used for
 * text parsing rules for feeds that read text data.
 * 
 * The main use case for file properties is in the Feed's method to list relevant
 * input files. As default, all feeds create an instance of
 * this with default settings. The default settings are as follows:
 * - The date can be parsed from the last element when the file name is splitted
 * with '_'.
 * -The time stamp has the following format, e.g. 2020-10-21T16-00-00Z.
 * 
 * @author johanssl
 */
public class FileReader {
   
    //=====file specs and acceptance rules============
   //=========================================
   int file_dtIndex =1;
   boolean file_dateAtLastSplit = true;
   private int file_boundsReadIndex=-1;
   public boolean file_BoundsRelevant =false;
   public int file_customIDindex =-1;
   final String file_defaultID;
   
   public String[] file_dateReplacement =null;
   public String file_DateAdd =null;
   public boolean file_noDateRelevance=false;//the files to be read do not have any particular time relevance
   public boolean file_yearMatching = false;//if true, then the file is deemed relevant if start/end year is contained in file name.
   
   final String[] file_formatEnds;
   int file_defaultPriority =0;
   HashMap<String,Integer> file_prioritySwitch= null;
   
   
   //=====text line parsing rules============
   //=========================================
   //with default settings a line looks like '61.0;25.1;Station10;2020-01-01T00:00:00;NO2;25.0;'
    public byte unreliability =0;//this is for measurement data point replacement rules. If another measurement with same date is added to the same StationSource, then the Observation with LOWER value will prevail.
    //This can happen when e.g., AQICN and FMI-Open reads measurements for the SAME station. 
    public boolean skipFirstLine=true;//set as false if there's no header lines in the file.
    
    public int parsedIndex_lat = 0;//read latitude from this split index (file name or line)
    public int parsedIndex_lon = 1;//read longitude from this split index (file name or line)
        public boolean coordsFromFilename=false;//if true, then the split '_' name has coordinates and they are read with the given indices.
        public boolean isPointData=true;//set this as false (no ID's and DBlines) only for SPECIAL data types that override a customTextParser method.
        public boolean dummyCoordinates =false;//set this true (lat,lon => middle of Boundaries) only for SPECIAL data types that override a customTextParser method.
    public int parsedIndex_ID = 2;//read ID from this split index (file name or line)
        public String additionToID=null;//if non-null, adds this to the left-hand-side of String.
        public boolean IdFromFilename=false;//if true, then the split '_' name has ID and it is read with the given index.
        public boolean IDcleanUp = false;//if true, removes characters that are not nice for file names.
        private DBsearch cachedDB = null;//if non-null, then a faster cached distance-based search for DBline entries is used.
    //time parsing    
    public int parsedIndex_dt = 3;//parse date stamp from this split index (file name or line)
        public ArrayList<String[]> obs_dt_replacements=new ArrayList<>();//modify String before date parsing
        public int flatHourAdd=0;//add this many hours to every data point Dtime.
        public boolean dateFromFile=false;//if true, then file date is used for each LINE of text-data.
        
    public int parsedIndex_type = 4;//index for variable type, used if NOT multiTypeLine
    public int parsedIndex_value = 5;//index for observation value for the type, used if NOT multiTypeLine
        private HashMap<Integer,VarConversion> index_to_type=null; //used if multiTypeLine
        public boolean multiTypeLine=false;//a multi-type line: there are several values in different columns.
        //the column name carries the type definition.
        private String acceptAndRemoveFromType=null;//AQICN special
       
    
    public int parsedIndex_quality=-1;//if larger than -1, then a unreliability metric for the StationSource is read from this index. This is the one used in DataFusion and is logged to db.csv.
        public boolean qualityFromFilename =false;
    public int parsedIndex_mheight=-1;//parse measurement height above ground from this index, if non-negative.
        public boolean mHeightFromFilename =false;
    public int parsedIndex_units=-1;//TODO
        

    public boolean monFriSparser=false;

   public FileReader(String[] formatEnds, String defID) {
    this.file_formatEnds = formatEnds;
    this.file_defaultID = defID;
   }
   
    
   public void setCachedDBsearch() {
       this.cachedDB =new DBsearch();
   }
   
   /**
    * Create a default instance. As default, all feeds create an instance of
 * this with default settings. The default settings are as follows:
 * - The date can be parsed from the last element when the file name is splitted
 * with '_'.
 * -The time stamp has the following format, e.g. 2020-10-21T16-00-00Z.
    * @param cons
    * @return 
    */
   public static FileReader getDefault(FeedConstructor cons) {
       FileReader fp = new FileReader(cons.formats, cons.feedDirName);
       fp.file_dateReplacement = new String[]{"00-00-00Z", "00:00:00"};
       return fp;
   }
   
   /**
    * Read the date from this index specifically.
    * @param i 
    */
   public void setDateSplitIndex_file(int i) {
       this.file_dateAtLastSplit =false;
       this.file_dtIndex =i;
   }
  
   /**
    * A file bounding box is parsed from the file name using this index
    * and the next 3. The order of values should be latmin,latmax,lonmin, lonmax.
    * @param i 
    */
   public void setBoundsSplitIndex_file(int i) {
       this.file_boundsReadIndex = i;
       if (this.file_boundsReadIndex<0) {
           this.file_BoundsRelevant = false;
       } else {
           this.file_BoundsRelevant = true;
       }
   }

   public String getFileID(File f) {
       if (this.file_customIDindex<0) return this.file_defaultID;
       String[] sp = f.getName().split("_");
       return sp[this.file_customIDindex];
   }
   
   public void addToFileDate(String string) {
       this.file_DateAdd = string;
   }
   
   
   public Dtime parseFileDate(Parser parser, File f, RunParameters p) {
       if (this.file_noDateRelevance|| this.file_yearMatching) return p.start();
       String name = f.getName();
       if (!name.contains("_")) {
           parser.logDateParseException(f.getName() + " does not contain the splitter '_'");
           return null;
       }
       
       for (String form:this.file_formatEnds) {
           if (name.contains(form)) {
               name = name.replace("~1.", ".");//remove some possible misnaming
               String[] sp = name.split("_");
               
               String date;
               if (this.file_dateAtLastSplit) {
                  date= sp[sp.length-1];
                  
               } else {
                   if (sp.length<=file_dtIndex) {
                    parser.logDateParseException(f.getName() + " date read index is too large: "+this.file_dtIndex);
                    return null;
                   }    
                   
                  date= sp[file_dtIndex];  
               }
               
               date = date.replace(form, "");
               if (file_dateReplacement!=null){
                   date = date.replace(file_dateReplacement[0], file_dateReplacement[1]);
               }
               if (file_DateAdd!=null)date+=file_DateAdd;
               Dtime dt = parser.parseDt(date, f.getName());
               
               if (dt!=null && monFriSparser) {//HERE special. Too much redundant data to read => sparse.
                    if (dt.dayOfWeek == Calendar.WEDNESDAY 
                            || dt.dayOfWeek == Calendar.TUESDAY 
                            || dt.dayOfWeek == Calendar.THURSDAY) {
                        return null;//don't read this file.
                    }
                }
               return dt;
           }//if file has this accepted format
       }//for listed formats
       parser.logDateParseException(f.getName() + " did not contain the Feed's file type extensions.");
       return null;
   }
   
   public Boundaries parseFileBounds(Parser parser, File f) {
       if (!this.file_BoundsRelevant) return null;
       
        String name = f.getName();
       if (!name.contains("_")) return null;
       
       //special: lat,lon is parsed from filename and we use this for this check.
       if (this.coordsFromFilename) {
           String[] sp = name.split("_");
           this.splitName =sp;
           Double lat = this.lat(parser, null,null);
           Double lon = this.lon(parser, null,null);
           if (lat!=null && lon !=null) {
               return new Boundaries(lat,lat+0.01, lon, lon+0.01);
           }
           return null;
       }

       for (String form:this.file_formatEnds) {
           if (name.contains(form)) {
               name = name.replace("~1.", ".");//remove some possible misnaming
               String[] sp = name.split("_");
               if (sp.length<=this.file_boundsReadIndex+4) return null;
               return parser.parseBounds(sp, this.file_boundsReadIndex);
               
           }
       }
       return null;  
   }
   
   public int getFilePriorityReducer(String name) {
       if (this.file_prioritySwitch==null || this.file_prioritySwitch.isEmpty()) {
           return this.file_defaultPriority;
       }
       
       for (String test:this.file_prioritySwitch.keySet()) {
           if (name.contains(test)) return this.file_prioritySwitch.get(test);
       }
       return this.file_defaultPriority;
   }

    public void addFilePriorityReducer(String value, int i) {
       if (this.file_prioritySwitch==null) this.file_prioritySwitch= new HashMap<>();
       this.file_prioritySwitch.put(value, i);
    }

    public boolean fileBoundsAcceptable(Parser parser, File add, Boundaries b) {
        if (!this.file_BoundsRelevant) return true;//has no relevance => read.
        Boundaries fileBounds = parseFileBounds(parser, add);
        if (fileBounds!=null && !fileBounds.intersectsOrContains(b)) {
            return false;
        } else {
        return true;
        }
    }

    private long fileIndex=0;
    /**
     * Return a hash key (long) that can be used to store this file in hash.
     * However, in case the file has no temporal relevance this returns
     * null (the file will not be read).
     * For some feeds all files are readable (no temporal relevance in file names)
     * and in such cases this returns a running index.
     * @param parser
     * @param add
     * @param checkSpanH
     * @return 
     */
    public Long fileDatesAcceptable(Parser parser, File add, RunParameters p, int checkSpanH) {
        
        if (this.file_yearMatching) {
            String nam = add.getName();
            if (nam.contains(p.start().year+"")) return p.start().systemSeconds();
            if (nam.contains(p.end().year+"")) return p.end().systemSeconds();
            return null;
        }
        
        if (this.file_noDateRelevance) {
            fileIndex++;
            return fileIndex;
        }
        Dtime start = p.loadStart();
        Dtime end = p.end();
        Dtime dataStart = parseFileDate(parser, add,p);
                if (dataStart==null) {
                    return null;
                }
                //span check
                int[] checkH = {dataStart.systemHours(),
                    dataStart.systemHours() + checkSpanH};
                
                boolean timeOk = evaluateSpan(start, end, checkH);//check timing
                if (!timeOk) {
                    EnfuserLogger.log(Level.FINER,this.getClass(),"File not time-relevant: "
                            + add.getAbsolutePath() +", "+dataStart.getStringDate_noTS());
                    return null;
                }
                //all is well, add to list. It is possibe that the time-key is already reserved.
                //this commonly happens e.g., with SILAM_ and SILAM_secondary files.
                return dataStart.systemSeconds();
    }

    
    //Text content parsing methods=============================================
    //=========================================================================
   
    
    private String filen;
    private String[] splitHeader;
    private String[] splitName;
    private Dtime fileDate;
    private int additions =0;
    private int filesRead =0;
    
    /**
     * Set file names, header String and variable mappings in order before parsing
     * content from this file.
     * @param parser
     * @param file the file to be parsed soon.
     * @param header the first line of the file.
     * @param f the feed
     * @param vars a mapping of variable types that should be read.
     */
    public void setFileForParsing(Parser parser, File file, String header,
            Feed f, HashMap<Integer,String> vars) {
        this.additions=0;
        this.filesRead++;
        this.filen = file.getAbsolutePath();
        this.splitHeader = header.split(";");
        this.splitName = file.getName().split("_");
        this.fileDate = this.parseFileDate(parser, file, f.ops.getRunParameters());
        if (this.multiTypeLine) {
            this.index_to_type = new HashMap<>();
            int ind =-1;
            for (String s:splitHeader) {
                ind++;
                s = s.replace(" ", "");
                if (acceptAndRemoveFromType!=null) {//AQICN special: e.g., _AQI
                    //the type must contain this, but we temporarily remove it (so that we understand the type). It's complicated.
                    if (!s.contains(acceptAndRemoveFromType)) continue; //e.g., NO2 and not NO2_AQI
                    s = s.replace(acceptAndRemoveFromType, ""); //NO2_AQI => NO2
                }
                VarConversion vc = f.ops.VARA.allVC_byName.get(s);
                if (vc!=null && vars.containsKey(vc.typeInt)) {
                    this.index_to_type.put(ind, vc);
                }
            }//for header
        }//if multi-line
    }

    
    private final static String USE_MHEIGHT ="measurementHeight";
    private final static String USE_QUALITY ="measurementQuality"; 
    private final static String USE_LAT ="LAT";
    private final static String USE_LON ="LON";
    private final static String USE_VALUE ="VALUE";
    
    /**
     * Parse latitude information, either from line or from filename.
     * If unsuccessful, returns null and problem is logged into Parser.
     * @param safeParser
     * @param splitline
     * @return 
     */
    private Double lat(Parser safeParser, String[] splitline, Boundaries b) {
        if (b!=null && dummyCoordinates) return b.getMidLat();
        
        String[] cont = splitline;
        if (this.coordsFromFilename) cont = splitName;
        String c = safeParser.fetch(cont,parsedIndex_lat);
        if (c==null) return null;
        return safeParser.parseDouble(c, USE_LAT);

    }
    
    /**
     * Parse longitude information, either from line or from filename.
     * If unsuccessful, returns null and problem is logged into Parser.
     * @param safeParser
     * @param splitline
     * @return 
     */
    private Double lon(Parser safeParser, String[] splitline,Boundaries b) {
        if (dummyCoordinates) return b.getMidLon();
        String[] cont = splitline;
        if (this.coordsFromFilename) cont = splitName;
        String c = safeParser.fetch(cont,parsedIndex_lon);
        if (c==null) return null;
        return safeParser.parseDouble(c, USE_LON);
    }
    
    /**
     * Parse date instance for the data point, either from line or from filename.
     * If unsuccessful, returns null and problem is logged into Parser.
     * The date instance will be converted into UTC-time.
     * If instructed to do so, then the date will be rounded down into full hours
     * (used for minute-data with averaging)
     * @param safeParser
     * @param splitline
     * @param line
     * @param p
     * @return 
     */
    private Dtime dt(Parser safeParser, String[] splitline, String line, RunParameters p) {
        String date;
        if (this.dateFromFile) {
            return this.fileDate;
        } else {
           date= safeParser.fetch(splitline, parsedIndex_dt); 
        }
        
        if (date==null)return null;
        
        if (!this.obs_dt_replacements.isEmpty()) {
            for (String[] rep:this.obs_dt_replacements) {
                date = date.replace(rep[0], rep[1]);
            }
            
        }
        Dtime dtc = safeParser.parseDt(date, line);
            if (dtc==null) return null;
        if (this.flatHourAdd!=0) dtc.addSeconds(3600*this.flatHourAdd);
            if (!p.withinLoadSpan(dtc)) return null;//not in span
            
            return dtc;
    }
    
     /**
     * Parse data point ID-string, either from line or from filename.
     * If unsuccessful, returns null and problem is logged into Parser.
     * @param safeParser
     * @param splitline
     * @return 
     */
    private String ID(Parser safeParser,Feed f,String[] splitline, double lat,double lon) {
        String ID=null;
        if (this.parsedIndex_ID<0) {//do nothing, left generic (activates if null)

        } else if (this.IdFromFilename) {
            ID = safeParser.fetch(this.splitName,parsedIndex_ID);
        } else {
            ID= safeParser.fetch(splitline,parsedIndex_ID);
        }
        
        if (ID!=null && this.IDcleanUp) {
            ID = ID.replace(",", "_");
            ID = ID.replace("(", "");
            ID = ID.replace(")", "");
            ID = ID.replace(" ", "");
        }
        
        if (this.additionToID!=null) ID = additionToID + ID;
        if (f.addCoordsToKnownID) ID+="_" +  f.coordinateAdditionToID(lat, lon);//in case the sensors CHANGE location.
        if (ID==null || ID.length() < 1 || ID.equals("null")) {
          ID=null;
        }
        return ID;
    }
    
     /**
     * Parse measurement height, either from line or from filename. If index
     * for this property is lower than zero, then default value of 3m is used.
     * If unsuccessful, returns null and problem is logged into Parser.
     * @param safeParser
     * @param splitline
     * @return 
     */
    private float measurementHeight(Parser safeParser,String[] splitline) {
        float height = 3f;
        if (this.parsedIndex_mheight>=0) {//instruction to parse this info from data exists.
            String[] cont = splitline;
            if (this.mHeightFromFilename) cont = splitName;
            String c = safeParser.fetch(cont,parsedIndex_mheight);
            if (c==null) return height;
            if (c.contains("m")) c = c.replace("m", "");
            Double d = safeParser.parseDouble(c, USE_MHEIGHT);
            if (d!=null) height = d.floatValue();
        }
        
        return height;
    }
    
     /**
     * Parse data point imprecision rating, either from line or from filename. If index
     * for this property is lower than zero, then default value (set in Feed) is used.
     * If unsuccessful, returns null and problem is logged into Parser.
     * @param safeParser
     * @param splitline
     * @return 
     */
    private int quality(Parser safeParser,String[] splitline, Feed f) {
        int qual = f.dataImprecisionRating;
        
        if (this.parsedIndex_quality>=0) {//instruction to parse this info from data exists.
            String[] cont = splitline;
            if (this.qualityFromFilename) cont = splitName;
            String c = safeParser.fetch(cont,parsedIndex_quality);
            if (c==null) return qual;
            if (c.contains("q")) c = c.replace("q", "");
            Double d = safeParser.parseDouble(c, USE_QUALITY);
            if (d!=null) qual= d.intValue();
        }
        
        return qual;
    }
    
    public void parseTextObservations(DataPack dp, Feed f,
            String line, HashMap<Integer,String> vars, Boundaries b, Parser safeParser,
            DataBase DB, MapPack mp) {
        
            //default approach
            String[] splitline =safeParser.safeSplit(";", 2, line);
            if (splitline==null) return;//parse fail
            
            Double lat = lat(safeParser,splitline,b);
            if (lat==null) return;//parse fail
            Double lon = lon(safeParser,splitline,b);
            if (lon==null) return;//parse fail
            //skip due to location?
            if (b != null && !b.intersectsOrContains(lat, lon)) {
                return;//not within domain
            }
            //TIME
            Dtime dtc = dt(safeParser,splitline,line,f.ops.getRunParameters());
            if (dtc==null) return;//parse fail
            
            float mheight =measurementHeight(safeParser,splitline);
            int qual = quality(safeParser,splitline,f);
            
            //ID
            DBline dbl=null;
            String ID = null;
            if (this.isPointData) {//DBlines are relevant for measurements. For custom feeds that read other data types this should be false and specialTextParser() should be overridden.
                ID=ID(safeParser,f,splitline,lat,lon);
                if (this.cachedDB!=null) {//special approach: find existing DBline based on nearby entries.
                    dbl = this.cachedDB.findClosest_cached(DB, f.searchRad_m, lat, lon);
                }
                if (dbl==null) dbl = DB.getStoreMeasurementSource(f,ID, ID, (byte)0,
                        lat, lon, qual, mheight);
                ID = dbl.ID;//now fully usable and unique
            }
            
            //READ THE ACTUAL VALUES==============
            //perhaps the feed has some unique method of adding Observations?
            boolean specialTreatment = f.specialTextParser(splitline,safeParser,dbl,dtc, dp,DB,mp);
            if(specialTreatment) return;//yes, there was a sepecial case. Done.
            
            if (this.multiTypeLine) {//read MANY Observations from this line!
                for (Integer colIndex :this.index_to_type.keySet()) {//for columns with values (known types)
                        String sval = safeParser.fetch(splitline, colIndex);
                        if (sval==null) continue;//OOB
                        Double value = safeParser.parseDouble(sval, USE_VALUE);
                        if (value==null) continue;//parse fail
                        VarConversion vc = this.index_to_type.get(colIndex);
                        int typeI =vc.typeInt;
                        
                        Observation fob = new Observation(typeI,
                                ID, (float) dbl.lat, (float) dbl.lon, dtc,
                                value.floatValue(), (float) dbl.height_m, filen);//not that we use dbl coordinates here (so these can be manually adjusted)
                        
                        this.addToPack(dp, fob, DB, vc, f);
                }//FOR colIndex
            }//IF MULTI-LINE
            
            else {//simple case (singular)
                String sval = safeParser.fetch(splitline, parsedIndex_value);
                if (sval==null) return;//OOB
                Double value = safeParser.parseDouble(sval, USE_VALUE);
                if (value==null) return;//parse fail

                String type = splitline[parsedIndex_type];
                 VarConversion vc = f.ops.VARA.allVC_byName.get(type);
                 if (vc==null) return;// not known type.
                 
                int typeI = vc.typeInt;
                if (!vars.containsKey(typeI)) return;//not to be loaded.
                
                Observation fob = new Observation(typeI,
                        ID, (float) dbl.lat, (float) dbl.lon, dtc,
                        value.floatValue(), (float) dbl.height_m, filen);//not that we use dbl coordinates here (so these can be manually adjusted)
                this.addToPack(dp, fob, DB, vc, f);
            }//if singular
    }
    
    public void addToPack(DataPack dp, Observation fob, DataBase DB, VarConversion vc, Feed f) {
        fob.setDataMaturity_h(unreliability);//for data replacements (the Observation with lower value lives on in Source's Layers.
        fob.sourceFile = filen;//keep tracking the source file for the Observation
        f.reformatParsedObservation(fob, vc);
        dp.addObservation(fob, f.ops, DB);
        additions++;
    }



    public void acceptAndRemoveTypeString(String _aqi) {
        this.acceptAndRemoveFromType = _aqi;
    }

    public int recentAdditions() {
        return this.additions;
    }

    public String currentFileName() {
        return this.filen;
    }



}
