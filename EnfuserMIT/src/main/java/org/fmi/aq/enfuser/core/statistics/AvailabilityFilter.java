/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.Layer;
import org.fmi.aq.essentials.date.Dtime;
import java.util.ArrayList;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class AvailabilityFilter {

    ArrayList<String[]> sourceVars;

    public AvailabilityFilter() {
        this.sourceVars = new ArrayList<>();
    }

    public void addSourceVar_availabilityCondition(String source, String var) {
        this.sourceVars.add(new String[]{
            source.replace(" ", ""),
            var.replace(" ", "")});
    }

    public boolean dataAvailable(FusionCore ens, Dtime dt) {

        for (String[] sourceVar : this.sourceVars) {
            String source = sourceVar[0];
            String type = sourceVar[1];

            int typeInt = ens.ops.getTypeInt(type);
            if (typeInt<0) {
                continue;
            }
            VariableData vd = ens.datapack.variables[typeInt];
            if (vd == null) {
                return false;
            }
            AbstractSource s = ens.datapack.variables[typeInt].getSource(source);
            if (s == null) {
                return false;
            }

            Layer l = s.getExactLayer(dt);
            if (l == null) {
                return false;
            }
        }
        return true;
    }

    public ArrayList<String> getUnavailableList(FusionCore ens, Dtime start, Dtime end, boolean print) {
        ArrayList<String> arr = new ArrayList<>();
        Dtime current = start.clone();
        current.addSeconds(-3600);
        int okc = 0;
        int unav = 0;
        while (current.systemHours() <= end.systemHours()) {
            current.addSeconds(3600);

            boolean ok = this.dataAvailable(ens, current);
            if (!ok) {
                unav++;
                arr.add(current.getStringDate());
                if (print) {
                    EnfuserLogger.log(Level.FINER,AvailabilityFilter.class,
                            "Unavailable: " + current.getStringDate());
                }
            } else {
                okc++;
            }

        }//while time
        EnfuserLogger.log(Level.FINER,AvailabilityFilter.class,
                "AvailabilityFilter: " + start.getStringDate() + " - " + end.getStringDate());
        EnfuserLogger.log(Level.FINER,AvailabilityFilter.class,
                "hours of available data: " + okc + ", unavailable = " + unav);
        return arr;
    }

}
