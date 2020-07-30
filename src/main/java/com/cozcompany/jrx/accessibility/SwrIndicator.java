/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import components.RWIndicator;
import java.awt.Color;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 *
 * @author Coz
 */
public class SwrIndicator extends RWIndicator 
        implements FocusListener, ChangeListener {
    JRX_TX myParent;
    
    public SwrIndicator(JRX_TX aParent) {
        super(aParent, "L", "SWR");
        
        setXLow(1.000);
        setXHigh(100.000);
        setYLow(1.000);
        setYHigh(100.000);       
        this.addFocusListener(this);
        this.setText("SWR unkown");
    }

    public void updateSwr() {
        double currentSwr = readValue();
        if (isEnabled()) {
            if (currentSwr > 3.0) setForeground(Color.RED);
            else setForeground(Color.BLACK);
            String valueString = String.format("SWR  %3.2f", currentSwr);
            this.setText(valueString);
        }
    }
    /**
     * Given the rigcaps reply, determine if this control capability exists.
     * @param source 
     * @return boolean true when capability exists.
     */
    public boolean enableIfCapable(String source){
        String search = "(?ism).*^Get level:.*?SWR\\(";
        boolean hasLevelValue = true; // This control has Level Value:
        return enableCap(source, search, hasLevelValue);
    }    
    
    @Override
    public void focusGained(FocusEvent e) {
        updateSwr();
    }

    @Override
    public void focusLost(FocusEvent e) {
    }

    /**
     * Not used at present.  Nice to see while tuning.
     * @param evt 
     */
    @Override
    public void stateChanged(ChangeEvent evt) {
        updateSwr();
    }
    
}
