/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.reader;

import java.util.ArrayList;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import java.util.concurrent.Callable;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.customemitters.AgePack;
import org.fmi.aq.enfuser.core.gaussian.fpm.FootprintBank;
import static org.fmi.aq.enfuser.datapack.reader.DefaultLoader.FPM_FORMS_NAME;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.essentials.gispack.Masks.MapPack;

/**
 *
 * @author johanssl
 */
public class DefaultLoadTask implements Callable<Object[]> {

    final String eleDesc;
    final FusionOptions ops;
    final private MapPack mp;
    final ArrayList<Feed> ff;
    final DataBase db;
    public DefaultLoadTask(String eleDesc, FusionOptions ops,
            MapPack mp, DataBase DB, ArrayList<Feed> ff) {
       
        this.ops = ops;
        this.eleDesc = eleDesc + "";
        this.mp = mp;
        this.ff = ff;
        this.db = DB;
    }

    @Override
    public Object[] call() throws Exception {
        Object o = null;

        if (eleDesc.contains("osmLayer")) {
            try {
                o = OsmLayer.load(eleDesc);
            } catch (Exception e) {
                EnfuserLogger.log(e, Level.SEVERE, this.getClass(),
                        "Cannot load OsmLayer: " + eleDesc);
            }

        } else if (eleDesc.contains("ALL_FEEDS")) {//dataPack needs DB so it must be created beforehand.
            o = new DataPack(db, ops, mp, ff);
            
        } else if (eleDesc.contains("FEED=")) {
            ArrayList<Feed> onef = new ArrayList<>();
            for (Feed f:ff) {
                String tag = f.mainProps.argTag;
                if (eleDesc.contains(tag)) {
                    onef.add(f);
                    break;
                }
            }//for feeds
             o = new DataPack(db, ops, mp, onef);
        
        } else if (eleDesc.contains(FPM_FORMS_NAME)) {
                o = FootprintBank.load();     
        
        } else if (eleDesc.contains(DefaultLoader.EMITTER_PACK_NAME)) {
               o = new AgePack(ops.getRunParameters().start(), ops.getRunParameters().end(), ops);            
                
        } else {
            EnfuserLogger.log(Level.WARNING, this.getClass(),
                    "Unknown load description: " + eleDesc);
        }
        if (o==null) return null;
        return new Object[]{o,eleDesc};
    }

}
