/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;

import java.io.File;
import org.fmi.aq.enfuser.options.FusionOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.core.DataCore;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.output.Arch;
import static org.fmi.aq.enfuser.core.output.Arch.getStamp;
import org.fmi.aq.enfuser.core.gaussian.puff.PPFcontainer;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import org.fmi.aq.enfuser.customemitters.AbstractEmitter;
import static org.fmi.aq.enfuser.customemitters.AbstractEmitter.CSVLINE_DYN;
import static org.fmi.aq.enfuser.customemitters.AbstractEmitter.HQLINE_DYN;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import org.fmi.aq.essentials.plotterlib.Visualization.FigureData;
import org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions;
import org.fmi.aq.essentials.plotterlib.animation.Anim;
import org.fmi.aq.essentials.plotterlib.animation.AnimEncoder;
import org.fmi.aq.essentials.shg.SparseHashGrid;


/**
 *This interface provides the means to have additional data Feeds that have not
 * been defined in the core package. For this to be possible an existing definition list
 * (static String[][], Feed) must be extended and this will occur when the static table is read
 * for the first time.
 * 
 * During setup, the collection of additional Feeds also need to be addressed: they
 * also need their settings to be defined. This is taken care of getDefaultRegionalArguments_setup().
 * 
 * Note: any additional Feed introduced this way, that is to be active, MUST have an argument line
 * defined in region_X.csv control file. Just as the hard-coded feeds do.
 * @author johanssl
 */
public class Feeder {

    public static SparseHashGrid getRainNowCastDataset(DataPack datapack) {
            return null;
    }

    
 /**
 * Get a list of all custom Feeds as Objects.
 * @param op FusionOptions that can be null. If so, then the feeds
 * should be given at least in disable read,write dummy mode.
 * @return a list of Feeds.
 */   
    public ArrayList<Object> getCustomFeeds(Object op) {
       
        FusionOptions ops = null;
        if (op!=null) ops = (FusionOptions)op;
        ArrayList<Feed> ff = getAll(ops);
        ArrayList<Object> obs = new ArrayList<>();
        for (Feed f:ff) {
            obs.add(f);
        }
        return obs;
    }
    
    public static ArrayList<Feed> getAll(FusionOptions ops) {
        ArrayList<Feed> ff = new ArrayList<>();  
        return ff; 
    }

    /**
    * Handle a read operation for an emitter that is not natively supported
    * without addon-libraries.Although emitters are not technically feeds, they are objects that provide
    * input for the model.
    * @param emType emitter type class
    * @param f the file from which the emitter is read from
    * @param ops options
    * @return an emitter (null if not identified)
    */
    public AbstractEmitter readEmitter(int emType, File f, FusionOptions ops) {
            return null;   
    }

     /**
     * Perform a feed-related task that may involve a specifically named feed
     * to be present
     * @param ens data core/FusionCore instance
     * @param start temporal start time range for the operation
     * @param end temporal end time range for the operation
     * @param taskID a string identifier to specify the task at hand.
     * @param keyValues additional instructions given as key-value hash.
     */
    public void manualFeedOperation(DataCore ens, Dtime start, Dtime end, String taskID,
            HashMap<String, String> keyValues) {

    }

    public void ingestCustomDataSets(DataPack datapack) {

    }
    
   /**
    * For the creation of allPollutants_x.zip, browse the content of loaded
    * custom feed content. Then create local files that will be included
    * in the final zip archive.
    * @param ens the core
    * @param af the AreaFusion instance that is currently being converted into
    * archive zip file.
    * @param dir directory for local files to be created in.
    * @return a list of local filenames that will be included in the zip.
    */ 
   public ArrayList<String> zipCustomFeedContentForArchive(FusionCore ens, AreaFusion af, String dir) {
       ArrayList<String> zipFiles = new ArrayList<>(); 
       return zipFiles;     
    }

    public float modifyFlowSpeedBasedOnCongestion(float speed, RoadPiece rp, Dtime dt) {
        return speed;
    }
    
        /**
     * Create some highly optional secondary output content.
     * @param ens the core
     * @param dir directory for output
     * @param areaStart time for modelling time start
     * @param end end time
     * @param nonMet_typeInts list of variables
     * @param single a single AreaFusion instance for area definition.
     */
    public static void secondaryOptionalOutput(FusionCore ens,String dir, Dtime areaStart, Dtime end,
            ArrayList<Integer> nonMet_typeInts, AreaFusion single) {
    }
    
    
    public static void modifyGridBoundariesForReading(Boundaries b, Feed aThis) {        

    }
    
 /**
 * Fetch a name to describe this pair of coordinates. The name should
 * have roadname (if applicable) suburb so that that location
 * would be unique.
 * @param lat latitude
 * @param lon longitude
 * @param compact if true the returned String should be more compact
 * @return String description. This should not be very long (smaller than 40 characters)
 * and should not contain characters unsuitable for file names.
 */
    public static String reverseGeocode(double lat, double lon, boolean compact) {
        return null;
    }
    
}
