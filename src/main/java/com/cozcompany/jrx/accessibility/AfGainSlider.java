/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

/**
 *
 * @author Coz
 */
public class AfGainSlider extends RWSlider {
    private double previousLevel = 0.0;
    
    public AfGainSlider(JRX_TX frame) {
        super(frame, "L", "AF", 0);        
    }
    /**
     * Disable and gray out the AF GAIN slider and set current radio level to zero
     * as a software generated mute function.  Since no rig that I know of has a
     * mute function, maybe this is ok for the present.
     */
    public void mute() {       
        previousLevel = level;
        writeDirect(0.0); 
        this.setEnabled(false);
    }
    /**
     * Write the previous level to the AF GAIN Slider, set the radio with the
     * previous value and then enable the control...which may actually read the
     * radio value.
     */
    public void unmute() {       
        this.setEnabled(true);
        writeDirect(previousLevel);
    }
    
    private void writeDirect(double aLevel) {
        if (!parent.inhibit && !localInhibit && isEnabled()) {
            String com = String.format("%s %s %.2f", 
                                    prefix.toUpperCase(), token, aLevel);
            parent.sendRadioCom(com, 0, true);
            oldLevel = aLevel;
        }          
    }    
}
