/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.ERCFarguments;
import org.fmi.aq.interfacing.CloudStorage;
import java.io.File;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.ftools.FileOps;

/**
 *
 * @author johanssl
 */
public class EmitterSynch {

    public static void synchEmitterFiles(FusionOptions ops, Dtime start, Dtime end) {

        EnfuserLogger.log(Level.FINER,EmitterSynch.class,"EmitterSynch:");
        ERCFarguments rops = ops.getArguments();
        GlobOptions.get().setHttpProxies();
        CloudStorage io = rops.getAWS_S3(true);
        String targetDir = ops.getDir(FusionOptions.DIR_EMITTERS_REGIONAL) + "temp" + FileOps.Z;
        File test = new File(targetDir);
        if (!test.exists()) {
            test.mkdirs();
        }

        EnfuserLogger.log(Level.FINER,EmitterSynch.class,"TargetDir=" + targetDir);
        String[] bucket_filter = rops.getEmitterSynchString(ops.areaID());
        if (bucket_filter == null) {
            EnfuserLogger.log(Level.FINER,EmitterSynch.class,
                    "\t Synch settings have not been defined in RegionArguments.");
            return;
        }

        String bucket = bucket_filter[0];
        String reg = bucket_filter[1];
        String areaDef = bucket_filter[2];
        //io.syncDownloadContent(targetDir, bucket, areaDef, reg);
        io.syncDownloadContent_extended(targetDir, bucket, areaDef, reg,
                new Dtime[]{start,end}, ops.boundsExpanded(true));
    }

}
