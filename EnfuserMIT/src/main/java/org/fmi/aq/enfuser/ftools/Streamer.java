/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

/**
 * This class is for the forking and streaming of console output and errors to a
 * collection of logging files as well as Swing textAreas.
 *
 * @author johanssl
 */
public class Streamer extends OutputStream {

    private static Streamer str_o;
    private static Streamer str_err;

    /**
     * Duplicate all console feedback and errors to log files.
     *
     * @param dir directory where the log files will be created at
     * @param desc a text description for the log file ".e., mapTask1 or
     * areaRun_helsinki".
     */
    public static void setDefaultConsoleLogging_toFile(String dir, String desc) {
        resetDefaultConsoleLogging();
        if (str_o == null) {
            str_o = new Streamer("sout",  System.out, new ArrayList<>());
        }

        if (str_err == null) {
            str_err = new Streamer("ERROR",  System.err, new ArrayList<>());
        }

        //streamers
        File f = new File(dir + "logs" + Z);
        if (!f.exists()) {
            f.mkdirs();
        }

        String timestamp = Dtime.getSystemDate().getStringDate_YYYY_MM_DDTHH();
        //reset output streams
        String slog = "logs/sout_" + desc + timestamp + "Z.txt";
        String elog = "logs/errOut_" + desc + timestamp + "Z.txt";

        ArrayList<String> soutFiles = new ArrayList<>();
        soutFiles.add(dir + slog);

        ArrayList<String> errFiles = new ArrayList<>();
        errFiles.add(dir + elog);

        str_o.resetFileStream(soutFiles, true);
        str_err.resetFileStream(errFiles, true);

        System.setOut(new PrintStream(str_o));
        System.setErr(new PrintStream(str_err));

    }

    /**
     * Remove all file logs that the default streamers are attached to. If the
     * default streamers are null, then this has no effect.
     */
    public static void resetDefaultConsoleLogging() {
        if (str_o != null) {
            str_o.resetFileStream(new ArrayList<>(), true);
        }

        if (str_err != null) {
            str_err.resetFileStream(new ArrayList<>(), true);
        }

    }

    private StringBuilder buffer;
    private String prefix;
    private PrintStream old;

    public ArrayList<PrintStream> fileStreams = new ArrayList<>();
    public ArrayList<String> files = new ArrayList<>();
    public Streamer(String prefix, PrintStream old, ArrayList<String> fileStream_names) {
        this.prefix = prefix;
        buffer = new StringBuilder(128);
        buffer.append("[").append(prefix).append("] ");
        this.old = old;

        for (String fileStream_name : fileStream_names) {
            try {
                PrintStream fileStream = new PrintStream(new BufferedOutputStream(
                        new FileOutputStream(fileStream_name)), true);
                fileStreams.add(fileStream);
                files.add(fileStream_name);
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Update the list of file logs where console feedback is directed to. This
     * will first close all current file streams and then open new ones.
     *
     * @param fileStream_names a list of files names (full absolute path) to new
     * log files.
     * @param append if true, then an existing log file will be appended to, and
     * will not be overwritten.
     */
    public void resetFileStream(ArrayList<String> fileStream_names, boolean append) {
        for (PrintStream fileStream : fileStreams) {
            if (fileStream != null) {
                fileStream.close();
            }
        }

        fileStreams.clear();
        this.files.clear();
        for (String fileStream_name : fileStream_names) {
            try {
                PrintStream fileStream = new PrintStream(new BufferedOutputStream(
                        new FileOutputStream(new File(fileStream_name), append)), true);
                fileStreams.add(fileStream);
                files.add(fileStream_name);
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            }
        }

    }

    @Override
    public void write(int b) throws IOException {
        char c = (char) b;
        String value = Character.toString(c);
        buffer.append(value);
        if (value.equals("\n") ) {

            buffer.delete(0, buffer.length());
            buffer.append("[").append(prefix).append("] ");
        }
        old.print(c);

        for (PrintStream fileStream : fileStreams) {
            if (fileStream != null) {
                fileStream.print(c);
            }
        }

    }
}
