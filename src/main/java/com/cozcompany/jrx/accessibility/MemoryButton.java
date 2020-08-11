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
import components.ModesListButton;
import components.RWListButton;
import java.awt.Color;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSlider;

/**
 * Each memory button represents an available storage for a VFO frequency, mode,
 * and other parameters including an internal state used for scan operations 
 * indicated by its foreground font color:
 * BLACK - programmed memory
 * GREEN - currently part of a scan group
 * BLUE - currently part of a scan group but skipped
 * RED - currently part of a scan group and selected
 * GRAY - unprogrammed memory
 * A 'memory BUTTON' is programmed using the current transceiver control settings
 * including the VFO frequency and mode.
 * 
 * Memory buttons, when left-clicked, immediately set the transceiver controls to
 * the values persisted by the button, and those controls send the settings to 
 * the radio.
 * 
 * Groups of memory buttons, delimited by unprogrammed buttons, are recognized as
 * channels to be scanned.
 * 
 * A group of two memory buttons is recognized as frequency scan limits where the
 * range endpoints are the button frequencies and the step size is selected by a 
 * control.   The Scope tab uses this binary group for the sweep frequency limits.
 * 
 * {@code
 * From Hamlib:rig.h
 * typedef enum {
 *         RIG_MODE_NONE =         0,      
 *         RIG_MODE_AM =           (1<<0), 
 *         RIG_MODE_CW =           (1<<1), 
 *         RIG_MODE_USB =          (1<<2), 
 *         RIG_MODE_LSB =          (1<<3), 
 *         RIG_MODE_RTTY =         (1<<4), 
 *         RIG_MODE_FM =           (1<<5), 
 *         RIG_MODE_WFM =          (1<<6), 
 *         RIG_MODE_CWR =          (1<<7), 
 *         RIG_MODE_RTTYR =        (1<<8), 
 *         RIG_MODE_AMS =          (1<<9), 
 *         RIG_MODE_PKTLSB =       (1<<10),
 *         RIG_MODE_PKTUSB =       (1<<11),
 *         RIG_MODE_PKTFM =        (1<<12),
 *         RIG_MODE_ECSSUSB =      (1<<13),
 *         RIG_MODE_ECSSLSB =      (1<<14),
 *         RIG_MODE_FAX =          (1<<15),
 *         RIG_MODE_SAM =          (1<<16),
 *         RIG_MODE_SAL =          (1<<17),
 *         RIG_MODE_SAH =          (1<<18),
 *         RIG_MODE_DSB =          (1<<19), 
 *     RIG_MODE_TESTS_MAX               
 * } rmode_t;
 * }
 * 
 * Design issues:  
 * 1) There is no UI access to the actual programmed parameters for a given 
 * memory button.
 * 2) Memory buttons save the index into the current radio control.  If you switch
 * radios, the index can mean something different to the new radio.
 * 3) There is no ID number of the settings structure persisted to correspond with 
 * a particular data structure version, which means that there can only be one 
 * format.  This implies obsolescense of the previous structure.
 * 
 * 
 * 
 * @author lutusp
 */
final public class MemoryButton extends JButton 
        implements MouseListener, FocusListener {

    JRX_TX parent;
    final String label;  // Coz This value never changes for this button.
    Timer defineButtonTimer = null;
    MouseEvent mouseEvent = null;
    final int TIMEOUT = 1000;
    boolean defineButton = false;
    int skipDuringScan = 0;
    String visualTip = "<span color=\"green\">Click: read</span><br/>"+
            "<span color=\"blue\">Right-click: toggle skip in memory scan</span>"+
            "<br/><span color=\"red\">Click and hold 1 sec.: write</span>"+
            "<br/><span color=\"purple\">Right-click and hold 1 sec: erase</span>";
    String audibleTip = " Left Click to set VFO frequency. " +
                "Right click to toggle SKIP in memory scan. "+
                "Click and hold one second to store current VFO and mode. "+
                "Right click and hold one second to erase.";
    
    public enum radioModes    { AM,  CW,  USB,  LSB, RTTY,  FM,  CWR,  RTTYR,   PKTLSB,   PKTUSB,   PKTFM,  PKTAM,   D_STAR };
    public String modeStr[] = {"AM","CW","USB","LSB","RTTY","FM","CWR","RTTYR", "PKTLSB", "PKTUSB", "PKTFM", "PKTAM", "D-STAR"};
    
    public TreeMap<String, Integer> displayMap;
    public TreeMap<Integer, String> reverseDisplayMap;
    
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
        setup();
        reset();
        updateState();
    }

    private void setup() {
        addMouseListener(this);
//        for(int index=0; index < modeStr.length; index++) {
//            displayMap.put(modeStr[index], index);
//            reverseDisplayMap.put(index, modeStr[index]);
//        }
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
        setAccessibleDescription();
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
            else if (foreColor == STATE_GRAY)
                stateColorString = "GRAY"; 
        } else {
            // Button is not enabled.  Color is GRAY.
            stateColorString = "GRAY";
        } 
        return stateColorString;       
    }
    
    
    @Override
    public String getToolTipText(MouseEvent event) {
        String ms = ((RWListButton)parent.sv_modesListButton).getItemForIndex(mode);    
        String freq = (frequency >= 0) ? String.format("%.6f MHz %s", 
                (double) frequency / 1e6, ms) : "Undefined";
        return String.format("<html>%s<br> %s</html>", freq, visualTip);
    }

    @Override
    public void focusGained(FocusEvent e) {
    }
    /**
     * Add the name of the button to the memory number and
     * add the color, frequency and mode.
     * @param e 
     */
    
    public void setAccessibleDescription() {
        StringBuilder name = new StringBuilder("");
        name.append(label);
        if (getStateString().equals("GRAY")) {
            name.append(" color is GRAY, button is not programmed. ");
        } else {
            name.append(" color ");
            name.append(getStateString());
            name.append(" frequency ");
            Double freq = ((double)frequency)/1.e6;
            name.append(freq.toString());
            name.append(" megahertz, mode index is "); 
            String modeItem = ((RWListButton)parent.sv_modesListButton).getItemForIndex(mode);
            name.append(modeItem + " ");
        }
        // Change accessibleDescription.
        getAccessibleContext().setAccessibleDescription(
                name.toString() + audibleTip);
    }

    @Override
    public void focusLost(FocusEvent e) {
    }
    /**
     * This timer task determines if a mouse button was held down longer than the 
 TIMEOUT value and if so, it stores the current Vfo frequency and operating
 parameters as a memory channel for a left-click or it erases the memory
 channel with a right-click.
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
        defineButtonTimer.schedule(new DefineButton(), TIMEOUT);
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
                // Set the radio frequency and mode and bandwidth.
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
            // Set frequency before mode.  Some frequencies have limited modes.
            parent.vfoState.writeFrequencyToRadioSelectedVfo(frequency);
            ((ModesListButton)parent.sv_modesListButton).writeModeAndBw( mode, filter);
            parent.vfoDisplay.frequencyToDigits(frequency);
            updateIfNeeded((CtcssListButton)parent.sv_ctcssListButton, ctcss);
            updateIfNeeded((AgcListButton)parent.sv_agcListButton, agc);
            updateIfNeeded(parent.sv_preampCheckBox, preamp);
            updateIfNeeded(parent.sv_antennaComboBox, antenna);
            updateIfNeeded(parent.sv_attenuatorCheckBox, attenuator);
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
        preamp = (parent.sv_preampCheckBox.isSelected()) ? 1 : 0;
        antenna = parent.sv_antennaComboBox.getSelectedIndex();
        attenuator = (parent.sv_attenuatorCheckBox.isSelected()) ? 1 : 0;
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
