/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.logging;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.enfuser.ftools.FileOps;

/**
 * The purpose fo this class is to safely parse data from text.
 * This should also take care of logging action in case irregularities
 * int the data are found and the parsing is unsuccessful.
 * @author johanssl
 */
public class Parser {

 
    public String fname;
    public int filesRead =1;
    
    ArrayList<String> splitFails;
    int splitSuccs=0;
    ArrayList<String> dateFails;
     int dateSuccs=0;
    ArrayList<String> numberFails;
     int numberSuccs=0;
     String fileReadFail=null;
   public Parser(String fname) {
       this.fname =fname;
   }
   public final static double TO_INFO_FRAC = 0.01;
   /**
    * Describe the parsing outcome for this text file.
    * @param c if non-null then a warning message will be logged in case
    * parsing exceptions occurred.
    * 
    * In case the cases of fails is very low, then the mild warning will be
    * printed out to general logging file.
     * @param out
    * @return warning message, if exceptions occurred.
    */
   public String getExceptionPrintout(Class c, PrintStream out) {
       
       if (fileReadFail==null && splitFails==null 
               && dateFails==null && numberFails==null) {
           return null;
       }
       Level severity = Level.INFO;
       String msg = "Parser exceptions, currentFile="+fname +", files read="
               +this.filesRead;
       if(c!=null) msg+=  ", class="+c.getName();
       
       if (fileReadFail!=null) msg+=", "+fileReadFail;
       
       if (splitFails !=null){
           msg+= ", splitFails="+splitFails.size() +", success="+this.splitSuccs 
                   +", example="+splitFails.get(0);
           //severity check
           double tot = +splitFails.size()+this.splitSuccs;
           double frac = (double)(splitFails.size())/tot;
           if (frac > TO_INFO_FRAC) severity = Level.WARNING;
       }
       if (dateFails!=null){
           msg+= ", dateFails="+dateFails.size() +", success="+this.dateSuccs 
                   +", example="+dateFails.get(0);
           //severity check
           double tot = +dateFails.size()+this.dateSuccs;
           double frac = (double)(dateFails.size())/tot;
           if (frac > TO_INFO_FRAC) severity = Level.WARNING;
       }
       if (numberFails!=null){
           msg+= ", numberFails="+numberFails.size() +", success="+this.numberSuccs 
                   +", example="+numberFails.get(0);
           
            //severity check
           double tot = +numberFails.size()+this.numberSuccs;
           double frac = (double)(numberFails.size())/tot;
           if (frac > TO_INFO_FRAC) severity = Level.WARNING;
       }
       
       if (c!=null) {
            EnfuserLogger.log(severity, c, msg,out);
       }
      return msg; 
   }
   
   /**
    * Parse boundaries from String[], assuming default order of doubles:
    * latmin,latmax,lonmin,lonmax.
    * @param split
    * @param startIndex start reading the doubles starting from this index.
    * @return 
    */
   public Boundaries parseBounds(String[] split, int startIndex) {
        try {
            
           Boundaries b = new Boundaries(split,startIndex);
           if (b.consistent()) {
               return b;
           }
           
        }catch (Exception e) {
            
            String sval ="";
            if (split!=null) {
                for (String s:split) {
                    sval+=s+",";
                }
            }
            
            if (numberFails==null) numberFails=new ArrayList<>();
            numberFails.add(sval +", Boundaries");
           
        }
        return null;
   }
   
    public String getExceptionPrintout(Class c) {
       return Parser.this.getExceptionPrintout(c, null);
    }
   
      public final static String TZ_SEPARATOR =",tz:";
      public Dtime parseDt(String date, String line) {

        try {
            int hourAdd =0;
            boolean utc =false;
           if (date.endsWith("Z")) {
               date=date.replace("Z", "");
               utc=true;
           }
           
           if (!date.contains(TZ_SEPARATOR) && date.endsWith("+0:00")) {
               date=date.replace("+0:00", "");
               utc=true;
           }
           
           if (!utc && date.contains(TZ_SEPARATOR)) {
               //e.g., 2020-01-01T00:00:00,tz:+08:00
               String[] sp = date.split(TZ_SEPARATOR);
               date = sp[0];//e.g., 2020-01-01T00:00:00
               String tz = sp[1];//+08:00
               tz = tz.split(":")[0];
               if (tz.contains("+"))tz =tz.replace("+", "");
               Double test =Double.valueOf(tz);
               hourAdd = test.intValue()*-1;
           }
           
           Dtime dt= new Dtime(date);
           if (hourAdd!=0) dt.addSeconds(3600*hourAdd);
           
           dateSuccs++;
           return dt;
        }catch (Exception e) {
            if (dateFails==null) dateFails=new ArrayList<>();
           dateFails.add(date +", "+line);
           
            return null;
        }
    } 
      
      
   
       public String[] safeSplit( String splitter, int lengthMustBe, String line) {
        String[] split = line.split(splitter);
        if (split.length<lengthMustBe) {
            String msg= line+", len_target:"+lengthMustBe +", len_actual:"+split.length;
            if (splitFails==null) splitFails= new ArrayList<>();
            splitFails.add(msg);
            return null;

        } else {
            splitSuccs++;
            return split;
        }
    }
    
     
     /**
      * Safely parse a Double value instance from the splitted String from
      * given index
     * @param sval
     * @param intention for exceptions, describe the intended property (e.g., latitude)
      * @return 
      */  
     public Double parseDouble(String sval, String intention) {
        
        try {
           Double d = Double.valueOf(sval);
           numberSuccs++;
           return d;
        }catch (NumberFormatException e) {
            
            //there are some cases where error messaging is not needed.
            if (sval==null ||sval.length()==0 || sval.equals("null")) return null;
            
            if (numberFails==null) numberFails=new ArrayList<>();
            numberFails.add(sval +", intension="+intention);
            return null;
        }
    }     
      
    public Integer parseInt(String sval, String intention) {
       try {
           Integer d = Integer.valueOf(sval.replace(" ", ""));
           numberSuccs++;
           return d;
        }catch (NumberFormatException | NullPointerException e) {
            if (numberFails==null) numberFails=new ArrayList<>();
            numberFails.add(sval +", "+intention);
            return null;
        }
    }


    public ArrayList<String> readStringFile(File file) {
        try {
            ArrayList<String> arr = FileOps.readStringArrayFromFile_unsafe(file);
            return arr;
        } catch (Exception e) {
            this.fileReadFail = file.getAbsolutePath() +" read error, " + e.getMessage();
            return new ArrayList<>();
        }
    }

    public void logSplitLengthException(String[] vals, int len) {
            String line ="";
            for (String s:vals) {
                line+=s +", ";
            }
            String msg= line+", len_target:"+len +", len_actual:"+vals.length;
            if (splitFails!=null) splitFails= new ArrayList<>();
            splitFails.add(msg);
    }

    public Integer parseEnfuserType(String type, VarAssociations var) {
       int typeInt = var.getTypeInt(type);
       if (typeInt<0) {
           if (numberFails==null) numberFails=new ArrayList<>();
            numberFails.add(type +", fusion typeInt parsing.");
           return null;
       }
       return typeInt;
    }
    
    /**
     * Transforms the variable type String into integer index and back
     * to native type name for the model.
     * @param type the parsed type String
     * @param VARA
     * @return native variable name, or null (not identified).
     */
    public String getNativeVariableType(String type, VarAssociations VARA) {
        
      int typeint = VARA.getTypeInt(type);
      try {
          
        if (typeint!=-1) {//known type
           String ntype = VARA.VAR_NAME(typeint);
           return ntype;
        }
      } catch (Exception e) {
           if (numberFails==null) numberFails=new ArrayList<>();
            numberFails.add(type +", fusion typeInt parsing.");
        }
      return null;
    }

    public void logDateParseException(String name) {
       if (dateFails==null) dateFails=new ArrayList<>();
           dateFails.add(name);
    }

    public String fetch(String[] cont, int ind) {
        if (cont.length<=ind) {
            String msg= "len_target:"+ind+1 +", len_actual:"+cont.length;
            if (splitFails!=null) splitFails= new ArrayList<>();
            splitFails.add(msg);
            return null;
        }
        return cont[ind];
    }

    public void switchFile(String filen) {
       this.fname = filen;
       this.filesRead++;
       
    }
}
