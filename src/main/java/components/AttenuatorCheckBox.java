/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import com.cozcompany.jrx.accessibility.JRX_TX;
import com.cozcompany.jrx.accessibility.RWCheckBox;

/**
 *
 * @author Coz
 */
public class AttenuatorCheckBox extends RWCheckBox {
    final int OFF_COMMAND = 0;
    final int ON_COMMAND = 12;
    JRX_TX myParent;
    
    public AttenuatorCheckBox(JRX_TX aParent) {
        super(aParent, "L", "ATT" );
        myParent = aParent;
    }
    @Override
    public void writeValue(boolean force) {
        if (!myParent.inhibit && !super.localInhibit && super.token != null) {
            super.state = isSelected();
            if (force || super.state != super.oldState) {
                String com = String.format("%s %s %s", super.prefix.toUpperCase(), super.token, super.state ? "12" : "0");
                myParent.sendRadioCom(com, 0, true);
                super.oldState = super.state;
            }
        }
    }
    @Override
    public void readValue() {
        if (super.token != null) {
            String com = String.format("%s %s", super.prefix.toLowerCase(), super.token);
            String s = myParent.sendRadioCom(com, 0, false);
            if (s != null) {
                super.state = s.equals("12");  //state is true when box is checked.
                super.oldState = super.state;
                super.localInhibit = true;
                setSelected(super.state);
                super.localInhibit = false;
            }
        }
    }
    /**
     * Given the rigcaps reply, determine if this control capability exists.
     * @param source 
     * @return boolean true when capability exists.
     */
    public boolean enableIfCapable(String source) {
        String search = "(?ism).*^Set level:.*?ATT\\(";
        boolean hasLevelValue = true; // This control has Level Value:
        return enableCap(source, search, hasLevelValue);
    }    
    
}
