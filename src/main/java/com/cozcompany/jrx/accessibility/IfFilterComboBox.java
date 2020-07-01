/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cozcompany.jrx.accessibility;

import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class represents the IF filter (bandwidth) combo box which has enough logic
 * to demand its own class to eliminate clutter in the parent class.
 * 
 * The filterComboBox and the modesComboBox work as a pair.  Changes to one
 * effects the other:
 * 
 * When a change is made to this box, the mode comboBox value is written to radio.
 * 
 * @author Coz
 */
public class IfFilterComboBox extends RWComboBox {
    
    public IfFilterComboBox(JRX_TX parent) {
        super(parent, "F", "");
        prefix = "F";
        token = "";       
    }

    //((RWComboBox) sv_filtersComboBox).setGenericComboBoxScale("", "", true, true);
    
    
    @Override
    protected void setGenericComboBoxScale(
            String tag,
            String search,
            boolean offOption,
            boolean numeric) {
        setup(false);
        boolean old_inhibit = parent.inhibit;
        parent.inhibit = true;
        int index = getSelectedIndex();
        setupFilterCombo();
        setComboBoxIndex(index);
        parent.inhibit = old_inhibit;
    }

    protected double getFilterBW() {
        int i = getSelectedIndex();
        if (i >= 0 && i < reverseUseMap.size()) {
            return Double.parseDouble(reverseUseMap.get(i));
        }
        return 0;
    }


    
    /**
     * Filter comboBox has unique setup.
     */
    private void setupFilterCombo() {
        if (parent.radioData != null) {
            removeAllItems();
            TreeMap<Long, String> valToKey = new TreeMap<>();
            valToKey.put(0L, "BW auto");
            String s = parent.radioData.replaceFirst(
                    "(?is).*?Filters:(.*?)Bandwidths.*", "$1");
            Pattern pattern = Pattern.compile("(?is)[0-9.]+ k?hz");
            Matcher m = pattern.matcher(s);
            while (m.find()) {
                String ss = m.group();
                String[] array = ss.split("\\s+");
                ss = "BW " + ss;
                double mult = (array[1].equalsIgnoreCase("hz")) ? 1 : 1000;
                double bw = Double.parseDouble(array[0]) * mult;
                long dv = (long) bw;  // bandwidth value, ss is units like khz
                // "valToKey" serves some important purposes:
                //      it sorts the entries and eliminates duplicates.
                valToKey.put(dv, ss);
            }
            for (long dv : valToKey.keySet()) {
                //addListItem(valToKey.get(dv), "" + dv);
                addListItem(valToKey.get(dv), dv, "" + dv);
                if (parent.comArgs.debug >= 1) {
                    parent.pout("key to filter : [" + dv + "]");
                }
            }
        }
    }
    
    // boolean force is not used by this class.
    @Override
    public void writeValue(boolean force) {
        if (!parent.inhibit && !localInhibit && token != null && isEnabled()) {
            if (commOK && !parent.inhibit) {
                    ((RWComboBox) parent.sv_modesComboBox).writeValueStr();
            }
        }
    }
    
    

    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    @Override
    public void readConvertedValue() {
        localInhibit = true;
        {
            strSelection = readValueStr();  // gets the selected mode.
            if (strSelection != null) {
                    inhibitSetItem(strSelection);
            }
        }
        localInhibit = false;
    }
    
    protected String readValueStr() {
        String s = "";
        if (token != null && isEnabled() && commOK) {            
            ((RWComboBox) parent.sv_modesComboBox).readValueStr(); // @TODO why not set value of s ?
        }
        return s;
    }
    
}
