/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 *
 * @author lutusp
 */
final public class ScanFunctions {
    

    JRX_TX parent;
    FreqDisplay vfoDisplayS;
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

    public ScanFunctions(JRX_TX p) {
        vfoDisplayS = p.vfoDisplay;
        parent = p;
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

    protected boolean validState() {
        return parent.dcdCapable || parent.useJRXSquelch;
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
                        long freq = vfoDisplayS.getFreq();
                        if (direction < 0) {                           
                            if (freq > scanEndFreq || freq < scanStartFreq) {
                                vfoDisplayS.frequencyToDigits(scanEndFreq);
                            }
                            scanStep = -Math.abs(scanStep);
                        } else {
                            if (freq > scanEndFreq || freq < scanStartFreq) {
                                vfoDisplayS.frequencyToDigits(scanStartFreq);
                            }
                            scanStep = Math.abs(scanStep);
                        }
                    }
                    scanTimer = new java.util.Timer();
                    scanTimer.scheduleAtFixedRate(new ScanEvents(), 0, (int) scanSpeedMS);
                    parent.updateScanControls();
                }
            } else {
                if (direction != scanDirection) {
                    scanDirection = direction;
                    scanStep = Math.abs(scanStep) * scanDirection;
                }
                if (programScan) {
                    vfoDisplayS.frequencyToDigits(getNextScanFrequency());
                } else {
                    //parent.p("increm scan");
                    vfoDisplayS.frequencyToDigits(vfoDisplayS.getFreq() + (long)scanStep);
                }
            }
        }
    }

    protected void stopScan(boolean alert) {
        if (scanTimer != null) {
            scanTimer.cancel();
            scanTimer = null;
        } else {
            if (alert) {
                Beep.beep();
            }
        }
        parent.updateScanControls();
    }

    protected boolean setScanParams() {
        int index = parent.getActiveTab();
        buttonScanMode = (index == 0);
        if (buttonScanMode) {
            return setMemoryScanParams();
        } else {
            return setTableScanParams();
        }
    }

    protected boolean setTableScanParams() {
        if (parent.validSetup()) {
            tableScanList = new ArrayList<>();
            int[] rowindices = parent.freqTable.getSelectedRows();
            for (int i : rowindices) {
                long f = 0;
                try {
                    double df = Double.parseDouble(parent.freqData[i][3]);
                    f = (long) (df * 1e6 + 0.5);
                } catch (Exception e) {
                }
                tableScanList.add(new TuneData(parent.freqData[i][2], f));
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
            scanStep = parent.getScanStep(parent.sv_scanStepComboBox);
            scanSpeedMS = parent.getTimeStep(parent.sv_scanSpeedComboBox);
            return tableScanList != null;
        }
        return false;
    }

    protected boolean setMemoryScanParams() {
        if (parent.validSetup()) {
            buttonScanList = parent.getScanButtons(parent.memoryButtonTotal);
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
                            parent.tellUser("Scan start and end frequencies are the same");
                            return false;
                        }
                        if (scanStartFreq > scanEndFreq) {
                            long temp = scanStartFreq;
                            scanStartFreq = scanEndFreq;
                            scanEndFreq = temp;
                        }
                    }
                    scanStep = parent.getScanStep(parent.sv_scanStepComboBox);
                    scanSpeedMS = parent.getTimeStep(parent.sv_scanSpeedComboBox);
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
            scanMemoryIndex = incrementScan(scanMemoryIndex, scanStartIndex, scanEndIndex, scanDirection);
            freq = getScanFrequency();
        }
        if (count < 0) {
            parent.noValidFrequenciesPrompt();
            stopScan(false);
            return parent.defaultFrequency;
        }
        return freq;
    }

    class ScanEvents extends TimerTask {

        long lastOpenSquelchTime = -1;

        @Override
        public void run() {
            // acquire ss and set ss meter
            parent.getSignalStrength();
            parent.setSMeter();
            // open/close squelch
            parent.getSquelch(false);
            boolean sqopen = parent.testSquelch();
            if (sqopen) {
                lastOpenSquelchTime = System.currentTimeMillis();
            }
            //p("freq: " + sv_freq);
            double dwellTime = lastOpenSquelchTime + parent.getTimeStep(parent.sv_dwellTimeComboBox);
            double now = System.currentTimeMillis();
            if (now >= dwellTime && !sqopen && scanTimer != null) {
                if (programScan) {
                    vfoDisplayS.frequencyToDigits(getNextScanFrequency());
                } else {
                    vfoDisplayS.frequencyToDigits(vfoDisplayS.getFreq() + (long)scanStep);
                    long freq = vfoDisplayS.getFreq();
                    if (scanDirection < 0) {
                        if (freq < scanStartFreq) {
                            vfoDisplayS.frequencyToDigits(scanEndFreq);
                        }
                    } else {
                        if (freq > scanEndFreq) {
                            vfoDisplayS.frequencyToDigits(scanStartFreq);
                        }
                    }
                }
            }
            //oldf = sv_freq;
            long t1 = System.currentTimeMillis();
            // set receiver frequency
            vfoDisplayS.frequencyToDigits(vfoDisplayS.getFreq());
            long t2 = System.currentTimeMillis();
            double dt = (t2 - t1) / 1000.0;
            if (parent.comArgs.debug >= 1) {
                parent.p(String.format("scan operation: frequency %d, latency: %f", vfoDisplayS.getFreq(), dt));
            }
            
        }
    }
}
