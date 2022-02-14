/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import java.util.Comparator;

/**
 *
 * @author johanssl
 */
public class MECsorter implements Comparator<MapEmitterCell> {

    @Override
    public int compare(MapEmitterCell g1, MapEmitterCell g2) {
        return (int) (g1.maximumContribution_percent - g2.maximumContribution_percent);

        //usage example:    
//Collections.sort(messages, new MessageTimeComparator());
    }
}
