/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import components.RadioNamesListButton;
import java.util.Map;


/**
 * Since some HAMLIB rig caps have errors, here is a way to fix them.
 * @author Coz
 */
public class RigCapsCorrections {
    
    public static void correct(JRX_TX parent, int rigCode) {
        if (RadioNamesListButton.isValidRadioCode(rigCode)) {
            switch (rigCode) {
                case 3070: 
                    correct3070(parent);
                    break;
                default:
                    break;                
            }           
        }
    }
    
    /**
     * Some rig caps have errors.  This is a fix.
     * @param parent 
     */
    public static void correct3070(JRX_TX parent) {
        parent.sv_ifShiftComboBox.setEnabled(false);
        parent.sv_antennaComboBox.setEnabled(false);
        
    }
    
}
