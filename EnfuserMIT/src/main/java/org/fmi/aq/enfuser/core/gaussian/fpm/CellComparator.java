/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.fpm;

import java.util.Comparator;

/**
 *
 * @author johanssl
 */
public class CellComparator implements Comparator<FootprintCell> {

    @Override
    public int compare(FootprintCell e1, FootprintCell e2) {
        return (int) (e1.dist_m - e2.dist_m);

        //usage example:    
//Collections.sort(messages, new MessageTimeComparator());
    }

}
