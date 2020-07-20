/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import com.cozcompany.jrx.accessibility.JRX_TX;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JComboBox;

/**
 * Class replaces scandelayComboBox with voiceOver compliant RWListBox representing
 * the possible delays between scan channels.  These values do not change.
 * custom creation code: new RWComboBox(this, null, null);
 * toolTipText: Scan Step Period (❃)
 * @author Coz
 */
public class StepPeriodListButton extends RWListButton {
    public Map<String, Integer> timeSteps = null;
    final Integer INITIAL_SPEED_INDEX = 5;
    public StepPeriodListButton(JRX_TX parent) {
        super(parent, "", "", "STEP PERIOD", "CHOOSE A SCAN SPEED");
        super.numericMode = true;
        
    }

    public void init() {
        double bv;
        double[] msteps = new double[]{1, 2, 5};
        String sl;
        initialize();
        removeAllItems();
        timeSteps = new TreeMap<>();
        bv = 1;
        for (int p = 0; p <= 4; p++) {
            for (double lv : msteps) {
                double v = bv * lv;
                if (v >= 1000) {
                    sl = String.format("%d s", (int) (v / 1000));
                } else {
                    sl = String.format("%d ms", (int) v);
                }
                timeSteps.put(sl, (int) v);
                addListItem(sl, v, String.valueOf(v));
            }
            bv *= 10;
        }
        setIndex(INITIAL_SPEED_INDEX);
        choices = getChoiceStrings();
        dialog.setNewData(choices);
        
    }

    public double getTimeStep() {
        return timeSteps.get(getSelectedItem());
    }

    public void setIndex(int index) {
        index = Math.max(0, index);
        index = Math.min(index, timeSteps.size() - 1);
        setSelectedIndex(index);  // Sets button text.
    }

//    @Override
//    public void actionPerformed(ActionEvent evt) {
//        
//    }



    
}
