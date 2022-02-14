/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.shg;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getH;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getLat;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getLon;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getW;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author johanssl IndexedData is a gridded data structure that benefits from
 * the sparsity of gridded data. Only non-zero values in H x W x Z -grid will
 * consume memory Proper indexing of occupied cells enable fast updating of new
 * and existing cells; ArrayList-searches are not used
 */
public class SparseHashGrid extends SparseGrid implements Serializable {

    private static final long serialVersionUID = 7236472293822776147L;//Important: change this IF changes are made that breaks serialization! 
    //private static final long serialVersionUID = 78391261;
    protected final HashMap<Long, AreaContainer> hmap_t; // list of h-creatures

    public SparseHashGrid(short maxH, short maxW, Boundaries b, String[] cellContentNames, int timeInterval) {

        super(maxH, maxW, b, cellContentNames, timeInterval);
        hmap_t = new HashMap<>();

    }

    public static final short H_test = 1000;
    public static final short W_test = 1000;

    @Override
    public boolean sumOrCreateContent_HW(float[] incr, short h, short w, boolean forceNew, Dtime dt, long sysSecs) {
        long tKey = this.getT_key(sysSecs);
        AreaContainer ac = this.hmap_t.get(tKey);
        // AreaContainer2 ac = this.hmap_t.get(date);

        if (ac == null) { // a new time slice!
            ac = new AreaContainer(); // not replicated

            this.hmap_t.put(tKey, ac);

            //adjust the range
            //getting the corrent timestamp from sysSecs can be tricky. See Datetime.main
            this.updateDateStrings(tKey, dt);

        }

        //short[] HW = this.getHW_index(lat, lon);
        //if (HW==null) return false;
        ac.sumOrCreateContent(incr, h, w, forceNew, this);
        return true;
    }

    @Override
    public float[] getCellContent_tkHW(long tKey, short h, short w) {
        AreaContainer gt = this.hmap_t.get(tKey);

        // AreaContainer2 gt = this.hmap_t.get(date);
        if (gt == null) {
            return null;
        }

        //Container gw = gt.get(h,w,this);
        float[] gw = gt.get(h, w, this);
        if (gw == null) {
            return null;
        }

        //return gw.values(this); //handles the conversion form Bytes to Floats, if applicable. Cool I say!
        return gw; //handles the conversion form Bytes to Floats, if applicable. Cool I say!
    }

    @Override
    public void saveToFile(String fullName) {

        EnfuserLogger.log(Level.FINER,this.getClass(),"Saving SparseHashGrid to: " + fullName);
        // Write to disk with FileOutputStream
        FileOutputStream f_out;
        try {
            f_out = new FileOutputStream(fullName);

            // Write object with ObjectOutputStream
            ObjectOutputStream obj_out;
            try {
                obj_out = new ObjectOutputStream(f_out);

                // Write object out to disk
                obj_out.writeObject(this);
                obj_out.close();
                f_out.close();
            } catch (IOException ex) {
                Logger.getLogger(SparseHashGrid.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(SparseHashGrid.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static SparseHashGrid load(String filename) {
        // Read from disk using FileInputStream
        FileInputStream f_in;
        try {
            f_in = new FileInputStream(filename);

            // Read object using ObjectInputStream
            ObjectInputStream obj_in;
            try {
                obj_in = new ObjectInputStream(f_in);
                try {
                    // Read an object
                    Object obj = obj_in.readObject();
                    if (obj instanceof SparseHashGrid) {
                        EnfuserLogger.log(Level.FINER,SparseHashGrid.class,"IndexedData loaded from .dat-file successfully.");
                        SparseHashGrid id = (SparseHashGrid) obj;
                        return id;
                    }
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(SparseHashGrid.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (IOException ex) {
                Logger.getLogger(SparseHashGrid.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(SparseHashGrid.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    @Override
    public int getMaxT() {
        int T = (int) ((this.maxKey - this.minKey) / 60 / this.timeInterval_min);
        return T;
    }

    @Override
    public float[] getCellContent(long sysSecs, double lat, double lon) {

        long tKey = this.getT_key(sysSecs);
        short h = (short) getH(this.b, lat, dlat);
        short w = (short) getW(this.b, lon, dlon);
        boolean ok = this.HWok(h, w);
        if (!ok) {
            return null;
        }

        return getCellContent_tkHW(tKey, h, w);
    }

    @Override
    public ArrayList<CoordinatedVector> getCoordinatedDataVectors(Dtime dt) {
        ArrayList<CoordinatedVector> arr = new ArrayList<>();
        long tKey = this.getT_key(dt);

        AreaContainer ac = this.hmap_t.get(tKey);
        // AreaContainer2 ac = this.hmap_t.get(date);

        if (ac == null) {
            return arr;
        }
        for (int h = 0; h < this.H; h++) {
            HashMap<Short, float[]> cont = ac.containers[h];
            if (cont == null) {
                continue;
            }

            for (Short w : cont.keySet()) {
                float[] dat = cont.get(w);
                if (dat == null) {
                    continue;//should not happen
                }
                float lat = (float) getLat(this.b, h, this.dlat);
                float lon = (float) getLon(this.b, w, this.dlon);

                arr.add(new CoordinatedVector(dat, lat, lon));

            }//for w that exist
        }//for h

        return arr;
    }

}
