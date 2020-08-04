/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import com.cozcompany.jrx.accessibility.*;
import java.awt.Component;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 * Other controls rely on the MODE setting for their own state.  An example is
 * CTCSS tone only applies to FM.  It is an error when requested in D-STAR mode.
 * The IF bandwidth changes with the MODE setting on most radios.
 * 
 * @author Coz
 */
public class ModesListButton extends RWListButton {
    JRX_TX parent;  
    String strFilter = "";
    String oldFilter = "x";
    String[] choices = { "AM", "FM", "CW" };

    public ModesListButton(JRX_TX aParent) {
        super(aParent, "M", "", "MODE", "MODES SELECTION");
        prefix = "M";
        token = "";
        super.numericMode = false;
        parent = aParent;
    }
     
    //((RWComboBox) sv_modesComboBox).setGenericScale("Mode", 
    //      "(?ism).*^Mode list:\\s*(.*?)\\s*$.*", false, false);

    @Override 
    protected void setup() {
        super.setup();
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
    @Override
    public void writeValueStr() {
        if (commOK && !parent.inhibit) {
            strFilter = String.format("%.1f", 
                     ((IfFilterListButton) parent.sv_ifFilterListButton).getFilterBW());               
            int index = getSelectedIndex();
            strSelection = reverseUseMap.get(index);
            boolean ctcssToneEnabled =  (strSelection.equals("FM"));
            // enable/disable CTCSS tone TX check box
            parent.sv_txCtcssCheckBox.setEnabled(ctcssToneEnabled);
            parent.sv_ctcssSquelchCheckBox.setEnabled(ctcssToneEnabled);            
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


    /**
     * This method is called for every control during initialize()... on startup\
     * and whenever the radio or interface is changed.
     * @param all is not used.
     */
    @Override
    public void selectiveReadValue(boolean all) {
        if (isEnabled() && commOK) {
            readConvertedValue();
        }
    }


    
    /** Override RWListButton method to simplify.
     *  Mode is never numeric.
     *  Mode always coordinates with IF filters list button.
     */
   @Override
    public void readConvertedValue() {
        localInhibit = true;
        strSelection = readValueStr();
        if (strSelection != null) {                
            String[] array = strSelection.split("(?sm)\\s+");
            if (array.length >= 2) {
                inhibitSetItem(array[0]);
                ((RWListButton) parent.sv_ifFilterListButton).
                        inhibitSetItem(array[1]);
            }                 
        }
        localInhibit = false;
    }
    /**
     * Set the selected item based on the given display string while the localInhibit
     * is enabled.
     * @param str the display string.
     */
    @Override
    public void inhibitSetItem(String str) {
        int index = 0;
        boolean ctcssToneEnabled = (str.equals("FM"));
        // enable/disable CTCSS tone TX check box
        parent.sv_txCtcssCheckBox.setEnabled(ctcssToneEnabled);
        parent.sv_ctcssSquelchCheckBox.setEnabled(ctcssToneEnabled);
        try {
            localInhibit = true;
            //String search = ".*"
            index = useMap.get(str);
            setSelectedIndex(index);
        } catch (Exception e) {
            System.out.println("inhibitSetItem had exception : " + e + " and index = "  + index);
        } finally {
            localInhibit = false;
        }
    }
    
    @Override
    public void setGenericScale(
            String tag,
            String search,
            boolean offOption,
            boolean numeric) {
        setup();
        boolean old_inhibit = parent.inhibit;
        parent.inhibit = true;
        int index = getSelectedIndex();
        if (parent.radioData != null) {
            try {
                String s = parent.radioData.replaceFirst(search, "$1");
                if (parent.comArgs.debug >= 1) {
                    parent.pout("listButton content: [" + s + "]");
                }
                String[] array = s.split("\\s+");
                super.dialog.setNewData(array);
                String is;
                if (array != null) {
                    removeAllItems();
                    int n = 0;
                    if (offOption) {
                        is = tag + " off";
                        addListItem(is, 0, "0");
                        n += 1;
                    }
                    for (String ss : array) {
                        is = ss;
                        double v = 0;
                        addListItem(is, v, ss);
                        n += 1;
                    }
                } else {
                    placeholderData(valueLabel);
                }
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        setListButtonIndex(index);
        parent.inhibit = old_inhibit;
    }

    public String getMode() {
        String s = null;
        if (parent.validSetup()) {
            s = (String)getSelectedItem();
        }
        return s;
    }
    
}
