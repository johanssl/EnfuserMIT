/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feeds;

import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.geoGrid.Boundaries;

/**
 *
 * @author johanssl
 */
public class FeedConstructor {
    
    
    public final static int IND_CREDENTIAL_REQ= 5;//define type of credentials required for the feed
    public final static String CREDENTALS_NONE = "NONE";//no credentials required
    public final static String CREDENTALS_APIK = "APIK";//api-key
    public final static String CREDENTALS_UP = "UPWD";//user-password
    
    
    public final String argTag;
    public String argLine;
    public int ticker_min=60;
    public final String feedDirName;
    public final String[] formats;
    public final Boundaries relevanceBounds;
    public final String apikRequirement;
    public final int checkSpanH;
    
    
    public final static Boundaries GLOBAL = new Boundaries(-90,90, -180,180);
    public final static Boundaries FIN = new Boundaries(59.4,68, 20,29.5);
     public final static Boundaries HEL = new Boundaries(59.9,60.4, 24.5,25.3);
    
    public final static Boundaries EU = new Boundaries(30,70, -13,45);
    public final static Boundaries CN = new Boundaries(19,50, 73,134);
    
    public final static Boundaries BALTIC= new Boundaries(51,70, 4,34);
    public final static Boundaries INDIA = new Boundaries(6,33, 67,97);
    
    
    //{"AllObs", "30", ARG_TAG   ,"2"    ,".txt;.gml" , CREDENTALS_NONE};
    private final static int DIRNAM_I =0;
    private final static int TICKER_I =1;
    private final static int TAG_I =2;
    private final static int CHECKH_I =3;
    private final static int FORM_I =4;
    private final static int CREDS_I =5;
    
    
     public static FeedConstructor create(String[] specs,Boundaries applicableRegion, FusionOptions ops) {
         String argLine = null;
         if (ops!=null) {
            argLine = ops.getArguments().getExtended(specs[TAG_I]); 
         }
         
         return new FeedConstructor(
          specs[DIRNAM_I],  
          Integer.valueOf(specs[TICKER_I]),
          specs[TAG_I],
          Integer.valueOf(specs[CHECKH_I]),
          specs[FORM_I].split(";"),
          specs[CREDS_I],
          argLine,
          applicableRegion
         );
     }        
    
    public FeedConstructor(
            String feedDirName,
            int ticker_min,
            String argTag,
            int checkH,
            String[] formats,
            String apikRequirement,
            String argLine,
            Boundaries relevanceBounds) {
        
        this.argTag = argTag;
        this.argLine = argLine;
        this.ticker_min = ticker_min;
        this.feedDirName = feedDirName;
        this.formats = formats;
        this.relevanceBounds = relevanceBounds;
        this.apikRequirement = apikRequirement;
        this.checkSpanH = checkH;
        
    }

    
}
