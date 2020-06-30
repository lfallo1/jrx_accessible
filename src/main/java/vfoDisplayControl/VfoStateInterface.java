/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vfoDisplayControl;


import com.cozcompany.jrx.accessibility.JRX_TX;
import java.awt.Color;
import java.awt.Container;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextField;

/**
 * This class is a re-entrant interface to the radio VFO's using locks to give 
 * results to any thread as to the selected VFO and its frequency and is 
 * the interface to change the selected JRadioButtonMenuItem state and
 * the transmit / receive VFO via software.  
 * 
 * Operating both the rig panel and rigctl together requires many lengthy atomic
 * communications which manipulate the radio states in way that is visible on the
 * rig panel.  All this is done to overcome the missing "get current vfo" command.
 * 
 * Here is a design that requires all the VFO state control happens in this app.
 * If the panel is used to change selected VFO, then the this version of the
 * interface will not find out about it very soon.  There is just too much overhead
 * in tracking the changes that were made with the rig panel.  The additional
 * CPU load of voiceOver cripples the comms operation.
 * 
 * Since the goal is to provide an accessible interface for the blind operator,
 * this interface provides a low over-head design that meets these requirements:
 * 
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
 * 1) Upon startup the rig vfo states and operating modes are set according to
 * the last configuration stored by this app.
 * 2) This interface keeps the current vfo states and operating mode in local
 * variables.  When a request is made for the current state, the local variables
 * are read with a readLock.
 * 3) When a request is made to change the current state or operating mode, the
 * rig is commanded to the new state or mode and the local variables are 
 * accessed with writeLock access.  All communications to/from the radio are made
 * with writeLock access.  The writeLock will have fair access, meaning that the
 * thread that has been waiting the longest will get the next access.
 * 4) The states and modes kept with this class are:
 *          o) selected VFO (which is always the Rx VFO)
 *          o) VFOA frequency
 *          o) VFOB frequency
 *          o) Vfo operating mode, one of : 
 *              (SIMPLEX | DUPLEX_PLUS | DUPLEX_MINUS | SPLIT)
 * 
 * 3) This interface is intended to be the sole software class that can effect
 * those changes, by calls from the UI components.  
 * 4) Since there are multiple threads for scanning and the like, make the
 * changes and status requests thread safe.
 * 5) The rx/tx mode will be one of (AM, FM, CW, USB, LSB, ...DSTAR).  Both the rx
 * VFO and the tx VFO will have the same mode.  There are no plans to support
 * split mode split frequency where tx is CW and rx is SSB for example.
 * 
 * 
 * 
 * The app vfo choice state machine is the JRadioButtonGroup which enforces 
 * selection of only one of the group's button at a time.
 * 
 * The app vfo operating mode state machine is the JRadioButtonGroup which
 * enforces selection of only one of the group's operating states at a time.
 * 
 * A textfield for each VFO is updated with its frequency and its background is
 * grayed for the unselected (Tx) VFO.
 * 
 * It is recommended by Oracle that any time a lock is used, it be contained in
 * a try/catch block.  Will to that here.
 * 
 * 
 * ID-51A Operation
 *  rigctl expects the arguments for the V command to be Main and Sub which
 * correspond to VFOA and VFOB.  The radio screen actually names them A and B.
 * "Main" is always VFOA and "Sub" is always VFOB.  They really are not just VFO's.
 * They represent two independent channels, each having a DUP capability .
 * 
 * 
 * @author Coz
 */

public class VfoStateInterface {
    public final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();    
    JRadioButtonMenuItem radioButtonVfoA;
    JRadioButtonMenuItem radioButtonVfoB;
    JTextField fieldVfoA;
    JTextField fieldVfoB;
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
    int RETRY_RX_FREQ = 3;
    long REFRESH_INTERAL = 10000L;  // Interval in milliseconds.
    public enum opMode { AM, FM, CW, CW_R, LSB, USB, LSB_D, USB_D, DV_DSTAR };
    public enum dupOffset {PLUS, MINUS};
    
    /**
     * State variables protected by lock.
     * *********************************************************************
     */   
    private opState currentOpState = opState.SIMPLEX;
    private vfoChoice selectedRxVfo = vfoChoice.VFOA;
    // Default dupOffset is PLUS.
    private dupOffset currentDuplexOffset = dupOffset.PLUS;
    private opMode currentOpMode = opMode.FM;
    private long currentFreqVfoA = 0;  // Change under writeLock
    private long currentFreqVfoB = 0;  // Change under writeLock
    private boolean splitEnabled = true;
    //***********************************************************************
       

    private String[] vfoCommandStrings; // Index into strings is vfoChoice.
    
    
    public enum Method { RX_FREQ, VFO_OP_STATE, DUPLEX_ON, SPLIT_ON, 
                        DUP_PLUS, RX_VFO, GET_VFOB, GET_VFOA ;}
    Long[] timeMap;
    /**
     * 
     * @param frame
     * @param a
     * @param b
     * @param freqA
     * @param freqB 
     */
    
     public VfoStateInterface(JRX_TX frame, 
            JRadioButtonMenuItem a, JRadioButtonMenuItem b, 
            JTextField freqA, JTextField freqB) {
        
        fieldVfoA = freqA;
        fieldVfoB = freqB;
        radioButtonVfoA =  a;
        radioButtonVfoB =  b;
        aFrame = frame;
        
        final int size = Method.values().length;    
        timeMap=new Long[size];
        // Set all initial timeMap to zero so that a rig read occurs on startup
        // for each "get from radio" method.
        timeMap[Method.RX_FREQ.ordinal()] =  0L;
        timeMap[Method.VFO_OP_STATE.ordinal()] = 0L;
        timeMap[Method.DUPLEX_ON.ordinal()] = 0L;
        timeMap[Method.SPLIT_ON.ordinal()] = 0L;
        timeMap[Method.DUP_PLUS.ordinal()] = 0L;
        timeMap[Method.RX_VFO.ordinal()] = 0L;
        timeMap[Method.GET_VFOA.ordinal()] = 0L;
        timeMap[Method.GET_VFOB.ordinal()] = 0L;
        
        vfoCommandStrings = new String[2];
        vfoCommandStrings[vfoChoice.VFOA.ordinal()] = vfoChoice.VFOA.name();
        vfoCommandStrings[vfoChoice.VFOB.ordinal()] = vfoChoice.VFOB.name();
        
        // Make sure the vfo item selection is exclusive.
        Container container = a.getParent();
        ButtonGroup vfoButtonGroup = new ButtonGroup();
        vfoButtonGroup.add(a);
        vfoButtonGroup.add(b);
        assert(vfoButtonGroup.getButtonCount() == 2);
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
    
    /**
     * Search the rigcaps radioData for the correct commands to set the current
     * VFO, then write to local state variables under read lock.
     * @param radioData 
     */
    public void setVfoCommandChoices(String radioData) {
        if ( radioData == null) return;
        try {
            lock.readLock().lock();
            String search = "(?ism).*^VFO list:\\s*VFOA\\s*VFOB\\s*$.*";
            if (radioData.matches(search)) {
                vfoCommandStrings[vfoChoice.VFOA.ordinal()] = "VFOA";
                vfoCommandStrings[vfoChoice.VFOB.ordinal()] = "VFOB";
            } else {               
                search = "(?ism).*^VFO list:\\s*Sub\\s*Main\\s*$.*";
                if (radioData.matches(search)) {
                    vfoCommandStrings[vfoChoice.VFOA.ordinal()] = "Main";
                    vfoCommandStrings[vfoChoice.VFOB.ordinal()] = "Sub";
                }
            }
            lock.readLock().unlock();
        }
        catch (Exception e) {
            aFrame.pout("setVfoCommandChoices had exception "+ e);
        }
    }
    
    public void setVfoSplitCapability(String radioData) {
        if ( radioData == null) return;
        try {
            lock.readLock().lock();
            String search = "(?ism).*^Can set Split VFO:\\s*Y\\s*$.*";
            splitEnabled = radioData.matches(search);
            aFrame.menuItemSplit.setEnabled(splitEnabled);
            lock.readLock().unlock();
        }
        catch (Exception e) {
            aFrame.pout("setVfoCommandChoices had exception "+ e);
        }       
    }
            
    /**
     * Update the UI textField for VFO A frequency in Mhz.
     * @param hertz 
     */
    public void setTextVfoA(long hertz) {           
        fieldVfoA.setText("  VFO A   "+ Double.toString(((double)hertz) / 1.E06)+ "  ");
    }
    public void setTextVfoB(long hertz) {           
        fieldVfoB.setText("  VFO B   "+ Double.toString(((double)hertz) / 1.E06)+ "  ");
    }
    /**
     * Calculate elapsed time since the last rig read and if it is greater than
     * the REFRESH_INTERAL, read the value from the rig.
     * @param call a rigValue
     * @return boolean true when time to refresh the value read from rig.
     */
    private boolean isTimeForRigValue(Method call) {
        long oldValue = timeMap[call.ordinal()];
        // base this on elapsed time since last rig value
        long newValue =  System.currentTimeMillis();
        timeMap[call.ordinal()] = newValue;
        return ((newValue - oldValue)> REFRESH_INTERAL);
    }
    
    /**
     * Get the current Rx frequency from the radio and write it to the correct
     * local state variable under write lock.
     * @return frequency in Hertz.
     */
    private long getRxFreqFromRadio() {
        long value = 0l;
        try {
            lock.writeLock().tryLock(10, TimeUnit.SECONDS); 
            for(int retry=0; retry < RETRY_RX_FREQ; retry++) {
                String sf = askRadio("f");
                if (sf == null) { 
                    value = 0;
                } else if (sf.matches(".*RPRT 0")) {
                    value = 0;                
                } else {
                    try {
                        value = Long.parseLong(sf);
                    }
                    catch ( NumberFormatException numEx ) {
                        value = 0;                   
                    }
                }
                if (value == 0 ) {
                    System.out.println("getRxFrequency retry : "+ retry);
                    continue;
                }
                else {
                    // Write to local state variable under writeLock.
                    if (selectedRxVfo == vfoChoice.VFOA) {
                        currentFreqVfoA = value;
                        setTextVfoA(value);  // Update text field
                    } else {
                        currentFreqVfoB = value;
                        setTextVfoB(value);  // Update text field
                    }
                    break;
                }                   
            }
            lock.writeLock().unlock();                
        }
        catch (Exception rxe) {
            aFrame.pout("getRxFreqFromRadio had write exception"+rxe);
        }
        return value;        
    }
    
    /**
     * Get the rx frequency from the local state variable under read lock and 
     * occasionally read the rx frequency from the rig under write lock.
     * @return long frequency in hertz.
     */
    public long getRxFrequency() {
        long value = 0;
        if (isTimeForRigValue(Method.RX_FREQ)) {
            value = getRxFreqFromRadio();
        } else {
            // Get read lock for local state.
            try {
                lock.readLock().tryLock(10, TimeUnit.SECONDS);
                if (selectedRxVfo == vfoChoice.VFOA)
                    value = currentFreqVfoA;
                else
                    value = currentFreqVfoB;
                lock.readLock().unlock();
            }
            catch (Exception rxE) {
                aFrame.pout("readLock exception on local read freq: " + rxE);
            }
        }
        return value;       
    }
    
    // For compatibility with existing VfoDisplayControler.
    public long getSelectedVfoFrequency() {
        return getRxFrequency();
    }

    /**
     * Get the vfo operating state from the local variable under read lock and
     * occasionally read the op state from the rig under write lock.
     * @return 
     */
    public opState getVfoOperatingState() {
        opState op = opState.SIMPLEX;
        if (isTimeForRigValue(Method.VFO_OP_STATE)) {            
            try {
                lock.writeLock().tryLock(10, TimeUnit.SECONDS);
                if (isVfoOpStateSplitOn())
                    op = opState.SPLIT;
                else if (isVfoOpStateDupOn())
                    op = opState.DUPLEX;
                else op = opState.SIMPLEX;
                // Update local state within writeLock.
                currentOpState = op;
                lock.writeLock().unlock();
            }
            catch (Exception opStEx) {
                aFrame.pout("getVfoOperatingState writeLock exception"+opStEx);
            }
        } else {
            try {
                lock.readLock().lock();
                op = currentOpState;
                lock.readLock().unlock();
            }
            catch (Exception getVfoOpEx) {
                aFrame.pout("getVfoOperatingState readLock exception"+getVfoOpEx);
            }            
        }
        return op;
    }
    
    public String getVfoName(vfoChoice choice) { 
        return choice.toString();  
    }

    /**
     * Set split VFO operating mode.  This means that one VFO is the Rx freq
     * and the other VFO is the Tx freq.  Technically you can operate two
     * different modes, one on each VFO.  This app will not support that.
     * 
     * Uses rigctl command:
     * S: set_split_vfo   (Split,TX VFO) where Split is an integer  [1|0]
     * where 1 means ON and 0 means OFF.  TX VFO is [VFOA|VFOB].
     * 
     * @param txChoice either VFO_A or VFO_B
     */   
    public boolean setVfoOpStateSplitOn(vfoChoice txChoice) {
        boolean success = false;
        // Technically you must turn repeater duplex off.
        if (isVfoOpStateDupOn()) {
            setVfoOpStateDupOff();
        }
        try {
            lock.writeLock().lock();
            String commArgs = new String(
                    "S 1 "+getVfoName(txChoice));
            String reply = askRadio(commArgs);
            success = (reply != null);
            if (success) currentOpState = opState.SPLIT;
            lock.writeLock().unlock();
        }
        catch (Exception eSplitOn) {
            aFrame.pout("setVfoOpStateSplitOn had exception"+eSplitOn);
            success = false;
        }
        return success;       
    }
    /**
     * Set VFO operating state to Split off both to rig and local variable.
     * @return boolean success
     */
    public boolean setVfoOpStateSplitOff() {
        boolean success = false;
        try {
            lock.writeLock().lock();
            String commArgs = new String("S 0 VFOA");  // VFO does not matter.
            String reply = askRadio(commArgs);
            success = (reply != null);
            if (success) currentOpState = opState.SIMPLEX;
            lock.writeLock().unlock();
        }
        catch (Exception eSplitOff){
            aFrame.pout("setVfoOpSstateSplitOff had exception "+eSplitOff);
            success = false;
        }
        return success;       
    }
    /**
     * Get the vfo op split state from local state variable under read lock and
     * occasionally read the state from the rig under write lock.
     * @return 
     */
    public boolean isVfoOpStateSplitOn() {
        boolean splitOn = false;
        if (!splitEnabled) return false;
        if (isTimeForRigValue(Method.SPLIT_ON)) {
            try {
                lock.writeLock().lock();                
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
                // Set local variable within writeLock.
                if (splitOn) currentOpState = opState.SPLIT;
                lock.writeLock().unlock();
            }
            catch (Exception exSplit) {
                aFrame.pout("isVfoOpStateSplitOn had exception"+exSplit);
            }
        } else {
            // Get local state variable with readLock.
            try { 
                lock.readLock().lock();
                splitOn =  (currentOpState == opState.SPLIT);
                lock.readLock().unlock();
            }
            catch (Exception exSplitLocal) {
                aFrame.pout("isVfoOpStateSplitOn had local exception"+exSplitLocal);
            }
        }
        return splitOn;        
    }
    /**
     * Set VFO operating state duplex plus offset by comms with radio and
     * setting local state variable.
     * @return 
     */
    public boolean setVfoOpStateDupPlus() {
        boolean success = false;    
        try {
            lock.writeLock().lock();
            String commArgs = new String("R +");
            String reply = askRadio(commArgs);
            success = (reply != null);
            if (success) {
                currentOpState = opState.DUPLEX;
                currentDuplexOffset = dupOffset.PLUS;
            }
            lock.writeLock().unlock();
        }
        catch (Exception eDupPlus) {
            aFrame.pout("setVfoOpStateDupPlus had exception "+eDupPlus);
            success = false;
        }
        return success;
    }
    /**
     * Set VFO operating state duplex minus offset by comms to radio and setting
     * local state variable.
     * @return 
     */
    public boolean setVfoOpStateDupMinus() {
        boolean success = false;
        try {
            lock.writeLock().lock();
            String commArgs = new String("R -");
            String reply = askRadio(commArgs);
            success = (reply!=null);
            if (success) {
                currentOpState = opState.DUPLEX;
                currentDuplexOffset = dupOffset.MINUS;                
            }
            lock.writeLock().unlock();
        }
        catch (Exception eDupMinus) {
            aFrame.pout("setVfoOpStateDupMinus had exception "+ eDupMinus);
            success = false;
        }
        return success;
    }
    /**
     * Set VFO operating state duplex OFF by comms to radio an setting local
     * state variables under write lock.
     * @return 
     */
    public boolean setVfoOpStateDupOff() {
        boolean success = false;
        try {
            lock.writeLock().tryLock(10, TimeUnit.SECONDS);
            String commArgs = new String("R 0");        
            String reply = askRadio(commArgs);
            success = (reply != null);
            if (success) {
                currentOpState = opState.SIMPLEX;
                currentDuplexOffset = dupOffset.PLUS;  // Default.               
            }
            lock.writeLock().unlock();
        }
        catch (Exception eSetDupOff) {
            aFrame.pout("setVfoOpStateDupOff had exception "+eSetDupOff);
            success = false;
        }
        return success;        
    }
    /**
     * Get the Vfo op state Duplex from local state variable under read lock and
     * occasionally get it from rig under write lock.
     * @return 
     */
    public boolean isVfoOpStateDupOn() {
        boolean shiftOn = false;
        if (isTimeForRigValue(Method.DUPLEX_ON)) {
            try {       
                lock.writeLock().lock();                
                String commArgs = new String("r");  
                // Returns "Protocol error" when on hf band.
                // Returns "Shift: + " when on vhf and DUP+.
                // Returns "Shift: - " when on vhf and DUP-.        
                String reply = askRadio(commArgs);
                if (reply != null) {
                    String dupSearch = "(?i).*Rptr Shift: [\\+\\-]";
                    shiftOn = reply.matches(dupSearch);
                }
                if (shiftOn) currentOpState = opState.DUPLEX;
                lock.writeLock().unlock();
            }
            catch (Exception eDuplexOn) {
                aFrame.pout("isVfoOpStateDupOn exception "+eDuplexOn);
            }
        } else {
            try {
                lock.readLock().lock();
                shiftOn = (currentOpState == opState.DUPLEX);
                lock.readLock().unlock();
            }
            catch (Exception eDuplexOnLocal) {
                aFrame.pout("isVfoOpStateDupOn exception "+ eDuplexOnLocal);
            }
        }
        return shiftOn;                
    }
    /**
     * Query local state variable for duplex plus state.
     * @return 
     */
    public boolean isVfoDuplexPlus() {
        boolean dupPlus = false;
        if (isTimeForRigValue(Method.DUP_PLUS)) {
            try {
                lock.writeLock().lock();
                dupPlus = false;
                String commArgs = new String("r");  
                // Returns "Protocol error" when on hf band.
                // Returns "Shift: + " when on vhf and DUP+.
                // Returns "Shift: - " when on vhf and DUP-.        
                String reply = askRadio(commArgs);
                if (reply != null) {
                    String dupPlusSearch = "(?i).*Rptr Shift: \\+";
                    dupPlus = reply.matches(dupPlusSearch);
                }
                lock.writeLock().unlock(); 
            }
            catch (Exception eIsDupPlus) {
                aFrame.pout("isVfoDuuplexPlus had exception during radio comms"+eIsDupPlus);
            }
        } else {
            try {
                lock.readLock().lock();
                dupPlus = (currentDuplexOffset == dupOffset.PLUS);
                lock.readLock().unlock();                
            }
            catch (Exception eReadPlus) {
                aFrame.pout("isVfoDuplexPlus had exception in read local state"+eReadPlus);
            }
        }
        return dupPlus;        
    }
    /**
     * Use existing methods that work under write lock to set Simplex mode and
     * then set the local state variable under write lock.
     */
    public void setVfoStateSimplex() {
        if (splitEnabled) setVfoOpStateSplitOff();
        setVfoOpStateDupOff();
        try {
            lock.writeLock().lock();  
            currentOpState = opState.SIMPLEX;
            lock.writeLock().unlock();
        }
        catch (Exception eSetSimplex) {
            aFrame.pout("setVfoStateSimplex had exception "+ eSetSimplex);            
        }
    }
    /**
     * Set the Rx (selected VFO) by comms with radio under write lock and set local
     * state variable under write lock; then read rx VFO freq.
     * 
     * What happens if comms fails?  @TODO Coz fix this.
     * @param choice 
     */
    public void setRxVfo(vfoChoice choice) {
        try {
            lock.writeLock().tryLock(10, TimeUnit.SECONDS);        
            String commArgs = new String("V "+ vfoCommandStrings[choice.ordinal()]);
            String reply = askRadio(commArgs);
            // @TODO handle reply of null which means comm error.
            // Upon coms error, the state DID NOT CHANGE.
            selectedRxVfo = choice;
            if (choice == vfoChoice.VFOA) {
                radioButtonVfoA.setSelected(true);
                fieldVfoA.setBackground(SELECTED_COLOR);
                fieldVfoB.setBackground(UNSELECTED_COLOR);                
            } else {
                radioButtonVfoB.setSelected(true);
                fieldVfoB.setBackground(SELECTED_COLOR);
                fieldVfoA.setBackground(UNSELECTED_COLOR);                
            }
            lock.writeLock().unlock();
        }
        catch (Exception eSetRx) {
            aFrame.pout("setRXVfo had exception "+eSetRx);
        }
    }
    /**
     * Set VFO A selected by comms with radio under write lock and set local
     * state variable under write lock.
     * 
     * @return boolean success.
     */
    public boolean setVfoASelected() {
        boolean success = false;
        try {
            lock.writeLock().tryLock(10, TimeUnit.SECONDS);  
            success = true;  
            String commArgs = new String("V " + vfoCommandStrings[vfoChoice.VFOA.ordinal()]);
            String reply = askRadio(commArgs);
            success = (reply!= null);
            if (success) {
                radioButtonVfoA.setSelected(true);
                selectedRxVfo = vfoChoice.VFOA;
                fieldVfoA.setBackground(SELECTED_COLOR);
                fieldVfoB.setBackground(UNSELECTED_COLOR);
            }
            lock.writeLock().unlock();
        }
        catch (Exception eSetA) {
            aFrame.pout("setVfoASelected had exception "+eSetA);
            success = false;
        }
        return success;
    }
    /**
     * Set VFO B selected by comms with radio under write lock and set local
     * state variable under write lock.
     * 
     * @return boolean success 
     */
    public boolean setVfoBSelected() {
        boolean success = false;
        try {
            lock.writeLock().tryLock(10, TimeUnit.SECONDS);  
            success = true; 
            String commArgs = new String("V " + vfoCommandStrings[vfoChoice.VFOB.ordinal()]);
            String reply = askRadio(commArgs);
            success = (reply!= null);
            if (success) {
                radioButtonVfoB.setSelected(true);
                selectedRxVfo = vfoChoice.VFOB;
                fieldVfoB.setBackground(SELECTED_COLOR);
                fieldVfoA.setBackground(UNSELECTED_COLOR);
            }
            lock.writeLock().unlock();
        }
        catch (Exception eSetB) {
            aFrame.pout("setVfoBSelected had exception "+eSetB);
            success = false;            
        }
        return success;
    }
        
    
    /**
     * Determine which VFO is selected, the Rx VFO.
     * 
     * @return vfoChoice
     */
    public vfoChoice getRxVfo() {
        vfoChoice choice  = vfoChoice.VFOA;
        if (isTimeForRigValue(Method.RX_VFO)) {
            try {       
                lock.writeLock().tryLock(10, TimeUnit.SECONDS);
                aFrame.noVfoDialog = true;
                long freq1 = getRxFrequency();
                setVfoASelected();      
                long freq2 = getRxFrequency();          
                // If the freqs are the same, VFOA is rxVFO.
                if ( freq1 == freq2 ) {
                    choice = vfoChoice.VFOA;
                    selectedRxVfo = vfoChoice.VFOA;
                    setTextVfoA(freq2);  // VFOA is selected
                    fieldVfoA.setBackground(SELECTED_COLOR);
                    // Get VFO B freq
                    setRxVfo(vfoChoice.VFOB);
                    long freqB = getRxFrequency();
                    // Set VFO B text field freq.
                    setTextVfoB(freqB);            
                    // Gray VFO B text field.
                    fieldVfoB.setBackground(UNSELECTED_COLOR);           
                    // Set VFO A selected.
                    setRxVfo(vfoChoice.VFOA);
                }
                else {
                    setVfoBSelected(); 
                    choice = vfoChoice.VFOB; //VFOB is selected;
                    selectedRxVfo = vfoChoice.VFOB;
                    setTextVfoB(freq1);
                    fieldVfoB.setBackground(SELECTED_COLOR);
                    setTextVfoA(freq2);
                    fieldVfoA.setBackground(UNSELECTED_COLOR);
                }
                aFrame.noVfoDialog = false;
                lock.writeLock().unlock();
            }
            catch (Exception eGetRxVfo) {
                aFrame.pout("getRxVfo had exception in comms write"+eGetRxVfo);               
            }
        } else {
            // Read local state variable under read lock.
            try {
                lock.readLock().lock();               
                choice = selectedRxVfo;  // Read state variable.
                lock.readLock().unlock();
            }
            catch (Exception eGetLocalRxVfo) {
                aFrame.pout("getRxVfo had exception in local read "+eGetLocalRxVfo);                
            }
        }
        return choice;
    }
    /**
     * Get the frequency of VFO A from radio and overwrite local state variable
     * frequency of VFOA under write lock.
     * 
     * @return frequency in Hertz
     */
    public long getVfoAFrequency() {
        long frequencyHertz = 0;    
        if (isTimeForRigValue(Method.GET_VFOA)) {
            try {       
                lock.writeLock().tryLock(10, TimeUnit.SECONDS);
                aFrame.noVfoDialog = true;
                if (selectedRxVfo == vfoChoice.VFOA) { // Read state variable.
                    frequencyHertz = getRxFreqFromRadio(); // Read rig.
                    setTextVfoA(frequencyHertz);  
                } else {
                    setRxVfo(vfoChoice.VFOA); // Write rig.
                    frequencyHertz = getRxFreqFromRadio(); // Read rig.
                    setTextVfoA(frequencyHertz);
                    setRxVfo(vfoChoice.VFOB); // Write rig.
                }
                if (frequencyHertz != 0) { // Zero indicates comms error.
                    currentFreqVfoA = frequencyHertz;  // Overwrite local state var.
                }
                lock.writeLock().unlock();
            }
            catch (Exception eGetVfoAFreq) {
                aFrame.pout("getVfoAFrequency had exception "+eGetVfoAFreq);
            }            
        } else {       
            try {
                lock.readLock().lock();  //blocks until lock is available.
                frequencyHertz = currentFreqVfoA;  // Read local state var.      
                lock.readLock().unlock();
            }
            catch (Exception eGetVfoA) {
                aFrame.pout("getVfoAFRequency had exception " + eGetVfoA);
            }
        }
        return frequencyHertz;
    }
       
     public long getVfoBFrequency() {
        long frequencyHertz = 0;
        if (isTimeForRigValue(Method.GET_VFOB)) {
            try {       
                lock.writeLock().tryLock(10, TimeUnit.SECONDS);
                aFrame.noVfoDialog = true;
                if (selectedRxVfo == vfoChoice.VFOB) { // Read state variable.
                    frequencyHertz = getRxFreqFromRadio(); // Read rig.
                    setTextVfoB(frequencyHertz);  
                } else {
                    setRxVfo(vfoChoice.VFOB); // Write rig.
                    frequencyHertz = getRxFreqFromRadio(); // Read rig.
                    setTextVfoB(frequencyHertz);
                    setRxVfo(vfoChoice.VFOA); // Write rig.
                }
                if (frequencyHertz != 0) { // Zero indicates comms error.
                    currentFreqVfoB = frequencyHertz;  // Overwrite local state var.
                }
                lock.writeLock().unlock();
            }
            catch (Exception eGetVfoBFreq) {
                aFrame.pout("getVfoBFrequency had exception "+eGetVfoBFreq);
            }            
        } else {       
            try {
                lock.readLock().lock();  //blocks until lock is available.
                frequencyHertz = currentFreqVfoB; // Read local state var.       
                lock.readLock().unlock();
            }
            catch (Exception eGetVfoB) {
                aFrame.pout("getVfoBFRequency had exception " + eGetVfoB);
            }
        }
        return frequencyHertz;
    }
    /**
     * From reading local state variable under read lock determine is Vfo A is
     * selected (is the Rx vfo).
     * @return 
     */
    public boolean vfoA_IsSelected() {
        boolean isVfoA = true;
        try {
            lock.readLock().lock();
            isVfoA = (selectedRxVfo == vfoChoice.VFOA);
            lock.readLock().unlock();           
        }
        catch (Exception eIsVfoA) {
            aFrame.pout("vfoA_isSelected had exception "+eIsVfoA);
        }
        return isVfoA;
    }    
 
   /**
     * Write the given frequency to the currently selected radio VFO under write
     * lock and update the local state variable under write lock.
     * @return true when frequency successfully communicated to radio.
     * @param frequencyHertz
    */
    public boolean writeFrequencyToRadioSelectedVfo(long frequencyHertz) {        
        boolean success = true;
        try {
            lock.writeLock().tryLock(10, TimeUnit.SECONDS);  
            aFrame.noVfoDialog = true;
            String commArgs = new String("F "+Long.toString(frequencyHertz));        
            String reply = askRadio(commArgs);
            success = (reply != null);
            if (success) {
                if (selectedRxVfo == vfoChoice.VFOA) {
                    currentFreqVfoA = frequencyHertz;
                    setTextVfoA( frequencyHertz );
                } else {
                    currentFreqVfoB = frequencyHertz;
                    setTextVfoB( frequencyHertz );                    
                }
            }           
            aFrame.noVfoDialog= false;
            lock.writeLock().unlock();
        }
        catch (Exception eWriteFreq) {
            aFrame.pout("writeFrequencyToRadioSelectedVfo had exception"+ eWriteFreq);
            success = false;
        }
        return success;
    }
    
    /**
     * Under write lock determine by reading the local state variable which vfo
     * is selected and communicate with the radio appropriately.
     * 
     * @param frequencyHertz
     * @return boolean success when all comms are successful.
     */
    public boolean writeFrequencyToRadioVfoA(long frequencyHertz) {
        boolean success = false;
        try {
            lock.writeLock().tryLock(10, TimeUnit.SECONDS);  
            aFrame.noVfoDialog = true;
            if (selectedRxVfo == vfoChoice.VFOA) {
                writeFrequencyToRadioSelectedVfo(frequencyHertz);
            } else {
                setVfoASelected(); // Change VFO selection momentarily.
                writeFrequencyToRadioSelectedVfo(frequencyHertz);
                setVfoBSelected();                
            }
            setTextVfoA(frequencyHertz);
            currentFreqVfoA = frequencyHertz;
            aFrame.noVfoDialog = false;
            success = true;
            lock.writeLock().unlock();
        }
        catch (Exception eWriteVfoA) {
            aFrame.pout("writeFrequencyToRadioVfoA had exception"+ eWriteVfoA);
            success = false;
        }
        return success;
    }
    /**
     * Under write lock determine by reading the local state variable which vfo
     * is selected and communicate with the radio appropriately.
     * 
     * @param frequencyHertz
     * @return boolean success true when all comms are successful.
     */   
    public boolean writeFrequencyToRadioVfoB(long frequencyHertz) {
        boolean success = false;
        try {
            lock.writeLock().lock();  //blocks until lock is available.
            aFrame.noVfoDialog = true;
            if (selectedRxVfo == vfoChoice.VFOB) {
                writeFrequencyToRadioSelectedVfo(frequencyHertz);
            } else {
                setVfoBSelected();
                writeFrequencyToRadioSelectedVfo(frequencyHertz);
                setVfoASelected();
            }
            setTextVfoB(frequencyHertz);
            currentFreqVfoB = frequencyHertz;
            aFrame.noVfoDialog = false;
            success = true;
            lock.writeLock().unlock();
        }
        catch (Exception eWriteVfoB) {
            aFrame.pout("writeFrequencyToRadioVfoB had exception "+ eWriteVfoB);
            success = false;
        }
        return success;
    }
     
     
    ////////////////////////////////////////////////////////////////////
    // THESE METHODS HAVE NOT YET BEEN UPDATED...WORKING RIGHT HERE......
     
   
    
////////////////////////////////////////////Working Below here.....    
    public void setVfoSplitTxDelta(vfoChoice txChoice, double splitDelta) {
        if (!splitEnabled) return;
        // This command is OK even if we are NOT in SPLIT state.
        String commArgs = new String(
                " "+Double.toString(splitDelta)+" "+getVfoName(txChoice));
        
        System.out.println("setVfoSplitTxDelta() is not done yet");

        //String reply = askRadio(commArgs);       
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

