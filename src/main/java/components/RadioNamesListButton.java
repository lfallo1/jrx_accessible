/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import com.cozcompany.jrx.accessibility.JRX_TX;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JOptionPane;

/**
 * To replace the sv_radioNamesComboBox here is the RadioNamesListButton class
 * which represents the radio models supported this version of HAMLIB.
 * 
 * JComboBox<String> sv_radioNamesComboBox;
 * actionPerformed Event handler : sv_radioNamesComboBoxActionPerformed
 * Type Parameters : <String>
 * toolTipText : Available radio manufacturers and models
 * actionCommand : comboBoxChanged
 * preferred size : [114,27]
 * font : Lucida Grande 14 Plain 
 * maximum row count = 8
 * 
 * @author Coz
 */
public class RadioNamesListButton extends RWListButton {
    static Map<String, Integer> radioCodes = null;
    static Map<Integer, String> radioNames = null;
    JRX_TX parent; 

    
    public RadioNamesListButton(JRX_TX aParent) {
        super(aParent, "" , "", "RADIO", "RADIO MODEL SELECTION"); 
        super.numericMode = false;
        parent = aParent;        
    }
    
    /**
     * Return the key String for the given value.  
     * 
     * @param value
     * @return 
     */
    static public String getNameKeyForRadioCodeValue(int value) {
         String rigName = null;
        if (radioNames != null) {
            rigName = radioNames.get(value);
        } 
        if (rigName == null) return "Radio Unknown" ;  
        else return rigName;
    }
    
    /**
     * Return the current HAMLIB radio code for the given rigName String.
     * @param rigName String.
     * @return Integer radioCode
     */
    static public Integer getRadioCode(String rigName) {
        if (radioCodes != null)
            return radioCodes.get(rigName);
        else
            return -1;               
    }
    
    public void setSelectedItem(String rigName) {
        Integer index = displayMap.get(rigName);
        if (index != null) {
            // Valid rigName.
            setSelectedIndex(index);            
        } else {
            System.out.println("in setSelectedItem() for rigName: "+ rigName +
                    " , Not a valid rigName.");
        }        
    }
    /**
     * Read a list of supported radios from hamlib backend and insert into 
     * radioCodes TreeMap; then
     * populate sv_radioNamesComboBox with the radio names.  The hamlib daemon
     * runs for the sole purpose of handling the -l command and then exits. The
     * rig specs are one rig per line and formatted in 5 columns using spaces 
     * instead of tabs.  So information in a column always starts at the same
     * character position from the start of the line.  That makes it easier to
     * check the parsing by position.  The rig numbers are increasing from top to
     * bottom of the list, but are not always consecutive.  It would be good to
     * display the back end  "Version" that is listed for each rig so that bad
     * behavior could be tracked to a particular rig version.
     * 
     * Requirement: It is possible to have a left-over rigctld running on the 
     * system.  If so, you will get that rigctld rig caps and not the ones you
     * are expecting.  You must see if one is already running.  It cannot be
     * killed by giving it a command.  Warn the user with a dialog box.
     * 
     */
    public void getSupportedRadios() {
        radioCodes = new TreeMap<>();
        radioNames = new TreeMap<>();
        String a, b, rigSpecs="";
        //p("trying to read rig specs...");
        if (parent.hamlibExecPath == null) {
            JOptionPane.showMessageDialog(this,"MISSING HAMLIB", 
                    "Missing Hamlib path.",
                    JOptionPane.WARNING_MESSAGE);
        } else {       
            rigSpecs = parent.runSysCommand(
                                new String[]{parent.hamlibExecPath, "-l"}, true);
            if (parent.comArgs.debug >= 1) {
                parent.pout("dump from rigctld -l: [" + rigSpecs + "]");
            }
        } 
        for (String item : rigSpecs.split(parent.LINE_SEP)) {
            // the try ... catch is only to filter the table header
            if (item.length() > 30) {
                try {
                    if (parent.comArgs.debug >= 1) {
                        parent.pout("rigctl radio description line: [" + item + "]");
                    }
                    // Remove the extra white space and place a tab between the 
                    // first two fields on the line.
                    String parse = item.replaceFirst("^\\s*(\\S+)\\s*(.*)$", "$1\t$2");
                    String[] fields = parse.split("\t");
                    if (fields != null && fields.length > 1) {
                        a = fields[0].trim();
                        if (a.matches("[0-9]+") && fields[1].length() > 47) {
                            b = fields[1].substring(0, 47).trim();
                            // Replace whiteSpace between Mfg and Model with one 
                            // space.  String b looks like "Yeasu FT-847" when it
                            // is put into the radioCodes tree as a key.
                            b = b.replaceAll("\\s+", " ");
                            int v = Integer.parseInt(a);
                            if (parent.comArgs.debug >= 1) {
                                parent.pout("radio record: " + b + " = " + v);
                            }
                            radioCodes.put(b, v);
                            radioNames.put(v, b);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                }
            }
        }
        removeAllItems();
        choices = new String[radioCodes.size()+1];
        choices[0] = new String("-- Radios --");
        Integer code = -100;
        addListItem(choices[0],(double)code, code.toString());
        int index = 1;
        for (String key : radioCodes.keySet()) {
            code = radioCodes.get(key);
            addListItem(key, (double)code, code.toString());
            choices[index] = new String(key);
            index++;
        }
        super.dialog.setNewData(choices);
    }
    /**
     * Cannot find a usage in original code....
     * @return 
     */
//    public String getRadioModel() {            
//        return readValueStr();
//    }
    
}
