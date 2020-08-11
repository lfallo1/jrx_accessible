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
import components.AgcListButton;
import components.AttenuatorCheckBox;
import components.CtcssListButton;
import components.IfFilterListButton;
import components.ModesListButton;
import components.RWListButton;
import components.RadioNamesListButton;
import components.InterfacesListButton;
import components.StepFrequencyListButton;
import components.StepPeriodListButton;
import components.DwellTimeListButton;
import components.PreampCheckBox;
import components.TimerIntervalListButton;
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

    final String appVersion = "5.1.0";
    final String appName;
    final String programName;
    public final String LINE_SEP;
    String userDir;
    String userPath;
    
    
    public ParseComLine comArgs = null;
    ImageIcon redLed, greenLed, blueLed, yellowLed;
    public ScanStateMachine scanStateMachine;
    ArrayList<String> interfaceNames = null;
    ChannelChart chart;
    public SquelchScheme squelchScheme;
    public RigComms rigComms; // Singleton
   
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

    int hamlibPort = 4532;
    String hamlibHost = "127.0.0.1";
    Socket hamlibSocket = null;
    InputStream hamlib_is = null;
    OutputStream hamlib_os = null;
    public String hamlibExecPath = "rigctld";
    public String radioData = null;
    Process hamlibDaemon = null;
    ConfigManager config = null;
    JFrame sv_frame = null;
    int squelchLow = -100;
    int squelchHigh = 100;
    public boolean isWindows;
    public boolean isMacOs;
    public boolean inhibit;  // application state 
    public boolean noVfoDialog;
    Font digitsFont;
    Font baseFont;
    final String FILE_SEP = System.getProperty("file.separator");
    String buttonFileDirectory;
    int signalStrengthErrorCount = 0;
   
    int defWidth = 890;
    int defHeight = 770;
    
    public MemoryCollection memoryCollection;
    public boolean slowRadio = false;
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
    Preferences prefs;

    //dependencies
    FileHelpers fileHelpers;

    /**
     * Creates new form JRX
     */
    public JRX_TX(String[] args) {
        // Call to initComponents happens after these calls.
        fileHelpers = new FileHelpers();
        rigComms = new RigComms();
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
        LINE_SEP = System.getProperty("line.separator");

        userDir = System.getProperty("user.home");
        userPath = userDir + FILE_SEP + "." + appName;
        
        initComponents();       
        digitsFont = new Font(Font.MONOSPACED, Font.PLAIN, 30);
        baseFont = new Font(Font.MONOSPACED, Font.PLAIN, getFont().getSize());
        setFont(baseFont);                
        // Must create/initialize vfoDisplay before scan functions.
        vfoFrequencyA.setText("VFO A  0000.000000");
        vfoFrequencyB.setText("VFO B  0000.000000");
        vfoDisplay = (VfoDisplayControl)vfoDisplayControl;
        vfoState = setUpVfoComponents(vfoDisplay);
        scanStateMachine = new ScanStateMachine(this);       
        scanDude = new ScanController(this);
        ((RadioNamesListButton)sv_radioNamesListButton).initialize();
        ((InterfacesListButton)sv_interfacesListButton).initialize();
        recieverGroupBox.getAccessibleContext().
                setAccessibleDescription("Receiver Controls");
        //setResizable(true);
        // default app size
        setSize(defWidth, defHeight);
        //setMaximumSize(new Dimension(defWidth,defHeight));
        createControlList();
        squelchScheme = new SquelchScheme(this);
        ((AgcListButton)sv_agcListButton).initialize();
        setupControls();
        setDefaultComboContent(); 
        config = new ConfigManager(this);
        if (!comArgs.reset) {
            config.read();
        }
        ((CtcssListButton)sv_ctcssListButton).initialize();
        ((IfFilterListButton)sv_ifFilterListButton).initialize();
        ((ModesListButton)sv_modesListButton).initialize();
        addComponentListener((VfoDisplayControl)vfoDisplayControl);
        
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


    public VfoStateInterface setUpVfoComponents(VfoDisplayControl vfoControl) {
        // Create an Prefernces object for access to this user's preferences.
        prefs = Preferences.userNodeForPackage(this.getClass());
        // Use the Mac OSX menuBar at the top of the screen.
        //System.setProperty("apple.laf.useScreenMenuBar", "true");
        addMenuBar();      
        // Add an exclusive interface to the Vfo selector so that only one thread
        // at a time gains access.
        vfoState = new VfoStateInterface(this, 
                menuItemA, menuItemB, vfoFrequencyA, vfoFrequencyB );
 
      
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
        vfoControl.setupPanes();                      
        noVfoDialog = false;
        
        
        
        // Cause the ones digit ftf to get the focus when the JFrame gets focus.                              
        JFormattedTextField textField;
        Vector<Component> order = vfoControl.getTraversalOrder();
        textField = (JFormattedTextField) order.get(0);
         this.addWindowFocusListener(new WindowAdapter() {
            public void windowGainedFocus(WindowEvent e) {
                textField.requestFocusInWindow();
            }
        });
        vfoControl.setFocusable(true);       
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
                Integer index = null;
                scanStateMachine.stopScan(false);
                int row = lsm.getMinSelectionIndex();
                String mode = chart.getValue(row, 2);
                double freq = Double.parseDouble(chart.getValue(row, 3));
                vfoState.writeFrequencyToRadioSelectedVfo((long)(freq*1e6 + 0.5));
                vfoDisplay.frequencyToDigits((long) (freq * 1e6 + 0.5));
                RWListButton modeListButton = (RWListButton) sv_modesListButton;
                if (freq < 137.0) {
                    // ID-51 only allows AM in this range.
                    int code = ((RWListButton)sv_radioNamesListButton).getSelectedIndex();
                    if (3084 == code) {
                        mode = "AM";
                        index = 0;
                    }
                } else {                    
                    mode = mode.toUpperCase();
                    index = modeListButton.displayMap.get(mode);
                }
                if (index != null) ((RWListButton)sv_modesListButton).setSelectedIndex(index);
                modeListButton.writeValue(true);
            }
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }
/////////////////////////////////////////////////////////////////////////////////
    
    /**
     * Determine the state of the comms Led and set it appropriately.
     * On detecting an error in reply text, comError is set to 2 by the radio
     * comms method; then the comError variable is checked
     * by this method and decremented on each check to determine the state of
     * the comms Led.  So you need two good comms in a row to set the led green.
     * 
     */
    public void setComErrorIcon() {
        if (comError > 0) {
            if ((comError -= 1) > 0) {
                comErrorIconLabel.setIcon(this.redLed);
                rigComms.setOffline(this);
            }
        } else {
            comErrorIconLabel.setIcon(this.greenLed);
            rigComms.setOnline();
        }
    }

    /**
     * Method is called upon changing the radio type or changing the device used
     * for rig communication or startup.  The interface list is subject to change
     * based on what kind of interface has been plugged in.
     */
    public void initialize() {
        if (!inhibit) {
            pout("Begin initialize(), not inhibit.");
            oldRadioFrequency = -1;
            dcdIconLabel.setText("");
            dcdIconLabel.setIcon(greenLed);
            String dcdLedString = "DCD LED indicator is green";
            dcdIconLabel.getAccessibleContext().setAccessibleDescription(dcdLedString);
            // Must reset to defaults again to accommodate change in rig and 
            // possibly the interface device.
            ((InterfacesListButton)sv_interfacesListButton).initInterfaceList();
            setDefaultComboContent();  
            setupHamlibDaemon();
            setComboBoxScales();                          
            vfoDisplay.setUpFocusManager();  
            vfoDisplay.makeVisible(vfoState); 
            if (sv_jrxToRadioButton.isSelected()) {
                // Force rig to app control settings.  NOT RECOMMENDED.
                vfoState.setVfoStateSimplex();
                vfoState.setRxVfo(VfoStateInterface.vfoChoice.VFOA);
                vfoState.writeFrequencyToRadioSelectedVfo(AERO_CLUB_FREQ);
                long freqB = vfoState.getVfoBFrequency();
                vfoState.setTextVfoB(freqB);
                writeRadioControls();
            } else {
                // Get the radio VFO settings and write them to application controls.
                VfoStateInterface.vfoChoice  choice = vfoState.getRxVfo();
                long valueA = vfoState.getVfoAFrequency();
                long valueB = vfoState.getVfoBFrequency();
                if (choice == VfoStateInterface.vfoChoice.VFOA) {
                    vfoDisplay.frequencyToDigits(valueA);
                } else {
                    vfoDisplay.frequencyToDigits(valueB);
                }
                VfoStateInterface.opState op = vfoState.getVfoOperatingState();
                if (op == VfoStateInterface.opState.SIMPLEX) {
                    menuItemSimplex.setSelected(true);
                } else if (op == VfoStateInterface.opState.DUPLEX) {
                    if (vfoState.isVfoDuplexPlus()) {
                        menuItemDuplexPlus.setSelected(true);
                    } else {
                        menuItemDuplexMinus.setSelected(true);
                    }                   
                } else if (op == VfoStateInterface.opState.SPLIT) {
                    menuItemSplit.setSelected(true);                   
                }
            }
            squelchScheme.setRadioSquelch();
            readRadioControls(true);  // Side effect : Reads frequency from radio
            ((TimerIntervalListButton)sv_timerIntervalListButton).startCyclicalTimer();
            measureSpeed(); // Writes frequency to radio.
            setComErrorIcon();            
            memoryScrollPane.getVerticalScrollBar().setUnitIncrement(8);
        }
    }

    public void readRadioControls(boolean all) {
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
    /**
     * Measure the time it takes to write the current frequency to the radio
     * vfo.  Unfortunately, on some radios, this turns the CTCSS TONE off. So
     * read the state of the TONE first and then set it afterwards appropriately.
     */
    private void measureSpeed() {
        long freq = vfoState.getSelectedVfoFrequency();
        // Read the current state of the TONE function.
        ((RWCheckBox)sv_txCtcssCheckBox).readValue();
        boolean wasOn = ((RWCheckBox)sv_txCtcssCheckBox).isSelected();
        long t = System.currentTimeMillis();
        oldRadioFrequency = -1;
        setRadioFrequency(freq);
        long dt = System.currentTimeMillis() - t;
        ((RWCheckBox)sv_txCtcssCheckBox).setSelected(wasOn);
        ((RWCheckBox)sv_txCtcssCheckBox).writeValue(true);
        // Use a diferent strategy for slow radios.
        slowRadio = dt > 75;
        speedIconLabel.setText("");
        speedIconLabel.setIcon(slowRadio ? redLed : greenLed);
        speedIconLabel.setToolTipText(slowRadio ? "Slow radio coms" : "Fast radio coms");
        pout("radio com ms delay: " + dt);

    }

    private void setDefaultComboContent() {
        ((RWListButton) sv_ifFilterListButton).placeholderData("BW ");
        ((RWListButton) sv_modesListButton).placeholderData("Mode ");
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
                sv_dspSlider,
                sv_dspCheckBox,               
                sv_ifFilterListButton,
                sv_modesListButton,
                sv_antennaComboBox,
                sv_attenuatorCheckBox,
                sv_agcListButton,
                sv_preampCheckBox,
                sv_ctcssListButton,
                sv_txCtcssCheckBox,
                sv_ctcssSquelchCheckBox,
                sv_blankerCheckBox,
                sv_apfCheckBox,
                sv_anfCheckBox,
                sv_micGainSlider,
                sv_rfPowerSlider,
                sv_voxLevelSlider,
                sv_enableVoxCheckBox,
                sv_compressionSlider,
                sv_compressionCheckBox,
                sv_fbkinCheckBox
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
            pout("have windows exec: " + hamlibExecPath);

        } else {
            hamlibExecPath = fileHelpers.findAbsolutePath(hamlibExecPath);
        }
        
        // yhigh intentionally = ylow to allow the rig to set the range
        ((RWComboBox)sv_ifShiftComboBox).setComboBoxContent("IF", -50, 50, 5, -50, 50, 0, 0, 0); 
        ((RWListButton)sv_agcListButton).setContent("AGC", 0, 10, 1, 0, 1, 0, 10, 1);
        ((RWComboBox)sv_antennaComboBox).setComboBoxContent("Ant", 0, 4, 1, 0, 1, 0, 1, 1);  
        ((InterfacesListButton)sv_interfacesListButton).initInterfaceList();
        ((RadioNamesListButton)sv_radioNamesListButton).getSupportedRadios();
        ((TimerIntervalListButton) sv_timerIntervalListButton).initTimeValues();
        memoryCollection.init();
        ((StepFrequencyListButton)sv_scanStepListButton).init();
        ((StepPeriodListButton)sv_stepPeriodListButton).init();
        ((DwellTimeListButton)sv_dwellTimeListButton).init();
        ((StepFrequencyListButton)sv_scopeStepListButton).init();
        ((StepPeriodListButton)sv_scopeSpeedListButton).init();
        
        scannerPanel.setBackground(new Color(240, 240, 220));
        scopeControlLeftPanel.setBackground(new Color(240, 240, 220));
        firstSettingsPanel.setBackground(new Color(200, 220, 240));
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
     * WE DO NOT CHECK THE DUMP CAPS TO SEE IF WE STILL HAVE THE CORRECT RADIO?
     * WHAT???? @TODO COZ MUST DO THIS !!!
     * 
     * @return true if radioData != null.
     */
    private boolean getRigCaps() {
        
        String com = "\\dump_caps";
        radioData = sendRadioCom(com, 3, false);
        System.out.println(" Reply to \\dump_caps "+ radioData);
        if (radioData == null) {
            // Unable to communicate with the radio.
            sv_radioNamesListButton.setEnabled(true);
            sv_interfacesListButton.setEnabled(true);
        }  
        vfoState.setVfoCommandChoices(radioData);
        vfoState.setVfoSplitCapability(radioData);
        ((MicGainSlider)sv_micGainSlider).enableIfCapable(radioData);
        ((RfPowerSlider)sv_rfPowerSlider).enableIfCapable(radioData);
        ((VoxLevelSlider)sv_voxLevelSlider).enableIfCapable(radioData);
        ((CtcssListButton)sv_ctcssListButton).enableIfCapable(radioData);
        ((IfFilterListButton)sv_ifFilterListButton).enableIfCapable(radioData);
        ((AgcListButton)sv_agcListButton).enableIfCapable(radioData);
        ((AttenuatorCheckBox)sv_attenuatorCheckBox).enableIfCapable(radioData);
        ((RWComboBox)sv_antennaComboBox).enableCap(radioData, "(?ism).*^Can set Ant:\\rigSpecs+Y$", false); 
        ((PreampCheckBox)sv_preampCheckBox).enableIfCapable(radioData);
        ((RWSlider)sv_volumeSlider).enableCap(radioData, "(?ism).*^Set level:.*?AF\\(", true); 
        ((RWSlider)sv_rfGainSlider).enableCap(radioData, "(?ism).*^Set level:.*?RF\\(", true);
        ((RWSlider)sv_squelchSlider).enableCap(radioData, "(?ism).*^Set level:.*?SQL\\(", true);
        ((RWComboBox)sv_ifShiftComboBox).enableCap(radioData, "(?ism).*^Set level:.*?IF\\(", true); 
        ((RWCheckBox)sv_blankerCheckBox).enableCap(radioData, "(?ism).*^Set functions:.*?\\sNB\\s", false);
        ((RWCheckBox)sv_anfCheckBox).enableCap(radioData, "(?ism).*^Set functions:.*?\\sANF\\s", false);
        ((RWCheckBox)sv_apfCheckBox).enableCap(radioData, "(?ism).*^Set functions:.*?\\sAPF\\s", false);
        ((DspSlider)sv_dspSlider).enableIfCapable(radioData);
        ((RWCheckBox)sv_enableVoxCheckBox).enableCap(radioData, "(?ism).*^Set functions:.*?\\sVOX\\s", false);
        ((RWCheckBox)sv_dspCheckBox).enableCap(radioData, "(?ism).*^Set level:.*?NR\\(", false);
        ((RWCheckBox)sv_compressionCheckBox).enableCap(radioData,"(?ism).*^Set functions:.*?\\sCOMP\\s", false);
        ((RWCheckBox)sv_txCtcssCheckBox).enableCap(radioData,"(?ism).*^Set functions:.*?\\sTONE\\s", false);
        ((RWCheckBox)sv_ctcssSquelchCheckBox).enableCap(radioData,"(?ism).*^Set functions:.*?\\sTSQL\\s", false); 
        ((RWCheckBox)sv_fbkinCheckBox).enableCap(radioData, "(?ism).*^Set functions:.*?\\sFBKIN\\s", false);
        ((RWSlider)sv_compressionSlider).enableCap(radioData, "(?ism).*^Set level:.*?COMP\\(", true);
        ((RWSlider)sv_cwSpeedSlider).enableCap(radioData, "(?ism).*^Set level:.*?KEYSPD\\(", true);
        ((RWSlider)sv_cwToneSlider).enableCap(radioData, "(?ism).*^Set level:.*?CWPITCH\\(", true);
        sv_tunerCheckBox.setEnabled(false);   // Not implemented yet.
        int radioCode = ((RadioNamesListButton)sv_radioNamesListButton).getSelectedRadioCode();
        ((SwrIndicator)swrIndicator).enableIfCapable(radioData);
        RigCapsCorrections.correct(this, radioCode);
        String s = sendRadioCom("\\get_dcd", 0, false);
        dcdCapable = (s != null && s.matches("\\d+"));
        squelchScheme.setSquelchScheme();
        return true;
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


    public boolean validSetup() {
        return (!inhibit);
    }

    
    /**
     * Run a command line command an retrieve the response.
     * 
     * Used to list the devices available on the OS.
     * 
     * Used to get a list of radios and their codes before the socket has been 
     * set up to communicate with rigctld.
     */ 
    public String runSysCommand(String[] array, boolean read) {
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
    

    private void setComboBoxScales() {
        if (hamlibExecPath == null) return;
        inhibit = true; // Do not cause a change event handler to call initialize().
        ((IfFilterListButton) sv_ifFilterListButton).setGenericScale("", "", true, true);
        ((ModesListButton) sv_modesListButton).setGenericScale(
                "Mode", "(?ism).*^Mode list:\\s*(.*?)\\s*$.*", false, false);
        inhibit = false;
    }

    protected boolean askUser(String prompt) {
        Beep.beep();
        int choice = JOptionPane.showConfirmDialog(this, prompt);
        return choice == JOptionPane.OK_OPTION;
    }

    public void tellUser(String prompt) {
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
            closeConnection();  // Hangs here on initialize the second time...
            String interfaceName = null;
            String rigName = null;
            int rigCode = -1;
            if (comArgs.rigName != null) {
                rigName = comArgs.rigName;
                ((RadioNamesListButton)sv_radioNamesListButton).
                                                    setSelectedItem(rigName);
                sv_radioNamesListButton.setEnabled(false);
                try {
                    rigCode = RadioNamesListButton.getRadioCode(rigName);
                } catch (Exception e) {
                    tellUser(String.format("Error: rig name \"%s\" not known.", rigName));
                    sv_radioNamesListButton.setEnabled(true);
                }
            }
            if (comArgs.rigCode >= 0 && rigCode == -1) {
                rigCode = comArgs.rigCode;
                rigName = RadioNamesListButton.
                                        getNameKeyForRadioCodeValue(rigCode);
                inhibit = true;
                ((RadioNamesListButton)sv_radioNamesListButton).setSelectedItem(rigName);
                // Disable comboBox because choice has already been made.
                sv_radioNamesListButton.setEnabled(false);
                inhibit = false;
            }
            if (comArgs.interfaceName != null) {
                interfaceName = comArgs.interfaceName;
                inhibit = true;
                ((RWListButton)sv_interfacesListButton).setSelectedItem(interfaceName);
                sv_interfacesListButton.setEnabled(false);
                inhibit = false;
            } else if (((RWListButton)sv_interfacesListButton).
                    getSelectedIndex() > 0) {
                interfaceName = ((RWListButton)sv_interfacesListButton).
                        getSelectedItem();
            }

            if (rigCode == -1 && ((RadioNamesListButton)sv_radioNamesListButton).
                    getSelectedIndex() > 0) {
                rigName = (String) ((RadioNamesListButton)sv_radioNamesListButton).
                        getSelectedItem();
                rigCode = RadioNamesListButton.getRadioCode(rigName);
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
                pout("setup daemon with: " + rigName + "," + rigCode + "," + interfaceName);
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
                            rigComms.setOnline();
                            pout("socket connected: " + connected);                            
                        } catch (Exception e) {                           
                            pout("fail connect " + e.getMessage());
                            waitMS(500);
                            if (n-- <= 0) {
                                tellUser("Error: Cannot connect to Hamlib Daemon process.");
                                rigComms.setOffline(this);
                            }
                        }
                    }
                    // Now get radio capability data and set up drop-down lists.
                    getRigCaps();
                    // Radio name has been chosen, read the memoryButtons for this radio.
                    memoryCollection.readMemoryButtons();
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
                hamlib_os.write((s + LINE_SEP).getBytes());
                hamlib_os.flush();                
                pout("sendradiocom   emit: [" + s + "]");
                result = readInputStream(hamlib_is);
                pout("sendradiocom result: [" + result.replaceAll("[\r\n]", " ") + "]");
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
        int writeHoldCount = vfoState.lock.getWriteHoldCount();
        int queueLength = vfoState.lock.getQueueLength();
        int readHoldCount = vfoState.lock.getReadHoldCount();
        try {            
            vfoState.lock.writeLock().lock();            
            if (validSetup() && hamlibDaemon != null && hamlibSocket != null && s != null) {
                if (hamlibSocket.isConnected()) {
                    try {
                        hamlib_os.write((s + LINE_SEP).getBytes());
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
        boolean plusLocks = ((countAfter - writeHoldCount) > 0);           
        if (vfoState.lock.isWriteLockedByCurrentThread() && plusLocks ) {
            pout("sendRadioCom() comms is writeLocked by current" +
                    " thread; count = "+countAfter);                           
        }
        return result;
    }
    
    /**
     * deprecated because JRX squelch is not implemented.
     * @param squelchOpen 
     */
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
            ((TimerIntervalListButton)sv_timerIntervalListButton).cancelTimer();
            if (this.sv_volumeExitCheckBox.isSelected()) {
                setVolumeDirect(0.0);
            }
            if (hamlibSocket != null) {
                hamlibSocket.close();
                waitMS(100);
                hamlibSocket = null;
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
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

    }


    public SweepScope getScopePanel() {
        return (SweepScope) scopeDisplayPanel;
    }
    /**
     * If rawSignalCheckBox is checked return true.
     * @return 
     */
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

   public boolean getSignalStrength() {
        if (signalStrengthErrorCount > 2) return false;
        try {
            // Do not perform operation for high debug levels.  Why?
            if (comArgs.debug < 1000) {
                String com = (testRawSignalMode()) ? "l RAWSTR" : "l STRENGTH";
                String result = sendRadioCom(com, 1, false);
                //p("signal strength result: [" + result + "]");
                if (result != null && result.length() > 0) {
                    if (result.matches("(?ims).*?^-?\\d+$.*")) {
                        result = result.replaceFirst("(?ims).*?^(-?\\d+)$.*", "$1");
                        signalStrength = Double.parseDouble(result);
                        //p("ss: [" + result + "]," + ss);
                        signalStrengthErrorCount = 0;
                    }
                } else {
                    // result is set to null on a comms error.  Three in a row, your out.
                    signalStrengthErrorCount++;
                    signalStrength = 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        return true;
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

    public void setSMeter() {
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
        ((TimerIntervalListButton)sv_timerIntervalListButton).cancelTimer();
        if (scanStateMachine.scanTimer != null) {
            scanStateMachine.scanTimer.cancel();
            scanStateMachine.scanTimer = null;
        }
        config.write();
        memoryCollection.writeMemoryButtons();
        closeConnection();
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
        if (comArgs.debug >= 0) 
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
        sv_ctcssListButton = new components.CtcssListButton(this);
        sv_tunerCheckBox = new javax.swing.JCheckBox();
        swrIndicator = new SwrIndicator(this);
        pttCheckBox = new RWCheckBox(this, "T", "");
        ;
        ifControlsPanel = new javax.swing.JPanel();
        verticalListPanel = new javax.swing.JPanel();
        sv_rawSigCheckBox = new RWCheckBox(this,null,null);
        sv_blankerCheckBox = new RWCheckBox(this,"U","NB");
        sv_apfCheckBox = new RWCheckBox(this,"U","APF");
        sv_anfCheckBox = new RWCheckBox(this,"U","ANF");
        sv_dspCheckBox = new RWCheckBox(this,"U","NR");
        jLabel13 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        sv_ifShiftComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","IF");
        jLabel15 = new javax.swing.JLabel();
        sv_ifFilterListButton = new components.IfFilterListButton(this);
        sv_attenuatorCheckBox = new AttenuatorCheckBox(this);
        sv_preampCheckBox = new PreampCheckBox(this);
        sv_dspSlider = new DspSlider(this);
        dstarPanel = new javax.swing.JPanel();
        keyerPanel = new javax.swing.JPanel();
        sv_cwSpeedSlider = new CwSpeedSlider(this);
        sv_cwToneSlider = new CwSideToneSlider(this);
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        sv_fbkinCheckBox = new RWCheckBox(this,"U","FBKIN");
        cwSendText = new javax.swing.JFormattedTextField();
        rttyPanel = new javax.swing.JPanel();
        recieverGroupBox = new GroupBox();
        sv_squelchSlider = new com.cozcompany.jrx.accessibility.RWSlider(this,"L","SQL",0);
        sv_rfGainSlider = new com.cozcompany.jrx.accessibility.RWSlider(this,"L","RF",50);
        sv_synthSquelchCheckBox = new RWCheckBox(this,null,null);
        rfGainLabel = new javax.swing.JLabel();
        squelchLabel = new javax.swing.JLabel();
        sv_ctcssSquelchCheckBox = new RWCheckBox(this,"U","TSQL");
        sv_modesListButton = new components.ModesListButton(this);
        sv_agcListButton = new AgcListButton(this);
        sv_volumeSlider = new com.cozcompany.jrx.accessibility.AfGainSlider(this);
        afGainLabel = new javax.swing.JLabel();
        muteCheckBox = new javax.swing.JCheckBox();
        scanPanel = new javax.swing.JPanel();
        scannerPanel = new javax.swing.JPanel();
        sv_scanStepListButton = new components.StepFrequencyListButton(this);
        sv_stepPeriodListButton = new StepPeriodListButton(this);
        sv_dwellTimeListButton = new DwellTimeListButton(this);
        scanIconLabel = new javax.swing.JLabel();
        scanStopButton = new javax.swing.JButton();
        scanUpButton = new javax.swing.JButton();
        sv_squelchCheckBox = new RWCheckBox(this,null,null);
        copyMemButton = new javax.swing.JButton();
        pasteMemButton = new javax.swing.JButton();
        eraseMemButton = new javax.swing.JButton();
        channelsTabbedPane = new javax.swing.JTabbedPane();
        memoryStoragePanel = new javax.swing.JPanel();
        memoryScrollPane = new javax.swing.JScrollPane();
        memoryButtonsPanel = new javax.swing.JPanel();
        memoryPanel = new javax.swing.JPanel();
        channelPanel = new javax.swing.JPanel();
        jrxScopePanel = new javax.swing.JPanel();
        scopePanel = new javax.swing.JPanel();
        scopeDisplayPanel = new SweepScope(this);
        scopeControlPanel = new javax.swing.JPanel();
        scopeControlLeftPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        sv_scopeStepListButton = new StepFrequencyListButton(this);
        scopeStartStopButton = new javax.swing.JButton();
        sv_scopeDotsCheckBox = new javax.swing.JCheckBox();
        scopeScaleButton = new javax.swing.JButton();
        scopeDefaultsButton = new javax.swing.JButton();
        copyButton = new javax.swing.JButton();
        sv_scopeSpeedListButton = new StepPeriodListButton(this);
        appSettingsPanel = new javax.swing.JPanel();
        firstSettingsPanel = new javax.swing.JPanel();
        helpButton = new javax.swing.JButton();
        sv_jrxToRadioButton = new javax.swing.JRadioButton();
        sv_radioToJrxButton = new javax.swing.JRadioButton();
        sv_syncCheckBox = new javax.swing.JCheckBox();
        jLabel2 = new javax.swing.JLabel();
        sv_volumeExitCheckBox = new javax.swing.JCheckBox();
        tuneComsButton = new javax.swing.JButton();
        sv_timerIntervalListButton = new TimerIntervalListButton(this);
        ledPanel = new javax.swing.JPanel();
        signalProgressBar = new javax.swing.JProgressBar();
        sv_radioNamesListButton = new RadioNamesListButton(this);
        sv_interfacesListButton = new InterfacesListButton(this);
        vfoDisplayControl = new VfoDisplayControl(this);
        vfoGroup = new GroupBox();
        vfoFrequencyB = new javax.swing.JTextField();
        speedIconLabel = new javax.swing.JLabel();
        dcdIconLabel = new javax.swing.JLabel();
        comErrorIconLabel = new javax.swing.JLabel();
        vfoFrequencyA = new javax.swing.JTextField();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu3 = new javax.swing.JMenu();

        jRadioButtonMenuItem1.setSelected(true);
        jRadioButtonMenuItem1.setText("jRadioButtonMenuItem1");

        jMenuItem1.setText("jMenuItem1");

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setPreferredSize(new java.awt.Dimension(868, 770));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        radioPanel.setMaximumSize(new java.awt.Dimension(868, 707));

        overallTabbedPane.setMaximumSize(new java.awt.Dimension(693, 384));
        overallTabbedPane.setMinimumSize(new java.awt.Dimension(100, 100));
        overallTabbedPane.setPreferredSize(new java.awt.Dimension(693, 384));
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

        sv_antennaComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "ANTENNA 1", "Item 2", "Item 3", "Item 4" }));
        sv_antennaComboBox.setToolTipText("Available antennas (❃)");

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
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel11)
                        .addComponent(sv_enableVoxCheckBox))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(sv_voxLevelSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(micGainLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(2, 2, 2))
                            .addComponent(sv_micGainSlider, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(jLabel10)
                                .addComponent(sv_compressionCheckBox))
                            .addComponent(sv_compressionSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jLabel10, sv_compressionCheckBox, sv_compressionSlider});

        jPanel1Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jLabel11, sv_enableVoxCheckBox});

        sv_txCtcssCheckBox.setText("Tx CTCSS TONE");

        sv_ctcssListButton.setText("CTCSS 107.2  ...");

        sv_tunerCheckBox.setText("TUNER");

        swrIndicator.setBackground(new java.awt.Color(255, 255, 255));
        swrIndicator.setFont(new java.awt.Font("Lucida Grande", 1, 14)); // NOI18N
        swrIndicator.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        swrIndicator.setText("SWR  unknown");
        swrIndicator.setOpaque(true);

        pttCheckBox.setText("PTT");
        pttCheckBox.setToolTipText("Push to Talk");
        pttCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                pttCheckBoxStateChanged(evt);
            }
        });

        javax.swing.GroupLayout transmitterPanelLayout = new javax.swing.GroupLayout(transmitterPanel);
        transmitterPanel.setLayout(transmitterPanelLayout);
        transmitterPanelLayout.setHorizontalGroup(
            transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(transmitterPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addComponent(sv_txCtcssCheckBox)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(sv_ctcssListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 172, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(sv_tunerCheckBox)
                            .addComponent(sv_antennaComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 161, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 194, Short.MAX_VALUE))))
            .addGroup(transmitterPanelLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(sv_rfPowerSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 274, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, 133, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(swrIndicator, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pttCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 222, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        transmitterPanelLayout.setVerticalGroup(
            transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(transmitterPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE, false)
                        .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(swrIndicator)
                        .addComponent(pttCheckBox))
                    .addComponent(sv_rfPowerSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(7, Short.MAX_VALUE))
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addGap(18, 18, 18)
                        .addComponent(sv_antennaComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_txCtcssCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_ctcssListButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_tunerCheckBox)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );

        operationDetailsTabbedPane.addTab("Transmitter", null, transmitterPanel, "Important xmit controls and details.");
        transmitterPanel.getAccessibleContext().setAccessibleName("Transmitter ");
        transmitterPanel.getAccessibleContext().setAccessibleDescription("Set output power and mic gain");

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

        jLabel13.setText("I F Width");

        jLabel14.setText("DSP Level");

        sv_ifShiftComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_ifShiftComboBox.setToolTipText("IF Shift (❃)");

        jLabel15.setLabelFor(sv_ifShiftComboBox);
        jLabel15.setText("IF Shift");

        sv_ifFilterListButton.setText("BW  Auto     ...");

        sv_attenuatorCheckBox.setText("Attenuator");

        sv_preampCheckBox.setText("PreAmp");

        sv_dspSlider.setMajorTickSpacing(10);
        sv_dspSlider.setPaintTicks(true);
        sv_dspSlider.setValue(10);

        javax.swing.GroupLayout ifControlsPanelLayout = new javax.swing.GroupLayout(ifControlsPanel);
        ifControlsPanel.setLayout(ifControlsPanelLayout);
        ifControlsPanelLayout.setHorizontalGroup(
            ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ifControlsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ifControlsPanelLayout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(sv_dspSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel14))
                    .addGroup(ifControlsPanelLayout.createSequentialGroup()
                        .addComponent(sv_preampCheckBox)
                        .addGap(18, 18, 18)
                        .addComponent(sv_attenuatorCheckBox))
                    .addGroup(ifControlsPanelLayout.createSequentialGroup()
                        .addComponent(sv_ifShiftComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel15))
                    .addGroup(ifControlsPanelLayout.createSequentialGroup()
                        .addComponent(sv_ifFilterListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 196, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel13, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 165, Short.MAX_VALUE)
                .addComponent(verticalListPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(245, 245, 245))
        );
        ifControlsPanelLayout.setVerticalGroup(
            ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ifControlsPanelLayout.createSequentialGroup()
                .addGap(4, 4, 4)
                .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(ifControlsPanelLayout.createSequentialGroup()
                        .addComponent(verticalListPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 138, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(93, 93, 93))
                    .addGroup(ifControlsPanelLayout.createSequentialGroup()
                        .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(sv_ifFilterListButton)
                            .addComponent(jLabel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(ifControlsPanelLayout.createSequentialGroup()
                                .addGap(6, 6, 6)
                                .addComponent(jLabel14))
                            .addComponent(sv_dspSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(14, 14, 14)
                        .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel15)
                            .addComponent(sv_ifShiftComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(ifControlsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(sv_preampCheckBox)
                            .addComponent(sv_attenuatorCheckBox))
                        .addGap(82, 82, 82))))
        );

        sv_dspSlider.getAccessibleContext().setAccessibleName("D S P level");
        sv_dspSlider.getAccessibleContext().setAccessibleDescription("Digital Signal Processing Level");

        operationDetailsTabbedPane.addTab("I F Controls", ifControlsPanel);
        ifControlsPanel.getAccessibleContext().setAccessibleName("I F Controls");
        ifControlsPanel.getAccessibleContext().setAccessibleDescription("Various dsp and receiver controls.");

        operationDetailsTabbedPane.addTab("D-STAR", dstarPanel);
        dstarPanel.getAccessibleContext().setAccessibleName("Noise Reduction");
        dstarPanel.getAccessibleContext().setAccessibleDescription("R X Controls to reduce interference");

        sv_cwSpeedSlider.setMajorTickSpacing(10);
        sv_cwSpeedSlider.setMaximum(40);
        sv_cwSpeedSlider.setMinimum(5);
        sv_cwSpeedSlider.setMinorTickSpacing(1);
        sv_cwSpeedSlider.setPaintLabels(true);
        sv_cwSpeedSlider.setPaintTicks(true);
        sv_cwSpeedSlider.setValue(15);

        sv_cwToneSlider.setMajorTickSpacing(300);
        sv_cwToneSlider.setMaximum(900);
        sv_cwToneSlider.setMinimum(300);
        sv_cwToneSlider.setMinorTickSpacing(100);
        sv_cwToneSlider.setPaintLabels(true);
        sv_cwToneSlider.setPaintTicks(true);
        sv_cwToneSlider.setValue(600);

        jLabel6.setText("CW Speed WPM");

        jLabel7.setText("CW Side-Tone \nFrequency ");

        jLabel3.setText("CW TEXT:  TO SEND END LINE WITH RETURN");

        sv_fbkinCheckBox.setText("Fast Break-In");
        sv_fbkinCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_fbkinActionHandler(evt);
            }
        });

        cwSendText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cwSendTextActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout keyerPanelLayout = new javax.swing.GroupLayout(keyerPanel);
        keyerPanel.setLayout(keyerPanelLayout);
        keyerPanelLayout.setHorizontalGroup(
            keyerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(keyerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(keyerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cwSendText)
                    .addGroup(keyerPanelLayout.createSequentialGroup()
                        .addComponent(sv_cwToneSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 167, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel7, javax.swing.GroupLayout.DEFAULT_SIZE, 607, Short.MAX_VALUE))
                    .addGroup(keyerPanelLayout.createSequentialGroup()
                        .addGroup(keyerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(keyerPanelLayout.createSequentialGroup()
                                .addComponent(sv_cwSpeedSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel6)
                                .addGap(48, 48, 48)
                                .addComponent(sv_fbkinCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 169, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(keyerPanelLayout.createSequentialGroup()
                                .addGap(63, 63, 63)
                                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 302, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        keyerPanelLayout.setVerticalGroup(
            keyerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(keyerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(keyerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(sv_cwSpeedSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(keyerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(sv_fbkinCheckBox)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(keyerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(sv_cwToneSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel7, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 24, Short.MAX_VALUE)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cwSendText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        cwSendText.getAccessibleContext().setAccessibleName("Text to send via CW");
        cwSendText.getAccessibleContext().setAccessibleDescription("Type text to send and hit enter.");

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

        sv_ctcssSquelchCheckBox.setText("TSQL ON");
        sv_ctcssSquelchCheckBox.setToolTipText("Tone squelch control");
        sv_ctcssSquelchCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_ctcssSquelchCheckBoxActionPerformed(evt);
            }
        });

        sv_modesListButton.setText("MODE  LSB      ...");
        sv_modesListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_modesListButtonActionPerformed(evt);
            }
        });

        sv_agcListButton.setText("AGC   AGC-F  ...");

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
                .addGroup(recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(recieverGroupBoxLayout.createSequentialGroup()
                        .addComponent(sv_rfGainSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 207, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(sv_modesListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 215, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(sv_ctcssSquelchCheckBox))
                    .addGroup(recieverGroupBoxLayout.createSequentialGroup()
                        .addComponent(sv_squelchSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 169, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_synthSquelchCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(sv_agcListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 259, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );

        recieverGroupBoxLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {sv_rfGainSlider, sv_squelchSlider});

        recieverGroupBoxLayout.setVerticalGroup(
            recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(recieverGroupBoxLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(sv_rfGainSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(rfGainLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(sv_modesListButton)
                        .addComponent(sv_ctcssSquelchCheckBox)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(sv_agcListButton)
                    .addGroup(recieverGroupBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(sv_synthSquelchCheckBox, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 28, Short.MAX_VALUE)
                        .addComponent(sv_squelchSlider, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                        .addComponent(squelchLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        sv_agcListButton.getAccessibleContext().setAccessibleDescription("AGC fast or slow settings");

        sv_volumeSlider.setMajorTickSpacing(10);
        sv_volumeSlider.setMinorTickSpacing(5);
        sv_volumeSlider.setPaintTicks(true);
        sv_volumeSlider.setToolTipText("Audio Gain (❃)");

        afGainLabel.setText("AF Gain");

        muteCheckBox.setText("MUTE");
        muteCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                muteCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout operateTransceiverPanelLayout = new javax.swing.GroupLayout(operateTransceiverPanel);
        operateTransceiverPanel.setLayout(operateTransceiverPanelLayout);
        operateTransceiverPanelLayout.setHorizontalGroup(
            operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                        .addGap(28, 28, 28)
                        .addComponent(afGainLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_volumeSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 225, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(muteCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 349, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(recieverGroupBox))
                    .addComponent(operationDetailsTabbedPane, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );
        operateTransceiverPanelLayout.setVerticalGroup(
            operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(afGainLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(sv_volumeSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(muteCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(recieverGroupBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(operationDetailsTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 240, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(141, 141, 141))
        );

        recieverGroupBox.getAccessibleContext().setAccessibleName("Receiver Group");
        recieverGroupBox.getAccessibleContext().setAccessibleDescription("Receiver Controls Group");
        muteCheckBox.getAccessibleContext().setAccessibleName("Mute audio");
        muteCheckBox.getAccessibleContext().setAccessibleDescription("mute audio");

        overallTabbedPane.addTab("Operate Transceiver", operateTransceiverPanel);
        operateTransceiverPanel.getAccessibleContext().setAccessibleName("Tranceive Operations Tab");
        operateTransceiverPanel.getAccessibleContext().setAccessibleDescription("Vital transceiver controls");

        scanPanel.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                scanPanelFocusGained(evt);
            }
        });

        scannerPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        sv_scanStepListButton.setText("SCAN STEP  1 Khz ...");

        sv_stepPeriodListButton.setText("STEP PERIOD  500 msec ...");

        sv_dwellTimeListButton.setText("DWELL TIME  5 Sec ...");

        scanIconLabel.setText("x");
        scanIconLabel.setToolTipText("Scan Led turns green when scanning.");

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

        scanUpButton.setText("Start Scan ");
        scanUpButton.setToolTipText("Scan up");
        scanUpButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scanUpButtonMouseClicked(evt);
            }
        });
        scanUpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startScanUpHandler(evt);
            }
        });

        sv_squelchCheckBox.setText("VoiceOver announce ON Squelch Open");
        sv_squelchCheckBox.setToolTipText("VoiceOver announce on squelch open");

        copyMemButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        copyMemButton.setText("COPY");
        copyMemButton.setToolTipText("Copy JRX memory buttons to clipboard");
        copyMemButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                copyMemButtonMouseClicked(evt);
            }
        });
        copyMemButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyMemButtonActionPerformed(evt);
            }
        });

        pasteMemButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        pasteMemButton.setText("PASTE");
        pasteMemButton.setToolTipText("Paste JRX memory buttons from clipboard");
        pasteMemButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pasteMemButtonMouseClicked(evt);
            }
        });

        eraseMemButton.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
        eraseMemButton.setText("ERASE");
        eraseMemButton.setToolTipText("Erase all JRX memory buttons");
        eraseMemButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                eraseMemButtonMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout scannerPanelLayout = new javax.swing.GroupLayout(scannerPanel);
        scannerPanel.setLayout(scannerPanelLayout);
        scannerPanelLayout.setHorizontalGroup(
            scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(scannerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(scannerPanelLayout.createSequentialGroup()
                        .addComponent(scanIconLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(scanStopButton, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(scanUpButton, javax.swing.GroupLayout.PREFERRED_SIZE, 127, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(sv_squelchCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 286, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(scannerPanelLayout.createSequentialGroup()
                        .addComponent(sv_scanStepListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 206, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_stepPeriodListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 228, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_dwellTimeListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 202, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(copyMemButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pasteMemButton, javax.swing.GroupLayout.PREFERRED_SIZE, 68, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(eraseMemButton, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        scannerPanelLayout.setVerticalGroup(
            scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(scannerPanelLayout.createSequentialGroup()
                .addGroup(scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sv_scanStepListButton)
                    .addComponent(sv_stepPeriodListButton)
                    .addComponent(sv_dwellTimeListButton)
                    .addComponent(copyMemButton)
                    .addComponent(pasteMemButton, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGroup(scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(eraseMemButton)
                    .addGroup(scannerPanelLayout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addGroup(scannerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(scanIconLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(scanStopButton, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(scanUpButton, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(sv_squelchCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        scanUpButton.getAccessibleContext().setAccessibleName("START SCAN UPWARDS");

        memoryButtonsPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        memoryButtonsPanel.setMaximumSize(new java.awt.Dimension(741, 600));
        memoryButtonsPanel.setMinimumSize(new java.awt.Dimension(40, 40));
        memoryButtonsPanel.setPreferredSize(new java.awt.Dimension(741, 600));
        memoryButtonsPanel.setLayout(new java.awt.GridLayout(1, 0));
        memoryScrollPane.setViewportView(memoryButtonsPanel);

        javax.swing.GroupLayout memoryStoragePanelLayout = new javax.swing.GroupLayout(memoryStoragePanel);
        memoryStoragePanel.setLayout(memoryStoragePanelLayout);
        memoryStoragePanelLayout.setHorizontalGroup(
            memoryStoragePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(memoryStoragePanelLayout.createSequentialGroup()
                .addComponent(memoryScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 808, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 2, Short.MAX_VALUE))
        );
        memoryStoragePanelLayout.setVerticalGroup(
            memoryStoragePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, memoryStoragePanelLayout.createSequentialGroup()
                .addGap(0, 1, Short.MAX_VALUE)
                .addComponent(memoryScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 218, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        channelsTabbedPane.addTab("Memory ", null, memoryStoragePanel, "Memory Storage ");
        memoryStoragePanel.getAccessibleContext().setAccessibleName("Memory Storage Tab");
        memoryStoragePanel.getAccessibleContext().setAccessibleDescription("Settings are stored on the host computer.");

        channelPanel.setLayout(new java.awt.CardLayout());

        javax.swing.GroupLayout memoryPanelLayout = new javax.swing.GroupLayout(memoryPanel);
        memoryPanel.setLayout(memoryPanelLayout);
        memoryPanelLayout.setHorizontalGroup(
            memoryPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(channelPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        memoryPanelLayout.setVerticalGroup(
            memoryPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(channelPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        channelsTabbedPane.addTab("Favorite SWL Channels", null, memoryPanel, "A list of popular SWL frequencies");
        memoryPanel.getAccessibleContext().setAccessibleName("Favorite SWL Channels");
        memoryPanel.getAccessibleContext().setAccessibleDescription("List selection can be used with scanner");

        javax.swing.GroupLayout scanPanelLayout = new javax.swing.GroupLayout(scanPanel);
        scanPanel.setLayout(scanPanelLayout);
        scanPanelLayout.setHorizontalGroup(
            scanPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(scanPanelLayout.createSequentialGroup()
                .addGroup(scanPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(channelsTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 831, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(scanPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(scannerPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        scanPanelLayout.setVerticalGroup(
            scanPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, scanPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(scannerPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 68, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(channelsTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 265, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(574, 574, 574))
        );

        overallTabbedPane.addTab("Scan", null, scanPanel, "Scan Functions");
        scanPanel.getAccessibleContext().setAccessibleName("Scan ");
        scanPanel.getAccessibleContext().setAccessibleDescription("Software driven channel or incremental receive scanner");

        scopePanel.setMinimumSize(new java.awt.Dimension(700, 80));
        scopePanel.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout scopeDisplayPanelLayout = new javax.swing.GroupLayout(scopeDisplayPanel);
        scopeDisplayPanel.setLayout(scopeDisplayPanelLayout);
        scopeDisplayPanelLayout.setHorizontalGroup(
            scopeDisplayPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 700, Short.MAX_VALUE)
        );
        scopeDisplayPanelLayout.setVerticalGroup(
            scopeDisplayPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 255, Short.MAX_VALUE)
        );

        scopePanel.add(scopeDisplayPanel, java.awt.BorderLayout.CENTER);
        scopeDisplayPanel.getAccessibleContext().setAccessibleName("Scope Panel");
        scopeDisplayPanel.getAccessibleContext().setAccessibleDescription("Visual band oscilloscope sweep");

        scopeControlPanel.setBackground(new java.awt.Color(0, 0, 0));
        scopeControlPanel.setMinimumSize(new java.awt.Dimension(600, 80));
        scopeControlPanel.setPreferredSize(new java.awt.Dimension(600, 80));
        scopeControlPanel.setLayout(new java.awt.GridBagLayout());

        scopeControlLeftPanel.setMinimumSize(new java.awt.Dimension(450, 78));
        scopeControlLeftPanel.setPreferredSize(new java.awt.Dimension(450, 78));

        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("Sweep scope controls");
        jLabel1.setMaximumSize(new java.awt.Dimension(600, 16));
        jLabel1.setMinimumSize(new java.awt.Dimension(600, 16));
        jLabel1.setPreferredSize(new java.awt.Dimension(600, 16));

        sv_scopeStepListButton.setText("Scope Step Frequency ");

        scopeStartStopButton.setFont(new java.awt.Font("Lucida Grande", 0, 14)); // NOI18N
        scopeStartStopButton.setText("Start");
        scopeStartStopButton.setToolTipText("Start or stop the sweep");
        scopeStartStopButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scopeStartStopButtonMouseClicked(evt);
            }
        });
        scopeStartStopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                scopeStartStopButtonActionPerformed(evt);
            }
        });

        sv_scopeDotsCheckBox.setText("Dots");
        sv_scopeDotsCheckBox.setToolTipText("Add dots at each sample point");
        sv_scopeDotsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_scopeDotsCheckBoxActionPerformed(evt);
            }
        });

        scopeScaleButton.setText("Rescale");
        scopeScaleButton.setToolTipText("During or after a sweep, optimally scale the vertical axis");
        scopeScaleButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scopeScaleButtonMouseClicked(evt);
            }
        });

        scopeDefaultsButton.setText("Defaults");
        scopeDefaultsButton.setToolTipText("Set scaling defaults");
        scopeDefaultsButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scopeDefaultsButtonMouseClicked(evt);
            }
        });

        copyButton.setText("Copy");
        copyButton.setToolTipText("Copy sweep dataset to system clipboard");
        copyButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                copyButtonMouseClicked(evt);
            }
        });

        sv_scopeSpeedListButton.setText("Scope Sweep Speed");

        javax.swing.GroupLayout scopeControlLeftPanelLayout = new javax.swing.GroupLayout(scopeControlLeftPanel);
        scopeControlLeftPanel.setLayout(scopeControlLeftPanelLayout);
        scopeControlLeftPanelLayout.setHorizontalGroup(
            scopeControlLeftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, scopeControlLeftPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(scopeControlLeftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, scopeControlLeftPanelLayout.createSequentialGroup()
                        .addComponent(scopeScaleButton, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(scopeDefaultsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 161, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 78, Short.MAX_VALUE)
                        .addComponent(sv_scopeDotsCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, scopeControlLeftPanelLayout.createSequentialGroup()
                        .addComponent(sv_scopeStepListButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_scopeSpeedListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 258, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(scopeControlLeftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(copyButton, javax.swing.GroupLayout.DEFAULT_SIZE, 140, Short.MAX_VALUE)
                    .addComponent(scopeStartStopButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        scopeControlLeftPanelLayout.setVerticalGroup(
            scopeControlLeftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(scopeControlLeftPanelLayout.createSequentialGroup()
                .addGap(2, 2, 2)
                .addGroup(scopeControlLeftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(scopeControlLeftPanelLayout.createSequentialGroup()
                        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addGroup(scopeControlLeftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(sv_scopeStepListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(sv_scopeSpeedListButton)))
                    .addGroup(scopeControlLeftPanelLayout.createSequentialGroup()
                        .addComponent(scopeStartStopButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(3, 3, 3)))
                .addGroup(scopeControlLeftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(scopeControlLeftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(copyButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, Short.MAX_VALUE)
                        .addComponent(sv_scopeDotsCheckBox))
                    .addComponent(scopeScaleButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(scopeDefaultsButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addContainerGap())
        );

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

        helpButton.setText("JRX HOME PAGE");
        helpButton.setToolTipText("Visit the JRX Home Page");
        helpButton.setActionCommand("Help");
        helpButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                helpButtonMouseClicked(evt);
            }
        });

        jrxRadioButtonGroup.add(sv_jrxToRadioButton);
        sv_jrxToRadioButton.setText("JRX->Radio");
        sv_jrxToRadioButton.setToolTipText("At startup, JRX sets the radio's controls");

        jrxRadioButtonGroup.add(sv_radioToJrxButton);
        sv_radioToJrxButton.setSelected(true);
        sv_radioToJrxButton.setText("Radio->JRX");
        sv_radioToJrxButton.setToolTipText("At startup, the radio sets JRX's controls");

        sv_syncCheckBox.setText("Sync App with radio");
        sv_syncCheckBox.setToolTipText("<html>Dynamically synchronize JRX<br/>controls with radio controls");

        jLabel2.setText("Screen update Interval");

        sv_volumeExitCheckBox.setText("Volume down on exit");
        sv_volumeExitCheckBox.setToolTipText("<html>Turn down the radio volume<br/>when JRX exits");

        tuneComsButton.setText("Comms Timing");
        tuneComsButton.setToolTipText("Configure Hamlib communications");
        tuneComsButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tuneComsButtonMouseClicked(evt);
            }
        });

        sv_timerIntervalListButton.setText("Interval  100 msec");

        javax.swing.GroupLayout firstSettingsPanelLayout = new javax.swing.GroupLayout(firstSettingsPanel);
        firstSettingsPanel.setLayout(firstSettingsPanelLayout);
        firstSettingsPanelLayout.setHorizontalGroup(
            firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(firstSettingsPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addGroup(firstSettingsPanelLayout.createSequentialGroup()
                        .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(sv_jrxToRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 291, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(sv_radioToJrxButton, javax.swing.GroupLayout.PREFERRED_SIZE, 291, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(79, 79, 79))
                    .addGroup(firstSettingsPanelLayout.createSequentialGroup()
                        .addComponent(sv_timerIntervalListButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
                .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(firstSettingsPanelLayout.createSequentialGroup()
                        .addComponent(sv_syncCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 207, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_volumeExitCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 179, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(helpButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(tuneComsButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 152, Short.MAX_VALUE)))
                .addGap(124, 124, 124))
        );
        firstSettingsPanelLayout.setVerticalGroup(
            firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(firstSettingsPanelLayout.createSequentialGroup()
                .addGap(2, 2, 2)
                .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel2)
                    .addComponent(sv_syncCheckBox)
                    .addComponent(sv_volumeExitCheckBox)
                    .addComponent(sv_timerIntervalListButton))
                .addGap(1, 1, 1)
                .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sv_jrxToRadioButton)
                    .addComponent(tuneComsButton))
                .addGap(3, 3, 3)
                .addGroup(firstSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sv_radioToJrxButton)
                    .addComponent(helpButton))
                .addContainerGap(237, Short.MAX_VALUE))
        );

        sv_syncCheckBox.getAccessibleContext().setAccessibleDescription("Dynamically synchronize App controls with radio controls");

        javax.swing.GroupLayout appSettingsPanelLayout = new javax.swing.GroupLayout(appSettingsPanel);
        appSettingsPanel.setLayout(appSettingsPanelLayout);
        appSettingsPanelLayout.setHorizontalGroup(
            appSettingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(appSettingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(firstSettingsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 816, Short.MAX_VALUE)
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

        ledPanel.setOpaque(false);
        ledPanel.setLayout(new java.awt.GridBagLayout());

        signalProgressBar.setMaximum(20);
        signalProgressBar.setMinimum(-50);
        signalProgressBar.setToolTipText("Software S-Meter");
        signalProgressBar.setValue(-50);
        signalProgressBar.setMaximumSize(new java.awt.Dimension(694, 14));
        signalProgressBar.setMinimumSize(new java.awt.Dimension(10, 14));
        signalProgressBar.setPreferredSize(new java.awt.Dimension(694, 14));
        signalProgressBar.setStringPainted(true);

        sv_radioNamesListButton.setFont(new java.awt.Font("Lucida Grande", 0, 14)); // NOI18N
        sv_radioNamesListButton.setText("RADIO ICOM IC-7100 ...");
        sv_radioNamesListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_radioNamesActionPerformed(evt);
            }
        });

        sv_interfacesListButton.setText("INTERFACE /dev/tty.usbSerial ...");
        sv_interfacesListButton.setActionCommand("INTERFACE");
        sv_interfacesListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_interfacesListButtonActionPerformed(evt);
            }
        });

        vfoDisplayControl.setBorder(null);
        vfoDisplayControl.setForeground(new java.awt.Color(153, 153, 255));
        vfoDisplayControl.setMaximumSize(new java.awt.Dimension(850, 130));
        vfoDisplayControl.setVisible(true);
        vfoDisplayControl.getContentPane().setLayout(new java.awt.FlowLayout());

        vfoGroup.setBorder(null);
        vfoGroup.setResizable(true);
        vfoGroup.setMaximumSize(new java.awt.Dimension(695, 30));
        vfoGroup.setMinimumSize(new java.awt.Dimension(150, 30));
        vfoGroup.setPreferredSize(new java.awt.Dimension(518, 30));
        vfoGroup.setVisible(true);

        vfoFrequencyB.setFont(new java.awt.Font("Lucida Grande", 0, 14)); // NOI18N
        vfoFrequencyB.setText("VFO B 1234123123");
        vfoFrequencyB.setMaximumSize(new java.awt.Dimension(147, 27));
        vfoFrequencyB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                vfoFrequencyBActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout vfoGroupLayout = new javax.swing.GroupLayout(vfoGroup.getContentPane());
        vfoGroup.getContentPane().setLayout(vfoGroupLayout);
        vfoGroupLayout.setHorizontalGroup(
            vfoGroupLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, vfoGroupLayout.createSequentialGroup()
                .addContainerGap(373, Short.MAX_VALUE)
                .addComponent(vfoFrequencyB, javax.swing.GroupLayout.PREFERRED_SIZE, 153, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        vfoGroupLayout.setVerticalGroup(
            vfoGroupLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, vfoGroupLayout.createSequentialGroup()
                .addGap(0, 3, Short.MAX_VALUE)
                .addComponent(vfoFrequencyB, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        speedIconLabel.setText("<html>Speed<br>Icon");

        dcdIconLabel.setText("<html>DCD<br>Icon");

        comErrorIconLabel.setText("<html>Error<br>Icon");
        comErrorIconLabel.setToolTipText("Red indicates communications error");

        vfoFrequencyA.setFont(new java.awt.Font("Lucida Grande", 0, 14)); // NOI18N
        vfoFrequencyA.setText("VFO A 1234123123");
        vfoFrequencyA.setMaximumSize(new java.awt.Dimension(147, 27));
        vfoFrequencyA.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                vfoFrequencyAActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout radioPanelLayout = new javax.swing.GroupLayout(radioPanel);
        radioPanel.setLayout(radioPanelLayout);
        radioPanelLayout.setHorizontalGroup(
            radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(radioPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, radioPanelLayout.createSequentialGroup()
                        .addComponent(vfoFrequencyA, javax.swing.GroupLayout.PREFERRED_SIZE, 161, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(vfoGroup, javax.swing.GroupLayout.PREFERRED_SIZE, 532, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(radioPanelLayout.createSequentialGroup()
                        .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(radioPanelLayout.createSequentialGroup()
                                .addComponent(ledPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(comErrorIconLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(dcdIconLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(speedIconLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(sv_radioNamesListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 243, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(sv_interfacesListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 377, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(signalProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(vfoDisplayControl, javax.swing.GroupLayout.PREFERRED_SIZE, 826, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
            .addGroup(radioPanelLayout.createSequentialGroup()
                .addComponent(overallTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 840, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        radioPanelLayout.setVerticalGroup(
            radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(radioPanelLayout.createSequentialGroup()
                .addComponent(signalProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(sv_radioNamesListButton)
                        .addComponent(sv_interfacesListButton))
                    .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(speedIconLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(dcdIconLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(comErrorIconLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addComponent(ledPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(overallTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 388, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(vfoDisplayControl, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(vfoGroup, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(vfoFrequencyA, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(122, Short.MAX_VALUE))
        );

        vfoDisplayControl.getAccessibleContext().setAccessibleName("V F O Display Control");
        vfoDisplayControl.getAccessibleContext().setAccessibleDescription("Use arrow keys to navigate");
        vfoGroup.getAccessibleContext().setAccessibleName("V F O Group shows V F O A and V F O B");
        vfoGroup.getAccessibleContext().setAccessibleDescription("Read only display");

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
            .addComponent(radioPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        closeApp();
    }//GEN-LAST:event_formWindowClosing

    private void copyButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_copyButtonMouseClicked
        getScopePanel().saveData();
    }//GEN-LAST:event_copyButtonMouseClicked

    private void scopeDefaultsButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scopeDefaultsButtonMouseClicked
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

    private void sv_compressionCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_compressionCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_sv_compressionCheckBoxActionPerformed

    private void scanStopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scanStopButtonActionPerformed
        scanStateMachine.stopScan(true);
    }//GEN-LAST:event_scanStopButtonActionPerformed

    private void muteCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_muteCheckBoxActionPerformed
        if (muteCheckBox.isSelected()) ((AfGainSlider)sv_volumeSlider).mute();
        else    ((AfGainSlider)sv_volumeSlider).unmute();
    }//GEN-LAST:event_muteCheckBoxActionPerformed

    private void sv_enableVoxCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_enableVoxCheckBoxActionPerformed
        System.out.println("Vox enable check box is not functional yet.");
    }//GEN-LAST:event_sv_enableVoxCheckBoxActionPerformed

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

    private void sv_ctcssSquelchCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_ctcssSquelchCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_sv_ctcssSquelchCheckBoxActionPerformed

    private void sv_modesListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_modesListButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_sv_modesListButtonActionPerformed

    private void sv_radioNamesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_radioNamesActionPerformed
//        if (!inhibit) initialize(); 
//        String selectionStr = ((RadioNamesListButton)sv_radioNamesListButton).getSelectedItem();
//        sv_radioNamesListButton.getAccessibleContext().setAccessibleDescription("Selection is "+selectionStr);
//
    }//GEN-LAST:event_sv_radioNamesActionPerformed

    private void sv_interfacesListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_interfacesListButtonActionPerformed
//        if (!inhibit) initialize(); 
//        String selectionStr = ((RWListButton)sv_interfacesListButton).getSelectedItem();
//        ((RWListButton)sv_interfacesListButton).setButtonText(selectionStr);
//        sv_interfacesListButton.getAccessibleContext().setAccessibleDescription("Selection is "+selectionStr);                                                  
    }//GEN-LAST:event_sv_interfacesListButtonActionPerformed

    private void startScanUpHandler(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startScanUpHandler
        scanStateMachine.startScan(1);
    }//GEN-LAST:event_startScanUpHandler

    private void sv_fbkinActionHandler(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_fbkinActionHandler
        // TODO add your handling code here:
    }//GEN-LAST:event_sv_fbkinActionHandler

    private void cwSendTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cwSendTextActionPerformed
        String txt = evt.getActionCommand();
        System.out.println("Action Command : "+txt);
        String getText = cwSendText.getText();
        String comStr = "b "+getText;
        sendRadioCom(comStr, 1, true);
        System.out.println("Sending CW  ["+getText+"]");
        cwSendText.setText("");

    }//GEN-LAST:event_cwSendTextActionPerformed

    private void pttCheckBoxStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_pttCheckBoxStateChanged
        ((SwrIndicator)swrIndicator).updateSwr();
    }//GEN-LAST:event_pttCheckBoxStateChanged

    private void vfoFrequencyAActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_vfoFrequencyAActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_vfoFrequencyAActionPerformed

    private void vfoFrequencyBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_vfoFrequencyBActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_vfoFrequencyBActionPerformed

    private void copyMemButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyMemButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_copyMemButtonActionPerformed

    private void scopeStartStopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scopeStartStopButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_scopeStartStopButtonActionPerformed

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
    private javax.swing.JPanel appSettingsPanel;
    public javax.swing.JPanel channelPanel;
    public javax.swing.JTabbedPane channelsTabbedPane;
    protected javax.swing.JLabel comErrorIconLabel;
    private javax.swing.JButton copyButton;
    private javax.swing.JButton copyMemButton;
    public javax.swing.JFormattedTextField cwSendText;
    protected javax.swing.JLabel dcdIconLabel;
    private javax.swing.JPanel dstarPanel;
    private javax.swing.JButton eraseMemButton;
    private javax.swing.JPanel firstSettingsPanel;
    private javax.swing.JButton helpButton;
    private javax.swing.JPanel ifControlsPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
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
    public javax.swing.JPanel memoryButtonsPanel;
    public javax.swing.JPanel memoryPanel;
    public javax.swing.JScrollPane memoryScrollPane;
    public javax.swing.JPanel memoryStoragePanel;
    private javax.swing.JLabel micGainLabel;
    private javax.swing.JCheckBox muteCheckBox;
    private javax.swing.JPanel operateTransceiverPanel;
    private javax.swing.JTabbedPane operationDetailsTabbedPane;
    private javax.swing.JTabbedPane overallTabbedPane;
    private javax.swing.JButton pasteMemButton;
    private javax.swing.JCheckBox pttCheckBox;
    private javax.swing.JPanel radioPanel;
    protected javax.swing.JInternalFrame recieverGroupBox;
    private javax.swing.JLabel rfGainLabel;
    private javax.swing.JPanel rttyPanel;
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
    public javax.swing.JButton sv_agcListButton;
    protected javax.swing.JCheckBox sv_anfCheckBox;
    protected javax.swing.JComboBox sv_antennaComboBox;
    protected javax.swing.JCheckBox sv_apfCheckBox;
    public javax.swing.JCheckBox sv_attenuatorCheckBox;
    protected javax.swing.JCheckBox sv_blankerCheckBox;
    protected javax.swing.JCheckBox sv_compressionCheckBox;
    protected javax.swing.JSlider sv_compressionSlider;
    protected javax.swing.JButton sv_ctcssListButton;
    public javax.swing.JCheckBox sv_ctcssSquelchCheckBox;
    public javax.swing.JSlider sv_cwSpeedSlider;
    public javax.swing.JSlider sv_cwToneSlider;
    protected javax.swing.JCheckBox sv_dspCheckBox;
    public javax.swing.JSlider sv_dspSlider;
    public javax.swing.JButton sv_dwellTimeListButton;
    protected javax.swing.JCheckBox sv_enableVoxCheckBox;
    protected javax.swing.JCheckBox sv_fbkinCheckBox;
    public javax.swing.JButton sv_ifFilterListButton;
    protected javax.swing.JComboBox sv_ifShiftComboBox;
    public javax.swing.JButton sv_interfacesListButton;
    protected javax.swing.JRadioButton sv_jrxToRadioButton;
    protected javax.swing.JSlider sv_micGainSlider;
    public javax.swing.JButton sv_modesListButton;
    public javax.swing.JCheckBox sv_preampCheckBox;
    public javax.swing.JButton sv_radioNamesListButton;
    protected javax.swing.JRadioButton sv_radioToJrxButton;
    protected javax.swing.JCheckBox sv_rawSigCheckBox;
    protected javax.swing.JSlider sv_rfGainSlider;
    protected javax.swing.JSlider sv_rfPowerSlider;
    public javax.swing.JButton sv_scanStepListButton;
    protected javax.swing.JCheckBox sv_scopeDotsCheckBox;
    public javax.swing.JButton sv_scopeSpeedListButton;
    public javax.swing.JButton sv_scopeStepListButton;
    protected javax.swing.JCheckBox sv_squelchCheckBox;
    protected javax.swing.JSlider sv_squelchSlider;
    public javax.swing.JButton sv_stepPeriodListButton;
    protected javax.swing.JCheckBox sv_syncCheckBox;
    public javax.swing.JCheckBox sv_synthSquelchCheckBox;
    public javax.swing.JButton sv_timerIntervalListButton;
    protected javax.swing.JCheckBox sv_tunerCheckBox;
    public javax.swing.JCheckBox sv_txCtcssCheckBox;
    protected javax.swing.JCheckBox sv_volumeExitCheckBox;
    protected javax.swing.JSlider sv_volumeSlider;
    protected javax.swing.JSlider sv_voxLevelSlider;
    public javax.swing.JLabel swrIndicator;
    private javax.swing.JPanel transmitterPanel;
    private javax.swing.JButton tuneComsButton;
    private javax.swing.JPanel verticalListPanel;
    public javax.swing.JInternalFrame vfoDisplayControl;
    public javax.swing.JTextField vfoFrequencyA;
    public javax.swing.JTextField vfoFrequencyB;
    public javax.swing.JInternalFrame vfoGroup;
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
