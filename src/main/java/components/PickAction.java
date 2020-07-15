/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import java.awt.Component;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import static javax.swing.Action.MNEMONIC_KEY;
import static javax.swing.Action.SHORT_DESCRIPTION;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;

/**
 *
 * @author Coz  from Oracle example.
 */
public class PickAction extends AbstractAction {
    RWListButton listButton = null;
    ListDialog dialog = null;
    String prefix;
    
    public PickAction(String textPrefix, ImageIcon icon,
                      String desc, Integer mnemonic,
                      RWListButton aButton, ListDialog aDialog) {
        super(textPrefix, icon);
        prefix = textPrefix;
        putValue(SHORT_DESCRIPTION, desc);
        putValue(MNEMONIC_KEY, mnemonic);
        listButton = aButton;
        dialog = aDialog;
    }
        
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        //System.out.println("Action for PickAction command: "+ command);
        String selection = dialog.showDialog();
        //System.out.println("New selection is "+selection);
        //listButton.setButtonText(selection);
    }
}