/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import javax.swing.JOptionPane;

/**
 *
 * @author Coz
 */
public class ModesComboBox extends RWComboBox {
    String strFilter = "";
    String oldFilter = "x";

    public ModesComboBox(JRX_TX parent) {
        super(parent, "M", "");
        prefix = "M";
        token = "";        
    }
    
    //((RWComboBox) sv_modesComboBox).setGenericComboBoxScale("Mode", 
    //      "(?ism).*^Mode list:\\s*(.*?)\\s*$.*", false, false);

    @Override 
    protected void setup(boolean first) {
        super.setup(first);
        oldFilter = "xxx";
    }
     
    
    
    // boolean force is not used by this class.
    @Override
    public void writeValue(boolean force) {
        if (!parent.inhibit && !localInhibit && token != null && isEnabled()) {
                writeValueStr();
        }
    }
    /**
     * Method called by ifFilterComboBox to coordinate mode and IF filter; writes
     * mode to radio with selected filter.
     */
    protected void writeValueStr() {
        if (commOK && !parent.inhibit) {
            strFilter = String.format("%.1f", 
                     ((RWComboBox) parent.sv_filtersComboBox).getFilterBW());               
            int index = getSelectedIndex();
            strSelection = reverseUseMap.get(index);
            if (strSelection != null) {
                if (!strSelection.equals(oldStrSelection) || 
                        !strFilter.equals(oldFilter)) {
                    String result = null;
                    String com = String.format("%s %s %s %s", 
                            prefix.toUpperCase(), token, strSelection, strFilter);
                    result = parent.sendRadioCom(com, 0, true);
                    if (result == null) {
                        // Comms error. Radio rejected command.
                        // VoiceOver reads only the third argument, the title.
                        JOptionPane.showMessageDialog(this,
                            "Radio Rejected Command",
                            "Mode, freq, BW unsupported.",  
                            JOptionPane.PLAIN_MESSAGE);
                        com = String.format("%s %s %s %s", 
                            prefix.toUpperCase(), token, oldStrSelection, oldFilter);                      
                        parent.sendRadioCom(com, 0, true);
                        // Set modeComboBox the way it used to be.
                        Integer oldIndex = displayMap.get("Mode "+oldStrSelection);
                        if (oldIndex != null)
                            setSelectedIndex(oldIndex);                       
                    } else {
                        oldStrSelection = strSelection;
                        oldFilter = strFilter;
                    }
                }
            }
        }
    }
    


    /** Override RWComboBox method to simplify.
     *  Modes is never numeric.
     *  Modes always coordinate with IF filters combo box.
     */
   @Override
    public void readConvertedValue() {
        localInhibit = true;
        strSelection = readValueStr();
        if (strSelection != null) {                
            String[] array = strSelection.split("(?sm)\\s+");
            if (array.length >= 2) {
                inhibitSetItem(array[0]);
                ((RWComboBox) parent.sv_filtersComboBox).
                        inhibitSetItem(array[1]);
            }                 
        }
        localInhibit = false;
    }


















   /**
     * Query the Mode comboBox to get the modulation mode of item n.  Validates
     * input parameter n.
     * 
     * @todo Move this to the ModeComboBox class.
     * 
     * @param n
     * @return modulation mode name string or ""
     */
//    protected String getMode(int n) {
//        String s = "";
//        RWComboBox box = (RWComboBox) sv_modesComboBox;
//        if (n >= 0 && n < box.reverseUseMap.size()) {
//            s = box.reverseUseMap.get(n);
//        }
//        return s;
//    }
    
}
