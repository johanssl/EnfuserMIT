/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feeds;

import java.io.File;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.util.ArrayList;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.CREDENTALS_NONE;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.FIN;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.enfuser.logging.Parser;
import org.fmi.aq.essentials.gispack.Masks.MapPack;

/**
 *
 * @author johanssl
 */
public class FMIopenFeed extends Feed {
   private final static String ARG_TAG = "FMI_OPEN_ARGS";
   private final static String[] SPECS =  {"AllObs", "15", ARG_TAG   ,"2"    ,".txt" , CREDENTALS_NONE};
   
       public static boolean isMe(String argTag) {
        if(argTag==null) return false;
        return ARG_TAG.equals(argTag);
    }
       
    public FMIopenFeed(FusionOptions ops) {
        super(FeedConstructor.create(SPECS,FIN,ops),ops);
        searchRad_m=null;
        genID ="station";//base name for new information source
        genID_decimals=1000;//decimals used to round-up coordinates in name id
        
        this.fileReader.skipFirstLine=false;//no headers, each line has data.
    }
    

    @Override
    public boolean writesInitially() {
        return true;
    }
    @Override
    public boolean globalInitially() {
        return false;
    }
    @Override
    public String customArgumentInitially(ArrayList<String> areaNames) {
      return "insrt custom instructions here.";
    }
    @Override
    public boolean readsInitially() {
        return true;
    } 
    
    private final static String URL_BODY = "https://opendata.fmi.fi/wfs?request=GetFeature&storedquery_id=fmi::observations::";

    public ArrayList<String> getRecentAQ(Dtime current, double minlat, double maxlat, double minlon, double maxlon,
            String dir) {

        Dtime start = current.clone();
        Dtime end = current.clone();
        ArrayList<String> arrFiles = new ArrayList<>(9);
        start.convertToTimeZone((byte) 0);
        start.addSeconds(-3600 * 1);
        end.convertToTimeZone((byte) 0);
        end.addSeconds(3600);

        String start_str = start.getStringDate(Dtime.STAMP_NOZONE) + "Z";
        String end_str = end.getStringDate(Dtime.STAMP_NOZONE) + "Z";

        String address = URL_BODY + "airquality::hourly::simple&"
                + "bbox=" + minlon + "," + minlat + "," + maxlon + "," + maxlat
                + "&starttime=" + start_str + "&endtime=" + end_str
                + "&projection=epsg:4326";

        String content = "";
        println("Extracting AQ OBS with\n" + address);
        try {
            //content = getUrlContent(address);
            content = mineFromURL(address,null);
            SOUT.println(content);
            address = address.replace("fmi::", "urban::");
            //content+= getUrlContent(address);
            String content2 = mineFromURL(address,null);
            SOUT.println(content2);
            content += content2;

        } catch (Exception ex) {
            printStackTrace(ex);
        }

        if (content.length() < 1000) {
            return arrFiles;
        }

        //write raw as gml to file
        String localFileName = "FMI_AQ_" + start_str.replace(":", "-") + ".gml";

        if (this.rawDataOut) {
            ArrayList<String> arar = new ArrayList<>();
            arar.add(content);
            println("Saving FMI_AQ to " + dir + localFileName);
            FileOps.printOutALtoFile2(dir, arar, localFileName, false);
            arrFiles.add(localFileName + "");
        }

        //parsed data 
        ArrayList<String> arr = rawParser(content, null, new Parser(dir + localFileName));
        if (arr.isEmpty()) {
            return arrFiles;
        }

        //store processed lines     
        localFileName = "FMI_AQ_" + start_str.replace(":", "-") + ".txt";
        println("Saving FMI_AQ to " + dir + localFileName + ", there are " + arr.size() + " entries");

        FileOps.printOutALtoFile2(dir, arr, localFileName, false);
        arrFiles.add(localFileName + "");
        return arrFiles;

    }
    
    
    public static void main(String[] args) {
        Dtime start = new Dtime("2020-01-01T00:00:00");
        Dtime end = new Dtime("2020-12-31T23:00:00");
        FusionOptions ops = new FusionOptions("Finland");
        FMIopenFeed f = new FMIopenFeed(ops);
        f.printAvailabilityCSV(start, end);
    }
    
    public void printAvailabilityCSV(Dtime start, Dtime end) {
        ArrayList<String> arr = new ArrayList<>();
        arr.add("TIME;AVAILABILITY;");
        Dtime current = start.clone();
        while(current.systemHours()<end.systemHours()) {
            String fname = this.primaryMinerDir +"FMI_AQ_"+current.getStringDate_YYYY_MM_DDTHH() +"-00-00Z.txt";
            File f = new File(fname);
            String line = current.getStringDate_noTS()+";";
            if (f.exists()) {
                line+="1;";
            } else {
                line+="0";
            }
            current.addSeconds(3600);
            arr.add(line);
        }
        
        FileOps.printOutALtoFile2(this.ops.defaultOutDir(), arr,
                this.mainProps.feedDirName +"_availabilityTest.csv", false);
    }

    /**
     * Extract and store FMI open data measurements (hourly) for a given region
     * name and time span.
     *
     * @param start start time for extraction (use full hours in UTC)
     * @param end end time for extraction (use full hours in UTC)
     * @param reg region name. A name with this region must exist (has
     * region_...csv)
     */
    public static void extractSpan(Dtime start, Dtime end, String reg) {
        FusionOptions ops = new FusionOptions(reg);
        FMIopenFeed f = new FMIopenFeed(ops);
        Boundaries b = f.b;
        Dtime current = start.clone();
        while (current.systemHours() <= end.systemHours()) {

            try {

                f.getRecentAQ(current, b.latmin, b.latmax, b.lonmin, b.lonmax, f.primaryMinerDir);

            } catch (Exception e) {

            }
            current.addSeconds(3600);
        }

    }

    @Override
    public void read(DataPack dp, RunParameters p, DataBase DB, MapPack mp) {
        readTextData(dp, p, DB,mp);
    }

    @Override
    public ArrayList<String> store() {

        storeTick();
        Dtime current = Dtime.getSystemDate_utc(false, Dtime.FULL_HOURS);
        ArrayList<String> arr = getRecentAQ(current, b.latmin, b.latmax, b.lonmin, b.lonmax, primaryMinerDir);
        if (arr == null) {
            this.lastStoreCount = -1;
        } else {
            this.lastStoreCount = arr.size();
        }
        return arr;

    }

    public ArrayList<String> rawParser(String content, Boundaries b, Parser safeParser) {

        ArrayList<String> arr = new ArrayList<>();
        String[] elems;
        String[] temp;
        content = content.replace("\n", "");
        content = content.replace(" </gml:pos>", ";");
        content = content.replace("</gml:Point>", "");
        content = content.replace("</BsWfs:Location>", ";");

        content = content.replace("<BsWfs:Time>", "");
        content = content.replace("</BsWfs:Time>", ";");

        content = content.replace("<BsWfs:ParameterName>", "");
        content = content.replace("</BsWfs:ParameterName>", ";");

        content = content.replace("<BsWfs:ParameterValue>", "");
        content = content.replace("</BsWfs:ParameterValue>", ";");

        String[] split = content.split("<gml:pos>");
        for (String s : split) {
            temp = s.split("</BsWfs:BsWfsElement>");

            if (temp != null && temp.length > 1) {

                String trueLine = temp[0];
                if (trueLine.contains("NaN;")) {
                    continue;
                }
                trueLine = trueLine.replace("_PT1H_avg", "");

                String finalLine = "";
                elems = trueLine.split(";");
                elems[0] = elems[0].replace(" ", ";");//lat lon separated with " "
                for (String e : elems) {
                    e = e.replace(" ", "");//remove all whitespaces
                    finalLine += e + ";";
                }
                // EnfuserLogger.log(Level.FINER,this.getClass(),"\t" + finalLine); 
                arr.add(finalLine);

            }
        }//for split
        return arr;
    }

}
