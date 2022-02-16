/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.setup;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.ftools.FuserTools.findFileThatContains;
import org.fmi.aq.enfuser.ftools.Zipper;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;

/**
 * This class provides static methods to modify region file content after it has
 * been created. Main use case if for Feed argument edits and in essence are
 * String replace operations with pattern matching.
 *
 * @author johanssl
 */
public class RegionPostProcessing {

    
  File absoluteTarget;
  HashMap<String,ArrayList<String[]>> file_replacements = new HashMap<>();  
  FusionOptions ops;

  public final static String POSTPROC_NAME ="regionEdits.csv";
  public final static String ERCF ="ercf";

  
  public final static int TARGET_IND =0;
  public final static int MUST_CONTAIN_IND =1;
  public final static int REPLACE_THIS_IND =2;
  public final static int REPLACE_WITH_IND =3;
  
  public RegionPostProcessing(String dir, FusionOptions ops) {
      this.ops = ops;
      File f = new File(dir + POSTPROC_NAME);
      if (f.exists()) {
          ArrayList<String> arr = FileOps.readStringArrayFromFile(f);
          int k =-1;
          for (String line:arr) {
              k++;
              String[] split = line.split(";");
              if (split.length<REPLACE_WITH_IND+1) {
                  if (k!=0) {//not header, warn
                      EnfuserLogger.log(Level.WARNING, this.getClass(), "This replacement "
                          + "instruction has less than 4 elements: "+line +", at "+f.getAbsolutePath());
                  }
                  continue;
              }
              
              String targetKey = split[TARGET_IND].toLowerCase();
              if (targetKey.contains("file_contains")) continue;
              
              File target = findConfigurationFile(targetKey, ops);
              if (target!=null) {
                  ArrayList<String[]> content = this.file_replacements.get(target.getAbsolutePath());
                  if (content ==null) {
                      content = new ArrayList<>();
                      content.add(split);
                      this.file_replacements.put(target.getAbsolutePath(), content);
                  } else {
                      content.add(split);
                  }
              }//if exists
              else {
                  EnfuserLogger.log(Level.WARNING, this.getClass(), 
                          "RegionPostProcessing: Could not locate the target "
                                  + "file with instructions: "+targetKey);
              }
          }//for lines
      }//if instructions file exitst
  }
  
public void apply() {
    
    for (String name:file_replacements.keySet()) {
        System.out.println("Configuration file edit: "+ name);
        ArrayList<String[]> arr = this.file_replacements.get(name);
        System.out.println("\thas "+ arr.size() +" elements.");
        ArrayList<String> original = FileOps.readStringArrayFromFile(name);
        System.out.println("\tOriginal content loaded with "+ original.size() +" lines.");
        
        ArrayList<String> modified = new ArrayList<>();
        boolean modded = false;
        for (String line:original) {
            String mline = line +"";
            //do modifications
            for (String[] elem:arr) {
                try {
                    String mustHave = elem[MUST_CONTAIN_IND];
                    if (mustHave !=null && mustHave.length()>1 && !line.contains(mustHave)) {
                    //the line must have this to be modified, but it's not present
                       continue;
                    } else if (mustHave !=null && mustHave.length()>1) {
                        System.out.println("\t\t found the line for '"+mustHave+"'");
                    }

                    String replace = elem[REPLACE_THIS_IND];
                    String replacement = elem[REPLACE_WITH_IND];
                    if (mline.contains(replace)) {
                        mline = mline.replace(replace, replacement);
                        EnfuserLogger.log(Level.INFO,this.getClass(),
                                "\t edit "+name+": "+ replace +" => "+replacement);
                        modded =true;
                    } 
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }//for element
            modified.add(mline);
        }//for original lines
        
        if (modded) {
           EnfuserLogger.log(Level.INFO,RegionPostProcessing.class,
                    "Modifications done, writing a new file...");
            boolean append = false;
            listToFile(name, modified, append); 
        }
        
    }//for file types
}  

private static File findConfigurationFile(String key, FusionOptions ops) {
    GlobOptions g = GlobOptions.get();
     key = key.replace(" ", "");
    boolean regional = key.contains("regional");
    if (regional) {
        key = key.replace("regional", "");
    }
    if (key.contains(ERCF) || key.contains("region_")) {
        
        String nameSearch = "region_"+ops.getRegionName();
        String dir = g.dataDirCommon() +"Regions"+FileOps.Z;
        return  findFileThatContains(dir, new String[]{nameSearch,".csv"});
        
    } else  {
        
      String dir;
      if (regional) {
         dir = g.regionalDataDir(ops.getRegionName());
      } else {
         dir = g.dataDirCommon(); 
      }
      
      return  findFileThatContains(dir,key.split(",") ); 
    }

}   
    
    
    /**
     * Dump an array of lines (String) to file.
     *
     * @param name full path for the file to be created
     * @param lines array of lines
     * @param append true if content is added to file, false if existing content
     * is to be replaced.
     */
    public static void listToFile(String name, ArrayList<String> lines, boolean append) {
        String n = System.getProperty("line.separator");

        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(name, append));

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                out.write(line + n);
            }

            out.close();
        } catch (IOException e) {
        }

    }

    public final static int MOD_ADD = 0;
    public final static int MOD_REPLACE = 1;//can also work as remove operation

    /**
     * Modify text file content.
     *
     * @param filename full filename to be modified
     * @param keyID String identifier that causes modifications to occur. This
     * is assessed for each line separately.
     * @param modType type of modification to be done (add or replace)
     * @param mod added text or replacement text
     * @param mod2 for replacement operation, this is the String that is
     * replaced with another (mod).
     */
    public static void modifyArgument(String filename, String keyID, int modType,
            String mod, String mod2) {

        //get existing content
        ArrayList<String> arr = FileOps.readStringArrayFromFile(filename);
        ArrayList<String> newArr = new ArrayList<>();
        int mods = 0;
        EnfuserLogger.log(Level.FINER,RegionPostProcessing.class,
                "Argument modification: finding a line with '" + keyID + "'");
        for (String line : arr) {
            if (line.contains(keyID)) {//mod will target this line
                EnfuserLogger.log(Level.FINER,RegionPostProcessing.class,
                        "\t found the line.");
                if (modType == MOD_ADD) {
                    line += mod;
                    EnfuserLogger.log(Level.FINER,RegionPostProcessing.class,
                            "Add operation => " + mod);
                    mods++;
                } else {
                    EnfuserLogger.log(Level.FINER,RegionPostProcessing.class,
                            "Replace operation for: " + line);
                    line = line.replace(mod, mod2);
                    EnfuserLogger.log(Level.FINER,RegionPostProcessing.class,
                            "" + mod + " = > " + mod2);
                    EnfuserLogger.log(Level.FINER,RegionPostProcessing.class,
                            "Line is now: " + line);
                    mods++;

                }

            }//if hit

            newArr.add(line);//add without mods
        }

        if (mods > 0) {
            EnfuserLogger.log(Level.FINER,RegionPostProcessing.class,
                    "Modifications done, writing a new file...");
            boolean append = false;
            listToFile(filename, newArr, append);
        }
    }
  
  /**
   * This method unzip specifically named zipped resource files for a given
   * modelling region. The following types of zipped resources are identified:
   * - Emitters.zip - if a file ending like this exists then the content
   * will be unzipped to the emitter directory for the region
   * - WindProfiles.zip - FMI addon files for wind profiling
   * - Archives.zip - if a file ending like this exists then the content
   * will be unzipped to EnfuserArchive for the region as dynamic input data.
   * - Config.zip - if a file ending like this exists then the content
   * will be unzipped to the regional static configuration directory
   * @param ops options for the specific region (for paths)
   * @param sourceDir directory holding the resource files. Commonly this would
   * be the 'regional setup directory'
   */  
  public static void unzipPostSetup(FusionOptions ops, String sourceDir) {
      GlobOptions g = GlobOptions.get();

      File f = new File(sourceDir);
      if (!f.exists()) return;
      
      File[] ff = f.listFiles();
      if (ff==null) return;
      
      for (File test:ff) {
          try {
              String nam = test.getName();
          if (nam.contains("Emitters")
                  && nam.endsWith(".zip")) {//emitter copy
              
              String emitterDir = ops.getEmitterDir();
              EnfuserLogger.log(Level.INFO, RegionPostProcessing.class,
                      "emitter unzip: "+ test.getAbsolutePath());
              
              ArrayList<String> arr = Zipper.UnZip(test.getAbsolutePath(), emitterDir);
                for (String unzipped : arr) {
                    EnfuserLogger.log(Level.FINER,Setup.class,"\t adding " + unzipped);
                }
          }//if region name and a zip
          
          else if (nam.contains("indProfiles")
                  && nam.endsWith(".zip")) {//wind profile copy (FMI only)
              
              String mapDir = g.regionalDataDir(ops.getRegionName()) +"maps"+FileOps.Z;
              EnfuserLogger.log(Level.INFO, RegionPostProcessing.class,
                      "Unzip (windProfiles): "+ test.getAbsolutePath());
              
              ArrayList<String> arr = Zipper.UnZip(test.getAbsolutePath(), mapDir);
                for (String unzipped : arr) {
                    EnfuserLogger.log(Level.FINER,Setup.class,"\t adding " + unzipped);
                }
              
          } else if (nam.contains("Config")
                  && nam.endsWith(".zip")) { //to configuration directory
              
             String conDir = g.regionalDataDir(ops.getRegionName());
              EnfuserLogger.log(Level.INFO, RegionPostProcessing.class,
                      "Unzip (config): "+ test.getAbsolutePath());
              
              ArrayList<String> arr = Zipper.UnZip(test.getAbsolutePath(), conDir);
                for (String unzipped : arr) {
                    EnfuserLogger.log(Level.FINER,Setup.class,"\t adding " + unzipped);
                }
              
          } else if (nam.contains("toArchives")
                  && nam.endsWith(".zip")) { //to EnfuserArchive under the region name
              
              String archDir = g.enfuserArchiveDir(ops.getRegionName());
              EnfuserLogger.log(Level.INFO, RegionPostProcessing.class,
                      "Unzip (archives): "+ test.getAbsolutePath());
              
              ArrayList<String> arr = Zipper.UnZip(test.getAbsolutePath(), archDir);
                for (String unzipped : arr) {
                    EnfuserLogger.log(Level.FINER,Setup.class,"\t adding " + unzipped);
                }
          }
          
          
          } catch (Exception e) {
              e.printStackTrace();
          }
      }//for files at emitterSetup
     
      //finally, make text edits based on regionEdits.csv.
      try {
          RegionPostProcessing rpp = new RegionPostProcessing(sourceDir,ops);
          rpp.apply();
          
      }catch (Exception e) {
          EnfuserLogger.log(e, Level.WARNING, RegionPostProcessing.class,
                  "text edits failed at Setup post-processing for "+ ops.getRegionName());
      }
      
  }  
    

}
