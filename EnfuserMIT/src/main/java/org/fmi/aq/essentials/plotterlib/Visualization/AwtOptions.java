/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;

/**
 * This class holds awt Colors and other awt elements from VisualOptions.
 * @author johanssl
 */
public class AwtOptions {
   
    public final HashMap<Integer, Color> customCols= new HashMap<>();
    public final HashMap<Integer, Color> mask_categorical_cols = new HashMap<>();
    public final HashMap<Integer, Color> mask_surf_cols = new HashMap<>();
    public final HashMap<Integer, Color> mask_func_cols = new HashMap<>();
    
    public BufferedImage smallPic=null;
    
 
  public AwtOptions(VisualOptions vops) {
      //custom color hash
      for (Integer key:vops.customColors.keySet()) {
          int[] v = vops.customColors.get(key);
          this.customCols.put(key,new Color(v[0],v[1],v[2],v[3]));
      }
      
      //categorical hash
      for (Integer key:vops.mask_categorical_cols.keySet()) {
          int[] v = vops.mask_categorical_cols.get(key);
          this.mask_categorical_cols.put(key,new Color(v[0],v[1],v[2],v[3]));
      }
      
      //surface hash
      for (Integer key:vops.mask_surf_cols.keySet()) {
          int[] v = vops.mask_surf_cols.get(key);
          this.mask_surf_cols.put(key,new Color(v[0],v[1],v[2],v[3]));
      }
      
      //functional hash
      for (Integer key:vops.mask_func_cols.keySet()) {
          int[] v = vops.mask_func_cols.get(key);
          this.mask_func_cols.put(key,new Color(v[0],v[1],v[2],v[3]));
      }
      
      if (vops.smallPic!=null) {
          this.smallPic = (BufferedImage)vops.smallPic;
      }
      
  }

    public Color fontColor() {
       return this.customCols.get(VisualOptions.I_FONT_COLOR);
    }
    
    public Color getInverseFontColor() {
    Color c = fontColor();
    int rInv = 255 - c.getRed();
    int gInv = 255 - c.getGreen();
    int bInv = 255 - c.getBlue();
    return new Color(rInv, gInv, bInv);
}

    public Color getCustomColor(int key) {
       return this.customCols.get(key);
    }
     
}
