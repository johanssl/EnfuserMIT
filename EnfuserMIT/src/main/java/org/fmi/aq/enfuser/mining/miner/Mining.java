/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.miner;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.fmi.aq.interfacing.GraphicsMod;

/**
 *
 * @author johanssl
 */
public class Mining {

    private static SSLSocketFactory sslSocketFactory = null;

    public static boolean checkContains_NonZero(String file, String ID, PrintStream out) {
        out.println(ID + " scheduler: checking existance of " + file);
        File f = new File(file);
        if (f.exists()) {
            out.println("\t yep. it does exist...");

            if (f.length() > 0) {
                out.println("\t...and the size of the file is non-zero.");
                return true;
            } else {
                out.println("\t...but the size is 0 bytes.");
            }
        } else {
            out.println("\t The file does not exist.");
        }

        return false;

    }

    /**
     * Overrides the SSL TrustManager and HostnameVerifier to allow all certs
     * and hostnames. WARNING: This should only be used for testing, or in a
     * "safe" (i.e. firewalled) environment.
     *
     * @param connection the connection
     * @throws NoSuchAlgorithmException exception, SSL
     * @throws KeyManagementException exception SSL
     */
    public static void setAcceptAllVerifier(HttpsURLConnection connection)
            throws NoSuchAlgorithmException, KeyManagementException {

        // Create the socket factory.
        // Reusing the same socket factory allows sockets to be
        // reused, supporting persistent connections.
        if (null == sslSocketFactory) {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, ALL_TRUSTING_TRUST_MANAGER, new java.security.SecureRandom());
            sslSocketFactory = sc.getSocketFactory();
        }

        connection.setSSLSocketFactory(sslSocketFactory);

        // Since we may be using a cert with a different name, we need to ignore
        // the hostname as well.
        connection.setHostnameVerifier(ALL_TRUSTING_HOSTNAME_VERIFIER);
    }

    private static final TrustManager[] ALL_TRUSTING_TRUST_MANAGER = new TrustManager[]{
        new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }
    };

    private static final HostnameVerifier ALL_TRUSTING_HOSTNAME_VERIFIER = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    public final static int DOWNL_FAIL = 0;
    public final static int DOWNL_EXISTED = 1;
    public final static int DOWNL_SUCCESS = 2;

    /**
     * Establish a connection and download a file from the source in case the
     * file does not already exist (non-zero).
     *
     * @param localFileName the filename for download, including full path
     * @param simple if true connection is established in a simple way that
     * works for most datasources
     * @param certBypass disable SSL auth, which maybe required for Vaisala
     * servers etc, which do not have proper certificates.
     * @param address the url-address.
     * @param prout custom printstream for logging information (for Feeds this
     * can be a file stream)
     * @param usrPass user, password for simpleAuthentication for https. can be
     * null if not needed.
     * @param sizeEstimateMegs estimate for the file size, which is used for
     * progressBar
     * @param jbar if null, the progress of the download can be updated with
     * this progressBar.
     * @param forceRefresh set true to bypass the check for already existing
     * data with the exact same name.
     * @return boolean value that signals if the download was successful or the
     * file existed already.
     * @throws Exception something went wrong, most probably with the connection
     * (e.g., proxy settings)
     */
    public static int downloadFromURL_static(String localFileName, boolean simple,
            boolean certBypass, String address, PrintStream prout, String[] usrPass,
            Float sizeEstimateMegs, Object jbar, boolean forceRefresh) throws Exception {

        long numWritten = 0;
        GraphicsMod g = null;
        
        if (jbar != null) {
            g =new GraphicsMod();
            g.setJbarValue(jbar, 0, false);
        }
        
        OutputStream out = null;
        URLConnection conn;
        InputStream in = null;

        prout.println("=============FILE DOWNLOAD =================\n" 
                + address + "\n========================================================\n" 
                        + "TO: " + localFileName);

        boolean alreadyContains = checkContains_NonZero(localFileName, "", prout);
        if (alreadyContains && forceRefresh) {
            alreadyContains = false;

        }
        if (alreadyContains) {
            prout.println("folder already contains this SILAM grid, aborting.");
            return DOWNL_EXISTED;
        }

        try {
            URL url = new URL(address);
            out = new BufferedOutputStream(new FileOutputStream(localFileName));
            if (simple) {
                conn = url.openConnection();
            } else {//auth, certOverride can occurr
                //auth
                CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
                if (usrPass != null && address.contains("https:")) {//set basic authentication
                    Authenticator.setDefault(new Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(usrPass[0], usrPass[1].toCharArray());
                        }
                    });
                }

                conn = (HttpURLConnection) url.openConnection();

                if (certBypass && address.contains("https")) {
                    try {
                        prout.println("Bypass certificate authentication once for " + address);
                        setAcceptAllVerifier((HttpsURLConnection) conn);
                    } catch (NoSuchAlgorithmException ex) {
                        ex.printStackTrace(prout);
                    } catch (KeyManagementException ex) {
                        ex.printStackTrace();
                    }
                }

            }//if not simple

            in = conn.getInputStream();

            byte[] buffer = new byte[1024];

            int numRead;

            long lastMegs = 0;
            if (g != null) {
                g.setJbarString(jbar,"Downloading data",true);
            }
            while ((numRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, numRead);
                numWritten += numRead;
                //check size
                long megs = numWritten / 1000000;
                if (megs != lastMegs) {
                    lastMegs = megs;
                    String percs = "";
                    if (sizeEstimateMegs != null) {
                        float perc = 100f * lastMegs / sizeEstimateMegs;
                        if (perc > 100) {
                            perc = 100;
                        }
                        percs = "(" + (int) perc + "%)";
                        if (g != null) {
                            g.setJbarValue(jbar,(int) perc,false);
                            g.setJbarString(jbar,"Downloading data (" + (int) perc + "% complete)",false);
                        }
                    }
                    prout.println("\t downloaded: " + lastMegs + " Mb " + percs);
                }
            }
            if (g != null) {
                g.setJbarString(jbar,"",false);
            }
            prout.println(localFileName + "\t" + numWritten);
        } catch (Exception exe) {
            exe.printStackTrace(prout);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException ioe) {
            }
        }

        if (numWritten > 100) {
            return DOWNL_SUCCESS;
        } else {
            return DOWNL_FAIL;
        }

    }

    public static String mineFromURL_static(boolean certBypass, String address,
            PrintStream out, String[] usrPass) throws Exception {

        if (address.toLowerCase().contains("ftp://")) { // Let's deal with FTP separately
            URL server = new URL(address);
            out.println("Opening FTP connection to " + address);
            URLConnection ftpConnection = server.openConnection(); // TODO Necessary to deal with proxy?
            BufferedReader ftpIn = new BufferedReader(new InputStreamReader(ftpConnection.getInputStream()));
            ftpConnection.setConnectTimeout(10 * 1000);
            ftpConnection.connect();
            String ftpContent = "";
            char[] buf = new char[1024];
            StringBuilder sB = new StringBuilder();
            int count = 0;
            while (-1 < (count = ftpIn.read(buf))) {
                sB.append(buf, 0, count);
            }
            ftpContent = sB.toString();
            ftpIn.close();
            return ftpContent;
        }
        /*
    if (localHost) {
         HttpsURLConnection.setDefaultHostnameVerifier(
                        new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession ssls) {
                        return true;
                       //return hostname.equals("localhost");
                    }
                });
         //String test = readFromURL(address); 
    }
         */

        if (usrPass != null && address.contains("https:")) {//set basic authentication
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(usrPass[0], usrPass[1].toCharArray());
                }
            });
        }

        URL server = new URL(address);
        out.println("Opening connection to " + address);
        HttpURLConnection connection = (HttpURLConnection) server.openConnection();

        if (certBypass && address.contains("https")) {
            try {
                out.println("Bypass certificate authentication once for " + address);
                setAcceptAllVerifier((HttpsURLConnection) connection);
            } catch (NoSuchAlgorithmException ex) {
                ex.printStackTrace(out);
            } catch (KeyManagementException ex) {
                ex.printStackTrace();
            }
        }
        connection.setConnectTimeout(10 * 1000);
        connection.connect();

        //URLConnection yc = google.openConnection();
        // Check if we get a gzipped stream and set the InputStream accordingly
        PushbackInputStream aa = new PushbackInputStream(connection.getInputStream(), 2);
        byte[] magic = new byte[2];
        aa.read(magic);
        aa.unread(magic);
        BufferedReader in;
        if ((magic[0] == (byte) (GZIPInputStream.GZIP_MAGIC)) && (magic[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8))) {
            in = new BufferedReader(new InputStreamReader(new GZIPInputStream(aa)));
        } else {
            in = new BufferedReader(new InputStreamReader(aa));
        }
        String content = "";

        /*
    int lines =0;
    String inputLine;
    long sysSecs = System.currentTimeMillis()/1000;
        if (!stringBuilder) {
            while ((inputLine = in.readLine() ) != null) { 
            //content.add(inputLine);
                content+=inputLine;
                lines++;
            //EnfuserLogger.log(Level.FINER,this.getClass(),inputLine);
            long dur = System.currentTimeMillis()/1000 - sysSecs;
            if (cutOut > 0 && dur > cutOut) break;
            if (lines%1000 ==0) out.println("\t lines read: "+ lines);
            }
        out.println("Read successfully" +lines+ " lines from url.");
         */
        //} else {
        char[] buf = new char[1024];
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (-1 < (count = in.read(buf))) {
            sb.append(buf, 0, count);
        }

        content = sb.toString();
        //}
        //out.println("\n\n" +content);

        in.close();
        return content;
    }

}
