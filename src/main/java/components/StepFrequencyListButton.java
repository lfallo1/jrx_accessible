/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package components;

import com.cozcompany.jrx.accessibility.JRX_TX;
import java.util.Map;
import java.util.TreeMap;

/**
 * Class replaces scanStepComboBox with voiceOver compliant RWListBox.
 * custom creation code: new RWComboBox(this, null, null);
 * toolTipText: Scan Frequency step size (❃)
 * @author Coz
 */
public class StepFrequencyListButton extends RWListButton {
    Map<String, Double> scanSteps = null;
    final Integer INIT_STEP = 12;

    public StepFrequencyListButton(JRX_TX parent) {
        super(parent, "", "", "FREQUENCY STEP", "SELECT FREQUENCY STEP");
        super.numericMode = true;
    }
    
    public void init() {
        double bv;
        double[] msteps = new double[]{1, 2, 5};
        String sl;
        initialize();
        removeAllItems();
        scanSteps = new TreeMap<>();
        bv = 1;
        for (int p = 0; p <= 7; p++) {
            for (double lv : msteps) {
                double v = bv * lv;
                sl = stepLabel(v);
                scanSteps.put(sl, v);
                addListItem(sl, v, String.valueOf(v));
            }
            bv *= 10;
        }
        setIndex(INIT_STEP);
        choices = getChoiceStrings();
        dialog.setNewData(choices);
        
    }
    private String stepLabel(double v) {
        String[] labels = new String[]{"Hz", "kHz", "MHz"};
        double tv = 1;
        int i;
        for (i = 0; i < labels.length; i++) {
            if (v < tv * 1000) {
                break;
            }
            tv *= 1000;
        }
        String s = String.format("%.0f %s", v / tv, labels[i]);
        return s;
    }
    
    public double getScanStep() {
        return scanSteps.get(getSelectedItem());
    }
    
    public void setIndex(int index) {
        index = Math.max(0, index);
        index = Math.min(index, scanSteps.size() - 1);
        setSelectedIndex(index);  // Sets button text.
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

}
