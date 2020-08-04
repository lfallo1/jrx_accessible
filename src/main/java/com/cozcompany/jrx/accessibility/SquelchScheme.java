/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import javax.swing.Icon;

/**
 * A class to provide DCD for radios without that capability.
 * This class modifies the behavior of the squelchSlider in mysterious ways...
 * so the behavior should be separated into a separate control, say a 
 * synthetic squelch slider so that the two have NO interaction except that 
 * when one is enabled, the other is disabled.
 * 
 * 
 * @author Coz
 */
public class SquelchScheme {
    JRX_TX appFrame;
    boolean useJRXSquelch = false;
    // This needs to have three states: 0 = false, 1 = true, 2 = never set
    enum SquelchOpen  {NOT_OPEN, OPEN, NEVER_SET} ;
    SquelchOpen squelchOpen = SquelchOpen.NEVER_SET;



    public SquelchScheme(JRX_TX frame) {
        appFrame = frame;
        
    }
    
    public void setSquelchScheme() {
        // sv_synthSquelchCheckBox.setEnabled(!dcdCapable);
        appFrame.sv_synthSquelchCheckBox.setEnabled(false); // disable this feature.
        useJRXSquelch = false;
        //useJRXSquelch = appFrame.sv_synthSquelchCheckBox.isSelected();// && !dcdCapable;
        // reset squelch state to default
        ((RWSlider)appFrame.sv_squelchSlider).enableCap(appFrame.radioData, "(?ism).*^Set level:.*?SQL\\(", true);
        if (useJRXSquelch) {
            appFrame.sv_squelchSlider.setEnabled(true);
        }
        // was: boolean stateFlag = dcdCapable && !useJRXSquelch;
        boolean stateFlag = !useJRXSquelch;
        //p("dcd: " + dcdCapable + ",useJR: " + useJRXSquelch);
        ((RWSlider) appFrame.sv_squelchSlider).commOK = stateFlag;
        //((RWSlider) sv_volumeSlider).commOK = stateFlag;
        setRadioSquelch();
        Icon chosenDcdIcon;
        String dcdLedDescription;
        if (appFrame.dcdCapable) { 
            chosenDcdIcon = appFrame.greenLed;
            dcdLedDescription = "LED is green. Radio provides squelch scheme.";
        } else if (useJRXSquelch) {
            chosenDcdIcon = appFrame.yellowLed;
            dcdLedDescription = "LED is yellow. JRX_TX provides squelch scheme.";
        } else {
            chosenDcdIcon = appFrame.redLed;
            dcdLedDescription = "LED is red. No squelch scheme enabled.";
        }
//        appFrame.dcdIconLabel.setIcon(
//                appFrame.dcdCapable ? appFrame.greenLed : 
//                        useJRXSquelch ? appFrame.yellowLed : appFrame.redLed);
        appFrame.dcdIconLabel.setToolTipText(dcdLedDescription);
        appFrame.dcdIconLabel.getAccessibleContext().setAccessibleDescription(dcdLedDescription);
        appFrame.pout("DCD capable: " + appFrame.dcdCapable);
        ((RWSlider) appFrame.sv_squelchSlider).writeValue(true);
        ((RWSlider) appFrame.sv_volumeSlider).writeValue(true);
        getSquelch();
        appFrame.scanDude.updateScanControls();
    }
    
    public void setRadioSquelch() {
        if (useJRXSquelch) {
            String com = String.format("L SQL 0");
            appFrame.sendRadioCom(com, 0, false);
        }
        appFrame.oldVolume = -1;
        squelchOpen = SquelchOpen.NEVER_SET;
    }
    
    protected boolean testSquelch() {
        boolean so = (squelchOpen == SquelchOpen.OPEN) && appFrame.sv_squelchCheckBox.isSelected();
        appFrame.scanIconLabel.setIcon((so && appFrame.scanStateMachine.scanTimer != null) ? appFrame.redLed : appFrame.greenLed);
        return so;
    }

    /**
     * Read the radio current carrier detect to see if squelch is broken by a
     * received signal.  Set a local state variable based on sqOpen.
     * 
     * @return true when squelch is Open.
     */
    public boolean getSquelch() {
        boolean sqOpen = false;
        if (appFrame.dcdCapable ) {
            String s = appFrame.sendRadioCom("\\get_dcd", 1, false);
            if (s != null) {
                sqOpen = s.trim().equals("1") ? true : false;
            } 
        } else {
            double sv = ((ControlInterface) appFrame.sv_squelchSlider).getConvertedValue();
            sv = appFrame.ntrp(0, 1, appFrame.squelchLow, appFrame.squelchHigh, sv);
            sqOpen = (appFrame.signalStrength > sv) ? true : false;
        }
        //appFrame.pout("sqOpen: " + sqOpen + ", squelchOpen: " + squelchOpen.ordinal());
        if ( sqOpen ) {
            squelchOpen = SquelchOpen.values()[1];
        } else {
            squelchOpen = SquelchOpen.values()[0];
        }
        return sqOpen;
    }
}
