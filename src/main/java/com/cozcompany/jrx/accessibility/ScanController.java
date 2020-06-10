/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import java.util.Map;
import java.util.TreeMap;
import javax.swing.JComboBox;

/**
 *
 * @author Coz refactored @author lutusp
 */
public class ScanController {
    JRX_TX appFrame;
    Map<String, Double> scanSteps = null;
    Map<String, Integer> timeSteps = null;

    
    public ScanController(JRX_TX frame) {
        appFrame = frame;
        
    }
    protected void updateScanControls() {
        appFrame.sv_scanStepComboBox.setEnabled(appFrame.scanFunctions.scanTimer == null);
        appFrame.sv_scanSpeedComboBox.setEnabled(appFrame.scanFunctions.scanTimer == null);
        String label = "Scan";
        String toolTip = "No active scan";
        if (appFrame.scanFunctions.scanTimer != null) {
            label = (appFrame.scanFunctions.programScan) ? "Pscan" : "Mscan";
            toolTip = (appFrame.scanFunctions.programScan) ? "Program scan: scans memory locations" : "Memory scan: scans between two defined frequencies";
        } else {
            appFrame.memoryFunctions.resetButtonColors();
        }
        appFrame.scanTypeLabel.setText(label);
        appFrame.scannerPanel.setToolTipText(toolTip);
        appFrame.scanIconLabel.setIcon((appFrame.scanFunctions.validState()) ? appFrame.greenLed : appFrame.redLed);
    }

    protected void initScanValues(JComboBox<String> stepbox, int initstep, JComboBox<String> speedbox, int initspeed) {
        double bv;
        double[] msteps = new double[]{1, 2, 5};
        String sl;
        if (stepbox != null) {
            stepbox.removeAllItems();
            scanSteps = new TreeMap<>();
            bv = 1;
            for (int p = 0; p <= 7; p++) {
                for (double lv : msteps) {
                    double v = bv * lv;
                    sl = stepLabel(v);
                    scanSteps.put(sl, v);
                    stepbox.addItem(sl);
                }
                bv *= 10;
            }
            setComboBoxIndex(stepbox, initstep);
        }
        if (speedbox != null) {
            speedbox.removeAllItems();
            timeSteps = new TreeMap<>();
            bv = 1;
            for (int p = 0; p <= 4; p++) {
                for (double lv : msteps) {
                    double v = bv * lv;
                    if (v >= 1000) {
                        sl = String.format("%d s", (int) (v / 1000));
                    } else {
                        sl = String.format("%d ms", (int) v);
                    }
                    timeSteps.put(sl, (int) v);
                    speedbox.addItem(sl);
                }
                bv *= 10;
            }
            setComboBoxIndex(speedbox, initspeed);
        }
    }



    
    protected double getScanStep(JComboBox box) {
        return scanSteps.get((String) box.getSelectedItem());
    }

    protected double getTimeStep(JComboBox box) {
        return timeSteps.get((String) box.getSelectedItem());
    }
    
    protected double getComboBoxTimeStep(JComboBox box) {
        return timeSteps.get((String) box.getSelectedItem());
    }
   
    private void setComboBoxIndex(JComboBox box, int index) {
        index = Math.max(0, index);
        index = Math.min(index, box.getItemCount() - 1);
        box.setSelectedIndex(index);
    }

    private String stepLabel(double v) {
        String[] labels = new String[]{"Hz", "kHz", "MHz"};
        double tv = 1;
        int i;
        for (i = 0; i < labels.length; i++) {
            if (v < tv * 1000) {
                break;
            }
            tv *= 1000;
        }
        String s = String.format("%.0f %s", v / tv, labels[i]);
        return s;
    }
    
    protected void sleep(JComboBox box) {
        double v = getComboBoxTimeStep(box);
        appFrame.waitMS((int) v);
    }



}
