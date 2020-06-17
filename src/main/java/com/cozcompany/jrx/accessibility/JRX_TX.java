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
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;

import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Timer;
import java.util.regex.Pattern;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * @author lutusp
 */
final public class JRX_TX extends javax.swing.JFrame implements 
        ListSelectionListener {

    final String appVersion = "5.0.4";
    final String appName;
    final String programName;
    String lineSep;
    String userDir;
    String userPath;
    
    Timer periodicTimer;
    ParseComLine comArgs = null;
    ImageIcon redLed, greenLed, blueLed, yellowLed;
    ScanStateMachine scanStateMachine;
    ControlInterface[] settableControls;
    ArrayList<String> interfaceNames = null;
    ChannelChart chart;
    SquelchScheme squelchScheme;
   
    Map<String, Integer> radioCodes = null;
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
    boolean inhibit;
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
    int comError = 0;
    long oldTime = 0; // debugging
    FreqDisplay vfoDisplay;
    ScanController scanDude;

    //dependencies
    FileHelpers fileHelpers;

    /**
     * Creates new form JRX
     */
    public JRX_TX(String[] args) {

        fileHelpers = new FileHelpers();

        comArgs = new ParseComLine(this, args);
        inhibit = true;
        oldTime = System.currentTimeMillis();
       
        readBuffer = new byte[readBufferLen];
        memoryCollection = new MemoryCollection(this);
        appName = getClass().getSimpleName();
        programName = appName + " " + appVersion;
        setTitle(programName);
        setIconImage(new ImageIcon(getClass().getClassLoader().getResource("icons/JRX.png")).getImage());
        redLed = new ImageIcon(getClass().getClassLoader().getResource("icons/red-on-16.png"));
        greenLed = new ImageIcon(getClass().getClassLoader().getResource("icons/green-on-16.png"));
        blueLed = new ImageIcon(getClass().getClassLoader().getResource("icons/blue-on-16.png"));
        yellowLed = new ImageIcon(getClass().getClassLoader().getResource("icons/yellow-on-16.png"));
        lineSep = System.getProperty("line.separator");

        userDir = System.getProperty("user.home");
        userPath = userDir + FILE_SEP + "." + appName;
        buttonFilePath = userPath + FILE_SEP + "memoryButtons.ini";
        memoryCollection.setFilePath(buttonFilePath);
        new File(userPath).mkdirs();
        digitsFont = new Font(Font.MONOSPACED, Font.PLAIN, 30);
        initComponents();
        

        baseFont = new Font(Font.MONOSPACED, Font.PLAIN, getFont().getSize());
        setFont(baseFont);
        // Must create/initialize vfoDisplay before scan functions.
        vfoDisplay = new FreqDisplay(this, digitsParent);
        vfoDisplay.initDigits();
        scanStateMachine = new ScanStateMachine(this);       
        scanDude = new ScanController(this);
        // default app size
        setSize(defWidth, defHeight);
        setControlList();
        squelchScheme = new SquelchScheme(this);               
        setupControls();
        setDefaultComboContent(); 
        config = new ConfigManager(this);
        if (!comArgs.reset) {
            config.read();
        }
        chart = new ChannelChart(this);
        chart.init();        
        inhibit = false;
        initialize();
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
    private void resetTimer() {
        long delay = (long) ((RWComboBox) sv_timerIntervalComboBox).getConvertedValue();
        //p("delay: " + delay);
        if (delay > 0 && periodicTimer != null) {
            periodicTimer.schedule(new PeriodicEvents(), delay);
        }
    }
/////////////////////////////////////////////////////////////////////////////////
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
                vfoDisplay.frequencyToDigits((long) (freq * 1e6 + 0.5));
                RWComboBox box = (RWComboBox) sv_modesComboBox;
                mode = "Mode " + mode.toUpperCase();
                Integer index = box.displayMap.get(mode);
                if (index != null) sv_modesComboBox.setSelectedIndex(index);
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
            if (slowRadio || scanStateMachine.scanTimer != null) {
                vfoDisplay.timerUpdateFreq();
                //timerSetSliders();
            }
            resetTimer();
        }
    }
    /**
     * On detecting an error in reply text, comError is set to 2 which is checked
     * by this method and decremented on each check.
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


    private void initialize() {
        if (!inhibit) {
            oldRadioFrequency = -1;
            dcdIconLabel.setText("");
            dcdIconLabel.setIcon(greenLed);
            // must reset to defaults again
            // to accommodate change in rig
            //setDefaultComboContent();
            setupHamlibDaemon();
            inhibit = true;
            setComboBoxScales();
            inhibit = false;
            if (sv_jrxToRadioButton.isSelected()) {
                vfoDisplay.setFrequency();
                writeRadioControls();
            } else {
                vfoDisplay.frequencyToDigits(defaultFrequency); // Coz moved this.
            }
            squelchScheme.setRadioSquelch();
            readRadioControls(true);  // Reads frequency from radio
            startCyclicalTimer();
            measureSpeed();
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
            cont.writeValue(true);
        }
    }

    /**
     * Query the Mode comboBox to get the modulation mode of item n.  Validates
     * input parameter n.
     * 
     * @param n
     * @return modulation mode name string or ""
     */
    protected String getMode(int n) {
        String s = "";
        RWComboBox box = (RWComboBox) sv_modesComboBox;
        if (n >= 0 && n < box.reverseUseMap.size()) {
            s = box.reverseUseMap.get(n);
        }
        return s;
    }

    private void measureSpeed() {
        long t = System.currentTimeMillis();
        oldRadioFrequency = -1;
        setRadioFrequency(vfoDisplay.getFreq());
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

    private void setControlList() {
        controlList = new ArrayList<>();
        Component[] list = new Component[]{
                sv_squelchSlider,
                sv_volumeSlider,
                sv_rfGainSlider,
                //sv_ifShiftComboBox,
                sv_dspComboBox,
                sv_dspCheckBox,
                sv_modesComboBox,
                sv_filtersComboBox,
                //sv_antennaComboBox,
                sv_attenuatorComboBox,
                sv_agcComboBox,
                sv_preampComboBox,
                sv_ctcssComboBox,
                sv_ctcssCheckBox,
                sv_blankerCheckBox,
                sv_apfCheckBox,
                sv_anfCheckBox
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
        //setComboBoxContent((RWComboBox) sv_ifShiftComboBox, "IF", -50, 50, 5, -50, 50, 0, 0, 0);
        setComboBoxContent((RWComboBox) sv_dspComboBox, "DSP", 0, 100, 5, 0, 100, 0, 1, 0);
        setComboBoxContent((RWComboBox) sv_agcComboBox, "AGC", 0, 10, 1, 0, 1, 0, 10, 1);
        //setComboBoxContent((RWComboBox) sv_antennaComboBox, "Ant", 0, 4, 1, 0, 1, 0, 1, 1);
        initInterfaceList();
        initRigSpecs();
        memoryCollection.readMemoryButtons();
        initTimeValues((RWComboBox) sv_timerIntervalComboBox);

        scanDude.initScanValues(sv_scanStepComboBox, 12, sv_scanSpeedComboBox, 5);
        scanDude.initScanValues(null, 0, sv_dwellTimeComboBox, 10);
        scanDude.initScanValues(sv_scopeStepComboBox, 12, sv_scopeSpeedComboBox, 0);
        memoryButtonsPanel.setBackground(new Color(128, 200, 220));
        
        scannerPanel.setBackground(new Color(240, 240, 220));
        scopeControlLeftPanel.setBackground(new Color(240, 240, 220));
        scopeControlRightPanel.setBackground(new Color(200, 220, 240));
        digitsParent.setToolTipText("<html>Tune each digit:<ul><li>Mouse wheel (❃): up, down</li><li>Mouse click top: up</li><li>Mouse click bottom: down</li></ul></html>");
        scanDude.updateScanControls();
    }

    // force DSP on if auxiiliary DSP functions are activated
    private void enableDSP() {
        sv_dspCheckBox.setSelected(true);
        ((RWCheckBox) sv_dspCheckBox).actionPerformed(null);
    }

    /**
     *  Get radio capability data from Hamlib backend and set up drop-down lists.
     *  If a capability is not present in the replay to the "\dump_caps" command,
     *  then the UI Component is not enabled (grayed out);
     */
    private void getRigCaps() {
        String com = "\\dump_caps";
        radioData = sendRadioCom(com, 3, false);
        System.out.println(" Reply to \\dump_caps "+ radioData);

        enableControlCap(sv_ctcssComboBox, radioData, "(?ism).*^Can set CTCSS Squelch:\\s+Y$", false);
        //(sv_agcComboBox, radioData, "(?ism).*^Set level:.*?AGC\\(", true);
        //enableControlCap(sv_antennaComboBox, radioData, "(?ism).*^Can set Ant:\\s+Y$", false);
        enableControlCap(sv_preampComboBox, radioData, "(?ism).*^Set level:.*?PREAMP\\(", true);
        enableControlCap(sv_volumeSlider, radioData, "(?ism).*^Set level:.*?AF\\(", true);
        enableControlCap(sv_rfGainSlider, radioData, "(?ism).*^Set level:.*?RF\\(", true);
        enableControlCap(sv_squelchSlider, radioData, "(?ism).*^Set level:.*?SQL\\(", true);
        //enableControlCap(sv_ifShiftComboBox, radioData, "(?ism).*^Set level:.*?IF\\(", true);
        enableControlCap(sv_blankerCheckBox, radioData, "(?ism).*^Set functions:.*?\\sNB\\s", false);
        enableControlCap(sv_anfCheckBox, radioData, "(?ism).*^Set functions:.*?\\sANF\\s", false);
        enableControlCap(sv_apfCheckBox, radioData, "(?ism).*^Set functions:.*?\\sAPF\\s", false);
        enableControlCap(sv_dspComboBox, radioData, "(?ism).*^Set level:.*?NR\\(", true);
        String s = sendRadioCom("\\get_dcd", 0, false);
        dcdCapable = (s != null && s.matches("\\d+"));
        squelchScheme.setSquelchScheme();
    }





    protected void waitMS(int ms) {
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

    private void setComboBoxContent(
            RWComboBox box,
            String label,
            int start,
            int end,
            int step,
            int xlow,
            int xhigh,
            int ylow,
            int yhigh,
            int initial) {
        box.removeAllItems();
        for (int i = start; i <= end; i += step) {
            String s = String.format("%s %d", label, i);
            //box.addListItem(s, "" + i);
            box.addListItem(s, i, "" + i);
        }
        box.setXLow(xlow);
        box.setXHigh(xhigh);
        // to avoid resetting values,
        // make yhigh = ylow
        if (yhigh > ylow) {
            box.setYLow(ylow);
            box.setYHigh(yhigh);
        }
        box.setSelectedIndex(box.useMap.get("" + initial));
    }


    protected void readFrequency() {
        try {           
            String sf = sendRadioCom("f", 0, false);
            long v = Long.parseLong(sf);
            vfoDisplay.frequencyToDigits(v);
        } catch (Exception e) {
            //e.printStackTrace(System.out);
        }
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
                    v = vfoDisplay.getFreq();
                }
                if (v < 0) {
                    throw (new Exception("frequency <= 0"));
                }
                if (oldRadioFrequency != v) {
                    String com = String.format("F %d", v);
                    sendRadioCom(com, 0, true);
                    //System.out.println("setRadioFrequency() : "+ com);
                    oldRadioFrequency = v;
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

        ((RWComboBox) sv_preampComboBox).setGenericComboBoxScale("Pre", "(?ism).*^Preamp:\\s*(.*?)\\s*$.*", true, true);
        ((RWComboBox) sv_attenuatorComboBox).setGenericComboBoxScale("Att", "(?ism).*^Attenuator:\\s*(.*?)\\s*$.*", true, true);
        ((RWComboBox) sv_filtersComboBox).setGenericComboBoxScale("", "", true, true);
        ((RWComboBox) sv_modesComboBox).setGenericComboBoxScale("Mode", "(?ism).*^Mode list:\\s*(.*?)\\s*$.*", false, false);
        ((RWComboBox) sv_ctcssComboBox).setGenericComboBoxScale("CTCSS", "(?ism).*^CTCSS:\\s*(.*?)\\s*$.*", false, true);
    }
    /**
     * Read rig specs from hamlib.
     */
    private void initRigSpecs() {
        radioCodes = new TreeMap<>();
        String a, b, s="";
        //p("trying to read rig specs...");
        if (hamlibExecPath == null) {
            JOptionPane.showMessageDialog(this,"MISSING HAMLIB", 
                    "Missing Hamlib path.",
                    JOptionPane.WARNING_MESSAGE);
        } else {
        
            s = runSysCommand(new String[]{hamlibExecPath, "-l"}, true);
            if (comArgs.debug >= 1) {
                pout("dump from rigctld -l: [" + s + "]");
            }
        } 
        for (String item : s.split(lineSep)) {
            // the try ... catch is only to filter the table header
            if (item.length() > 30) {
                try {
                    if (comArgs.debug >= 1) {
                        pout("rigctl radio description line: [" + item + "]");
                    }
                    String parse = item.replaceFirst("^\\s*(\\S+)\\s*(.*)$", "$1\t$2");
                    String[] fields = parse.split("\t");
                    if (fields != null && fields.length > 1) {
                        a = fields[0].trim();
                        if (a.matches("[0-9]+") && fields[1].length() > 47) {
                            b = fields[1].substring(0, 47).trim();
                            b = b.replaceAll("\\s+", " ");
                            int v = Integer.parseInt(a);
                            if (comArgs.debug >= 1) {
                                pout("radio record: " + b + " = " + v);
                            }
                            radioCodes.put(b, v);
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
            int val = radioCodes.get(key);
            sv_radioNamesComboBox.addItem(key);
        }
    }

    /**
     * Get a list of radios and their codes before the socket has been set up.
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
                rigName = String.format("(radio code: %d)", rigCode);
                sv_radioNamesComboBox.setEnabled(false);
            }
            if (comArgs.interfaceName != null) {
                interfaceName = comArgs.interfaceName;
                sv_interfacesComboBox.setSelectedItem(interfaceName);
                sv_interfacesComboBox.setEnabled(false);
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
     * Given a swing Component (created by the Swing Gui designer), the reply
     * text "source" from a rigctl capabilities command "\dump_Caps", a regular
     * expression string "search" for searching the reply text, and a boolean which is
     * true to set value range.  The Component is enabled if it is found
     * in the reply by the search regex string.
     * 
     * @param cc
     * @param source
     * @param search
     * @param level 
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

    protected String sendRadioCom(String s, int localDebug, boolean writeMode) {
        String result = null;
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
            if (hamlibDaemon != null) {
                hamlibDaemon.destroy();
                waitMS(100);
                hamlibDaemon = null;
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
            // don't do this for high debug levels
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

    // based on a little online research
    // x (db) 0 = s9
    // x (db) -54 = s0
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
        radioPanel = new javax.swing.JPanel();
        overallTabbedPane = new javax.swing.JTabbedPane();
        operateTransceiverPanel = new javax.swing.JPanel();
        sv_squelchSlider = new com.cozcompany.jrx.accessibility.RWSlider(this,"L","SQL",0);
        sv_volumeSlider = new com.cozcompany.jrx.accessibility.RWSlider(this,"L","AF",20);
        sv_rfGainSlider = new com.cozcompany.jrx.accessibility.RWSlider(this,"L","RF",50);
        sv_ctcssComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"ctcss","");
        sv_synthSquelchCheckBox = new RWCheckBox(this,null,null);
        afGainLabel = new javax.swing.JLabel();
        ctcssLabel = new javax.swing.JLabel();
        rfGainLabel = new javax.swing.JLabel();
        squelchLabel = new javax.swing.JLabel();
        sv_modesComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"M","");
        modeLabel = new javax.swing.JLabel();
        sv_agcComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","AGC");
        agcLabel = new javax.swing.JLabel();
        muteCheckBox = new javax.swing.JCheckBox();
        operationDetailsTabbedPane = new javax.swing.JTabbedPane();
        transmitterPanel = new javax.swing.JPanel();
        rfPowerOutputSlider = new javax.swing.JSlider();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        micGainSlider = new javax.swing.JSlider();
        compressionSlider = new javax.swing.JSlider();
        jLabel10 = new javax.swing.JLabel();
        voxSlider = new javax.swing.JSlider();
        jLabel11 = new javax.swing.JLabel();
        jCheckBox2 = new javax.swing.JCheckBox();
        jButton1 = new javax.swing.JButton();
        jCheckBox3 = new javax.swing.JCheckBox();
        toneSelectionComboBox = new javax.swing.JComboBox<>();
        jLabel12 = new javax.swing.JLabel();
        sv_antennaComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"Y","");
        jButton2 = new javax.swing.JButton();
        jLabel18 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jRadioButton1 = new javax.swing.JRadioButton();
        jRadioButton2 = new javax.swing.JRadioButton();
        jRadioButton3 = new javax.swing.JRadioButton();
        ifControlsPanel = new javax.swing.JPanel();
        sv_filtersComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"F","");
        verticalListPanel = new javax.swing.JPanel();
        sv_rawSigCheckBox = new RWCheckBox(this,null,null);
        sv_blankerCheckBox = new RWCheckBox(this,"U","NB");
        sv_apfCheckBox = new RWCheckBox(this,"U","APF");
        sv_anfCheckBox = new RWCheckBox(this,"U","ANF");
        sv_ctcssCheckBox = new RWCheckBox(this,"U","TSQL");
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
        noiseReductionPanel = new javax.swing.JPanel();
        keyerPanel = new javax.swing.JPanel();
        rttyPanel = new javax.swing.JPanel();
        scanPanel = new javax.swing.JPanel();
        scannerPanel = new javax.swing.JPanel();
        sv_scanStepComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,null,null);
        sv_scanSpeedComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,null,null);
        sv_dwellTimeComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,null,null);
        scanDownButton = new javax.swing.JButton();
        scanStopButton = new javax.swing.JButton();
        scanUpButton = new javax.swing.JButton();
        sv_squelchCheckBox = new RWCheckBox(this,null,null);
        scanTypeLabel = new javax.swing.JLabel();
        scanIconLabel = new javax.swing.JLabel();
        channelsTabbedPane = new javax.swing.JTabbedPane();
        memoryStoragePanel = new javax.swing.JPanel();
        memoryScrollPane = new javax.swing.JScrollPane();
        memoryButtonsPanel = new javax.swing.JPanel();
        buttonPanel2 = new javax.swing.JPanel();
        copyMemButton = new javax.swing.JButton();
        pasteMemButton = new javax.swing.JButton();
        eraseMemButton = new javax.swing.JButton();
        helpButton = new javax.swing.JButton();
        tuneComsButton = new javax.swing.JButton();
        favoriteSWLChannels = new javax.swing.JPanel();
        memoryPanel = new javax.swing.JPanel();
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
        scopeControlRightPanel = new javax.swing.JPanel();
        sv_jrxToRadioButton = new javax.swing.JRadioButton();
        sv_radioToJrxButton = new javax.swing.JRadioButton();
        sv_syncCheckBox = new javax.swing.JCheckBox();
        sv_timerIntervalComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,null,null);
        jLabel2 = new javax.swing.JLabel();
        sv_volumeExitCheckBox = new javax.swing.JCheckBox();
        sv_radioNamesComboBox = new javax.swing.JComboBox<>();
        ledPanel = new javax.swing.JPanel();
        speedIconLabel = new javax.swing.JLabel();
        comErrorIconLabel = new javax.swing.JLabel();
        dcdIconLabel = new javax.swing.JLabel();
        radioNamesLabel = new javax.swing.JLabel();
        signalProgressBar = new javax.swing.JProgressBar();
        sv_interfacesComboBox = new javax.swing.JComboBox<>();
        vfoTabbedPane = new javax.swing.JTabbedPane();
        rxVfoPanel = new javax.swing.JPanel();
        digitsParent = new javax.swing.JPanel();
        txVfoPanel = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        overallTabbedPane.setMinimumSize(new java.awt.Dimension(100, 400));

        sv_squelchSlider.setMajorTickSpacing(10);
        sv_squelchSlider.setMinorTickSpacing(5);
        sv_squelchSlider.setPaintTicks(true);
        sv_squelchSlider.setToolTipText("Squelch (❃)\n");
        sv_squelchSlider.setValue(0);
        sv_squelchSlider.setMaximumSize(new java.awt.Dimension(32, 38));
        sv_squelchSlider.setPreferredSize(new java.awt.Dimension(20, 38));

        sv_volumeSlider.setMajorTickSpacing(10);
        sv_volumeSlider.setMinorTickSpacing(5);
        sv_volumeSlider.setPaintTicks(true);
        sv_volumeSlider.setToolTipText("Audio Gain (❃)");

        sv_rfGainSlider.setMajorTickSpacing(10);
        sv_rfGainSlider.setMinorTickSpacing(5);
        sv_rfGainSlider.setPaintTicks(true);

        sv_ctcssComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "123.0", "Item 2", "Item 3", "Item 4" }));
        sv_ctcssComboBox.setToolTipText("CTCSS tone squelch frequencies (❃)");

        sv_synthSquelchCheckBox.setText("Squelch");
        sv_synthSquelchCheckBox.setToolTipText("Use JRX squelch scheme");
        sv_synthSquelchCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_synthSquelchCheckBoxActionPerformed(evt);
            }
        });

        afGainLabel.setText("AF Gain");

        ctcssLabel.setLabelFor(sv_ctcssComboBox);
        ctcssLabel.setText("CTCSS");

        rfGainLabel.setLabelFor(sv_rfGainSlider);
        rfGainLabel.setText("RF Gain");

        squelchLabel.setText("Squelch");

        sv_modesComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "LSB", "Item 2", "Item 3", "Item 4" }));
        sv_modesComboBox.setToolTipText("Operating modes (❃)");
        sv_modesComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_modesComboBoxActionPerformed(evt);
            }
        });

        modeLabel.setLabelFor(sv_modesComboBox);
        modeLabel.setText("MODE");

        sv_agcComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "AGC", "Item 2", "Item 3", "Item 4" }));
        sv_agcComboBox.setToolTipText("AGC setting (❃)");
        sv_agcComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_agcComboBoxActionPerformed(evt);
            }
        });

        agcLabel.setLabelFor(sv_agcComboBox);
        agcLabel.setText("AGC");

        muteCheckBox.setText("Mute");

        rfPowerOutputSlider.setMajorTickSpacing(10);
        rfPowerOutputSlider.setMinorTickSpacing(5);
        rfPowerOutputSlider.setPaintLabels(true);
        rfPowerOutputSlider.setPaintTicks(true);

        jLabel8.setFont(new java.awt.Font("Lucida Grande", 0, 14)); // NOI18N
        jLabel8.setLabelFor(rfPowerOutputSlider);
        jLabel8.setText("RF Power Output");

        jLabel9.setLabelFor(micGainSlider);
        jLabel9.setText("MIC GAIN");

        micGainSlider.setMajorTickSpacing(10);
        micGainSlider.setMinorTickSpacing(5);
        micGainSlider.setPaintTicks(true);

        compressionSlider.setMajorTickSpacing(10);
        compressionSlider.setMinorTickSpacing(5);
        compressionSlider.setPaintTicks(true);

        jLabel10.setLabelFor(compressionSlider);
        jLabel10.setText("Compression");

        voxSlider.setMajorTickSpacing(10);
        voxSlider.setMinorTickSpacing(5);
        voxSlider.setPaintTicks(true);

        jLabel11.setLabelFor(voxSlider);
        jLabel11.setText("VOX Level");

        jCheckBox2.setText("Enable Comp");
        jCheckBox2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBox2ActionPerformed(evt);
            }
        });

        jButton1.setMnemonic('C');
        jButton1.setText("COPY RX to TX");

        jCheckBox3.setText("Enable VOX");

        toneSelectionComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "TONE", "Item 2", "Item 3", "Item 4" }));

        jLabel12.setText("Tone Selection");

        sv_antennaComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_antennaComboBox.setToolTipText("Available antennas (❃)");

        jButton2.setMnemonic('S');
        jButton2.setText("SWAP RX and TX");

        jLabel18.setText("Antenna");

        jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        splitFreqGroup.add(jRadioButton1);
        jRadioButton1.setFont(new java.awt.Font("Lucida Grande", 0, 14)); // NOI18N
        jRadioButton1.setText("Simplex");

        splitFreqGroup.add(jRadioButton2);
        jRadioButton2.setFont(new java.awt.Font("Lucida Grande", 0, 14)); // NOI18N
        jRadioButton2.setText("Repeater Duplex");

        splitFreqGroup.add(jRadioButton3);
        jRadioButton3.setFont(new java.awt.Font("Lucida Grande", 0, 14)); // NOI18N
        jRadioButton3.setText("Split Frequency same mode");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jRadioButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jRadioButton3))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(jRadioButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 193, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(23, Short.MAX_VALUE))
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {jRadioButton1, jRadioButton3});

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jRadioButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(5, 5, 5)
                .addComponent(jRadioButton2)
                .addGap(5, 5, 5)
                .addComponent(jRadioButton3)
                .addContainerGap(15, Short.MAX_VALUE))
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jRadioButton1, jRadioButton2, jRadioButton3});

        javax.swing.GroupLayout transmitterPanelLayout = new javax.swing.GroupLayout(transmitterPanel);
        transmitterPanel.setLayout(transmitterPanelLayout);
        transmitterPanelLayout.setHorizontalGroup(
            transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(transmitterPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(voxSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 167, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addComponent(jLabel11)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jCheckBox3, javax.swing.GroupLayout.PREFERRED_SIZE, 114, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(14, 14, 14))
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 147, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(57, 57, 57)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(transmitterPanelLayout.createSequentialGroup()
                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(micGainSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 171, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(compressionSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 171, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(8, 8, 8)
                .addComponent(jLabel10)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jCheckBox2)
                .addContainerGap(274, Short.MAX_VALUE))
            .addGroup(transmitterPanelLayout.createSequentialGroup()
                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(rfPowerOutputSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 311, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(sv_antennaComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel18))
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addGap(177, 177, 177)
                        .addComponent(jLabel9)
                        .addGap(66, 66, 66)
                        .addComponent(toneSelectionComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 121, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel12, javax.swing.GroupLayout.PREFERRED_SIZE, 108, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        transmitterPanelLayout.setVerticalGroup(
            transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(transmitterPanelLayout.createSequentialGroup()
                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addGap(15, 15, 15)
                        .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(rfPowerOutputSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel8)
                            .addComponent(sv_antennaComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel18))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(micGainSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(transmitterPanelLayout.createSequentialGroup()
                                .addGap(12, 12, 12)
                                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                                    .addComponent(compressionSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jLabel10))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(voxSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel11)
                                        .addComponent(jCheckBox3)))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(transmitterPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, 50, Short.MAX_VALUE)
                                    .addComponent(jButton1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .addGroup(transmitterPanelLayout.createSequentialGroup()
                                .addGap(4, 4, 4)
                                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(transmitterPanelLayout.createSequentialGroup()
                                .addGap(12, 12, 12)
                                .addComponent(jCheckBox2))))
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addGap(79, 79, 79)
                        .addComponent(jLabel12))
                    .addGroup(transmitterPanelLayout.createSequentialGroup()
                        .addGap(80, 80, 80)
                        .addComponent(toneSelectionComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(90, Short.MAX_VALUE))
        );

        transmitterPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jLabel8, rfPowerOutputSlider});

        transmitterPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {compressionSlider, jCheckBox2, jLabel10});

        transmitterPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jCheckBox3, jLabel11, voxSlider});

        transmitterPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jLabel12, jLabel9, toneSelectionComboBox});

        transmitterPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {jLabel18, sv_antennaComboBox});

        operationDetailsTabbedPane.addTab("Transmitter", null, transmitterPanel, "Important xmit controls and details.");
        transmitterPanel.getAccessibleContext().setAccessibleName("Transmitter ");
        transmitterPanel.getAccessibleContext().setAccessibleDescription("Set output power and mic gain");

        sv_filtersComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_filtersComboBox.setToolTipText("Bandwidth filters (❃)\n");

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

        sv_ctcssCheckBox.setText("CTCSS");
        sv_ctcssCheckBox.setToolTipText("Tone squelch control");

        sv_dspCheckBox.setText("DSP");
        sv_dspCheckBox.setToolTipText("Digital signal processing");

        javax.swing.GroupLayout verticalListPanelLayout = new javax.swing.GroupLayout(verticalListPanel);
        verticalListPanel.setLayout(verticalListPanelLayout);
        verticalListPanelLayout.setHorizontalGroup(
            verticalListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(verticalListPanelLayout.createSequentialGroup()
                .addGap(2, 2, 2)
                .addComponent(sv_rawSigCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGroup(verticalListPanelLayout.createSequentialGroup()
                .addGap(1, 1, 1)
                .addComponent(sv_blankerCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGroup(verticalListPanelLayout.createSequentialGroup()
                .addGap(1, 1, 1)
                .addComponent(sv_ctcssCheckBox))
            .addComponent(sv_dspCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(sv_apfCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(sv_anfCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        verticalListPanelLayout.setVerticalGroup(
            verticalListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(verticalListPanelLayout.createSequentialGroup()
                .addComponent(sv_rawSigCheckBox)
                .addGap(0, 0, 0)
                .addComponent(sv_blankerCheckBox)
                .addGap(0, 0, 0)
                .addComponent(sv_ctcssCheckBox)
                .addGap(0, 0, 0)
                .addComponent(sv_dspCheckBox)
                .addGap(0, 0, 0)
                .addComponent(sv_apfCheckBox)
                .addGap(0, 0, 0)
                .addComponent(sv_anfCheckBox))
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
                        .addComponent(verticalListPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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
                    .addComponent(verticalListPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
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

        operationDetailsTabbedPane.addTab("Noise Reduction", noiseReductionPanel);
        noiseReductionPanel.getAccessibleContext().setAccessibleName("Noise Reduction");
        noiseReductionPanel.getAccessibleContext().setAccessibleDescription("R X Controls to reduce interference");

        operationDetailsTabbedPane.addTab("Keyer", keyerPanel);
        keyerPanel.getAccessibleContext().setAccessibleName("Keyer panel");
        keyerPanel.getAccessibleContext().setAccessibleDescription("Choose CW key parameters.");

        operationDetailsTabbedPane.addTab("RTTY", rttyPanel);
        rttyPanel.getAccessibleContext().setAccessibleName("RTTY");
        rttyPanel.getAccessibleContext().setAccessibleDescription("Radio Teletype settings and decode window.");

        javax.swing.GroupLayout operateTransceiverPanelLayout = new javax.swing.GroupLayout(operateTransceiverPanel);
        operateTransceiverPanel.setLayout(operateTransceiverPanelLayout);
        operateTransceiverPanelLayout.setHorizontalGroup(
            operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                        .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(rfGainLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(squelchLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 60, Short.MAX_VALUE)))
                            .addComponent(afGainLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(sv_rfGainSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 207, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(sv_squelchSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 169, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(sv_modesComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(sv_ctcssComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(sv_agcComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ctcssLabel)
                            .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                                .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(agcLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(modeLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(40, 40, 40)
                                .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(muteCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(sv_synthSquelchCheckBox, javax.swing.GroupLayout.PREFERRED_SIZE, 129, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                    .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                        .addGap(72, 72, 72)
                        .addComponent(sv_volumeSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 225, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(operationDetailsTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 677, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        operateTransceiverPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {sv_rfGainSlider, sv_squelchSlider, sv_volumeSlider});

        operateTransceiverPanelLayout.setVerticalGroup(
            operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(sv_modesComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(modeLabel)
                                .addComponent(muteCheckBox))
                            .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(sv_volumeSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(afGainLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .addGap(12, 12, 12)
                        .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(sv_rfGainSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(rfGainLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(sv_ctcssComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(ctcssLabel))
                        .addGap(10, 10, 10)
                        .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(squelchLabel)
                            .addComponent(sv_squelchSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(17, 17, 17)
                        .addComponent(operationDetailsTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(operateTransceiverPanelLayout.createSequentialGroup()
                        .addGap(108, 108, 108)
                        .addGroup(operateTransceiverPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(sv_synthSquelchCheckBox)
                            .addComponent(sv_agcComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(agcLabel))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        operateTransceiverPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {ctcssLabel, muteCheckBox, sv_ctcssComboBox});

        operateTransceiverPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {agcLabel, sv_agcComboBox, sv_synthSquelchCheckBox});

        overallTabbedPane.addTab("Operate Transceiver", operateTransceiverPanel);
        operateTransceiverPanel.getAccessibleContext().setAccessibleName("Operations Tab");
        operateTransceiverPanel.getAccessibleContext().setAccessibleDescription("Vital transceiver controls");

        scannerPanel.setLayout(new java.awt.GridBagLayout());

        sv_scanStepComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_scanStepComboBox.setToolTipText("Scan frequency step size (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        scannerPanel.add(sv_scanStepComboBox, gridBagConstraints);

        sv_scanSpeedComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_scanSpeedComboBox.setToolTipText("Scan delay (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        scannerPanel.add(sv_scanSpeedComboBox, gridBagConstraints);
        sv_scanSpeedComboBox.getAccessibleContext().setAccessibleName("Scan delay before restart scan");
        sv_scanSpeedComboBox.getAccessibleContext().setAccessibleDescription("Scan delay when squelch opens");

        sv_dwellTimeComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_dwellTimeComboBox.setToolTipText("Pause dwell time (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        scannerPanel.add(sv_dwellTimeComboBox, gridBagConstraints);
        sv_dwellTimeComboBox.getAccessibleContext().setAccessibleName("Dwell time in seconds");

        scanDownButton.setText("<-");
        scanDownButton.setToolTipText("Scan down");
        scanDownButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scanDownButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        scannerPanel.add(scanDownButton, gridBagConstraints);

        scanStopButton.setText("Stop");
        scanStopButton.setToolTipText("Halt scan");
        scanStopButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scanStopButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        scannerPanel.add(scanStopButton, gridBagConstraints);

        scanUpButton.setText("->");
        scanUpButton.setToolTipText("Scan up");
        scanUpButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                scanUpButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        scannerPanel.add(scanUpButton, gridBagConstraints);

        sv_squelchCheckBox.setSelected(true);
        sv_squelchCheckBox.setText("Squelch");
        sv_squelchCheckBox.setToolTipText("Pause on squelch active");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        scannerPanel.add(sv_squelchCheckBox, gridBagConstraints);

        scanTypeLabel.setText("Scan");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        scannerPanel.add(scanTypeLabel, gridBagConstraints);

        scanIconLabel.setText("x");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        scannerPanel.add(scanIconLabel, gridBagConstraints);

        memoryButtonsPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        memoryButtonsPanel.setMinimumSize(new java.awt.Dimension(40, 40));
        memoryButtonsPanel.setLayout(new java.awt.GridLayout(1, 0));
        memoryScrollPane.setViewportView(memoryButtonsPanel);

        buttonPanel2.setOpaque(false);
        buttonPanel2.setLayout(new java.awt.GridBagLayout());

        copyMemButton.setText("CM");
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

        pasteMemButton.setText("PM");
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

        eraseMemButton.setText("EM");
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

        helpButton.setText("HELP");
        helpButton.setToolTipText("Visit the JRX Home Page");
        helpButton.setActionCommand("Help");
        helpButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                helpButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel2.add(helpButton, gridBagConstraints);

        tuneComsButton.setText("Conf");
        tuneComsButton.setToolTipText("Configure Hamlib communications");
        tuneComsButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tuneComsButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel2.add(tuneComsButton, gridBagConstraints);

        javax.swing.GroupLayout memoryStoragePanelLayout = new javax.swing.GroupLayout(memoryStoragePanel);
        memoryStoragePanel.setLayout(memoryStoragePanelLayout);
        memoryStoragePanelLayout.setHorizontalGroup(
            memoryStoragePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(memoryStoragePanelLayout.createSequentialGroup()
                .addGap(90, 90, 90)
                .addComponent(buttonPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(185, Short.MAX_VALUE))
            .addComponent(memoryScrollPane)
        );
        memoryStoragePanelLayout.setVerticalGroup(
            memoryStoragePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(memoryStoragePanelLayout.createSequentialGroup()
                .addComponent(memoryScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 325, Short.MAX_VALUE)
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
            .addComponent(memoryPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        memoryPanel.getAccessibleContext().setAccessibleName("Favorite Channels Table");
        memoryPanel.getAccessibleContext().setAccessibleDescription("Select a row to set V F O.");

        channelsTabbedPane.addTab("Favorite SWL Channels", null, favoriteSWLChannels, "A list of popular SWL frequencies");
        favoriteSWLChannels.getAccessibleContext().setAccessibleName("Favorite SWL Channels");
        favoriteSWLChannels.getAccessibleContext().setAccessibleDescription("List selection can be used with scanner");

        javax.swing.GroupLayout scanPanelLayout = new javax.swing.GroupLayout(scanPanel);
        scanPanel.setLayout(scanPanelLayout);
        scanPanelLayout.setHorizontalGroup(
            scanPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(scanPanelLayout.createSequentialGroup()
                .addGap(181, 181, 181)
                .addComponent(scannerPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(scanPanelLayout.createSequentialGroup()
                .addComponent(channelsTabbedPane)
                .addContainerGap())
        );
        scanPanelLayout.setVerticalGroup(
            scanPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(scanPanelLayout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addComponent(scannerPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(channelsTabbedPane))
        );

        overallTabbedPane.addTab("Scan", scanPanel);
        scanPanel.getAccessibleContext().setAccessibleName("Scan ");
        scanPanel.getAccessibleContext().setAccessibleDescription("Software driven channel or increment receive scanner");

        scopePanel.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout scopeDisplayPanelLayout = new javax.swing.GroupLayout(scopeDisplayPanel);
        scopeDisplayPanel.setLayout(scopeDisplayPanelLayout);
        scopeDisplayPanelLayout.setHorizontalGroup(
            scopeDisplayPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 644, Short.MAX_VALUE)
        );
        scopeDisplayPanelLayout.setVerticalGroup(
            scopeDisplayPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 371, Short.MAX_VALUE)
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

        scopeControlRightPanel.setLayout(new java.awt.GridBagLayout());

        jrxRadioButtonGroup.add(sv_jrxToRadioButton);
        sv_jrxToRadioButton.setSelected(true);
        sv_jrxToRadioButton.setText("JRX->Radio");
        sv_jrxToRadioButton.setToolTipText("At startup, JRX sets the radio's controls");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        scopeControlRightPanel.add(sv_jrxToRadioButton, gridBagConstraints);

        jrxRadioButtonGroup.add(sv_radioToJrxButton);
        sv_radioToJrxButton.setText("Radio->JRX");
        sv_radioToJrxButton.setToolTipText("At startup, the radio sets JRX's controls");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        scopeControlRightPanel.add(sv_radioToJrxButton, gridBagConstraints);

        sv_syncCheckBox.setText("Sync with radio");
        sv_syncCheckBox.setToolTipText("<html>Dynamically synchronize JRX<br/>controls with radio controls");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        scopeControlRightPanel.add(sv_syncCheckBox, gridBagConstraints);

        sv_timerIntervalComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_timerIntervalComboBox.setToolTipText("Control/event update timer interval (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        scopeControlRightPanel.add(sv_timerIntervalComboBox, gridBagConstraints);

        jLabel2.setText("JRX setup controls");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        scopeControlRightPanel.add(jLabel2, gridBagConstraints);

        sv_volumeExitCheckBox.setSelected(true);
        sv_volumeExitCheckBox.setText("Volume down on exit");
        sv_volumeExitCheckBox.setToolTipText("<html>Turn down the radio volume<br/>when JRX exits");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        scopeControlRightPanel.add(sv_volumeExitCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 1, 1, 1);
        scopeControlPanel.add(scopeControlRightPanel, gridBagConstraints);

        scopePanel.add(scopeControlPanel, java.awt.BorderLayout.SOUTH);

        jrxScopePanel.add(scopePanel);

        overallTabbedPane.addTab("Scope", jrxScopePanel);
        jrxScopePanel.getAccessibleContext().setAccessibleName("Band scope ");
        jrxScopePanel.getAccessibleContext().setAccessibleDescription("Visual sweep display of selected band.");

        sv_radioNamesComboBox.setFont(new java.awt.Font("Lucida Grande", 0, 14)); // NOI18N
        sv_radioNamesComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "IC-7100", "Item 2", "Item 3", "Item 4" }));
        sv_radioNamesComboBox.setToolTipText("Available radio manufacturers and models");
        sv_radioNamesComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_radioNamesComboBoxActionPerformed(evt);
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

        sv_interfacesComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "/dev/tty.SLABusbToUart1", "Item 2", "Item 3", "Item 4" }));
        sv_interfacesComboBox.setToolTipText("Available communication interfaces");
        sv_interfacesComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_interfacesComboBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout radioPanelLayout = new javax.swing.GroupLayout(radioPanel);
        radioPanel.setLayout(radioPanelLayout);
        radioPanelLayout.setHorizontalGroup(
            radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(radioPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(overallTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 708, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(radioPanelLayout.createSequentialGroup()
                        .addComponent(ledPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sv_radioNamesComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 148, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(radioNamesLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(sv_interfacesComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 249, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(radioPanelLayout.createSequentialGroup()
                .addComponent(signalProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 703, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        radioPanelLayout.setVerticalGroup(
            radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(radioPanelLayout.createSequentialGroup()
                .addComponent(signalProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(radioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(ledPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(radioNamesLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sv_interfacesComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sv_radioNamesComboBox, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(overallTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 531, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(148, 148, 148))
        );

        radioPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {ledPanel, radioNamesLabel});

        vfoTabbedPane.setAlignmentY(0.0F);

        rxVfoPanel.setBackground(new java.awt.Color(0, 0, 0));

        digitsParent.setLayout(new java.awt.GridBagLayout());

        javax.swing.GroupLayout rxVfoPanelLayout = new javax.swing.GroupLayout(rxVfoPanel);
        rxVfoPanel.setLayout(rxVfoPanelLayout);
        rxVfoPanelLayout.setHorizontalGroup(
            rxVfoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(digitsParent, javax.swing.GroupLayout.DEFAULT_SIZE, 684, Short.MAX_VALUE)
        );
        rxVfoPanelLayout.setVerticalGroup(
            rxVfoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(digitsParent, javax.swing.GroupLayout.DEFAULT_SIZE, 108, Short.MAX_VALUE)
        );

        digitsParent.getAccessibleContext().setAccessibleName("VFO Display Control");

        vfoTabbedPane.addTab("RX VFO  146.670 Mhz", null, rxVfoPanel, "Receiver VFO control and display.  Use arrow keys to change values and traverse digits.");
        rxVfoPanel.getAccessibleContext().setAccessibleName("RX VFO PANEL");
        rxVfoPanel.getAccessibleContext().setAccessibleDescription("");

        javax.swing.GroupLayout txVfoPanelLayout = new javax.swing.GroupLayout(txVfoPanel);
        txVfoPanel.setLayout(txVfoPanelLayout);
        txVfoPanelLayout.setHorizontalGroup(
            txVfoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 684, Short.MAX_VALUE)
        );
        txVfoPanelLayout.setVerticalGroup(
            txVfoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        vfoTabbedPane.addTab("TX VFO 146.070 Mhz", txVfoPanel);
        txVfoPanel.getAccessibleContext().setAccessibleName("TX V F O display and control");
        txVfoPanel.getAccessibleContext().setAccessibleDescription("Use arrow keys to change value or traverse digits.");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(radioPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 717, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(vfoTabbedPane)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(radioPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 606, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(vfoTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing

        closeApp();
    }//GEN-LAST:event_formWindowClosing

    private void scanHelpButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scanHelpButtonMouseClicked
        // TODO add your handling code here:
        launchHelp("http://arachnoid.com/JRX/index.html#Spectrum_Analysis");
    }//GEN-LAST:event_scanHelpButtonMouseClicked

    private void copyButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_copyButtonMouseClicked
        // TODO add your handling code here:
        getScopePanel().saveData();
    }//GEN-LAST:event_copyButtonMouseClicked

    private void scopeDefaultsButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scopeDefaultsButtonMouseClicked
        // TODO add your handling code here:
        getScopePanel().setup();
    }//GEN-LAST:event_scopeDefaultsButtonMouseClicked

    private void scopeScaleButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scopeScaleButtonMouseClicked
        // TODO add your handling code here:
        getScopePanel().autoscale();
    }//GEN-LAST:event_scopeScaleButtonMouseClicked

    private void sv_scopeDotsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_scopeDotsCheckBoxActionPerformed
        // TODO add your handling code here:
        repaint();
    }//GEN-LAST:event_sv_scopeDotsCheckBoxActionPerformed

    private void scopeStartStopButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scopeStartStopButtonMouseClicked
        // TODO add your handling code here:
        getScopePanel().startSweep();
    }//GEN-LAST:event_scopeStartStopButtonMouseClicked

    private void scanUpButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scanUpButtonMouseClicked
        // TODO add your handling code here:
        scanStateMachine.startScan(1);
    }//GEN-LAST:event_scanUpButtonMouseClicked

    private void scanStopButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scanStopButtonMouseClicked
        // TODO add your handling code here:
        scanStateMachine.stopScan(true);
    }//GEN-LAST:event_scanStopButtonMouseClicked

    private void scanDownButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_scanDownButtonMouseClicked
        // TODO add your handling code here:
        scanStateMachine.startScan(-1);
    }//GEN-LAST:event_scanDownButtonMouseClicked

    private void sv_anfCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_anfCheckBoxActionPerformed
        // TODO add your handling code here:
        enableDSP();
    }//GEN-LAST:event_sv_anfCheckBoxActionPerformed

    private void sv_apfCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_apfCheckBoxActionPerformed
        // TODO add your handling code here:
        enableDSP();
    }//GEN-LAST:event_sv_apfCheckBoxActionPerformed

    private void sv_rawSigCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_rawSigCheckBoxActionPerformed
        // TODO add your handling code here:
        testRawSignalMode();
    }//GEN-LAST:event_sv_rawSigCheckBoxActionPerformed

    private void tuneComsButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tuneComsButtonMouseClicked
        // TODO add your handling code here:
        showConfigDialog();
    }//GEN-LAST:event_tuneComsButtonMouseClicked

    private void helpButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_helpButtonMouseClicked
        // TODO add your handling code here:
        launchHelp("http://arachnoid.com/JRX");
    }//GEN-LAST:event_helpButtonMouseClicked

    private void eraseMemButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_eraseMemButtonMouseClicked
        // TODO add your handling code here:
        memoryCollection.dispatch(evt);
    }//GEN-LAST:event_eraseMemButtonMouseClicked

    private void pasteMemButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pasteMemButtonMouseClicked
        // TODO add your handling code here:
        memoryCollection.dispatch(evt);
    }//GEN-LAST:event_pasteMemButtonMouseClicked

    private void copyMemButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_copyMemButtonMouseClicked
        // TODO add your handling code here:
        memoryCollection.dispatch(evt);
    }//GEN-LAST:event_copyMemButtonMouseClicked

    private void sv_synthSquelchCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_synthSquelchCheckBoxActionPerformed
        // TODO add your handling code here:
        squelchScheme.setSquelchScheme();
    }//GEN-LAST:event_sv_synthSquelchCheckBoxActionPerformed

    private void sv_interfacesComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_interfacesComboBoxActionPerformed
        // TODO add your handling code here:
        initialize();
    }//GEN-LAST:event_sv_interfacesComboBoxActionPerformed

    private void sv_radioNamesComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_radioNamesComboBoxActionPerformed
        // TODO add your handling code here:
        initialize();
    }//GEN-LAST:event_sv_radioNamesComboBoxActionPerformed

    private void sv_modesComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_modesComboBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_sv_modesComboBoxActionPerformed

    private void sv_agcComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_agcComboBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_sv_agcComboBoxActionPerformed

    private void jCheckBox2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBox2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jCheckBox2ActionPerformed

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
    private javax.swing.JPanel buttonPanel2;
    private javax.swing.JTabbedPane channelsTabbedPane;
    protected javax.swing.JLabel comErrorIconLabel;
    private javax.swing.JSlider compressionSlider;
    private javax.swing.JButton copyButton;
    private javax.swing.JButton copyMemButton;
    private javax.swing.JLabel ctcssLabel;
    protected javax.swing.JLabel dcdIconLabel;
    private javax.swing.JPanel digitsParent;
    private javax.swing.JButton eraseMemButton;
    private javax.swing.JPanel favoriteSWLChannels;
    private javax.swing.JButton helpButton;
    private javax.swing.JPanel ifControlsPanel;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JCheckBox jCheckBox2;
    private javax.swing.JCheckBox jCheckBox3;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JRadioButton jRadioButton1;
    private javax.swing.JRadioButton jRadioButton2;
    private javax.swing.JRadioButton jRadioButton3;
    private javax.swing.ButtonGroup jrxRadioButtonGroup;
    private javax.swing.JPanel jrxScopePanel;
    private javax.swing.JPanel keyerPanel;
    private javax.swing.JPanel ledPanel;
    protected javax.swing.JPanel memoryButtonsPanel;
    protected javax.swing.JPanel memoryPanel;
    private javax.swing.JScrollPane memoryScrollPane;
    private javax.swing.JPanel memoryStoragePanel;
    private javax.swing.JSlider micGainSlider;
    private javax.swing.JLabel modeLabel;
    private javax.swing.JCheckBox muteCheckBox;
    private javax.swing.JPanel noiseReductionPanel;
    private javax.swing.JPanel operateTransceiverPanel;
    private javax.swing.JTabbedPane operationDetailsTabbedPane;
    private javax.swing.JTabbedPane overallTabbedPane;
    private javax.swing.JButton pasteMemButton;
    private javax.swing.JLabel radioNamesLabel;
    private javax.swing.JPanel radioPanel;
    private javax.swing.JLabel rfGainLabel;
    private javax.swing.JSlider rfPowerOutputSlider;
    private javax.swing.JPanel rttyPanel;
    private javax.swing.JPanel rxVfoPanel;
    private javax.swing.JButton scanDownButton;
    private javax.swing.JButton scanHelpButton;
    protected javax.swing.JLabel scanIconLabel;
    private javax.swing.JPanel scanPanel;
    private javax.swing.JButton scanStopButton;
    protected javax.swing.JLabel scanTypeLabel;
    private javax.swing.JButton scanUpButton;
    protected javax.swing.JPanel scannerPanel;
    private javax.swing.JPanel scopeControlLeftPanel;
    private javax.swing.JPanel scopeControlPanel;
    private javax.swing.JPanel scopeControlRightPanel;
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
    protected javax.swing.JCheckBox sv_ctcssCheckBox;
    protected javax.swing.JComboBox sv_ctcssComboBox;
    protected javax.swing.JCheckBox sv_dspCheckBox;
    protected javax.swing.JComboBox sv_dspComboBox;
    protected javax.swing.JComboBox<String> sv_dwellTimeComboBox;
    protected javax.swing.JComboBox<String> sv_filtersComboBox;
    protected javax.swing.JComboBox sv_ifShiftComboBox;
    protected javax.swing.JComboBox<String> sv_interfacesComboBox;
    protected javax.swing.JRadioButton sv_jrxToRadioButton;
    protected javax.swing.JComboBox<String> sv_modesComboBox;
    protected javax.swing.JComboBox sv_preampComboBox;
    protected javax.swing.JComboBox<String> sv_radioNamesComboBox;
    protected javax.swing.JRadioButton sv_radioToJrxButton;
    protected javax.swing.JCheckBox sv_rawSigCheckBox;
    protected javax.swing.JSlider sv_rfGainSlider;
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
    protected javax.swing.JCheckBox sv_volumeExitCheckBox;
    protected javax.swing.JSlider sv_volumeSlider;
    private javax.swing.JComboBox<String> toneSelectionComboBox;
    private javax.swing.JPanel transmitterPanel;
    private javax.swing.JButton tuneComsButton;
    private javax.swing.JPanel txVfoPanel;
    private javax.swing.JPanel verticalListPanel;
    private javax.swing.JTabbedPane vfoTabbedPane;
    private javax.swing.JSlider voxSlider;
    // End of variables declaration//GEN-END:variables
}
