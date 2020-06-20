/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;

import vfoDisplayControl.DecadeDigit;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;


/**
 * Implements a one digit wrap around digit with recursive
 * carry to higher decades.  
 * 
 * This model computes the next value base ten with a
 * step size of one.
 * 
 * @author Coz
 */
final public class DecadeModel implements ChangeListener, ActionListener {
    protected DecadeModel linkedModel = null;
    private int decade;
    private DecadeDigit digit;
    int initialValue = 0;
    int minimumValue = 0;
    int maximumValue = 9;
    int currentValue = initialValue;
    Object valueObj = new Object();
        
    public DecadeModel(int aPowerOfTen, DecadeDigit aDigit) {
        decade = aPowerOfTen;
        digit = aDigit;
        
    }

    public void setLinkedModel(DecadeModel aLinkedModel) {
        linkedModel = aLinkedModel; 
    }
        
    protected void setDecade(int n) {
        decade = n;
    }
    protected int getDecade() {
        return decade;
    }
    
    public void setValue(int n) {
        currentValue = n;
        valueObj = (Integer)currentValue;
        assert(currentValue < (maximumValue+1));
        assert(currentValue > (minimumValue-1));
    }
    
    public Object getValue() {
        valueObj = (Integer)currentValue;
        return valueObj;
    }
    
    /*
     * Implement digit wrap around and decade recursive increment.
    */
    public int getNextValue() {
        currentValue = currentValue+1;
        if (currentValue > maximumValue) {                   
            // The digit wants to go higher than maximum.
            // Wrap the digit around to minimum.  
            setValue( minimumValue );
            if (linkedModel != null) {
                int linkedModelValue = linkedModel.getNextValue();
                // @todo limit recursion
                linkedModel.setValue(linkedModelValue);             
                DecadeDigit nextDecadeDigit = linkedModel.digit;
                Object obj = linkedModelValue;
                nextDecadeDigit.setValue(obj);
                
            } else {
                // Let the VfoDisplayPanel wrap around.
            }
        }
        return currentValue;
    }


    public int getPreviousValue() {
        currentValue = currentValue-1;
        if(currentValue < 0) {
            // The digit wants to go lower than minimum.
            // Wrap the digit around to maximum.
            setValue(maximumValue);
            if(linkedModel != null) {
                int linkedModelValue = linkedModel.getPreviousValue();
                linkedModel.setValue(linkedModelValue);                                
                DecadeDigit nextDecadeDigit = linkedModel.digit;
                Object obj = linkedModelValue;
                nextDecadeDigit.setValue(obj);
            } else {
                // Let the VfoDisplayPanel wrap around.
            }
        }  
        return currentValue;       
    }    

    @Override
    public void stateChanged(ChangeEvent e) {
    }

    @Override
    public void actionPerformed(ActionEvent e) {
    }
}
