/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

/**
 *
 * @author johanssl
 */
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;
import java.io.*;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.GlobOptions;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;


public class FileOps {


    public static void printOutALtoFile2(File f, ArrayList<String> lines, boolean append) {
        printOutALtoFile2(f.getParent() + Z, lines, f.getName(), append);
    }

    /**
     * This method prints recieved messages hourly counters to message.csv file
     *
     * @param resultsDir the directory
     * @param lines list of lines to be printed to file
     * @param name local file name without parents
     * @param append if true, lines will be appended to an existing file, if
     * available.
     */
    public static void printOutALtoFile2(String resultsDir, ArrayList<String> lines,
            String name, boolean append) {
        String n = System.getProperty("line.separator");

        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(resultsDir + name, append));

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                out.write(line + n);
            }

            out.close();
        } catch (IOException e) {
        }

    }

    public static void deleteFile(String fileName) {
        // A File object to represent the filename
        boolean doIt = true;
        File f = new File(fileName);

        // Make sure the file or directory exists and isn't write protected
        if (!f.exists()) {
            doIt = false;
            EnfuserLogger.log(Level.WARNING,FileOps.class,
                  "Delete: no such file or directory: " + fileName);
        }

        if (!f.canWrite()) {
            doIt = false;
            EnfuserLogger.log(Level.WARNING,FileOps.class,
                  "Delete: write protected: " + fileName);
        }

        // Attempt to delete it
        boolean success = false;
        if (doIt) {
            success = f.delete();
        }

        if (!success) {
            throw new IllegalArgumentException("Delete: deletion failed");
        }
    }

    public static final void unzip(String file, String rootdir) {
        Enumeration entries;
        ZipFile zipFile;

        try {
            zipFile = new ZipFile(file);
            entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                if (entry.isDirectory()) {
// Assume directories are stored parents first then children.
                    EnfuserLogger.log(Level.FINE,FileOps.class,
                  "Extracting directory: " + entry.getName());
// This is not robust, just for demonstration purposes.
                    (new File(entry.getName())).mkdir();
                    continue;
                }
                EnfuserLogger.log(Level.FINE,FileOps.class,
                  "Extracting file: " + entry.getName());

                String outFile = rootdir + entry.getName();
                OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile));
                InputStream in = zipFile.getInputStream(entry);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, len);
                }
                in.close();
                out.close();

            }
            zipFile.close();
        } catch (IOException ioe) {
            EnfuserLogger.log(ioe,Level.WARNING,FileOps.class,
                  "Unzip failed with "+ file +" to "+ rootdir);
        }
    }

//This method prints recieved messages hourly counters to message.csv file
    public static void printOutStringToFile(String resultsDir, String line, String name) {
        String n = System.getProperty("line.separator");
        boolean append;
        append = false;

        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(resultsDir + name, append));

            out.write(line);
            out.close();
        } catch (IOException e) {
        }

    }

    public static void lines_to_file(String filename, String linesToWrite,
            boolean appendToFile) {

        PrintWriter pw = null;

        try {

            if (appendToFile) {

                //If the file already exists, start writing at the end of it.
                pw = new PrintWriter(new FileWriter(filename, true));

            } else {

                pw = new PrintWriter(new FileWriter(filename));

            }

            pw.println(linesToWrite);
            pw.flush();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //Close the PrintWriter
            if (pw != null) {
                pw.close();
            }

        }

    }

    /**
     * Read a text file into ArrayList, one line per element.
     *
     * @param file full file name (File.absolutePath())
     * @return an arrayList of lines.
     */
    public static ArrayList<String> readStringArrayFromFile(String file) {
        return readStringArrayFromFile(file, false);
    }

    /**
     * Read a text file into ArrayList, one line per element.
     *
     * @param file full file name (File.absolutePath())
     * @param notify true if console printout about read amount of lines.
     * @return an arrayList of lines.
     */
    public static ArrayList<String> readStringArrayFromFile(String file, boolean notify) {
        ArrayList<String> temp = new ArrayList<>();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                temp.add(line.trim());
            }
            br.close();
        } catch (FileNotFoundException e) {
            EnfuserLogger.log(Level.FINER,FileOps.class,"file: file NOT found!");

            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        //  String[] returnable = new String[temp.size()];
        //  temp.toArray(returnable);
        if (notify) {
            EnfuserLogger.log(Level.FINER,FileOps.class,"Read " + temp.size() + " lines from " + file);
        }
        return temp;
    }
    
        public static ArrayList<String> readStringArrayFromFile_unsafe(File file) throws Exception {
        ArrayList<String> temp = new ArrayList<>();

            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                temp.add(line.trim());
            }
            br.close();
       
        return temp;
    }

    public final static String ISO = "ISO-8859-1";

    public static ArrayList<String> readStringArrayFromFile_ISO(String file) {
        ArrayList<String> temp = new ArrayList<>();

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), ISO));
            String line;

            while ((line = br.readLine()) != null) {
                temp.add(line.trim());
            }
            br.close();
        } catch (FileNotFoundException e) {
            EnfuserLogger.log(Level.FINER,FileOps.class,"file: file NOT found!");

            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return temp;
    }
    
        public static ArrayList<String> readStringArrayFromFile_Nlines(String file, int N) {
        ArrayList<String> temp = new ArrayList<>();

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), ISO));
            String line;

            while ((line = br.readLine()) != null) {
                temp.add(line.trim());
                if (temp.size()>N) break;
            }
            br.close();
        } catch (FileNotFoundException e) {
            EnfuserLogger.log(Level.FINER,FileOps.class,"file: file NOT found!");

            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return temp;
    }

    /**
     * Read a text file into ArrayList, one line per element.
     *
     * @param file full file name (File.absolutePath())
     * @param MAX_LINES limit the maximum amount of read line to this.
     * @return an arrayList of lines.
     */
    public static ArrayList<String> readStringArrayFromFile(String file, int MAX_LINES) {
        ArrayList<String> temp = new ArrayList<>();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            int n = 0;
            while ((line = br.readLine()) != null && n < MAX_LINES) {
                temp.add(line.trim());
                n++;
            }
            br.close();
        } catch (FileNotFoundException e) {
            EnfuserLogger.log(Level.FINER,FileOps.class,"file: file NOT found!");

            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        //  String[] returnable = new String[temp.size()];
        //  temp.toArray(returnable);
        EnfuserLogger.log(Level.FINER,FileOps.class,"Read " + temp.size() + " lines from " + file);
        return temp;
    }

    public static ArrayList<String> readStringArrayFromFile(File file) {
        return readStringArrayFromFile(file.getAbsolutePath());
    }
    
  
  public static void main(String[] args) {
      diskSpace_IO(215);
  }  
    
  private final static float BIL = 1000*1000*1000;
  /**
   * Check current disk space at IO-root and issue a warning if
   * the amount if lower than the given threshold.
   * @param mustBeFree_GB Warning threshold in gigabytes.
   */
  public static void diskSpace_IO(double mustBeFree_GB) {  

      ArrayList<Path> paths = new ArrayList<>();
      GlobOptions g = GlobOptions.get();
      File f = new File(g.defaultIOroot());
      paths.add(f.toPath());
      
        for (Path root:paths) {

            String line =root+": ";
            try {
                FileStore store = Files.getFileStore(root);
                double available = editPrecision(store.getUsableSpace()/BIL,2);
                double total = editPrecision(store.getTotalSpace()/BIL,2);
                
                line+=("available (GB) =" + available
                                    + ", total (GB)=" + total);
                
                EnfuserLogger.log(Level.INFO,FileOps.class,line);
                
                if (available < mustBeFree_GB) {
                    EnfuserLogger.log(Level.WARNING,FileOps.class,
                        "Disk space at IO-root is running Low! (is lower than " + mustBeFree_GB+")");
                }
                
            } catch (IOException e) {
                EnfuserLogger.log(Level.WARNING,FileOps.class,
                        "Error querying space for "+root.toString());
            }
        }
  }

    /**
     * Read a simple text file where there are lines in format 'key=value'
     * into a hash.In case a line does not contain '=' it will be omitted.
     * @param f text file 
     * @param removeWhiteSpace if true, then white spaces will be removed from lines. 
     * @param lowerCaseKeys if true, then keys will be lowerCase characters only.
     * @return parsed key-value String pairs in hash.
     */
    public static HashMap<String, String> readKeyValueHash(File f,
            boolean removeWhiteSpace, boolean lowerCaseKeys) {
        
      HashMap<String, String> keyVals = new HashMap<>();
      ArrayList<String> arr = FileOps.readStringArrayFromFile(f);
            for (String s : arr) {
                try {
                    if (removeWhiteSpace) s = s.replace(" ", "");
                    if (!s.contains("=")) continue;
                    String[] split = s.split("=");
                    String key = split[0];
                    if (lowerCaseKeys) key = key.toLowerCase();
                    String value = split[1];
                    keyVals.put(key,value);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
        }//for lines
       return keyVals;     
    }

    public static ArrayList<File> getFilesEndingWith(String sourceDir, String[] ends) {
       ArrayList<File> files = new ArrayList<>();
       File f = new File(sourceDir);
       File[] ff = f.listFiles();
       if (ff==null) return files;
       
       for (File test:ff) {
           String name = test.getName().toLowerCase();
           for (String end:ends) {
               if (name.endsWith(end)) {
                   files.add(test);
                   EnfuserLogger.log(Level.INFO, FileOps.class,
                           "Adding file: "+ test.getAbsolutePath());
                   break;
               }//if ends with
           }//for endings
       }//for files
        
      return files;  
    }
}//CLASS END
