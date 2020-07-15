/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;


import com.cozcompany.jrx.accessibility.JRX_TX;
import java.awt.Component;
import javax.swing.JFrame;


/**
 * A ListButton for CTCSS selection to replace a conventional JComboBox.  At
 * this time, voiceOver does not work with the JComboBox.  The CTCSS values
 * are not likely to ever change, so initialize the list with the standard
 * CTCSS tones.  The application needs to disable this button if CTCSS tones
 * are NOT supported by a given rig.  The JList in the dialog contains only
 * the tone frequencies, not the CTCSS label.  It is very tiring to listen to
 * CTCSS phrases 50 times.
 * 
 * @author Coz
 */
public class CtcssListButton extends RWListButton {
    JRX_TX parent;
    PickAction action;    
    ListDialog dialog;
    String title = new String("CTCSS TONE SELECTION");
    String prefix = new String("CTCSS TONE");
    String[] choicesCTCSS = { "67.0", "69.3", "71.9", "74.4", "77.0", "79.7",
        "82.5", "85.4", "88.5", "91.5", "94.8", "97.4", "100.0", "103.5", "107.2",
        "110.9", "114.8", "118.8", "123.0", "127.3", "131.8", "136.5", "141.3",
        "146.2", "151.4", "156.7", "159.8", "162.2", "165.5", "167.9", "171.3",
        "173.8", "177.3", "179.9", "183.5", "186.2", "189.9", "192.8", "196.6",
        "199.5", "203.5", "206.5", "210.7", "218.1", "225.7", "229.1", "233.6", 
        "241.8", "250.3", "254.1" };
    public CtcssListButton(JRX_TX aParent) {
        super( aParent,  "ctcss" , "" );
        parent = aParent;
        super.numericMode = true;
        // Generate a list of default choices. Not necessary.
        // super.listButtonPlaceholderData("CTCSS ");        
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
                choicesCTCSS,
                prefix);  
             
        action = new PickAction( 
                prefix,
                null,
                "Select Ctcss tone from dialog list.",
                null,                
                this,
                dialog);
        
        setAction(action);
        
        for (int index=0; index< choicesCTCSS.length; index++){
            double v = Double.valueOf(choicesCTCSS[index]);
            String ss = String.format("%.0f", v * 10);
            super.addListItem(choicesCTCSS[index], v*10.0, ss);                   
        }
        setButtonText(choicesCTCSS[this.selectedIndex]);
        getAccessibleContext().setAccessibleName(
                "Open dialog to choose a CTCSS tone.");
    }
        /**
     * Given the rigcaps reply, determine if this control capability exists.
     * @param source 
     * @return boolean true when capability exists.
     */
    public boolean enableIfCapable(String source){
        String search = "(?ism).*^Set functions:.*?\\sTONE\\s";
        boolean hasLevelValue = false; // This control has Level Value:
        boolean isEnabled = enableCap(source, search, hasLevelValue);
        if (isEnabled) {
            super.writeValue(true);
        }
        return isEnabled;
    }    

}
