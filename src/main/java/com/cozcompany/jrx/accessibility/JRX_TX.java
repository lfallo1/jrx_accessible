// ***************************************************************************
// *   Copyright (C) 2012 by Paul Lutus                                      *
// *   lutusp@arachnoid.com                                                  *
// *                                                                         *
// *   This program is free software; you can redistribute it and/or modify  *
// *   it under the terms of the GNU General Public License as published by  *
// *   the Free Software Foundation; either version 2 of the License, or     *
// *   (at your option) any later version.                                   *
// *                                                                         *
// *   This program is distributed in the hope that it will be useful,       *
// *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
// *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
// *   GNU General Public License for more details.                          *
// *                                                                         *
// *   You should have received a copy of the GNU General Public License     *
// *   along with this program; if not, write to the                         *
// *   Free Software Foundation, Inc.,                                       *
// *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
// ***************************************************************************
package com.cozcompany.jrx.accessibility;

import com.cozcompany.jrx.accessibility.utilities.FileHelpers;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Timer;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.accessibility.AccessibleContext;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import vfoDisplayControl.VfoDisplayControl;
import vfoDisplayControl.VfoStateInterface;
import vfoDisplayControl.GroupBox;

/**
 * JRX_TX contains the main thread of the application.
 * 
 * 
 * Application State Variables:
 * 
 * 1) boolean inhibit : True means that the application does not at present have
 * a complete and valid initialization AKA validSetup().  It is set to true upon
 * startup.  When true, it inhibits controls from writing to the rig, inhibits
 * starting the rigctld (daemon), inhibits initialize() method, inhibits S meter,
 * inhibits reading radio frequency, inhibits getting mode from rig, inhibits all
 * radio coms, inhibits setTableScanParams, setMemoryScanParams, and inhibits
 * setScanParams.
 * 2) boolean isMacOS : We are running on a MAC.
 * 
 * 
 * 
 * @author lutusp
 */
final public class JRX_TX extends javax.swing.JFrame implements 
        ListSelectionListener, ItemListener , ActionListener {

    final String appVersion = "5.0.10";
    final String appName;
    final String programName;
    String lineSep;
    String userDir;
    String userPath;
    
    Timer periodicTimer;
    ParseComLine comArgs = null;
    ImageIcon redLed, greenLed, blueLed, yellowLed;
    ScanStateMachine scanStateMachine;
    ArrayList<String> interfaceNames = null;
    ChannelChart chart;
    SquelchScheme squelchScheme;
   
    Map<String, Integer> radioCodes = null;
    Map<Integer, String> radioNames = null;
    Map<String, Double> filters = null;
    Map<String, Double> ctcss = null;
    ArrayList<ControlInterface> controlList;
    int iErrorValue = -100;
    long defaultFrequency = 145000000;

    int sv_hamrigWriteDelay = 12;
    int sv_hamrigPostWriteDelay = 2;
    int sv_hamrigRetries = 3;
    int sv_hamrigTimeout = 400;
    boolean sv_hamrigUseCustomSettings = false;
    int memoryButtonTotal = 200;
    int hamlibPort = 4532;
    String hamlibHost = "127.0.0.1";
    Socket hamlibSocket = null;
    InputStream hamlib_is = null;
    OutputStream hamlib_os = null;
    String hamlibExecPath = "rigctld";
    String radioData = null;
    Process hamlibDaemon = null;
    ConfigManager config = null;
    JFrame sv_frame = null;
    int squelchLow = -100;
    int squelchHigh = 100;
    boolean isWindows;
    boolean isMacOs;
    public boolean inhibit;  // application state 
    public boolean noVfoDialog;
    Font digitsFont;
    Font baseFont;
    final String FILE_SEP = System.getProperty("file.separator");
    String buttonFilePath;
   
    int MODE_CW = 0;
    int MODE_LSB = 1;
    int MODE_USB = 2;
    int MODE_AM = 3;
    int MODE_FM = 4;
    int MODE_WFM = 5;
    int defWidth = 717;
    int defHeight = 762;
    
    MemoryCollection memoryCollection;
    boolean slowRadio = false;
    int readBufferLen = 2048;
    byte[] readBuffer;
    boolean dcdCapable = false;
    double signalStrength = 0;
    double oldVolume = -1;
    long oldRadioFrequency = -1;
    Color darkGreen, darkBlue, darkRed;
    int comError = 0; // Comms Led starts out green.
    long oldTime = 0; // debugging
    
    VfoDisplayControl vfoDisplay;
    VfoStateInterface vfoState;
    ScanController scanDude;
    static String VFO_SELECT_A_TEXT = "Select radio VFO A";
    static String VFO_SELECT_B_TEXT = "Select radio VFO B";
    static String VFO_SIMPLEX_TEXT  = "Select simplex";
    static String VFO_DUPLEX_PLUS_TEXT   = "Select duplex +";
    static String VFO_DUPLEX_MINUS_TEXT   = "Select duplex -";
    static String VFO_SPLIT_TEXT    = "Select split";
    static String LAST_VFO = "LAST_VFO";
    final long AERO_CLUB_FREQ = 147240000;   // AERO CLUB 2m repeater output
    final long SHAWSVILLE_REPEATER_OUTPUT_FREQ = 145330000; // Shawsville Rptr
    JMenuBar menuBar;
    public JRadioButtonMenuItem menuItemA;
    public JRadioButtonMenuItem menuItemB;
    public JRadioButtonMenuItem menuItemSimplex; 
    public JRadioButtonMenuItem menuItemDuplexPlus;
    public JRadioButtonMenuItem menuItemDuplexMinus;
    public JRadioButtonMenuItem menuItemSplit; 
    public JTextField frequencyVfoA;
    public JTextField frequencyVfoB;
    Preferences prefs;

    //dependencies
    FileHelpers fileHelpers;

    /**
     * Creates new form JRX
     */
    public JRX_TX(String[] args) {
        // Call to initComponents happens after these calls.
        fileHelpers = new FileHelpers();
        comArgs = new ParseComLine(this, args);
        inhibit = true;
        oldTime = System.currentTimeMillis();       
        readBuffer = new byte[readBufferLen];
        memoryCollection = new MemoryCollection(this);
        appName = getClass().getSimpleName();
        programName = appName + " " + appVersion;
        setTitle(programName);
        setIconImage(new ImageIcon(getClass().getClassLoader().
                getResource("icons/JRX.png")).getImage());
        redLed = new ImageIcon(getClass().getClassLoader().
                getResource("icons/red-on-16.png"));
        greenLed = new ImageIcon(getClass().getClassLoader().
                getResource("icons/green-on-16.png"));
        blueLed = new ImageIcon(getClass().getClassLoader().
                getResource("icons/blue-on-16.png"));
        yellowLed = new ImageIcon(getClass().getClassLoader().
                getResource("icons/yellow-on-16.png"));
        lineSep = System.getProperty("line.separator");

        userDir = System.getProperty("user.home");
        userPath = userDir + FILE_SEP + "." + appName;
        buttonFilePath = userPath + FILE_SEP + "memoryButtons.ini";
        memoryCollection.setFilePath(buttonFilePath);
        new File(userPath).mkdirs();
        
        initComponents();       
        digitsFont = new Font(Font.MONOSPACED, Font.PLAIN, 30);
        baseFont = new Font(Font.MONOSPACED, Font.PLAIN, getFont().getSize());
        setFont(baseFont);                
        // Must create/initialize vfoDisplay before scan functions.
        frequencyVfoA = new JTextField("VFO A  0000.000000");
        frequencyVfoB = new JTextField("VFO B  0000.000000");
        vfoTabbedPane.setTabComponentAt(0, frequencyVfoA);
        vfoTabbedPane.setTabComponentAt(1, frequencyVfoB);
        vfoDisplay = (VfoDisplayControl)jInternalFrame1;
        vfoState = setUpVfoComponents(vfoDisplay);
        rxVfoPanel.setVisible(true);
        scanStateMachine = new ScanStateMachine(this);       
        scanDude = new ScanController(this);
        recieverGroupBox.getAccessibleContext().
                setAccessibleDescription("Receiver Controls");
        setResizable(true);
        // default app size
        setSize(defWidth, defHeight);
        createControlList();
        squelchScheme = new SquelchScheme(this);               
        setupControls();
        setDefaultComboContent(); 
        config = new ConfigManager(this);
        if (!comArgs.reset) {
            config.read();
        }
        // Why does the shortCut key M not work ????????
        muteCheckBox.setText("Mute"); // Do this manually, GUI uses deprecated call to setLabel()
        ((JCheckBox)muteCheckBox).setMnemonic(KeyEvent.VK_M);
        int specialKey = ((JCheckBox)muteCheckBox).getMnemonic(); // Does not work!
        chart = new ChannelChart(this);
        chart.init();
        dismissOldHamlibTask();
        inhibit = false;
        initialize();
    }


    public VfoStateInterface setUpVfoComponents(VfoDisplayControl vfoGroup) {
        // Create an Prefernces object for access to this user's preferences.
        prefs = Preferences.userNodeForPackage(this.getClass());
        // Use the Mac OSX menuBar at the top of the screen.
        //System.setProperty("apple.laf.useScreenMenuBar", "true");
        addMenuBar();      
        // Add an exclusive interface to the Vfo selector so that only one thread
        // at a time gains access.
        vfoState = new VfoStateInterface(this, 
                menuItemA, menuItemB, frequencyVfoA, frequencyVfoB );
 
      
        // @todo Add this later with stored frequency of the selected vfo.
        noVfoDialog = true;
        String lastVfo = prefs.get("LAST_VFO", "VFO_SELECT_A_TEXT");
        if ( lastVfo == null) {
            // There is no history.
            // Vfo A is arbitrary default,
            vfoState.setVfoASelected();
        } else if ( lastVfo.equals(VFO_SELECT_A_TEXT)) {
            vfoState.setVfoASelected();
        } else if ( lastVfo.equals(VFO_SELECT_B_TEXT)) {
            vfoState.setVfoBSelected();
        } else {
            // Do no recognize the entry.
            System.out.println("Unrecognized preference :"+lastVfo);
            vfoState.setVfoASelected();
        }        
        // Must instantiate components before initialization of VfoDisplayControl.     
        vfoGroup.setupPanes();                      
        noVfoDialog = false;
        
        
        
        // Cause the ones digit ftf to get the focus when the JFrame gets focus.                              
        JFormattedTextField textField;
        Vector<Component> order = vfoGroup.getTraversalOrder();
        textField = (JFormattedTextField) order.get(0);
         this.addWindowFocusListener(new WindowAdapter() {
            public void windowGainedFocus(WindowEvent e) {
                textField.requestFocusInWindow();
            }
        });
        vfoGroup.setFocusable(true);       
        String infoStr  = "<html>VFO Display Digits can be <br>"+
                                "adjusted using up/down <br>"+
                                "arrows, left click and the<br>"+
                                "scroll wheel.             <br>"+
                                "Left/right arrows traverse<br>"+
                                "digits.  ";
        return vfoState;
    }
    
    /**
     * Create the menu bar for the display and add menu items to operate the
     * VFO selection and copy tasks.
     * 
     * Requirement: Gray or otherwise disable menu items that are not supported
     * by the rig.  For example, ID-51 does not support SPLIT vfo op mode.
     */        
    protected void addMenuBar() {                         
        menuBar = jMenuBar1;
        JMenu menu = jMenu3;
        //Build the first menu.       
        menu.setMnemonic(KeyEvent.VK_V);
        AccessibleContext menuContext = menu.getAccessibleContext();
        menuContext.setAccessibleDescription(
            "Pick the radio VFO that the VFO Panel controls");
        menuContext.setAccessibleName("Choose Radio VFO");
        //Set JMenuItem A.
        menuItemA = new JRadioButtonMenuItem(VFO_SELECT_A_TEXT, true);
        menuItemA.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_A, ActionEvent.ALT_MASK));
        AccessibleContext itemAContext = menuItemA.getAccessibleContext();
        itemAContext.setAccessibleDescription(
            "VFO panel controls radio VFO A");
        itemAContext.setAccessibleName("Choose radio VFO A");       
        menuItemA.addItemListener(this);
        menu.add(menuItemA);
        //Set JMenuItem B.
        menuItemB = new JRadioButtonMenuItem(VFO_SELECT_B_TEXT, false);
        menuItemB.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_B, ActionEvent.ALT_MASK));
        AccessibleContext itemBContext = menuItemB.getAccessibleContext();
        itemBContext.setAccessibleDescription(
            "VFO panel controls radio VFO B");
        itemBContext.setAccessibleName("Choose radio VFO B");
        menuItemB.addItemListener(this);
        menu.add(menuItemB);
        // Add VFO "copy" menu items.
        menu.addSeparator();
        JMenuItem a2b = new JMenuItem("Copy VFO A to VFO B", KeyEvent.VK_C);
        AccessibleContext a2bContext = a2b.getAccessibleContext();
        a2bContext.setAccessibleName("Copy Vfo A to Vfo B");
        a2bContext.setAccessibleDescription("Use shortcut key option C");
        a2b.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_C, ActionEvent.ALT_MASK));
        a2b.addItemListener(this);
        a2b.addActionListener(this);
        menu.add(a2b);
        JMenuItem swap = new JMenuItem("Swap VFO A with VFO B", KeyEvent.VK_S);
        AccessibleContext swapContext = a2b.getAccessibleContext();
        swapContext.setAccessibleName("Swap Vfo A with Vfo B");
        swapContext.setAccessibleDescription("Use shortcut key option S");
        swap.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_S, ActionEvent.ALT_MASK));
        swap.addItemListener(this);
        swap.addActionListener(this);
        menu.add(swap);
        
        // Add VFO operating modes radio menu buttons.
        menu.addSeparator();
        menuItemSimplex = new JRadioButtonMenuItem(VFO_SIMPLEX_TEXT, true);
        splitFreqGroup.add(menuItemSimplex);
        menuItemSimplex.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_P, ActionEvent.ALT_MASK));
        AccessibleContext simplexContext = menuItemSimplex.getAccessibleContext();
        simplexContext.setAccessibleDescription(
            "VFO simplex operation");
        simplexContext.setAccessibleName("Simplex");       
        menuItemSimplex.addItemListener(this);
        menu.add(menuItemSimplex);
        
        menuItemDuplexPlus = new JRadioButtonMenuItem(VFO_DUPLEX_PLUS_TEXT, false);
        splitFreqGroup.add(menuItemDuplexPlus);
        menuItemDuplexPlus.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_U, ActionEvent.ALT_MASK));
        AccessibleContext duplexPlusContext = menuItemDuplexPlus.getAccessibleContext();
        duplexPlusContext.setAccessibleDescription(
            "VFO duplex plus repeater offset operation");
        duplexPlusContext.setAccessibleName("Duplex Plus");       
        menuItemDuplexPlus.addItemListener(this);
        menu.add(menuItemDuplexPlus);
        
        menuItemDuplexMinus = new JRadioButtonMenuItem(VFO_DUPLEX_MINUS_TEXT, false);
        splitFreqGroup.add(menuItemDuplexMinus);
        menuItemDuplexMinus.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_D, ActionEvent.ALT_MASK));
        AccessibleContext duplexMinusContext = menuItemDuplexMinus.getAccessibleContext();
        duplexMinusContext.setAccessibleDescription(
            "VFO duplex minus repeater offset operation");
        duplexMinusContext.setAccessibleName("Duplex Minus");       
        menuItemDuplexMinus.addItemListener(this);
        menu.add(menuItemDuplexMinus);
               
        menuItemSplit = new JRadioButtonMenuItem(VFO_SPLIT_TEXT, false);
        splitFreqGroup.add(menuItemSplit);
        menuItemSplit.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_T, ActionEvent.ALT_MASK));
        AccessibleContext splitContext = menuItemSplit.getAccessibleContext();
        splitContext.setAccessibleDescription(
            "VFO split operation");
        splitContext.setAccessibleName("Split");       
        menuItemSplit.addItemListener(this);
        menu.add(menuItemSplit);
    }
 

    private void startCyclicalTimer() {
        if (comArgs.runTimer) {
            if (periodicTimer != null) {
                periodicTimer.cancel();
            }
            // This is not a java swing Timer.
            periodicTimer = new java.util.Timer();
            resetTimer();
        }
    }
    /**
     * Get a delay interval from the sv_timerIntervalComboBox and schedule a
     * TimerTask (PeriodicEvents) after the given interval; technically this
     * method RESTARTS the timer (as opposed to resetting the timer).
     */
    protected void resetTimer() {
        long delay = (long) ((RWComboBox) sv_timerIntervalComboBox).getConvertedValue();
        //p("delay: " + delay);
        if (delay > 0 && periodicTimer != null) {
            periodicTimer.schedule(new PeriodicEvents(), delay);
        }
    }
/////////////////////////////////////////////////////////////////////////////////
    /**
     * Handle list selection event from SWL ChannelChart and write the freq and
     * mode to the current Rx radio VFO.
     * @param e 
     */
    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        try {
            ListSelectionModel lsm = (ListSelectionModel) e.getSource();
            if (!lsm.isSelectionEmpty()) {
                scanStateMachine.stopScan(false);
                int row = lsm.getMinSelectionIndex();
                String mode = chart.getValue(row, 2);
                double freq = Double.parseDouble(chart.getValue(row, 3));
                vfoState.writeFrequencyToRadioSelectedVfo((long)(freq*1e6 + 0.5));
                vfoDisplay.frequencyToDigits((long) (freq * 1e6 + 0.5));
                RWComboBox box = (RWComboBox) sv_modesComboBox;
                mode = "Mode " + mode.toUpperCase();
                Integer index = box.displayMap.get(mode);
                if (index != null) sv_modesComboBox.setSelectedIndex(index);
                box.writeValue(true);
            }
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }
/////////////////////////////////////////////////////////////////////////////////
    
    /**
     * The control loop for dynamic behavior.
     */
    class PeriodicEvents extends TimerTask {

        @Override
        public void run() {
            if (!getScopePanel().isRunning() && scanStateMachine.scanTimer == null) {
                getSignalStrength();
                setSMeter();
                squelchScheme.getSquelch(false);
                setComErrorIcon();
                
                readRadioControls(false);
            }
//            if (slowRadio || scanStateMachine.scanTimer != null) {
//                vfoDisplay.timerUpdateFreq();
//                //timerSetSliders();  //@TODO Coz fix this.
//            }
            resetTimer();
        }
    }
    /**
     * Determine the state of the comms Led and set it appropriately.
     * On detecting an error in reply text, comError is set to 2 by the radio
     * comms method; then the comError variable is checked
     * by this method and decremented on each check to determine the state of
     * the comms Led.  So you need two good comms in a row to set the led green.
     * 
     */
    private void setComErrorIcon() {
        if (comError > 0) {
            if ((comError -= 1) > 0) {
                comErrorIconLabel.setIcon(this.redLed);
            }
        } else {
            comErrorIconLabel.setIcon(this.greenLed);
        }
    }

    /**
     * Method is called upon changing the radio type or changing the device used
     * for rig communication or startup.
     */
    private void initialize() {
        if (!inhibit) {
            oldRadioFrequency = -1;
            dcdIconLabel.setText("");
            dcdIconLabel.setIcon(greenLed);
            String dcdLedString = "DCD LED indicator is green";
            dcdIconLabel.getAccessibleContext().setAccessibleDescription(dcdLedString);
            // Must reset to defaults again to accommodate change in rig.
            setDefaultComboContent();  
            setupHamlibDaemon();
            setComboBoxScales();                          
            vfoDisplay.setUpFocusManager();  
            vfoDisplay.makeVisible(vfoState); 
            if (sv_jrxToRadioButton.isSelected()) {
                vfoState.setVfoStateSimplex();
                vfoState.setRxVfo(VfoStateInterface.vfoChoice.VFOA);
                vfoState.writeFrequencyToRadioSelectedVfo(AERO_CLUB_FREQ);
                long freqB = vfoState.getVfoBFrequency();
                vfoState.setTextVfoB(freqB);
                writeRadioControls();
            } else {
                // Get the radio VFO settings and write them to application controls.
                vfoDisplayControl.VfoStateInterface.vfoChoice  choice = vfoState.getRxVfo();
                long valueA = vfoState.getVfoAFrequency();
                long valueB = vfoState.getVfoBFrequency();
                if (choice == vfoDisplayControl.VfoStateInterface.vfoChoice.VFOA) {
                    vfoDisplay.frequencyToDigits(valueA);
                } else {
                    vfoDisplay.frequencyToDigits(valueB);
                }
            }
            squelchScheme.setRadioSquelch();
            readRadioControls(true);  // Reads frequency from radio
            startCyclicalTimer();
            measureSpeed(); // Writes frequency to radio.
            setComErrorIcon();            
            memoryScrollPane.getVerticalScrollBar().setUnitIncrement(8);
        }
    }

    private void readRadioControls(boolean all) {
        if (all || this.sv_syncCheckBox.isSelected()) {
            readFrequency();
            for (ControlInterface cont : controlList) {
                cont.selectiveReadValue(all);
            }
        }
    }

    private void writeRadioControls() {
        for (ControlInterface cont : controlList) {
            if (((Component)cont).isEnabled() )
                cont.writeValue(true);
        }
    }

    private void measureSpeed() {
        long freq = vfoState.getSelectedVfoFrequency();
        long t = System.currentTimeMillis();
        oldRadioFrequency = -1;
        setRadioFrequency(freq);
        long dt = System.currentTimeMillis() - t;
        // use a diferent strategy for slow radios
        slowRadio = dt > 75;
        speedIconLabel.setText("");
        speedIconLabel.setIcon(slowRadio ? redLed : greenLed);
        speedIconLabel.setToolTipText(slowRadio ? "Slow radio coms" : "Fast radio coms");
        if (comArgs.debug >= 0) {
            pout("radio com ms delay: " + dt);
        }
    }

    private void setDefaultComboContent() {
        ((RWComboBox) sv_filtersComboBox).comboPlaceholderData();
        ((RWComboBox) sv_ctcssComboBox).comboPlaceholderData();
        ((RWComboBox) sv_preampComboBox).comboPlaceholderData();
        ((RWComboBox) sv_attenuatorComboBox).comboPlaceholderData();
        ((RWComboBox) sv_modesComboBox).comboPlaceholderData();
    }
    /**
     * Create an array of controls which reflect and adjust the rig settings.
     * 
     * The list of available controls for the current rig model are read from
     * rigctld and may not actually be accurate.  
     * 
     * We need a way to override the list provided by rigctl when we find that
     * the capability is incorrect.   For example, IC-7100 does not support
     * antenna selection or if shift adjustment by remote control.  
     */
    private void createControlList() {
        controlList = new ArrayList<>();
        Component[] list = new Component[]{
                sv_squelchSlider,
                sv_volumeSlider,
                sv_rfGainSlider,
                sv_ifShiftComboBox,
                sv_dspComboBox,
                sv_dspCheckBox,               
                sv_filtersComboBox,
                sv_modesComboBox,
                sv_antennaComboBox,
                sv_attenuatorComboBox,
                sv_agcComboBox,
                sv_preampComboBox,
                sv_ctcssComboBox,
                sv_ctcssCheckBox,
                sv_blankerCheckBox,
                sv_apfCheckBox,
                sv_anfCheckBox,
                sv_micGainSlider,
                sv_rfPowerSlider,
                sv_voxLevelSlider,
                sv_enableVoxCheckBox,
                sv_compressionSlider,
                sv_compressionCheckBox
        };
        for (Component comp : list) {
            controlList.add((ControlInterface) comp);
        }
    }

    public String recurseSearch(File dir, Pattern search) {
        String result = null;
        File listFile[] = dir.listFiles();
        if (listFile != null) {
            for (File f : listFile) {
                if (f.isDirectory()) {
                    result = recurseSearch(f, search);
                    if (result != null) {
                        return result;
                    }
                } else {
                    if (search.matcher(f.getName()).matches()) {
                        return f.getPath();
                    }
                }
            }
        }
        return result;
    }

    private String findHamlibOnWindows() {
        File f = new File(".");
        Pattern p = Pattern.compile("(?i).*?" + hamlibExecPath + "\\.exe.*");
        return recurseSearch(f, p);
    }
    /**
     * Initialize the sv_timerIntervalComboBox with ingenius algorithm to make
     * steps { 10ms,20ms,50ms,100ms,.....5s,10s,50s,OFF }.
     * @param timebox 
     */
    private void initTimeValues(RWComboBox timebox) {
        timebox.removeAllItems();
        double[] msteps = new double[]{1, 2, 5};
        String sl;
        double bv = 10;
        for (int p = 0; p <= 3; p++) {
            for (double lv : msteps) {
                double v = bv * lv;
                if (v >= 1000) {
                    sl = String.format("%d s", (int) (v / 1000));
                } else {
                    sl = String.format("%d ms", (int) v);
                }
                timebox.addListItem(sl, v, "" + v);
            }
            bv *= 10;
        }
        timebox.addListItem("Off", 0, "0");
        timebox.setSelectedIndex(5);
    }


    private void setupControls() {
        sv_frame = this;
        speedIconLabel.setText("");
        scanIconLabel.setText("");
        comErrorIconLabel.setText("");
        darkGreen = new Color(0, 128, 0);
        darkBlue = new Color(0, 0, 128);
        darkRed = new Color(128, 0, 0);
        String osName = System.getProperty("os.name").toLowerCase();
        isWindows = (osName.indexOf("win") >= 0);
        isMacOs = osName.startsWith("mac os x");
        if (isWindows) {
            hamlibExecPath = findHamlibOnWindows();
            if (hamlibExecPath == null) {
                JOptionPane.showMessageDialog(
                        this,
                        String.format(
                                "Error: Cannot find hamlib executable \"%s\"\nCannot continue. "
                                        + "Please visit http://arachnoid.com/JRX for help.", hamlibExecPath));
                System.exit(0);
            }
            hamlibExecPath = "\"" + hamlibExecPath + "\"";
            if (comArgs.debug >= 0) {
                pout("have windows exec: " + hamlibExecPath);
            }
        } else {
            hamlibExecPath = fileHelpers.findAbsolutePath(hamlibExecPath);
        }
        
        // yhigh intentionally = ylow to allow the rig to set the range
        ((RWComboBox)sv_ifShiftComboBox).setComboBoxContent("IF", -50, 50, 5, -50, 50, 0, 0, 0); //@todo Coz remove comment
        //sv_ifShiftComboBox.setEnabled(false); // @todo Coz temporary fix.... Remove this line.
        ((RWComboBox)sv_dspComboBox).setComboBoxContent("DSP", 0, 100, 5, 0, 100, 0, 1, 0);
        ((RWComboBox)sv_agcComboBox).setComboBoxContent("AGC", 0, 10, 1, 0, 1, 0, 10, 1);
        ((RWComboBox)sv_antennaComboBox).setComboBoxContent("Ant", 0, 4, 1, 0, 1, 0, 1, 1);  //@todo Coz remove comment
        //sv_antennaComboBox.setEnabled(false); // @todo Coz temporary fix... Remove this line.
        initInterfaceList();
        getSupportedRadios();
        memoryCollection.readMemoryButtons();
        initTimeValues((RWComboBox) sv_timerIntervalComboBox);

        scanDude.initScanValues(sv_scanStepComboBox, 12, sv_scanSpeedComboBox, 5);
        scanDude.initScanValues(null, 0, sv_dwellTimeComboBox, 10);
        scanDude.initScanValues(sv_scopeStepComboBox, 12, sv_scopeSpeedComboBox, 0);
        memoryButtonsPanel.setBackground(new Color(128, 200, 220));
        
        scannerPanel.setBackground(new Color(240, 240, 220));
        scopeControlLeftPanel.setBackground(new Color(240, 240, 220));
        firstSettingsPanel.setBackground(new Color(200, 220, 240));
        rxVfoPanel.setToolTipText("<html>Tune each digit:<ul><li>Mouse wheel (❃): up, down</li><li>Mouse click top: up</li><li>Mouse click bottom: down</li></ul></html>");
        scanDude.updateScanControls();
    }

    // force DSP on if auxiiliary DSP functions are activated
    private void enableDSP() {
        sv_dspCheckBox.setSelected(true);
        ((RWCheckBox) sv_dspCheckBox).actionPerformed(null);
    }

    /**
     *  Get radio capability data from Hamlib backend and set up drop-down lists.
     *  If a capability is not present in the reply to the "\dump_caps" command,
     *  then the UI Component is not enabled (grayed out).  This is the first
     * communication to the rig. If sendRadioCom returns a null radioData, we
     * were unable to talk to the rig.  In this case, enable the radio selection
     * combo box and enable the device selection combo box.  Something went
     * wrong.
     * 
     * @return true if radioData != null.
     */
    private boolean getRigCaps() {
        
        String com = "\\dump_caps";
        radioData = sendRadioCom(com, 3, false);
        System.out.println(" Reply to \\dump_caps "+ radioData);
        if (radioData == null) {
            // Unable to communicate with the radio.
            sv_radioNamesComboBox.setEnabled(true);
            sv_interfacesComboBox.setEnabled(true);
        }  
        vfoState.setVfoCommandChoices(radioData);
        vfoState.setVfoSplitCapability(radioData);
        ((MicGainSlider)sv_micGainSlider).enableIfCapable(radioData);
        ((RfPowerSlider)sv_rfPowerSlider).enableIfCapable(radioData);
        ((VoxLevelSlider)sv_voxLevelSlider).enableIfCapable(radioData);
        ((RWComboBox)sv_ctcssComboBox).enableCap(radioData, "(?ism).*^Can set CTCSS Squelch:\\s+Y$", false);
        ((RWComboBox)sv_agcComboBox).enableCap(radioData, "(?ism).*^Set level:.*?AGC\\(", true);
        ((RWComboBox)sv_attenuatorComboBox).enableCap(radioData, "(?ism).*^Set level:.*?ATT\\(", false);
        ((RWComboBox)sv_antennaComboBox).enableCap(radioData, "(?ism).*^Can set Ant:\\rigSpecs+Y$", false); // Coz override for IC-7100
        ((RWComboBox)sv_preampComboBox).enableCap(radioData, "(?ism).*^Set level:.*?PREAMP\\(", true);
        ((RWSlider)sv_volumeSlider).enableCap(radioData, "(?ism).*^Set level:.*?AF\\(", true); 
        ((RWSlider)sv_rfGainSlider).enableCap(radioData, "(?ism).*^Set level:.*?RF\\(", true);
        ((RWSlider)sv_squelchSlider).enableCap(radioData, "(?ism).*^Set level:.*?SQL\\(", true);
        ((RWComboBox)sv_ifShiftComboBox).enableCap(radioData, "(?ism).*^Set level:.*?IF\\(", true); // Coz override for IC-7100
        ((RWCheckBox)sv_blankerCheckBox).enableCap(radioData, "(?ism).*^Set functions:.*?\\sNB\\s", false);
        ((RWCheckBox)sv_anfCheckBox).enableCap(radioData, "(?ism).*^Set functions:.*?\\sANF\\s", false);
        ((RWCheckBox)sv_apfCheckBox).enableCap(radioData, "(?ism).*^Set functions:.*?\\sAPF\\s", false);
        ((RWComboBox)sv_dspComboBox).enableCap(radioData, "(?ism).*^Set level:.*?NR\\(", true);
        ((RWCheckBox)sv_enableVoxCheckBox).enableCap(radioData, "(?ism).*^Set functions:.*?\\sVOX\\s", false);
        ((RWCheckBox)sv_dspCheckBox).enableCap(radioData, "(?ism).*^Set level:.*?NR\\(", false);
        ((RWCheckBox)sv_compressionCheckBox).enableCap(radioData,"(?ism).*^Set functions:.*?\\sCOMP\\s", false);
        ((RWSlider)sv_compressionSlider).enableCap(radioData, "(?ism).*^Set level:.*?COMP\\(", true);
        
        String s = sendRadioCom("\\get_dcd", 0, false);
        dcdCapable = (s != null && s.matches("\\d+"));
        squelchScheme.setSquelchScheme();
        return true;
    }


    protected String getRadioModel() {
        RWComboBox box = (RWComboBox)sv_radioNamesComboBox;        
        return box.readValueStr();
    }

    public void waitMS(int ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }


    protected double ntrp(double xa, double xb, double ya, double yb, double x) {
        return ((x - xa) * (yb - ya) / (xb - xa)) + ya;
    }

    protected int intrp(double xa, double xb, double ya, double yb, double x) {
        return (int) ntrp(xa, xb, ya, yb, x);
    }

    protected double freqStrength(long v) {
        if (v > 0) {
            long t1 = System.currentTimeMillis();
            // force a radio call
            oldRadioFrequency = -1;
            setRadioFrequency(v);
            getSignalStrength();
            long t2 = System.currentTimeMillis();
            double dt = (t2 - t1) / 1000.0;
            if (comArgs.debug >= 1) {
                pout(String.format("scope sample delay: %f", dt));
            }
        }
        return signalStrength;
    }

    protected void readFrequency() {
        long freq = vfoState.getRxFrequency();
        vfoDisplay.frequencyToDigits(freq);
    }
    
    /**
     * 
     * 
     * @param v Frequency in hertz to send to radio.
     * @return true when radio is actually updated.
     */
    public boolean requestSetRadioFrequency(long v) {
        if (!slowRadio && scanStateMachine.scanTimer == null) {            
            setRadioFrequency(v);    
            return true;
        } else {
            return false;
        }
    }
    public void setRadioFrequency(long v) {
        try {
            if (validSetup()) {
                if (v < 0) {
                    v = vfoState.getSelectedVfoFrequency();
                }
                if (v <= 0) {
                    //throw (new Exception("frequency <= 0"));
                    System.out.println("vfoState current frequency <= 0.");
                } else {
                    if (oldRadioFrequency != v) {
                        boolean success = vfoState.writeFrequencyToRadioSelectedVfo(v);
                        oldRadioFrequency = v;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }


    protected boolean validSetup() {
        return (!inhibit);
    }

    public String getMode() {
        String s = null;
        if (validSetup()) {
            s = (String) sv_modesComboBox.getSelectedItem();
        }
        return s;
    }
    
    /**
     * Initialize comm device (interface) comboBox by searching /dev/tty for
     * possible serial ports or USB ports.
     */
    private void initInterfaceList() {
        sv_interfacesComboBox.removeAllItems();
        sv_interfacesComboBox.addItem("-- Interfaces --");
        if (isWindows) {
            for (int i = 1; i <= 16; i++) {
                sv_interfacesComboBox.addItem(String.format("COM%d", i));
            }
        } else {
            String data;
            if (isMacOs) {
                // List all usb interfaces using MacOs.
                data = runSysCommand(new String[]{"bash", "-c", "echo  /dev/tty.* /dev/video.*"}, true);
            } else {           
                // List all serial interfaces using Linux.
                data = runSysCommand(new String[]{"bash", "-c", "echo /dev/ttyS* /dev/ttyUSB* /dev/video*"}, true);
            }
            if (comArgs.debug >= 1) {
                pout("serial list output: [" + data + "]");
            }
            for (String s : data.split("\\s+")) {
                // don't add unexpanded arguments
                if (!s.matches(".*\\*.*")) {
                    sv_interfacesComboBox.addItem(s);
                    System.out.println("Found mac usb serial device :"+s);
                }
            }
        }
    }

    private void setComboBoxScales() {
        if (hamlibExecPath == null) return;
        inhibit = true; // Do not cause a change event handler to call initialize().
        ((RWComboBox) sv_preampComboBox).setGenericComboBoxScale("Pre", "(?ism).*^Preamp:\\s*(.*?)\\s*$.*", true, true);
        ((RWComboBox) sv_attenuatorComboBox).setGenericComboBoxScale("Att", "(?ism).*^Attenuator:\\s*(.*?)\\s*$.*", true, true);
        ((RWComboBox) sv_filtersComboBox).setGenericComboBoxScale("", "", true, true);
        ((RWComboBox) sv_modesComboBox).setGenericComboBoxScale("Mode", "(?ism).*^Mode list:\\s*(.*?)\\s*$.*", false, false);
        ((RWComboBox) sv_ctcssComboBox).setGenericComboBoxScale("CTCSS", "(?ism).*^CTCSS:\\s*(.*?)\\s*$.*", false, true);
        inhibit = false;
    }
    /**
     * Read a list of supported radios from hamlib backend and insert into 
     * radioCodes TreeMap; then
     * populate sv_radioNamesComboBox with the radio names.  The hamlib daemon
     * runs for the sole purpose of handling the -l command and then exits. The
     * rig specs are one rig per line and formatted in 5 columns using spaces 
     * instead of tabs.  So information in a column always starts at the same
     * character position from the start of the line.  That makes it easier to
     * check the parsing by position.  The rig numbers are increasing from top to
     * bottom of the list, but are not always consecutive.  It would be good to
     * display the back end  "Version" that is listed for each rig so that bad
     * behavior could be tracked to a particular rig version.
     * 
     * Requirement: It is possible to have a left-over rigctld running on the 
     * system.  If so, you will get that rigctld rig caps and not the ones you
     * are expecting.  You must see if one is already running.  It cannot be
     * killed by giving it a command.  Warn the user with a dialog box.
     * 
     * 
     * @todo Move this to RadioNamesComboBox class.
     */
    private void getSupportedRadios() {
        radioCodes = new TreeMap<>();
        radioNames = new TreeMap<>();
        String a, b, rigSpecs="";
        //p("trying to read rig specs...");
        if (hamlibExecPath == null) {
            JOptionPane.showMessageDialog(this,"MISSING HAMLIB", 
                    "Missing Hamlib path.",
                    JOptionPane.WARNING_MESSAGE);
        } else {
        
            rigSpecs = runSysCommand(new String[]{hamlibExecPath, "-l"}, true);
            if (comArgs.debug >= 1) {
                pout("dump from rigctld -l: [" + rigSpecs + "]");
            }
        } 
        for (String item : rigSpecs.split(lineSep)) {
            // the try ... catch is only to filter the table header
            if (item.length() > 30) {
                try {
                    if (comArgs.debug >= 1) {
                        pout("rigctl radio description line: [" + item + "]");
                    }
                    // Remove the extra white space and place a tab between the 
                    // first two fields on the line.
                    String parse = item.replaceFirst("^\\s*(\\S+)\\s*(.*)$", "$1\t$2");
                    String[] fields = parse.split("\t");
                    if (fields != null && fields.length > 1) {
                        a = fields[0].trim();
                        if (a.matches("[0-9]+") && fields[1].length() > 47) {
                            b = fields[1].substring(0, 47).trim();
                            // Replace whiteSpace between Mfg and Model with one 
                            // space.  String b looks like "Yeasu FT-847" when it
                            // is put into the radioCodes tree as a key.
                            b = b.replaceAll("\\s+", " ");
                            int v = Integer.parseInt(a);
                            if (comArgs.debug >= 1) {
                                pout("radio record: " + b + " = " + v);
                            }
                            radioCodes.put(b, v);
                            radioNames.put(v, b);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                }
            }
        }
        sv_radioNamesComboBox.removeAllItems();
        sv_radioNamesComboBox.addItem("-- Radios --");
        for (String key : radioCodes.keySet()) {
            sv_radioNamesComboBox.addItem(key);
        }
    }

    /**
     * Return the key String for the given value.  
     * 
     * @param value
     * @return 
     */
    public String getNameKeyForRadioCodeValue(int value) {
        String rigName = radioNames.get(value);
        if (rigName == null) return "Radio Unknown" ;  
        else return rigName;
    }
       
    
    /**
     * Run a command line command an retrieve the response - used to get a list
     * of radios and their codes before the socket has been set up.
     */ 
    private String runSysCommand(String[] array, boolean read) {
        String result = "";
        try {
            Process p = new ProcessBuilder(array).redirectErrorStream(true).start();
            if (read) {
                result = new Scanner(p.getInputStream()).useDelimiter("\\Z").next();
            }
        } 
        catch  (NullPointerException e) {
                System.out.print("runSysCommand() Failed to start process for command : ");
                for (int iii = 0; iii<array.length; iii++) {
                    System.out.print(array[iii]);
                    System.out.println();
                }
                e.printStackTrace(System.out);
        } 
        catch (Exception e) {
            e.printStackTrace(System.out);           
        }
        return result;
    }

    protected boolean askUser(String prompt) {
        Beep.beep();
        int choice = JOptionPane.showConfirmDialog(this, prompt);
        return choice == JOptionPane.OK_OPTION;
    }

    protected void tellUser(String prompt) {
        Beep.beep();
        JOptionPane.showMessageDialog(this, prompt);
    }

    
    /**
     * Start up the Hamlib daemon to run for the duration of the app waiting for
     * text commands from this app via Tcp connection.
     * 
     * Requirement: Determine the rigName and set the radioNamesComboBox to
     * the correct rigName.  It is displayed prominently on the top of the UI
     * even if it is disabled.
     * 
     * Note: Prerequisite for this routine is the creation of the radioCodes.
     */
    private void setupHamlibDaemon() {
        if (!inhibit) {
            closeConnection();
            String interfaceName = null;
            String rigName = null;
            int rigCode = -1;
            if (comArgs.rigName != null) {
                rigName = comArgs.rigName;
                sv_radioNamesComboBox.setSelectedItem(rigName);
                sv_radioNamesComboBox.setEnabled(false);
                try {
                    rigCode = radioCodes.get(rigName);
                } catch (Exception e) {
                    tellUser(String.format("Error: rig name \"%s\" not known.", rigName));
                    sv_radioNamesComboBox.setEnabled(true);
                }
            }
            if (comArgs.rigCode >= 0 && rigCode == -1) {
                rigCode = comArgs.rigCode;
                rigName = getNameKeyForRadioCodeValue(rigCode);
                inhibit = true;
                sv_radioNamesComboBox.setSelectedItem(rigName);
                // Disable comboBox because choice has already been made.
                sv_radioNamesComboBox.setEnabled(false);
                inhibit = false;
            }
            if (comArgs.interfaceName != null) {
                interfaceName = comArgs.interfaceName;
                inhibit = true;
                sv_interfacesComboBox.setSelectedItem(interfaceName);
                sv_interfacesComboBox.setEnabled(false);
                inhibit = false;
            } else if (sv_interfacesComboBox.getSelectedIndex() > 0) {
                interfaceName = (String) sv_interfacesComboBox.getSelectedItem();
            }

            if (rigCode == -1 && sv_radioNamesComboBox.getSelectedIndex() > 0) {
                rigName = (String) sv_radioNamesComboBox.getSelectedItem();
                rigCode = radioCodes.get(rigName);
            }
            pout("setupHamlibDaemon rigCode :" +rigCode+ " interfaceName : "+ interfaceName);
            if (rigCode >= 0 && interfaceName != null) {
                String[] com;
                if (sv_hamrigUseCustomSettings) {
                    com = new String[]{
                            hamlibExecPath,
                            String.format("--set-conf=write_delay=%d", sv_hamrigWriteDelay),
                            String.format("--set-conf=post_write_delay=%d", sv_hamrigPostWriteDelay),
                            String.format("--set-conf=retry=%d", sv_hamrigRetries),
                            String.format("--set-conf=timeout=%d", sv_hamrigTimeout),
                            "-m",
                            "" + rigCode,
                            "-r",
                            interfaceName
                    };
                } else {
                    com = new String[]{
                            hamlibExecPath,
                            "-m",
                            "" + rigCode,
                            "-r",
                            interfaceName
                    };
                }

                if (comArgs.debug >= 0) {
                    pout("setup daemon with: " + rigName + "," + rigCode + "," + interfaceName);
                }
                try {
                    hamlibDaemon = new ProcessBuilder(com).redirectErrorStream(true).start();
                    
                    boolean connected = false;
                    int n = 5;
                    while (!connected && n >= 0) {
                        try {
                            hamlibSocket = new Socket(hamlibHost, hamlibPort);
                            hamlibSocket.setKeepAlive(true);
                            hamlibSocket.setTcpNoDelay(true);
                            hamlib_is = hamlibSocket.getInputStream();
                            hamlib_os = hamlibSocket.getOutputStream();
                            connected = true;// hamlibSocket.isConnected();
                            if (comArgs.debug >= 0) {
                                pout("socket connected: " + connected);
                            }
                        } catch (Exception e) {
                            if (comArgs.debug >= 0) {
                                pout("fail connect " + e.getMessage());
                            }
                            waitMS(500);
                            if (n-- <= 0) {
                                tellUser("Error: Cannot connect to Hamlib Daemon process.");
                            }
                        }
                    }
                    // Now get radio capability data and set up drop-down lists.
                    getRigCaps();
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                }
            }
        }
    }
    /**
     * Only upon startup, determine if a rigctld is already running 
     * and if so, connect to it via normal means and give it a quit command.
     * 
     */
    private void dismissOldHamlibTask() {
        boolean connected = false;
        String result = null;
        int n = 2;
        int localDebug = 0;
        while (!connected && n >= 0) {
            try {
                hamlibSocket = new Socket(hamlibHost, hamlibPort);
                hamlibSocket.setKeepAlive(true);
                hamlibSocket.setTcpNoDelay(true);
                hamlib_is = hamlibSocket.getInputStream();
                hamlib_os = hamlibSocket.getOutputStream();
                connected = true;// hamlibSocket.isConnected();
            } catch (Exception e) {
                n--;
                waitMS(500);
            }
        }
        if (hamlibSocket != null) {
            try {
                // Send a Quit command to rigctld via tcp port output stream.
                String s = "q";
                hamlib_os.write((s + lineSep).getBytes());
                hamlib_os.flush();
                if (comArgs.debug >= localDebug) {
                    pout("sendradiocom   emit: [" + s + "]");
                }
                result = readInputStream(hamlib_is);
                if (comArgs.debug >= localDebug) {
                    pout("sendradiocom result: [" + result.replaceAll("[\r\n]", " ") + "]");
                }
                // close streams
                hamlibSocket.shutdownInput(); 
                hamlibSocket.shutdownOutput();         
                // close socket
                if (hamlibSocket != null) {
                    hamlibSocket.close();
                    waitMS(100);
                    hamlibSocket = null;
                }
            }
            catch (Exception eProbe) {
                pout("dismissOldHamlibTask had exception " + eProbe);
            }
            // Only the second arg (title) is read by voiceOver.            
            JOptionPane.showMessageDialog(this,
                    "rigctld is already running. Please kill the process.", 
                    "rigctld is already running. Please kill the process.", 
                    JOptionPane.WARNING_MESSAGE);
        }       
    }
    
    /**
     * Given a swing Component (created by the Swing Gui designer), the reply
     * text "source" from a rigctl capabilities command "\dump_Caps", a regular
     * expression string "search" for searching the reply text, and a boolean which is
     * true to set value range.  The Component is enabled if it is found
     * in the reply by the search regex string.
     * 
     * Note: This method is deprecated.  Moved to ControlInterface class as default method.
     * @param cc
     * @param source
     * @param search
     * @param level when true searches for level command capability: "L  xxxx"
     */
    protected void enableControlCap(Component cc, String source, String search, boolean level) {
        boolean enabled = (source != null && source.matches(search + ".*"));
        cc.setEnabled(enabled);
        if (enabled && level) {
            try {
                if (cc instanceof ControlInterface) {
                    String range = source.replaceFirst(search + "([0-9+-]+)\\.\\.([0-9+-]+).*", "$1,$2");
                    String[] digits = range.split(",");
                    int low = Integer.parseInt(digits[0]);
                    int high = Integer.parseInt(digits[1]);
                    if (high - low == 0) {
                        low = 0;
                        high = 1;
                    }
                    ControlInterface box = (ControlInterface) cc;
                    //(source + " control cap:" + low + "," + high);
                    box.setYLow(low);
                    box.setYHigh(high);
                }
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
    }

    private String readInputStream(InputStream is) {
        StringBuilder sb = new StringBuilder();
        try {
            int len;
            do {
                len = is.read(readBuffer, 0, readBufferLen);
                if (len >= 0) {
                    sb.append(new String(readBuffer, 0, len));
                }
            } while (len == readBufferLen);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        return sb.toString().trim();
    }
    
    /**
     * Send the string "s" to the radio via rigctld TCP port with writeLock and 
     * return the result string or null when in error.
     * 
     * @param s
     * @param localDebug
     * @param writeMode
     * @return 
     */
    public String sendRadioCom(String s, int localDebug, boolean writeMode) {
        String result = null;
        int countBefore = vfoState.lock.getWriteHoldCount();
        try {            
            vfoState.lock.writeLock().lock();            
            if (validSetup() && hamlibDaemon != null && hamlibSocket != null && s != null) {
                if (hamlibSocket.isConnected()) {
                    try {
                        hamlib_os.write((s + lineSep).getBytes());
                        hamlib_os.flush();
                        if (comArgs.debug >= localDebug) {
                            pout("sendradiocom   emit: [" + s + "]");
                        }
                        result = readInputStream(hamlib_is);
                        if (comArgs.debug >= localDebug) {
                            pout("sendradiocom result: [" + result.replaceAll("[\r\n]", " ") + "]");
                            //p("sendradiocom result: [" + result + "]");
                        }
                        if (result.matches("(?i).*RPRT -.*")) {
                            comError = 2;
                            result = null;
                        }
                    } catch (Exception e) {
                        e.printStackTrace(System.out);
                    }
                }
            }
            vfoState.lock.writeLock().unlock();
        }
        catch (Exception eComms) {
            System.out.println("sendRadioCom() had lock exception "+ eComms);
        }
        // Debug info to System.out for accumulated locks.
        int countAfter = vfoState.lock.getWriteHoldCount();
        boolean plusLocks = ((countAfter - countBefore) > 0);           
        if (vfoState.lock.isWriteLockedByCurrentThread() && plusLocks ) {
            pout("sendRadioCom() comms is writeLocked by current" +
                    " thread; count = "+countAfter);                           
        }
        return result;
    }
    


    protected void setVolume(boolean squelchOpen) {
        //p("setvolume " + squelchOpen);
        double volume = ((ControlInterface) this.sv_volumeSlider).getConvertedValue();
        volume = (squelchOpen) ? volume : 0;
        if (volume != oldVolume) {
            setVolumeDirect(volume);
            oldVolume = volume;
        }
    }

    private void setVolumeDirect(double v) {
        String com = String.format("L AF %.2f", v);
        sendRadioCom(com, 0, true);
        ((RWSlider) this.sv_volumeSlider).oldLevel = -1;
    }

    private void squelchOnExit() {
        ((RWSlider) sv_squelchSlider).writeValue(true);
    }

    private void closeConnection() {
        try {
            if (periodicTimer != null) {
                periodicTimer.cancel();
                periodicTimer = null;
            }
            if (this.sv_volumeExitCheckBox.isSelected()) {
                setVolumeDirect(0.0);
            }
            squelchOnExit();
            if (hamlibSocket != null) {
                hamlibSocket.close();
                waitMS(100);
                hamlibSocket = null;
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }


    protected SweepScope getScopePanel() {
        return (SweepScope) scopeDisplayPanel;
    }

    private boolean testRawSignalMode() {
        boolean raw = this.sv_rawSigCheckBox.isSelected();
        if (raw) {
            squelchLow = -256;
            squelchHigh = 256;
        } else {
            squelchLow = -100;
            squelchHigh = 100;
        }
        return raw;
    }

    protected void getSignalStrength() {
        try {
            // Do not perform operation for high debug levels
            if (comArgs.debug < 2) {
                String com = (testRawSignalMode()) ? "l RAWSTR" : "l STRENGTH";
                String result = sendRadioCom(com, 1, false);
                //p("signal strength result: [" + result + "]");
                if (result != null && result.length() > 0) {
                    if (result.matches("(?ims).*?^-?\\d+$.*")) {
                        result = result.replaceFirst("(?ims).*?^(-?\\d+)$.*", "$1");
                        signalStrength = Double.parseDouble(result);
                        //p("ss: [" + result + "]," + ss);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }
    /**
     * Convert double value in db to S units.
     *    based on a little online research
     * x (db) 0 = s9
     * x (db) -54 = s0
     * @author Paul
     * @param x double signal strength in dbm
     * @return double S units
     */
     double dbToSUnits(double x) {
        return 9 + x / 6.0;
    }

    protected void setSMeter() {
        if (validSetup()) {
            try {
                double ss = signalStrength;
                if (testRawSignalMode()) {
                    ss -= 120;
                }
                double sunit = dbToSUnits(ss);
                signalProgressBar.setValue((int) ss);
                signalProgressBar.setString(String.format("S %.0f (%.0f db)", sunit, ss));

            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
    }




    public String readTextFile(String path, String lineSep) {
        String result = "";
        try {
            result = new Scanner(new File(path)).useDelimiter("\\Z").next();
        } catch (Exception e) {
            // e.printStackTrace();
        }
        return result;
    }

    public void writeTextFile(String path, String data) {
        try {
            try (PrintWriter out = new PrintWriter(new File(path))) {
                out.write(data);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    public void launchHelp(String url) {
        try {
            Desktop desktop = Desktop.getDesktop();
            desktop.browse(new URI(url));
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace(System.out);
        }
    }

    protected void writeToClipboard(String s) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable transferable = new StringSelection(s);
        clipboard.setContents(transferable, null);
    }

    protected String readFromClipboard() {
        String data = "";
        try {
            data = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (HeadlessException | UnsupportedFlavorException | IOException e) {
        }
        return data;
    }

    private void showConfigDialog() {
        ConfigComsDialog dlg = new ConfigComsDialog(
                this,
                true,
                sv_hamrigWriteDelay,
                sv_hamrigPostWriteDelay,
                sv_hamrigRetries,
                sv_hamrigTimeout,
                sv_hamrigUseCustomSettings);
        dlg.setTitle(programName + " communication settings");
        dlg.setVisible(true);
        sv_hamrigUseCustomSettings = dlg.accept;
        if (sv_hamrigUseCustomSettings) {
            sv_hamrigWriteDelay = Integer.parseInt(dlg.writeDelayTextField.getText());
            sv_hamrigPostWriteDelay = Integer.parseInt(dlg.postWriteDelayTextField.getText());
            sv_hamrigRetries = Integer.parseInt(dlg.retriesTextField.getText());
            sv_hamrigTimeout = Integer.parseInt(dlg.timeoutTextField.getText());
        }
    }

    private void closeApp() {
        getScopePanel().stopSweep(false);
        scanStateMachine.stopScan(false);
        if (periodicTimer != null) {
            periodicTimer.cancel();
            periodicTimer = null;
        }
        if (scanStateMachine.scanTimer != null) {
            scanStateMachine.scanTimer.cancel();
            scanStateMachine.scanTimer = null;
        }
        config.write();
        memoryCollection.writeMemoryButtons();
        closeConnection();
        // Kill the Hamlib daemon.
        for (int tries=0; tries < 2; tries++){
            try {
                hamlibDaemon.destroy();
                waitMS(1000); // Sometimes its hard to kill this guy.               
            }
            catch (Exception e) {
                pout("closeApp failed to terminate hamlibDaemon with exception "+e);
            }
        }
        System.exit(0);
    }

    public String gcFromTimeMS(long timeMS) {
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTimeInMillis(timeMS);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return sdf.format(gc.getTime());
    }

    public String gcFromNow() {
        return gcFromTimeMS(System.currentTimeMillis());
    }

    // a shorthand function for debugging
    public void pout(String s) {
        long t = System.currentTimeMillis();
        Time et = new Time(t);
        double dt = (t - oldTime) / 1000.0;
        System.out.println(String.format("%s : %06f : %s", gcFromNow(), dt, s));
        oldTime = t;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jrxRadioButtonGroup = new javax.swing.ButtonGroup();
        splitFreqGroup = new javax.swing.ButtonGroup();
        jRadioButtonMenuItem1 = new javax.swing.JRadioButtonMenuItem();
        jMenuItem1 = new javax.swing.JMenuItem();
        radioPanel = new javax.swing.JPanel();
        overallTabbedPane = new javax.swing.JTabbedPane();
        operateTransceiverPanel = new javax.swing.JPanel();
        operationDetailsTabbedPane = new javax.swing.JTabbedPane();
        transmitterPanel = new javax.swing.JPanel();
        sv_rfPowerSlider = new RfPowerSlider(this);
        jLabel8 = new javax.swing.JLabel();
        sv_antennaComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"Y","");
        jLabel18 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        sv_compressionCheckBox = new RWCheckBox(this, "U", "COMP");
        sv_enableVoxCheckBox = new RWCheckBox(this,"U","VOX");
        sv_compressionSlider = new RWSlider(this,"L","COMP",0);
        sv_micGainSlider = new MicGainSlider(this);
        sv_voxLevelSlider = new VoxLevelSlider(this);
        micGainLabel = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        sv_txCtcssCheckBox = new RWCheckBox(this,"U","TONE");
        ifControlsPanel = new javax.swing.JPanel();
        sv_filtersComboBox = new com.cozcompany.jrx.accessibility.IfFilterComboBox(this);
        verticalListPanel = new javax.swing.JPanel();
        sv_rawSigCheckBox = new RWCheckBox(this,null,null);
        sv_blankerCheckBox = new RWCheckBox(this,"U","NB");
        sv_apfCheckBox = new RWCheckBox(this,"U","APF");
        sv_anfCheckBox = new RWCheckBox(this,"U","ANF");
        sv_dspCheckBox = new RWCheckBox(this,"U","NR");
        jLabel13 = new javax.swing.JLabel();
        sv_dspComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","NR");
        jLabel14 = new javax.swing.JLabel();
        sv_ifShiftComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","IF");
        jLabel15 = new javax.swing.JLabel();
        sv_preampComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","PREAMP");
        jLabel16 = new javax.swing.JLabel();
        sv_attenuatorComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","ATT");
        jLabel17 = new javax.swing.JLabel();
        dstarPanel = new javax.swing.JPanel();
        keyerPanel = new javax.swing.JPanel();
        rttyPanel = new javax.swing.JPanel();
        recieverGroupBox = new GroupBox();
        sv_squelchSlider = new com.cozcompany.jrx.accessibility.RWSlider(this,"L","SQL",0);
        sv_rfGainSlider = new com.cozcompany.jrx.accessibility.RWSlider(this,"L","RF",50);
        sv_ctcssComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"ctcss","");
        sv_synthSquelchCheckBox = new RWCheckBox(this,null,null);
        rfGainLabel = new javax.swing.JLabel();
        squelchLabel = new javax.swing.JLabel();
        sv_agcComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","AGC");
        agcLabel = new javax.swing.JLabel();
        sv_ctcssCheckBox = new RWCheckBox(this,"U","TSQL");
        sv_modesComboBox = new com.cozcompany.jrx.accessibility.ModesComboBox(this);
        sv_volumeSlider = new com.cozcompany.jrx.accessibility.AfGainSlider(this);
        afGainLabel = new javax.swing.JLabel();
        scanPanel = new javax.swing.JPanel();
        channelsTabbedPane = new javax.swing.JTabbedPane();
        memoryStoragePanel = new javax.swing.JPanel();
        memoryScrollPane = new javax.swing.JScrollPane();
        memoryButtonsPanel = new javax.swing.JPanel();
        buttonPanel2 = new javax.swing.JPanel();
        copyMemButton = new javax.swing.JButton();
        pasteMemButton = new javax.swing.JButton();
        eraseMemButton = new javax.swing.JButton();
        favoriteSWLChannels = new javax.swing.JPanel();
        memoryPanel = new javax.swing.JPanel();
        scannerPanel = new javax.swing.JPanel();
        sv_scanStepComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,null,null);
        jLabel3 = new javax.swing.JLabel();
        sv_scanSpeedComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,null,null);
        jLabel4 = new javax.swing.JLabel();
        sv_dwellTimeComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,null,null);
        jLabel5 = new javax.swing.JLabel();
        scanIconLabel = new javax.swing.JLabel();
        scanDownButton = new javax.swing.JButton();
        scanStopButton = new javax.swing.JButton();
        scanUpButton = new javax.swing.JButton();
        sv_squelchCheckBox = new RWCheckBox(this,null,null);
        jrxScopePanel = new javax.swing.JPanel();
        scopePanel = new javax.swing.JPanel();
        scopeDisplayPanel = new SweepScope(this);
        scopeControlPanel = new javax.swing.JPanel();
        scopeControlLeftPanel = new javax.swing.JPanel();
        sv_scopeStepComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,null,null);
        sv_scopeSpeedComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,null,null);
        scopeStartStopButton = new javax.swing.JButton();
        sv_scopeDotsCheckBox = new javax.swing.JCheckBox();
        scopeScaleButton = new javax.swing.JButton();
        scopeDefaultsButton = new javax.swing.JButton();
        copyButton = new javax.swing.JButton();
        scanHelpButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        appSettingsPanel = new javax.swing.JPanel();
        firstSettingsPanel = new javax.swing.JPanel();
        helpButton = new javax.swing.JButton();
        sv_jrxToRadioButton = new javax.swing.JRadioButton();
        sv_radioToJrxButton = new javax.swing.JRadioButton();
        sv_syncCheckBox = new javax.swing.JCheckBox();
        sv_timerIntervalComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,null,null);
        jLabel2 = new javax.swing.JLabel();
        sv_volumeExitCheckBox = new javax.swing.JCheckBox();
        tuneComsButton = new javax.swing.JButton();
        sv_radioNamesComboBox = new javax.swing.JComboBox<>();
        muteCheckBox = new javax.swing.JCheckBox();
        ledPanel = new javax.swing.JPanel();
        speedIconLabel = new javax.swing.JLabel();
        comErrorIconLabel = new javax.swing.JLabel();
        dcdIconLabel = new javax.swing.JLabel();
        radioNamesLabel = new javax.swing.JLabel();
        signalProgressBar = new javax.swing.JProgressBar();
        sv_interfacesComboBox = new javax.swing.JComboBox<>();
        vfoTabbedPane = new javax.swing.JTabbedPane();
        rxVfoPanel = new javax.swing.JPanel();
        jInternalFrame1 = new VfoDisplayControl(this);
        txVfoPanel = new javax.swing.JPanel();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu3 = new javax.swing.JMenu();

        jRadioButtonMenuItem1.setSelected(true);
        jRadioButtonMenuItem1.setText("jRadioButtonMenuItem1");

        jMenuItem1.setText("jMenuItem1");

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        overallTabbedPane.setMinimumSize(new java.awt.Dimension(100, 400));
        overallTabbedPane.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                overallTabbedPaneStateChanged(evt);
            }
        });

        sv_rfPowerSlider.setMajorTickSpacing(10);
        sv_rfPowerSlider.setMinorTickSpacing(5);
        sv_rfPowerSlider.setPaintLabels(true);
        sv_rfPowerSlider.setPaintTicks(true);

        jLabel8.setFont(new java.awt.Font("Lucida Grande", 0, 14)); // NOI18N
        jLabel8.setLabelFor(sv_rfPowerSlider);
        jLabel8.setText("RF Power Output");

        sv_antennaComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_antennaComboBox.setToolTipText("Available antennas (❃)");

        jLabel18.setText("Antenna");

        jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        sv_compressionCheckBox.setText("Enable Comp");
        sv_compressionCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_compressionCheckBoxActionPerformed(evt);
            }
        });

        sv_enableVoxCheckBox.setText("Enable VOX");
        sv_enableVoxCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_enableVoxCheckBoxActionPerformed(evt);
            }
        });

        sv_compressionSlider.setMajorTickSpacing(10);
        sv_compressionSlider.setMinorTickSpacing(5);
        sv_compressionSlider.setPaintTicks(true);

        sv_micGainSlider.setMajorTickSpacing(10);
        sv_micGainSlider.setMinorTickSpacing(5);
        sv_micGainSlider.setPaintTicks(true);

        sv_voxLevelSlider.setMajorTickSpacing(10);
        sv_voxLevelSlider.setMinorTickSpacing(5);
        sv_voxLevelSlider.setPaintTicks(true);

        micGainLabel.setLabelFor(sv_micGainSlider);
        micGainLabel.setText("MIC GAIN");

        jLabel11.setLabelFor(sv_voxLevelSlider);
        jLabel11.setText("VOX Level");

        jLabel10.setLabelFor(sv_compressionSlider);
        jLabel10.setText("Compression");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(sv_compressionSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel10)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(sv_compressionCheckBox))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(sv_voxLevelSlider, javax.swing.GroupLayout.DEFAULT_SIZE, 160, Short.MAX_VALUE)
                            .addComponent(sv_micGainSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(micGainLabel)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel11, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(sv_enableVoxCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 114, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap(23, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(9, 9, 9)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(sv_voxLevelSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_micGainSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_compressionSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel11)
                            .addComponent(sv_enableVoxCheckBox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(micGainLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(12, 12, 12)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel10)
                            .addComponent(sv_compressionCheckBox))))
                .addContainerGap(15, Short.MAX_VALUE))
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jLabel10, sv_compressionCheckBox, sv_compressionSlider});

        jPanel1Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jLabel11, sv_enableVoxCheckBox, sv_voxLevelSlider});

        jPanel1Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {micGainLabel, sv_micGainSlider});

        sv_txCtcssCheckBox.setText("Tx CTCSS TONE");

        javax.swing.GroupLayout transmitterPanelLayout = new javax.swing.GroupLayout(transmitterPanel);
        transmitterPanel.setLayout(transmitterPanelLayout);
        transmitterPanelLayout.setHorizontalGroup(
            transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(transmitterPanelLayout.createSequentialGroup()
                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addGap(16, 16, 16)
                        .addComponent(sv_rfPowerSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 274, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(transmitterPanelLayout.createSequentialGroup()
                                .addComponent(sv_antennaComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jLabel18, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(sv_txCtcssCheckBox))))
                .addContainerGap(24, Short.MAX_VALUE))
        );
        transmitterPanelLayout.setVerticalGroup(
            transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(transmitterPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sv_rfPowerSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(sv_antennaComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel18, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_txCtcssCheckBox))
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(124, Short.MAX_VALUE))
        );

        operationDetailsTabbedPane.addTab("Transmitter", null, transmitterPanel, "Important xmit controls and details.");
        transmitterPanel.getAccessibleContext().setAccessibleName("Transmitter ");
        transmitterPanel.getAccessibleContext().setAccessibleDescription("Set output power and mic gain");

        sv_filtersComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_filtersComboBox.setToolTipText("Bandwidth I F filters (❃) ");

        verticalListPanel.setOpaque(false);

        sv_rawSigCheckBox.setText("Raw");
        sv_rawSigCheckBox.setToolTipText("<html>Use unconverted signal<br/>strength readings");
        sv_rawSigCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_rawSigCheckBoxActionPerformed(evt);
            }
        });

        sv_blankerCheckBox.setText("NB");
        sv_blankerCheckBox.setToolTipText("<html>Noise Blanker -- reduces<br/>some kinds of noise");

        sv_apfCheckBox.setText("APF");
        sv_apfCheckBox.setToolTipText("Automatic Peak Filter");
        sv_apfCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_apfCheckBoxActionPerformed(evt);
            }
        });

        sv_anfCheckBox.setText("ANF");
        sv_anfCheckBox.setToolTipText("Automatic Notch Filter");
        sv_anfCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_anfCheckBoxActionPerformed(evt);
            }
        });

        sv_dspCheckBox.setText("DSP");
        sv_dspCheckBox.setToolTipText("Digital signal processing");

        javax.swing.GroupLayout verticalListPanelLayout = new javax.swing.GroupLayout(verticalListPanel);
        verticalListPanel.setLayout(verticalListPanelLayout);
        verticalListPanelLayout.setHorizontalGroup(
            verticalListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(verticalListPanelLayout.createSequentialGroup()
                .addGroup(verticalListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(verticalListPanelLayout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addComponent(sv_rawSigCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(verticalListPanelLayout.createSequentialGroup()
                        .addGap(1, 1, 1)
                        .addComponent(sv_dspCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(5, 5, 5))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, verticalListPanelLayout.createSequentialGroup()
                .addGroup(verticalListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(sv_apfCheckBox, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sv_anfCheckBox, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sv_blankerCheckBox, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        verticalListPanelLayout.setVerticalGroup(
            verticalListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(verticalListPanelLayout.createSequentialGroup()
                .addComponent(sv_rawSigCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(sv_blankerCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(sv_dspCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(sv_apfCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(sv_anfCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        jLabel13.setLabelFor(sv_filtersComboBox);
        jLabel13.setText("I F Width");

        sv_dspComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_dspComboBox.setToolTipText("DSP Noise Reduction (❃)");

        jLabel14.setLabelFor(sv_dspComboBox);
        jLabel14.setText("DSP Level");

        sv_ifShiftComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_ifShiftComboBox.setToolTipText("IF Shift (❃)");

        jLabel15.setLabelFor(sv_ifShiftComboBox);
        jLabel15.setText("IF Shift");

        sv_preampComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_preampComboBox.setToolTipText("Preamp setting (❃)");

        jLabel16.setLabelFor(sv_preampComboBox);
        jLabel16.setText("Preamp");

        sv_attenuatorComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_attenuatorComboBox.setToolTipText("Input attenuator (❃)");

        jLabel17.setLabelFor(sv_attenuatorComboBox);
        jLabel17.setText("Attenuator");

        javax.swing.GroupLayout ifControlsPanelLayout = new javax.swing.GroupLayout(ifControlsPanel);
        ifControlsPanel.setLayout(ifControlsPanelLayout);
        ifControlsPanelLayout.setHorizontalGroup(
            ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ifControlsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ifControlsPanelLayout.createSequentialGroup()
                        .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(ifControlsPanelLayout.createSequentialGroup()
                                .addComponent(sv_filtersComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(12, 12, 12)
                                .addComponent(jLabel13, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(ifControlsPanelLayout.createSequentialGroup()
                                .addComponent(sv_dspComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jLabel14))
                            .addGroup(ifControlsPanelLayout.createSequentialGroup()
                                .addComponent(sv_ifShiftComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jLabel15))
                            .addGroup(ifControlsPanelLayout.createSequentialGroup()
                                .addComponent(sv_preampComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(jLabel16)))
                        .addGap(129, 129, 129)
                        .addComponent(verticalListPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(ifControlsPanelLayout.createSequentialGroup()
                        .addComponent(sv_attenuatorComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel17))))
        );
        ifControlsPanelLayout.setVerticalGroup(
            ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ifControlsPanelLayout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(verticalListPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 138, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(ifControlsPanelLayout.createSequentialGroup()
                        .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(sv_filtersComboBox)
                            .addComponent(jLabel13))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(sv_dspComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel14))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(sv_ifShiftComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel15))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(sv_preampComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel16))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sv_attenuatorComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel17))
                .addContainerGap())
        );

        ifControlsPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jLabel13, sv_filtersComboBox});

        operationDetailsTabbedPane.addTab("I F Controls", ifControlsPanel);
        ifControlsPanel.getAccessibleContext().setAccessibleName("I F Controls");
        ifControlsPanel.getAccessibleContext().setAccessibleDescription("Various dsp and receiver controls.");

        operationDetailsTabbedPane.addTab("D-STAR", dstarPanel);
        dstarPanel.getAccessibleContext().setAccessibleName("Noise Reduction");
        dstarPanel.getAccessibleContext().setAccessibleDescription("R X Controls to reduce interference");

        operationDetailsTabbedPane.addTab("Keyer", keyerPanel);
        keyerPanel.getAccessibleContext().setAccessibleName("Keyer panel");
        keyerPanel.getAccessibleContext().setAccessibleDescription("Choose CW key parameters.");

        operationDetailsTabbedPane.addTab("RTTY", rttyPanel);
        rttyPanel.getAccessibleContext().setAccessibleName("RTTY");
        rttyPanel.getAccessibleContext().setAccessibleDescription("Radio Teletype settings and decode window.");

        recieverGroupBox.setBackground(new java.awt.Color(204, 204, 204));
        recieverGroupBox.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        recieverGroupBox.setToolTipText("Rx Controls");
        recieverGroupBox.setOpaque(true);
        recieverGroupBox.setVisible(true);

        sv_squelchSlider.setMajorTickSpacing(10);
        sv_squelchSlider.setMinorTickSpacing(5);
        sv_squelchSlider.setPaintTicks(true);
        sv_squelchSlider.setToolTipText("Squelch (❃)\n");
        sv_squelchSlider.setValue(0);
        sv_squelchSlider.setMaximumSize(new java.awt.Dimension(32, 38));
        sv_squelchSlider.setPreferredSize(new java.awt.Dimension(20, 38));

        sv_rfGainSlider.setMajorTickSpacing(10);
        sv_rfGainSlider.setMinorTickSpacing(5);
        sv_rfGainSlider.setPaintTicks(true);

        sv_ctcssComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "123.0", "Item 2", "Item 3", "Item 4" }));
        sv_ctcssComboBox.setToolTipText("CTCSS tone squelch frequencies (❃)");
        sv_ctcssComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_ctcssComboBoxActionHandler(evt);
            }
        });

        sv_synthSquelchCheckBox.setText("JRX Squelch ON");
        sv_synthSquelchCheckBox.setToolTipText("Use JRX squelch scheme");
        sv_synthSquelchCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_synthSquelchCheckBoxActionPerformed(evt);
            }
        });

        rfGainLabel.setLabelFor(sv_rfGainSlider);
        rfGainLabel.setText("RF Gain");

        squelchLabel.setText("Squelch");

        sv_agcComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "AGC", "Item 2", "Item 3", "Item 4" }));
        sv_agcComboBox.setToolTipText("AGC setting (❃)");
        sv_agcComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_agcComboBoxActionPerformed(evt);
            }
        });

        agcLabel.setLabelFor(sv_agcComboBox);
        agcLabel.setText("AGC");

        sv_ctcssCheckBox.setText("TSQL ON");
        sv_ctcssCheckBox.setToolTipText("Tone squelch control");

        sv_modesComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "LSB", "Item 2", "Item 3", "Item 4" }));
        sv_modesComboBox.setToolTipText("Operating modes (❃)");
        sv_modesComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_modesComboBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout recieverGroupBoxLayout = new javax.swing.GroupLayout(recieverGroupBox.getContentPane());
        recieverGroupBox.getContentPane().setLayout(recieverGroupBoxLayout);
        recieverGroupBoxLayout.setHorizontalGroup(
            recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recieverGroupBoxLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(rfGainLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(squelchLabel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(recieverGroupBoxLayout.createSequentialGroup()
                        .addComponent(sv_rfGainSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 207, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(sv_modesComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(54, 54, 54)
                        .addComponent(sv_ctcssComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(recieverGroupBoxLayout.createSequentialGroup()
                        .addComponent(sv_squelchSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 169, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_synthSquelchCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(sv_agcComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGroup(recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(recieverGroupBoxLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(sv_ctcssCheckBox)
                        .addContainerGap())
                    .addGroup(recieverGroupBoxLayout.createSequentialGroup()
                        .addGap(18, 18, 18)
                        .addComponent(agcLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );

        recieverGroupBoxLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {sv_rfGainSlider, sv_squelchSlider});

        recieverGroupBoxLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {sv_agcComboBox, sv_ctcssComboBox, sv_modesComboBox});

        recieverGroupBoxLayout.setVerticalGroup(
            recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recieverGroupBoxLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(sv_rfGainSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(rfGainLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(sv_ctcssComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(sv_ctcssCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(sv_modesComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(squelchLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sv_squelchSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(sv_agcComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(agcLabel)
                        .addComponent(sv_synthSquelchCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        recieverGroupBoxLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {agcLabel, sv_agcComboBox});

        recieverGroupBoxLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {sv_rfGainSlider, sv_squelchSlider});

        sv_volumeSlider.setMajorTickSpacing(10);
        sv_volumeSlider.setMinorTickSpacing(5);
        sv_volumeSlider.setPaintTicks(true);
        sv_volumeSlider.setToolTipText("Audio Gain (❃)");

        afGainLabel.setText("AF Gain");

        javax.swing.GroupLayout operateTransceiverPanelLayout = new javax.swing.GroupLayout(operateTransceiverPanel);
        operateTransceiverPanel.setLayout(operateTransceiverPanelLayout);
        operateTransceiverPanelLayout.setHorizontalGroup(
            operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(operationDetailsTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 677, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(afGainLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_volumeSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 225, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(recieverGroupBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        operateTransceiverPanelLayout.setVerticalGroup(
            operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(sv_volumeSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(afGainLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(recieverGroupBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(operationDetailsTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 400, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        recieverGroupBox.getAccessibleContext().setAccessibleName("Receiver Group");
        recieverGroupBox.getAccessibleContext().setAccessibleDescription("Receiver Controls Group");

        overallTabbedPane.addTab("Operate Transceiver", operateTransceiverPanel);
        operateTransceiverPanel.getAccessibleContext().setAccessibleName("Tranceive Operations Tab");
        operateTransceiverPanel.getAccessibleContext().setAccessibleDescription("Vital transceiver controls");

        scanPanel.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                scanPanelFocusGained(evt);
            }
        });

        memoryButtonsPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        memoryButtonsPanel.setMinimumSize(new java.awt.Dimension(40, 40));
        memoryButtonsPanel.setLayout(new java.awt.GridLayout(1, 0));
        memoryScrollPane.setViewportView(memoryButtonsPanel);

        buttonPanel2.setOpaque(false);
        buttonPanel2.setLayout(new java.awt.GridBagLayout());

        copyMemButton.setText("COPY to Clipboard");
        copyMemButton.setToolTipText("Copy JRX memory buttons to clipboard");
        copyMemButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                copyMemButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel2.add(copyMemButton, gridBagConstraints);

        pasteMemButton.setText("PASTE to Clipboard");
        pasteMemButton.setToolTipText("Paste JRX memory buttons from clipboard");
        pasteMemButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pasteMemButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel2.add(pasteMemButton, gridBagConstraints);

        eraseMemButton.setText("ERASE ALL MEMORY");
        eraseMemButton.setToolTipText("Erase all JRX memory buttons");
        eraseMemButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                eraseMemButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel2.add(eraseMemButton, gridBagConstraints);

        javax.swing.GroupLayout memoryStoragePanelLayout = new javax.swing.GroupLayout(memoryStoragePanel);
        memoryStoragePanel.setLayout(memoryStoragePanelLayout);
        memoryStoragePanelLayout.setHorizontalGroup(
            memoryStoragePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(memoryStoragePanelLayout.createSequentialGroup()
                .addGap(90, 90, 90)
                .addComponent(buttonPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(72, Short.MAX_VALUE))
            .addGroup(memoryStoragePanelLayout.createSequentialGroup()
                .addComponent(memoryScrollPane)
                .addContainerGap())
        );
        memoryStoragePanelLayout.setVerticalGroup(
            memoryStoragePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(memoryStoragePanelLayout.createSequentialGroup()
                .addComponent(memoryScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 275, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(buttonPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        channelsTabbedPane.addTab("Memory ", null, memoryStoragePanel, "Memory Storage ");
        memoryStoragePanel.getAccessibleContext().setAccessibleName("Memory Storage Tab");
        memoryStoragePanel.getAccessibleContext().setAccessibleDescription("Settings are stored on the host computer.");

        memoryPanel.setPreferredSize(new java.awt.Dimension(648, 330));

        javax.swing.GroupLayout favoriteSWLChannelsLayout = new javax.swing.GroupLayout(favoriteSWLChannels);
        favoriteSWLChannels.setLayout(favoriteSWLChannelsLayout);
        favoriteSWLChannelsLayout.setHorizontalGroup(
            favoriteSWLChannelsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, favoriteSWLChannelsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(memoryPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        favoriteSWLChannelsLayout.setVerticalGroup(
            favoriteSWLChannelsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(memoryPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 316, Short.MAX_VALUE)
        );

        memoryPanel.getAccessibleContext().setAccessibleName("Favorite Channels Table");
        memoryPanel.getAccessibleContext().setAccessibleDescription("Select a row to set V F O.");

        channelsTabbedPane.addTab("Favorite SWL Channels", null, favoriteSWLChannels, "A list of popular SWL frequencies");
        favoriteSWLChannels.getAccessibleContext().setAccessibleName("Favorite SWL Channels");
        favoriteSWLChannels.getAccessibleContext().setAccessibleDescription("List selection can be used with scanner");

        sv_scanStepComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_scanStepComboBox.setToolTipText("Scan frequency step size (❃)");

        jLabel3.setLabelFor(sv_scanStepComboBox);
        jLabel3.setText("Scan Step");

        sv_scanSpeedComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_scanSpeedComboBox.setToolTipText("Scan delay (❃)");

        jLabel4.setLabelFor(sv_scanSpeedComboBox);
        jLabel4.setText("Scan Speed");

        sv_dwellTimeComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_dwellTimeComboBox.setToolTipText("Pause dwell time (❃)");

        jLabel5.setLabelFor(sv_dwellTimeComboBox);
        jLabel5.setText("Dwell Time");

        scanIconLabel.setText("x");

        scanDownButton.setText("Start Scan Down");
        scanDownButton.setToolTipText("Scan down");
        scanDownButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scanDownButtonMouseClicked(evt);
            }
        });

        scanStopButton.setText("Stop Scan");
        scanStopButton.setToolTipText("Halt scan");
        scanStopButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scanStopButtonMouseClicked(evt);
            }
        });
        scanStopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                scanStopButtonActionPerformed(evt);
            }
        });

        scanUpButton.setText("Start Scan Up");
        scanUpButton.setToolTipText("Scan up");
        scanUpButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scanUpButtonMouseClicked(evt);
            }
        });

        sv_squelchCheckBox.setSelected(true);
        sv_squelchCheckBox.setText("Pause ON Squelch Open");
        sv_squelchCheckBox.setToolTipText("Pause on squelch active");

        javax.swing.GroupLayout scannerPanelLayout = new javax.swing.GroupLayout(scannerPanel);
        scannerPanel.setLayout(scannerPanelLayout);
        scannerPanelLayout.setHorizontalGroup(
            scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(scannerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(scannerPanelLayout.createSequentialGroup()
                        .addComponent(scanIconLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(scanDownButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(scanStopButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(scanUpButton)
                        .addGap(18, 18, 18)
                        .addComponent(sv_squelchCheckBox))
                    .addGroup(scannerPanelLayout.createSequentialGroup()
                        .addComponent(sv_scanStepComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel3)
                        .addGap(18, 18, 18)
                        .addComponent(sv_scanSpeedComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(sv_dwellTimeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel5)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        scannerPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {sv_dwellTimeComboBox, sv_scanSpeedComboBox, sv_scanStepComboBox});

        scannerPanelLayout.setVerticalGroup(
            scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(scannerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(sv_scanStepComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(sv_dwellTimeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel4)
                        .addComponent(jLabel5)
                        .addComponent(sv_scanSpeedComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(10, 10, 10)
                .addGroup(scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(scanIconLabel)
                    .addComponent(scanDownButton)
                    .addComponent(scanStopButton)
                    .addComponent(scanUpButton)
                    .addComponent(sv_squelchCheckBox))
                .addContainerGap())
        );

        scannerPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jLabel4, sv_dwellTimeComboBox, sv_scanSpeedComboBox, sv_scanStepComboBox});

        sv_scanSpeedComboBox.getAccessibleContext().setAccessibleName("Scan delay before restart scan");
        sv_scanSpeedComboBox.getAccessibleContext().setAccessibleDescription("Scan delay when squelch opens");
        sv_dwellTimeComboBox.getAccessibleContext().setAccessibleName("Dwell time in seconds");
        scanDownButton.getAccessibleContext().setAccessibleName("START SCAN DOWNWARDS");
        scanUpButton.getAccessibleContext().setAccessibleName("START SCAN UPWARDS");

        javax.swing.GroupLayout scanPanelLayout = new javax.swing.GroupLayout(scanPanel);
        scanPanel.setLayout(scanPanelLayout);
        scanPanelLayout.setHorizontalGroup(
            scanPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(scanPanelLayout.createSequentialGroup()
                .addComponent(channelsTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 681, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 6, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, scanPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scannerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        scanPanelLayout.setVerticalGroup(
            scanPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(scanPanelLayout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addComponent(scannerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(channelsTabbedPane))
        );

        overallTabbedPane.addTab("Scan", null, scanPanel, "Scan Functions");
        scanPanel.getAccessibleContext().setAccessibleName("Scan ");
        scanPanel.getAccessibleContext().setAccessibleDescription("Software driven channel or incremental receive scanner");

        scopePanel.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout scopeDisplayPanelLayout = new javax.swing.GroupLayout(scopeDisplayPanel);
        scopeDisplayPanel.setLayout(scopeDisplayPanelLayout);
        scopeDisplayPanelLayout.setHorizontalGroup(
            scopeDisplayPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 644, Short.MAX_VALUE)
        );
        scopeDisplayPanelLayout.setVerticalGroup(
            scopeDisplayPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 349, Short.MAX_VALUE)
        );

        scopePanel.add(scopeDisplayPanel, java.awt.BorderLayout.CENTER);
        scopeDisplayPanel.getAccessibleContext().setAccessibleName("Scope Panel");
        scopeDisplayPanel.getAccessibleContext().setAccessibleDescription("Visual band oscilloscope sweep");

        scopeControlPanel.setBackground(new java.awt.Color(0, 0, 0));
        scopeControlPanel.setLayout(new java.awt.GridBagLayout());

        scopeControlLeftPanel.setLayout(new java.awt.GridBagLayout());

        sv_scopeStepComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_scopeStepComboBox.setToolTipText("Sweep frequency step size (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        scopeControlLeftPanel.add(sv_scopeStepComboBox, gridBagConstraints);

        sv_scopeSpeedComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_scopeSpeedComboBox.setToolTipText("Sweep delay (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        scopeControlLeftPanel.add(sv_scopeSpeedComboBox, gridBagConstraints);

        scopeStartStopButton.setText("Start");
        scopeStartStopButton.setToolTipText("Start or stop the sweep");
        scopeStartStopButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scopeStartStopButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        scopeControlLeftPanel.add(scopeStartStopButton, gridBagConstraints);

        sv_scopeDotsCheckBox.setText("Dots");
        sv_scopeDotsCheckBox.setToolTipText("Add dots at each sample point");
        sv_scopeDotsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_scopeDotsCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        scopeControlLeftPanel.add(sv_scopeDotsCheckBox, gridBagConstraints);

        scopeScaleButton.setText("Rescale");
        scopeScaleButton.setToolTipText("During or after a sweep, optimally scale the vertical axis");
        scopeScaleButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scopeScaleButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        scopeControlLeftPanel.add(scopeScaleButton, gridBagConstraints);

        scopeDefaultsButton.setText("Defaults");
        scopeDefaultsButton.setToolTipText("Set scaling defaults");
        scopeDefaultsButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scopeDefaultsButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        scopeControlLeftPanel.add(scopeDefaultsButton, gridBagConstraints);

        copyButton.setText("Copy");
        copyButton.setToolTipText("Copy sweep dataset to system clipboard");
        copyButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                copyButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        scopeControlLeftPanel.add(copyButton, gridBagConstraints);

        scanHelpButton.setText("Help");
        scanHelpButton.setToolTipText("Visit the JRX Home Page");
        scanHelpButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scanHelpButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        scopeControlLeftPanel.add(scanHelpButton, gridBagConstraints);

        jLabel1.setText("Sweep scope controls");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        scopeControlLeftPanel.add(jLabel1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 1, 1, 1);
        scopeControlPanel.add(scopeControlLeftPanel, gridBagConstraints);

        scopePanel.add(scopeControlPanel, java.awt.BorderLayout.SOUTH);

        jrxScopePanel.add(scopePanel);

        overallTabbedPane.addTab("Scope", null, jrxScopePanel, "Oscilloscope Display of band sweep");
        jrxScopePanel.getAccessibleContext().setAccessibleName("Band scope ");
        jrxScopePanel.getAccessibleContext().setAccessibleDescription("Visual sweep display of selected band.");

        helpButton.setText("HELP");
        helpButton.setToolTipText("Visit the JRX Home Page");
        helpButton.setActionCommand("Help");
        helpButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                helpButtonMouseClicked(evt);
            }
        });

        jrxRadioButtonGroup.add(sv_jrxToRadioButton);
        sv_jrxToRadioButton.setSelected(true);
        sv_jrxToRadioButton.setText("JRX->Radio");
        sv_jrxToRadioButton.setToolTipText("At startup, JRX sets the radio's controls");

        jrxRadioButtonGroup.add(sv_radioToJrxButton);
        sv_radioToJrxButton.setText("Radio->JRX");
        sv_radioToJrxButton.setToolTipText("At startup, the radio sets JRX's controls");

        sv_syncCheckBox.setText("Sync App with radio");
        sv_syncCheckBox.setToolTipText("<html>Dynamically synchronize JRX<br/>controls with radio controls");

        sv_timerIntervalComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_timerIntervalComboBox.setToolTipText("Control/event update timer interval (❃)");

        jLabel2.setLabelFor(sv_timerIntervalComboBox);
        jLabel2.setText("Screen update Interval");

        sv_volumeExitCheckBox.setSelected(true);
        sv_volumeExitCheckBox.setText("Volume down on exit");
        sv_volumeExitCheckBox.setToolTipText("<html>Turn down the radio volume<br/>when JRX exits");

        tuneComsButton.setText("Comms Timing");
        tuneComsButton.setToolTipText("Configure Hamlib communications");
        tuneComsButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tuneComsButtonMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout firstSettingsPanelLayout = new javax.swing.GroupLayout(firstSettingsPanel);
        firstSettingsPanel.setLayout(firstSettingsPanelLayout);
        firstSettingsPanelLayout.setHorizontalGroup(
            firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(firstSettingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(firstSettingsPanelLayout.createSequentialGroup()
                        .addComponent(sv_radioToJrxButton, javax.swing.GroupLayout.PREFERRED_SIZE, 291, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(firstSettingsPanelLayout.createSequentialGroup()
                                .addGap(168, 168, 168)
                                .addComponent(sv_volumeExitCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 351, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(firstSettingsPanelLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(helpButton))))
                    .addGroup(firstSettingsPanelLayout.createSequentialGroup()
                        .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(firstSettingsPanelLayout.createSequentialGroup()
                                .addGap(6, 6, 6)
                                .addComponent(sv_timerIntervalComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 146, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(sv_jrxToRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 291, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tuneComsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 152, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(sv_syncCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 176, javax.swing.GroupLayout.PREFERRED_SIZE)))))
        );
        firstSettingsPanelLayout.setVerticalGroup(
            firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(firstSettingsPanelLayout.createSequentialGroup()
                .addGap(2, 2, 2)
                .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(sv_timerIntervalComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2)
                    .addComponent(sv_syncCheckBox)
                    .addComponent(sv_volumeExitCheckBox))
                .addGap(1, 1, 1)
                .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sv_jrxToRadioButton)
                    .addComponent(tuneComsButton))
                .addGap(3, 3, 3)
                .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sv_radioToJrxButton)
                    .addComponent(helpButton))
                .addContainerGap(349, Short.MAX_VALUE))
        );

        firstSettingsPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jLabel2, sv_timerIntervalComboBox});

        sv_syncCheckBox.getAccessibleContext().setAccessibleDescription("Dynamically synchronize App controls with radio controls");

        javax.swing.GroupLayout appSettingsPanelLayout = new javax.swing.GroupLayout(appSettingsPanel);
        appSettingsPanel.setLayout(appSettingsPanelLayout);
        appSettingsPanelLayout.setHorizontalGroup(
            appSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(appSettingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(firstSettingsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 675, Short.MAX_VALUE)
                .addContainerGap())
        );
        appSettingsPanelLayout.setVerticalGroup(
            appSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(appSettingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(firstSettingsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        overallTabbedPane.addTab("App Settings", null, appSettingsPanel, "Application Settings");
        appSettingsPanel.getAccessibleContext().setAccessibleName("Application Settings");
        appSettingsPanel.getAccessibleContext().setAccessibleDescription("Application Settings");

        sv_radioNamesComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 14)); // NOI18N
        sv_radioNamesComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "IC-7100", "Item 2", "Item 3", "Item 4" }));
        sv_radioNamesComboBox.setToolTipText("Available radio manufacturers and models");
        sv_radioNamesComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_radioNamesComboBoxActionPerformed(evt);
            }
        });

        muteCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                muteCheckBoxActionPerformed(evt);
            }
        });

        ledPanel.setOpaque(false);
        ledPanel.setLayout(new java.awt.GridBagLayout());

        speedIconLabel.setText("Speed Icon");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        ledPanel.add(speedIconLabel, gridBagConstraints);

        comErrorIconLabel.setText("Error Icon");
        comErrorIconLabel.setToolTipText("Red indicates communications error");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        ledPanel.add(comErrorIconLabel, gridBagConstraints);

        dcdIconLabel.setText("DCD Icon");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        ledPanel.add(dcdIconLabel, gridBagConstraints);

        radioNamesLabel.setLabelFor(sv_radioNamesComboBox);
        radioNamesLabel.setText("Radio Name");

        signalProgressBar.setMaximum(20);
        signalProgressBar.setMinimum(-50);
        signalProgressBar.setValue(-50);
        signalProgressBar.setStringPainted(true);

        sv_interfacesComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "/dev/tty.SLABusbToUart2", "Item 2", "Item 3", "Item 4" }));
        sv_interfacesComboBox.setToolTipText("Available communication interfaces");
        sv_interfacesComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_interfacesComboBoxActionPerformed(evt);
            }
        });

        vfoTabbedPane.setToolTipText("Visual tool tip for VFO tabbed pane");
        vfoTabbedPane.setAlignmentY(0.0F);

        rxVfoPanel.setBackground(new java.awt.Color(0, 0, 0));
        rxVfoPanel.setToolTipText(" VFO A visual tool tip");

        jInternalFrame1.setBorder(null);
        jInternalFrame1.setVisible(true);

        javax.swing.GroupLayout jInternalFrame1Layout = new javax.swing.GroupLayout(jInternalFrame1.getContentPane());
        jInternalFrame1.getContentPane().setLayout(jInternalFrame1Layout);
        jInternalFrame1Layout.setHorizontalGroup(
            jInternalFrame1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 676, Short.MAX_VALUE)
        );
        jInternalFrame1Layout.setVerticalGroup(
            jInternalFrame1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 126, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout rxVfoPanelLayout = new javax.swing.GroupLayout(rxVfoPanel);
        rxVfoPanel.setLayout(rxVfoPanelLayout);
        rxVfoPanelLayout.setHorizontalGroup(
            rxVfoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(rxVfoPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jInternalFrame1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        rxVfoPanelLayout.setVerticalGroup(
            rxVfoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(rxVfoPanelLayout.createSequentialGroup()
                .addComponent(jInternalFrame1)
                .addContainerGap())
        );

        vfoTabbedPane.addTab("VFO  A", null, rxVfoPanel, "VFO A control and display.  Use arrow keys to change values and traverse digits.");
        rxVfoPanel.getAccessibleContext().setAccessibleName("VFO A ");
        rxVfoPanel.getAccessibleContext().setAccessibleDescription("VFO A audio description");

        javax.swing.GroupLayout txVfoPanelLayout = new javax.swing.GroupLayout(txVfoPanel);
        txVfoPanel.setLayout(txVfoPanelLayout);
        txVfoPanelLayout.setHorizontalGroup(
            txVfoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 695, Short.MAX_VALUE)
        );
        txVfoPanelLayout.setVerticalGroup(
            txVfoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        vfoTabbedPane.addTab("VFO B ", null, txVfoPanel, "VFO B audio tool tip");
        txVfoPanel.getAccessibleContext().setAccessibleName("V F O B display ");
        txVfoPanel.getAccessibleContext().setAccessibleDescription("Use arrow keys to change value or traverse digits.");

        javax.swing.GroupLayout radioPanelLayout = new javax.swing.GroupLayout(radioPanel);
        radioPanel.setLayout(radioPanelLayout);
        radioPanelLayout.setHorizontalGroup(
            radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(radioPanelLayout.createSequentialGroup()
                .addComponent(signalProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 703, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(radioPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(vfoTabbedPane, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(radioPanelLayout.createSequentialGroup()
                        .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(overallTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 708, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(radioPanelLayout.createSequentialGroup()
                                .addComponent(ledPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(sv_radioNamesComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(radioNamesLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(sv_interfacesComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(muteCheckBox)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        radioPanelLayout.setVerticalGroup(
            radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(radioPanelLayout.createSequentialGroup()
                .addComponent(signalProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(radioPanelLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(ledPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(radioPanelLayout.createSequentialGroup()
                        .addGap(3, 3, 3)
                        .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(sv_radioNamesComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(radioNamesLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(sv_interfacesComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(muteCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(overallTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 498, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(vfoTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 178, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        radioPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {ledPanel, radioNamesLabel});

        muteCheckBox.getAccessibleContext().setAccessibleName("Mute audio");
        muteCheckBox.getAccessibleContext().setAccessibleDescription("mute audio");
        vfoTabbedPane.getAccessibleContext().setAccessibleName("VFO tabbed pane");
        vfoTabbedPane.getAccessibleContext().setAccessibleDescription("VFO tabbed pane audio description");

        jMenu3.setText("VFO Operations");
        jMenuBar1.add(jMenu3);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(radioPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(radioPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        closeApp();
    }//GEN-LAST:event_formWindowClosing

    private void scanHelpButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scanHelpButtonMouseClicked
        launchHelp("http://arachnoid.com/JRX/index.html#Spectrum_Analysis");
    }//GEN-LAST:event_scanHelpButtonMouseClicked

    private void copyButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_copyButtonMouseClicked
        getScopePanel().saveData();
    }//GEN-LAST:event_copyButtonMouseClicked

    private void scopeDefaultsButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scopeDefaultsButtonMouseClicked
        // TODO add your handling code here:
        getScopePanel().setup();
    }//GEN-LAST:event_scopeDefaultsButtonMouseClicked

    private void scopeScaleButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scopeScaleButtonMouseClicked
        getScopePanel().autoscale();
    }//GEN-LAST:event_scopeScaleButtonMouseClicked

    private void sv_scopeDotsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_scopeDotsCheckBoxActionPerformed
        repaint();
    }//GEN-LAST:event_sv_scopeDotsCheckBoxActionPerformed

    private void scopeStartStopButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scopeStartStopButtonMouseClicked
        getScopePanel().startSweep();
    }//GEN-LAST:event_scopeStartStopButtonMouseClicked

    private void scanUpButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scanUpButtonMouseClicked
        scanStateMachine.startScan(1);
    }//GEN-LAST:event_scanUpButtonMouseClicked

    private void scanStopButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scanStopButtonMouseClicked
        scanStateMachine.stopScan(true);
    }//GEN-LAST:event_scanStopButtonMouseClicked

    private void scanDownButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scanDownButtonMouseClicked
        scanStateMachine.startScan(-1);
    }//GEN-LAST:event_scanDownButtonMouseClicked

    private void sv_anfCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_anfCheckBoxActionPerformed
        enableDSP();
    }//GEN-LAST:event_sv_anfCheckBoxActionPerformed

    private void sv_apfCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_apfCheckBoxActionPerformed
        enableDSP();
    }//GEN-LAST:event_sv_apfCheckBoxActionPerformed

    private void sv_rawSigCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_rawSigCheckBoxActionPerformed
        testRawSignalMode();
    }//GEN-LAST:event_sv_rawSigCheckBoxActionPerformed

    private void tuneComsButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tuneComsButtonMouseClicked
       showConfigDialog();
    }//GEN-LAST:event_tuneComsButtonMouseClicked

    private void helpButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_helpButtonMouseClicked
        launchHelp("http://arachnoid.com/JRX");
    }//GEN-LAST:event_helpButtonMouseClicked

    private void eraseMemButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_eraseMemButtonMouseClicked
        memoryCollection.dispatch(evt);
    }//GEN-LAST:event_eraseMemButtonMouseClicked

    private void pasteMemButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pasteMemButtonMouseClicked
        memoryCollection.dispatch(evt);
    }//GEN-LAST:event_pasteMemButtonMouseClicked

    private void copyMemButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_copyMemButtonMouseClicked
        memoryCollection.dispatch(evt);
    }//GEN-LAST:event_copyMemButtonMouseClicked

    private void sv_synthSquelchCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_synthSquelchCheckBoxActionPerformed
        squelchScheme.setSquelchScheme();
    }//GEN-LAST:event_sv_synthSquelchCheckBoxActionPerformed

    private void sv_interfacesComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_interfacesComboBoxActionPerformed
        if (!inhibit) initialize(); 
        String selectionStr = (String)sv_interfacesComboBox.getSelectedItem();
        sv_interfacesComboBox.getAccessibleContext().setAccessibleDescription("Selection is "+selectionStr);
    }//GEN-LAST:event_sv_interfacesComboBoxActionPerformed

    private void sv_radioNamesComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_radioNamesComboBoxActionPerformed
        if (!inhibit) initialize();
        String selectionStr = (String)sv_radioNamesComboBox.getSelectedItem();
        sv_radioNamesComboBox.getAccessibleContext().setAccessibleDescription("Selection is "+selectionStr);
    }//GEN-LAST:event_sv_radioNamesComboBoxActionPerformed

    private void sv_modesComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_modesComboBoxActionPerformed
        String itemStr = (String)sv_modesComboBox.getSelectedItem();
        if (itemStr == null) return;
        if (itemStr.matches("-- n/a.*") ) return;
        sv_modesComboBox.getAccessibleContext().setAccessibleDescription("Selection is "+itemStr);
    }//GEN-LAST:event_sv_modesComboBoxActionPerformed

    private void sv_agcComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_agcComboBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_sv_agcComboBoxActionPerformed

    private void sv_compressionCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_compressionCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_sv_compressionCheckBoxActionPerformed

    private void scanStopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scanStopButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_scanStopButtonActionPerformed

    private void muteCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_muteCheckBoxActionPerformed
        if (muteCheckBox.isSelected()) ((AfGainSlider)sv_volumeSlider).mute();
        else    ((AfGainSlider)sv_volumeSlider).unmute();
    }//GEN-LAST:event_muteCheckBoxActionPerformed

    private void sv_enableVoxCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_enableVoxCheckBoxActionPerformed
        System.out.println("Vox enable check box is not functional yet.");
    }//GEN-LAST:event_sv_enableVoxCheckBoxActionPerformed

    private void sv_ctcssComboBoxActionHandler(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_ctcssComboBoxActionHandler
        String itemStr = (String)sv_ctcssComboBox.getSelectedItem();
        if (itemStr == null) return;
        if (itemStr.matches("-- n/a.*") ) return;
        sv_ctcssComboBox.getAccessibleContext().setAccessibleDescription("Selection is "+itemStr);
    }//GEN-LAST:event_sv_ctcssComboBoxActionHandler

    private void scanPanelFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_scanPanelFocusGained
        scanPanel.getAccessibleContext().setAccessibleDescription("Scan Panel tab selected.");
    }//GEN-LAST:event_scanPanelFocusGained

    private void overallTabbedPaneStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_overallTabbedPaneStateChanged
        
        int index = overallTabbedPane.getSelectedIndex();
        Component ponent = overallTabbedPane.getComponentAt(index);
        String title = overallTabbedPane.getTitleAt(index);
        System.out.println("Tabbed pane state change event :"+title+" selected.");
        overallTabbedPane.getAccessibleContext().setAccessibleName(title);
    }//GEN-LAST:event_overallTabbedPaneStateChanged

    /**
     * @param args the command line arguments
     */
    public static void main(final String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : 
                    javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | 
                 IllegalAccessException | 
                 javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(JRX_TX.class
                    .getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        //</editor-fold>
        //</editor-fold>
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new JRX_TX(args).setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel afGainLabel;
    private javax.swing.JLabel agcLabel;
    private javax.swing.JPanel appSettingsPanel;
    private javax.swing.JPanel buttonPanel2;
    protected javax.swing.JTabbedPane channelsTabbedPane;
    protected javax.swing.JLabel comErrorIconLabel;
    private javax.swing.JButton copyButton;
    private javax.swing.JButton copyMemButton;
    protected javax.swing.JLabel dcdIconLabel;
    private javax.swing.JPanel dstarPanel;
    private javax.swing.JButton eraseMemButton;
    private javax.swing.JPanel favoriteSWLChannels;
    private javax.swing.JPanel firstSettingsPanel;
    private javax.swing.JButton helpButton;
    private javax.swing.JPanel ifControlsPanel;
    private javax.swing.JInternalFrame jInternalFrame1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JMenu jMenu3;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JRadioButtonMenuItem jRadioButtonMenuItem1;
    private javax.swing.ButtonGroup jrxRadioButtonGroup;
    private javax.swing.JPanel jrxScopePanel;
    private javax.swing.JPanel keyerPanel;
    private javax.swing.JPanel ledPanel;
    protected javax.swing.JPanel memoryButtonsPanel;
    protected javax.swing.JPanel memoryPanel;
    private javax.swing.JScrollPane memoryScrollPane;
    private javax.swing.JPanel memoryStoragePanel;
    private javax.swing.JLabel micGainLabel;
    private javax.swing.JCheckBox muteCheckBox;
    private javax.swing.JPanel operateTransceiverPanel;
    private javax.swing.JTabbedPane operationDetailsTabbedPane;
    private javax.swing.JTabbedPane overallTabbedPane;
    private javax.swing.JButton pasteMemButton;
    private javax.swing.JLabel radioNamesLabel;
    private javax.swing.JPanel radioPanel;
    protected javax.swing.JInternalFrame recieverGroupBox;
    private javax.swing.JLabel rfGainLabel;
    private javax.swing.JPanel rttyPanel;
    private javax.swing.JPanel rxVfoPanel;
    private javax.swing.JButton scanDownButton;
    private javax.swing.JButton scanHelpButton;
    protected javax.swing.JLabel scanIconLabel;
    private javax.swing.JPanel scanPanel;
    private javax.swing.JButton scanStopButton;
    private javax.swing.JButton scanUpButton;
    private javax.swing.JPanel scannerPanel;
    private javax.swing.JPanel scopeControlLeftPanel;
    private javax.swing.JPanel scopeControlPanel;
    private javax.swing.JButton scopeDefaultsButton;
    private javax.swing.JPanel scopeDisplayPanel;
    private javax.swing.JPanel scopePanel;
    private javax.swing.JButton scopeScaleButton;
    protected javax.swing.JButton scopeStartStopButton;
    private javax.swing.JProgressBar signalProgressBar;
    private javax.swing.JLabel speedIconLabel;
    private javax.swing.ButtonGroup splitFreqGroup;
    private javax.swing.JLabel squelchLabel;
    protected javax.swing.JComboBox sv_agcComboBox;
    protected javax.swing.JCheckBox sv_anfCheckBox;
    protected javax.swing.JComboBox sv_antennaComboBox;
    protected javax.swing.JCheckBox sv_apfCheckBox;
    protected javax.swing.JComboBox sv_attenuatorComboBox;
    protected javax.swing.JCheckBox sv_blankerCheckBox;
    protected javax.swing.JCheckBox sv_compressionCheckBox;
    protected javax.swing.JSlider sv_compressionSlider;
    protected javax.swing.JCheckBox sv_ctcssCheckBox;
    protected javax.swing.JComboBox sv_ctcssComboBox;
    protected javax.swing.JCheckBox sv_dspCheckBox;
    protected javax.swing.JComboBox sv_dspComboBox;
    protected javax.swing.JComboBox<String> sv_dwellTimeComboBox;
    protected javax.swing.JCheckBox sv_enableVoxCheckBox;
    protected javax.swing.JComboBox<String> sv_filtersComboBox;
    protected javax.swing.JComboBox sv_ifShiftComboBox;
    protected javax.swing.JComboBox<String> sv_interfacesComboBox;
    protected javax.swing.JRadioButton sv_jrxToRadioButton;
    protected javax.swing.JSlider sv_micGainSlider;
    protected javax.swing.JComboBox<String> sv_modesComboBox;
    protected javax.swing.JComboBox sv_preampComboBox;
    protected javax.swing.JComboBox<String> sv_radioNamesComboBox;
    protected javax.swing.JRadioButton sv_radioToJrxButton;
    protected javax.swing.JCheckBox sv_rawSigCheckBox;
    protected javax.swing.JSlider sv_rfGainSlider;
    protected javax.swing.JSlider sv_rfPowerSlider;
    protected javax.swing.JComboBox<String> sv_scanSpeedComboBox;
    protected javax.swing.JComboBox<String> sv_scanStepComboBox;
    protected javax.swing.JCheckBox sv_scopeDotsCheckBox;
    protected javax.swing.JComboBox<String> sv_scopeSpeedComboBox;
    protected javax.swing.JComboBox<String> sv_scopeStepComboBox;
    protected javax.swing.JCheckBox sv_squelchCheckBox;
    protected javax.swing.JSlider sv_squelchSlider;
    protected javax.swing.JCheckBox sv_syncCheckBox;
    public javax.swing.JCheckBox sv_synthSquelchCheckBox;
    protected javax.swing.JComboBox<String> sv_timerIntervalComboBox;
    protected javax.swing.JCheckBox sv_txCtcssCheckBox;
    protected javax.swing.JCheckBox sv_volumeExitCheckBox;
    protected javax.swing.JSlider sv_volumeSlider;
    protected javax.swing.JSlider sv_voxLevelSlider;
    private javax.swing.JPanel transmitterPanel;
    private javax.swing.JButton tuneComsButton;
    private javax.swing.JPanel txVfoPanel;
    private javax.swing.JPanel verticalListPanel;
    private javax.swing.JTabbedPane vfoTabbedPane;
    // End of variables declaration//GEN-END:variables

   @Override
    public void actionPerformed(ActionEvent e) {
        String actionString = e.getActionCommand();
        if (actionString == "Copy VFO A to VFO B") {
            vfoState.copyAtoB();
                 
            if (!vfoState.vfoA_IsSelected()) {
                long freqA = vfoState.getVfoAFrequency();
                vfoDisplay.frequencyToDigits(freqA);
            }
            JOptionPane.showMessageDialog(this,
                    "VFO A copied to VFO B",
                    "VFO A copied to VFO B",  // VoiceOver reads only this line.
                    JOptionPane.PLAIN_MESSAGE);
        } else if (actionString == "Swap VFO A with VFO B") {
            vfoState.swapAwithB();
                 
            if (vfoState.vfoA_IsSelected()) {
                long freqA = vfoState.getVfoAFrequency();
                vfoDisplay.frequencyToDigits(freqA);
            } else {
                long freqB = vfoState.getVfoBFrequency();
                vfoDisplay.frequencyToDigits(freqB);                
            }
            JOptionPane.showMessageDialog(this,
                    "VFO A swapped with VFO B",
                    "VFO A swapped with VFO B",  // VoiceOver reads only this line.
                    JOptionPane.PLAIN_MESSAGE);            
        }
    } 
    @Override
    public void itemStateChanged(ItemEvent e) {
        if (noVfoDialog) return;        
        
        Object itemObj = e.getItem();
        JMenuItem item = (JMenuItem) itemObj;
        String itemText = item.getText();
        //System.out.println("item.name :"+itemText);
        if (itemText.equals(VFO_SELECT_A_TEXT)) {
            if (item.isSelected()) {
                vfoState.setVfoASelected();
                prefs.put(LAST_VFO, VFO_SELECT_A_TEXT);
                long freq = vfoState.getSelectedVfoFrequency();
                vfoDisplay.frequencyToDigits(freq);
                // If voiceOver enabled, need this dialog to announce vfo change.
                JOptionPane.showMessageDialog(this,
                    "VFO A Selected", // VoiceOver does not read the text in dialog.
                    "VFO A Selected", // VoiceOver reads only this line, the title.
                    JOptionPane.PLAIN_MESSAGE);
            }           
        } else if (itemText.equals(VFO_SELECT_B_TEXT)) {
            if (item.isSelected()) {
                vfoState.setVfoBSelected();
                prefs.put(LAST_VFO, VFO_SELECT_B_TEXT);
                long freq = vfoState.getSelectedVfoFrequency();
                vfoDisplay.frequencyToDigits(freq);
                // If voiceOver enabled, need this dialog to announce vfo change.
                JOptionPane.showMessageDialog(this,
                    "VFO B Selected",
                    "VFO B Selected",  // VoiceOver reads only this line.
                    JOptionPane.PLAIN_MESSAGE);
            }
        } else if (itemText.equals(VFO_SIMPLEX_TEXT)) {
            if (item.isSelected()) {
                vfoState.setVfoStateSimplex();
                //prefs.put(LAST_VFO_OP, VFO_SIMPLEX_TEXT);
                // If voiceOver enabled, need this dialog to announce vfo change.
                JOptionPane.showMessageDialog(this,
                    "Simplex Selected",
                    "Simplex Selected",  // VoiceOver reads only this line.
                    JOptionPane.PLAIN_MESSAGE);
            }
        } else if (itemText.equals(VFO_DUPLEX_PLUS_TEXT)) {
            if (item.isSelected()) {
                vfoState.setVfoOpStateDupPlus();
                //prefs.put(LAST_VFO_OP, VFO_DUPLEX_PLUS_TEXT);
                // If voiceOver enabled, need this dialog to announce vfo change.
                JOptionPane.showMessageDialog(this,
                    "Duplex Plus Selected",
                    "Duplex Plus Selected",  // VoiceOver reads only this line.
                    JOptionPane.PLAIN_MESSAGE);
            }
        } else if (itemText.equals(VFO_DUPLEX_MINUS_TEXT)) {
            if (item.isSelected()) {
                vfoState.setVfoOpStateDupMinus();
                //prefs.put(LAST_VFO_OP, VFO_DUPLEX_MINUS_TEXT);
                // If voiceOver enabled, need this dialog to announce vfo change.
                JOptionPane.showMessageDialog(this,
                    "Duplex Minus Selected",
                    "Duplex Minus Selected",  // VoiceOver reads only this line.
                    JOptionPane.PLAIN_MESSAGE);
            }
        } else {
            System.out.println("Unknown menu item handled in itemStateChanged()");
        }
    }
}
