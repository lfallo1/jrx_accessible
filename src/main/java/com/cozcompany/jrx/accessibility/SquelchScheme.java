/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

/**
 *
 * @author Coz
 */
public class SquelchScheme {
    JRX_TX appFrame;
    boolean useJRXSquelch = false;
    // this was a boolean but it needs to
    // have three states: -1, never set
    // 0 = false, 1 = true
    int squelchOpen = -1;



    public SquelchScheme(JRX_TX frame) {
        appFrame = frame;
        
    }
    
    public void setSquelchScheme() {
        // sv_synthSquelchCheckBox.setEnabled(!dcdCapable);
        appFrame.sv_synthSquelchCheckBox.setEnabled(true);
        useJRXSquelch = appFrame.sv_synthSquelchCheckBox.isSelected();// && !dcdCapable;
        // reset squelch state to default
        appFrame.enableControlCap(appFrame.sv_squelchSlider, appFrame.radioData, "(?ism).*^Set level:.*?SQL\\(", true);
        if (useJRXSquelch) {
            appFrame.sv_squelchSlider.setEnabled(true);
        }
        // was: boolean stateFlag = dcdCapable && !useJRXSquelch;
        boolean stateFlag = !useJRXSquelch;
        //p("dcd: " + dcdCapable + ",useJR: " + useJRXSquelch);
        ((RWSlider) appFrame.sv_squelchSlider).commOK = stateFlag;
        //((RWSlider) sv_volumeSlider).commOK = stateFlag;
        setRadioSquelch();
        appFrame.dcdIconLabel.setIcon(
                appFrame.dcdCapable ? appFrame.greenLed : 
                        useJRXSquelch ? appFrame.yellowLed : appFrame.redLed);
        appFrame.dcdIconLabel.setToolTipText((stateFlag) ? "Radio provides squelch scheme" : 
                useJRXSquelch ? "JRX provides squelch scheme" : "No squelch scheme enabled");
        if (appFrame.comArgs.debug >= 0) {
            appFrame.pout("DCD capable: " + appFrame.dcdCapable);
        }
        ((RWSlider) appFrame.sv_squelchSlider).writeValue(false);
        ((RWSlider) appFrame.sv_volumeSlider).writeValue(false);
        getSquelch(true);
        appFrame.scanDude.updateScanControls();
    }
    
    public void setRadioSquelch() {
        if (useJRXSquelch) {
            String com = String.format("L SQL 0");
            appFrame.sendRadioCom(com, 0, false);
        }
        appFrame.oldVolume = -1;
        squelchOpen = -1;
    }
    
    protected boolean testSquelch() {
        boolean so = (squelchOpen == 1) && appFrame.sv_squelchCheckBox.isSelected();
        appFrame.scanIconLabel.setIcon((so && appFrame.scanStateMachine.scanTimer != null) ? appFrame.redLed : appFrame.greenLed);
        return so;
    }

    protected void getSquelch(boolean force) {
        int sqOpen = appFrame.iErrorValue;
        if (appFrame.dcdCapable && !useJRXSquelch) {
            if (appFrame.comArgs.debug < 2) {
                String s = appFrame.sendRadioCom("\\get_dcd", 1, false);
                if (s != null) {
                    sqOpen = s.trim().equals("1") ? 1 : 0;
                }
            }
        } else if (!useJRXSquelch) {
            sqOpen = 1;
        } else {
            double sv = ((ControlInterface) appFrame.sv_squelchSlider).getConvertedValue();
            sv = appFrame.ntrp(0, 1, appFrame.squelchLow, appFrame.squelchHigh, sv);
            sqOpen = (appFrame.signalStrength > sv) ? 1 : 0;
        }
        //p("sqOpen " + sqOpen + "," + squelchOpen);
        if ((sqOpen != appFrame.iErrorValue && sqOpen != squelchOpen) || force) {
            //p("sqOpen2 " + sqOpen);
            squelchOpen = sqOpen;           
            appFrame.setVolume(squelchOpen == 1); 
        }
    }   
}
