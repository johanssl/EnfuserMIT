/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.source;

import java.util.Comparator;

/**
 *
 * @author johanssl
 */
public class LayerComparator implements Comparator<Layer> {

    @Override
    public int compare(Layer l1, Layer l2) {
        return (int) (l1.dt_system_min - l2.dt_system_min);

        //usage example:    
//Collections.sort(messages, new MessageTimeComparator());
    }

}
