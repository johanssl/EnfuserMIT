/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feeds;

import org.fmi.aq.enfuser.mining.feed.roadweather.RoadMaintenance;
import org.fmi.aq.enfuser.mining.feed.roadweather.RoadWeatherFeed;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.interfacing.Feeder;

/**
 *This class creates a list of all known Feeds, also taking into account
 *  the addon-feeds. This list can be formed for a) feeds that extract
 * b) feeds that read during load and c) all active (reads or extracts) feeds.
 * @author johanssl
 */
public class FeedFetch {


 public final static String MUST_READ ="MUST_READ";
 public final static String MUST_STORE ="MUST_STORE";
 public final static String MUST_ACT ="MUST_ACT";
 
    public static ArrayList<Feed> getFeeds(FusionOptions ops,
            String filter) {
        boolean mustRead = false;
         if (filter!=null && filter.contains(MUST_READ)) mustRead = true;
        boolean mustStore = false;
         if (filter!=null && filter.contains(MUST_STORE)) mustStore = true;
         boolean mustAct = false;
            if (filter!=null && filter.contains(MUST_ACT)) mustAct = true;
            
        ArrayList<Feed> ff = new ArrayList<>();
                ff.add(new FMIopenFeed(ops));
                ff.add(new SilamFeed(ops));
                ff.add(new HirlamFeed(ops));
                ff.add(new RoadMaintenance(ops));                
                ff.add(new RoadWeatherFeed(ops));
                ff.add(new HarmonieFeed(ops));

                
               ArrayList<Object> obs = new Feeder().getCustomFeeds(ops);
               for (Object o:obs) {
                   ff.add((Feed)o);
               } 
                
                ArrayList<Feed> finalF = new ArrayList<>();
                for (Feed f:ff) {
                    if (mustRead && !f.readable) continue;
                    if (mustStore && !f.stores) continue;
                    if (mustAct && !f.readable && !f.stores) continue;
                    
                    finalF.add(f);
                }
                
                return finalF;
    }   
    

    public static Feed[] getRegionalFeeds_vect(FusionOptions ops,
            String filter) {

        ArrayList<Feed> feeds = getFeeds(ops,filter);
        Feed[] ff = new Feed[feeds.size()];
        return feeds.toArray(ff);
    }   
    
}
