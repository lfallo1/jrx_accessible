/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

/**
 * Slider represents the RF output power for the selected channel.  Some radios
 * have the capability to set different power levels for each channel.  The ID-51
 * has two channels, A and B.  Each can be on a different band and have a 
 * different mode and power output setting.  For example, channel A could be a
 * repeater on 440 band with a negative repeater offset running very low power.
 * Channel B could be on the 2Meter band with plus offset on D-Star and full power.
 * The two channels are independent and can both receive at the same time.  Only
 * the selected channel can transmit.
 * 
 * Requirement:  Read rig power upon changing channels.  Do not force the same
 * power output setting on both channels. @TODO Coz
 * 
 * @author Coz
 */
public class RfPowerSlider extends RWSlider {
    
    public RfPowerSlider(JRX_TX parent) {
        super(parent, "L", "RFPOWER", 50 /* percent */);
    }

    /**
     * Given the rigcaps reply, determine if this control capability exists.
     * @param source 
     * @return boolean true when capability exists.
     */
    public boolean enableIfCapable(String source){
        String search = "(?ism).*^Set level:.*?RFPOWER\\(";
        boolean hasLevelValue = true; // This control has Level Value:
        return enableCap(source, search, hasLevelValue);
    }    
}
