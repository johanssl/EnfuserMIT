/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feeds;

import java.util.Comparator;

/**
 *
 * @author johanssl
 */
public class SilamAddressComparator implements Comparator<SilamAddress> {

    @Override
    public int compare(SilamAddress s1, SilamAddress s2) {
        return (int) (s2.getRank() - s1.getRank());

    }


}
