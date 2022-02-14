/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.logging;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.GlobOptions;

/**
 *This class is used to funnel logging and error information.
 *Since this is a new feature (11/2020) there is a switch to roll back to
 * the old style of showing information, in which case system.out and printDebug()
 * are used.
 * 
 * Intended Level use-cases:
 * The SEVERE level should only be used when the application really is in trouble.
 * Users are being affected without having a way to work around the issue.
 * 
 * The WARNING level should be used when something bad happened, but the application
 * still has the chance to heal itself or the issue can wait a day or two to be fixed.
 * 
 * The INFO level should be used to document state changes in the application or
 * some entity within the application.
 * 
 * All of FINE, FINER, and FINEST are intended for relatively detailed tracing.
 * The exact meaning of the three levels will vary between subsystems, but in general,
 * FINEST should be used for the most voluminous detailed output, FINER for
 * somewhat less detailed output, and FINE for the lowest volume (and most important) messages.
 * 
 * @author johanssl
 */
public class EnfuserLogger {
    /**
     *If true then uses the traditional System.out treatment for messages
     * and exception stack traces.
     */
    public final static String OL_SUMMARY_FILE = "contentSummary.txt";
    public static boolean OLD_STYLE=true;
    private static final ConcurrentHashMap<String,ArrayList<String>> 
            REPORTS = new ConcurrentHashMap<>();

    public static void log(Exception e, Level l, Class c, String msg) {
        log(e, l, c, msg, System.out);
    }
    
    public static void log(Level l, Class c, String msg, PrintStream out) {
        log(null, l, c, msg, out);
    }
 
  public static void log(Level l, Class c, String msg) {
     log(null, l, c, msg, System.out);
 }

/**
 * For old style treatment of logging, check the message Level against
 * one set in GlobOptions. If message level is lower then this returns false
 * (no logging activities).
 * @param l
 * @return 
 */  
private static boolean loggable(Level l) {
    Level thresh = Level.INFO;
     
     if (GlobOptions.initDone()) {
         GlobOptions g = GlobOptions.get();
         thresh = g.getLoggingLevel();
     }
     
     if (thresh.intValue()<= l.intValue()) {
         return true;
     } else {
         return false;
     }
}  
 
/**
 * Handle logging of a message and possibly an Exception. The way how this
 * is done is affected by whether or not 'OLD_STYLE' (static boolean) is set
 * on or off. In case it's set as 'true' then the logging behaves a similar
 * fashion that previous model versions did.
 * @param e Exception, will be logged if non-null.
 * @param l Level for the message. Lower level messages can be omitted if lower
 * than set in GlobOptions.
 * @param c The class responsible for the logging activity
 * @param msg the message to be logged 
 * @param out custom printStream for the logging (TODO)
 */
 public static void log(Exception e, Level l, Class c, String msg, PrintStream out) {
     if (OLD_STYLE) {
         if (out==null) out = System.out;
         boolean log = loggable(l);//check level against settings
         if (log) {//level exceeds of equals set level for logging.

             if (l.intValue()>=Level.WARNING.intValue()) {//warning or severe => to err in 'old-style'
                 if (l == Level.WARNING) msg = "WARNING: "+msg;
                 if (l == Level.SEVERE) msg = "SEVERE: "+msg;
                  
                 if (out == System.out) {
                     out = System.err;
                 }
             }
             //print it
             out.println(msg);

             if (e!=null) {//an Exception to log
                 e.printStackTrace(out);
             }
         }
         
     } else {
         //TODO printstream not taken into account
         java.util.logging.Logger.getLogger(
                 c.getName())
                 .log(l, msg, e);
     }
 } 

    public static void sleep(int ms, Class c) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            log(ex,Level.SEVERE,c,"Can't get no sleep!");
        }
    }

    public static void logReport(String name, String topic,
            ArrayList<String> lines) {
        String key = name +"_"+topic;
        REPORTS.put(key, lines);
    }

    public static String getReport(String name, String topic) {
       String key = name +"_"+topic;
       ArrayList<String> arr = REPORTS.get(key);
       if (arr==null) return null;
       
       String s ="";
       for (String line:arr) {
           s+=line+"\n";
       }
       
       return s;
    }

    public static ArrayList<String> getOsmLayerCreationReport(String name) {
       String topic = OL_SUMMARY_FILE;
       String key = name +"_"+topic;
       ArrayList<String> arr = REPORTS.get(key);
       return arr;  
    }

    public static void fineLog(Class c, String string) {
        EnfuserLogger.log(Level.FINE, c, string);
    }

    public static void log(Level INFO, String string) {
       log(INFO,EnfuserLogger.class,string);
    }
  
  

  
}
