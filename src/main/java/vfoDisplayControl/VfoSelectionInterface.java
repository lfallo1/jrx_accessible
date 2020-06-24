/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;

import com.cozcompany.jrx.accessibility.JRX_TX;
import java.awt.Color;
import java.awt.Container;
import java.text.DecimalFormat;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextField;

/**
 * This class is a re-entrant interface to the radio VFO's using locks to give 
 * results to any thread as to the selected VFO and its frequency and is 
 * the interface to change the selected JRadioButtonMenuItem state and
 * the transmit / receive VFO via software.  
 * 
 * In order to operate both the rig panel and rigctl together, the rig must have
 * a way to :
 * 1) Get radio receive vfo: [VFO_A | VFO_B]. In simplex, this VFO is the
 * transmit and receive frequency.
 * 2) Get current VFO OperatingState [ Simplex | Duplex | Split ]
 * 3) Get the receive frequency.
 * 4) Get the transmit frequency.
 * 
 * Version 4.0 of Hamlib supports the Icom IC-7100 with commands that meet these
 * specifications.  Version 3.3 does not.  
 * 
 * 
 * So this interface has this contract:
 * 1) Requests for the current state ARE requests to the radio because the rig
 * panel can be used to change anything at any time.  Therefore, no internal
 * state is kept in this interface.  It is always requested directly from the rig.
 * 2) Get the vfo operating mode and frequencies on startup and on a periodic
 * basis.  This periodic update already exists, but did not include the vfo
 * operating states.
 * 2) Handle requests to change the vfo operating state and frequencies.  These 
 * will change the rig settings and are reflected on the rig panel.  Changes made
 * on the rig panel will change the app control settings when the periodic update
 * occurs.
 * 3) This interface is intended to be the sole software class that can effect
 * those changes, by calls from the UI components and by polling the rig.
 * 4) Since there are multiple threads for scanning and the like, make the
 * changes thread safe.
 * 5) The VFO operating state must be one of [Simplex, Duplex, Split]. The rig
 * allows SPLIT and DUP at the same time.  If that is found to be the case on
 * startup, then SPLIT will be turned off.  That is an arbitrary choice.  
 *    If, at any time after startup, Split and Dup are both found to be on, then 
 * the current app control state will determine what happens.  There can only be 
 * one state.  I see it as an operating mistake to have DUP on and SPLIT on at 
 * the same time.
 * 
 * 
 * The app vfo choice state machine is in the JRadioButtonGroup which enforces 
 * selection of only one of the group's button at a time.
 * 
 * The app vfo operating state machine is in the JRadioButtonGroup which
 * enforces selection of only one of the group's operating states at a time.
 * 
 * A textfield for each VFO is updated with its frequency and its background is
 * grayed for the unselected (Tx) VFO.
 * 
 * @author Coz
 */
public class VfoSelectionInterface {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();    
    JRadioButtonMenuItem vfoA;
    JRadioButtonMenuItem vfoB;
    JTextField frequencyVfoA;
    JTextField frequencyVfoB;
    static Color SELECTED_COLOR = Color.WHITE; 
    static Color UNSELECTED_COLOR = Color.LIGHT_GRAY;
    JRX_TX aFrame;
    /** 
     * opState is the VFO operating state which can be one of
     * {Simplex | Duplex | Split}.  These are the common ham operating terms.
     */
    public enum opState { SIMPLEX, DUPLEX, SPLIT };
    
    /**
     * vfoChoice is one of VFOA or VFOB which are the actual arguments to the
     * rigctl protocol.  They are directly converted to text arguments.
     */
    public enum vfoChoice { VFOA, VFOB };
    public enum duplexChoice { PLUS, MINUS };
    
    
    
    
 
    public VfoSelectionInterface(JRX_TX frame, 
            JRadioButtonMenuItem a, JRadioButtonMenuItem b, 
            JTextField freqA, JTextField freqB) {
        
        frequencyVfoA = freqA;
        frequencyVfoB = freqB;
        vfoA =  a;
        vfoB =  b;
        aFrame = frame;
        // Make the vfo item selection exclusive.
        Container container = a.getParent();
        ButtonGroup group = new ButtonGroup();
        group.add(a);
        group.add(b);
        //Menu items must be in the same group to be exclusively selected.
        //assert (vfoA.getModel().getGroup()==vfoB.getModel().getGroup());
    }
    

    private String askRadio(String ask){
        String sf = " ";    
        try {
            sf = aFrame.sendRadioCom(ask, 1, false);
        }
        catch (Exception e) {            
        }
        return sf;
    }
    
    public void setTextVfoA(long hertz) {           
        frequencyVfoA.setText("  VFO A   "+ Double.toString(((double)hertz) / 1.E06)+ "  ");
    }
    public void setTextVfoB(long hertz) {           
        frequencyVfoB.setText("  VFO B   "+ Double.toString(((double)hertz) / 1.E06)+ "  ");
    }

    
    
    public long getRxFrequency() {
        lock.readLock().lock(); 
        long value = 0;
        String sf = askRadio("f");
        if (sf == null) 
            value = 0;
        else 
            value = Long.parseLong(sf);
        lock.readLock().unlock(); 
        return value;       
    }
    
    // For compatibility with existing VfoDisplayControler.
    public long getSelectedVfoFrequency() {
        return getRxFrequency();
    }

       
    public opState getVfoOperatingState() {
        opState op = opState.SIMPLEX;
        lock.readLock().lock();
        if (isVfoOpStateSplitOn())
            op = opState.SPLIT;
        else if (isVfoOpStateDupOn())
            op = opState.DUPLEX;
        else op = opState.SIMPLEX;
        lock.readLock().unlock();
        return op;
    }
    
    public String getVfoName(vfoChoice choice) { 
        return choice.toString();  
    }

    /**
     * Set split VFO operating mode.  This means that one VFO is the Rx freq
     * and the other VFO is the Tx freq.  Technically you can operate two
     * different modes, one on each VFO.  
     * 
     * Uses rigctl command:
     * S: set_split_vfo   (Split,TX VFO) where Split is an integer  [1|0]
     * where 1 means ON and 0 means OFF.  TX VFO is [VFOA|VFOB].
     * 
     * @param txChoice either VFO_A or VFO_B
     */   
    public boolean setVfoOpStateSplitOn(vfoChoice txChoice) {
        lock.readLock().lock();
        // Technically you must turn repeater duplex off.
        if (isVfoOpStateDupOn()) {
            setVfoOpStateDupOff();
        }
        String commArgs = new String(
                "S 1 "+getVfoName(txChoice));
        String reply = askRadio(commArgs);
        boolean success = (reply != null);
        lock.readLock().unlock();
        return success;       
    }
    
    public boolean setVfoOpStateSplitOff() {
        lock.readLock().lock();
        String commArgs = new String("S 0 VFOA");  // VFO does not matter.
        String reply = askRadio(commArgs);
        boolean success = (reply != null);
        lock.readLock().unlock();        
        return success;       
    }
    
    public boolean isVfoOpStateSplitOn() {
        lock.readLock().lock();
        boolean splitOn = false;
        boolean error = true;
        String commArgs = new String("s");  
        // Returns "Protocol error" when on 2M DUP.
        // Returns "Split: 0 \nTX VFO: VFOB" when on hf and split off.
        // Returns "Split: 1 \nTX VFO: VFOB" when on hf and split on.
        String reply = askRadio(commArgs);
        if (reply != null) {
            error = reply.matches("(?i).*Protocol error");
        } 
        if (!error) {
            String splitSearch = "?i).*Split: 1";
            splitOn = reply.matches(splitSearch);            
        }
        lock.readLock().unlock();   
        return splitOn;        
    }
    
    public boolean setVfoOpStateDupPlus() {
        lock.readLock().lock();
        String commArgs = new String("R +");
        String reply = askRadio(commArgs);
        boolean success = (reply != null);
        lock.readLock().unlock();   
        return success;
    }
    
    public boolean setVfoOpStateDupMinus() {
        lock.readLock().lock();
        String commArgs = new String("R -");
        String reply = askRadio(commArgs);
        boolean success = (reply!=null);
        lock.readLock().unlock(); 
        return success;
    }
    
    public boolean setVfoOpStateDupOff() {
        lock.readLock().lock();
        String commArgs = new String("R 0");        
        String reply = askRadio(commArgs);
        boolean success = (reply != null);
        lock.readLock().unlock(); 
        return success;        
    }
    
    public boolean isVfoOpStateDupOn() {
        lock.readLock().lock();
        boolean shiftOn = false;
        String commArgs = new String("r");  
        // Returns "Protocol error" when on hf band.
        // Returns "Shift: + " when on vhf and DUP+.
        // Returns "Split: - " when on vhf and DUP-.        
        String reply = askRadio(commArgs);
        if (reply != null) {
            String dupSearch = "(?i).*Rptr Shift: [\\+\\-]";
            shiftOn = reply.matches(dupSearch);
        }
        lock.readLock().unlock(); 
        return shiftOn;                
    }
    
    public boolean isVfoDuplexPlus() {
        lock.readLock().lock();
        boolean dupPlus = false;
        String commArgs = new String("r");  
        // Returns "Protocol error" when on hf band.
        // Returns "Shift: + " when on vhf and DUP+.
        // Returns "Split: - " when on vhf and DUP-.        
        String reply = askRadio(commArgs);
        if (reply != null) {
            String dupPlusSearch = "(?i).*Rptr Shift: \\+";
            dupPlus = reply.matches(dupPlusSearch);
        }
        lock.readLock().unlock(); 
        return dupPlus;        
    }
    
    public void setVfoStateSimplex() {
        lock.readLock().lock();  
        setVfoOpStateSplitOff();
        setVfoOpStateDupOff();
        lock.readLock().unlock();         
    }
    public void setRxVfo(vfoChoice choice) {
        lock.readLock().lock();        
        String commArgs = new String("V "+ getVfoName(choice));
        String reply = askRadio(commArgs);        
        lock.readLock().unlock(); 
    }
    
    public boolean setVfoASelected(){
        boolean success = true;  
        String commArgs = new String("V " + getVfoName(vfoChoice.VFOA));
        String reply = askRadio(commArgs);
        success = (reply!= null);
        vfoA.setSelected(true);
        frequencyVfoA.setBackground(SELECTED_COLOR);
        frequencyVfoB.setBackground(UNSELECTED_COLOR);
        return success;
    }
    
    public boolean setVfoBSelected() {
        boolean success = true; 
        String commArgs = new String("V " + getVfoName(vfoChoice.VFOB));
        String reply = askRadio(commArgs);
        success = (reply!= null);
        vfoB.setSelected(true);
        frequencyVfoB.setBackground(SELECTED_COLOR);
        frequencyVfoA.setBackground(UNSELECTED_COLOR);
        return success;
    }
        
    
    /**
     * Determine which VFO is selected, the Rx VFO.
     * 
     * @return vfoChoice
     */
    public vfoChoice getRxVfo() {
        lock.readLock().lock();
        vfoChoice choice;
        long freq1 = getRxFrequency();               
        setVfoASelected();      
        long freq2 = getRxFrequency();          
        // If the freqs are the same, VFOA is rxVFO.
        if ( freq1 == freq2 ) {
            choice = vfoChoice.VFOA;
            setTextVfoA(freq2);  // VFOA is selected
            frequencyVfoA.setBackground(SELECTED_COLOR);
            // Get VFO B freq
            setRxVfo(vfoChoice.VFOB);
            long freqB = getRxFrequency();
            // Set VFO B text field freq.
            setTextVfoB(freqB);            
            // Gray VFO B text field.
            frequencyVfoB.setBackground(UNSELECTED_COLOR);           
            // Set VFO A selected.
            setRxVfo(vfoChoice.VFOA);
        }
        else
            choice = vfoChoice.VFOB; //VFOB is selected;
            setTextVfoB(freq1);
            frequencyVfoB.setBackground(SELECTED_COLOR);
            setTextVfoA(freq2);
            frequencyVfoA.setBackground(UNSELECTED_COLOR);
        lock.readLock().unlock(); 
        return choice;
    }
    
    public long getVfoAFrequency() {
        lock.readLock().lock();
        long frequencyHertz = 0;
        // get current rx freq
        long freq1 = getRxFrequency();            
        // set current vfo to A
        setVfoASelected();     
        // get current rx freq
        long freq2 = getRxFrequency();
        if ( freq1 == freq2 ) {
            // Current vfo is VFOA.
            frequencyHertz = freq2;
            setTextVfoA(freq2);  // VFOA is selected
            frequencyVfoA.setBackground(SELECTED_COLOR);
            frequencyVfoB.setBackground(UNSELECTED_COLOR);  
        } else {
            // Selected vfo had been VFOB.
            setVfoBSelected();
            setTextVfoB(freq1);
            frequencyVfoB.setBackground(SELECTED_COLOR);
            frequencyVfoA.setBackground(UNSELECTED_COLOR);
        }
        lock.readLock().unlock(); 
        return frequencyHertz;
    }
       
     public long getVfoBFrequency() {
        lock.readLock().lock();  //blocks until lock is available.     
        long frequencyHertz = 0;
        // get current rx freq
        long freq1 = getRxFrequency();            
        // set current vfo to B
        setVfoBSelected();     
        // get current rx freq
        long freq2 = getRxFrequency();
        if ( freq1 == freq2 ) {
            // Selected VFO is VFOB.
            frequencyHertz = freq2;
            setTextVfoB(freq2);  // VFOB is selected
            frequencyVfoB.setBackground(SELECTED_COLOR);
            frequencyVfoA.setBackground(UNSELECTED_COLOR);
        } else {
            // Selected vfo had been VFOA.
            setVfoASelected();
            setTextVfoA(freq1);
            frequencyVfoA.setBackground(SELECTED_COLOR);
            frequencyVfoB.setBackground(UNSELECTED_COLOR);  
        }
        lock.readLock().unlock(); 
        return frequencyHertz;
    }

    public boolean vfoA_IsSelected() {
        boolean isVfoA = true;
        vfoChoice choice = getRxVfo();
        isVfoA = (choice == vfoChoice.VFOA);
        return isVfoA;
    }    
 
   /**
     * Write the given frequency to the currently selected radio VFO.
     * @return true when frequency successfully communicated to radio.
     * @param frequencyHertz
    */
    public boolean writeFrequencyToRadioSelectedVfo(long frequencyHertz) {        
        boolean success = true;
        lock.writeLock().lock();  //blocks until lock is available.
        String commArgs = new String("F "+Long.toString(frequencyHertz));        
        String reply = askRadio(commArgs);
        success = (reply != null);
        lock.writeLock().unlock();
        // Now update the app controls.
        getVfoAFrequency();
        getVfoBFrequency();       
        return success;
    }
     
    public boolean writeFrequencyToRadioVfoA(long frequencyHertz) {
        boolean success = false;
        lock.writeLock().lock();  //blocks until lock is available.
        long freq1 = getRxFrequency();               
        setVfoASelected();      
        long freq2 = getRxFrequency();          
        // If the freqs are the same, VFOA is rxVFO.
        if ( freq1 == freq2 ) {
            // VFOA is the selected VFO.
            writeFrequencyToRadioSelectedVfo(frequencyHertz);
            setTextVfoA(frequencyHertz);
            frequencyVfoA.setBackground(SELECTED_COLOR);
            frequencyVfoB.setBackground(UNSELECTED_COLOR);
        } else {
            // VFOB was the selected VFO before it was changed to VFOA.            
            writeFrequencyToRadioSelectedVfo(frequencyHertz);
            setTextVfoA(frequencyHertz);
            frequencyVfoB.setBackground(SELECTED_COLOR);
            frequencyVfoA.setBackground(UNSELECTED_COLOR);
            setVfoBSelected();
            setTextVfoB(freq1);
        }
       lock.writeLock().unlock();
        return success;
    }
     
    public boolean writeFrequencyToRadioVfoB(long frequencyHertz) {
        boolean success = false;
        lock.writeLock().lock();  //blocks until lock is available.
        long freq1 = getRxFrequency();               
        setVfoBSelected();      
        long freq2 = getRxFrequency();          
        // If the freqs are the same, VFOB is rxVFO.
        if ( freq1 == freq2 ) {
            // VFOB is the selected VFO.
            writeFrequencyToRadioSelectedVfo(frequencyHertz);
            setTextVfoB(frequencyHertz);
            frequencyVfoB.setBackground(SELECTED_COLOR);
            frequencyVfoA.setBackground(UNSELECTED_COLOR);
        } else {
            // VFOA was the selected VFO before it was changed to VFOB.           
            writeFrequencyToRadioSelectedVfo(frequencyHertz);
            setTextVfoB(frequencyHertz);
            setVfoASelected();
            setTextVfoA(freq1);
            frequencyVfoA.setBackground(SELECTED_COLOR);
            frequencyVfoB.setBackground(UNSELECTED_COLOR);            
        }
       lock.writeLock().unlock();
        return success;

    }
     
     
     
   
    
////////////////////////////////////////////Working Below here.....    
    public void setVfoSplitTxDelta(vfoChoice txChoice, double splitDelta) {
        // This command is OK even if we are NOT in SPLIT state.
        String commArgs = new String(
                " "+Double.toString(splitDelta)+" "+getVfoName(txChoice));
        
        System.out.println("setVfoSplitTxDelta() is not done yet");

        //String reply = askRadio(commArgs);       
    }
      
    /**
     * Given which Vfo to access, write the given frequency to the radio.
     * @return true when frequency successfully communicated to radio.
     * @param frequencyHertz 
     * @param isVfoA
     * WANT TO DELETE THIS METHOD  DO NOT USE iT.........
    */
    public boolean writeXFrequencyToRadio(long frequencyHertz, boolean isVfoA) {
        boolean success = true;
        lock.writeLock().lock();  //blocks until lock is available.
    
        // Simulate sending freq value to Radio for testing.
        // Update the radio frequency text field.
        double mhz = (double) frequencyHertz;
        mhz = mhz / 1000000.0;
        DecimalFormat decimalFormat = new DecimalFormat("#.000000");
        String numberAsString = decimalFormat.format(mhz);
        if (isVfoA)
            frequencyVfoA.setText(numberAsString);
        else
            frequencyVfoB.setText(numberAsString);

        lock.writeLock().unlock();
        return success;
    }


    
    public boolean copyAtoB() {
        boolean success = true;
        long freqA = getVfoAFrequency();
        success = writeFrequencyToRadioVfoB(freqA);       
        return success;       
    }
    
    public boolean swapAwithB() {
        boolean success = true;
        long oldFreqA = getVfoAFrequency();
        long oldFreqB = getVfoBFrequency();
        writeFrequencyToRadioVfoA(oldFreqB);
        writeFrequencyToRadioVfoB(oldFreqA);           
        return success;
    }
}

