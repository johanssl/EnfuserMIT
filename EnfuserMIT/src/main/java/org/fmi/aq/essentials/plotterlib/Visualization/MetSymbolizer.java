/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import static java.lang.Math.PI;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;

/**
 *
 * @author johanssl
 */
public class MetSymbolizer {


    /**
     * This method adds meteorological symbols to image, as defined in
     * AreaFusion.MET_SYMOBLS First the symbols are loaded from file and then,
     * rain/temperature/cloudiness values are evaluated and corresponding
     * symbols are placed on top of the image.
     *
     * @param fd The FigureData for which the process is done. The object must
     * have met-data in GeoGrid within.
     * @return transparent BufferedImage for added metSymbols.
     */
    public static BufferedImage createMetSymbolsLayer(FigureData fd) {
        if (!fd.ops.metSymbols || fd.metGrids==null) {
            return null;
        }

        AwtOptions awt = fd.ops.getAWTops();
        String folder = fd.ops.visualDataDir() + "symbols" + Z;
        ArrayList<GeoGrid> grids = fd.metGrids;

        int H = fd.trueH - fd.HEIGHT_ADD_BANNER - fd.HEIGHT_ADD_COLBAR;
        int W = fd.trueW;

        double dlat = fd.getBounds().getDlat(H, W);
        double dlon = fd.getBounds().getDlon(H, W);

        int WIND_DIR_N = -1;
        int WIND_SPEED_E = -1;

        boolean NEcomponents = false;

        for (int i = 0; i < grids.size(); i++) {
            if (grids.get(i) == null) {
                continue;
            }
            if (grids.get(i).varName.contains("indSpeed") || grids.get(i).varName.contains("IND_SPEED") || grids.get(i).varName.contains("ind_speed")) {
                WIND_SPEED_E = i;
                // EnfuserLogger.log(Level.FINER,MetSymbolizer.class,"WindSpeed found.");
            }
            if (grids.get(i).varName.contains("indDir") || grids.get(i).varName.contains("IND_DIR") || grids.get(i).varName.contains("ind_dir")) {
                WIND_DIR_N = i;
                //  EnfuserLogger.log(Level.FINER,MetSymbolizer.class,"WindDir found.");
            }
        }

        //check NEcomponents if speed and dir indices have not been initialized
        if (WIND_DIR_N == -1 && WIND_SPEED_E == -1) {
            for (int i = 0; i < grids.size(); i++) {
                if (grids.get(i) == null) {
                    continue;
                }
                if (grids.get(i).varName.contains("ind_n") || grids.get(i).varName.contains("ind_N")) {
                    WIND_DIR_N = i;
                    NEcomponents = true;
                    // EnfuserLogger.log(Level.FINER,MetSymbolizer.class,"WindSpeed found.");
                } else if (grids.get(i).varName.contains("ind_E") || grids.get(i).varName.contains("ind_e")) {
                    WIND_SPEED_E = i;
                    NEcomponents = true;
                    //  EnfuserLogger.log(Level.FINER,MetSymbolizer.class,"WindDir found.");
                }
            }
        }

        BufferedImage arrow = null;
        Image sizedArrow = null;
        //BufferedImage rotArr = null;
        try {
            
        if (fd.ops.slimWindVectorField) {    
                arrow = ImageIO.read(new File(folder + "slim_arrow.png"));
        } else if (fd.ops.colorScheme == FigureData.COLOR_GREEN_TO_VIOLET) {
                arrow = ImageIO.read(new File(folder + "arrow_cyan.png"));
        } else {
                arrow = ImageIO.read(new File(folder + "arrow.png"));
        }

        } catch (IOException e) {
            EnfuserLogger.log(Level.FINER,MetSymbolizer.class,"Couldn't load " + folder + "arrow.png");
        }

        BufferedImage newGridImg = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = newGridImg.createGraphics();

        Font f_inGrid = new Font(fd.ops.font, Font.PLAIN, fd.fontSize);
        g2d.setColor(awt.fontColor());

        int hStart = (int) (0.12 * H);
        int hEnd = (int) (0.95 * H);

        int wStart = (int) (0.05 * W);
        int wEnd = (int) (0.98 * W);

        if (fd.ops.colBarStyle == VisualOptions.CBAR_TRANSP) {
            hEnd = (int) (0.85 * H);
        }
        
        if (fd.ops.slimWindVectorField) {
             hStart = (int) (-0.05 * H);
             hEnd = (int) (1.05 * H);
             wStart = (int) (-0.05 * W);
             wEnd = (int) (1.05 * W);
        }

        // WIND VECTORS AND SYMBOLS=================
        int hSparsity = (int) (H * fd.ops.metSymbolSparsity * 0.7);
        int wSparsity = (int) (W * fd.ops.metSymbolSparsity * 0.7);
        int stringMods =2;//larger value, less wind speed String draws at the arror symbols.
        if (fd.ops.slimWindVectorField) {//more symbol arrows (but slim versions), less text. 
            hSparsity/=3;
            if (hSparsity<0) hSparsity =1;
            wSparsity/=3;
            if(wSparsity<0) wSparsity =1;
            stringMods = Integer.MAX_VALUE;
        }

        if (WIND_DIR_N != -1 && WIND_SPEED_E != -1) {

            int hcount = -1;
            int wcount = -1;
            for (int h = hStart; h < hEnd; h += hSparsity) {
                hcount++;
                for (int w = wStart; w < wEnd; w += wSparsity) {
                    wcount++;
                    double lat = fd.getBounds().latmax - h * dlat;  // graphics latitude works in INVERSE ways. Usually 0,0 would be minlon, maxlat, but here it's latmin,lonmin
                    double lon = fd.getBounds().lonmin + w * dlon;

                    Double windDir = null;
                    Double windSpeed = null;

                    try {
                        windDir = grids.get(WIND_DIR_N).getValue_closest(lat, lon);//can also be the N compoment
                        windSpeed = grids.get(WIND_SPEED_E).getValue_closest(lat, lon);//can also be the E component

                        if (NEcomponents) {//these are actually the N and E components of wind => conversion needed
                            float[] dirV = NE_toDirSpeed(windDir.floatValue(), windSpeed.floatValue());
                            windDir = (double) dirV[0];
                            windSpeed = (double) dirV[1];
                        }

                    } catch (NullPointerException exx) {
                    } catch (ArrayIndexOutOfBoundsException exx) {
                    }

                    // draw wind symbol only if correct values was found
                    if (windDir != null && windSpeed != null) {
                        float scaler = fd.metSymbolScaler();
                        sizedArrow = modifyWindArrow(arrow, windSpeed, scaler, fd.ops.slimWindVectorField);
                        g2d = GraphicsRotator.setRotatedImage(g2d, w, h, windDir, sizedArrow);
                         
                        try {
                            if (hcount % stringMods == 0 && wcount % stringMods ==0) {
                                g2d.setFont(f_inGrid);
                                String str = editPrecision(windSpeed, 1) + " m/s";
                                g2d.drawString(str, (int) (w + sizedArrow.getWidth(null) * 0.5), (int) (h + sizedArrow.getHeight(null) * 0.5));
                                if (fd.ops.font3D) {
                                    g2d.setPaint(awt.getInverseFontColor());
                                    g2d.drawString(str, (int) (1 + w + sizedArrow.getWidth(null) * 0.5), (int) (1 + h + sizedArrow.getHeight(null) * 0.5)); //inverse color with a one pixel offset
                                    g2d.setPaint(awt.fontColor());
                                }
                            }
    
                            
                        } catch (ArrayIndexOutOfBoundsException e) {
                            e.printStackTrace();
                        } //RHEL 7.4 problem with fonts could occur  

                        //   W----x----W----x----W
                        //   x----W----x----W----x
                        // the modulos will cause such cross-placement of wind information (W), x = empty
                    }

                }// for w
            }// for h
        }

        // Symbol and additional text
        WeatherDetails wdet = new WeatherDetails(fd);
        wdet.setContent();
        
        int fs = (int) (fd.fontSize * 0.75);
        Font f = new Font(fd.ops.font, Font.PLAIN, fs);
        int texth = (int) (H * 0.99);
        if (fd.ops.colBarStyle == VisualOptions.BANNER_TRANSP) {
            texth -= fd.CBAR_HEIGHT;
        }

        int w = (int) (W * 0.01); // left
        if (wdet.symbol != null) {
            g2d.drawImage(wdet.symbol, w, texth - wdet.symbol.getHeight(), null);
            w += wdet.symbol.getWidth();
        }
        for (String s : wdet.text) {

            g2d.setFont(f);

            try {
                if (fd.ops.font3D) {
                    g2d.setPaint(awt.getInverseFontColor());
                    g2d.drawString(s, w + 1, texth + 1); //inverse color with a one pixel offset
                    g2d.setPaint(awt.fontColor());
                }
                g2d.drawString(s, w, texth);
            } catch (ArrayIndexOutOfBoundsException e) {
                e.printStackTrace();
            } //RHEL 7.4 problem with fonts could occur

            texth -= (int) (fs * 1.1);
        }

        g2d.dispose();
        return newGridImg;

    }
    public static final double min_v = 1;
    public static final double max_v = 12;

    public static Image modifyWindArrow(BufferedImage arrow, double windSpeed,
            float resolutionFactor, boolean slim) {

        if (windSpeed < min_v) {
            windSpeed = min_v;
        }
        if (windSpeed > max_v) {
            windSpeed = max_v;
        }

        double h_scaler;
        double w_scaler;

        double MAX_W = 0.5;
        double MIN_W = 0.4;

        double MAX_H = 1.0;
        double MIN_H = 0.4;
        if (slim) {
            MIN_H =0.25;
            MAX_H = 0.9;
            
            MAX_W = 0.35;
            MIN_W =0.25;
        }
        

        double frac = (windSpeed - min_v) / (max_v - min_v);

        w_scaler = MIN_W + frac * (MAX_W - MIN_W);
        h_scaler = MIN_H + frac * (MAX_H - MIN_H);

        Image img = arrow.getScaledInstance((int) (resolutionFactor * arrow.getWidth() * w_scaler),
                (int) (resolutionFactor * arrow.getHeight() * h_scaler), Image.SCALE_SMOOTH);
        return img;
    }


    public static float[] NE_toDirSpeed(float sin, float cos) {
        float v = (float) Math.sqrt(sin * sin + cos * cos);
        if (v < 0.1) {
            v = 0.1f;//otherwise NaN, not good. if the wind speed is zero the direction will not matter in any case!
        }
        sin /= v;
        cos /= v;
        float wd;
        if (sin >= 0 && cos >= 0) {//I
            double arad = Math.asin(sin);
            float angle = (float) radIntoDeg(arad);
            wd = 90 - angle;

        } else if (sin <= 0 && cos >= 0) {// IV
            sin *= -1;//now it's positive.
            double arad = Math.asin(sin);
            float angle = (float) radIntoDeg(arad);
            wd = 90 + angle;

        } else if (sin <= 0 && cos <= 0) {//III

            sin *= -1;//now it's positive.
            double arad = Math.asin(sin);
            float angle = (float) radIntoDeg(arad);
            wd = 270 - angle;

        } else if (sin >= 0 && cos <= 0) {

            double arad = Math.asin(sin);
            float angle = (float) radIntoDeg(arad);
            wd = 270 + angle;

        } else {
            EnfuserLogger.log(Level.FINER,MetSymbolizer.class,
                    "ERROR: WindDir re-transform fail! sin = " + sin + ", cos = " + cos);
            wd = 0;
        }

        wd += 180;//with new versions non-inverted N-E components are used so this must be rotated 180 degrees!
        if (wd > 360) {
            wd -= 360;
        }

        return new float[]{wd, v};

    }

    public static double radIntoDeg(double r) {
        return (r / 2 / PI * 360);
    }

}
