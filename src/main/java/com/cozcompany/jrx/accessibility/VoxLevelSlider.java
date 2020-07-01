/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

/**
 * Slider represents the VOX level setting.
 * 
 * @author Coz
 */
public class VoxLevelSlider extends RWSlider {
    
    public VoxLevelSlider(JRX_TX parent) {
        super(parent, "L", "VOXGAIN", 50 /* percent */);
    }

    /**
     * Given the rigcaps reply, determine if this control capability exists.
     * @param source 
     * @return boolean true when capability exists.
     */
    public boolean enableIfCapable(String source){
        String search = "(?ism).*^Set level:.*?VOXGAIN\\(";
        boolean hasLevelValue = true; // This control has Level Value:
        return enableCap(source, search, hasLevelValue);
    }    
    
}
