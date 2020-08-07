/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import components.DwellTimeListButton;
import components.StepFrequencyListButton;
import components.StepPeriodListButton;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import vfoDisplayControl.VfoDisplayControl;

/**
 *
 * @author lutusp
 */
final public class ScanStateMachine {
    

    JRX_TX parent;
    VfoDisplayControl vfoDisplayS;
    long scanStartFreq = 0;
    long scanEndFreq = 0;
    int scanStartIndex = 0;
    int scanEndIndex = 0;
    int scanMemoryIndex = 0;
    boolean programScan = false;
    double scanStep;
    double scanSpeedMS = 500;
    int scanDirection = 0;
    Timer scanTimer = null;
    MemoryButton oldButton = null;
    ArrayList<MemoryButton> buttonScanList = null;
    ArrayList<TuneData> tableScanList = null;
    boolean buttonScanMode = false;
    boolean sqOpen = false;
    boolean oldSqOpen = false;
    boolean hasStepped = false;
   

    public ScanStateMachine(JRX_TX p) {
        vfoDisplayS = p.vfoDisplay;
        parent = p;
    }

    protected void noValidFrequenciesPrompt() {
        parent.tellUser("<html>No valid memory buttons in range<br/>or all set to <span color=\"blue\">\"skip in memory scan\"</span>");
    }

    protected long getScanFrequency() {
        if (buttonScanMode) {
            MemoryButton mb = buttonScanList.get(scanMemoryIndex);
            if (oldButton != null) {
                oldButton.updateState(parent.darkGreen);
            }
            mb.updateState(Color.red);
            oldButton = mb;
            return ((mb.skipDuringScan != 0) ? -1 : mb.frequency);
        } else {
            return tableScanList.get(scanMemoryIndex).freq;
        }
    }
    
    public Timer getScanTimer() {
        return scanTimer;        
    }

    protected boolean validState() {
        return parent.dcdCapable || parent.squelchScheme.useJRXSquelch;
    }

    protected void startScan(int direction) {
        if (!validState()) {
            parent.tellUser("No squelch scheme enabled");
        } else {
            parent.getScopePanel().stopSweep(false);
            if (scanTimer == null) {
                oldButton = null;
                scanDirection = direction;
                if (setScanParams()) {
                    if (programScan) {
                        if (direction < 0) {
                            if (scanMemoryIndex > scanEndIndex || scanMemoryIndex < scanStartIndex) {
                                scanMemoryIndex = scanEndIndex;
                            }
                        } else {
                            if (scanMemoryIndex > scanEndIndex || scanMemoryIndex < scanStartIndex) {
                                scanMemoryIndex = scanStartIndex;
                            }
                        }
                    } else {
                        // Scan every scanStep over a range.
                        long freq = vfoDisplayS.getFreq();
                        if (direction < 0) {                           
                            if (freq > scanEndFreq || freq < scanStartFreq) {                    
                                parent.vfoState.writeFrequencyToRadioSelectedVfo(scanEndFreq);
                                vfoDisplayS.frequencyToDigits(scanEndFreq);
                            }
                            scanStep = -Math.abs(scanStep);
                        } else {
                            if (freq > scanEndFreq || freq < scanStartFreq) {
                                parent.vfoState.writeFrequencyToRadioSelectedVfo(scanStartFreq);
                                vfoDisplayS.frequencyToDigits(scanStartFreq);
                            }
                            scanStep = Math.abs(scanStep);
                        }
                    }
                    scanTimer = new java.util.Timer();
                    //scanTimer.scheduleAtFixedRate(new ScanEvents(), 0, (int) scanSpeedMS); // WHY?
                    scanTimer.schedule(new ScanEvents(), 0, (int) scanSpeedMS);
                    parent.scanDude.updateScanControls();
                }
            } else {
                if (direction != scanDirection) {
                    scanDirection = direction;
                    scanStep = Math.abs(scanStep) * scanDirection;
                }
                if (programScan) {
                    long freq = getNextScanFrequency();
                    parent.vfoState.writeFrequencyToRadioSelectedVfo(freq);
                    vfoDisplayS.frequencyToDigits(freq);
                } else {
                    //parent.pout("increm scan");
                    // Scanning in steps between two memory buttons.
                    long freq = vfoDisplayS.getFreq() + (long)scanStep;
                    parent.vfoState.writeFrequencyToRadioSelectedVfo(freq);
                    vfoDisplayS.frequencyToDigits(freq);
                }
            }
        }
    }

    protected void stopScan(boolean alert) {
        if (scanTimer != null) {
            scanTimer.cancel();
            scanTimer.purge();
            scanTimer = null;
        } else {
            if (alert) {
                Beep.beep();
            }
        }
        parent.scanDude.updateScanControls();
    }
    /**
     * Based on which tab is selected choose the scan frequencies and scan mode.
     * @return true if successful.
     */
    protected boolean setScanParams() {
        int index = parent.channelsTabbedPane.getSelectedIndex();
        buttonScanMode = (index == 0);
        if (buttonScanMode) {
            return setMemoryScanParams();
        } else {
            return setTableScanParams();
        }
    }

    /**
     * A Scanner method using selected rows of the ChannelChart, scan the 
     * channels listed there.  There are two ways to scan channels.  If you select
     * more than two, each channel in the selection is scanned in turn.  If you
     * select only two channels, the range in between those channels is scanned by
     * the step size increment.
     * 
     * @return Boolean success
     */
    protected boolean setTableScanParams() {
        if (parent.validSetup()) {
            tableScanList = new ArrayList<>();
            int[] rowindices = parent.chart.getSelectRows();
            for (int i : rowindices) {
                long f = 0;
                try {
                    double df = Double.parseDouble(parent.chart.getValue(i,3));
                    f = (long) (df * 1e6 + 0.5);
                } catch (Exception e) {
                }
                tableScanList.add(new TuneData(parent.chart.getValue(i,2), f));
            }
            scanStartIndex = 0;
            scanEndIndex = tableScanList.size();
            if (scanEndIndex < 2) {
                tableScanList = null;
                parent.tellUser("<html>For scanning, please select two or more frequencies<br/>from the frequency table.");
            } else {
                programScan = scanEndIndex > 2;
                if (!programScan) {
                    scanStartFreq = tableScanList.get(0).freq;
                    scanEndFreq = tableScanList.get(1).freq;
                    if (scanStartFreq > scanEndFreq) {
                        long temp = scanStartFreq;
                        scanStartFreq = scanEndFreq;
                        scanEndFreq = temp;
                    }
                }
            }
            scanStep = ((StepFrequencyListButton)parent.sv_scanStepListButton).
                    getScanStep();
            scanSpeedMS = ((StepPeriodListButton)parent.sv_stepPeriodListButton).
                    getTimeStep();
            return tableScanList != null;
        }
        return false;
    }

        /**
         * A Scanner method using a range of memory buttons as scan channels.
         * There are two modes of memory button scans.  The first has two buttons
         * in the buttonScanList.  The scan starts with the lowest frequency
         * button and ends with the highest frequency button using the scan step
         * to traverse the difference between the endpoints.
         * 
         * The second scan mode has more than two buttons in the buttonScanList
         * and each button's frequency is scanned in turn.
         * 
         * @return Boolean success
         */
    protected boolean setMemoryScanParams() {
        if (parent.validSetup()) {
            buttonScanList = parent.memoryCollection.
                    getScanButtons(MemoryCollection.MEMORY_BUTTONS_QTY);
            if (buttonScanList != null) {
                int sz = buttonScanList.size();
                if (sz >= 2) {
                    programScan = sz > 2;
                    if (programScan) {
                        scanStartIndex = 0;
                        scanEndIndex = sz;
                    } else {
                        scanStartFreq = buttonScanList.get(0).frequency;
                        scanEndFreq = buttonScanList.get(1).frequency;
                        if (scanStartFreq == scanEndFreq) {
                            parent.tellUser(
                                "Scan start and end frequencies are the same");
                            return false;
                        }
                        if (scanStartFreq > scanEndFreq) {
                            long temp = scanStartFreq;
                            scanStartFreq = scanEndFreq;
                            scanEndFreq = temp;
                        }
                    }
                    scanStep = ((StepFrequencyListButton)parent.
                            sv_scanStepListButton).getScanStep();
                    scanSpeedMS = ((StepPeriodListButton)parent.
                            sv_stepPeriodListButton).getTimeStep();
                    return true;

                } else {
                    parent.tellUser("<html>For scanning, please click the lower or upper button<br/>of a set of at least two adjacent defined memory buttons");
                }
            }
        }
        return false;
    }

    protected int incrementScan(int i, int lo, int hi, int increm) {
        if (hi > lo) {
            return ((i + increm + (hi-lo)) % (hi - lo) + lo);
        } else {
            return i;
        }
    }

    protected long getNextScanFrequency() {
        long freq = -1;
        int count = 100;
        while (freq < 0 && count-- > 0) {
            scanMemoryIndex = incrementScan(
                    scanMemoryIndex, scanStartIndex, scanEndIndex, scanDirection);
            freq = getScanFrequency();
        }
        if (count < 0) {
            noValidFrequenciesPrompt();
            stopScan(false);
            return parent.defaultFrequency;
        }
        return freq;
    }
    /**
     * Background task to scan channels.
     * 
     * Something in this task is eating click events on the start/stop buttons.
     */
    class ScanEvents extends TimerTask {

        long lastOpenSquelchTime = -1;

        @Override
        public void run() {
            // acquire ss and set ss meter
            parent.getSignalStrength();
            parent.setSMeter();
            boolean voiceOver = ((JCheckBox)parent.sv_squelchCheckBox).isSelected();
            oldSqOpen = sqOpen;
            sqOpen = parent.squelchScheme.getSquelch();
            // Set squelch open time based on first transition from closed to open
            // after stepping though channels.
            if (sqOpen && !oldSqOpen && hasStepped) {
                lastOpenSquelchTime = System.currentTimeMillis(); // returns long milliseconds.
                hasStepped = false;
                if (voiceOver) {
                    voiceOverAnnounce();
                }
            }
            double dwellPeriod = ((DwellTimeListButton)parent.sv_dwellTimeListButton).getTimeStep();
            double dwellTime = lastOpenSquelchTime + 
                    ((DwellTimeListButton)parent.sv_dwellTimeListButton).
                            getTimeStep();
            double now = System.currentTimeMillis();
            if (now >= dwellTime  && scanTimer != null) {
                hasStepped = true;
                if (programScan) {
                    // Scanning through group of memory buttons.
                    long freq = getNextScanFrequency();
                    parent.vfoState.writeFrequencyToRadioSelectedVfo(freq);
                    vfoDisplayS.frequencyToDigits(freq);
                } else {
                    // Scanning in steps between two memory buttons.
                    long freq = vfoDisplayS.getFreq() + (long)scanStep;
                    parent.vfoState.writeFrequencyToRadioSelectedVfo(freq);
                    vfoDisplayS.frequencyToDigits(freq);
                    freq = vfoDisplayS.getFreq();
                    if (scanDirection < 0) {
                        if (freq < scanStartFreq) {
                            parent.vfoState.writeFrequencyToRadioSelectedVfo(scanEndFreq);
                            vfoDisplayS.frequencyToDigits(scanEndFreq);
                        }
                    } else {
                        if (freq > scanEndFreq) {
                            parent.vfoState.writeFrequencyToRadioSelectedVfo(scanStartFreq);
                            vfoDisplayS.frequencyToDigits(scanStartFreq);
                        }
                    }
                }
            }
            //oldf = sv_freq;
            long t1 = System.currentTimeMillis();
            // set receiver frequency
            long freq = vfoDisplayS.getFreq();
            vfoDisplayS.frequencyToDigits(freq);
            long t2 = System.currentTimeMillis();
            double dt = (t2 - t1) / 1000.0;
            if (parent.comArgs.debug >= 1) {
                parent.pout(String.format(
                        "scan operation: frequency %d, latency: %f", 
                        vfoDisplayS.getFreq(), dt));
            }            
        }
    }
            
            
    private void voiceOverAnnounce() {        
        String announce;
        if (buttonScanMode) {
            // Scanning memory buttons.
            if (programScan) {
                // Scanning through a group of memory buttons.
                // Announce button label and frequency.
                MemoryButton mb = buttonScanList.get(scanMemoryIndex);
                double mhz = ((double)mb.frequency)/1.e6;
                announce = String.format("%s freq %f", mb.label, mhz);
            } else {
                // Scanning in steps between two memory buttons.
                // Announce frequency for step scan.
                long freq = vfoDisplayS.getFreq();
                double mhz = ((double)freq)/1.e6;
                announce = String.format("Step freq %f", mhz);
            }
        } else {
            // Scanning channel chart.
            if (programScan) {
                // Scanning through a group of channels.
                // Announce channel label and frequency.
                long freq = tableScanList.get(scanMemoryIndex).freq;
                double mhz = ((double)freq)/1.e6;
                announce = String.format("Channel freq %f",  mhz);
            } else {
                // Scanning in steps between two memory buttons.
                // Announce frequency for step scan.
                long freq = vfoDisplayS.getFreq();
                double mhz = ((double)freq)/1.e6;
                announce = String.format("Step freq %f", mhz);
            }
        }
        JOptionPane.showMessageDialog(parent,
                "Squelch Open VoiceOver Dialog",
                announce,  // VoiceOver reads only this line.
                JOptionPane.PLAIN_MESSAGE);
    }
}
