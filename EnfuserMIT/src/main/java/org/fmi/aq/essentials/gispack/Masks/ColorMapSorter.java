/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.Masks;

import java.util.Comparator;
import static org.fmi.aq.enfuser.ftools.FastMath.MILLION;
import org.fmi.aq.essentials.gispack.osmreader.colmap.ColorMap;

/**
 * @author johanssl
 */
public class ColorMapSorter implements Comparator<ColorMap> {

    @Override
    public int compare(ColorMap c1, ColorMap c2) {
        return (int) (c1.dlat*MILLION - c2.dlat*MILLION);

        //usage example:    
//Collections.sort(messages, new MessageTimeComparator());
    }


}
