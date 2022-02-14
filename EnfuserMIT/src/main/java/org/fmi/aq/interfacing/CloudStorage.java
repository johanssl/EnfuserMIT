/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;

import java.io.File;
import java.util.ArrayList;

/**
 *An interface for storing and reading datasets fron and to a Cloud Service,
 * such as AWS S3. This is aimed mainly for output data, but can as well
 * offer emission data updating and storing mined input data from Feeds
 * to the cloud service.
 * 
 * The interface has been designed based on AWS S3 and therefore some of the methods
 * may require generalization in the future.
 * @author johanssl
 */
public class CloudStorage {


    public CloudStorage() {
        
    }
    
    
     /**
     * Connect to the cloud service. Once established
     * file download and storing must be possible.
     * @return boolean value to describe whether or not the connection
     * was successfully established.
     */
    public boolean establishConnection() {
        return false;
    }

    /**
    * Close any active connections. In case there are none,
    * this should do nothing.
    * @return boolean value to describe whether or not the connection
    * was closed successfully.
    */
    public boolean closeConnection() {
      return true;
    }

    /**
    * Browse the content of the opened Clound Service and a return a list
    * of "keys". These keys must be usable to launch file downloads using the keys.
    * @param args TODO parameters for the method that have not been defined yet.
    * use null.
    * @param filter a string name filter that should act as follows
    *  - only the files that contain the String value are listed in the result.
    *  - if null, then the filter is not used (all files listed)
    * @return a list of "keys" in String form, that can be used for download.
    */
    public ArrayList<String> browse(String[] args, String filter) {
        return null;
    }

    /**
    * Launch a file download for the given array of keys.
    * @param keys an array of keys to be downloaded from the service
    * @param dir directory where downloaded file are to be put.
    * @return a list of absolute file paths for all successfully downloaded
    * files.
    */
    public ArrayList<String> download(String[] keys, String dir) {
        return null;
    }
    
    public File download(String key, String dir) {
        return null;
    }

    /**
    * Save settings related to the Cloud Service. This is especially intended
    * to be used BEFORE the connection is established so that the given list
    * of tag-value pairs contains e.g., server location, "bucket" names
    * and credentials required for the connection.
    * @param arr an arrayList of tag-value pairs {tag,value}.
    * //TODO the possible TAG names should be well defined.
    * @return true if successful.
    */
    public boolean setParameters(ArrayList<String[]> arr) {
        return true;
    }

    
    /**
    * Store a local file to the Cloud Service.
    * @param fname full absolute path to the file that is to be put to the cloud.
    * @param key key name which is used for the identification of the file
    * once it has been stored to the service.
    * @param acl access control object that should define the access rights 
    * for this specific file that is stored.
    *   - Example: For AWS S3 this should be a CannedAccessControl instance
    * @param o TODO, not used, (null)
    * @return boolean value: true for success.
    */
    public boolean put(String fname, String key, Object acl, Object o) {
            return false;
    }

   /**
   * Establish connection to AWS S3 bucket and search files that has
   * the 'areaDef' in their names.Then, check all files in the targetDir
   * and for the missing files, download them to targetDir.
   * @param targetDir target directory that is synched with content
   * @param bucket bucket name for AWS S3
   * @param areaDef area name that must be contained in the synched files.
   * @param region define AWS region for the bucket
   */ 
    public void syncDownloadContent(String targetDir, String bucket, String areaDef, String region) {

    }


   /**
   * Establish connection to AWS S3 bucket and search files that has
   * the 'areaDef' in their names.Then, check all files in the targetDir
   * and for the missing files, download them to targetDir.
   * @param targetDir target directory that is synched with content
   * @param bucket bucket name for AWS S3
   * @param areaDef area name that must be contained in the synched files.
   * @param region define AWS region for the bucket
   * @param dates Dtime[] start, end for checking the files that are relevant.
   * @param bounds modelling area Boundaries
   */ 
    public void syncDownloadContent_extended(String targetDir, String bucket, String areaDef,
            String region, Object[] dates, Object bounds) {
              
    }

}
