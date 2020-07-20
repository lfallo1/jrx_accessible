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
            for (String s : data.split("\\s+")) {
                // don't add unexpanded arguments
                if (!s.matches(".*\\*.*")) {
                    ((RWListButton)parent.sv_interfacesListButton).addListItem(s, 0, "0");
                    System.out.println("Found mac usb serial device :"+s);
                }
            }
        }
    }     
}
