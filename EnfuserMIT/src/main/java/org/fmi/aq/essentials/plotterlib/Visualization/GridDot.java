/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import org.fmi.aq.enfuser.datapack.source.DBline;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.essentials.geoGrid.Boundaries;


/**
 *
 * @author johanssl
 */
public class GridDot {

    public double lat;
    public double lon;
    String text;
    public float value;
    private Float outerValue=null;
    private boolean asDot=false;
    public boolean textAlso =false;
    
    private double dotSizePercent=1;
    int typeInt;
    public GridDot(int typeInt,double lat, double lon, String text,
            float value, Double dotSize_percent) {
        this.typeInt = typeInt;
        this.lat = lat;
        this.lon = lon;
        this.text = text;
        this.value =value;
        if (dotSize_percent!=null && dotSize_percent> 0) {
            dotSizePercent = dotSize_percent;
            asDot = true;
        }
    }
    
    public GridDot(int typeInt,double lat, double lon, String text,
            float value,int index) {
       this(typeInt,lat, lon,text,value, VisualOptions.getValueDotSize(index));
    }
    
    public void setAsDot(int index) {
        Double d =  VisualOptions.getValueDotSize(index);
        if (d!=null) {
             this.dotSizePercent =d;
             asDot =true;
        } else {
            asDot =false;
        }
    }
    
    static void drawDots(FigureData aThis, Graphics2D g2d, Color fontc, Color fontc_inv) {
        //coordinated strings (could be converted into colored dots)  
        if (aThis.dots != null) {
            Boundaries reg = aThis.reg;
            VisualOptions vops = aThis.ops;
            for (GridDot gl : aThis.dots) {
                
                double lat = gl.lat;
                double lon = gl.lon;
                int x = (int) ((lon - reg.lonmin) / (reg.lonmax - reg.lonmin) * aThis.trueW);
                int y = (int) ((reg.latmax - lat) / (reg.latmax - reg.latmin) * aThis.trueH);
                
                if (gl.asDot) {
                    
                    
                    int rad;
                    if (gl.outerValue!=null) {
                        rad =  gl.getDotRadius(aThis);
                        rad+=0.4*rad;
                        g2d.setPaint(gl.assessOuterDotColor(aThis));
                        g2d.fillOval(x-rad/2, y-rad/2, rad, rad);
                        g2d.setPaint(fontc);//oval border, switch color
                        g2d.drawOval(x-rad/2, y-rad/2, rad, rad);
                    }
                    
                    rad = gl.getDotRadius(aThis);
                    Color valueDotCol = gl.assessDotColor(aThis);
                    g2d.setPaint(valueDotCol);
                    g2d.fillOval(x-rad/2, y-rad/2, rad, rad);
                    
                    g2d.setPaint(fontc);//oval border, switch color
                    g2d.drawOval(x-rad/2, y-rad/2, rad, rad);
                    g2d.fillRect(x, y, 1, 1);//center pixel
    
                }
                
                if (!gl.asDot || gl.textAlso) {//as text
                    String text =gl.text;
                    g2d.setPaint(fontc);
                    int fontSize = (int) (aThis.fontSize * 0.75);
                        g2d.setFont(new Font(vops.font, vops.fontStyle, fontSize));

                        //adjust text down by 0.5 font size. Now a character string <-- points directly at the intended target.
                        int yDown = (int) (fontSize * 0.5);

                        if (vops.font3D) {
                            g2d.setPaint(fontc_inv);
                            GraphicsRotator.setRotatedText(g2d, x + 1, y + yDown + 1, 0, text); //inverse color with a one pixel offset
                            g2d.setPaint(fontc);
                        }
                        GraphicsRotator.setRotatedText(g2d, x, y + yDown, 0, text);
                }
 
        }//for dots
        }
    }

    public GridDot(float lat, float lon, String dot) {
        this(0,lat,lon,dot,0,null);
    }

    public GridDot(float[] latlon, String string) {
          this(0,latlon[0],latlon[1],string,0,null);
    }


    
    
    
    
       /**
     * Try to convert a text String into value, which in turn will be turned to
     * a Color based on the color progression set in FigureData. In case the
     * string value has some common Enfuser patters (such as 'µgm-3') the parser
     * can clear these automatically.
     *
     * @param text input String that should be a value.
     * @param ops options
     * @return color from the figure's color palette that mathes with the parsed
     * value. Return null in case 'dot' feature has not been enabled in
     * VisualOptions or the parse fails.
     */
    private Color assessDotColor(FigureData aThis) {
            return aThis.getColor(this.value);
    }
    
        private Color assessOuterDotColor(FigureData aThis) {
            return aThis.getColor(this.outerValue);
    }


    /**
     * Estimate proper dot size (radius) to be drawn to graphics.
     * @return radius in terms of pixels.
     */
    private int getDotRadius(FigureData aThis) {

        int rads = (int) (Math.sqrt(aThis.trueH * aThis.trueW) * this.dotSizePercent / 2);
        if (rads < 5) {
            rads = 5;//lower limit
        }
        if (rads > 25) {
            rads = 25;//limit, this is after all, a 'dot'
        }
        return rads;
    }

    public boolean dottable() {
       return this.asDot;
    }

    public void qualityDotSizeMod(DataBase DB, String ID) {
        if (!this.dottable()) return;
        if (DB==null ||ID ==null) return;
        DBline dbl = DB.get(ID);
        if (dbl!=null) {
            qualityDotSizeMod(dbl.varScaler(this.typeInt)); 
        }
    }

    public void scaleDotSize(double d) {
        if (d<0.1) d =0.1;
        if (this.dottable()) this.dotSizePercent*=d;
    }

    public void qualityDotSizeMod(float imprec) {
         if (!this.dottable()) return;
        if (imprec <1) imprec =1;
        double scaler = 4.0/imprec;
        if (scaler >1) scaler=1;
        if (scaler < 0.4) scaler =0.4;
        this.scaleDotSize(scaler);
    }

    public void addOuterValue(double pred) {
        this.outerValue =(float)pred;
    }
    
    
}
