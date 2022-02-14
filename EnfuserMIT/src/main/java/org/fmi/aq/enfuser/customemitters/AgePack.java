/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import org.fmi.aq.essentials.date.Dtime;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 *
 * @author johanssl
 */
public class AgePack {

    public ArrayList<AbstractEmitter> ages;

    public AgePack(Dtime start, Dtime end, FusionOptions ops) {
        this.ages = AbstractEmitter.readAllFromDir(ops, start, end);
    }

}
