/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feeds;

import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.GridSource;
import org.fmi.aq.enfuser.datapack.source.Layer;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.GeoGrid;

/**
 *
 * @author johanssl
 */
public class HourlyInterpolator {
    
       public static void interpolateMissingLayers(Feed f, DataPack dp,
            ArrayList<String> vars) {

        
        for (String var : vars) {

            ArrayList<GeoGrid> adds = new ArrayList<>();

            int typeInt = f.ops.VARA.getTypeInt(var);
            VariableData vd = dp.variables[typeInt];
            if (vd == null) {
                continue;
            }

            AbstractSource s = vd.getSource(f.mainProps.feedDirName);
            GridSource gs = (GridSource) s;
            if (s == null) {
                continue;
            }

            Dtime current = s.dom.start.clone();
            current.addSeconds(-3600);//in the while loop 1h is instantly added so the first iteration seconds equal 'current'.
            while (current.systemHours < s.dom.end.systemHours) {
                current.addSeconds(3600);//this way endless loops are impossible.

                // EnfuserLogger.log(Level.FINER,GFSfeed.class,"GFS GRID INTERPOLATION for "+ var +"======================================================================================");
                try {
                    //interpolate 3h, 6h data into hourly coverage
                    Layer[] ls = s.getPreviousNextLayer(current, null);
                    Layer prev = ls[0];
                    Layer next = ls[1];

                    if (prev == null || next == null) {
                        // EnfuserLogger.log(Level.FINER,GFSfeed.class,"\t===============GFS interpolation: null layer, cannot interpolate, " + current.getStringDate());
                        continue;
                    }//not found for some reason
                    else if (prev == next) {
                        //  EnfuserLogger.log(Level.FINER,GFSfeed.class,"\t===============GFS interpolation: layers are the same, no need to interpolate, " + current.getStringDate());
                        continue;
                    }

                    float diffB = current.systemHours() - prev.g.dt.systemHours();
                    if (diffB <= 0) {
                        //  EnfuserLogger.log(Level.FINER,GFSfeed.class,"\t===============GFS interpolation: diffB <=0 !!!!" +diffB+", " +current.getStringDate());
                        continue;
                    } ////exact same time found, nothing to interpolate. ls[1] has the same time, no need to check that
                    float diffN = next.g.dt.systemHours() - current.systemHours();
                    if (diffN <= 0) {
                        // EnfuserLogger.log(Level.FINER,GFSfeed.class,"\t===============GFS interpolation: diffN <=0!!!!" +diffN+", " + current.getStringDate());
                        continue;
                    }
                    //  EnfuserLogger.log(Level.FINER,GFSfeed.class,"\t===============GFS interpolation: diffN =" + diffN +", diffB = "+diffB+ ", "+current.getStringDate());
                    float f1 = diffN / (diffN + diffB);//e.g., diff next is large => previous get higher weight
                    float f2 = diffB / (diffN + diffB);

                    GeoGrid gpr = prev.g;
                    GeoGrid gnxt = next.g;
                    float[][] dat = new float[gpr.H][gpr.W];
                    for (int h = 0; h < dat.length; h++) {
                        for (int w = 0; w < dat[0].length; w++) {
                            dat[h][w] = f1 * gpr.values[h][w] + f2 * gnxt.values[h][w];
                        }
                    }
                    GeoGrid gInter = new GeoGrid(dat, current.clone(), gpr.gridBounds);
                    adds.add(gInter);

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }//while current

            //add new interpolated layers
            for (GeoGrid g : adds) {
                
                    EnfuserLogger.log(Level.FINER,HourlyInterpolator.class,"\t" + f.mainProps.feedDirName
                            + ": Adding interpolated layer: " + g.dt.getStringDate() + " for var " + var);
                    EnfuserLogger.log(Level.FINER,HourlyInterpolator.class,g.dlatInMeters + ", " + g.dlonInMeters);
                    EnfuserLogger.log(Level.FINER,HourlyInterpolator.class,g.gridBounds.toText());
                
                gs.addGrid(g,null);
            }

        }//for vars
    } 
    
}
