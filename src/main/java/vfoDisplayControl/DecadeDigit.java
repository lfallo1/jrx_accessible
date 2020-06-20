/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;


import java.awt.Color;
import java.awt.Font;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.text.ParseException;
import java.util.ArrayList;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.JFormattedTextField;
import javax.swing.text.MaskFormatter;

/**
 * Implements a decade digit within a multi-digit numeric display.
 * 
 * It is one digit wide and has the range of 0 to 9 which wraps around
 * when incremented or decremented past its range.  The model is
 * DecadeModel which takes care of the wrap around and passing on the
 * carry to adjacent decade digits.  It was originally developed/used for a 
 * JSpinner version of the display.
 * 
 * @author Coz
 */
public class DecadeDigit extends JFormattedTextField
        implements Accessible, FocusListener, MouseWheelListener, 
        MouseListener, KeyListener  {
    
    DecadeModel model;
    VfoDisplayControl frameGroup;
    final static Font DIGITS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 30);
    final static Color NON_ZERO_COLOR = new Color(0,192,0);
    final static Color ZERO_COLOR = new Color(0,64,0);
    protected double fontScale = 0.0;
    final static String VALUE_CHANGE = "valueChange";
    MaskFormatter maskFormatter;
   
    public DecadeDigit(VfoDisplayControl group, double scale) {
        super();
        fontScale = scale;
        frameGroup = group;
        try {
            maskFormatter = new MaskFormatter("#");
            this.setFormatter(maskFormatter);
        } catch (ParseException e) {
            System.out.print(e);
            System.out.println("No formatter was created for the DecadeDigit.");
        }
        setModel(new DecadeModel(0,this));
        setToolTipText("Lou, use the force!");
        // Set foreground numeral color Green. Set background transparent.        
        setFocusable(true);
        setForeground(Color.GREEN);
        Color backTransparent = new Color(0,0,0,0);
        setBackground(backTransparent);
        setEditable(false);        
        setOpaque(false);
        addMouseWheelListener(this);
        addMouseListener(this);
        addKeyListener(this);
        addFocusListener(this);
        addPropertyChangeListener(VALUE_CHANGE, frameGroup);                                 
        setValue(this.getModel().getValue());
        requestFocus();
    }
    
    public double getFontScale() {
        return fontScale;
    }
    
    public void setFontScale( double scale ) {
        fontScale = scale;
    }
    private void setModel(DecadeModel digitModel) {
        model = digitModel;
    }

    public DecadeModel getModel() {
        return model;
    }
    
    public static String getName(int powerOf10){
        assert(powerOf10 < VfoDisplayControl.QUANTITY_DIGITS);
        String[] name = { "Ones","Tens","Hundreds","Thousands",
            "Ten thousands","hundred thousands","millions",
            "ten millions","Hundred millions","Billions"};
        String accName = name[powerOf10]+" digit ";
        return accName;
    }
    /**
     * Given the remainder of the frequency after mod 10 operation, dim the
     * digit when the remainder is zero.
     * 
     * @param v is remainder after mod 10 operation.
     */
    public void setBright(boolean v) {
        setForeground(v?NON_ZERO_COLOR:ZERO_COLOR);
    }

    // The only way for a user to set a decade is by linking DecadeDigits.
    private void setDecade(int powerOfTen) {
        DecadeModel myModel = (DecadeModel)getModel();
        myModel.setDecade(powerOfTen); 
    }
    
    /**
     * Method sets the accessible name, accessible description and the jcomponent
     * name based on the model's decade.  Decade zero is set by default upon
     * construction of the DecadeDigit. All other decades are set by linking
     * the models; @see method linkToNextHigherDecade().
     */
    public void setAccessibleInfo(){
        DecadeModel myModel = (DecadeModel)getModel();
        int powerOfTen = myModel.getDecade();
        AccessibleContext ftfContext = this.getAccessibleContext();
        String name = getName(powerOfTen)+" JFormattedTextField";
        ftfContext.setAccessibleName(name);
        this.setName(name);
        ftfContext.setAccessibleDescription(
            "Up and down arrows change value; Left and right arrows traverse digits.");
    }
    
    // Start linking DecadeDigits with the lowest(rightMost) digit.
    private void linkToNextHigherDecade(DecadeDigit higherDecadeDigit) {
        higherDecadeDigit.setDecade(getDecade() + 1);
        DecadeModel low = (DecadeModel)this.getModel();
        DecadeModel high = (DecadeModel)higherDecadeDigit.getModel();
        low.setLinkedModel(high);
    }
    
    public int getDecade() {
        return ((DecadeModel) getModel()).getDecade();
    }
    
    public static void linkAllDigits(ArrayList<DecadeDigit> freqDigits, int arraySize) {
        int limit = arraySize - 1;
        for (int count = 0 ; count < limit; count ++)
            freqDigits.get(count).linkToNextHigherDecade(freqDigits.get(count+1));           
    }
    
    
    /**
     * There are two representations of the current value of the field.  One is
     * the textual representation in the textField which is displayed upon 
     * commit.  The other is the currentValue stored in the DecadeModel.  The
     * idea is to keep them in synch or die a painful death.  In any case, the
     * currentValue of the model always wins.  This method sets them both to the
     * same value.  
     * 
     * @param obj Object representing the numeric value.
     */
    @Override
    public void setValue(Object obj) {
        int oldValue = (Integer)getValue();
        int value = (Integer) obj;
        this.getModel().setValue(value);
        super.setValue(obj);
        this.firePropertyChange(VALUE_CHANGE, oldValue, value );
    }
    
    /**
     * Must override this method because sometime, somewhere, someone will call
     * the method and the two values for the field better match.
     * 
     * @return object representing JFormatedTextField textField.
     */
    @Override
    public Object getValue() {
        assert (this.getModel() != null);
        Object ftfObj = super.getValue();
        if (ftfObj == null) {
            // Super does not have a value yet.
            return (Integer)0;
        }
        DecadeModel digitModel = getModel();
        if ( digitModel == null) {
            //It's too early in the process.  We don't have a model yet.
            return super.getValue();
        }
        Object modelObj = (Integer) digitModel.getValue();
        int ftfValue = (Integer)ftfObj;
        int modelValue = (Integer)modelObj;
        assert(ftfValue == modelValue);
        return ftfObj;
    }
    

    @Override
    public void focusGained(FocusEvent e) {
        System.out.println("DecadeDigit :"+ getName() + " received focus.");
    }

    @Override
    public void focusLost(FocusEvent e) {
        
    }
    
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        Object oldValue = this.getValue();
        int oldInt = (Integer)oldValue;
        int v = e.getWheelRotation();
        int newInt = 0;
        if (v < 0) {
            newInt = model.getPreviousValue();            
        } else {            
            newInt = model.getNextValue();
        }
        this.setValue((Integer)newInt);
        //firePropertyChange(VALUE_CHANGE, oldInt, newInt);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            Object oldValue = this.getValue();
            int oldInt = (Integer)oldValue;
            int my = e.getY();
            int cy = getHeight() / 2;
            int newInt = 0;
            if (my < cy) {
                newInt = model.getNextValue();
            } else {
                newInt = model.getPreviousValue();
            }
            this.setValue((Integer)newInt);
            //firePropertyChange(VALUE_CHANGE, oldInt, newInt);       
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        Object oldValue = this.getValue();
        int oldInt = (Integer)oldValue;
        if (key == KeyEvent.VK_UP) {
            int newInt = model.getNextValue();
            Object obj = (Integer)newInt;
            this.setValue(obj);
            //firePropertyChange(VALUE_CHANGE, oldInt, newInt);
        }
        if (key == KeyEvent.VK_DOWN) {
            int newInt = model.getPreviousValue();
            Object obj = (Integer)newInt;
            this.setValue(obj);
            //firePropertyChange(VALUE_CHANGE, oldInt, newInt);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

}

    

