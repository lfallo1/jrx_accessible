/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import com.cozcompany.jrx.accessibility.RWSlider;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseWheelListener;
import javax.accessibility.AccessibleContext;
import javax.swing.event.ChangeListener;

/**
 * The NR slider shows values from 0 to 100 which are scaled to 0 to 1 for the
 * IC-7100.  Those are the default scales for the RWSlider class.
 * 
 * @author Coz
 */
public class DspSlider extends RWSlider implements MouseWheelListener, ChangeListener, KeyListener {
    JRX_TX myParent;
    int INITIAL_VALUE; // percent

    public DspSlider(JRX_TX aParent) {
        super(aParent, "L", "NR", 10);
        myParent = aParent;
        INITIAL_VALUE = 10;
        AccessibleContext context = getAccessibleContext();
        context.setAccessibleName("Noise Reduction Level");
        context.setAccessibleDescription("Adjusts from 0 to 100 percent");
        setupOnce();        
    }
    
    public void setupOnce() {
        this.addKeyListener(this);
        super.initial = INITIAL_VALUE;
        setValue(initial);
    }

    /**
     * Given the rigcaps reply, determine if this control capability exists.
     * @param source 
     * @return boolean true when capability exists.
     */
    public boolean enableIfCapable(String source) {
        String search = "(?ism).*^Set level:.*?NR\\(";
        boolean hasLevelValue = true; // This control has Level Value:
        return enableCap(source, search, hasLevelValue);
    }    
    
    @Override
    public void keyTyped(KeyEvent e) {
    }

    
    /**
     * Respond to left and right arrow keys to decrement or increment by 5% each
     * key press.
     * @param e 
     */
    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == (KeyEvent.VK_RIGHT)) {
            level = getValue();
            level += 0.05;
            setValue((int)level);
        } else if (e.getKeyCode() == (KeyEvent.VK_LEFT)) {
            level = getValue();
            level -= 0.05;
            setValue((int)level);            
        }        
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }
    
}
