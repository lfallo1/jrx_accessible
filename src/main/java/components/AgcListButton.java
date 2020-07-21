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
public class AgcListButton extends RWListButton {
    JRX_TX parent;  
    public AgcListButton(JRX_TX aParent) {
        super(aParent, "L", "AGC", "AGC", "AGC SELECTION");
        prefix = "L";
        token = "AGC";
        parent = aParent;
    }
 
    /**
     * Given the rigcaps reply (String source), determine if this control 
     * capability exists.
     * @param source 
     * @return boolean true when capability exists.
     */
    public boolean enableIfCapable(String source){
        String search = "(?ism).*^Set level:.*?AGC\\(";
        boolean hasLevelValue = true; // This control has Level Value:
        return enableCap(source, search, hasLevelValue);
    }
    
    @Override
    public void setContent(
            String label,
            int start,
            int end,
            int step,
            int xlow,
            int xhigh,
            int ylow,
            int yhigh,
            int initial) {
        // Make this work for IC-7100 ONLY.
        removeAllItems();
        valueLabel = label;
        addListItem("AGC-F", 2, "2");
        addListItem("AGC-M", 5, "5");
        addListItem("AGC-S", 3, "3");       
        setSelectedIndex(0);
        dialog.setNewData(getChoiceStrings());
    }
    @Override
    public void setButtonText(String value) {
        String str = value;
        String formattedText = String.format("%-23S ...", str);
        setText(formattedText);
        getAccessibleContext().setAccessibleDescription(str+" selected");
    }
    
    
}
