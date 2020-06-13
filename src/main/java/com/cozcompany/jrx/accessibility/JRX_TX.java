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

    final String appVersion = "5.0.2";
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
   
    Map<String, Integer> radioCodes = null;
    Map<String, Double> filters = null;
    Map<String, Double> ctcss = null;
    ArrayList<ControlInterface> controlList;
    int iErrorValue = -100;
    long defaultFrequency = 145000000;
    int sv_displayState = 0;
    JButton[] stateButtons;
    String[] buttonLabels;
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
    JFrame sv_frameDims = null;
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
    int defWidth = 800;
    int defHeight = 400;
    
    MemoryCollection memoryCollection;
    boolean slowRadio = false;
    int readBufferLen = 2048;
    byte[] readBuffer;
    boolean dcdCapable = false;
    boolean useJRXSquelch = false;
    // this was a boolean but it needs to
    // have three states: -1, never set
    // 0 = false, 1 = true
    int squelchOpen = -1;
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
        stateButtons = new JButton[]{radioMemoryButton, radioListButton, radioScannerButton};
        buttonLabels = new String[]{"Radio:Buttons", "Radio:List", "Scope/Setup"};
        baseFont = new Font(Font.MONOSPACED, Font.PLAIN, getFont().getSize());
        setFont(baseFont);
        // Must create/initialize vfoDisplay before scan functions.
        Rectangle displaySpace = new Rectangle(0,19,276,45);
        vfoDisplay = new FreqDisplay(this, digitsParent, displaySpace);
        vfoDisplay.initDigits();

        scanStateMachine = new ScanStateMachine(this);
        scanDude = new ScanController(this);
        // default app size
        setSize(defWidth, defHeight);
        setControlList();
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

    private void setDisplayState(int v) {       
        if (sv_displayState != v) {
            sv_displayState = v;   
            configureDisplayState();
        }
         
    }

    private void configureDisplayState() {
        CardLayout rcl = (CardLayout) radioPanel.getLayout();
        rcl.show(radioPanel, (sv_displayState < 2) ? "receiverCard" : "scopeCard");
        CardLayout mcl = (CardLayout) memoryPanel.getLayout();
        mcl.show(memoryPanel, (sv_displayState == 0) ? "memoryCard" : "tableCard");
        int i = 0;
        for (JButton b : stateButtons) {
            b.setText((i == sv_displayState) ? String.format("<html><b>%s</b>", 
                    buttonLabels[i]) : buttonLabels[i]);
            i++;
        }
    }



    private void startCyclicalTimer() {
        if (comArgs.runTimer) {
            if (periodicTimer != null) {
                periodicTimer.cancel();
            }
            periodicTimer = new java.util.Timer();
            resetTimer();
        }
    }

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
                getSquelch(false);
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
            configureDisplayState();
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
            setRadioSquelch();
            readRadioControls(true);  // Reads frequency from radio
            startCyclicalTimer();
            measureSpeed();
            setComErrorIcon();            
            memoryScrollPane.getVerticalScrollBar().setUnitIncrement(8);
            getActiveTab();
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
            p("radio com ms delay: " + dt);
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
                sv_rfGainComboBox,
                sv_ifShiftComboBox,
                sv_dspComboBox,
                sv_dspCheckBox,
                sv_modesComboBox,
                sv_filtersComboBox,
                sv_antennaComboBox,
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
        sv_frameDims = this;
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
                p("have windows exec: " + hamlibExecPath);
            }
        } else {
            hamlibExecPath = fileHelpers.findAbsolutePath(hamlibExecPath);
        }
        setComboBoxContent((RWComboBox) sv_rfGainComboBox, "RF", 0, 100, 5, 0, 100, 0, 1, 100);
        // yhigh intentionally = ylow to allow the rig to set the range
        setComboBoxContent((RWComboBox) sv_ifShiftComboBox, "IF", -50, 50, 5, -50, 50, 0, 0, 0);
        setComboBoxContent((RWComboBox) sv_dspComboBox, "DSP", 0, 100, 5, 0, 100, 0, 1, 0);
        setComboBoxContent((RWComboBox) sv_agcComboBox, "AGC", 0, 10, 1, 0, 1, 0, 10, 1);
        setComboBoxContent((RWComboBox) sv_antennaComboBox, "Ant", 0, 4, 1, 0, 1, 0, 1, 1);
        initInterfaceList();
        initRigSpecs();
        memoryCollection.readMemoryButtons();
        initTimeValues((RWComboBox) sv_timerIntervalComboBox);

        scanDude.initScanValues(sv_scanStepComboBox, 12, sv_scanSpeedComboBox, 5);
        scanDude.initScanValues(null, 0, sv_dwellTimeComboBox, 10);
        scanDude.initScanValues(sv_scopeStepComboBox, 12, sv_scopeSpeedComboBox, 0);
        memoryButtonsPanel.setBackground(new Color(128, 200, 220));
        sliderPanel.setBackground(new Color(192, 200, 192));
        listPanel.setBackground(new Color(200, 220, 240));
        scannerPanel.setBackground(new Color(240, 240, 220));
        scopeControlLeftPanel.setBackground(new Color(240, 240, 220));
        scopeControlRightPanel.setBackground(new Color(200, 220, 240));
        receiverPanel.setBackground(Color.black);
        memoryPanel.setBackground(Color.black);
        digitsParent.setToolTipText("<html>Tune each digit:<ul><li>Mouse wheel (❃): up, down</li><li>Mouse click top: up</li><li>Mouse click bottom: down</li></ul></html>");
        scanDude.updateScanControls();
    }

    // force DSP on if auxiiliary DSP functions are activated
    private void enableDSP() {
        sv_dspCheckBox.setSelected(true);
        ((RWCheckBox) sv_dspCheckBox).actionPerformed(null);
    }

    private void getRigCaps() {
        String com = "\\dump_caps";
        radioData = sendRadioCom(com, 3, false);
        enableControlCap(sv_ctcssComboBox, radioData, "(?ism).*^Can set CTCSS Squelch:\\s+Y$", false);
        enableControlCap(sv_agcComboBox, radioData, "(?ism).*^Set level:.*?AGC\\(", true);
        enableControlCap(sv_antennaComboBox, radioData, "(?ism).*^Can set Ant:\\s+Y$", false);
        enableControlCap(sv_preampComboBox, radioData, "(?ism).*^Set level:.*?PREAMP\\(", true);
        enableControlCap(sv_volumeSlider, radioData, "(?ism).*^Set level:.*?AF\\(", true);
        enableControlCap(sv_rfGainComboBox, radioData, "(?ism).*^Set level:.*?RF\\(", true);
        enableControlCap(sv_squelchSlider, radioData, "(?ism).*^Set level:.*?SQL\\(", true);
        enableControlCap(sv_ifShiftComboBox, radioData, "(?ism).*^Set level:.*?IF\\(", true);
        enableControlCap(sv_blankerCheckBox, radioData, "(?ism).*^Set functions:.*?\\sNB\\s", false);
        enableControlCap(sv_anfCheckBox, radioData, "(?ism).*^Set functions:.*?\\sANF\\s", false);
        enableControlCap(sv_apfCheckBox, radioData, "(?ism).*^Set functions:.*?\\sAPF\\s", false);
        enableControlCap(sv_dspComboBox, radioData, "(?ism).*^Set level:.*?NR\\(", true);
        String s = sendRadioCom("\\get_dcd", 0, false);
        dcdCapable = (s != null && s.matches("\\d+"));
        setSquelchScheme();
    }

    private void setSquelchScheme() {
        // sv_synthSquelchCheckBox.setEnabled(!dcdCapable);
        sv_synthSquelchCheckBox.setEnabled(true);
        useJRXSquelch = sv_synthSquelchCheckBox.isSelected();// && !dcdCapable;
        // reset squelch state to default
        enableControlCap(sv_squelchSlider, radioData, "(?ism).*^Set level:.*?SQL\\(", true);
        if (useJRXSquelch) {
            sv_squelchSlider.setEnabled(true);
        }
        // was: boolean stateFlag = dcdCapable && !useJRXSquelch;
        boolean stateFlag = !useJRXSquelch;
        //p("dcd: " + dcdCapable + ",useJR: " + useJRXSquelch);
        ((RWSlider) sv_squelchSlider).commOK = stateFlag;
        //((RWSlider) sv_volumeSlider).commOK = stateFlag;
        setRadioSquelch();
        dcdIconLabel.setIcon(dcdCapable ? greenLed : useJRXSquelch ? yellowLed : redLed);
        dcdIconLabel.setToolTipText((stateFlag) ? "Radio provides squelch scheme" : 
                useJRXSquelch ? "JRX provides squelch scheme" : "No squelch scheme enabled");
        if (comArgs.debug >= 0) {
            p("DCD capable: " + dcdCapable);
        }
        ((RWSlider) sv_squelchSlider).writeValue(false);
        ((RWSlider) sv_volumeSlider).writeValue(false);
        getSquelch(true);
        scanDude.updateScanControls();
    }

    private void setRadioSquelch() {
        if (useJRXSquelch) {
            String com = String.format("L SQL 0");
            sendRadioCom(com, 0, false);
        }
        oldVolume = -1;
        squelchOpen = -1;
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

    protected boolean testSquelch() {
        boolean so = (squelchOpen == 1) && this.sv_squelchCheckBox.isSelected();
        scanIconLabel.setIcon((so && scanStateMachine.scanTimer != null) ? redLed : greenLed);
        return so;
    }

    protected void getSquelch(boolean force) {
        int sqOpen = iErrorValue;
        if (dcdCapable && !useJRXSquelch) {
            if (comArgs.debug < 2) {
                String s = sendRadioCom("\\get_dcd", 1, false);
                if (s != null) {
                    sqOpen = s.trim().equals("1") ? 1 : 0;
                }
            }
        } else if (!useJRXSquelch) {
            sqOpen = 1;
        } else {
            double sv = ((ControlInterface) sv_squelchSlider).getConvertedValue();
            sv = ntrp(0, 1, squelchLow, squelchHigh, sv);
            sqOpen = (signalStrength > sv) ? 1 : 0;
        }
        //p("sqOpen " + sqOpen + "," + squelchOpen);
        if ((sqOpen != iErrorValue && sqOpen != squelchOpen) || force) {
            //p("sqOpen2 " + sqOpen);
            squelchOpen = sqOpen;
            setVolume(squelchOpen == 1);
        }
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
                p(String.format("scope sample delay: %f", dt));
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
            System.out.println("readFrequency from radio : "+ v );
            sf = sendRadioCom("f", 0, false);
            v = Long.parseLong(sf);
            System.out.println("extra readFrequency from radio : "+ v );

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
            
            setRadioFrequency(vfoDisplay.getFreq());
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
                p("serial list output: [" + data + "]");
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
                p("dump from rigctld -l: [" + s + "]");
            }
        } 
        for (String item : s.split(lineSep)) {
            // the try ... catch is only to filter the table header
            if (item.length() > 30) {
                try {
                    if (comArgs.debug >= 1) {
                        p("rigctl radio description line: [" + item + "]");
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
                                p("radio record: " + b + " = " + v);
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
//            //////////////////////////////////////HACK
//            //////HACK BELOW
//            ProcessBuilder pb = new ProcessBuilder(array).redirectErrorStream(true);
//            Map<String, String> env = pb.environment();
//            String path = env.get("PATH");
//            env.remove("PATH");
//            env.put("PATH", "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/Library/Apple/usr/bin");
//            path = env.get("PATH"); 
//            Process p = pb.start();
//            ////////////////////////////////////////END HACK
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
            p("setupHamlibDaemon rigCode :" +rigCode+ " interfaceName : "+ interfaceName);
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
                    p("setup daemon with: " + rigName + "," + rigCode + "," + interfaceName);
                }
                try {
//                    //////////////////////////////////////////////////////////
//                    // HACK
//                    hamlibExecPath = "/usr/local/bin/rigctld";
//                    ProcessBuilder pb = new ProcessBuilder(com).redirectErrorStream(true);
//                    Map<String, String> env = pb.environment();
//                    String path = env.get("PATH");
//                    env.remove("PATH");
//                    env.put("PATH", "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/Library/Apple/usr/bin");
//                    path = env.get("PATH");                                            
//                    hamlibDaemon = pb.start();
//                    //////////////////////////////////////////////////////////
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
                                p("socket connected: " + connected);
                            }
                        } catch (Exception e) {
                            if (comArgs.debug >= 0) {
                                p("fail connect " + e.getMessage());
                            }
                            waitMS(500);
                            if (n-- <= 0) {
                                tellUser("Error: Cannot connect to Hamlib Daemon process.");
                            }
                        }
                    }
                    // now get radio data and set up drop-down lists
                    getRigCaps();
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                }
            }
        }
    }

    private void enableControlCap(Component cc, String source, String search, boolean level) {
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
                        p("sendradiocom   emit: [" + s + "]");
                    }
                    result = readInputStream(hamlib_is);
                    if (comArgs.debug >= localDebug) {
                        p("sendradiocom result: [" + result.replaceAll("[\r\n]", " ") + "]");
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


    private void checkMainTabState() {
        //int index = this.sv_mainTabbedPane.getSelectedIndex();
        if (sv_displayState < 2) {
            this.getScopePanel().stopSweep(true);
        }
    }

    protected int getActiveTab() {
        //int index = this.sv_freqListTabbedPane.getSelectedIndex();
        //scannerPanel.setVisible(index == 0);
        return sv_displayState;
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
    public void p(String s) {
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
        radioPanel = new javax.swing.JPanel();
        receiverPanel = new javax.swing.JPanel();
        listPanel = new javax.swing.JPanel();
        buttonPanel5 = new javax.swing.JPanel();
        sv_radioNamesComboBox = new javax.swing.JComboBox<>();
        speedIconLabel = new javax.swing.JLabel();
        comErrorIconLabel = new javax.swing.JLabel();
        dcdIconLabel = new javax.swing.JLabel();
        buttonPanel4 = new javax.swing.JPanel();
        sv_interfacesComboBox = new javax.swing.JComboBox<>();
        sv_antennaComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"Y","");
        sv_synthSquelchCheckBox = new RWCheckBox(this,null,null);
        buttonPanel1 = new javax.swing.JPanel();
        sv_filtersComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"F","");
        sv_ctcssComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"ctcss","");
        sv_attenuatorComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","ATT");
        buttonPanel3 = new javax.swing.JPanel();
        sv_modesComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"M","");
        sv_agcComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","AGC");
        sv_dspComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","NR");
        buttonPanel6 = new javax.swing.JPanel();
        sv_rfGainComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","RF");
        sv_ifShiftComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","IF");
        sv_preampComboBox = new com.cozcompany.jrx.accessibility.RWComboBox(this,"L","PREAMP");
        buttonPanel2 = new javax.swing.JPanel();
        copyMemButton = new javax.swing.JButton();
        pasteMemButton = new javax.swing.JButton();
        eraseMemButton = new javax.swing.JButton();
        helpButton = new javax.swing.JButton();
        quitButton = new javax.swing.JButton();
        tuneComsButton = new javax.swing.JButton();
        verticalListPanel = new javax.swing.JPanel();
        sv_rawSigCheckBox = new RWCheckBox(this,null,null);
        sv_blankerCheckBox = new RWCheckBox(this,"U","NB");
        sv_apfCheckBox = new RWCheckBox(this,"U","APF");
        sv_anfCheckBox = new RWCheckBox(this,"U","ANF");
        sv_ctcssCheckBox = new RWCheckBox(this,"U","TSQL");
        sv_dspCheckBox = new RWCheckBox(this,"U","NR");
        signalPanel = new javax.swing.JPanel();
        signalProgressBar = new javax.swing.JProgressBar();
        digitsParent = new javax.swing.JPanel();
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
        sliderPanel = new javax.swing.JPanel();
        sv_volumeSlider = new com.cozcompany.jrx.accessibility.RWSlider(this,"L","AF",20);
        sv_squelchSlider = new com.cozcompany.jrx.accessibility.RWSlider(this,"L","SQL",0);
        memoryPanel = new javax.swing.JPanel();
        memoryScrollPane = new javax.swing.JScrollPane();
        memoryButtonsPanel = new javax.swing.JPanel();
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
        button_bar = new javax.swing.JPanel();
        radioMemoryButton = new javax.swing.JButton();
        radioListButton = new javax.swing.JButton();
        radioScannerButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        radioPanel.setLayout(new java.awt.CardLayout());

        receiverPanel.setLayout(new java.awt.GridBagLayout());

        listPanel.setLayout(new java.awt.GridBagLayout());

        buttonPanel5.setOpaque(false);
        buttonPanel5.setLayout(new java.awt.GridBagLayout());

        sv_radioNamesComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_radioNamesComboBox.setToolTipText("Available radio manufacturers and models");
        sv_radioNamesComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_radioNamesComboBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel5.add(sv_radioNamesComboBox, gridBagConstraints);

        speedIconLabel.setText("Speed Icon");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        buttonPanel5.add(speedIconLabel, gridBagConstraints);

        comErrorIconLabel.setText("Error Icon");
        comErrorIconLabel.setToolTipText("Red indicates communications error");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        buttonPanel5.add(comErrorIconLabel, gridBagConstraints);

        dcdIconLabel.setText("DCD Icon");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        buttonPanel5.add(dcdIconLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        listPanel.add(buttonPanel5, gridBagConstraints);

        buttonPanel4.setOpaque(false);
        buttonPanel4.setLayout(new java.awt.GridBagLayout());

        sv_interfacesComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_interfacesComboBox.setToolTipText("Available communication interfaces");
        sv_interfacesComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_interfacesComboBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel4.add(sv_interfacesComboBox, gridBagConstraints);

        sv_antennaComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_antennaComboBox.setToolTipText("Available antennas (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel4.add(sv_antennaComboBox, gridBagConstraints);

        sv_synthSquelchCheckBox.setText("Squelch");
        sv_synthSquelchCheckBox.setToolTipText("Use JRX squelch scheme");
        sv_synthSquelchCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_synthSquelchCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        buttonPanel4.add(sv_synthSquelchCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        listPanel.add(buttonPanel4, gridBagConstraints);

        buttonPanel1.setOpaque(false);
        buttonPanel1.setLayout(new java.awt.GridBagLayout());

        sv_filtersComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_filtersComboBox.setToolTipText("Bandwidth filters (❃)\n");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel1.add(sv_filtersComboBox, gridBagConstraints);

        sv_ctcssComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_ctcssComboBox.setToolTipText("CTCSS tone squelch frequencies (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel1.add(sv_ctcssComboBox, gridBagConstraints);

        sv_attenuatorComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_attenuatorComboBox.setToolTipText("Input attenuator (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel1.add(sv_attenuatorComboBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        listPanel.add(buttonPanel1, gridBagConstraints);

        buttonPanel3.setOpaque(false);
        buttonPanel3.setLayout(new java.awt.GridBagLayout());

        sv_modesComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_modesComboBox.setToolTipText("Operating modes (❃)");
        sv_modesComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_modesComboBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel3.add(sv_modesComboBox, gridBagConstraints);

        sv_agcComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_agcComboBox.setToolTipText("AGC setting (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel3.add(sv_agcComboBox, gridBagConstraints);

        sv_dspComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_dspComboBox.setToolTipText("DSP Noise Reduction (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel3.add(sv_dspComboBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        listPanel.add(buttonPanel3, gridBagConstraints);

        buttonPanel6.setOpaque(false);
        buttonPanel6.setLayout(new java.awt.GridBagLayout());

        sv_rfGainComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_rfGainComboBox.setToolTipText("RF Gain (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel6.add(sv_rfGainComboBox, gridBagConstraints);

        sv_ifShiftComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_ifShiftComboBox.setToolTipText("IF Shift (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel6.add(sv_ifShiftComboBox, gridBagConstraints);

        sv_preampComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_preampComboBox.setToolTipText("Preamp setting (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel6.add(sv_preampComboBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        listPanel.add(buttonPanel6, gridBagConstraints);

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

        quitButton.setText("Quit");
        quitButton.setToolTipText("Exit JRX");
        quitButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                quitButtonMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        buttonPanel2.add(quitButton, gridBagConstraints);

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

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        listPanel.add(buttonPanel2, gridBagConstraints);

        verticalListPanel.setOpaque(false);
        verticalListPanel.setLayout(new java.awt.GridBagLayout());

        sv_rawSigCheckBox.setText("Raw");
        sv_rawSigCheckBox.setToolTipText("<html>Use unconverted signal<br/>strength readings");
        sv_rawSigCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_rawSigCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 2);
        verticalListPanel.add(sv_rawSigCheckBox, gridBagConstraints);

        sv_blankerCheckBox.setText("NB");
        sv_blankerCheckBox.setToolTipText("<html>Noise Blanker -- reduces<br/>some kinds of noise");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        verticalListPanel.add(sv_blankerCheckBox, gridBagConstraints);

        sv_apfCheckBox.setText("APF");
        sv_apfCheckBox.setToolTipText("Automatic Peak Filter");
        sv_apfCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_apfCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        verticalListPanel.add(sv_apfCheckBox, gridBagConstraints);

        sv_anfCheckBox.setText("ANF");
        sv_anfCheckBox.setToolTipText("Automatic Notch Filter");
        sv_anfCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sv_anfCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        verticalListPanel.add(sv_anfCheckBox, gridBagConstraints);

        sv_ctcssCheckBox.setText("CTCSS");
        sv_ctcssCheckBox.setToolTipText("Tone squelch control");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 1, 0, 1);
        verticalListPanel.add(sv_ctcssCheckBox, gridBagConstraints);

        sv_dspCheckBox.setText("DSP");
        sv_dspCheckBox.setToolTipText("Digital signal processing");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        verticalListPanel.add(sv_dspCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridheight = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        listPanel.add(verticalListPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 0, 1);
        receiverPanel.add(listPanel, gridBagConstraints);

        signalPanel.setLayout(new java.awt.GridBagLayout());

        signalProgressBar.setMaximum(20);
        signalProgressBar.setMinimum(-50);
        signalProgressBar.setValue(-50);
        signalProgressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        signalPanel.add(signalProgressBar, gridBagConstraints);

        digitsParent.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        signalPanel.add(digitsParent, gridBagConstraints);

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

        sv_dwellTimeComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        sv_dwellTimeComboBox.setToolTipText("Pause dwell time (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        scannerPanel.add(sv_dwellTimeComboBox, gridBagConstraints);

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

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        signalPanel.add(scannerPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 0, 0);
        receiverPanel.add(signalPanel, gridBagConstraints);

        sliderPanel.setLayout(new java.awt.GridBagLayout());

        sv_volumeSlider.setToolTipText("Audio Gain (❃)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        sliderPanel.add(sv_volumeSlider, gridBagConstraints);

        sv_squelchSlider.setToolTipText("Squelch (❃)\n");
        sv_squelchSlider.setValue(0);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        sliderPanel.add(sv_squelchSlider, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = 8;
        gridBagConstraints.ipady = 8;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 0, 1);
        receiverPanel.add(sliderPanel, gridBagConstraints);

        memoryPanel.setLayout(new java.awt.CardLayout());

        memoryButtonsPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 2));
        memoryButtonsPanel.setLayout(new java.awt.GridLayout(1, 0));
        memoryScrollPane.setViewportView(memoryButtonsPanel);

        memoryPanel.add(memoryScrollPane, "memoryCard");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.1;
        receiverPanel.add(memoryPanel, gridBagConstraints);

        radioPanel.add(receiverPanel, "receiverCard");

        scopePanel.setLayout(new java.awt.BorderLayout());
        scopePanel.add(scopeDisplayPanel, java.awt.BorderLayout.CENTER);

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

        radioPanel.add(scopePanel, "scopeCard");

        getContentPane().add(radioPanel, java.awt.BorderLayout.CENTER);

        radioMemoryButton.setText("Radio:Buttons");
        radioMemoryButton.setToolTipText("Show memory buttons");
        radioMemoryButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                radioMemoryButtonMouseClicked(evt);
            }
        });
        button_bar.add(radioMemoryButton);

        radioListButton.setText("Radio:List");
        radioListButton.setToolTipText("Show frequency list");
        radioListButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                radioListButtonMouseClicked(evt);
            }
        });
        button_bar.add(radioListButton);

        radioScannerButton.setText("Scope/Setup");
        radioScannerButton.setToolTipText("Show spectrum scope and setup controls");
        radioScannerButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                radioScannerButtonMouseClicked(evt);
            }
        });
        button_bar.add(radioScannerButton);

        getContentPane().add(button_bar, java.awt.BorderLayout.PAGE_END);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
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

    private void quitButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_quitButtonMouseClicked
        // TODO add your handling code here:
        closeApp();
    }//GEN-LAST:event_quitButtonMouseClicked

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
        setSquelchScheme();
    }//GEN-LAST:event_sv_synthSquelchCheckBoxActionPerformed

    private void sv_interfacesComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_interfacesComboBoxActionPerformed
        // TODO add your handling code here:
        initialize();
    }//GEN-LAST:event_sv_interfacesComboBoxActionPerformed

    private void sv_radioNamesComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_radioNamesComboBoxActionPerformed
        // TODO add your handling code here:
        initialize();
    }//GEN-LAST:event_sv_radioNamesComboBoxActionPerformed

    private void radioMemoryButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_radioMemoryButtonMouseClicked
        // TODO add your handling code here:
        setDisplayState(0);
    }//GEN-LAST:event_radioMemoryButtonMouseClicked

    private void radioListButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_radioListButtonMouseClicked
        // TODO add your handling code here:
        setDisplayState(1);
    }//GEN-LAST:event_radioListButtonMouseClicked

    private void radioScannerButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_radioScannerButtonMouseClicked
        // TODO add your handling code here:
        setDisplayState(2);
    }//GEN-LAST:event_radioScannerButtonMouseClicked

    private void sv_modesComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sv_modesComboBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_sv_modesComboBoxActionPerformed

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
    private javax.swing.JPanel buttonPanel1;
    private javax.swing.JPanel buttonPanel2;
    private javax.swing.JPanel buttonPanel3;
    private javax.swing.JPanel buttonPanel4;
    private javax.swing.JPanel buttonPanel5;
    private javax.swing.JPanel buttonPanel6;
    private javax.swing.JPanel button_bar;
    private javax.swing.JLabel comErrorIconLabel;
    private javax.swing.JButton copyButton;
    private javax.swing.JButton copyMemButton;
    private javax.swing.JLabel dcdIconLabel;
    private javax.swing.JPanel digitsParent;
    private javax.swing.JButton eraseMemButton;
    private javax.swing.JButton helpButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.ButtonGroup jrxRadioButtonGroup;
    private javax.swing.JPanel listPanel;
    protected javax.swing.JPanel memoryButtonsPanel;
    protected javax.swing.JPanel memoryPanel;
    private javax.swing.JScrollPane memoryScrollPane;
    private javax.swing.JButton pasteMemButton;
    private javax.swing.JButton quitButton;
    private javax.swing.JButton radioListButton;
    private javax.swing.JButton radioMemoryButton;
    private javax.swing.JPanel radioPanel;
    private javax.swing.JButton radioScannerButton;
    private javax.swing.JPanel receiverPanel;
    private javax.swing.JButton scanDownButton;
    private javax.swing.JButton scanHelpButton;
    protected javax.swing.JLabel scanIconLabel;
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
    private javax.swing.JPanel signalPanel;
    private javax.swing.JProgressBar signalProgressBar;
    private javax.swing.JPanel sliderPanel;
    private javax.swing.JLabel speedIconLabel;
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
    protected javax.swing.JComboBox sv_rfGainComboBox;
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
    private javax.swing.JButton tuneComsButton;
    private javax.swing.JPanel verticalListPanel;
    // End of variables declaration//GEN-END:variables
}
