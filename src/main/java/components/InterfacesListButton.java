/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import com.cozcompany.jrx.accessibility.JRX_TX;

/**
 *
 * @author Coz
 */
public class InterfacesListButton extends RWListButton {
   
    public InterfacesListButton(JRX_TX aParent) {
        super(aParent, "", "","INTERFACE", "INTERFACE SELECTION");
        prefix = "";
        token = "";
        super.numericMode = false;
    }
    /**
     * Initialize comm device (interface) comboBox by searching /dev/tty for
     * possible serial ports or USB ports.
     */
    public void initInterfaceList() {
        ((RWListButton)parent.sv_interfacesListButton).removeAllItems();
        
        ((RWListButton)parent.sv_interfacesListButton).
                addListItem("-- Interfaces --",0,"0");
        if (parent.isWindows) {
            for (int i = 1; i <= 16; i++) {
                ((RWListButton)parent.sv_interfacesListButton).
                    addListItem(String.format("COM%d", i), i, String.valueOf(i));
            }
        } else {
            String data;
            if (parent.isMacOs) {
                // List all usb interfaces using MacOs.
                data = parent.runSysCommand(new 
                   String[]{"bash", "-c", "echo  /dev/tty.* /dev/video.*"}, true);
            } else {           
                // List all serial interfaces using Linux.
                data = parent.runSysCommand(new 
                   String[]{"bash", "-c", "echo /dev/ttyS* /dev/ttyUSB* /dev/video*"}, true);
            }
            if (parent.comArgs.debug >= 1) {
                parent.pout("serial list output: [" + data + "]");
            }
            int index = 1;
            for (String s : data.split("\\s+")) {
                // don't add unexpanded arguments
                if (!s.matches(".*\\*.*")) {
                    ((RWListButton)parent.sv_interfacesListButton).addListItem(s, index, String.valueOf(index));
                    System.out.println("Found mac usb serial device :"+s);
                    index++;
                }
            }
        }
        // Update the Dialog with new choices.
        dialog.setNewData(getChoiceStrings());
    }
    
    @Override
    public void setSelectedItem(String item){
        Integer index  = displayMap.get(item);
        if (index == null) {
            // Here is the case that the interface has been swapped out and maybe
            // the radio too.  index is null.  We need to enable the interface control.
            parent.tellUser("Chosen interface "+item+" is no longer available.");
            setEnabled(true);
        } else {
            selectedIndex = index;
            setButtonText(item);
        }
    }
    /**
     * Set the selected item based on the given display string while the localInhibit
     * is enabled.
     * @param s the display string.
     */
    @Override
    public void inhibitSetItem(String s) {
        int index = 0;
        try {
            localInhibit = true;
            index = displayMap.get(s);
            setSelectedIndex(index);
        } catch (Exception e) {
            System.out.println("inhibitSetItem had exception : " + e + " and index = "  + index);
        } finally {
            localInhibit = false;
        }
    }
    
    @Override
    public void writeValueStr() {
        // Comms could be not OK.  This change could establish comms.
        if (!parent.inhibit) {
            int index = getSelectedIndex();
            strSelection = reverseDisplayMap.get(index);
            if (strSelection != null) {
//                if (!strSelection.equals(oldStrSelection) ) {
                    String com = String.format("%s %s %s ", 
                        prefix.toUpperCase(), token, strSelection);
                    parent.sendRadioCom(com, 0, true);
                    oldStrSelection = strSelection;
//                }
                // This is a new interface.  Call parent.initialize().... to start fresh.
                if (!parent.inhibit) parent.initialize();                    
            }
        }
    }

}
