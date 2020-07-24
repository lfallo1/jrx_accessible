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
    }

    public void updateSwr() {
        double currentSwr = readValue();
        if (currentSwr > 3.0) setForeground(Color.RED);
        else setForeground(Color.BLACK);
        String valueString = String.format("SWR  %3.2f", currentSwr);
        this.setText(valueString);
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
