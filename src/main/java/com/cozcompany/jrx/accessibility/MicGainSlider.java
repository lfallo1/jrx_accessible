/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import java.awt.Component;

/**
 * Concrete class represents a transceiver microphone gain control as a slider.
 * @author Coz
 */
public class MicGainSlider extends RWSlider {
    
    public MicGainSlider(JRX_TX parent) {
        super(parent, "L", "MICGAIN", 50 /* percent */);        
    }

    /**
     * Given the rigcaps reply, determine if this control capability exists.
     * @param source 
     * @return boolean true when capability exists.
     */
    public boolean enableIfCapable(String source){
        String search = "(?ism).*^Set level:.*?MICGAIN\\(";
        boolean hasLevelValue = true; // This control has Level Value:
        return enableCap(source, search, hasLevelValue);
    }
}
