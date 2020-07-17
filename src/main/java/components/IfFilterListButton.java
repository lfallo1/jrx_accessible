/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import com.cozcompany.jrx.accessibility.*;
import java.awt.Component;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFrame;

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
public class IfFilterListButton extends RWListButton {
    JRX_TX parent;
    PickAction action;    
    ListDialog dialog;
    String title = new String("IF BANDWIDTH SELECTION");
    String[] choices = { "3", "6", "12" };
   
    public IfFilterListButton(JRX_TX aParent) {
        super(aParent, "F", "","BANDWIDTH");
        parent = aParent;
        prefix = "F";
        token = "";
        super.numericMode = true;
    }
    
    /**
     * Must create components that use this pointer after the CTOR is complete.
     */
    public void initialize() {
        dialog = new ListDialog(
                (JFrame)super.parent, 
                (Component)this, 
                title, 
                title,
                this.selectedIndex,
                choices);  
             
        action = new PickAction( 
                "BW",
                null,
                "Select IF Bandwidth from dialog list.",
                null,                
                this,
                dialog);
        
        setAction(action);
        
        for (int index=0; index< choices.length; index++){
            double v = Double.valueOf(choices[index]);
            String ss = String.format("%.0f Khz", v );
            super.addListItem(choices[index], v*1000.0, ss);                   
        }
        setButtonText(choices[this.selectedIndex]);
        getAccessibleContext().setAccessibleName(
                "Open dialog to choose an I F bandwidth.");
    }
    
    /**
     * This control capability is always enabled.
     * @param source 
     * @return boolean true when capability exists.
     */
    public boolean enableIfCapable(String source){
        return true;
    }

    /**
     * IfFilterListButton has unique method for populating the selection dialog.
     * @param tag
     * @param search
     * @param offOption
     * @param numeric 
     */    
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
        setupFilter();
        setSelectedIndex(index);
        parent.inhibit = old_inhibit;
    }

    public double getFilterBW() {
        int i = getSelectedIndex();
        if (i >= 0 && i < reverseUseMap.size()) {
            return Double.parseDouble(reverseUseMap.get(i));
        }
        return 0;
    }
    
    /**
     * Filter ListButton has unique setup.
     */
    private void setupFilter() {
        if (parent.radioData != null) {
            removeAllItems();
            TreeMap<Long, String> valToKey = new TreeMap<>();
            valToKey.put(0L, "auto");
            String s = parent.radioData.replaceFirst(
                    "(?is).*?Filters:(.*?)Bandwidths.*", "$1");
            Pattern pattern = Pattern.compile("(?is)[0-9.]+ k?hz");
            Matcher m = pattern.matcher(s);
            while (m.find()) {
                String ss = m.group();
                String[] array = ss.split("\\s+");
                double mult = (array[1].equalsIgnoreCase("hz")) ? 1 : 1000;
                double bw = Double.parseDouble(array[0]) * mult;
                long dv = (long) bw;  // bandwidth value, ss is units like khz
                // "valToKey" serves some important purposes:
                //      it sorts the entries and eliminates duplicates.
                valToKey.put(dv, ss); // valToKey(12000,"12 khz"); for example
            }
            String[] choices = new String[valToKey.size()];
            int ii = 0;
            for (long dv : valToKey.keySet()) {
                //addListItem(valToKey.get(dv), "" + dv);
                addListItem(valToKey.get(dv), dv, "" + dv);
                choices[ii] = valToKey.get(dv);
                ii++;
                if (parent.comArgs.debug >= 1) {
                    parent.pout("key to filter : [" + dv + "]");
                }
            }
            dialog.setNewData(choices);
        }
    }
    
    // boolean force is not used by this class.
    @Override
    public void writeValue(boolean force) {
        if (!parent.inhibit && !localInhibit && token != null && isEnabled()) {
            if (commOK && !parent.inhibit) {
                ((RWListButton) parent.sv_modesListButton).writeValueStr();
            }
        }
    }
    
    
    @Override
    public void readConvertedValue() {
        localInhibit = true;
        {
            strSelection = readValueStr();  // gets the selected mode plus the bandwidth.
            System.out.println("IfFilter strSelection = "+ strSelection);
            String[] fields = strSelection.split("\\s+");
            // The first field is the mode.  The second field is the bandWidth
            if (fields[1] != null) {
                inhibitSetItem(fields[1]);
            }
        }
        localInhibit = false;
    }
    
    public String readValueStr() {
        String s = "";
        if (token != null && isEnabled() && commOK) {            
            s = ((RWListButton) parent.sv_modesListButton).readValueStr(); 
        }
        return s;
    }
    
}
