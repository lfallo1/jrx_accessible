/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import java.util.Map;
import java.util.TreeMap;
import javax.accessibility.AccessibleContext;
import javax.swing.JComboBox;

/**
 *
 * @author Coz refactored @author lutusp
 */
public class ScanController {
    JRX_TX appFrame;
    Map<String, Integer> timeSteps = null;

    
    public ScanController(JRX_TX frame) {
        appFrame = frame;
        
    }
    protected void updateScanControls() {
        appFrame.sv_scanStepListButton.setEnabled(appFrame.scanStateMachine.scanTimer == null);
        appFrame.sv_stepPeriodListButton.setEnabled(appFrame.scanStateMachine.scanTimer == null);
        String label = "Scan"; 
        String toolTip = "No active scan"; 
        if (appFrame.scanStateMachine.scanTimer != null) {
            label = (appFrame.scanStateMachine.programScan) ? " Channel list scan " : " Memory button scan ";
            toolTip = (appFrame.scanStateMachine.programScan) ? "Program scan: scans memory locations" : "Memory scan: scans between two defined frequencies";
        } else {
            appFrame.memoryCollection.resetButtonColors();
        }
        appFrame.scanIconLabel.setToolTipText(toolTip);
        //appFrame.scanIconLabel.setIcon((appFrame.scanStateMachine.validState()) ? appFrame.greenLed : appFrame.redLed);
        AccessibleContext context = appFrame.scanIconLabel.getAccessibleContext();
        if (appFrame.scanStateMachine.scanTimer == null) {
            appFrame.scanIconLabel.setIcon(appFrame.redLed);
            context.setAccessibleDescription("Scan state LED is red. No scan is active.");             
        } else {            
            appFrame.scanIconLabel.setIcon(appFrame.greenLed);
            context.setAccessibleDescription("Scan state LED is green. "+label+" is active.");
        }
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

   
    protected void sleep(JComboBox box) {
        double v = getComboBoxTimeStep(box);
        appFrame.waitMS((int) v);
    }



}
