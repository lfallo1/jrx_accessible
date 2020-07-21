// ***************************************************************************
// *   Copyright (C) 2012 by Paul Lutus                                      *
// *   lutusp@arachnoid.com                                                  *
// *                                                                         *
// *   This program is free software; you can redistribute it and/or modify  *
// *   it under the terms of the GNU General Public License as published by  *
// *   the Free Software Foundation; either version 2 of the License, or     *
// *   (audibleTip your option) any later version.                                   *
// *                                                                         *
// *   This program is distributed in the hope that it will be useful,       *
// *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
// *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
// *   GNU General Public License for more details.                          *
// *                                                                         *
// *   You should have received a copy of the GNU General Public License     *
// *   along with this program; if not, write to the                         *
// *   Free Software Foundation, Inc.,                                       *
// *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
// ***************************************************************************
package com.cozcompany.jrx.accessibility;

import components.AgcListButton;
import components.CtcssListButton;
import components.RWListButton;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSlider;

/**
 * Each memory button represents an available storage for a VFO frequency and 
 * mode and has an internal state used for scan operations indicated by its 
 * foreground font color.
 * 
 * BLACK -
 * GREEN -
 * BLUE -
 * RED -
 * PURPLE - 
 * 
 * @author lutusp
 */
final public class MemoryButton extends JButton implements MouseListener {

    JRX_TX parent;
    String label;
    Timer defineButtonTimer = null;
    MouseEvent mouseEvent = null;
    int timeout = 1000;
    boolean defineButton = false;
    int skipDuringScan = 0;
    String visualTip = "<span color=\"green\">Click: read</span><br/>"+
            "<span color=\"blue\">Right-click: toggle skip in memory scan</span>"+
            "<br/><span color=\"red\">Click and hold 1 sec.: write</span>"+
            "<br/><span color=\"purple\">Right-click and hold 1 sec: erase</span>";
    String audibleTip = "Left Click to set VFO frequency. " +
                "Right click to toggle SKIP in memory scan. "+
                "Click and hold one second to store current VFO and mode. "+
                "Right click and hold one second to erase.";
    long frequency = -1;
    int mode;
    int filter;
    int agc;
    int stepSizeIndex = 0;
    int ctcss;
    int nb;
    int antenna;
    int preamp;
    int attenuator;
    final Color STATE_GREEN; 
    final Color STATE_RED;
    final Color STATE_BLUE;
    final Color STATE_BLACK;
    final Color STATE_GRAY;


    public MemoryButton(String lbl, JRX_TX p) {
        super(lbl);
        label = lbl;
        parent = p;
        STATE_GREEN = parent.darkGreen;
        STATE_RED = parent.darkRed;
        STATE_BLUE = parent.darkBlue;
        STATE_BLACK = Color.black;
        STATE_GRAY = Color.darkGray;
        setToolTipText(visualTip);
        getAccessibleContext().setAccessibleDescription(audibleTip);
        setup();
        reset();
        updateState();
    }

    private void setup() {
        addMouseListener(this);
    }

    protected void setButtonColor(Color col) {
        setForeground(col);
    }

    protected void reset() {
        frequency = -1;
        mode = -1;
        filter = -1;
        agc = -1;
        ctcss = -1;
        nb = -1;
        antenna = -1;
        preamp = -1;
        attenuator = -1;
    }

    protected void updateState(Color foreground) {
        setForeground((frequency >= 0) ? (skipDuringScan != 0)?Color.blue:foreground : Color.gray);
    }
    
    protected void updateState() {
        updateState(Color.black);
    }

    protected String getStateString() {
        String stateColorString = "GRAY";
        if (isEnabled()) {
            Color foreColor = getForeground();    
            if (foreColor == STATE_BLACK)  
                stateColorString = "BLACK";
            else if (foreColor == STATE_GREEN)
                stateColorString = "GREEN";
            else if (foreColor == STATE_RED)
                stateColorString = "RED";
            else if (foreColor == STATE_BLUE)
                stateColorString = "BLUE";
            else 
                stateColorString = "UNKNOWN"; 
        } else {
            // Button is not enabled.  Color is GRAY.
            stateColorString = "GRAY";
        } 
        return stateColorString;       
    }
    
    
    @Override
    public String getToolTipText(MouseEvent event) {
        String ms = parent.getMode();       
        String freq = (frequency >= 0) ? String.format("%.6f MHz %s", 
                (double) frequency / 1e6, ms) : "Undefined";
        // Change accessibleDescription.
        getAccessibleContext().setAccessibleDescription("button is "+
                getStateString() +" "+ freq + audibleTip);
        return String.format("<html>%s<br> %s</html>", freq, visualTip);
    }
    /**
     * This timer task determines if a mouse button was held down longer than the 
     * timeout value and if so, it stores the current Vfo frequency and operating
     * parameters as a memory channel for a left-click or it erases the memory
     * channel with a right-click.
     */
    private class DefineButton extends TimerTask {

        @Override
        public void run() {
            if (mouseEvent != null) {
                int button = mouseEvent.getButton();
                if (button == MouseEvent.BUTTON1) {
                    Beep.beep();
                    defineButton = true;
                    writeButton();
                } else if (button == MouseEvent.BUTTON3 && frequency >= 0) {
                    if (parent.askUser(String.format("Okay to erase memory %s?", label))) {
                        reset();
                    }
                }
                updateState();
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        //parent.pout("mouse pressed");
        mouseEvent = e;
        setForeground(Color.red);
        defineButton = false;
        defineButtonTimer = new java.util.Timer();
        defineButtonTimer.schedule(new DefineButton(), timeout);
        parent.getScopePanel().stopSweep(false);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        //parent.pout("mouse released");
        if (defineButtonTimer != null) {
            defineButtonTimer.cancel();
            defineButtonTimer = null;
        }
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (!defineButton) {
                readButton();
            }
        }
        else if (e.getButton() == MouseEvent.BUTTON3) {
           if (!defineButton) {
                skipDuringScan ^= 1;
            } 
        }
        updateState();
    }
    
    private void updateIfNeeded(RWListButton cc, int v) {
        int ov = cc.getSelectedIndex();
        if (ov != v) {
            try {
                cc.setSelectedIndex(v);
            }
            catch (Exception ex) {
                p("MemoryButton:updateIfNeeded() exception :"+ ex);
            }
        }

    }

    private void updateIfNeeded(JComboBox cc, int v) {
        int ov = cc.getSelectedIndex();
        if (ov != v) {
            try {
                cc.setSelectedIndex(v);
            }
            catch (Exception ex) {
                p("MemoryButton:updateIfNeeded() exception :"+ ex);
            }
        }

    }

    
    private void updateIfNeeded(JSlider sc, int v) {
        int ov = sc.getValue();
        if (ov != v) {
            sc.setValue(v);
        }
    }

    private void updateIfNeeded(JCheckBox cb, int v) {
        int ov = (cb.isSelected()) ? 1 : 0;
        if (ov != v) {
            cb.setSelected(v != 0);
        }
    }

    protected boolean readButton() {
        if (frequency >= 0) {
            parent.scanStateMachine.stopScan(false);
            parent.memoryCollection.sv_mostRecentButton = label;
            // always set bandwidth before mode
            updateIfNeeded((RWListButton)(parent.sv_ifFilterListButton), filter);
            // always set mode before frequency
            updateIfNeeded((RWListButton)(parent.sv_modesListButton), mode);
            parent.vfoState.writeFrequencyToRadioSelectedVfo(frequency);
            parent.vfoDisplay.frequencyToDigits(frequency);
            updateIfNeeded((CtcssListButton)parent.sv_ctcssListButton, ctcss);
            updateIfNeeded((AgcListButton)parent.sv_agcListButton, agc);
            updateIfNeeded(parent.sv_preampComboBox, preamp);
            updateIfNeeded(parent.sv_antennaComboBox, antenna);
            updateIfNeeded(parent.sv_attenuatorComboBox, attenuator);
            updateIfNeeded((RWListButton)parent.sv_scanStepListButton, stepSizeIndex);
            //updateIfNeeded(parent.sv_rfGainSlider, rfGain);
            //updateIfNeeded(parent.sv_squelchSlider, squelch);
            //updateIfNeeded(parent.sv_ifShiftSlider, ifShift);
            updateIfNeeded(parent.sv_blankerCheckBox, nb);
            return true;
        } else {
            Beep.beep();
            parent.tellUser("This memory button is undefined. To define it,\n"+
                            "press and hold for more than one second.");
            return false;
        }
    }

    private void writeButton() {
        parent.memoryCollection.sv_mostRecentButton = label;
        filter = ((RWListButton)parent.sv_ifFilterListButton).getSelectedIndex();
        mode = ((RWListButton)parent.sv_modesListButton).getSelectedIndex();
        frequency = parent.vfoState.getRxFrequency();
        ctcss = ((RWListButton)parent.sv_ctcssListButton).getSelectedIndex();
        agc = ((RWListButton)parent.sv_agcListButton).getSelectedIndex();
        preamp = parent.sv_preampComboBox.getSelectedIndex();
        antenna = parent.sv_antennaComboBox.getSelectedIndex();
        attenuator = parent.sv_attenuatorComboBox.getSelectedIndex();
        stepSizeIndex = ((RWListButton)parent.sv_scanStepListButton).getSelectedIndex();
        nb = (parent.sv_blankerCheckBox.isSelected()) ? 1 : 0;
        updateState();
    }

    public void defineFromString(String s) {
        try {
            String[] array = s.split(",");
            if (array.length == 11) {
                frequency = Long.parseLong(array[0]);
                mode = Integer.parseInt(array[1]);
                filter = Integer.parseInt(array[2]);
                agc = Integer.parseInt(array[3]);
                ctcss = Integer.parseInt(array[4]);
                stepSizeIndex = Integer.parseInt(array[5]);
                nb = Integer.parseInt(array[6]);
                preamp = Integer.parseInt(array[7]);
                antenna = Integer.parseInt(array[8]);
                attenuator = Integer.parseInt(array[9]);
                skipDuringScan = Integer.parseInt(array[10]);
                updateState();
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    @Override
    public String toString() {
        return String.format("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d",
                frequency,
                mode,
                filter,
                agc,
                ctcss,
                stepSizeIndex,
                nb,
                preamp,
                antenna,
                attenuator,
                skipDuringScan);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    private void p(String s) {
        System.out.println(s);
    }
}
