/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feeds;

import java.util.ArrayList;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.CREDENTALS_NONE;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.GLOBAL;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.essentials.gispack.Masks.MapPack;

/**
 *This dummy feed class in inteded to be used
 * for ID-matching only. The method that does this
 * requres a Feed with the properties that are set
 * within the class constructor.
 * @author johanssl
 */
public class IDsearcher extends Feed{
     private final static String[] SPECS =  {"dummy", "180", "dummy"    ,"-1"   ,"txt;csv" ,CREDENTALS_NONE}; //2  
    public IDsearcher(Integer searchRad, String genID, int decimals, FusionOptions ops) {
      super(FeedConstructor.create(SPECS,GLOBAL,ops),ops);
      searchRad_m=searchRad;
      this.genID =genID;//base name for new information source
      genID_decimals=decimals;//decimals used to round-up coordinates in name id
      this.primaryMinerDir="";
    }

    @Override
    public void read(DataPack dp, RunParameters p, DataBase DB, MapPack mp) {
        
    }

    @Override
    public ArrayList<String> store() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean writesInitially() {
        return false;
    }
    @Override
    public boolean globalInitially() {
        return false;
    }
    @Override
    public String customArgumentInitially(ArrayList<String> areaNames) {
        return "";
    }
    @Override
    public boolean readsInitially() {
        return false;
    }
}
