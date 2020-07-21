/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import com.cozcompany.jrx.accessibility.JRX_TX;
import com.cozcompany.jrx.accessibility.RWSlider;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.accessibility.AccessibleContext;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * This morse code key speed slider is set for IC-7100.
 * @author Coz
 */
public class CwSpeedSlider extends RWSlider 
        implements MouseWheelListener, ChangeListener {
    JRX_TX myParent;
    final int INITIAL_VALUE = 15;
    
    
    public CwSpeedSlider(JRX_TX aParent) {
        super(aParent, "L", "KEYSPD");
        myParent = aParent;
        setMajorTickSpacing(10);
        setMinorTickSpacing(1);
        setPaintTicks(true);
        setPaintLabels(true);
        setMinimum(5);
        setMaximum(40);
        AccessibleContext context = getAccessibleContext();
        context.setAccessibleName("INTERNAL CW KEYER SPEED");
        context.setAccessibleDescription("Adjusts from 5 to 40 WPM");
        setupOnce();
        
    }
    

    public void setupOnce() {
        addMouseWheelListener(this);
        addChangeListener(this);
        super.initial = INITIAL_VALUE;
        setValue(initial);
    }
    
    @Override
    public void stateChanged(ChangeEvent evt) {
        writeValue(false);
    }

    /**
     * Write the slider value to the rig without conversion.
    */
    @Override
    public void writeValue(boolean force) {
        if (super.commOK  || force) {
            if (!myParent.inhibit && !localInhibit && isEnabled()) {
                level = getValue();
                if (force || level != oldLevel) {
                    String com = String.format("%s %s %d", prefix.toUpperCase(), token, (int)level);
                    parent.sendRadioCom(com, 0, true);
                    oldLevel = level;
                }
            }
        }
    }
    
    /**
     * The values from the radio agree with the slider values so no conversion is
     * necessary.
     */
    @Override
    public void readConvertedValue() {
        level = readValue();
        if (level != errorValue && level != oldLevel) {
            setValue((int)level);
            oldLevel = level;
        }
    }
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        int v = e.getWheelRotation();
        int iv = (v < 0) ? -1 : 1;
        setValue(getValue() + iv);
    }
    
}
