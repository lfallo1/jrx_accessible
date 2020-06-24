/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;



import java.awt.AWTKeyStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import javax.accessibility.AccessibleContext;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE;


/**
 * Implements a ten digit integer display/control where each digit can be 
 * manipulated, wraps around and carries to the next digit appropriately.
 * 
 * The VfoDisplayControl digits can be incremented and decremented by the
 * up and down arrows. The left and right arrows traverse the decades. A digit
 * is given a new description upon focus which includes the decade of the 
 * digit and the current VFO frequency in Mhz.  VoiceOver announces that new
 * description.  There are shortcut keys ALT-A and ALT-B to select either
 * VFO A or VFO B.  These "radio menu buttons" choose which radio VFO is controlled
 * by manipulating the VFO Display Control digits.  VoiceOver DOES NOT announce
 * at all upon reaching the JInternalFrame menu and menu items.  That is a bug
 * and am submitting it to Oracle with a simple example called JInternalFrameBug.
 * To overcome this voiceOver problem, a dialog is opened when the VFO is changed.
 * Blind users can use the OPT-A and OPT-B to choose VfoA and VfoB respectively
 * without having to navigate the menu items that have no audio feedback.
 * 
 * A recent revelation is that the aspect ratio of the vfo display is pretty much 
 * constant.  All the component sizes are calculated.  
 * 
 * Sighted users can click on the upper half of a digit to increment or lower
 * half to decrement.  The mouseWheel is a faster way to increment/decrement.
 * 
 * It is not possible to just type digits to enter a frequency in this app.  Each
 * digit is a formatted text field, but editing is disabled.  Editing introduces
 * many complications which include the question of when to commit.  It is far
 * easier and quicker to use the arrow keys.   That is why editing is disabled.
 * 
 * To scan the band, pick a decade digit and hold down the up/down arrow.
 * 
 * @author Coz
 */
final public class VfoDisplayControl extends GroupBox 
        implements PropertyChangeListener , ActionListener {
    
    protected ArrayList<DecadeDigit> freqDigits = null;
    protected ArrayList<BarnDoor> barnDoors = null;
    public final static int QUANTITY_DIGITS = 10;
    public final static int QUANTITY_DOORS = QUANTITY_DIGITS;
    JFrame aFrame;
    long sv_freq;
    long currentFrequency = 3563000L;
    long oldFrequency = 0;
    boolean inhibit = true;  // Inhibit interaction during construction.
    static Vector<Component> order;
    boolean silent = false;
    VfoSelectionInterface vfoState;    
    JPanel glassPane;       
    final float LITTLE_FONT_FUDGE = 0.90f;
    final float BIG_FONT_FUDGE = 0.90f;
    final int DIGIT_GAP = 0;


    public VfoDisplayControl(JFrame frame) {
        super();
        aFrame = frame;
        setClosable(false);
        setFocusCycleRoot(true);
        setFocusable(true);
        setResizable(true);
        Dimension minSize = new Dimension(300,200);
        setMinimumSize(minSize);
        Dimension maxSize = new Dimension(32000, 500);
        setMaximumSize(maxSize);
        AccessibleContext contextVfoControl = getAccessibleContext();
        contextVfoControl.setAccessibleName("V F O Display Control");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); //not a user operation.       
    }
    /**
     * Method to use glass pane as the dynamic content pane for the frame and
     * use the content pane for static / background information and graphics.
     * 
     * We are drawing the DecadeDigits on the glass pane.
     * We are drawing the BarnDoors  on the content pane.
     * 
     */
    public void setupPanes() {
        VfoDisplayControl display = this;
        //Rectangle frameBounds = display.getBounds();
        //Dimension frameSize = display.getSize();
        setupGlassPane(display);
        setupContentPane(display);

        adjustSize(display);     
        
    }
    
    /**
     * Create the glass pane panel and configure the layered panes that hold the
     * DecadeDigits.
     * 
     * The glass pane contains all the dynamic components, the DecadeDigits.
     * 
     * @param display 
     */
    public void setupGlassPane(VfoDisplayControl display) {
        Rectangle rootBounds = display.getRootPane().getBounds();
        Container contentPane = display.getContentPane();
        Rectangle contentBounds = contentPane.getBounds();
        
        contentPane.setLayout(null);
        display.setGlassPane(new JPanel());
        glassPane = (JPanel) display.getGlassPane();       
        glassPane.setLayout(new FlowLayout());
        glassPane.setBounds(contentBounds);
        //MUST HAVE THE FOLLOWING LINE FOR GLASS PANE TO BE TRANSPARENT!
        glassPane.setOpaque(false);       
        //Insets gInsets = glassPane.getInsets(); // insets are zero       
        //Insets mgInsets = layeredPaneMegahertz.getInsets(); // insets are zero        
        initDigits();
        insertDigitsIntoPane(glassPane);
    }
 
    /**
     * Create all ten DecadeDigits, initialize them ,store them in an ordered 
     * collection which is used to traverse the digits, then insert them into
     * three panels indicating scientific notation grouping.
     * 
     * These digits are stored in ascending decade order.  JRX code uses descending
     * order which makes the carry calculations much more efficient.  Chose to
     * use ascending order because that is the traversal order, but since there
     * are two collections of digits, may go back and copy JRX algorithm.
     * 
     * The one hertz digits are usually smaller on radio displays.  Use the
     * JRX proportions.
     */
    private void initDigits() {
        freqDigits = new ArrayList<>();
        freqDigits.add(new DecadeDigit(this, 0.7));    
        freqDigits.add(new DecadeDigit(this, 0.7));    
        freqDigits.add(new DecadeDigit(this, 0.7)); 
        freqDigits.add(new DecadeDigit(this, 1.0));
        freqDigits.add(new DecadeDigit(this, 1.0));    
        freqDigits.add(new DecadeDigit(this, 1.0));      
        freqDigits.add(new DecadeDigit(this, 1.0));
        freqDigits.add(new DecadeDigit(this, 1.0));  
        freqDigits.add(new DecadeDigit(this, 1.0));   
        freqDigits.add(new DecadeDigit(this, 1.0));
        assert(freqDigits.size() == QUANTITY_DIGITS);      
        
        // Link the digits by linking all the models.
        DecadeDigit.linkAllDigits(freqDigits, QUANTITY_DIGITS);
        // Save the focus order.        
        order = new Vector<>(QUANTITY_DIGITS);
        for (int iii=0; iii<QUANTITY_DIGITS; iii++) {
            // The order vector contains the formated text fields.
            Component ftf = freqDigits.get(iii);
            // Every ftf has unique accessible info based on decade. 
            ((DecadeDigit)ftf).setAccessibleInfo();
            order.add(ftf);
        }         
    }

    /**
     * On the glass pane, three panels hold the DecadeDigits that make up the
     * ten digit frequency display; create them and fill them with digits.
     */
    private void insertDigitsIntoPane(JPanel pane) {      
        
        pane.removeAll();
        ((FlowLayout) pane.getLayout()).setHgap(DIGIT_GAP); //snuggle horizontally
        pane.add(freqDigits.get(9));
        pane.add(freqDigits.get(8));
        pane.add(freqDigits.get(7));
        pane.add(freqDigits.get(6));
        pane.add(new DecimalPoint());
        
        pane.add(freqDigits.get(5));
        pane.add(freqDigits.get(4));
        pane.add(freqDigits.get(3));
        pane.add(new DecimalPoint());
        
        pane.add(freqDigits.get(2));
        pane.add(freqDigits.get(1));
        pane.add(freqDigits.get(0));
    }
       
   /**
     * Calculate VFO Display component sizes based on JLabel text strings.
     * 
     * @param resizedDisplay 
     */
    protected void adjustSize(VfoDisplayControl resizedDisplay) {
        Font bigFont = new Font("Monospace", Font.PLAIN, 100);
        Font littleFont = new Font("Monospace", Font.PLAIN, 70);

        JLabel longLabel = new JLabel("0000.000.");
        longLabel.setFont(bigFont);
        JLabel fourDigitLabel = new JLabel("0000");
        fourDigitLabel.setFont(bigFont);
        JLabel threeDigitLabel = new JLabel("000");
        threeDigitLabel.setFont(littleFont);
        
        Dimension longDim = longLabel.getPreferredSize();
        Dimension shortDim = threeDigitLabel.getPreferredSize();
        Dimension totalDim = new Dimension( longDim.width+shortDim.width, longDim.height);
        float desiredAspectRatio = (float)totalDim.height / (float)totalDim.width;
        
        if (resizedDisplay == null) return;  // too early...
        
        Rectangle displayRect = resizedDisplay.getGlassPane().getBounds();
        if (displayRect == null) return;  // too early...
        int displayWidth = displayRect.width;
        
        ///////////HACK
        //displayWidth = 630;
        
        if (displayWidth == 0) return; // too early...
        int displayHeight = displayRect.height;
        
        ////////////HACK
        //displayHeight = 115;
        
        float givenAspectRatio = (float)displayHeight / (float)displayWidth;
        Rectangle newBounds;
        if (givenAspectRatio < desiredAspectRatio) {
            // New dimension limited by given height so height stays the same.
            newBounds = displayRect;
            newBounds.width = (int)((float)displayRect.height / desiredAspectRatio);           
        } else {
            // New dimension limited by given width so width stays the same.
            newBounds = displayRect;
            newBounds.height = (int)((float)displayRect.width * desiredAspectRatio);
        }
        float scaleFactor = (float)newBounds.width / (float) totalDim.width;       
        int desiredLittleFontWidth = (int)(((float)shortDim.width/3.)*scaleFactor);
        int desiredLittleFontHeight = (int)((float)shortDim.height * scaleFactor);
        
        double fourDigitWidth = fourDigitLabel.getPreferredSize().getWidth();
        double fourDigitHeight = fourDigitLabel.getPreferredSize().getHeight();
        
        int desiredBigFontWidth = (int)(fourDigitWidth*scaleFactor/4.0);
        int desiredBigFontHeight = (int)(fourDigitHeight*scaleFactor);
        
        int littleFontSize = computeFontSize(desiredLittleFontWidth, desiredLittleFontHeight, "0", littleFont);
        int bigFontSize = computeFontSize(desiredBigFontWidth,  desiredBigFontHeight, "0", bigFont);
                
        Font bigDigitFont = new Font("Monospace", Font.PLAIN, bigFontSize );
        Font littleDigitFont = new Font("Monospace", Font.PLAIN, littleFontSize );
       
        for (int index=0; index<QUANTITY_DIGITS; index++) {
            DecadeDigit digit = freqDigits.get(index);            
            if (index <3) {                
                digit.setFont(littleDigitFont);
            } else {                
                digit.setFont(bigDigitFont);               
            } 
        }
        
        Dimension littleDigitDim = freqDigits.get(0).getPreferredSize();
        Dimension bigDigitDim = freqDigits.get(9).getPreferredSize();
        // Use big font for all the decimal points.
        DecimalPoint.setFonts(bigDigitFont);
        // Little digits are 0,1,2.
        // Little barn doors are 7,8,9.
        //Dimension bigDigitDim = new Dimension((int)(desiredBigFontWidth),  (int)(desiredBigFontHeight));
        //Dimension littleDigitDim = new Dimension((int)(desiredLittleFontWidth), (int)(desiredLittleFontHeight));
        for (int iii=0; iii<barnDoors.size(); iii++) {
            if ( iii > 6)
                barnDoors.get(iii).addShapes(littleDigitDim);
            else
                barnDoors.get(iii).addShapes(bigDigitDim);
        }
        Container contentPane = resizedDisplay.getContentPane();
        Graphics g = contentPane.getGraphics();
        contentPane.paint(g);
    }
     
    

    /**
     * Given the label dimensions, the text, and the font, what is the font size 
     * that will fit?  Found something like this method on the internet.  Very nice.
     * 
     * @param fieldWidth
     * @param fieldHeight
     * @param text
     * @param font
     * @return fontSize integer
     */
    protected int computeFontSize( int fieldWidth, int fieldHeight, String text, Font font) {
        int stringWidth = getFontMetrics(font).stringWidth(text);
        int componentWidth = fieldWidth;
        // Find out how much the font can grow in width.
        double widthRatio = (double)componentWidth / (double)stringWidth;
        int newFontSize = (int)(font.getSize() * widthRatio);
        Font fontTry;
        int fontSizeToUse = newFontSize;
        for ( int size = newFontSize; size >0 ; size--) {
            fontTry = new Font("Monospace", Font.PLAIN, size );         
            // Pick a new font size so DecadeDigit will not be larger than the height of label.
            DecadeDigit digit = new DecadeDigit(this,1.0);
            digit.setFont(fontTry);
            Dimension dim = digit.getPreferredSize();
            if (dim.width <= fieldWidth && dim.height <= fieldHeight) {
                fontSizeToUse = size;
                break;
            }
        }
        return fontSizeToUse;
    }
    
    /**
     * All the dynamic components are added to the glass pane so the context
     * pane is just boilerPlate and there are no events to handle.
     * @param display 
     */
    public void setupContentPane(VfoDisplayControl display) {
        barnDoors = new ArrayList<BarnDoor>();
        Container pane = display.getContentPane();
        // Little digits are 0,1,2.
        // Little barn doors are 7,8,9.

        Dimension bigDigitDim = new Dimension(67, 127);
        Dimension littleDigitDim = new Dimension(47, 88);
      
        pane.setBackground(Color.BLACK);
        pane.setLayout(new FlowLayout());
        ((FlowLayout) pane.getLayout()).setHgap(DIGIT_GAP);
        
        for (int iii=0; iii<4; iii++) {
            BarnDoor door = new BarnDoor(bigDigitDim);
            door.addShapes(bigDigitDim);
            barnDoors.add(door);
            pane.add(door);
            door.setVisible(true);
        } 
        pane.add(new DecimalPoint());
       
        for (int iii=4; iii<7; iii++) {
            BarnDoor door = new BarnDoor(bigDigitDim);
            door.addShapes(bigDigitDim);
            barnDoors.add(door);
            pane.add(door);
            door.setVisible(true);
        }
        pane.add(new DecimalPoint());

        for (int iii=7; iii<10; iii++) {
            BarnDoor door = new BarnDoor(littleDigitDim);
            door.addShapes(littleDigitDim); 
            barnDoors.add(door);
            pane.add(door);
            door.setVisible(true);
        } 
        Graphics g = pane.getGraphics();
        pane.paint(g);
        assert (barnDoors.size() == QUANTITY_DOORS);
   }
        
    
    
    public void makeVisible(VfoSelectionInterface vfoInterface) {
        vfoState = vfoInterface;
        long selectedFreq = vfoState.getSelectedVfoFrequency();
        inhibit = false;
        frequencyToDigits(selectedFreq);        
        getGlassPane().setVisible(true);
        getContentPane().setVisible(true);        
        getContentPane().repaint();
    }

    
    public static Vector<Component> getTraversalOrder() {
        return order;
    }
    
    /**
     * Method returns the currently selected VFO frequency which is actually
     * provided by the vfoState class.  In legacy code, the request was made
     * of the Vfo itself so this helper method was provided.
     * 
     * @return the selected VFO's frequency.
     */
    public long getFreq() {
        return vfoState.getSelectedVfoFrequency();
    }       
    
    /**
     * Given a long representation of a frequency in hertz, set the decade
     * digits to display that frequency.
     * 
     * @param v A long representing frequency in Hertz
     */
    public void frequencyToDigits(long v) {
        if(inhibit) return;
        silent = true;  // Do not handle digit generated value changes.
        sv_freq = v;
        currentFrequency = sv_freq;
        long modulatedValue = v;
        // Expecting list ordered from LSD to MSD.      
        int size = freqDigits.size();
        for (int i = 0; i < size; i++) {
            DecadeDigit fd = freqDigits.get(i);
            fd.setValue( (int) (modulatedValue % 10));
            fd.setBright(modulatedValue != 0);
            modulatedValue /= 10;
        }
        silent = false;  // Enable digit change event handling.
    }

    /**
    * From the currently displayed digits in the VfoDisplayControl construct a
    * base ten representation and return it as a long.  Dim the leading zeroes.
    * 
    * @return the frequency in hertz shown by the collection of JSpinner digits.
    */    
    public long digitsToFrequency() {
        sv_freq = 0;
        if (!inhibit) {
            inhibit = true;
            freqDigits.forEach((dig) -> {
                DecadeModel  model = (DecadeModel) dig.getModel();
                Object value = model.getValue();
                String digitString = value.toString();
                Integer digit = Integer.valueOf(digitString);
                int decade = model.getDecade();
                sv_freq =  (long)Math.pow(10, decade) * digit + sv_freq ;
                dig.setBright(true); 
            });
            // Dim leading zeroes.
            for ( int ii = QUANTITY_DIGITS; ii>0; ii--) {
                DecadeDigit dig = freqDigits.get(ii-1);
                if ((Integer)dig.getValue() == 0) {
                    dig.setBright(false);
                } else {
                    break;
                }    
            }
        }
        inhibit = false;
        return sv_freq;
    }

   /**
     * Method called when the VfoA radio button changes the VFO selection with
     * the requirement to update the VFO display control with the
     * frequency read from the radio VFO A.
     * 
     * Turn off the VFO change handler while this update takes place.  Set focus
     * on the ones digit.
     * @return true.
     */
    public boolean loadRadioFrequencyToVfoA() {       
        boolean success = true;
        long freqHertz;
        String valString;
        // Simlate read freq from Radio VFO a.
        freqHertz = vfoState.getVfoAFrequency();            
        frequencyToDigits(freqHertz);
        if ( !vfoState.vfoA_IsSelected()) vfoState.setVfoASelected();
        //getTraversalOrder().get(0).requestFocus();
        return success;
    }
    /**
     * Method called when the VfoB radio button changes the VFO selection with
     * the requirement to update the VFO display control with the
     * frequency read from the radio VFO B.
     * 
     * Turn off the VFO change handler while this update takes place.  Set focus
     * on the ones digit.
     * @return true.
     */
    public boolean loadRadioFrequencyToVfoB() {       
        boolean success = true;
        long freqHertz;
        String valString;    
        // Simlate read freq from Radio VFO B.           
        freqHertz = vfoState.getVfoBFrequency();            
        frequencyToDigits(freqHertz);
        if ( vfoState.vfoA_IsSelected())  vfoState.setVfoBSelected();
        //getTraversalOrder().get(0).requestFocus();
        return success;
    }
     
    @SuppressWarnings("unchecked")
    public void setUpFocusManager() {
        // Make sure that the VfoDisplayControl is focus manager.
        // It appears that voiceOver StepInto is ignoring focus manager.
        setFocusCycleRoot(true);
        VfoDigitTraversalPolicy policy; 
        Vector<Component> order = getTraversalOrder();
        policy = new VfoDigitTraversalPolicy(order);
        setFocusTraversalPolicy(policy);
        setFocusTraversalPolicyProvider(true);
        setFocusable(true);
        setVisible(true);
        // Add focus traverse keys left and right arrow.
        // In this case, FORWARD is to the left.
        setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);

        Set set = new HashSet<>( 
            getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS ) );
       
        final AWTKeyStroke keyStrokeRight = KeyStroke.getKeyStroke("LEFT");
        set.add(keyStrokeRight) ;
        setFocusTraversalKeys(
            KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, set);
        
        set = new HashSet<>( getFocusTraversalKeys(
            KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS ) );
        final AWTKeyStroke keyStrokeLeft = KeyStroke.getKeyStroke("RIGHT");           
        set.add(keyStrokeLeft);
        setFocusTraversalKeys(
            KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, set );
        setFocusTraversalKeysEnabled(true);
                    
        assert(areFocusTraversalKeysSet(
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS) );
        
        assert( getFocusTraversalPolicy() != null);
        assert( isFocusCycleRoot());
        setEnabled(true);             
    }       

    /**
     * This listener could possibly called very early on so there is a "silent"
     * variable that is used to prevent handling events before components are
     * created. Also, when the VFO selection is changed, that variable is set
     * true to mute the barrage of changes.
     * 
     * @param evt 
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (silent) return;
        Object source = evt.getSource();
        DecadeDigit digit = (DecadeDigit)source;
        String name = digit.getName();
        // getValue() synchronizes the model and ftf values.
        int value = (Integer) digit.getValue();
        //System.out.print("PropertyChangeEvent :");
        //System.out.print(" source : " + name);
        //System.out.println(" ; getValue() returns :"+ value);        
        Component ftf = (Component)source;         
        // Update this ftf description with new frequency and decade name.
        // Update the debug panel frequency.           
        DecadeModel model = digit.getModel();
        DecadeModel decadeModel = (DecadeModel)model;
        int decade = decadeModel.getDecade();
        if( vfoState == null) {
            // We are in the contruction process. Too early.
            return;
        }
        // Change the field description so voiceOver will announce it.
        long freq = digitsToFrequency();
        vfoState.writeFrequencyToRadioSelectedVfo(freq);
        String vfoString = "VFO B";
        if (vfoState.vfoA_IsSelected()) vfoString = "VFO A";           
        //System.out.println("handleChangeEvent - model value: "+ String.valueOf(value)); 
        for ( int iii=0; iii<QUANTITY_DIGITS; iii++) {
            StringBuilder freqString = new StringBuilder("");
            freqString.append(vfoString+" Frequency "+Double.toString(((double)freq)/1000000.)+" Mhz; ");
            Component comp = order.get(iii);
            JFormattedTextField ftfield = (JFormattedTextField)comp;
            freqString.append(ftfield.getAccessibleContext().getAccessibleName());
            ftfield.getAccessibleContext().setAccessibleDescription(freqString.toString());
        }
        // Print out just this field's name and description.
        //String ftfName = ftf.getAccessibleContext().getAccessibleName();
        //System.out.println("ftf accessible name :"+ftfName);
        //String ftfDesc = ftf.getAccessibleContext().getAccessibleDescription();
        //System.out.println("ftf accessible description :"+ftfDesc);               
    }       

    public void vfoDisplayResized(java.awt.event.HierarchyEvent evt) {                                   
        adjustSize(this);
    }                                  
    

    @Override
    public void actionPerformed(ActionEvent e) {
        String actionString = e.getActionCommand();
        if (actionString == "Copy VFO A to VFO B") {
            vfoState.copyAtoB();
                 
            if (!vfoState.vfoA_IsSelected()) {
                long freqA = vfoState.getVfoAFrequency();
                frequencyToDigits(freqA);
            }
            JOptionPane.showMessageDialog(this,
                    "VFO A copied to VFO B",
                    "VFO A copied to VFO B",  // VoiceOver reads only this line.
                    JOptionPane.PLAIN_MESSAGE);
        } else if (actionString == "Swap VFO A with VFO B") {
            vfoState.swapAwithB();
                 
            if (vfoState.vfoA_IsSelected()) {
                long freqA = vfoState.getVfoAFrequency();
                frequencyToDigits(freqA);
            } else {
                long freqB = vfoState.getVfoBFrequency();
                frequencyToDigits(freqB);
                
            }
            JOptionPane.showMessageDialog(this,
                    "VFO A swapped with VFO B",
                    "VFO A swapped with VFO B",  // VoiceOver reads only this line.
                    JOptionPane.PLAIN_MESSAGE);            
        }
    } 
    
}
