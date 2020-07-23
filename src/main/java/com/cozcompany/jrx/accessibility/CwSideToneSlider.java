/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.accessibility.AccessibleContext;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 *
 * @author Coz
 */
public class CwSideToneSlider extends RWSlider 
        implements MouseWheelListener, ChangeListener, KeyListener {
    JRX_TX myParent;
    final int INITIAL_VALUE = 600;
    
    
    public CwSideToneSlider(JRX_TX aParent) {
        super(aParent, "L", "CWPITCH");
        myParent = aParent;
        setMajorTickSpacing(300);
        setMinorTickSpacing(100);
        setPaintTicks(true);
        setPaintLabels(true);
        setMinimum(300);
        setMaximum(900);
        AccessibleContext context = getAccessibleContext();
        context.setAccessibleName("CW Side tone frequency");
        context.setAccessibleDescription("Adjusts from 300 to 900 Hertz");
        setupOnce();
        
    }
    

    public void setupOnce() {
        addMouseWheelListener(this);
        addChangeListener(this);
        this.addKeyListener(this);
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

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == (KeyEvent.VK_RIGHT)) {
            level = getValue();
            level += 10;
            setValue((int)level);
        } else if (e.getKeyCode() == (KeyEvent.VK_LEFT)) {
            level = getValue();
            level -= 10;
            setValue((int)level);            
        }        
    }


    @Override
    public void keyReleased(KeyEvent e) {
    }
    
}
