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
}
