/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Rectangle;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.ListIterator;
import javax.swing.JPanel;


/**
 *
 * @author 
 */
public class FreqDisplay extends JPanel implements HierarchyBoundsListener {
    ArrayList<FreqDigit> freqDigits = null;
    JPanel digitsParent;
    JRX_TX appFrame;
    Rectangle space;

    long sv_freq = 145000000l; 
    long digitFrequency;
    int  arrayIndex;
    final int  DIGITS_QTY = 10;
    
    
    
    public FreqDisplay(JRX_TX aFrame, JPanel parent) {
        digitsParent = parent;
        appFrame = aFrame;
        space = parent.getBounds();
        setBounds(space);
    }
    
    public long getFreq() {
        return sv_freq;
    }
        
    public void initDigits() {
        removeAll();
        digitsParent.setBackground(Color.black);
        setBackground(Color.black);
        setLayout(new FlowLayout());
        // Scrunch digits together horizontally.
        ((FlowLayout) getLayout()).setHgap(0);
        freqDigits = new ArrayList<>(DIGITS_QTY);
        arrayIndex = DIGITS_QTY-1;
        FreqDigit fd, ofd;
        ofd = setupDigits( 4, null, 1); 
        add(new FreqDigit(appFrame, -1 , 1));
        ofd = setupDigits( 3, ofd, 1);
        setupDigits( 3, ofd, 0.7f);       
        GridBagConstraints gridBagConstraints;
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        digitsParent.add(this, gridBagConstraints);
        adjustSize();
    }

    /**
     * Create frequency digits in descending order of decade.
     * @param n  is number of frequency digits in this group.
     * @param ofd previous frequency digit used for setting carry
     * @param size a size scale factor 0.0 to 1.0
     * @return last created frequency digit
     */
    private FreqDigit setupDigits( int n, FreqDigit ofd, float size) {
        for (int i = 0; i < n; i++) {
            FreqDigit fd = new FreqDigit(appFrame, 0, size);
            freqDigits.add(fd);
            add(fd);
            if (ofd != null) {
                fd.setCarry(ofd);
            }
            ofd = fd;
        }
        return ofd;
    }
    
    protected void setFrequency() {
        frequencyToDigits(sv_freq);
    }

    /**
     * Set the FreqDisplay digits to the given frequency in Hertz; then set radio
     * frequency.
     * @param v given frequency in Hertz
     * Note: the array freqDigits is in descending order of decade.
     */
    public void frequencyToDigits(long v) {
        sv_freq = v;
        digitFrequency = sv_freq;
        ListIterator<FreqDigit> revIter = freqDigits.listIterator(freqDigits.size());
        while (revIter.hasPrevious()) {
            FreqDigit fd = revIter.previous();
            fd.setDigit(v % 10);
            fd.setBright(v != 0);
            //fd.setBright(v != 0);
            v /= 10;
        }
        if (appFrame.requestSetRadioFrequency(digitFrequency)) {
            sv_freq = digitFrequency;
        }
    }
    /**
     * Take the displayed frequency digits and assemble the frequency in Hertz;
     * then request the radio VFO frequency be set to that frequency value.
     * Note: the array freqDigits is in descending order of decade.
     * Note: sv_freq always holds the latest frequency commanded to the radio.
     */
    protected void digitsToFrequency() {
        if (appFrame.validSetup()) {
            digitFrequency = 0;
            for (FreqDigit fd : freqDigits) {
                digitFrequency = (digitFrequency * 10) + fd.getValue();
                fd.setBright(digitFrequency != 0);
            }
        }
        // Set the system frequency to the result.
        if (appFrame.requestSetRadioFrequency(digitFrequency)) {
            sv_freq = digitFrequency;
        }
    }
        
    protected void timerUpdateFreq() {
        if (digitFrequency >= 0 && digitFrequency != sv_freq) {
            frequencyToDigits(digitFrequency);
        }
    }

    
    private void adjustSize() {
        int fontSize = (int) (digitsParent.getWidth() / 6.5);
        for (Component c : getComponents()) {
            float fs = ((FreqDigit) c).fontScale;
            Font font = new Font("Monospace", Font.PLAIN, (int) (fontSize * fs));
            c.setFont(font);
        }
    }

    @Override
    public void ancestorMoved(HierarchyEvent e) {
        // Not expecting my ancestor to move....
        throw new UnsupportedOperationException("Not supported yet."); 
    }

    @Override
    public void ancestorResized(HierarchyEvent e) {
         adjustSize();
    }

    
}
