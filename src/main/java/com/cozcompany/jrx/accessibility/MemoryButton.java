// ***************************************************************************
// *   Copyright (C) 2012 by Paul Lutus                                      *
// *   lutusp@arachnoid.com                                                  *
// *                                                                         *
// *   This program is free software; you can redistribute it and/or modify  *
// *   it under the terms of the GNU General Public License as published by  *
// *   the Free Software Foundation; either version 2 of the License, or     *
// *   (at your option) any later version.                                   *
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
    String tt = "<span color=\"green\">Click: read</span><br/><span color=\"blue\">Right-click: toggle skip in memory scan</span><br/><span color=\"red\">Click and hold 1 sec.: write</span><br/><span color=\"purple\">Right-click and hold 1 sec: erase</span>";
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

    public MemoryButton(String lbl, JRX_TX p) {
        super(lbl);
        label = lbl;
        parent = p;
        setToolTipText(tt);
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

    @Override
    public String getToolTipText(MouseEvent event) {
        String ms = parent.getMode(mode);
        String s = (frequency >= 0) ? String.format("%.6f MHz %s", (double) frequency / 1e6, ms) : "Undefined";
        return String.format("<html>%s<br/>%s</html>", s, tt);
    }

    private class defineButton extends TimerTask {

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
        //parent.p("mouse pressed");
        mouseEvent = e;
        setForeground(Color.red);
        defineButton = false;
        defineButtonTimer = new java.util.Timer();
        defineButtonTimer.schedule(new defineButton(), timeout);
        parent.getScopePanel().stopSweep(false);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        //parent.p("mouse released");
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

    private void updateIfNeeded(JComboBox cc, int v) {
        int ov = cc.getSelectedIndex();
        if (ov != v) {
            cc.setSelectedIndex(v);
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
             parent.scanFunctions.stopScan(false);
            parent.sv_mostRecentButton = label;
            // always set bandwidth before mode
            updateIfNeeded(parent.sv_filtersComboBox, filter);
            // always set mode before frequency
            updateIfNeeded(parent.sv_modesComboBox, mode);
            //if (frequency != parent.sv_freq) {
            parent.vfoDisplay.frequencyToDigits(frequency);
            //}
            updateIfNeeded(parent.sv_ctcssComboBox, ctcss);
            updateIfNeeded(parent.sv_agcComboBox, agc);
            updateIfNeeded(parent.sv_preampComboBox, preamp);
            updateIfNeeded(parent.sv_antennaComboBox, antenna);
            updateIfNeeded(parent.sv_attenuatorComboBox, attenuator);
            updateIfNeeded(parent.sv_scanStepComboBox, stepSizeIndex);
            //updateIfNeeded(parent.sv_rfGainSlider, rfGain);
            //updateIfNeeded(parent.sv_squelchSlider, squelch);
            //updateIfNeeded(parent.sv_ifShiftSlider, ifShift);
            updateIfNeeded(parent.sv_blankerCheckBox, nb);
            return true;
        } else {
            Beep.beep();
            parent.tellUser("This memory button is undefined. To define it,\npress it for more than one second.");
            return false;
        }
    }

    private void writeButton() {
        parent.sv_mostRecentButton = label;
        filter = parent.sv_filtersComboBox.getSelectedIndex();
        mode = parent.sv_modesComboBox.getSelectedIndex();
        frequency = parent.vfoDisplay.sv_freq;
        ctcss = parent.sv_ctcssComboBox.getSelectedIndex();
        agc = parent.sv_agcComboBox.getSelectedIndex();
        preamp = parent.sv_preampComboBox.getSelectedIndex();
        antenna = parent.sv_antennaComboBox.getSelectedIndex();
        attenuator = parent.sv_attenuatorComboBox.getSelectedIndex();
        stepSizeIndex = parent.sv_scanStepComboBox.getSelectedIndex();
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
