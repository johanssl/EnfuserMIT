/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.fastgrids;

import org.fmi.aq.enfuser.core.AreaInfo;
import static org.fmi.aq.enfuser.core.tasker.BlockAnalysisTask.multiThreadTask;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author Lasse
 */
public class BlockBank {

    public BlockAnalysis[][] blocks;
    AreaInfo in;
    int nullCount=0;
    int dummies =0;
    public BlockBank(AreaInfo in, FusionCore ens, boolean mt) {
        this.in = in;
        if (mt) {
            this.blocks = multiThreadTask(ens, in);
              for (int h = 0; h < in.H; h++) {
                for (int w = 0; w < in.W; w++) {
                   if (this.blocks[h][w]==null) {
                       nullCount++;
                   } else if (this.blocks[h][w].dummy) {
                       dummies++;
                   }
                }//for w
              }//for h
              
        } else {//single thread
            this.blocks = new BlockAnalysis[in.H][in.W];

            for (int h = 0; h < in.H; h++) {
                for (int w = 0; w < in.W; w++) {
                    double lat = in.getLat(h);
                    double lon = in.getLon(w);

                    OsmLayer osmMask = ens.mapPack.getOsmLayer(lat, lon);
                    this.blocks[h][w] = new BlockAnalysis(osmMask, ens.ops, lat, lon, null);
                    if (this.blocks[h][w]==null) {
                        nullCount++;
                    } else if (this.blocks[h][w].dummy) {
                       dummies++;
                   }
                }//for w
            }//for h

        }
        EnfuserLogger.log(Level.INFO,BlockBank.class,"All BlockAnalysis instances "
                + "created. Nulls="+this.nullCount +", dummies="+dummies);
    }



    BlockAnalysis getStoreBlockAnalysis(OsmLayer osmMask, FusionCore ens, double lat,
            double lon) {

        int h = in.getH(lat);
        int w = in.getW(lon);
        if (in.oobs(h,w)) {
            return new BlockAnalysis(osmMask, ens.ops, lat, lon, null);
        } else {
            return this.blocks[h][w]; 
        }

    }

}
