/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.util.Comparator;

/**
 *
 * @author johanssl
 */
public class RadListComparator implements Comparator<RadListElement> {

    @Override
    public int compare(RadListElement e1, RadListElement e2) {
        return (int) ((int)(e1.dist_m*10) - (int)(e2.dist_m*10));
    }
}
