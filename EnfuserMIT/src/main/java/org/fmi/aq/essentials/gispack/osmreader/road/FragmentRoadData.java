/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.road;

import org.fmi.aq.essentials.gispack.osmreader.core.Way;

/**
 *
 * @author johanssl
 */
public class FragmentRoadData {

    public Way way;
    byte value;

    public FragmentRoadData(Way way, byte value) {
        this.way = way;
        this.value = value;

    }

}
